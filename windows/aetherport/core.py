"""AetherPort desktop services. No UI dependencies, so operations can be tested separately."""
from __future__ import annotations

import base64
import json
import os
import re
import secrets
import shutil
import stat
import struct
import subprocess
import sys
import tempfile
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from urllib.parse import quote

import requests
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag

APP_NAME = 'AetherPort'
VERSION = '3.0.27'
MAGIC = b'AETHCRD1'
ITERATIONS = 210_000
MAX_BACKUP = 2 * 1024 * 1024
MAX_ENTRIES = 512
MAX_FIELD = 128 * 1024
IGNORED_ROOT = {'__MACOSX', '.DS_Store', '.git', '.hf_uploader_meta.json', '.forgeport-project.json'}
IGNORED_FILES = {'.DS_Store', '.hf_uploader_meta.json', '.forgeport-project.json'}
PROJECT_DIRS = {'.github', '.gitlab', '.vscode', 'src', 'app', 'lib', 'assets', 'res', 'resources',
                'public', 'static', 'templates', 'images', 'docs', 'doc', 'test', 'tests',
                'android', 'ios', 'web', 'windows', 'linux', 'macos', 'gradle', 'scripts',
                'config', 'data', 'build', 'dist', 'META-INF', 'node_modules', 'vendor',
                'bin', 'include'}
NAME_PATTERN = re.compile(r'[A-Z_][A-Z0-9_]{0,127}\Z')
REPO_PATTERN = re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z')
BRANCH_PATTERN = re.compile(r'[A-Za-z0-9._/-]+\Z')


def data_dir() -> Path:
    if os.name == 'nt':
        return Path(os.environ.get('LOCALAPPDATA', Path.home() / 'AppData' / 'Local')) / APP_NAME
    return Path.home() / '.local' / 'share' / APP_NAME


def normalize_repo(text: str, service: str) -> str:
    value = text.strip().rstrip('/')
    if value.endswith('.git'):
        value = value[:-4]
    if service == 'github':
        value = re.sub(r'^(?:https://github\.com/|git@github\.com:)', '', value)
    else:
        value = re.sub(r'^(?:https://huggingface\.co/spaces/|spaces/|space:)', '', value)
    value = value.strip('/')
    if not REPO_PATTERN.fullmatch(value):
        raise ValueError('Enter a repository as owner/repository.')
    return value


def validate_branch(text: str) -> str:
    value = text.strip()
    if (not value or len(value) > 240 or not BRANCH_PATTERN.fullmatch(value)
        or value.startswith('/') or value.endswith('/') or '..' in value
        or '//' in value or value.endswith('.lock') or '@{' in value):
        raise ValueError('Invalid Git branch name.')
    return value


def repo_variable_name(label: str, hf=False) -> str:
    suffix = re.sub('[^A-Z0-9]+', '_', label.strip().upper()).strip('_')[:100]
    if not suffix:
        raise ValueError('A repository label is required.')
    return ('HF_REPOSITORY_' if hf else 'GITHUB_REPOSITORY_') + suffix


def repository_kind(name: str) -> str | None:
    if name == 'GITHUB_REPOSITORY' or name.startswith('GITHUB_REPOSITORY_'):
        return 'github'
    if name in ('HF_REPOSITORY', 'HUGGINGFACE_REPOSITORY') or name.startswith(('HF_REPOSITORY_', 'HUGGINGFACE_REPOSITORY_')):
        return 'hf'
    return None


def normalize_variable(name: str) -> str:
    name = name.strip().upper()
    if not NAME_PATTERN.fullmatch(name):
        raise ValueError('Variable names must contain A-Z, 0-9 or _ and cannot start with a number.')
    return name


def validate_values(values: dict[str, str]) -> dict[str, str]:
    checked: dict[str, str] = {}
    for original, value in values.items():
        name = normalize_variable(original)
        if name in checked or not isinstance(value, str) or not value or len(value.encode('utf-8')) > MAX_FIELD:
            raise ValueError('Invalid or duplicated credential in backup.')
        kind = repository_kind(name)
        checked[name] = normalize_repo(value, 'github' if kind == 'github' else 'hf') if kind else value
    if not 1 <= len(checked) <= MAX_ENTRIES:
        raise ValueError('No credentials available, or too many entries.')
    return checked


