import json
from pathlib import Path

from app.rag.course_relations import CourseRelationIndex
from app.rag.query_planner import NON_COURSE_CATEGORIES, plan_query


def _relation(**overrides):
    value = {
        "course_code": "COMP2022",
        "course_document_id": "GUIDE:course-repo-COMP2052",
        "course_name": "数据结构",
        "entry_year": 2019,
        "major": "计算机类",
        "major_code": "0101",
        "repo_id": "COMP2052",
        "semester": "第一学年春季",
        "relation_id": "GUIDE:course-relation-data-structures",
        "relation_group_id": "GUIDE:course-relation-group-2019-0101",
        "plan_id": "PLAN-2019-0101",
        "plan_source_ids": ["plan-source:2019-0101"],
        "plan_source_urls": ["https://example.test/2019-0101"],
    }
    value.update(overrides)
    return value


def _index(tmp_path: Path,
           rows: list[dict] | None = None) -> CourseRelationIndex:
    normalized = tmp_path / "normalized"
    normalized.mkdir()
    rows = rows or [_relation()]
    (normalized / "course_material_relations.jsonl").write_text(
        "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
        encoding="utf-8",
    )
    return CourseRelationIndex.load(tmp_path)


def test_exact_course_uses_exact_documents_and_purchase_policy(tmp_path):
    plan = plan_query("COMP2022 的教材怎么买", _index(tmp_path))

    assert plan.should_retrieve is True
    assert plan.course_document_ids == [
        "GUIDE:course-repo-COMP2052",
        "GUIDE:course-purchase-policy",
    ]
    assert plan.extra_categories == []
    assert plan.fallback_categories == list(NON_COURSE_CATEGORIES)
    assert plan.course_match_mode == "alias"
    assert len(plan.course_relation_summaries) == 1


def test_exact_course_purchase_phrase_adds_policy_without_global_keyword(
        tmp_path):
    plan = plan_query("COMP2022 可以买二手吗", _index(tmp_path))

    assert plan.course_document_ids == [
        "GUIDE:course-repo-COMP2052",
        "GUIDE:course-purchase-policy",
    ]


def test_generic_course_material_question_searches_all_course_categories(
        tmp_path):
    plan = plan_query("教材什么时候买比较合适", _index(tmp_path))

    assert plan.should_retrieve is True
    assert plan.course_document_ids == []
    assert plan.extra_categories == [
        "campus_lifecycle",
        "course_materials",
        "course_purchase_policy",
    ]
    assert plan.course_match_mode == "none"


def test_non_keyword_query_uses_non_course_semantic_fallback(tmp_path):
    plan = plan_query("帮我找一台便宜的二手电脑", _index(tmp_path))

    assert plan.should_retrieve is True
    assert plan.include_posts is True
    assert plan.course_document_ids == []
    assert plan.extra_categories == []
    assert plan.fallback_categories == list(NON_COURSE_CATEGORIES)


def test_blank_query_skips_retrieval(tmp_path):
    plan = plan_query("   ", _index(tmp_path))

    assert plan.should_retrieve is False
    assert plan.include_posts is False
    assert plan.fallback_categories == []


def test_course_catalog_query_returns_relations_and_parent_documents(tmp_path):
    rows = [
        _relation(),
        _relation(
            course_code="COMP3001",
            course_document_id="GUIDE:course-repo-COMP3001",
            course_name="计算机网络",
            repo_id="COMP3001",
            relation_id="GUIDE:course-relation-networks",
        ),
        _relation(
            course_code="EE1001",
            course_document_id="GUIDE:course-repo-EE1001",
            course_name="电路基础",
            repo_id="EE1001",
            major="电子信息类",
            major_code="0202",
            relation_id="GUIDE:course-relation-circuits",
        ),
    ]

    plan = plan_query("计算机类 2019 级有哪些课程", _index(tmp_path, rows))

    assert plan.course_match_mode == "constraints"
    assert plan.course_document_ids == [
        "GUIDE:course-repo-COMP2052",
        "GUIDE:course-repo-COMP3001",
    ]
    assert {item.course_name
            for item in plan.course_relation_summaries} == {
                "数据结构",
                "计算机网络",
            }
    assert "GUIDE:course-purchase-policy" not in plan.course_document_ids


def test_user_background_does_not_trigger_course_dimension_selection(tmp_path):
    plan = plan_query(
        "我是计算机类 2019 级，帮我找一台便宜电脑",
        _index(tmp_path),
    )

    assert plan.course_match_mode == "none"
    assert plan.course_document_ids == []
    assert plan.course_relation_summaries == []


def test_mixed_course_and_dorm_query_keeps_both_scopes(tmp_path):
    plan = plan_query("COMP2022 的教材能放宿舍吗", _index(tmp_path))

    assert plan.course_document_ids == [
        "GUIDE:course-repo-COMP2052",
        "GUIDE:course-purchase-policy",
    ]
    assert plan.extra_categories == ["campus_dorm"]
    assert plan.fallback_categories == list(NON_COURSE_CATEGORIES)
