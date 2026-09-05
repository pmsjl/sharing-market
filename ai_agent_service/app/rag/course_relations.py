"""基于规范化 JSONL 的确定性课程关系选择器。"""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re

from pydantic import BaseModel, ConfigDict, ValidationError

from app.rag.models import CourseMatchMode, CourseRelationSummary


class CourseRelation(BaseModel):
    """``course_material_relations.jsonl`` 中一条可追溯的关系记录。"""

    model_config = ConfigDict(extra="ignore")

    course_code: str
    course_document_id: str
    course_name: str
    entry_year: int
    major: str
    major_code: str
    repo_id: str
    semester: str
    relation_id: str
    relation_group_id: str
    plan_id: str
    plan_source_ids: list[str]
    plan_source_urls: list[str]


@dataclass(frozen=True)
class CourseConstraints:
    entry_year: int | None
    majors: tuple[str, ...]
    major_codes: tuple[str, ...]
    academic_years: tuple[str, ...]
    seasons: tuple[str, ...]

    @property
    def has_any(self) -> bool:
        return bool(self.entry_year or self.majors or self.major_codes
                    or self.academic_years or self.seasons)


@dataclass(frozen=True)
class CourseMatch:
    """根据用户原文选择出的父文档和结构化课程事实。"""

    document_ids: set[str]
    course_names: list[str]
    relation_summaries: list[CourseRelationSummary]
    mode: CourseMatchMode
    constraints_fallback: bool = False


_ACADEMIC_YEAR_TERMS = ("第一学年", "第二学年", "第三学年", "第四学年")
_SEASON_TERMS = ("春季", "秋季", "夏季")
_ENTRY_YEAR_PATTERN = re.compile(r"(?<!\d)(20\d{2})\s*级")