def _field(value: str) -> bytes:
    encoded = value.encode('utf-8')
    if not 1 <= len(encoded) <= MAX_FIELD:
        raise ValueError('Invalid backup field size.')
    return struct.pack('>I', len(encoded)) + encoded


def portable_backup_encrypt(values: dict[str, str], password: str) -> bytes:
    if len(password) < 12:
        raise ValueError('Use a backup password of at least 12 characters.')
    records = validate_values(values)
    payload = struct.pack('>II', 1, len(records))
    for name, value in sorted(records.items()):
        payload += _field(name) + _field(value)
    if len(payload) > MAX_BACKUP - 64:
        raise ValueError('Too many credentials to export.')
    import hashlib
    salt, nonce = secrets.token_bytes(16), secrets.token_bytes(12)
    key = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, ITERATIONS, 32)
    # Identical binary format and AAD to Android CredentialBackupCodec.kt.
    return MAGIC + salt + nonce + AESGCM(key).encrypt(nonce, payload, MAGIC)


def portable_backup_decrypt(blob: bytes, password: str) -> dict[str, str]:
    if not 8+16+12+17 <= len(blob) <= MAX_BACKUP or blob[:8] != MAGIC:
        raise ValueError('Unsupported or invalid AetherPort credential backup.')
    import hashlib
    salt, nonce, ciphertext = blob[8:24], blob[24:36], blob[36:]
    key = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, ITERATIONS, 32)
    try:
        raw = AESGCM(key).decrypt(nonce, ciphertext, MAGIC)
    except InvalidTag as exc:
        raise ValueError('Wrong password or damaged credential backup.') from exc
    mv = memoryview(raw)
    pos = 0
    def read_num() -> int:
        nonlocal pos
        if pos + 4 > len(mv):
            raise ValueError('Truncated backup data.')
        value = struct.unpack_from('>I', mv, pos)[0]
        pos += 4
        return value
    def read_str() -> str:
        nonlocal pos
        length = read_num()
        if not 1 <= length <= MAX_FIELD or pos + length > len(mv):
            raise ValueError('Invalid credential field.')
        value = bytes(mv[pos:pos+length]).decode('utf-8', errors='strict')
        pos += length
        return value
    if read_num() != 1:
        raise ValueError('Unsupported backup format version.')
    count = read_num()
    if not 1 <= count <= MAX_ENTRIES:
        raise ValueError('Invalid backup entry count.')
    result: dict[str, str] = {}
    for _ in range(count):
        name, value = read_str(), read_str()
        if name in result:
            raise ValueError('Backup has duplicate credential names.')
        result[name] = value
    if pos != len(mv):
        raise ValueError('Unexpected data in credential backup.')
    return validate_values(result)


