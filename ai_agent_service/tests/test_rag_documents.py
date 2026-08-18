from collections import Counter
from pathlib import Path

from app.rag.chunker import chunk_guide
from app.rag.document_loader import load_document_metas

KNOWLEDGE_ROOT = Path(__file__).resolve().parents[1] / "knowledge"
metas = load_document_metas(KNOWLEDGE_ROOT)


def test_effective_snapshot_counts():
    counts = Counter(item.category for item in metas)
    assert counts == {
        "platform_policy": 13,
        "campus_dorm": 5,
        "campus_lifecycle": 5,
        "course_materials": 75,
        "course_purchase_policy": 1,
    }


def test_course_source_section_is_not_embedded():
    meta = next(item for item in metas
                if item.document_id == "GUIDE:course-repo-COMP2052")
    chunks = chunk_guide(KNOWLEDGE_ROOT, meta)
    assert {item.section for item in chunks} == {"教材"}
    assert all("raw.githubusercontent.com" not in item.content
               for item in chunks)


def test_dorm_document_keeps_rule_sections():
    meta = next(item for item in metas
                if item.document_id == "GUIDE:campus-dorm-appliance-rules")
    sections = {item.section for item in chunk_guide(KNOWLEDGE_ROOT, meta)}
    assert "官方禁用电器（硬性规定）" in sections
    assert "学生分享（参考）" in sections
