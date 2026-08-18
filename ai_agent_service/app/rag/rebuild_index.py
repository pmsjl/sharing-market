"""离线索引重建入口：``python -m app.rag.rebuild_index``。"""

import asyncio
from datetime import datetime, timezone
from pathlib import Path

from app.clients.java_backend import JavaBackendClient
from app.core.config import Settings
from app.rag.chunker import chunk_guide, chunk_post
from app.rag.document_loader import load_document_metas
from app.rag.index_store import build_index

KNOWLEDGE_ROOT = Path(__file__).resolve().parents[2] / "knowledge"


async def main() -> None:
    """加载正式 manifest、创建 chunks，并原子发布新索引版本。"""
    settings = Settings()
    metas = load_document_metas(KNOWLEDGE_ROOT)
    guide_chunks = [
        chunk for meta in metas for chunk in chunk_guide(KNOWLEDGE_ROOT, meta)
    ]
    posts = await JavaBackendClient(settings).fetch_post_snapshot()
    post_chunks = [chunk for post in posts for chunk in chunk_post(post)]
    chunks = guide_chunks + post_chunks
    build_dir = await build_index(
        settings,
        metas,
        chunks,
        posts=posts,
        snapshot_at=datetime.now(timezone.utc).isoformat(),
    )
    print(f"guides={len(metas)}, posts={len(posts)}, "
          f"chunks={len(chunks)}, version={build_dir.name}")


if __name__ == "__main__":
    asyncio.run(main())
