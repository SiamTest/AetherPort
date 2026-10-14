from pathlib import Path
from zipfile import ZipFile
import os
import struct
import time
import pytest
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from aetherport.core import (portable_backup_encrypt,portable_backup_decrypt,VariableVault,
                            list_archives,delete_archive,extract_publishable,normalize_repo,
                            validate_branch,repo_variable_name,repository_kind,mask,
                            MAGIC,ITERATIONS,validate_values)


def test_portable_backup_roundtrip():
    data={'GITHUB_TOKEN_SIAMTEST':'ghp_exampletoken', 'GITHUB_REPOSITORY_AETHERPORT':'SiamTest/AetherPort',
          'HF_TOKEN':'hf_exampletoken'}
    blob=portable_backup_encrypt(data,'sixteenlettersok')
    assert blob.startswith(b'AETHCRD1')
    assert len(blob)<1024
    assert portable_backup_decrypt(blob,'sixteenlettersok')==data
    assert portable_backup_encrypt(data,'sixteenlettersok') != blob
    with pytest.raises(ValueError,match='Wrong password'):
        portable_backup_decrypt(blob,'incorrectpassword')
    tampered=bytearray(blob);tampered[-1]^=1
    with pytest.raises(ValueError,match='Wrong password'):
        portable_backup_decrypt(bytes(tampered),'sixteenlettersok')


def test_android_binary_format_can_be_decrypted():
    # Independent Java-compatible DataOutputStream payload, manually encrypted with Java's constants.
    import hashlib
    entries={'GITHUB_TOKEN_SIAMTEST':'ghp_test', 'GITHUB_REPOSITORY_PROJECT':'SiamTest/AetherPort'}
    content=struct.pack('>II',1,len(entries))
    for name,value in entries.items():
        for field in (name,value):
            raw=field.encode('utf8');content+=struct.pack('>I',len(raw))+raw
    salt=bytes(range(16));iv=b'0123456789ab'
    key=hashlib.pbkdf2_hmac('sha256',b'sixteenlettersok',salt,210000,32)
    android_style=MAGIC+salt+iv+AESGCM(key).encrypt(iv,content,MAGIC)
    assert portable_backup_decrypt(android_style,'sixteenlettersok')==entries


def test_backup_rejects_invalid_and_overlong(tmp_path):
    with pytest.raises(ValueError):portable_backup_encrypt({'BAD':'xyz'},'short')
    with pytest.raises(ValueError):portable_backup_encrypt({'BAD-NAME':'xyz'},'sixteenlettersok')
    with pytest.raises(ValueError):portable_backup_decrypt(b'not a backup','sixteenlettersok')
    vault=VariableVault(tmp_path)
    vault.put('GITHUB_TOKEN_SIAMTEST','abc')
    incoming=portable_backup_encrypt({'GITHUB_TOKEN_SIAMTEST':'override','HF_TOKEN':'hf_abc'},'sixteenlettersok')
    assert vault.import_backup(incoming,'sixteenlettersok')==(1,1)
    assert vault.all()=={'GITHUB_TOKEN_SIAMTEST':'abc','HF_TOKEN':'hf_abc'}
    assert vault.import_backup(incoming,'sixteenlettersok',True)==(2,0)
    assert vault.all()['GITHUB_TOKEN_SIAMTEST']=='override'
    with pytest.raises(ValueError):vault.import_backup(incoming,'wrongpassword')
    assert vault.all()['GITHUB_TOKEN_SIAMTEST']=='override'
    assert b'override' not in vault.file.read_bytes() # vault encrypted at rest


def zipmake(path,files):
    with ZipFile(path,'w') as z:
        for name,data in files.items():z.writestr(name,data)


def test_zip_wrapper_is_removed(tmp_path):
    source=tmp_path/'wrapped.zip';dest=tmp_path/'out';dest.mkdir()
    zipmake(source,{'SampleProject/README.md':'hi','SampleProject/.github/workflows/main.yml':'abc'})
    root=extract_publishable(source,dest)
    assert root.name=='SampleProject'
    assert (root/'README.md').read_text()=='hi'
    assert (root/'.github/workflows/main.yml').read_text()=='abc'


