from PIL import Image

PADDING_RATIO = 0.08
MIN_PADDING_PX = 4
MAX_PADDING_PX = 20

Box = tuple[int, int, int, int]


def pad_box(box: Box, image_width: int, image_height: int) -> Box:
    """앱 YoloPostProcessor.addPadding 과 같은 여백 규칙. 모든 실행기가 같은 크롭을 보게 한다."""
    x, y, w, h = box
    pad = min(max(int(max(w, h) * PADDING_RATIO), MIN_PADDING_PX), MAX_PADDING_PX)
    px = max(x - pad, 0)
    py = max(y - pad, 0)
    pw = min(w + pad * 2, image_width - px)
    ph = min(h + pad * 2, image_height - py)
    return px, py, pw, ph


def crop_item(image: Image.Image, box: Box) -> Image.Image:
    x, y, w, h = pad_box(box, image.width, image.height)
    return image.crop((x, y, x + w, y + h))
