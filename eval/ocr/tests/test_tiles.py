from ocr_eval.tiles import pack


def test_pack_wraps_rows_by_max_width():
    positions = pack([(100, 50), (100, 80), (100, 40)], max_width=220, gap=10)
    assert positions == [(0, 0), (110, 0), (0, 90)]


def test_pack_places_oversized_tile_on_its_own_row():
    positions = pack([(50, 50), (500, 60), (50, 50)], max_width=300, gap=0)
    assert positions == [(0, 0), (0, 50), (0, 110)]
