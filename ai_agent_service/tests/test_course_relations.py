import json
from pathlib import Path

from app.rag.course_relations import CourseRelationIndex


def _relation(**overrides):
    value = {
        "course_code": "CODE1001",
        "course_document_id": "GUIDE:course-repo-REPO1001",
        "course_name": "示例课程",
        "entry_year": 2019,
        "major": "计算机类",
        "major_code": "0101",
        "repo_id": "REPO1001",
        "semester": "第一学年春季",
    }
    value.update(overrides)
    suffix = "-".join(
        [
            value["course_code"],
            value["repo_id"],
            value["major_code"],
            str(value["entry_year"]),
            value["semester"],
        ]
    )
    value.setdefault("relation_id", f"GUIDE:course-relation-{suffix}")
    value.setdefault(
        "relation_group_id", f"GUIDE:course-relation-group-{suffix}"
    )
    value.setdefault("plan_id", f"PLAN-{value['major_code']}-{value['entry_year']}")
    value.setdefault("plan_source_ids", [f"plan-source:{suffix}"])
    value.setdefault("plan_source_urls", [f"https://example.test/{suffix}"])
    return value


def _load_index(tmp_path: Path, rows: list[dict]) -> CourseRelationIndex:
    runtime = tmp_path / "runtime"
    runtime.mkdir()
    (runtime / "course_material_relations.jsonl").write_text(
        "\n".join(json.dumps(row, ensure_ascii=False) for row in rows),
        encoding="utf-8",
    )
    return CourseRelationIndex.load(tmp_path)


def test_alias_precedence_and_longest_course_name(tmp_path):
    index = _load_index(
        tmp_path,
        [
            _relation(
                course_code="COMP2022",
                repo_id="COMP2052",
                course_name="数据结构",
                course_document_id="GUIDE:course-repo-COMP2052",
            ),
            _relation(
                course_code="OTHER1001",
                repo_id="OTHER1001",
                course_name="数据结构",
                course_document_id="GUIDE:course-repo-OTHER1001",
            ),
            _relation(
                course_code="LONG1001",
                repo_id="LONG1001",
                course_name="数据结构与算法",
                course_document_id="GUIDE:course-repo-LONG1001",
            ),
        ],
    )

    code_match = index.match("COMP2022 的数据结构教材")
    assert code_match.document_ids == {"GUIDE:course-repo-COMP2052"}
    assert code_match.course_names == ["数据结构"]

    assert index.match("COMP2052 的数据结构教材").document_ids == {
        "GUIDE:course-repo-COMP2052"
    }
    name_match = index.match("数据结构与算法用什么教材")
    assert name_match.document_ids == {"GUIDE:course-repo-LONG1001"}
    assert name_match.course_names == ["数据结构与算法"]


def test_dimensions_can_select_courses_without_a_course_alias(tmp_path):
    index = _load_index(
        tmp_path,
        [
            _relation(
                course_code="SIGNAL-A",
                repo_id="SIGNAL-A",
                course_name="信号与系统 A",
                course_document_id="GUIDE:course-repo-SIGNAL-A",
                semester="第一学年春季",
            ),
            _relation(
                course_code="SIGNAL-B",
                repo_id="SIGNAL-B",
                course_name="信号与系统 B",
                course_document_id="GUIDE:course-repo-SIGNAL-B",
                semester="第一学年秋季",
            ),
            _relation(
                course_code="SIGNAL-C",
                repo_id="SIGNAL-C",
                course_name="信号与系统 C",
                course_document_id="GUIDE:course-repo-SIGNAL-C",
                entry_year=2020,
                major="电子信息类",
                major_code="0202",
                semester="第一学年春季",
            ),
        ],
    )

    match = index.match(
        "计算机类 2019 级第一学年春季有哪些课程",
        allow_dimension_only=True,
    )
    assert match.mode == "constraints"
    assert match.document_ids == {"GUIDE:course-repo-SIGNAL-A"}
    assert match.course_names == []

    major_code_match = index.match(
        "0101 2019级有哪些课程",
        allow_dimension_only=True,
    )
    assert major_code_match.document_ids == {
        "GUIDE:course-repo-SIGNAL-A",
        "GUIDE:course-repo-SIGNAL-B",
    }


def test_dimension_only_no_match_does_not_expand_to_all_courses(tmp_path):
    index = _load_index(tmp_path, [_relation()])

    match = index.match("2030级有哪些课程", allow_dimension_only=True)

    assert match.mode == "constraints_no_match"
    assert match.document_ids == set()
    assert match.relation_summaries == []
    assert match.constraints_fallback is False


def test_alias_constraints_empty_falls_back_to_original_course(tmp_path):
    index = _load_index(
        tmp_path,
        [
            _relation(
                course_code="SIGNAL-A",
                repo_id="SIGNAL-A",
                course_name="信号与系统",
                course_document_id="GUIDE:course-repo-SIGNAL-A",
            ),
            _relation(
                course_code="SIGNAL-B",
                repo_id="SIGNAL-B",
                course_name="信号与系统",
                course_document_id="GUIDE:course-repo-SIGNAL-B",
                semester="第一学年秋季",
            ),
        ],
    )

    match = index.match("2030级信号与系统")

    assert match.mode == "alias"
    assert match.constraints_fallback is True
    assert match.document_ids == {
        "GUIDE:course-repo-SIGNAL-A",
        "GUIDE:course-repo-SIGNAL-B",
    }


def test_relation_summaries_are_deduplicated_and_keep_provenance(tmp_path):
    index = _load_index(
        tmp_path,
        [
            _relation(
                course_code="COMP2022",
                repo_id="COMP2052",
                course_name="数据结构",
                course_document_id="GUIDE:course-repo-COMP2052",
                relation_id="GUIDE:course-relation-one",
                plan_source_ids=["plan:one"],
                plan_source_urls=["https://example.test/one"],
            ),
            _relation(
                course_code="COMP2022",
                repo_id="COMP2052",
                course_name="数据结构",
                course_document_id="GUIDE:course-repo-COMP2052",
                entry_year=2020,
                major="计算机科学与技术",
                major_code="0102",
                relation_id="GUIDE:course-relation-two",
                plan_source_ids=["plan:two"],
                plan_source_urls=["https://example.test/two"],
            ),
        ],
    )

    summary = index.match("数据结构").relation_summaries

    assert len(summary) == 1
    assert summary[0].entry_years == [2019, 2020]
    assert summary[0].majors == ["计算机科学与技术", "计算机类"]
    assert summary[0].relation_ids == [
        "GUIDE:course-relation-one",
        "GUIDE:course-relation-two",
    ]
    assert summary[0].plan_source_urls == [
        "https://example.test/one",
        "https://example.test/two",
    ]


def test_real_relation_uses_parent_document_not_course_code():
    knowledge_root = Path(__file__).resolve().parents[1] / "knowledge"
    index = CourseRelationIndex.load(knowledge_root)

    match = index.match("计算机类 2019 级的数据结构用什么教材 COMP2022")

    assert match.document_ids == {"GUIDE:course-repo-COMP2052"}
    assert "COMP2022" not in match.document_ids
    assert match.relation_summaries[0].relation_ids
    assert match.relation_summaries[0].plan_source_urls
