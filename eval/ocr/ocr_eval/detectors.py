from __future__ import annotations

import importlib.util
from functools import lru_cache

import torch
from huggingface_hub import hf_hub_download
from PIL import Image
from transformers import AutoImageProcessor, AutoModelForObjectDetection

from ocr_eval.proposals import Proposal, xyxy_to_xywh

KOHARU_REPO = "mayocream/koharu-layout-rfdetr-seg-2xl-1152"
KOHARU_REVISION = "aed55fdb8ca953c6bec33cf6ed6dd52a9b72bfa2"
KOHARU_CLASSES = ["text", "onomatopoeia", "bubble", "panel"]
# 모델 카드 권장값. 효과음은 크고 도안화된 글자를 놓치지 않도록 낮게 둔다.
KOHARU_THRESHOLDS = {"text": 0.25, "onomatopoeia": 0.20, "bubble": 0.50}

OGKALU_REPO = "ogkalu/comic-text-and-bubble-detector"
OGKALU_REVISION = "16e8a622f91fabc6b5b65c96d32d1183f8843546"
OGKALU_THRESHOLD = 0.3


@lru_cache(maxsize=1)
def _koharu_model():
    weights = hf_hub_download(repo_id=KOHARU_REPO, filename="model.safetensors", revision=KOHARU_REVISION)
    loader_path = hf_hub_download(repo_id=KOHARU_REPO, filename="load_model.py", revision=KOHARU_REVISION)
    spec = importlib.util.spec_from_file_location("koharu_layout_loader", loader_path)
    loader = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loader)
    return loader.load_model(weights)


def run_koharu(image: Image.Image) -> list[Proposal]:
    detections = _koharu_model().predict(image, threshold=0.20, shape=(1152, 1152), include_source_image=False)
    proposals = []
    for (x1, y1, x2, y2), class_id, score in zip(detections.xyxy, detections.class_id, detections.confidence):
        label = KOHARU_CLASSES[int(class_id)]
        if label in KOHARU_THRESHOLDS and score >= KOHARU_THRESHOLDS[label]:
            proposals.append(Proposal("koharu", label, float(score), xyxy_to_xywh(x1, y1, x2, y2)))
    return proposals


@lru_cache(maxsize=1)
def _ogkalu_model():
    processor = AutoImageProcessor.from_pretrained(OGKALU_REPO, revision=OGKALU_REVISION)
    model = AutoModelForObjectDetection.from_pretrained(OGKALU_REPO, revision=OGKALU_REVISION).eval()
    return processor, model


def run_ogkalu(image: Image.Image) -> list[Proposal]:
    processor, model = _ogkalu_model()
    inputs = processor(images=image, return_tensors="pt")
    with torch.no_grad():
        outputs = model(**inputs)
    result = processor.post_process_object_detection(
        outputs, threshold=OGKALU_THRESHOLD, target_sizes=torch.tensor([(image.height, image.width)])
    )[0]
    return [
        Proposal("ogkalu", model.config.id2label[int(label)], float(score), xyxy_to_xywh(*box.tolist()))
        for score, label, box in zip(result["scores"], result["labels"], result["boxes"])
    ]
