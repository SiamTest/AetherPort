"""Local OAuth tests: mock Google/browser; export real google-auth credentials."""
import json
import pickle
from datetime import datetime, timedelta, timezone
from unittest.mock import Mock
import pytest
from google.auth.exceptions import RefreshError
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from aetherport import google_auth
from aetherport.core import VariableVault


def credentials(scopes=None, expired=False):
    return Credentials('access-test', refresh_token='refresh-test',
        token_uri='https://oauth2.googleapis.com/token', client_id='test.apps.googleusercontent.com',
        client_secret='secret-test', scopes=scopes or list(google_auth.WZML_SCOPES),
        expiry=datetime.now(timezone.utc).replace(tzinfo=None)+timedelta(hours=-1 if expired else 2))


@pytest.fixture
def setup(monkeypatch,tmp_path):
    path=tmp_path/'credentials.json'
    path.write_text(json.dumps({'installed': {'client_id':'test.apps.googleusercontent.com',
        'client_secret':'secret-test', 'auth_uri':'https://accounts.google.com/o/oauth2/auth',
        'token_uri':'https://oauth2.googleapis.com/token'}}))
    flow=Mock();flow.run_local_server.return_value=credentials()
    factory=Mock(return_value=flow)
    monkeypatch.setattr(InstalledAppFlow,'from_client_config',factory)
    return path,VariableVault(tmp_path/'cache'),factory,flow


def test_wzml_export_and_saved_token_reuse(setup):
    path,cache,factory,flow=setup
    info,blob=google_auth.authorize(path,cache=cache)
    loaded=pickle.loads(blob)  # Only bytes produced by this test.
    assert isinstance(loaded,Credentials) and loaded.valid
    assert loaded.refresh_token=='refresh-test' and loaded.expiry is not None
    assert set(loaded.scopes)==set(google_auth.WZML_SCOPES)
    assert json.loads(info)['token']=='access-test'
    args=flow.run_local_server.call_args.kwargs
    assert args['host']=='127.0.0.1' and args['port']==0
    assert args['prompt']=='select_account consent' and args['access_type']=='offline'
    assert set(factory.call_args.kwargs['scopes'])==set(google_auth.WZML_SCOPES)
    google_auth.authorize(path,cache=cache)
    assert factory.call_count==1
    assert b'refresh-test' not in cache.file.read_bytes()


def test_expired_token_refreshes_without_browser(setup,monkeypatch):
    path,cache,factory,flow=setup
    google_auth.authorize(path,cache=cache)
    key=next(iter(cache.all()));cache.put(key,credentials(expired=True).to_json())
    def refresh(self,request):
        self.token='refreshed-access'
        self.expiry=datetime.now(timezone.utc).replace(tzinfo=None)+timedelta(hours=2)
    monkeypatch.setattr(Credentials,'refresh',refresh)
    info,blob=google_auth.authorize(path,cache=cache)
    assert pickle.loads(blob).token==json.loads(info)['token']=='refreshed-access'
    assert factory.call_count==1


def test_revoked_token_and_force_new_sign_in(setup,monkeypatch):
    path,cache,factory,flow=setup
    google_auth.authorize(path,cache=cache)
    key=next(iter(cache.all()));cache.put(key,credentials(expired=True).to_json())
    def revoked(self,request):raise RefreshError('invalid_grant')
    monkeypatch.setattr(Credentials,'refresh',revoked)
    google_auth.authorize(path,cache=cache)
    google_auth.authorize(path,force_new=True,cache=cache)
    assert factory.call_count==3


@pytest.mark.parametrize('result',['timeout','missing-refresh','partial-scopes'])
def test_failure_preserves_saved_credentials(setup,result):
    path,cache,factory,flow=setup
    google_auth.authorize(path,cache=cache);before=cache.all()
    bad=credentials()
    if result=='timeout':bad=None
    elif result=='missing-refresh':bad._refresh_token=None
    else:bad._granted_scopes=['https://www.googleapis.com/auth/drive']
    flow.run_local_server.return_value=bad
    with pytest.raises(ValueError):google_auth.authorize(path,force_new=True,cache=cache)
    assert cache.all()==before


@pytest.mark.parametrize('change',['web','endpoint','empty-scopes'])
def test_invalid_setup_rejected_before_browser(setup,change):
    path,cache,factory,flow=setup
    content=json.loads(path.read_text())
    if change=='web':content={'web':content['installed']}
    elif change=='endpoint':content['installed']['token_uri']='https://example.com/token'
    path.write_text(json.dumps(content))
    with pytest.raises(ValueError):
        google_auth.authorize(path,'' if change=='empty-scopes' else google_auth.DEFAULT_SCOPES,cache=cache)
    factory.assert_not_called()
    assert not cache.all()
