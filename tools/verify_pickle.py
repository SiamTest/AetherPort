import pickle
import sys
import types
from pathlib import Path

class Credentials:
    def __setstate__(self, state):
        self.__dict__.update(state)

Credentials.__module__ = "google.oauth2.credentials"

google = types.ModuleType("google")
oauth2 = types.ModuleType("google.oauth2")
credentials_mod = types.ModuleType("google.oauth2.credentials")
credentials_mod.Credentials = Credentials
google.oauth2 = oauth2
oauth2.credentials = credentials_mod
sys.modules["google"] = google
sys.modules["google.oauth2"] = oauth2
sys.modules["google.oauth2.credentials"] = credentials_mod

obj = pickle.loads(Path(sys.argv[1]).read_bytes())
assert isinstance(obj, Credentials)
assert obj.token is None
assert obj.expiry is None
assert obj._refresh_token == "refresh-test"
assert obj._client_id == "client-test.apps.googleusercontent.com"
assert obj._client_secret == "secret-test"
assert obj._token_uri == "https://oauth2.googleapis.com/token"
assert obj._scopes == ["https://www.googleapis.com/auth/drive"]
print("pickle smoke test: OK")
