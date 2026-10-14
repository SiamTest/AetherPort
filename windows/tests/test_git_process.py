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
