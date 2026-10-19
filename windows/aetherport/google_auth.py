"""WZML-X-compatible Google credentials, generated locally with browser OAuth."""
from __future__ import annotations
import hashlib
import json
import pickle
from pathlib import Path
from .core import VariableVault, data_dir

WZML_SCOPES = (
    'https://www.googleapis.com/auth/drive',
    'https://www.googleapis.com/auth/youtube.upload',
    'https://www.googleapis.com/auth/youtube.readonly',
    'https://www.googleapis.com/auth/youtube.force-ssl',
)
DEFAULT_SCOPES = ' '.join(WZML_SCOPES)


def authorize(credentials_file: Path, scopes_text: str = DEFAULT_SCOPES,
              force_new: bool = False, cache: VariableVault | None = None):
    try:
        from google.auth.exceptions import RefreshError
        from google.auth.transport.requests import Request
        from google.oauth2.credentials import Credentials
        from google_auth_oauthlib.flow import InstalledAppFlow
    except ImportError as exc:
        raise RuntimeError('Google OAuth dependencies are missing from this build.') from exc
    scopes = list(dict.fromkeys(s for s in scopes_text.replace(',', ' ').split() if s))
    if not scopes or any(not (s in ('openid', 'email', 'profile') or
                             s.startswith('https://www.googleapis.com/auth/')) for s in scopes):
        raise ValueError('Choose at least one valid Google OAuth permission.')
    if credentials_file.stat().st_size > 64 * 1024:
        raise ValueError('credentials.json is too large.')
    content = json.loads(credentials_file.read_text(encoding='utf-8-sig'))
    config = content.get('installed') if isinstance(content, dict) else None
    if not isinstance(config, dict) or not config.get('client_id') or not config.get('client_secret'):
        raise ValueError('Choose credentials.json from a Google Desktop OAuth client.')
    if (config.get('auth_uri') not in ('https://accounts.google.com/o/oauth2/auth',
                                      'https://accounts.google.com/o/oauth2/v2/auth') or
            config.get('token_uri') != 'https://oauth2.googleapis.com/token'):
        raise ValueError('Use an unmodified Google Desktop credentials.json with Google OAuth endpoints.')
    cache = cache if cache is not None else VariableVault(data_dir()/'google-oauth')
    key = 'GOOGLE_' + hashlib.sha256((config['client_id'] + '\n' + '\n'.join(sorted(scopes))).encode()).hexdigest().upper()
    credentials = None
    saved = cache.all().get(key) if not force_new else None
    if saved:
        try:
            info = json.loads(saved)
            if info.get('client_id') == config['client_id'] and info.get('client_secret') == config['client_secret']:
                credentials = Credentials.from_authorized_user_info(info)
                if not credentials.has_scopes(scopes):
                    credentials = None
        except (ValueError, TypeError):
            credentials = None
    if credentials is not None and not credentials.valid:
        try:
            credentials.refresh(Request())
        except RefreshError:
            credentials = None
    if credentials is None:
        flow = InstalledAppFlow.from_client_config(content, scopes=scopes, autogenerate_code_verifier=True)
        credentials = flow.run_local_server(
            host='127.0.0.1', port=0, open_browser=True,
            authorization_prompt_message='Complete Google sign-in in your browser.',
            success_message='Authorization received. You can close this tab and return to AetherPort.',
            timeout_seconds=180, prompt='select_account consent', access_type='offline')
    if not credentials or not credentials.refresh_token or not credentials.valid:
        raise ValueError('Google sign-in did not produce usable refresh credentials. Try signing in again.')
    granted = credentials.granted_scopes if credentials.granted_scopes is not None else credentials.scopes
    if not set(scopes).issubset(granted or ()):
        raise ValueError('Some requested permissions were not approved. Sign in again and allow the selected permissions.')
    cache.put(key, credentials.to_json())
    return credentials.to_json().encode('utf-8'), pickle.dumps(credentials, protocol=4)
