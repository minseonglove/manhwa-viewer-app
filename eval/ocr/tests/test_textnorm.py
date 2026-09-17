from ocr_eval.textnorm import lenient, strict


def test_strict_removes_only_whitespace():
    assert strict("こ ん\nにち\tは！") == "こんにちは！"


def test_lenient_applies_nfkc():
    assert lenient("ＯＫ！？１２") == "OK!?12"


def test_lenient_unifies_ellipsis_variants():
    assert lenient("え……") == lenient("え...") == lenient("え・・・") == lenient("え‥") == "え…"


def test_lenient_keeps_single_middle_dot():
    assert lenient("ア・イ") == "ア・イ"


def test_lenient_unifies_wave_dash():
    assert lenient("ねえ〜") == lenient("ねえ～") == "ねえ~"


def test_lenient_removes_decorative_symbols():
    assert lenient("好き♡だよ★♪") == "好きだよ"
