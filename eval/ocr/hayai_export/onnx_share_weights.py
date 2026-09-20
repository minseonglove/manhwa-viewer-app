"""Point the decode graph's initializers at the prefill graph's bytes.

prefill and decode hold byte-identical copies of the whole decoder (62.11 MB of 62.11 MB).
ONNX has no multi-signature file the way LiteRT does, but external data is addressed by
(location, offset, length), so two models can reference one blob. This writes prefill's
tensors once and rewrites decode to read the same bytes.
"""

from __future__ import annotations

import hashlib
import shutil
import sys
from pathlib import Path

import onnx
from onnx import TensorProto

SRC = Path(sys.argv[1])
DST = Path(sys.argv[2])
GRAPHS = ("encoder", "prefill", "decode")
ASSETS = ("position_base.npy", "tokenizer.json")
BLOB = "hayai_decoder_shared.data"
# Small tensors stay inline: ONNX Runtime does shape inference before loading external data,
# so Slice/Reshape arguments must be readable from the model file itself.
MIN_EXTERNAL_BYTES = 4096


def digest(t: TensorProto) -> str:
    return hashlib.sha256(t.raw_data).hexdigest()


def main() -> int:
    if DST.exists():
        shutil.rmtree(DST)
    DST.mkdir(parents=True)
    shutil.copy(SRC / "hayai_encoder.onnx", DST / "hayai_encoder.onnx")

    prefill = onnx.load(str(SRC / "hayai_prefill.onnx"), load_external_data=False)
    decode = onnx.load(str(SRC / "hayai_decode.onnx"), load_external_data=False)

    # One blob holding every prefill initializer, each recorded by content hash.
    offsets: dict[str, tuple[int, int]] = {}
    blob = DST / BLOB
    written = 0
    with blob.open("wb") as fh:
        for t in prefill.graph.initializer:
            if len(t.raw_data) < MIN_EXTERNAL_BYTES:
                continue
            h = digest(t)
            if h not in offsets:
                offsets[h] = (fh.tell(), len(t.raw_data))
                fh.write(t.raw_data)
                written += len(t.raw_data)

    def point_at_blob(tensor: TensorProto) -> bool:
        if len(tensor.raw_data) < MIN_EXTERNAL_BYTES:
            return False
        spot = offsets.get(digest(tensor))
        if spot is None:
            return False
        offset, length = spot
        tensor.ClearField("raw_data")
        tensor.data_location = TensorProto.EXTERNAL
        del tensor.external_data[:]
        for key, value in (("location", BLOB), ("offset", str(offset)), ("length", str(length))):
            entry = tensor.external_data.add()
            entry.key, entry.value = key, value
        return True

    moved = {"prefill": 0, "decode": 0}
    kept = {"prefill": 0, "decode": 0}
    for name, model in (("prefill", prefill), ("decode", decode)):
        for t in model.graph.initializer:
            if point_at_blob(t):
                moved[name] += 1
            else:
                kept[name] += 1
        onnx.save(model, str(DST / f"hayai_{name}.onnx"))

    for asset in ASSETS:
        shutil.copy(SRC / asset, DST / asset)
    for g in GRAPHS:
        shutil.copy(SRC / f"hayai_{g}.io.json", DST / f"hayai_{g}.io.json")

    def mb(p: Path) -> float:
        return p.stat().st_size / 1e6

    total = sum(mb(DST / f"hayai_{g}.onnx") for g in GRAPHS) + mb(blob)
    print(f"  blob {BLOB}: {mb(blob):.1f} MB, {len(offsets)} unique tensors ({written/1e6:.1f} MB written)")
    for name in ("prefill", "decode"):
        print(f"  {name:8} {moved[name]} initializers share the blob, {kept[name]} stayed inline "
              f"-> {mb(DST / f'hayai_{name}.onnx'):.1f} MB")
    print(f"  encoder  {mb(DST / 'hayai_encoder.onnx'):.1f} MB (untouched)")
    print(f"  TOTAL    {total:.1f} MB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
