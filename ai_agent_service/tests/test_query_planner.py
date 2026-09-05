import json
from pathlib import Path

from app.rag.course_relations import CourseRelationIndex
from app.rag.query_planner import (
    NON_COURSE_CATEGORIES,
    plan_query,
    resolve_course_match,
)
from app.routing.query_router import RetrieveRouteDecision


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


def _index(tmp_path: Path, rows: list[dict] | None = None) -> CourseRelationIndex:
    runtime = tmp_path / "runtime"
    runtime.mkdir()
    rows = rows or [_relation()]
    (runtime / "course_material_relations.jsonl").write_text(
        "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
        encoding="utf-8",
    )
    return CourseRelationIndex.load(tmp_path)


def _plan(
    query: str,
    relations: CourseRelationIndex,
    decision: RetrieveRouteDecision,
):
    raw_match = relations.match(query, allow_dimension_only=True)
    effective_match = resolve_course_match(raw_match, decision)
    return plan_query(effective_match, decision), effective_match


def test_material_requirement_uses_exact_course_a_and_policy_b(tmp_path):
    decision = RetrieveRouteDecision(
        knowledge_domains=["course"],
        retrieval_strategy="targeted",
    )

    plan, match = _plan("COMP2022 老师指定什么教材", _index(tmp_path), decision)

    assert match.mode == "alias"
    assert plan.course_document_ids == ["GUIDE:course-repo-COMP2052"]
    assert plan.include_course_purchase_policy is True


def test_course_history_uses_a_with_uniform_policy_b(tmp_path):
    decision = RetrieveRouteDecision(
        knowledge_domains=["course"],
        retrieval_strategy="targeted",
    )

    plan, _ = _plan("COMP2022 资料以前提到什么", _index(tmp_path), decision)

    assert plan.course_document_ids == ["GUIDE:course-repo-COMP2052"]
    assert plan.include_course_purchase_policy is True


def test_generic_course_planning_uses_policy_without_unrelated_course_a(tmp_path):
    decision = RetrieveRouteDecision(
        knowledge_domains=["course"],
        retrieval_strategy="targeted",
    )

    plan, match = _plan("教材什么时候买比较合适", _index(tmp_path), decision)

    assert match.mode == "none"
    assert plan.course_document_ids == []
    assert plan.include_course_purchase_policy is True
    assert plan.primary_guide_categories == []


def test_catalog_route_uses_relation_result_without_keyword_reclassification(tmp_path):
    rows = [
        _relation(),
        _relation(
            course_code="COMP3001",
            course_document_id="GUIDE:course-repo-COMP3001",
            course_name="计算机网络",
            repo_id="COMP3001",
            relation_id="GUIDE:course-relation-networks",
        ),
    ]
    decision = RetrieveRouteDecision(
        knowledge_domains=["course"],
        retrieval_strategy="targeted",
    )

    plan, match = _plan("计算机类 2019 级有哪些课程", _index(tmp_path, rows), decision)

    assert match.mode == "constraints"
    assert plan.course_a_quota == 2
    assert plan.course_document_ids == [
        "GUIDE:course-repo-COMP2052",
        "GUIDE:course-repo-COMP3001",
    ]
    assert plan.include_course_purchase_policy is True


def test_non_course_route_discards_incidental_course_match(tmp_path):
    decision = RetrieveRouteDecision(
        knowledge_domains=["transaction_experience"],
        retrieval_strategy="targeted",
    )

    plan, match = _plan("COMP2022 用的电脑怎么验货", _index(tmp_path), decision)

    assert match.mode == "none"
    assert plan.course_document_ids == []
    assert plan.post_retrieval_mode == "primary"


def test_course_multilabel_maps_only_requested_c_channels(tmp_path):
    decision = RetrieveRouteDecision(
        knowledge_domains=["course", "platform_policy", "transaction_experience"],
        retrieval_strategy="targeted",
    )

    plan, _ = _plan("COMP2022 实验设备怎么买", _index(tmp_path), decision)

    assert plan.course_auxiliary_categories == ["platform_policy"]
    assert plan.post_retrieval_mode == "course_auxiliary"
    assert plan.fallback_guide_categories == []


def test_non_catalog_course_constraints_are_kept_as_candidate_scope(tmp_path):
    decision = RetrieveRouteDecision(
        knowledge_domains=["course"],
        retrieval_strategy="targeted",
    )

    plan, match = _plan("计算机类 2019 级指定什么教材", _index(tmp_path), decision)

    assert match.mode == "constraints"
    assert plan.course_document_ids == ["GUIDE:course-repo-COMP2052"]
    assert plan.include_course_purchase_policy is True


def test_broad_fallback_uses_fixed_non_course_lanes_without_topic_guessing(tmp_path):
    decision = RetrieveRouteDecision(retrieval_strategy="broad_fallback")

    plan, _ = _plan("这事儿该怎么处理", _index(tmp_path), decision)

    assert plan.primary_guide_categories == []
    assert plan.fallback_guide_categories == list(NON_COURSE_CATEGORIES)
    assert plan.post_retrieval_mode == "primary"


def test_broad_course_fallback_keeps_a_and_uniform_policy_b(tmp_path):
    decision = RetrieveRouteDecision(
        knowledge_domains=["course"],
        retrieval_strategy="broad_fallback",
    )

    plan, _ = _plan("COMP2022 这事儿怎么处理", _index(tmp_path), decision)

    assert plan.course_document_ids == ["GUIDE:course-repo-COMP2052"]
    assert plan.include_course_purchase_policy is True
    assert plan.course_auxiliary_categories == list(NON_COURSE_CATEGORIES)
    assert plan.post_retrieval_mode == "course_auxiliary"