class VariableVault:
    """Encrypted at rest with current Windows user's DPAPI key (offline-dev fallback only)."""
    def __init__(self, root: Path | None = None):
        self.root = root or data_dir()
        self.root.mkdir(parents=True, exist_ok=True)
        self.file = self.root / 'variables.enc'

    def _protect(self, raw: bytes) -> bytes:
        if os.name == 'nt':
            return _windows_dpapi(raw, False)
        # Development fallback on non-Windows systems. Not used in Windows builds.
        from cryptography.fernet import Fernet
        key_path = self.root / '.development-key'
        if not key_path.exists():
            with key_path.open('xb') as f:
                f.write(Fernet.generate_key())
            key_path.chmod(0o600)
        return Fernet(key_path.read_bytes()).encrypt(raw)

    def _unprotect(self, raw: bytes) -> bytes:
        if os.name == 'nt':
            return _windows_dpapi(raw, True)
        from cryptography.fernet import Fernet
        return Fernet((self.root / '.development-key').read_bytes()).decrypt(raw)

    def all(self) -> dict[str, str]:
        if not self.file.exists():
            return {}
        result = json.loads(self._unprotect(self.file.read_bytes()).decode('utf-8'))
        if not isinstance(result, dict):
            raise ValueError('Invalid local vault data.')
        return result

    def _save(self, values: dict[str, str]):
        encoded = self._protect(json.dumps(values, ensure_ascii=False, sort_keys=True).encode('utf-8'))
        temp = self.file.with_suffix('.tmp')
        with temp.open('wb') as out:
            out.write(encoded)
            out.flush()
            os.fsync(out.fileno())
        if os.name != 'nt':
            temp.chmod(0o600)
        os.replace(temp, self.file)

    def put(self, name: str, value: str):
        name = normalize_variable(name)
        normalized = validate_values({name: value})[name]
        values = self.all()
        values[name] = normalized
        self._save(values)

    def delete(self, name: str):
        values = self.all()
        values.pop(normalize_variable(name), None)
        self._save(values)

    def import_backup(self, blob: bytes, password: str, replace=False) -> tuple[int, int]:
        incoming = portable_backup_decrypt(blob, password)  # validate whole backup before touching vault
        existing = self.all()
        changed = {name: value for name, value in incoming.items() if replace or name not in existing}
        if changed:
            self._save({**existing, **changed})
        return len(changed), len(incoming)-len(changed)

    def github_token(self, repo: str) -> str:
        values = self.all()
        owner = normalize_repo(repo, 'github').split('/')[0].upper()
        exact = 'GITHUB_TOKEN_' + re.sub('[^A-Z0-9]+', '_', owner).strip('_')
        if exact in values:
            return values[exact]
        tokens = [v for k,v in values.items() if k.startswith('GITHUB_TOKEN')]
        if len(tokens) == 1:
            return tokens[0]
        raise ValueError(f'Add {exact} in Variables (one token per GitHub owner).')

    def repositories(self, hf=False) -> list[str]:
        vals = self.all()
        kind = 'hf' if hf else 'github'
        return sorted(set(v for k,v in vals.items() if repository_kind(k) == kind))

    def hf_tokens(self) -> list[str]:
        return sorted(k for k in self.all() if k.startswith(('HF_TOKEN', 'HUGGINGFACE_TOKEN')))


def _windows_dpapi(data: bytes, decrypt: bool) -> bytes:
    """Windows CryptProtectData/UnprotectData, scoped to the signed-in Windows user."""
    import ctypes
    from ctypes import wintypes
    class DATA_BLOB(ctypes.Structure):
        _fields_ = [('cbData', wintypes.DWORD), ('pbData', ctypes.POINTER(ctypes.c_byte))]
    crypt32 = ctypes.windll.crypt32
    kernel32 = ctypes.windll.kernel32
    crypt32.CryptProtectData.argtypes = [ctypes.POINTER(DATA_BLOB), wintypes.LPCWSTR,
        ctypes.POINTER(DATA_BLOB), ctypes.c_void_p, ctypes.c_void_p,
        wintypes.DWORD, ctypes.POINTER(DATA_BLOB)]
    crypt32.CryptProtectData.restype = wintypes.BOOL
    crypt32.CryptUnprotectData.argtypes = [ctypes.POINTER(DATA_BLOB), ctypes.POINTER(wintypes.LPWSTR),
        ctypes.POINTER(DATA_BLOB), ctypes.c_void_p, ctypes.c_void_p,
        wintypes.DWORD, ctypes.POINTER(DATA_BLOB)]
    crypt32.CryptUnprotectData.restype = wintypes.BOOL
    kernel32.LocalFree.argtypes = [ctypes.c_void_p]
    kernel32.LocalFree.restype = ctypes.c_void_p
    buf = ctypes.create_string_buffer(data)
    inp = DATA_BLOB(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_byte)))
    out = DATA_BLOB()
    func = crypt32.CryptUnprotectData if decrypt else crypt32.CryptProtectData
    if decrypt:
        result = func(ctypes.byref(inp), None, None, None, None, 0x01, ctypes.byref(out))
    else:
        result = func(ctypes.byref(inp), 'AetherPort credentials', None, None, None, 0x01, ctypes.byref(out))
    if not result:
        raise OSError('Windows could not decrypt the credential vault for this user.')
    try:
        return ctypes.string_at(out.pbData, out.cbData)
    finally:
        kernel32.LocalFree(ctypes.cast(out.pbData, ctypes.c_void_p))


