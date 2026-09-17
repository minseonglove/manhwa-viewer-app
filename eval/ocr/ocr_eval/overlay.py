from PIL import Image, ImageDraw

GRID_MINOR = 50
GRID_MAJOR = 100


def draw_grid(image: Image.Image) -> Image.Image:
    canvas = image.convert("RGB").copy()
    draw = ImageDraw.Draw(canvas)
    for x in range(0, canvas.width, GRID_MINOR):
        major = x % GRID_MAJOR == 0
        draw.line([(x, 0), (x, canvas.height)], fill=(120, 170, 255) if major else (200, 220, 255), width=1)
        if major:
            draw.text((x + 2, 2), str(x), fill=(0, 90, 220))
    for y in range(0, canvas.height, GRID_MINOR):
        major = y % GRID_MAJOR == 0
        draw.line([(0, y), (canvas.width, y)], fill=(120, 170, 255) if major else (200, 220, 255), width=1)
        if major:
            draw.text((2, y + 2), str(y), fill=(0, 90, 220))
    return canvas
