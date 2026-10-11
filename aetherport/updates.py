"""Windows release discovery and verified installer downloads."""
from __future__ import annotations
import hashlib
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlparse
import requests
from .core import VERSION, normalize_repo

RELEASE_TAG = 'windows-stable'
ASSET_PATTERN = re.compile(r'AetherPort-Windows-Setup-v(\d+\.\d+\.\d+)\.exe\Z', re.I)


def semver(value):
    return tuple(int(x) for x in value.split('.'))


def find_windows_update(repo, token=''):
    repo = normalize_repo(repo, 'github')
    headers = {'Accept':'application/vnd.github+json'}
    if token: headers['Authorization'] = f'Bearer {token}'
    response = requests.get(f'https://api.github.com/repos/{repo}/releases/tags/{RELEASE_TAG}',
                            headers=headers,timeout=25)
    if response.status_code == 404: return None
    response.raise_for_status()
    release = response.json()
    checksum_assets = [a for a in release.get('assets', []) if a.get('name') == 'SHA256SUMS.txt']
    checksums = {}
    if checksum_assets:
        resp = requests.get(checksum_assets[0]['url'],headers={**headers,'Accept':'application/octet-stream'},timeout=25)
        resp.raise_for_status()
        for line in resp.text.splitlines():
            parts = line.strip().split(maxsplit=1)
            if len(parts) == 2 and re.fullmatch('[0-9a-fA-F]{64}',parts[0]):
                checksums[parts[1].lstrip('*')] = parts[0].lower()
    options = []
    for asset in release.get('assets', []):
        name = asset.get('name','')
        match = ASSET_PATTERN.fullmatch(name)
        if match and semver(match.group(1)) > semver(VERSION):
            if name not in checksums:
                raise ValueError('Windows update has no matching SHA256 checksum; cannot install safely.')
            options.append({'version':match.group(1), 'name':name,
                            'url':asset.get('url'), 'size':asset.get('size'),
                            'sha256':checksums[name]})
    return sorted(options, key=lambda x:semver(x['version']), reverse=True)[0] if options else None


def download_installer(update, destination: Path, token='', progress=None):
    headers = {'Accept':'application/octet-stream'}
    if token: headers['Authorization'] = f'Bearer {token}'
    url = update['url']
    if not url.startswith('https://api.github.com/repos/'):
        raise ValueError('Unrecognized update location.')
    destination.parent.mkdir(parents=True,exist_ok=True)
    part = destination.with_suffix('.partial')
    try:
        with requests.get(url,headers=headers,stream=True,timeout=75) as response:
            response.raise_for_status()
            with part.open('wb') as output:
                size = 0
                for chunk in response.iter_content(1024*512):
                    if chunk:
                        size += len(chunk)
                        output.write(chunk)
                        if progress: progress(size)
        if size < 64 * 1024 or (update.get('size') and size != update['size']):
            raise ValueError('Installer download was incomplete.')
        expected_hash = update.get('sha256','')
        if not re.fullmatch('[0-9a-fA-F]{64}', expected_hash):
            raise ValueError('No verified SHA256 checksum available for this installer.')
        digest = hashlib.sha256()
        with part.open('rb') as downloaded:
            for block in iter(lambda:downloaded.read(1024*1024),b''):
                digest.update(block)
        if digest.hexdigest().lower() != expected_hash.lower():
            raise ValueError('The installer failed SHA256 integrity verification.')
        part.replace(destination)
        return destination
    finally:
        part.unlink(missing_ok=True)


def launch_installer(path: Path):
    if sys.platform != 'win32':
        raise RuntimeError('Windows installer can only be started on Windows.')
    if not path.is_file():
        raise FileNotFoundError(path)
    import os
    os.startfile(str(path))
