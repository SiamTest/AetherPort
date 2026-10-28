"""Git subprocesses stay hidden while their results and errors remain available."""
import os
import subprocess
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from aetherport import core


@pytest.mark.parametrize('failed', [False, True])
def test_git_runs_without_a_console_and_preserves_output_and_error_redaction(monkeypatch, tmp_path, failed):
    monkeypatch.setattr(core.shutil, 'which', lambda command: 'git')
    run = Mock(return_value=SimpleNamespace(returncode=1 if failed else 0,
                                            stdout='commit-id\n', stderr='Failed using dummy-secret'))
    monkeypatch.setattr(core.subprocess, 'run', run)
    environment = {'AETHERPORT_GIT_TOKEN': 'dummy-secret'}
    if failed:
        with pytest.raises(RuntimeError, match=r'Failed using \[REDACTED\]'):
            core._run_git(['status'], tmp_path, environment)
    else:
        assert core._run_git(['status'], tmp_path, environment) == 'commit-id'
    assert run.call_args.kwargs['creationflags'] == (subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    assert run.call_args.kwargs['capture_output'] is True
    assert run.call_args.kwargs['env'] == environment


def test_git_errors_remove_terminal_codes_before_truncation_and_redact_secrets(monkeypatch, tmp_path):
    monkeypatch.setattr(core.shutil, 'which', lambda command: 'git')
    error = ("\x1b[31m" * 180 + "remote: Error: 'emoji' with value '✦' is invalid.\x1b[0m\n"
             "\x1b]8;;https://example.test/dummy-secret\x1b\\documentation\x1b]8;;\x1b\\\nToken: dummy-secret")
    monkeypatch.setattr(core.subprocess, 'run', lambda *args, **kwargs:
                        SimpleNamespace(returncode=1, stdout='', stderr=error))
    with pytest.raises(RuntimeError) as failure:
        core._run_git(['push'], tmp_path, {'AETHERPORT_GIT_TOKEN': 'dummy-secret'})
    message = str(failure.value)
    assert message.startswith("remote: Error: 'emoji' with value '✦'")
    assert '\x1b' not in message and '[31m' not in message
    assert 'dummy-secret' not in message and '[REDACTED]' in message
    assert 'documentation' in message