class CourseRelationIndex:
    """为真实别名建立索引，并在查询时选择关系记录。

    精确别名保持固定优先级：课程代码、仓库号、最长且不重叠的课程名。
    专业、年份和学期也能成为一级选择条件，但必须由规划器明确允许。
    """

    def __init__(self) -> None:
        self.relations: list[CourseRelation] = []
        self.course_name_dict: dict[str, set[str]] = {}
        self.course_code_dict: dict[str, set[str]] = {}
        self.repo_id_dict: dict[str, set[str]] = {}
        self.major_dict: dict[str, set[str]] = {}
        self.major_code_dict: dict[str, set[str]] = {}
        self.entry_year_dict: dict[int, set[str]] = {}
        self.semester_dict: dict[str, set[str]] = {}
        self._course_names_longest: list[str] = []
        self._majors_longest: list[str] = []
        self._major_codes_longest: list[str] = []

    @classmethod
    def load(cls, knowledge_root: Path) -> "CourseRelationIndex":
        obj = cls()
        path = knowledge_root / "runtime" / "course_material_relations.jsonl"
        for index, line in enumerate(
                path.read_text(encoding="utf-8-sig").splitlines(), start=1):
            if not line.strip():
                continue
            try:
                relation = CourseRelation.model_validate(json.loads(line))
            except (json.JSONDecodeError, ValidationError) as exc:
                raise ValueError(f"课程关系表第 {index} 行格式不合法：{path}") from exc
            obj.relations.append(relation)
            obj._add(obj.course_name_dict, relation.course_name, relation)
            obj._add(obj.course_code_dict, relation.course_code, relation)
            obj._add(obj.repo_id_dict, relation.repo_id, relation)
            obj._add(obj.major_dict, relation.major, relation)
            obj._add(obj.major_code_dict, relation.major_code, relation)
            obj._add(obj.entry_year_dict, relation.entry_year, relation)
            obj._add(obj.semester_dict, relation.semester, relation)

        obj._course_names_longest = obj._longest_first(obj.course_name_dict)
        obj._majors_longest = obj._longest_first(obj.major_dict)
        obj._major_codes_longest = obj._longest_first(obj.major_code_dict)
        return obj

    @staticmethod
    def _add(objDict: dict, alias, relation: CourseRelation) -> None:
        objDict.setdefault(alias, set()).add(relation.course_document_id)

    @staticmethod
    def _longest_first(objDict: dict[str, set[str]]) -> list[str]:
        return sorted(objDict, key=lambda k: (-len(k), k))

    def match(
        self,
        query: str,
        *, 
        allow_dimension_only: bool = False,
    ) -> CourseMatch:
        """优先解析课程别名；没有别名时可按显式维度选择。"""
        literal_names = self._match_non_overlapping(query,
                                                    self._course_names_longest)
        constraints = self._extract_constraints(query)

        course_codes = self._match_token_aliases(query, self.course_code_dict)
        if course_codes:
            rows = [
                item for item in self.relations
                if item.course_code in course_codes
            ]
            return self._alias_match(rows, literal_names, constraints)

        repo_ids = self._match_token_aliases(query, self.repo_id_dict)
        if repo_ids:
            rows = [
                item for item in self.relations if item.repo_id in repo_ids
            ]
            return self._alias_match(rows, literal_names, constraints)

        if literal_names:
            rows = [
                item for item in self.relations
                if item.course_name in literal_names
            ]
            return self._alias_match(rows, literal_names, constraints)

        if allow_dimension_only and constraints.has_any:
            rows = self._filter_relations(self.relations, constraints)
            mode: CourseMatchMode = ("constraints"
                                     if rows else "constraints_no_match")
            return self._build_match(rows, [], mode)

        return CourseMatch(set(), [], [], "none")

    def _alias_match(
        self,
        rows: list[CourseRelation],
        literal_names: list[str],
        constraints: CourseConstraints,
    ) -> CourseMatch:
        if not constraints.has_any:
            return self._build_match(rows, literal_names, "alias")
        narrowed = self._filter_relations(rows, constraints)
        if narrowed:
            return self._build_match(narrowed, literal_names, "alias")
        # 可选约束与真实课程别名冲突时，不能据此错误断言没有课程资料。
        return self._build_match(
            rows,
            literal_names,
            "alias",
            constraints_fallback=True,
        )

    def _extract_constraints(self, query: str) -> CourseConstraints:
        year_match = _ENTRY_YEAR_PATTERN.search(query)
        entry_year = int(year_match.group(1)) if year_match else None
        majors = tuple(self._match_non_overlapping(query,
                                                   self._majors_longest))
        major_codes = tuple(
            self._match_token_aliases(query, self.major_code_dict))
        academic_years = tuple(term for term in _ACADEMIC_YEAR_TERMS
                               if term in query)
        seasons = tuple(term for term in _SEASON_TERMS if term in query)
        return CourseConstraints(
            entry_year=entry_year,
            majors=majors,
            major_codes=major_codes,
            academic_years=academic_years,
            seasons=seasons,
        )

    @staticmethod
    def _filter_relations(
        rows: list[CourseRelation],
        constraints: CourseConstraints,
    ) -> list[CourseRelation]:
        selected: list[CourseRelation] = []
        for relation in rows:
            if (constraints.entry_year is not None
                    and relation.entry_year != constraints.entry_year):
                continue
            if constraints.majors and relation.major not in constraints.majors:
                continue
            if (constraints.major_codes
                    and relation.major_code not in constraints.major_codes):
                continue
            if constraints.academic_years and not any(
                    term in relation.semester
                    for term in constraints.academic_years):
                continue
            if constraints.seasons and not any(
                    term in relation.semester for term in constraints.seasons):
                continue
            selected.append(relation)
        return selected

    def _build_match(
        self,
        rows: list[CourseRelation],
        course_names: list[str],
        mode: CourseMatchMode,
        *,
        # *前面的可以正常
        # (rows,
        # names,
        # "alias",
        # constraints_fallback=False)
        # 这种传参
        # 加*表示后面的参数必须显式传递用constraints_fallback=False,
        # 而不能直接写一个False赋值，
        constraints_fallback: bool = False,
    ) -> CourseMatch:
        return CourseMatch(
            document_ids={item.course_document_id
                          for item in rows},
            course_names=course_names,
            relation_summaries=self._summarize(rows),
            mode=mode,
            constraints_fallback=constraints_fallback,
        )



    # 这里返回的是检索到的符合的relation类，
    # 然后将这些转化为课程->专业入学年份等等的映射关系(summary类)
    @staticmethod
    def _summarize(
        rows: list[CourseRelation], ) -> list[CourseRelationSummary]:

        grouped: dict[tuple[str, str, str, str, str],
                      list[CourseRelation]] = {}
        for relation in rows:
            key = (
                relation.course_name,
                relation.course_code,
                relation.repo_id,
                relation.course_document_id,
                relation.semester,
            )
            grouped.setdefault(key, []).append(relation)

        summaries: list[CourseRelationSummary] = []
        for key in sorted(grouped):
            course_name, course_code, repo_id, document_id, semester = key
            relations = grouped[key]
            summaries.append(
                CourseRelationSummary(
                    course_name=course_name,
                    course_code=course_code,
                    repo_id=repo_id,
                    course_document_id=document_id,
                    semester=semester,
                    majors=sorted({item.major
                                   for item in relations}),
                    major_codes=sorted({item.major_code
                                        for item in relations}),
                    entry_years=sorted({item.entry_year
                                        for item in relations}),
                    relation_ids=sorted(
                        {item.relation_id
                         for item in relations}),
                    relation_group_ids=sorted(
                        {item.relation_group_id
                         for item in relations}),
                    plan_ids=sorted({item.plan_id
                                     for item in relations}),
                    plan_source_ids=sorted({
                        source_id
                        for item in relations
                        for source_id in item.plan_source_ids
                    }),
                    plan_source_urls=sorted({
                        source_url
                        for item in relations
                        for source_url in item.plan_source_urls
                    }),
                ))
        return summaries

    @staticmethod
    def _match_token_aliases(query: str,
                             obj_dict: dict[str, set[str]]) -> list[str]:
        matched = [
            alias for alias in obj_dict if re.search(
                # escape就是把这里的alias当作字符串而不是正则表达式匹配，
                # 比如C++这里的就是字符串而不是正则中的+，
                # 前后的意思是不允许出现字母或数字，用于匹配完整code
                rf"(?<![A-Za-z0-9]){re.escape(alias)}(?![A-Za-z0-9])",
                query,
                flags=re.IGNORECASE,
            )
        ]
        return sorted(matched, key=lambda value: (-len(value), value))

    @staticmethod
    def _match_non_overlapping(query: str, aliases: list[str]) -> list[str]:
        matched: list[str] = []
        occupied: list[tuple[int, int]] = []
        for alias in aliases:
            start = query.find(alias)
            while start >= 0:
                end = start + len(alias)
                if not any(start < occupied_end and end > occupied_start
                           for occupied_start, occupied_end in occupied):
                    matched.append(alias)
                    occupied.append((start, end))
                    break
                start = query.find(alias, start + 1)
        return matched
