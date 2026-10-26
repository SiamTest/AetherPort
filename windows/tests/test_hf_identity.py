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
    ({'emailVerified': None}, 'did not return email verification status', 'Email verified: Not returned'),
    ({'email': None}, 'did not return a usable commit email', 'Commit email: Missing or invalid'),
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
