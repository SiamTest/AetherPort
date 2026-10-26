"""Identity failures stay distinct without leaking tokens or personal email."""
from types import SimpleNamespace

import pytest

from aetherport import core


@pytest.fixture
def profile(monkeypatch):
    value = {'type': 'user', 'name': 'ExampleOwner', 'fullname': 'Token Owner',
             'email': 'owner@example.com', 'emailVerified': True,
             'auth': {'accessToken': {'role': 'fineGrained'}}}
    response = SimpleNamespace(status_code=200, raise_for_status=lambda: None, json=lambda: value)
    monkeypatch.setattr(core.requests, 'get', lambda *args, **kwargs: response)
    return value, response


def test_verified_fine_grained_profile_keeps_its_commit_identity(profile):
    assert core._identity('hf', 'dummy-secret') == ('Token Owner', 'owner@example.com')
    report = core.huggingface_token_status('dummy-secret')
    assert 'Account: @ExampleOwner' in report and 'Token role: fineGrained' in report
    assert 'Email verified: Yes' in report and 'Commit email: Present' in report
    assert 'dummy-secret' not in report and 'owner@example.com' not in report


@pytest.mark.parametrize('change, message, status', [
    ({'emailVerified': False}, 'unverified email for @ExampleOwner', 'Email verified: No'),
    ({'emailVerified': 'false'}, 'invalid email verification status', 'Email verified: Invalid'),
    ({'email': 'bad@example'}, 'did not return a usable commit email', 'Commit email: Invalid'),
])
def test_account_failures_are_specific_and_keep_validation(profile, change, message, status):
    profile[0].update(change)
    with pytest.raises(ValueError, match=message):
        core._identity('hf', 'dummy-secret')
    assert status in core.huggingface_token_status('dummy-secret')


@pytest.mark.parametrize('status', [401, 403])
def test_rejected_token_does_not_become_an_email_error(profile, status):
    profile[1].status_code = status
    with pytest.raises(ValueError, match=f'rejected this saved token \\({status}\\)'):
        core.huggingface_token_status('dummy-secret')


def test_malformed_profile_cannot_supply_a_commit_identity(profile):
    profile[0]['type'] = 'org'
    with pytest.raises(ValueError, match='personal account identity'):
        core._identity('hf', 'dummy-secret')


@pytest.mark.parametrize('null_status', [False, True])
def test_omitted_or_null_verification_is_unknown_not_unverified(profile, null_status):
    if null_status:
        profile[0]['emailVerified'] = None
    else:
        profile[0].pop('emailVerified')
    assert core._identity('hf', 'dummy-secret') == ('Token Owner', 'owner@example.com')
    assert 'Email verified: Not returned' in core.huggingface_token_status('dummy-secret')
    profile[0]['email'] = 'bad@example'
    with pytest.raises(ValueError, match='usable commit email'):
        core._identity('hf', 'dummy-secret')


@pytest.mark.parametrize('email', [None, ''])
def test_hidden_email_and_verification_use_authenticated_accounts_hub_noreply(profile, email):
    profile[0].pop('emailVerified')
    if email is None:
        profile[0].pop('email')
    else:
        profile[0]['email'] = email
    assert core._identity('hf', 'dummy-secret') == ('Token Owner', 'ExampleOwner@users.noreply.huggingface.co')
    report = core.huggingface_token_status('dummy-secret')
    assert 'Commit email: Hub no-reply fallback' in report
    assert 'Email verified: Not returned' in report
    assert 'dummy-secret' not in report
    profile[0]['emailVerified'] = False
    with pytest.raises(ValueError, match='unverified email'):
        core._identity('hf', 'dummy-secret')