def test_zip_preserves_project_root(tmp_path):
    source=tmp_path/'src-only.zip';dest=tmp_path/'out';dest.mkdir()
    zipmake(source,{'src/main.py':'hi'})
    root=extract_publishable(source,dest)
    assert root==dest
    assert (root/'src/main.py').is_file()


def test_zip_preserves_mixed_root(tmp_path):
    source=tmp_path/'mix.zip';dest=tmp_path/'out';dest.mkdir()
    zipmake(source,{'README.md':'hi','app/main.py':'hi'})
    root=extract_publishable(source,dest)
    assert root==dest


@pytest.mark.parametrize('member', ['../outside.txt','../../foo','/etc/passwd','C:/Windows/evil','folder/../bad'])
def test_archive_path_traversal_is_blocked(tmp_path,member):
    source=tmp_path/'evil.zip';dest=tmp_path/'out';dest.mkdir()
    zipmake(source,{member:'bad'})
    with pytest.raises(ValueError):extract_publishable(source,dest)
    assert not (tmp_path/'outside.txt').exists()


def test_symlink_zip_is_blocked(tmp_path):
    from zipfile import ZipInfo
    import stat
    source=tmp_path/'symlink.zip'
    with ZipFile(source,'w') as z:
        info=ZipInfo('bad');info.create_system=3;info.external_attr=(stat.S_IFLNK|0o777)<<16
        z.writestr(info,'../../etc')
    dest=tmp_path/'out';dest.mkdir()
    with pytest.raises(ValueError,match='symbolic links'):extract_publishable(source,dest)


def test_archive_sort_and_safe_delete(tmp_path):
    folder=tmp_path/'downloads';folder.mkdir()
    newer=folder/'new.zip';older=folder/'old.zip';outside=tmp_path/'outside.zip'
    for f in (older,newer,outside): f.write_bytes(b'zipmock')
    now=time.time()
    os.utime(older,(now-100,now-100))
    assert [f.name for f in list_archives(folder)]==['new.zip','old.zip']
    with pytest.raises(ValueError):delete_archive(outside,folder)
    assert outside.exists()
    delete_archive(newer,folder)
    assert not newer.exists()
    assert [f.name for f in list_archives(folder)]==['old.zip']


def test_repo_normalization_and_branches():
    assert normalize_repo('https://github.com/SiamTest/AetherPort.git','github')=='SiamTest/AetherPort'
    assert normalize_repo('https://huggingface.co/spaces/demo/test','hf')=='demo/test'
    assert repo_variable_name('AetherPort',False)=='GITHUB_REPOSITORY_AETHERPORT'
    assert repository_kind('HF_REPOSITORY_X')=='hf'
    assert validate_branch('feature/aether-port')=='feature/aether-port'
    for bad in ['../main','feat//bad','','main.lock','some ref','/foo']:
        with pytest.raises(ValueError):validate_branch(bad)


def test_token_matching(tmp_path):
    vault=VariableVault(tmp_path)
    vault.put('GITHUB_TOKEN_SIAMTEST','abc')
    vault.put('GITHUB_TOKEN_OTHER','other')
    assert vault.github_token('SiamTest/AetherPort')=='abc'
    with pytest.raises(ValueError):vault.github_token('NotStored/Other')


def test_zip_deletion_refuses_symlinks(tmp_path):
    folder=tmp_path/'downloads';folder.mkdir()
    target=tmp_path/'secret.zip';target.write_bytes(b'token')
    link=folder/'shortcut.zip'
    try:link.symlink_to(target)
    except (OSError, NotImplementedError):pytest.skip('Symlinks unavailable')
    with pytest.raises(ValueError):delete_archive(link,folder)
    assert target.exists()


def test_archive_skips_embedded_git_metadata(tmp_path):
    source=tmp_path/'source.zip';dest=tmp_path/'out';dest.mkdir()
    zipmake(source, {'wrapper/.GiT/config':'bad', 'wrapper/README.md':'good'})
    root=extract_publishable(source,dest)
    assert not (root/'.GiT').exists()
    assert (root/'README.md').read_text()=='good'
