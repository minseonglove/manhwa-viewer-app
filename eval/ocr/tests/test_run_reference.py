from PIL import Image

from ocr_eval.gt import GtItem, GtPage
from ocr_eval.run_reference import recognize_pages


def test_recognize_pages_uses_padded_crops_and_keys(tmp_path):
    Image.new("RGB", (1125, 1600), "white").save(tmp_path / "p1.jpg")
    pages = [GtPage(page="p1", width=1125, height=1600, items=(
        GtItem("t01", (200, 300, 100, 150), "bubble", "vertical", "あ"),
        GtItem("t02", (5, 1500, 100, 150), "sfx", "vertical", "", unreadable=True),
    ))]
    seen_sizes = []

    def fake_ocr(image):
        seen_sizes.append(image.size)
        return f"{image.width}x{image.height}"

    results = recognize_pages(pages, tmp_path, fake_ocr)
    assert seen_sizes == [(124, 174), (124, 112)]
    assert results["p1/t01"]["text"] == "124x174"
    assert set(results) == {"p1/t01", "p1/t02"}
    assert isinstance(results["p1/t01"]["ms"], int)
