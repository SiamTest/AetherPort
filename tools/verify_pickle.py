"""Unpickle only our generated test fixture, using the actual Google credentials class."""
import datetime
import pickle
import sys
from pathlib import Path
from google.oauth2.credentials import Credentials

obj = pickle.loads(Path(sys.argv[1]).read_bytes())
assert isinstance(obj, Credentials)
assert obj.refresh_token == "refresh-test"
assert obj.client_id == "client-test.apps.googleusercontent.com"
assert obj.client_secret == "secret-test"
assert obj.token_uri == "https://oauth2.googleapis.com/token"
assert obj.scopes == ["https://www.googleapis.com/auth/drive"]
if len(sys.argv) > 2 and sys.argv[2] == "full":
    assert obj.token == "access-test"
    assert obj.expiry == datetime.datetime(2100, 1, 2, 3, 4, 5, 123456)
    assert obj.valid
else:
    assert obj.token is None and obj.expiry is None and not obj.valid
print("Google credentials pickle compatibility: OK")

# Exercise google-auth's real refresh path with an offline token endpoint response.
import json
from urllib.parse import parse_qs
calls = []
def token_endpoint(url, method='GET', body=None, headers=None, **kwargs):
    assert url == 'https://oauth2.googleapis.com/token' and method == 'POST'
    form = parse_qs(body.decode())
    assert form['refresh_token'] == ['refresh-test']
    calls.append(form)
    return type('Response', (), {'status': 200, 'headers': {}, 'data': json.dumps({
        'access_token': 'refreshed-test', 'expires_in': 3600, 'token_type': 'Bearer',
        'scope': 'https://www.googleapis.com/auth/drive'}).encode()})()
obj.refresh(token_endpoint)
assert obj.valid and obj.token == 'refreshed-test' and len(calls) == 1
print('Google credentials refresh compatibility: OK')
