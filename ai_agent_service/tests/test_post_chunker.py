from app.rag.chunker import chunk_post
from app.rag.models import PostSnapshot


def _post(*, content: str, source_version: str = "1786796580000") -> PostSnapshot:
    return PostSnapshot(
        id=12,
        title="显示器面交验货记录",
        content=content,
        tags=["数码", "显示器"],
        createTime="2026-08-15 20:22:00",
        updateTime="2026-08-15 20:23:00",
        sourceVersion=source_version,
    )


def test_post_chunker_keeps_introduction_h2_and_identity_fields():
    chunks = chunk_post(_post(content=(
        "# 显示器面交验货记录\n\n"
        "这是开场经历，不能因为后面存在二级标题而丢失。\n\n"
        "## 到现场先检查\n\n"
        "先检查接口，再连接电脑测试。\n\n"
        "## 付款前\n\n"
        "确认坏点和维修记录。"
    )))

    assert [item.section for item in chunks] == [None, "到现场先检查", "付款前"]
    assert chunks[0].content.startswith("这是开场经历")
    assert all(item.source_type == "POST" for item in chunks)
    assert all(item.source_id == "12" for item in chunks)
    assert all(item.document_id == "POST:12" for item in chunks)
    assert all(item.category == "community_post" for item in chunks)
    assert all(item.metadata["sourceVersion"] == "1786796580000" for item in chunks)
    assert "数码" in chunks[1].embedding_text
    assert "显示器" in chunks[1].embedding_text
    assert "到现场先检查" in chunks[1].embedding_text


def test_post_chunker_splits_single_oversized_line():
    chunks = chunk_post(_post(content="长" * 2501))

    assert len(chunks) == 3
    assert [len(item.content) for item in chunks] == [1200, 1200, 101]
    assert all(len(item.content) <= 1200 for item in chunks)


def test_post_chunk_ids_change_with_source_version_but_stay_deterministic():
    content = "## 验货\n\n检查屏幕和接口。"

    first = chunk_post(_post(content=content, source_version="1000"))
    repeated = chunk_post(_post(content=content, source_version="1000"))
    edited = chunk_post(_post(content=content, source_version="2000"))

    assert [item.chunk_id for item in first] == [
        item.chunk_id for item in repeated
    ]
    assert [item.chunk_id for item in first] != [
        item.chunk_id for item in edited
    ]
