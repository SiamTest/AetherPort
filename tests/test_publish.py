"""Offline end-to-end Git publishing: a local bare repository stands in for GitHub."""
import os
import subprocess
from pathlib import Path
from zipfile import ZipFile
import pytest
from aetherport import core


def git(args, cwd, env=None):
    return subprocess.run(['git', *args], cwd=str(cwd), env=env, check=True,
                          capture_output=True, text=True).stdout.strip()


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
    assert 'Published commit' in result
    output=git(['--git-dir',str(bare),'ls-tree','-r','--name-only','main'],tmp_path)
    assert 'README.md' in output
    assert '.github/workflows/build.yml' in output
    assert 'old.txt' not in output
    assert 'wrapper/' not in output
    second=core.publish_zip(archive,'github','ExampleOwner/ExampleProject','dummy-token','main','New release')
    assert 'No changes found' in second
