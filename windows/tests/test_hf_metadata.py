"""Only the reported invalid star emoji is normalized, in staged Space metadata."""
import pytest
from aetherport import core


@pytest.mark.parametrize('value', ['✦', "'✧'", '"✦"'])
def test_space_card_normalization_preserves_quotes_comments_bom_crlf_and_body(tmp_path, value):
    text = f'\ufeff---\r\ntitle: Keep ✦\r\nemoji: {value} # icon\r\nsdk: docker\r\n---\r\nBody ✧\r\nemoji: ✦\r\n'
    readme = tmp_path/'README.md'
    readme.write_bytes(text.encode())
    core._normalize_hf_readme(tmp_path)
    expected = text.replace(f'emoji: {value} # icon', f'emoji: {value.replace("✦", "✨").replace("✧", "✨")} # icon')
    assert readme.read_bytes() == expected.encode()
    core._normalize_hf_readme(tmp_path)
    assert readme.read_bytes() == expected.encode()


@pytest.mark.parametrize('data', [b'# Plain README\nemoji: \xe2\x9c\xa6\n',
    '---\nemoji: 🚀\n---\n'.encode(), '---\nemoji: ✨\n---\n'.encode(),
    "---\nemoji: '✦ demo'\n---\n".encode(), '---\nemoji: ✦\n'.encode(),
    '---\nemoji: ✦\n---\n'.encode() + b'\xff'])
def test_other_readme_contents_are_unchanged(tmp_path, data):
    readme = tmp_path/'README.md'
    readme.write_bytes(data)
    core._normalize_hf_readme(tmp_path)
    assert readme.read_bytes() == data
