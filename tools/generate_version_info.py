"""Generate Windows EXE File/Product Version metadata for PyInstaller from the app version.

Run in the repository root: python tools/generate_version_info.py .build/windows-version-info.txt
"""
from __future__ import annotations
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from aetherport.core import VERSION  # noqa: E402


def generate_version_info(version: str) -> str:
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Expected a three-part Windows release version")
    major, minor, patch = map(int, version.split('.'))
    if any(not 0 <= x <= 65535 for x in (major, minor, patch)):
        raise ValueError("Windows VERSIONINFO components must fit 16 bits")
    return f'''VSVersionInfo(
  ffi=FixedFileInfo(
    filevers=({major}, {minor}, {patch}, 0),
    prodvers=({major}, {minor}, {patch}, 0),
    mask=0x3f,
    flags=0x0,
    OS=0x40004,
    fileType=0x1,
    subtype=0x0,
    date=(0, 0)
  ),
  kids=[
    StringFileInfo([
      StringTable('040904B0', [
        StringStruct('CompanyName', 'AetherPort'),
        StringStruct('FileDescription', 'AetherPort Windows'),
        StringStruct('FileVersion', '{version}.0'),
        StringStruct('InternalName', 'AetherPort'),
        StringStruct('OriginalFilename', 'AetherPort.exe'),
        StringStruct('ProductName', 'AetherPort'),
        StringStruct('ProductVersion', '{version}.0')
      ])
    ]),
    VarFileInfo([VarStruct('Translation', [1033, 1200])])
  ]
)
'''


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python tools/generate_version_info.py OUTPUT_FILE')
    target = Path(sys.argv[1])
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(generate_version_info(VERSION), encoding='utf-8')
    print(f'Windows executable version metadata: {VERSION}')


if __name__ == '__main__':
    main()
