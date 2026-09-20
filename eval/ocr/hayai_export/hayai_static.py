"""Static-shape rewrites of the three Hayai OCR v2.1 export graphs.

Mirrors `export_kv.py`'s `EncoderWithPosInput` / `PrefillDecoderKV` /
`DecodeDecoderKV` arithmetic exactly, with two substitutions that a CoreML
conversion needs:

* `apply_rotary_emb_2d` builds complex tensors (`torch.view_as_complex`), which
  has no CoreML lowering. `rope_real` is the algebraically identical real form.
* every sequence/batch extent is a Python constant rather than read off a traced
  tensor, because traced shape arithmetic lowers to `aten::floor_divide` on
  symbolic ints, which the converter rejects.
"""

from __future__ import annotations

import sys
from pathlib import Path

import torch
import torch.nn as nn
import torch.nn.functional as F
from safetensors.torch import load_file

MAX_PATCHES = 256
MAX_TEXT = 64
TOTAL = 320
PREFILL_LEN = MAX_PATCHES + 1  # 257: all vision patches plus the single BOS token
D_MODEL = 512
D_HEAD = 64
D_AXIS = 32
H_Q = 8
H_KV = 2
N_LAYERS = 12


def rope_real(x: torch.Tensor, cos: torch.Tensor, sin: torch.Tensor, seq: int, heads: int) -> torch.Tensor:
    """`apply_rotary_emb_2d` without complex tensors. x:(1,seq,heads,64) cos/sin:(1,seq,32)."""
    xf = x.float().reshape(1, seq, heads, D_AXIS, 2)
    x_even, x_odd = xf[..., 0], xf[..., 1]
    c = cos.float().unsqueeze(2)
    s = sin.float().unsqueeze(2)
    return torch.stack([x_even * c - x_odd * s, x_even * s + x_odd * c], dim=-1).flatten(-2).to(x.dtype)


class EncoderStatic(nn.Module):
    """SigLIP2 tower with host-supplied position embeddings (export_kv.EncoderWithPosInput)."""

    def __init__(self, vision_model):
        super().__init__()
        self.patch_embedding = vision_model.embeddings.patch_embedding
        self.layers = nn.ModuleList(list(vision_model.encoder.layers))
        self.post_ln = vision_model.post_layernorm

    def forward(self, pixel_values, attention_mask, pos_embeds):
        hidden = self.patch_embedding(pixel_values) + pos_embeds
        mask = ((attention_mask == 0).float() * -1e9).unsqueeze(1).unsqueeze(1)
        for layer in self.layers:
            hidden = layer(hidden_states=hidden, attention_mask=mask)
        return self.post_ln(hidden)


