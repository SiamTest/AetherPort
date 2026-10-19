"""Offline end-to-end Git publishing: a local bare repository stands in for GitHub."""
import os
import subprocess
from pathlib import Path
from zipfile import ZipFile
import pytest
from aetherport import core


def git(args, cwd, env=None):
    output = subprocess.run(['git', *args], cwd=str(cwd), env=env, check=True,
                            capture_output=True, text=True, encoding='utf-8').stdout
    return output if '-z' in args else output.strip()


@pytest.mark.skipif(not __import__('shutil').which('git'),reason='Git not installed')
def test_publish_replaces_repository_tree_without_force_push(monkeypatch,tmp_path):
    bare=tmp_path/'hosting'/'ExampleOwner'/'ExampleProject.git'
    bare.parent.mkdir(parents=True)
    git(['init','--bare','--initial-branch=main',str(bare)],tmp_path)
    initial=tmp_path/'seed'; initial.mkdir()
    git(['init','--initial-branch=main'],initial)
    git(['config','user.name','Test'],initial);git(['config','user.email','test@example.com'],initial)
    (initial/'old.txt').write_text('remove me')
    git(['add','.'],initial);git(['commit','-m','first'],initial)
    git(['remote','add','origin',str(bare)],initial)
    git(['push','origin','main'],initial)
    archive=tmp_path/'project.zip'
    with ZipFile(archive,'w') as z:
        z.writestr('wrapper/.github/workflows/build.yml','hello')
        z.writestr('wrapper/README.md','expected')
    # Git URL rewrite maps https://x-access-token@github.com/... to a local file:// bare repo.
    location = str((tmp_path/'hosting').resolve()).replace('\\','/')
    prefix = ('file:///' + location if os.name == 'nt' else 'file://' + location) + '/'
    original_git_env = core._git_environment
    def custom_git_env(work,token):
        result=original_git_env(work,token)
        result.update(GIT_CONFIG_COUNT='1',GIT_CONFIG_KEY_0=f'url.{prefix}.insteadOf',
                      GIT_CONFIG_VALUE_0='https://x-access-token@github.com/')
        return result
    monkeypatch.setattr(core,'_git_environment',custom_git_env)
    monkeypatch.setattr(core,'_identity',lambda service,token:('ExampleOwner','123+ExampleOwner@users.noreply.github.com'))
    result=core.publish_zip(archive,'github','ExampleOwner/ExampleProject','dummy-token','main','New release')
    assert 'Published 3 file commits' in result
    output=git(['--git-dir',str(bare),'ls-tree','-r','--name-only','main'],tmp_path)
    assert 'README.md' in output
    assert '.github/workflows/build.yml' in output
    assert 'old.txt' not in output
    assert 'wrapper/' not in output
    second=core.publish_zip(archive,'github','ExampleOwner/ExampleProject','dummy-token','main','New release')
    assert 'No changes found' in second


@pytest.fixture
def local_publish(monkeypatch, tmp_path):
    def setup(seed_files, service, initial_branch='main'):
        bare = tmp_path/'hosting'/'ExampleOwner'/'ExampleProject.git'
        bare.parent.mkdir(parents=True)
        git(['init', '--bare', f'--initial-branch={initial_branch}', str(bare)], tmp_path)
        git(['--git-dir', str(bare), 'config', 'core.longpaths', 'true'], tmp_path)
        seed = tmp_path/'seed'
        seed.mkdir()
        git(['init', f'--initial-branch={initial_branch}'], seed)
        git(['config', 'user.name', 'Initial author'], seed)
        git(['config', 'user.email', 'initial@example.com'], seed)
        for path, content in seed_files.items():
            file = seed/path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content, encoding='utf-8')
        initial = None
        if seed_files:
            git(['add', '-A'], seed)
            git(['commit', '-m', 'Initial'], seed)
            initial = git(['rev-parse', 'HEAD'], seed)
            git(['remote', 'add', 'origin', str(bare)], seed)
            git(['push', 'origin', initial_branch], seed)
        location = (tmp_path/'hosting').resolve().as_posix()
        prefix = ('file:///' if os.name == 'nt' else 'file://') + location + '/'
        original_env = core._git_environment
        def environment(work, token):
            result = original_env(work, token)
            source = 'https://x-access-token@github.com/' if service == 'github' else 'https://ExampleOwner@huggingface.co/spaces/'
            result.update(GIT_CONFIG_COUNT='1', GIT_CONFIG_KEY_0=f'url.{prefix}.insteadOf',
                          GIT_CONFIG_VALUE_0=source)
            return result
        monkeypatch.setattr(core, '_git_environment', environment)
        monkeypatch.setattr(core, '_identity', lambda service, token: ('Token Owner', '123+owner@users.noreply.github.com'))
        return bare, initial
    return setup