@dataclass(frozen=True)
class Archive:
    path: Path
    name: str
    modified: float
    size: int


def list_archives(folder: Path) -> list[Archive]:
    folder = folder.expanduser().resolve()
    if not folder.is_dir():
        return []
    result = []
    for file in folder.iterdir():
        if file.is_file() and not file.is_symlink() and file.suffix.lower() == '.zip':
            stat_result = file.stat()
            result.append(Archive(file, file.name, stat_result.st_mtime, stat_result.st_size))
    return sorted(result, key=lambda a: (-a.modified, a.name.lower(), str(a.path)))


def delete_archive(file: Path, folder: Path):
    folder = folder.expanduser().resolve(strict=True)
    # Direct child only; never follow symlinks or delete a directory/outside file.
    if file.parent.resolve(strict=True) != folder or file.is_symlink() or not file.is_file() or file.suffix.lower() != '.zip':
        raise ValueError('Only ZIP files directly inside the configured folder can be deleted.')
    file.unlink()


def _zip_entry_name(info: zipfile.ZipInfo) -> tuple[str, ...]:
    name = info.filename.replace('\\', '/')
    if name.startswith('/') or '\x00' in name or re.match(r'^[A-Za-z]:', name):
        raise ValueError('ZIP contains an unsafe absolute path.')
    parts = PurePosixPath(name).parts
    if any(p in ('', '.', '..') for p in name.split('/') if p != '') or not parts:
        raise ValueError('ZIP contains an unsafe relative path.')
    if any(c in p for p in parts for c in '<>:"|?*') or any(p.rstrip(' .') != p for p in parts):
        raise ValueError('ZIP contains an invalid Windows path.')
    if info.create_system == 3 and stat.S_ISLNK(info.external_attr >> 16):
        raise ValueError('ZIP contains symbolic links, which cannot be published safely.')
    return parts


def extract_publishable(archive: Path, destination: Path):
    file_count = 0
    total_uncompressed = 0
    with zipfile.ZipFile(archive) as zipped:
        for entry in zipped.infolist():
            parts = _zip_entry_name(entry)
            if any(p.casefold() in {n.casefold() for n in IGNORED_FILES} for p in parts) or any(p.casefold() == '.git' for p in parts) or parts[0].casefold() == '__macosx':
                continue
            if entry.is_dir():
                continue
            file_count += 1
            total_uncompressed += entry.file_size
            if file_count > 25000 or total_uncompressed > 3 * 1024**3:
                raise ValueError('Archive is too large to publish safely.')
            output = destination.joinpath(*parts)
            output.parent.mkdir(parents=True, exist_ok=True)
            with zipped.open(entry) as src, output.open('wb') as dst:
                shutil.copyfileobj(src, dst, length=1024 * 1024)
    children = [p for p in destination.iterdir() if p.name.casefold() not in {n.casefold() for n in IGNORED_ROOT}]
    if len(children) == 1 and children[0].is_dir() and children[0].name not in PROJECT_DIRS:
        root = children[0]
    else:
        root = destination
    if not any(f.is_file() for f in root.rglob('*')):
        raise ValueError('ZIP contains no publishable files.')
    return root


