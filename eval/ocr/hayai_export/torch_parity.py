"""Check the static rewrites against the pinned checkpoint's own inference path.

The reference is `HayaiModel.generate`'s static-KV-cache path (the code that
produced the torch fp32 baseline), not `export_kv.py`'s module definitions: at
checkpoint revision 4cf1398 `GroupedQueryAttention.forward` only transposes k/v
on the `kv_cache is not None` branch, so `export_kv.PrefillDecoderKV`'s
cache-less `layer(...)` call cannot run against this revision at all.

Every case carries a tolerance and a breach exits non-zero, so the table in the
report is reproducible from `torch_parity.log` rather than from a transcript.

Usage: torch_parity.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import torch
from PIL import Image

HERE = Path(__file__).resolve().parent
def _checkpoint_dir() -> Path:
    """The Hugging Face snapshot of the checkpoint: config.json, model.safetensors, modeling_hayai.py, tokenizer.json."""
    value = os.environ.get("HAYAI_CHECKPOINT_DIR")
    if not value:
        raise SystemExit("set HAYAI_CHECKPOINT_DIR to the hayai-ocr-v2 snapshot directory")
    return Path(value)


EXP = _checkpoint_dir()
LOG = HERE / "torch_parity.log"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))

from hayai_ocr.litert import compute_pos_embeds, preprocess_image_pil  # noqa: E402
from hayai_static import (  # noqa: E402
    MAX_PATCHES, PREFILL_LEN, TOTAL, DecodeStatic, EncoderStatic, PrefillStatic,
    load_hayai, rope_real, sample_inputs,
)
from ocr_eval.crop import crop_item  # noqa: E402
from ocr_eval.gt import load_pages, ready_pages  # noqa: E402
from ocr_eval.paths import GT_DIR, PAGES_DIR  # noqa: E402

# Per-case absolute tolerance, and why it is where it is. Every threshold is set
# from the value this case has already been measured at, with the headroom noted.
TOLERANCES = {
    "rope": (1e-05, "published complex form measured at 4.8e-07; fp32 reassociation noise only"),
    "encoder": (1e-05, "measured 0.0 -- the two paths are the same ops on the same inputs"),
    "encoder padded, mask only": (1e-05, "measured 0.0; isolates the one substituted line"),
    "encoder padded, full": (1e-03, "measured 7.4e-05, which is the host vs checkpoint "
                                    "position-embedding resize, not the rewrite"),
    "prefill": (1e-04, "measured 1.9e-05; fp32 SDPA reassociation over 12 layers"),
    "decode": (1e-04, "measured 1.2e-05; same"),
}

FAILURES: list[str] = []


class _Tee:
    """Keep the log this table is quoted from, without hiding the run."""

    def __init__(self, path: Path):
        self.file = path.open("w")
        self.stream = sys.__stdout__

    def write(self, text: str) -> int:
        self.file.write(text)
        return self.stream.write(text)

    def flush(self) -> None:
        self.file.flush()
        self.stream.flush()


def check(tag: str, worst: float) -> None:
    tol, why = TOLERANCES[tag]
    ok = worst <= tol
    print(f"[{tag:26}] max abs diff {worst:.3e}   tol {tol:.0e}   {'ok' if ok else 'BREACH'}   ({why})")
    if not ok:
        FAILURES.append(f"{tag}: {worst:.3e} > {tol:.0e}")


def check_outputs(tag: str, ref, got) -> None:
    ref = ref if isinstance(ref, tuple) else (ref,)
    got = got if isinstance(got, tuple) else (got,)
    if len(ref) != len(got):
        raise ValueError(f"{tag}: {len(ref)} reference outputs vs {len(got)}")
    check(tag, max((r - g).abs().max().item() for r, g in zip(ref, got)))


def ref_prefill(decoder, visual_features, text_token_ids, cos, sin):
    """Checkpoint `generate` prefill: static KV cache, cache_seqlens=0."""
    x = torch.cat([decoder.projector(visual_features), decoder.token_embeddings(text_token_ids)], dim=1)
    mask = decoder.generate_block_causal_mask(MAX_PATCHES, 1, x.device)
    kv_cache = {i: (torch.zeros(1, 2, TOTAL, 64), torch.zeros(1, 2, TOTAL, 64)) for i in range(12)}
    for i, layer in enumerate(decoder.layers):
        x = layer(x, mask=mask, cos_sin=(cos[:, :PREFILL_LEN, :], sin[:, :PREFILL_LEN, :]),
                  kv_cache=kv_cache, layer_idx=i, cache_seqlens=0)
    logits = decoder.output_head(decoder.final_norm(x[:, -1:, :])).squeeze(1)
    return tuple([logits] + [kv_cache[i][0] for i in range(12)] + [kv_cache[i][1] for i in range(12)])


def ref_decode(decoder, token_ids, cos, sin, pos, past_k, past_v):
    """Checkpoint `generate` decode step: static KV cache at cache_seqlens=pos, mask=None."""
    x = decoder.token_embeddings(token_ids)
    kv_cache = {i: (past_k[i].clone(), past_v[i].clone()) for i in range(12)}
    for i, layer in enumerate(decoder.layers):
        x = layer(x, mask=None, cos_sin=(cos, sin), kv_cache=kv_cache, layer_idx=i, cache_seqlens=pos)
    logits = decoder.output_head(decoder.final_norm(x)).squeeze(1)
    return tuple([logits] + [kv_cache[i][0] for i in range(12)] + [kv_cache[i][1] for i in range(12)])


def real_crop():
    page = ready_pages(load_pages(GT_DIR))[0]
    item = page.items[0]
    img = crop_item(Image.open(PAGES_DIR / f"{page.page}.jpg").convert("RGB"), item.bbox)
    return f"{page.page}/{item.id}", img


def main() -> int:
    sys.stdout = _Tee(LOG)
    torch.manual_seed(0)  # the random cases were unseeded, so no two runs produced the same table
    model = load_hayai(EXP)
    from modeling_hayai import apply_rotary_emb_2d  # noqa: PLC0415

    with torch.no_grad():
        worst_rope = 0.0
        for seq, heads in ((1, 8), (257, 2)):
            x = torch.randn(1, seq, heads, 64)
            c, s = torch.randn(1, seq, 32), torch.randn(1, seq, 32)
            worst_rope = max(worst_rope,
                             (rope_real(x, c, s, seq, heads) - apply_rotary_emb_2d(x, c, s)).abs().max().item())
        check("rope", worst_rope)

        smp = sample_inputs()
        vm = getattr(model.vision_encoder, "vision_model", model.vision_encoder)
        static_encoder = EncoderStatic(vm).eval()

        pv, am, _ = smp["encoder"]
        pos16 = vm.embeddings.position_embedding.weight.reshape(256, 768).unsqueeze(0)
        ref_enc = model.vision_encoder(pixel_values=pv, pixel_attention_mask=am,
                                       spatial_shapes=torch.tensor([[16, 16]])).last_hidden_state
        check_outputs("encoder", ref_enc, static_encoder(pv, am, pos16))

        # The case every real crop actually takes: fewer than 256 patches, so a
        # padded attention mask and a resized position embedding. The one line
        # EncoderStatic substitutes is the mask build, so the isolating case
        # holds the position embeddings equal on both sides; the full case then
        # shows what the host's own resize adds on top.
        crop_id, img = real_crop()
        pv_r, am_r, (ph, pw) = preprocess_image_pil(img)
        pos_base = vm.embeddings.position_embedding.weight.detach().numpy()
        pe = torch.from_numpy(compute_pos_embeds(pos_base, ph, pw))
        pv_r, am_r = torch.from_numpy(pv_r), torch.from_numpy(am_r)
        shapes = torch.tensor([[ph, pw]])
        print(f"[padded case] crop {crop_id}  patches {ph}x{pw}={ph * pw} of {MAX_PATCHES}  "
              f"({MAX_PATCHES - ph * pw} padded slots, position embeddings resized 16x16 -> {ph}x{pw})")
        got = static_encoder(pv_r, am_r, pe)

        hidden = vm.embeddings.patch_embedding(pv_r) + pe
        original = vm.embeddings.forward
        vm.embeddings.forward = lambda *_a, **_k: hidden
        try:
            ref_mask_only = model.vision_encoder(pixel_values=pv_r, pixel_attention_mask=am_r,
                                                 spatial_shapes=shapes).last_hidden_state
        finally:
            vm.embeddings.forward = original
        check_outputs("encoder padded, mask only", ref_mask_only, got)

        ref_full = model.vision_encoder(pixel_values=pv_r, pixel_attention_mask=am_r,
                                        spatial_shapes=shapes).last_hidden_state
        check_outputs("encoder padded, full", ref_full, got)
        pe_ref = vm.embeddings.resize_positional_embeddings(
            vm.embeddings.position_embedding.weight.reshape(16, 16, -1), shapes, max_length=MAX_PATCHES)
        print(f"[padded case] position embeddings, host PIL bilinear vs checkpoint F.interpolate(antialias): "
              f"max abs {(pe_ref - pe).abs().max().item():.3e}")

        vis, tok, cos, sin, mask = smp["prefill"]
        check_outputs("prefill", ref_prefill(model.decoder, vis, tok, cos, sin),
                      PrefillStatic(model.decoder).eval()(vis, tok, cos, sin, mask))

        pos = PREFILL_LEN
        past_k = [torch.cat([torch.randn(1, 2, pos, 64), torch.zeros(1, 2, TOTAL - pos, 64)], 2) for _ in range(12)]
        past_v = [torch.cat([torch.randn(1, 2, pos, 64), torch.zeros(1, 2, TOTAL - pos, 64)], 2) for _ in range(12)]
        tok_d, cos_d, sin_d = torch.tensor([[7]], dtype=torch.long), torch.randn(1, 1, 32), torch.randn(1, 1, 32)
        dec_mask = torch.full((1, 1, 1, TOTAL), -1e9)
        dec_mask[..., : pos + 1] = 0.0
        write_mask = (torch.arange(TOTAL) == pos).view(1, 1, TOTAL, 1).float()
        check_outputs("decode", ref_decode(model.decoder, tok_d, cos_d, sin_d, pos, past_k, past_v),
                      DecodeStatic(model.decoder).eval()(tok_d, cos_d, sin_d, dec_mask, write_mask, *past_k, *past_v))

    if FAILURES:
        print(f"\nFAIL: {len(FAILURES)} tolerance breach(es)")
        for line in FAILURES:
            print(f"  {line}")
        return 1
    print(f"\nPASS: {len(TOLERANCES)} cases within tolerance")
    return 0


if __name__ == "__main__":
    sys.exit(main())
