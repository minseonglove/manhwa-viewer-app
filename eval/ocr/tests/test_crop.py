from PIL import Image

from ocr_eval.crop import crop_item, pad_box


def test_small_box_uses_min_padding():
    # max(20, 30) * 0.08 = 2.4 -> 2 -> clamp 4
    assert pad_box((100, 100, 20, 30), 1125, 1600) == (96, 96, 28, 38)


def test_medium_box_uses_ratio_padding():
    # max(100, 150) * 0.08 = 12
    assert pad_box((200, 300, 100, 150), 1125, 1600) == (188, 288, 124, 174)


def test_large_box_uses_max_padding():
    # max(300, 500) * 0.08 = 40 -> clamp 20
    assert pad_box((200, 300, 300, 500), 1125, 1600) == (180, 280, 340, 540)


def test_box_is_clamped_to_image_edges():
    # pad 12: x=5-12 -> 0, w=100+24=124 but limited to 1125-0
    assert pad_box((5, 1500, 100, 150), 1125, 1600) == (0, 1488, 124, 112)


def test_crop_item_returns_padded_region():
    image = Image.new("RGB", (1125, 1600), "white")
    crop = crop_item(image, (200, 300, 100, 150))
    assert crop.size == (124, 174)