def _run_git(args: list[str], cwd: Path, environment: dict[str,str], progress=None) -> str:
    if shutil.which('git') is None:
        raise RuntimeError('Git for Windows was not found. Install Git for Windows and restart AetherPort.')
    # Capture output; strip lines to prevent a remote's exception accidentally showing sensitive URLs.
    process = subprocess.run(['git', '-c', 'credential.helper=', *args], cwd=str(cwd), env=environment,
                             capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=240, check=False,
                             creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    if process.returncode != 0:
        msg = re.sub(r'\x1b(?:\[[0-?]*[ -/]*[@-~]|\][^\x07\x1b]*(?:\x07|\x1b\\))', '',
                     process.stderr or process.stdout or 'Git operation failed').strip()
        token = environment.get('AETHERPORT_GIT_TOKEN', '')
        if token:
            msg = msg.replace(token, '[REDACTED]')
        raise RuntimeError(msg[-800:])
    return process.stdout if '-z' in args else process.stdout.strip()


def _identity(service: str, token: str) -> tuple[str,str]:
    if service == 'github':
        resp = requests.get('https://api.github.com/user', headers={'Authorization': f'Bearer {token}',
                             'Accept':'application/vnd.github+json'}, timeout=25)
        resp.raise_for_status()
        user = resp.json()
        login, account_id = str(user.get('login', '')), int(user.get('id') or 0)
        if user.get('type') != 'User' or not re.fullmatch(r'[A-Za-z0-9_-]+', login) or account_id <= 0:
            raise ValueError('GitHub did not return a personal account identity.')
        name = str(user.get('name') or login).strip()[:200]
        return name, f'{account_id}+{login}@users.noreply.github.com'
    user = _huggingface_profile(token)
    username = user['name']
    if user.get('emailVerified') is False:
        raise ValueError(f'Hugging Face reports an unverified email for @{username}. Check that this saved token belongs to your verified account.')
    # An omitted status is unknown, not an explicit verification failure.
    if user.get('emailVerified') is not None and not isinstance(user['emailVerified'], bool):
        raise ValueError(f'Hugging Face returned an invalid email verification status for @{username}.')
    return str(user.get('fullname') or username)[:200], _huggingface_commit_email(user)


def _huggingface_profile(token: str) -> dict:
    resp = requests.get('https://huggingface.co/api/whoami-v2',
                        headers={'Authorization': f'Bearer {token}', 'Accept': 'application/json'}, timeout=25)
    if resp.status_code in (401, 403):
        raise ValueError(f'Hugging Face rejected this saved token ({resp.status_code}). Check its value and whether it is expired or revoked.')
    resp.raise_for_status()
    user = resp.json()
    if (not isinstance(user, dict) or user.get('type') != 'user' or
            not isinstance(user.get('name'), str) or not re.fullmatch(r'[A-Za-z0-9_-]+', user['name'])):
        raise ValueError('Hugging Face did not return a personal account identity for this token.')
    return user


def _huggingface_commit_email(user: dict) -> str:
    """The caller has already authenticated and validated the profile username."""
    email = user.get('email')
    if email is None or email == '':
        # Hub-supported profile attribution when the API hides personal email.
        # https://github.com/huggingface/hub-docs/issues/259
        return f"{user['name']}@users.noreply.huggingface.co"
    if not isinstance(email, str) or not re.fullmatch(r'[^\s<>@]+@[^\s<>@]+\.[^\s<>@]+', email):
        raise ValueError(f"Hugging Face did not return a usable commit email for @{user['name']}.")
    return email


def huggingface_token_status(token: str) -> str:
    """Show safe identity diagnostics; never include the token or account email."""
    user = _huggingface_profile(token)
    verified = user.get('emailVerified')
    verification = 'Yes' if verified is True else 'No' if verified is False else 'Not returned' if verified is None else 'Invalid'
    try:
        _huggingface_commit_email(user)
        email_status = 'Hub no-reply fallback' if user.get('email') in (None, '') else 'Present'
    except ValueError:
        email_status = 'Invalid'
    auth = user.get('auth')
    access = auth.get('accessToken') if isinstance(auth, dict) else None
    role = access.get('role') if isinstance(access, dict) else None
    role = role if role in ('read', 'write', 'fineGrained', 'god') else 'Not returned'
    return (f"Account: @{user['name']}\nToken role: {role}\n"
            f"Email verified: {verification}\nCommit email: {email_status}\n\n"
            'These details belong to the token saved in AetherPort. Compare the account with the one signed in to your browser. '
            'If Hugging Face hides your email, publishing uses its account-linked no-reply address. '
            'Repository write access is checked separately when publishing.')


def _git_environment(tmp: Path, token: str) -> dict[str,str]:
    env = os.environ.copy()
    # Credentials are passed through process environment, not command-line args or repo config.
    askpass = tmp / ('aetherport-askpass.cmd' if os.name == 'nt' else 'aetherport-askpass.sh')
    if os.name == 'nt':
        askpass.write_text('@echo off\r\necho %AETHERPORT_GIT_TOKEN%\r\n', encoding='utf-8')
    else:
        askpass.write_text('#!/bin/sh\nprintf "%s\\n" "$AETHERPORT_GIT_TOKEN"\n', encoding='utf-8')
        askpass.chmod(0o700)
    env.update(GIT_ASKPASS=str(askpass), GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='never', AETHERPORT_GIT_TOKEN=token,
               GIT_LFS_SKIP_SMUDGE='1')
    return env


def _safe_remove_repo_contents(repo_dir: Path):
    for path in repo_dir.iterdir():
        if path.name == '.git':
            continue
        if path.is_symlink() or path.is_file():
            path.unlink()
        elif path.is_dir():
            shutil.rmtree(path)


def _normalize_hf_readme(root: Path) -> None:
    """Fix only known rejected decorative stars in the staged Space card, never the ZIP."""
    readme = root / 'README.md'
    if not readme.is_file():
        return
    raw = readme.read_bytes()
    try:
        text = raw.decode('utf-8')
    except UnicodeDecodeError:
        return
    opening = re.match(r'\A\ufeff?---\r?\n', text)
    if not opening:
        return
    closing = re.compile(r'^---[ \t]*\r?(?:\n|$)', re.MULTILINE).search(text, opening.end())
    if not closing:
        return
    metadata = text[opening.end():closing.start()]
    fixed = re.sub(r"(?m)^(emoji:[ \t]*)(['\"]?)[✦✧]\2(?=[ \t]*(?:#[^\r\n]*)?\r?$)",
                   lambda match: match[1] + match[2] + '✨' + match[2], metadata)
    if fixed != metadata:
        readme.write_bytes((text[:opening.end()] + fixed + text[closing.start():]).encode('utf-8'))


def publish_zip(archive: Path, service: str, repo: str, token: str,
                branch: str, message: str, progress=None) -> str:
    repo = normalize_repo(repo, service)
    branch = validate_branch(branch)
    message = message.strip()[:240] or 'Small bug fixes'
    if not archive.is_file():
        raise ValueError('Select an existing ZIP file.')
    if progress: progress('Validating archive and account…')
    name, email = _identity(service, token)
    with tempfile.TemporaryDirectory(prefix='aetherport-') as tmp_str:
        tmp = Path(tmp_str)
        env = _git_environment(tmp, token)
        extracted = tmp / 'extracted'
        extracted.mkdir()
        root = extract_publishable(archive, extracted)
        work = tmp / 'repo'
        host = 'github.com' if service == 'github' else 'huggingface.co'
        suffix = repo if service == 'github' else 'spaces/' + repo
        user = 'x-access-token' if service == 'github' else repo.split('/')[0]
        url = f'https://{quote(user)}@{host}/{suffix}.git'
        if progress: progress(f'Checking branches in {repo}…')
        refs = _run_git(['ls-remote', '--symref', url, 'HEAD', 'refs/heads/*'], tmp, env).splitlines()
        heads = {line.split('\t', 1)[1][len('refs/heads/'):] for line in refs
                 if '\trefs/heads/' in line}
        default = next((line.split()[1][len('refs/heads/'):] for line in refs
                        if line.startswith('ref: refs/heads/') and line.endswith('\tHEAD')), None)
        if heads:
            base = branch if branch in heads else default if default in heads else sorted(heads)[0]
            if progress: progress(f'Cloning {repo}:{base}…')
            _run_git(['clone', '--single-branch', '--branch', base, '--depth', '1', url, str(work)], tmp, env)
            if branch != base:
                _run_git(['checkout', '-b', branch], work, env)
        else:
            if progress: progress(f'Preparing first commit on {branch}…')
            _run_git(['init', f'--initial-branch={branch}', str(work)], tmp, env)
            _run_git(['remote', 'add', 'origin', url], work, env)
        _safe_remove_repo_contents(work)
        if progress: progress('Copying ZIP contents…')
        for item in root.iterdir():
            dest = work / item.name
            if item.is_dir(): shutil.copytree(item, dest)
            else: shutil.copy2(item, dest)
        if service != 'github':
            _normalize_hf_readme(work)
        _run_git(['add', '-A'], work, env)
        changes = _run_git(['diff', '--cached', '--name-status', '--no-renames', '-z'], work, env).split('\0')[:-1]
        files = sorted(zip(changes[::2], changes[1::2]), key=lambda item: (item[0] != 'D', item[1]))
        if not files:
            if branch not in heads:
                _run_git(['push', 'origin', f'HEAD:refs/heads/{branch}'], work, env)
                return f'Created branch {repo}:{branch}. No file changes were needed.'
            return 'No changes found. Nothing was pushed.'
        # Unstage the batch; deletions go first so file/directory replacements remain one path per commit.
        if heads:
            _run_git(['reset', '--mixed', 'HEAD'], work, env)
        else:
            _run_git(['read-tree', '--empty'], work, env)
        for number, (status, path) in enumerate(files, 1):
            if progress: progress(f'Creating commit {number}/{len(files)}: {path}')
            literal = ':(literal)' + path
            if status == 'D': _run_git(['rm', '--cached', '--', literal], work, env)
            else: _run_git(['add', '--', literal], work, env)
            _run_git(['-c', f'user.name={name}', '-c', f'user.email={email}', 'commit', '-m', f'{message}: {path}'], work, env)
        if _run_git(['status', '--porcelain'], work, env):
            raise RuntimeError('Some file changes were not committed. Nothing was pushed.')
        if progress: progress(f'Pushing to {branch}…')
        _run_git(['push', 'origin', f'HEAD:refs/heads/{branch}'], work, env)
        commit = _run_git(['rev-parse', '--short=8', 'HEAD'], work, env)
        return f'Published {len(files)} file commits to {repo}:{branch}. Last commit {commit}.'


def download_hf(repo: str, token: str, branch: str, output: Path, progress=None) -> Path:
    repo = normalize_repo(repo, 'hf')
    branch = validate_branch(branch)
    if output.suffix.lower() != '.zip':
        raise ValueError('Choose a .zip destination.')
    with tempfile.TemporaryDirectory(prefix='aetherport-hf-') as tmp_str:
        tmp = Path(tmp_str)
        env = _git_environment(tmp, token)
        user = quote(repo.split('/')[0])
        url = f'https://{user}@huggingface.co/spaces/{repo}.git'
        if progress: progress('Downloading Hugging Face Space…')
        work = tmp/'repo'
        _run_git(['clone', '--single-branch', '--branch', branch, '--depth', '1', url, str(work)], tmp, env)
        output.parent.mkdir(parents=True, exist_ok=True)
        temp_zip = output.with_suffix('.zip.part')
        try:
            with zipfile.ZipFile(temp_zip, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=5) as z:
                for f in work.rglob('*'):
                    if f.is_file() and '.git' not in f.relative_to(work).parts and not f.is_symlink():
                        z.write(f, f.relative_to(work).as_posix())
            os.replace(temp_zip, output)
        finally:
            temp_zip.unlink(missing_ok=True)
    return output


def mask(value: str) -> str:
    if len(value) <= 4: return '••••'
    if len(value) <= 8: return value[:1] + '••••' + value[-1:]
    return value[:3] + '••••••' + value[-3:]
