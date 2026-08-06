"""离线索引重建入口：``python -m app.rag.rebuild_index``。"""

import asyncio
from pathlib import Path

from app.core.config import Settings
from app.rag.chunker import chunk_document
from app.rag.document_loader import load_document_metas
from app.rag.index_store import build_index

KNOWLEDGE_ROOT = Path(__file__).resolve().parents[2] / "knowledge"


async def main() -> None:
    """加载正式 manifest、创建 chunks，并原子发布新索引版本。"""
    settings = Settings()
    metas = load_document_metas(KNOWLEDGE_ROOT)
    chunks = [
        chunk
        for meta in metas
        for chunk in chunk_document(KNOWLEDGE_ROOT, meta)
    ]
    build_dir = await build_index(settings, metas, chunks)
    print(f"documents={len(metas)}, chunks={len(chunks)}, version={build_dir.name}")


if __name__ == "__main__":
    asyncio.run(main())
