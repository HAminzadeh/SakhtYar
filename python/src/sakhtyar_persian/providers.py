from __future__ import annotations
from functools import lru_cache

@lru_cache(maxsize=1)
def embedding_model():
    from sentence_transformers import SentenceTransformer
    return SentenceTransformer("Alibaba-NLP/gte-multilingual-base", trust_remote_code=True)

def embed(texts: list[str]):
    return embedding_model().encode(texts, normalize_embeddings=True)

@lru_cache(maxsize=1)
def reranker_model():
    from transformers import AutoModelForSequenceClassification, AutoTokenizer
    name="Alibaba-NLP/gte-multilingual-reranker-base"
    return AutoTokenizer.from_pretrained(name), AutoModelForSequenceClassification.from_pretrained(
        name, trust_remote_code=True)

@lru_cache(maxsize=1)
def hezar_ocr():
    from hezar.models import Model
    return Model.load("hezarai/crnn-base-fa-v2")

def ocr_images(paths: list[str]):
    return hezar_ocr().predict(paths)