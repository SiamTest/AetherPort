"""Exercise release scripts locally with a fake APK and GitHub CLI; never publish."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap

root = Path(__file__).resolve().parents[1]
workflow = (root / '.github/workflows/build-android.yml').read_text()


def script(step):
    block = workflow.split(f'      - name: {step}\n', 1)[1].split('\n      - name:', 1)[0]
    return textwrap.dedent(block.split('        run: |\n', 1)[1])


for existing in (False, True):
    with tempfile.TemporaryDirectory() as directory:
        work = Path(directory)
        apk_dir = work / 'app/build/outputs/apk/debug'
        apk_dir.mkdir(parents=True)
        (apk_dir / 'app-debug.apk').write_bytes(b'local release-check fixture')
        (work / 'app/build.gradle.kts').write_text((root / 'app/build.gradle.kts').read_text())
        bin_dir = work / 'bin'
        bin_dir.mkdir()
        gh = bin_dir / 'gh'
        gh.write_text('''#!/usr/bin/env python3
import json, os, sys
with open(os.environ['GH_CALLS'], 'a') as log:
    log.write(json.dumps(sys.argv[1:]) + '\\n')
if sys.argv[1:3] == ['release', 'view']:
    sys.exit(0 if os.environ['EXISTING_RELEASE'] == '1' else 1)
''')
        gh.chmod(0o755)
        env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'],
                   BUILD_TYPE='debug', GITHUB_REPOSITORY='example/AetherPort',
                   GITHUB_SHA='0123456789abcdef', GITHUB_ENV=str(work / 'env'),
                   GH_CALLS=str(work / 'calls.jsonl'), EXISTING_RELEASE=str(int(existing)))
        for step in ('Prepare APK artifact', 'Publish GitHub Release'):
            subprocess.run(['bash', '-n'], input=script(step), text=True, check=True)
        subprocess.run(['bash', '-c', script('Prepare APK artifact')], cwd=work, env=env, check=True)
        manifest = json.loads((work / 'dist/aetherport-update.json').read_text())
        assert manifest['prerelease'] is False, manifest
        assert manifest['version'] == '3.0.9' and manifest['tag'] == 'v3.0.9', manifest
        assert manifest['apk'].startswith('AetherPort-Android-3.0.9'), manifest
        for line in (work / 'env').read_text().splitlines():
            key, value = line.split('=', 1)
            env[key] = value
        subprocess.run(['bash', '-c', script('Publish GitHub Release')], cwd=work, env=env, check=True)
        calls = [json.loads(line) for line in (work / 'calls.jsonl').read_text().splitlines()]
        assert [call[1] for call in calls] == (['view', 'upload', 'edit'] if existing else ['view', 'create'])
        release = calls[-1]
        assert '--latest' in release and '--prerelease=false' in release, release
        assert release[2] == env['RELEASE_TAG'] == manifest['tag']
        if existing:
            assert '--draft=false' in release
        upload = calls[1]
        assert 'dist/aetherport-update.json' in upload
        assert any(arg.endswith('.apk') for arg in upload)
        assert any(arg.endswith('.sha256') for arg in upload)

print('Stable-release scripts: manifest, new release and existing-release promotion passed.')
