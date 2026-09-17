def pack(sizes: list[tuple[int, int]], max_width: int, gap: int = 8) -> list[tuple[int, int]]:
    positions = []
    x = y = row_height = 0
    for width, height in sizes:
        if x > 0 and x + width > max_width:
            x = 0
            y += row_height + gap
            row_height = 0
        positions.append((x, y))
        x += width + gap
        row_height = max(row_height, height)
    return positions
