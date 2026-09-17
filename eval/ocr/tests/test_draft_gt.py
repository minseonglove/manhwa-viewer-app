from ocr_eval.draft_gt import draft_items


def _proposals():
    return {
        "page": "p1", "width": 1125, "height": 1600,
        "text": [
            {"n": 1, "bbox": [800, 100, 60, 200], "label": "text", "score": 0.9, "sources": [{"source": "koharu"}]},
            {"n": 2, "bbox": [500, 100, 200, 40], "label": "text_free", "score": 0.8, "sources": [{"source": "ogkalu"}]},
            {"n": 3, "bbox": [100, 900, 150, 150], "label": "onomatopoeia", "score": 0.3, "sources": [{"source": "koharu"}]},
            {"n": 4, "bbox": [300, 500, 40, 120], "label": "text", "score": 0.5, "sources": [{"source": "koharu"}]},
        ],
        "bubbles": [{"source": "koharu", "score": 0.9, "bbox": [780, 80, 100, 240]}],
    }


def test_draft_items_guess_category_and_orientation():
    items = draft_items(_proposals())
    assert [(i.id, i.category, i.orientation) for i in items] == [
        ("t01", "bubble", "vertical"),       # koharu text inside bubble
        ("t02", "aside", "horizontal"),      # text_free, wide
        ("t03", "sfx", "vertical"),          # onomatopoeia, square -> vertical
        ("t04", "aside", "vertical"),        # koharu text outside any bubble
    ]
    assert all(i.text == "" and not i.unreadable for i in items)
    assert items[0].note == "n=1 label=text sources=koharu"
