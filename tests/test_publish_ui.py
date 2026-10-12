"""Exercise publish button callbacks without opening windows or using accounts."""
from types import SimpleNamespace
from unittest.mock import Mock

import pytest

from aetherport import app


class Widget:
    def __init__(self, *args, **kwargs):
        pass

    def pack(self, **kwargs):
        pass


@pytest.fixture
def controls(monkeypatch):
    buttons = {}
    fields = {}
    interpreter = app.tk.Tcl()
    string_var = app.tk.StringVar
    monkeypatch.setattr(app.tk, 'StringVar', lambda **kw: string_var(interpreter, **kw))
    monkeypatch.setattr(app.tk, 'Frame', Widget)
    monkeypatch.setattr(app.ttk, 'Combobox', Widget)
    monkeypatch.setattr(app, 'label', lambda *args, **kw: Widget())

    def button(parent, text, command, **kwargs):
        buttons[text] = command
        return Widget()

    monkeypatch.setattr(app, 'Button', button)
    monkeypatch.setattr(app, 'field_line', lambda parent, title, variable: fields.update({title: variable}))
    monkeypatch.setattr(app.messagebox, 'showerror', Mock())
    monkeypatch.setattr(app.messagebox, 'askyesno', Mock(return_value=True))
    return buttons, fields


@pytest.mark.parametrize('hf', [False, True])
def test_publish_button_chooses_repository_before_upload(controls, monkeypatch, tmp_path, hf):
    buttons, fields = controls
    archive = tmp_path / 'project.zip'
    archive.write_bytes(b'fixture')
    vault = SimpleNamespace(hf_tokens=lambda: ['HF_TOKEN'], all=lambda: {'HF_TOKEN': 'hf-test'},
                            github_token=Mock(return_value='gh-test'))
    window = SimpleNamespace(card=lambda: Widget(), _archive_field=lambda box: None,
                             active_zip=archive, busy=False, vault=vault,
                             _repository_dialog=Mock(), _task=Mock(), _progress=Mock())
    publish_zip = Mock()
    monkeypatch.setattr(app, 'publish_zip', publish_zip)
    app.AetherPortWindow._publish_ui(window, hf)
    fields['Branch'].set('release')
    fields['Commit message'].set('Updated project')
    buttons['Publish to Hugging Face' if hf else 'Publish to GitHub']()
    window._repository_dialog.assert_called_once()
    assert window._repository_dialog.call_args.args[0] == hf
    assert window._repository_dialog.call_args.kwargs == {'branch': 'release'}
    window._task.assert_not_called()
    app.messagebox.askyesno.assert_not_called()
    choose = window._repository_dialog.call_args.args[1]
    choose('Example/Project')
    app.messagebox.askyesno.assert_called_once()
    assert 'Example/Project:release' in app.messagebox.askyesno.call_args.args[1]
    window._task.call_args.args[0]()
    publish_zip.assert_called_once_with(archive, 'hf' if hf else 'github', 'Example/Project',
                                       'hf-test' if hf else 'gh-test', 'release',
                                       'Updated project', window._progress)
    window._task.reset_mock()
    app.messagebox.askyesno.return_value = False
    choose('Example/Project')
    window._task.assert_not_called()


@pytest.mark.parametrize('state', ['missing_zip', 'invalid_branch', 'busy'])
def test_publish_setup_does_not_open_chooser(controls, tmp_path, state):
    buttons, fields = controls
    archive = tmp_path / 'project.zip'
    archive.write_bytes(b'fixture')
    window = SimpleNamespace(card=lambda: Widget(), _archive_field=lambda box: None,
                             active_zip=None if state == 'missing_zip' else archive,
                             busy=state == 'busy', _repository_dialog=Mock())
    app.AetherPortWindow._publish_ui(window)
    if state == 'invalid_branch': fields['Branch'].set('../main')
    buttons['Publish to GitHub']()
    window._repository_dialog.assert_not_called()


@pytest.mark.parametrize('hf', [False, True])
@pytest.mark.parametrize('repositories', [[], ['Example/Project', 'Example/Other']])
def test_repository_chooser_select_cancel_and_manage(controls, monkeypatch, hf, repositories):
    buttons, _ = controls
    dialog = SimpleNamespace(body=Widget(), destroy=Mock())
    monkeypatch.setattr(app, 'Modal', lambda *args, **kw: dialog)
    monkeypatch.setattr(app, 'ScrollBox', lambda *args: SimpleNamespace(
        inner=Widget(), pack=lambda **kw: None, bind_wheel=lambda: None))
    window = SimpleNamespace(vault=SimpleNamespace(repositories=lambda value: repositories), show_page=Mock())
    callback = Mock(side_effect=lambda repo: dialog.destroy.assert_called_once())
    app.AetherPortWindow._repository_dialog(window, hf, callback, branch='main')
    buttons['Cancel']()
    callback.assert_not_called()
    dialog.destroy.reset_mock()
    buttons['Manage in Variables']()
    window.show_page.assert_called_once_with('Variables')
    callback.assert_not_called()
    if repositories:
        dialog.destroy.reset_mock()
        buttons[repositories[-1]]()
        callback.assert_called_once_with(repositories[-1])
