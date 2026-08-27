"""带不可变版本目录和原子 CURRENT 指针的 FAISS 索引。"""

from __future__ import annotations

import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Protocol
from uuid import uuid4

import faiss
import numpy as np

from app.core.config import Settings
from app.rag.document_loader import MANIFEST_FILES
from app.rag.embedding_client import EmbeddingClient, l2_normalize
from app.rag.models import GuideDocumentMeta, KnowledgeChunk, PostSnapshot

CHUNKING_VERSION = "guide-post-v2"
KNOWLEDGE_ROOT = Path(__file__).resolve().parents[2] / "knowledge"


class BatchEmbeddingProtocol(Protocol):
    """索引构建阶段批量生成向量所需的最小接口。"""

    async def embed_batch(self, texts: list[str], /) -> list[list[float]]: ...


def manifest_sha256(knowledge_root: Path) -> str:
    """按固定顺序计算三个 manifest 的哈希，用于识别语料漂移。"""
    digest = hashlib.sha256()
    for relative_path in MANIFEST_FILES:
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        digest.update((knowledge_root / relative_path).read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def _build_id() -> str:
    """生成抗冲突且可按时间排序的不可变版本目录名。"""
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return f"{timestamp}-{uuid4().hex}"


def current_build_name(settings: Settings) -> str | None:
    """读取并校验 CURRENT 中的单级不可变构建目录名。"""
    try:
        build_name = (Path(settings.rag_index_dir) /
                      "CURRENT").read_text(encoding="utf-8").strip()
    except OSError:
        return None
    if not build_name or Path(build_name).name != build_name:
        return None
    return build_name


def post_snapshot_sha256(posts: list[PostSnapshot]) -> str:
    """计算稳定快照摘要，记录本次构建实际使用的完整 Post 集合。"""
    payload = [
        post.model_dump(mode="json", by_alias=True)
        for post in sorted(posts, key=lambda item: item.id)
    ]
    encoded = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        #json按照键值对中的键这个字母顺序进行排序，确保不同字典键值对顺序一致
        separators=(",", ":"),
        #不同键值对用，隔开，键值之间用冒号隔开
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


async def build_index(
    settings: Settings,
    guideMetas: list[GuideDocumentMeta],
    chunks: list[KnowledgeChunk],
    embedder: BatchEmbeddingProtocol | None = None,
    knowledge_root: Path = KNOWLEDGE_ROOT,
    posts: list[PostSnapshot] | None = None,
    snapshot_at: str | None = None,
) -> Path:
    """先完整构建新索引，再原子切换为当前版本。

    所有数据文件写完前，旧 ``CURRENT`` 指向的版本始终可读。构建失败最多
    留下一个未使用的版本目录，不能覆盖仍在工作的索引指针。
    """
    if not chunks:
        raise ValueError("无法为 0 个 chunk 构建 RAG 索引")

    embedder = embedder or EmbeddingClient(settings)
    raw_vectors = await embedder.embed_batch(
        [chunk.embedding_text for chunk in chunks])
    if len(raw_vectors) != len(chunks):
        raise ValueError("embedding 返回数量与 chunk 数量不一致")
    vectors = np.vstack([l2_normalize(vector)
                         for vector in raw_vectors]).astype(np.float32)
    if vectors.ndim != 2 or vectors.shape[1] != settings.embedding_dimensions:
        raise ValueError("embedding 向量维度与当前配置不一致")

    index = faiss.IndexFlatIP(vectors.shape[1])
    index.add(vectors)

    index_dir = Path(settings.rag_index_dir)
    build_dir = index_dir / "versions" / _build_id()
    build_dir.mkdir(parents=True, exist_ok=False)

    faiss.write_index(index, str(build_dir / "index.faiss"))
    np.save(build_dir / "vectors.npy", vectors)
    with (build_dir / "chunks.jsonl").open("w", encoding="utf-8") as file:
        for chunk in chunks:
            file.write(chunk.model_dump_json() + "\n")

    posts = posts or []
    guide_chunks = [chunk for chunk in chunks if chunk.source_type == "GUIDE"]
    post_chunks = [chunk for chunk in chunks if chunk.source_type == "POST"]
    metadata = {
        "buildId": build_dir.name,
        "embeddingModel": settings.embedding_model,
        "embeddingDimensions": settings.embedding_dimensions,
        "chunkingVersion": CHUNKING_VERSION,
        "documentCount": len(guideMetas) + len(posts),
        "guideDocumentCount": len(guideMetas),
        "postDocumentCount": len(posts),
        "guideChunkCount": len(guide_chunks),
        "postChunkCount": len(post_chunks),
        "chunkCount": len(chunks),
        "manifestSha256": manifest_sha256(knowledge_root),
        "postSnapshotSha256": post_snapshot_sha256(posts),
        "postSnapshotAt": snapshot_at
        or datetime.now(timezone.utc).isoformat(),
    }
    (build_dir / "meta.json").write_text(json.dumps(metadata,
                                                    ensure_ascii=False),
                                         encoding="utf-8")

    # ``os.replace`` 在同一文件系统内是原子操作；读取方只会看到旧的完整
    # 指针或新的完整指针，不会读到只写了一部分的 CURRENT 文件。
    current_path = index_dir / "CURRENT"
    temporary_current = index_dir / f".CURRENT-{uuid4().hex}.tmp"
    temporary_current.write_text(build_dir.name, encoding="utf-8")
    os.replace(temporary_current, current_path)
    #把tmp文件名字改成"CURRENT",并覆盖原本的文件，实现无缝更新
    return build_dir


class IndexStore:
    """已经完成全部校验、可直接用于检索的不可变索引版本。"""

    def __init__(
        self,
        index: faiss.Index,
        vectors: np.ndarray,
        chunks: list[KnowledgeChunk],
        metadata: dict,
        build_name: str | None = None,
    ) -> None:
        self.index = index
        self.vectors = vectors
        self.chunks = chunks
        self.metadata = metadata
        self.build_name = build_name or metadata.get("buildId")

    @classmethod
    def load(
        cls,
        settings: Settings,
        knowledge_root: Path = KNOWLEDGE_ROOT,
        build_name: str | None = None,
    ) -> "IndexStore | None":
        """加载 CURRENT；缓存缺失、过期或损坏时返回 ``None``。

        RAG 是可选增强能力。损坏缓存应让调用方降级为空上下文，而不是导致
        Agent 服务启动失败。
        """
        try:
            index_dir = Path(settings.rag_index_dir)
            selected_build = build_name or current_build_name(settings)
            if not selected_build or Path(
                    selected_build).name != selected_build:
                return None
            build_dir = index_dir / "versions" / selected_build
            metadata = json.loads(
                (build_dir / "meta.json").read_text(encoding="utf-8"))
            if not isinstance(metadata, dict):
                return None
            index = faiss.read_index(str(build_dir / "index.faiss"))
            vectors = np.load(build_dir / "vectors.npy")
            chunks = [
                KnowledgeChunk.model_validate(json.loads(line))
                for line in (build_dir / "chunks.jsonl").read_text(
                    encoding="utf-8").splitlines() if line.strip()
            ]

            if metadata.get("embeddingModel") != settings.embedding_model:
                return None
            if metadata.get(
                    "embeddingDimensions") != settings.embedding_dimensions:
                return None
            if metadata.get("chunkingVersion") != CHUNKING_VERSION:
                return None
            if metadata.get("buildId") != selected_build:
                return None
            if metadata.get("chunkCount") != len(chunks):
                return None
            guide_chunk_count = sum(chunk.source_type == "GUIDE"
                                    for chunk in chunks)
            post_chunk_count = sum(chunk.source_type == "POST"
                                   for chunk in chunks)
            if metadata.get("guideChunkCount") != guide_chunk_count:
                return None
            if metadata.get("postChunkCount") != post_chunk_count:
                return None
            guide_document_count = metadata.get("guideDocumentCount")
            post_document_count = metadata.get("postDocumentCount")
            if (isinstance(guide_document_count, bool)
                    or not isinstance(guide_document_count, int)
                    or guide_document_count < 0
                    or isinstance(post_document_count, bool)
                    or not isinstance(post_document_count, int)
                    or post_document_count < 0 or metadata.get("documentCount")
                    != guide_document_count + post_document_count):
                return None
            if not isinstance(metadata.get("postSnapshotSha256"), str):
                return None
            if not isinstance(metadata.get("postSnapshotAt"), str):
                return None
            if vectors.ndim != 2 or vectors.shape[
                    1] != settings.embedding_dimensions:
                return None
            if index.d != settings.embedding_dimensions:
                return None
            if index.ntotal != len(chunks) or len(chunks) != vectors.shape[0]:
                return None
            if metadata.get("manifestSha256") != manifest_sha256(
                    knowledge_root):
                return None
        except (
                OSError,
                ValueError,
                TypeError,
                KeyError,
                IndexError,
                json.JSONDecodeError,
                RuntimeError,
        ):
            return None
        return cls(index, vectors, chunks, metadata, selected_build)
