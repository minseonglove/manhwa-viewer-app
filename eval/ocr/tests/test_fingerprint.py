from PIL import Image

from ocr_eval.fingerprint import gt_fingerprint
from ocr_eval.gt import GtItem, GtPage


def _page(bbox=(10, 10, 50, 100), text="あ"):
    return GtPage(page="p1", width=100, height=100, items=(
        GtItem("t01", bbox, "bubble", "vertical", text),
    ))


def _pages_dir(tmp_path, color="white"):
    Image.new("RGB", (100, 100), color).save(tmp_path / "p1.jpg")
    return tmp_path


def test_same_pages_produce_same_fingerprint(tmp_path):
    pages_dir = _pages_dir(tmp_path)
    assert gt_fingerprint([_page()], pages_dir) == gt_fingerprint([_page()], pages_dir)


def test_bbox_change_produces_different_fingerprint(tmp_path):
    pages_dir = _pages_dir(tmp_path)
    a = gt_fingerprint([_page(bbox=(10, 10, 50, 100))], pages_dir)
    b = gt_fingerprint([_page(bbox=(20, 10, 50, 100))], pages_dir)
    assert a != b


def test_text_change_does_not_affect_fingerprint(tmp_path):
    pages_dir = _pages_dir(tmp_path)
    a = gt_fingerprint([_page(text="あ")], pages_dir)
    b = gt_fingerprint([_page(text="いう")], pages_dir)
    assert a == b


def test_image_bytes_change_produces_different_fingerprint(tmp_path):
    dir_a = tmp_path / "a"
    dir_a.mkdir()
    dir_b = tmp_path / "b"
    dir_b.mkdir()
    Image.new("RGB", (100, 100), "white").save(dir_a / "p1.jpg")
    Image.new("RGB", (100, 100), "black").save(dir_b / "p1.jpg")
    assert gt_fingerprint([_page()], dir_a) != gt_fingerprint([_page()], dir_b)