def write_archive(path, files):
    with ZipFile(path, 'w') as zipped:
        for name, content in files.items():
            zipped.writestr('wrapper/' + name, content)


def published_paths(bare, initial, tmp_path):
    commits = git(['--git-dir', str(bare), 'rev-list', '--reverse', f'{initial}..main'], tmp_path).splitlines()
    paths = []
    for commit in commits:
        changed = git(['--git-dir', str(bare), 'diff-tree', '--no-commit-id', '--name-only',
                       '--no-renames', '-r', '-z', commit], tmp_path).split('\0')[:-1]
        assert len(changed) == 1, (commit, changed)
        identity = git(['--git-dir', str(bare), 'show', '-s', '--format=%an|%ae|%cn|%ce', commit], tmp_path)
        assert identity == 'Token Owner|123+owner@users.noreply.github.com|Token Owner|123+owner@users.noreply.github.com'
        subject = git(['--git-dir', str(bare), 'show', '-s', '--format=%s', commit], tmp_path)
        assert subject == 'Update ZIP: ' + changed[0]
        paths.extend(changed)
    return paths


@pytest.mark.parametrize('service', ['github', 'hf'])
def test_fifty_changed_files_create_fifty_single_file_commits(local_publish, tmp_path, service):
    names = [' leading space.txt', 'bracket[1].txt', 'unicodé.txt', '--option.txt'] + [f'file-{n:02}.txt' for n in range(46)]
    bare, initial = local_publish({**{name: 'old' for name in names}, 'unchanged.txt': 'same'}, service)
    archive = tmp_path/'project.zip'
    write_archive(archive, {**{name: 'new' for name in names}, 'unchanged.txt': 'same'})
    result = core.publish_zip(archive, service, 'ExampleOwner/ExampleProject', 'dummy', 'main', 'Update ZIP')
    assert 'Published 50 file commits' in result
    assert set(published_paths(bare, initial, tmp_path)) == set(names)
    assert git(['--git-dir', str(bare), 'rev-list', '--count', 'main'], tmp_path) == '51'
    result = core.publish_zip(archive, service, 'ExampleOwner/ExampleProject', 'dummy', 'main', 'Update ZIP')
    assert 'No changes found' in result
    assert git(['--git-dir', str(bare), 'rev-list', '--count', 'main'], tmp_path) == '51'


def test_renames_and_file_directory_replacements_remain_single_path_commits(local_publish, tmp_path):
    before = {'file-to-dir': 'old', 'dir-to-file/child.txt': 'old', 'old-name.txt': 'same'}
    after = {'file-to-dir/child.txt': 'new', 'dir-to-file': 'new', 'new-name.txt': 'same'}
    bare, initial = local_publish(before, 'github')
    archive = tmp_path/'project.zip'
    write_archive(archive, after)
    result = core.publish_zip(archive, 'github', 'ExampleOwner/ExampleProject', 'dummy', 'main', 'Update ZIP')
    assert 'Published 6 file commits' in result
    assert published_paths(bare, initial, tmp_path) == sorted(before) + sorted(after)
    assert set(git(['--git-dir', str(bare), 'ls-tree', '-r', '--name-only', '-z', 'main'], tmp_path).split('\0')[:-1]) == set(after)


