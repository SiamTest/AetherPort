"""Guard Windows release version alignment across all outputs."""
from pathlib import Path
import re

from aetherport.core import VERSION

ROOT = Path(__file__).resolve().parents[1]


def test_windows_version_and_installer_are_in_sync():
    assert re.fullmatch(r"\d+\.\d+\.\d+", VERSION)
    iss = (ROOT / "installer/AetherPort.iss").read_text(encoding="utf-8")
    assert f'#define MyAppVersion "{VERSION}"' in iss
    assert 'OutputBaseFilename=AetherPort-Windows-Setup-v{#MyAppVersion}' in iss
    assert '#ifndef MyAppVersion' in iss


def test_windows_release_workflow_uses_core_version():
    workflow = (ROOT / ".github/workflows/windows-release.yml").read_text(encoding="utf-8")
    assert 'from aetherport.core import VERSION; print(VERSION)' in workflow
    assert '"APP_VERSION=$version" >> $env:GITHUB_ENV' in workflow
    assert '/DMyAppVersion=$env:APP_VERSION' in workflow
    assert 'AetherPort-Windows-${{ env.APP_VERSION }}' in workflow
    assert 'AetherPort-Windows-Setup-v${{ env.APP_VERSION }}.exe' in workflow
    assert 'AetherPort-Windows-Portable-v${{ env.APP_VERSION }}.zip' in workflow
    assert re.search(r"AetherPort-Windows-Setup-v3\.0\.15", workflow) is None


def test_windows_executable_resource_uses_current_version():
    from tools.generate_version_info import generate_version_info
    major, minor, patch = map(int, VERSION.split('.'))
    metadata = generate_version_info(VERSION)
    assert f'filevers=({major}, {minor}, {patch}, 0)' in metadata
    assert f'prodvers=({major}, {minor}, {patch}, 0)' in metadata
    assert f"StringStruct('FileVersion', '{VERSION}.0')" in metadata
    assert f"StringStruct('ProductVersion', '{VERSION}.0')" in metadata
    workflow = (ROOT / '.github/workflows/windows-release.yml').read_text()
    assert '--version-file .build/windows-version-info.txt' in workflow
