from aetherport.updates import semver,ASSET_PATTERN

def test_release_parsing():
    assert semver('3.0.15') < semver('3.0.16')
    assert ASSET_PATTERN.fullmatch('AetherPort-Windows-Setup-v3.0.15.exe')
    assert not ASSET_PATTERN.fullmatch('AetherPort-Android-3.0.15.apk')