def test_commit_failure_does_not_push_a_partial_batch(local_publish, monkeypatch, tmp_path):
    bare, initial = local_publish({'a.txt': 'old', 'b.txt': 'old'}, 'github')
    archive = tmp_path/'project.zip'
    write_archive(archive, {'a.txt': 'new', 'b.txt': 'new'})
    original_run = core._run_git
    attempts = 0
    def fail_second_commit(args, *rest):
        nonlocal attempts
        if 'commit' in args:
            attempts += 1
            if attempts == 2: raise RuntimeError('Simulated commit failure')
        return original_run(args, *rest)
    monkeypatch.setattr(core, '_run_git', fail_second_commit)
    with pytest.raises(RuntimeError, match='Simulated commit failure'):
        core.publish_zip(archive, 'github', 'ExampleOwner/ExampleProject', 'dummy', 'main', 'Update ZIP')
    assert git(['--git-dir', str(bare), 'rev-parse', 'main'], tmp_path) == initial
    assert archive.is_file()


@pytest.mark.parametrize('service', ['github', 'hf'])
@pytest.mark.parametrize('initial_branch', ['main', 'master'])
def test_empty_repository_publishes_first_single_file_commits(local_publish, tmp_path, service, initial_branch):
    bare, initial = local_publish({}, service, initial_branch)
    archive = tmp_path/'project.zip'
    write_archive(archive, {'README.md': 'new', 'src/app.py': 'app'})
    result = core.publish_zip(archive, service, 'ExampleOwner/ExampleProject', 'dummy', 'main', 'Update ZIP')
    assert 'Published 2 file commits' in result
    commits = git(['--git-dir', str(bare), 'rev-list', '--reverse', 'main'], tmp_path).splitlines()
    assert len(commits) == 2
    for commit in commits:
        paths = git(['--git-dir', str(bare), 'diff-tree', '--root', '--no-commit-id',
                     '--name-only', '-r', '-z', commit], tmp_path).split('\0')[:-1]
        assert len(paths) == 1
        identity = git(['--git-dir', str(bare), 'show', '-s', '--format=%an|%ae', commit], tmp_path)
        assert identity == 'Token Owner|123+owner@users.noreply.github.com'
    assert git(['--git-dir', str(bare), 'show', 'main:README.md'], tmp_path) == 'new'
    assert 'No changes found' in core.publish_zip(archive, service, 'ExampleOwner/ExampleProject', 'dummy', 'main', 'Update ZIP')


@pytest.mark.parametrize('service', ['github', 'hf'])
@pytest.mark.parametrize('changed', [False, True])
def test_missing_branch_preserves_default_branch_and_its_history(local_publish, tmp_path, service, changed):
    bare, initial = local_publish({'README.md': 'old'}, service, 'master')
    archive = tmp_path/'project.zip'
    write_archive(archive, {'README.md': 'new' if changed else 'old'})
    core.publish_zip(archive, service, 'ExampleOwner/ExampleProject', 'dummy', 'main', 'Update ZIP')
    assert git(['--git-dir', str(bare), 'rev-parse', 'master'], tmp_path) == initial
    assert git(['--git-dir', str(bare), 'show', 'master:README.md'], tmp_path) == 'old'
    git(['--git-dir', str(bare), 'merge-base', '--is-ancestor', initial, 'main'], tmp_path)
    assert git(['--git-dir', str(bare), 'show', 'main:README.md'], tmp_path) == ('new' if changed else 'old')


@pytest.mark.parametrize('empty', [False, True])
def test_failed_new_branch_batch_does_not_create_a_remote_branch(local_publish, monkeypatch, tmp_path, empty):
    bare, initial = local_publish({} if empty else {'a.txt': 'old'}, 'github', 'master')
    archive = tmp_path/'project.zip'
    write_archive(archive, {'a.txt': 'new', 'b.txt': 'new'})
    original_run = core._run_git
    attempts = 0
    def fail_second_commit(args, *rest):
        nonlocal attempts
        if 'commit' in args:
            attempts += 1
            if attempts == 2:
                raise RuntimeError('Simulated commit failure')
        return original_run(args, *rest)
    monkeypatch.setattr(core, '_run_git', fail_second_commit)
    with pytest.raises(RuntimeError, match='Simulated commit failure'):
        core.publish_zip(archive, 'github', 'ExampleOwner/ExampleProject', 'dummy', 'main', 'Update ZIP')
    assert not git(['--git-dir', str(bare), 'branch', '--list', 'main'], tmp_path)
    if initial:
        assert git(['--git-dir', str(bare), 'rev-parse', 'master'], tmp_path) == initial