class PrefillStatic(nn.Module):
    """export_kv.PrefillDecoderKV with constant extents and real-valued RoPE."""

    def __init__(self, decoder):
        super().__init__()
        self.projector = decoder.projector
        self.token_embeddings = decoder.token_embeddings
        self.layers = nn.ModuleList(list(decoder.layers))
        self.final_norm = decoder.final_norm
        self.output_head = decoder.output_head

    def forward(self, visual_features, text_token_ids, cos, sin, mask):
        s = PREFILL_LEN
        x = torch.cat([self.projector(visual_features), self.token_embeddings(text_token_ids)], dim=1)
        cos_s, sin_s = cos[:, :s, :], sin[:, :s, :]
        mask_s = mask[:, :, :s, :s]
        caches_k, caches_v = [], []
        for layer in self.layers:
            normed = layer.attn_norm(x)
            q = rope_real(layer.attn.q_norm(layer.attn.w_q(normed).view(1, s, H_Q, D_HEAD)), cos_s, sin_s, s, H_Q)
            k = rope_real(layer.attn.k_norm(layer.attn.w_k(normed).view(1, s, H_KV, D_HEAD)), cos_s, sin_s, s, H_KV)
            v = layer.attn.w_v(normed).view(1, s, H_KV, D_HEAD)
            k_t, v_t = k.transpose(1, 2), v.transpose(1, 2)
            pad = torch.zeros(1, H_KV, TOTAL - s, D_HEAD, device=x.device, dtype=k_t.dtype)
            caches_k.append(torch.cat([k_t, pad], dim=2))
            caches_v.append(torch.cat([v_t, pad], dim=2))
            ctx = F.scaled_dot_product_attention(
                q.transpose(1, 2), k_t.repeat_interleave(H_Q // H_KV, dim=1),
                v_t.repeat_interleave(H_Q // H_KV, dim=1), attn_mask=mask_s, is_causal=False,
            )
            x = x + layer.attn_res_scale * layer.attn.w_o(ctx.transpose(1, 2).reshape(1, s, D_MODEL))
            x = x + layer.ffn_res_scale * layer.ffn(layer.ffn_norm(x))
        logits = self.output_head(self.final_norm(x[:, -1:, :])).squeeze(1)
        return tuple([logits] + caches_k + caches_v)


class DecodeStatic(nn.Module):
    """export_kv.DecodeDecoderKV with constant extents and real-valued RoPE.

    The `pos` scalar becomes a host-computed `write_mask`: deriving the one-hot
    cache slot inside the graph needs `arange == pos`, whose index arithmetic is
    exactly what the converter rejects.
    """

    def __init__(self, decoder):
        super().__init__()
        self.token_embeddings = decoder.token_embeddings
        self.layers = nn.ModuleList(list(decoder.layers))
        self.final_norm = decoder.final_norm
        self.output_head = decoder.output_head

    def forward(self, token_ids, cos, sin, mask, write_mask, *past_kv):
        past_ks, past_vs = past_kv[:N_LAYERS], past_kv[N_LAYERS:]
        x = self.token_embeddings(token_ids)
        wm = write_mask.to(torch.bool)
        new_ks, new_vs = [], []
        for i, layer in enumerate(self.layers):
            residual = x
            normed = layer.attn_norm(x)
            q = rope_real(layer.attn.q_norm(layer.attn.w_q(normed).view(1, 1, H_Q, D_HEAD)), cos, sin, 1, H_Q)
            k = rope_real(layer.attn.k_norm(layer.attn.w_k(normed).view(1, 1, H_KV, D_HEAD)), cos, sin, 1, H_KV)
            v = layer.attn.w_v(normed).view(1, 1, H_KV, D_HEAD)
            uk = torch.where(wm, k.transpose(1, 2).expand(1, H_KV, TOTAL, D_HEAD), past_ks[i])
            uv = torch.where(wm, v.transpose(1, 2).expand(1, H_KV, TOTAL, D_HEAD), past_vs[i])
            ctx = F.scaled_dot_product_attention(
                q.transpose(1, 2), uk.repeat_interleave(H_Q // H_KV, dim=1),
                uv.repeat_interleave(H_Q // H_KV, dim=1), attn_mask=mask, is_causal=False,
            )
            x = residual + layer.attn_res_scale * layer.attn.w_o(ctx.transpose(1, 2).reshape(1, 1, D_MODEL))
            x = x + layer.ffn_res_scale * layer.ffn(layer.ffn_norm(x))
            new_ks.append(uk)
            new_vs.append(uv)
        return tuple([self.output_head(self.final_norm(x)).squeeze(1)] + new_ks + new_vs)


def load_hayai(src_dir: Path):
    """Load the torch checkpoint. `src_dir` holds config.json + model.safetensors + modeling_hayai.py."""
    src = str(src_dir)
    if src not in sys.path:
        sys.path.insert(0, src)
    from configuration_hayai import HayaiConfig
    from modeling_hayai import HayaiModel

    model = HayaiModel(HayaiConfig.from_pretrained(src))
    model.load_state_dict(load_file(str(src_dir / "model.safetensors")), strict=False)
    return model.eval()


def prefill_mask() -> torch.Tensor:
    """`hayai_ocr.litert.make_prefill_mask` as a torch tensor (block-causal, vision cannot see BOS)."""
    m = torch.full((TOTAL, TOTAL), -1e9)
    m[:MAX_PATCHES, :MAX_PATCHES] = 0
    m[MAX_PATCHES:PREFILL_LEN, :PREFILL_LEN] = 0
    m[PREFILL_LEN:, :MAX_PATCHES] = 0
    return m.reshape(1, 1, TOTAL, TOTAL)


def sample_inputs():
    """Reference example inputs, shapes taken from export_kv.convert_float_models."""
    return {
        "encoder": (torch.randn(1, MAX_PATCHES, 768), torch.ones(1, MAX_PATCHES, dtype=torch.int64),
                    torch.randn(1, MAX_PATCHES, 768)),
        "prefill": (torch.randn(1, MAX_PATCHES, 768), torch.tensor([[1]], dtype=torch.long),
                    torch.randn(1, TOTAL, D_AXIS), torch.randn(1, TOTAL, D_AXIS), prefill_mask()),
        "decode": (torch.tensor([[1]], dtype=torch.long), torch.randn(1, 1, D_AXIS), torch.randn(1, 1, D_AXIS),
                   torch.zeros(1, 1, 1, TOTAL), (torch.arange(TOTAL) == PREFILL_LEN).view(1, 1, TOTAL, 1).float(),
                   *[torch.zeros(1, H_KV, TOTAL, D_HEAD) for _ in range(2 * N_LAYERS)]),
    }
