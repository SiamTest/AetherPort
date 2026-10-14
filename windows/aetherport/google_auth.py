"""Google Desktop OAuth flow; exports genuine google-auth token JSON and pickle."""
from __future__ import annotations
import pickle
from pathlib import Path

DEFAULT_SCOPES = 'https://www.googleapis.com/auth/drive.file'

def authorize(credentials_file: Path, scopes_text: str):
    try:
        from google_auth_oauthlib.flow import InstalledAppFlow
    except ImportError as exc:
        raise RuntimeError('Google OAuth dependencies are missing from this build.') from exc
    scopes = list(dict.fromkeys(s for s in scopes_text.replace(',', ' ').split() if s))
    if not scopes:
        raise ValueError('Enter at least one OAuth scope.')
    import json
    content = json.loads(credentials_file.read_text(encoding='utf-8'))
    if not isinstance(content.get('installed'), dict):
        raise ValueError('Choose credentials.json from a Google Desktop OAuth client.')
    flow = InstalledAppFlow.from_client_secrets_file(str(credentials_file), scopes=scopes)
    credentials = flow.run_local_server(host='127.0.0.1', port=0, open_browser=True,
                                        authorization_prompt_message='Complete Google sign-in in your browser.',
                                        timeout_seconds=180)
    if not credentials.refresh_token:
        raise ValueError('Google did not issue a refresh token; revoke an earlier grant and try again.')
    return credentials.to_json().encode('utf-8'), pickle.dumps(credentials, protocol=4)
