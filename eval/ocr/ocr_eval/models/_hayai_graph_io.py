"""The graph IO contract `hayai_ocr.litert.LitertOcrEngine.generate` assumes.

Shared by every Hayai adapter (CoreML, ONNX, ...). `generate` addresses every tensor
positionally and the 24 cache tensors all share one shape, so a graph whose IO happened to be
ordered differently would run to completion on the wrong layer's cache and show up only as a
worse CER. One copy of the check, so the backends cannot drift apart on what they accept.
"""

from __future__ import annotations

SOURCE_MODEL = "JustANormalTinkerer/hayai-ocr-v2"
REVISION = "4cf1398f8d9a56a2d4bd7c1d2fb7648773660a62"  # checkpoint the graphs were converted from
GRAPHS = ("encoder", "prefill", "decode")
N_LAYERS = 12
DECODE_POS_INPUT = 4  # index of `pos` in generate's decode input order

ENCODER_INPUTS = ("pixel_values", "attention_mask", "pos_embeds")
DECODE_SCALAR_INPUTS = ("token_ids", "cos", "sin", "mask", "write_mask")
DECODE_CACHE_INPUTS = tuple(
    [f"past_k_{i}" for i in range(N_LAYERS)] + [f"past_v_{i}" for i in range(N_LAYERS)]
)
CACHE_OUTPUTS = tuple(name.replace("past_", "new_", 1) for name in DECODE_CACHE_INPUTS)


def check_io_order(io: dict[str, dict]) -> None:
    """Refuse IO that `generate`'s positional addressing would silently misread."""
    enc_in, enc_out = tuple(io["encoder"]["inputs"]), tuple(io["encoder"]["outputs"])
    pre_out = tuple(io["prefill"]["outputs"])
    dec_in, dec_out = tuple(io["decode"]["inputs"]), tuple(io["decode"]["outputs"])
    expected_dec_in = DECODE_SCALAR_INPUTS + DECODE_CACHE_INPUTS
    expected_out = ("logits",) + CACHE_OUTPUTS

    if enc_in != ENCODER_INPUTS:
        raise ValueError(f"encoder inputs are {enc_in}, expected {ENCODER_INPUTS}")
    if len(enc_out) != 1:
        raise ValueError(f"encoder outputs are {enc_out}; generate reads exactly one")
    if dec_in != expected_dec_in:
        raise ValueError(f"decode inputs are {dec_in}, expected {expected_dec_in}")
    if pre_out != expected_out:
        raise ValueError(f"prefill outputs are {pre_out}, expected {expected_out}")
    if dec_out != expected_out:
        raise ValueError(f"decode outputs are {dec_out}, expected {expected_out}")
    if len(pre_out) - 1 != len(DECODE_CACHE_INPUTS):
        raise ValueError(
            f"prefill emits {len(pre_out) - 1} caches but decode takes {len(DECODE_CACHE_INPUTS)}"
        )


def pos_to_write_mask(pos: int, total: int):
    """The cache slot `pos` names, as the one-hot `(1,1,total,1)` the graphs take.

    `export_kv.DecodeDecoderKV` computes `arange(total) == pos` in-graph and selects with it;
    the converted graphs take the result instead, because that index arithmetic is what does
    not survive conversion.
    """
    import numpy as np

    pos = int(pos)
    if not 0 <= pos < total:
        raise ValueError(f"pos {pos} is outside the cache [0, {total}); no slot would be written")
    return (np.arange(total) == pos).reshape(1, 1, total, 1)
