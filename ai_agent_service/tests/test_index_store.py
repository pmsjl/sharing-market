import asyncio
import json
from pathlib import Path

import numpy as np
import pytest

from app.core.config import Settings
from app.rag.document_loader import MANIFEST_FILES
from app.rag.index_store import IndexStore, build_index
from app.rag.models import DocumentMeta, KnowledgeChunk, PostSnapshot


class _FakeEmbedder:
    async def embed_batch(self, texts: list[str]) -> list[list[float]]:
        return [[1.0, 0.0] if index == 0 else [0.0, 1.0] for index, _ in enumerate(texts)]


def _settings(tmp_path: Path) -> Settings:
    return Settings(
        embedding_model="test-embedding",
        embedding_dimensions=2,
        rag_index_dir=str(tmp_path / "rag-index"),
    )


def _knowledge_root(tmp_path: Path) -> Path:
    root = tmp_path / "knowledge"
    for relative_path in MANIFEST_FILES:
        path = root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f"manifest: {relative_path}\n", encoding="utf-8")
    return root


def _meta() -> DocumentMeta:
    return DocumentMeta(
        document_id="GUIDE:test-document",
        category="course_materials",
        status="effective",
        title="Test document",
        relative_path="documents/effective/test.md",
        chunking="h2",
        source_ids=["S1"],
        source_urls=["https://example.test/source"],
        invalidation_condition="test",
        last_verified_at="2026-08-06",
    )


def _chunks() -> list[KnowledgeChunk]:
    return [
        KnowledgeChunk(
            chunk_id="GUIDE:test-document#one",
            document_id="GUIDE:test-document",
            source_id="test-document",
            category="course_materials",
            title="Test document",
            section="教材",
            chunk_index=0,
            content="first",
            embedding_text="first",
        ),
        KnowledgeChunk(
            chunk_id="GUIDE:test-document#two",
            document_id="GUIDE:test-document",
            source_id="test-document",
            category="course_materials",
            title="Test document",
            section="教材",
            chunk_index=1,
            content="second",
            embedding_text="second",
        ),
    ]


def test_build_publishes_unique_version_and_load_validates_it(tmp_path):
    settings = _settings(tmp_path)
    knowledge_root = _knowledge_root(tmp_path)

    first = asyncio.run(
        build_index(settings, [_meta()], _chunks(), _FakeEmbedder(), knowledge_root)
    )
    second = asyncio.run(
        build_index(settings, [_meta()], _chunks(), _FakeEmbedder(), knowledge_root)
    )

    assert first.name != second.name
    current = Path(settings.rag_index_dir) / "CURRENT"
    assert current.read_text(encoding="utf-8") == second.name
    assert not list(Path(settings.rag_index_dir).glob(".CURRENT-*.tmp"))

    store = IndexStore.load(settings, knowledge_root)
    assert store is not None
    assert store.index.ntotal == 2
    assert store.vectors.shape == (2, 2)


def test_load_degrades_for_manifest_drift_or_corrupt_vectors(tmp_path):
    settings = _settings(tmp_path)
    knowledge_root = _knowledge_root(tmp_path)
    build_dir = asyncio.run(
        build_index(settings, [_meta()], _chunks(), _FakeEmbedder(), knowledge_root)
    )

    (knowledge_root / MANIFEST_FILES[0]).write_text("changed", encoding="utf-8")
    assert IndexStore.load(settings, knowledge_root) is None

    # 使用新 manifest 重建后，再主动破坏缓存 ndarray 的形状。
    build_dir = asyncio.run(
        build_index(settings, [_meta()], _chunks(), _FakeEmbedder(), knowledge_root)
    )
    np.save(build_dir / "vectors.npy", np.array([1.0, 0.0], dtype=np.float32))
    assert IndexStore.load(settings, knowledge_root) is None


def test_mixed_build_records_guide_and_post_snapshot_metadata(tmp_path):
    settings = _settings(tmp_path)
    knowledge_root = _knowledge_root(tmp_path)
    post = PostSnapshot(
        id=11,
        title="显示器验货记录",
        content="面交时检查接口和坏点。",
        tags=["数码", "验货"],
        createTime="2026-08-15 20:22:00",
        updateTime="2026-08-15 20:23:00",
        sourceVersion="1786796580000",
    )
    post_chunk = KnowledgeChunk(
        chunk_id="POST:11#one",
        document_id="POST:11",
        source_type="POST",
        source_id="11",
        category="community_post",
        title=post.title,
        section=None,
        chunk_index=0,
        content=post.content,
        embedding_text=post.content,
        metadata={"sourceVersion": post.source_version},
    )

    build_dir = asyncio.run(build_index(
        settings,
        [_meta()],
        [_chunks()[0], post_chunk],
        _FakeEmbedder(),
        knowledge_root,
        posts=[post],
        snapshot_at="2026-08-17T08:00:00+00:00",
    ))

    metadata = json.loads(
        (build_dir / "meta.json").read_text(encoding="utf-8")
    )
    assert metadata["documentCount"] == 2
    assert metadata["guideDocumentCount"] == 1
    assert metadata["postDocumentCount"] == 1
    assert metadata["guideChunkCount"] == 1
    assert metadata["postChunkCount"] == 1
    assert metadata["postSnapshotAt"] == "2026-08-17T08:00:00+00:00"
    assert len(metadata["postSnapshotSha256"]) == 64
    assert IndexStore.load(settings, knowledge_root) is not None


def test_failed_rebuild_does_not_switch_current(tmp_path):
    class _FailingEmbedder:
        async def embed_batch(self, texts):
            raise RuntimeError("embedding unavailable")

    settings = _settings(tmp_path)
    knowledge_root = _knowledge_root(tmp_path)
    first = asyncio.run(
        build_index(settings, [_meta()], _chunks(), _FakeEmbedder(), knowledge_root)
    )
    current = Path(settings.rag_index_dir) / "CURRENT"

    with pytest.raises(RuntimeError, match="embedding unavailable"):
        asyncio.run(build_index(
            settings,
            [_meta()],
            _chunks(),
            _FailingEmbedder(),
            knowledge_root,
        ))

    assert current.read_text(encoding="utf-8") == first.name
