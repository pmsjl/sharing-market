"""商品简介全量验收测试。"""
from __future__ import annotations

import json
import re
import runpy
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SOURCE = REPO / "tools/commodity_descriptions.jsonl"
REPORT = REPO / "tools/commodity_description_quality_report.json"
CLEANUP_SQL = REPO / "market_backend/sql/20260819_cleanup_all_commodity_descriptions.sql"
SEARCH_REPORT = REPO / "tools/commodity_search_regression_report.json"
POST_SEED_SOURCE = REPO / "tools/post_aligned_commodity_seed.py"
POST_SEED_SQL = REPO / "market_backend/sql/20260819_post_aligned_commodity_seed.sql"

# 内部批次标记和知识库说明不得出现在用户可见的商品简介中。
BANNED = (
    "资料条目标识", "核心检索信息", "本商品为校园平台演示挂牌",
    "课程知识库仅证明", "课程资料关联：", "商品情况：",
)

# 这里只拦截指令性的验货或交易建议；“器件测试”“测试夹线”等商品用途或配件名称允许保留。
ADVICE_PATTERN = re.compile(
    r"面交.{0,100}(建议|可以|可|应)|"
    r"(测试|查看|交接|检查).{0,100}(时|前)|"
    r"试穿.{0,100}前|现场或装机后|使用前(需要|需|应)|"
    r"逐项测试|不只确认能够开机|核对.{0,100}后再决定"
)


def load_rows():
    return [
        json.loads(line)
        for line in SOURCE.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def test_all_commodity_descriptions_are_natural_and_complete():
    rows = load_rows()
    descriptions = [row["description"] for row in rows]

    assert len(rows) == 968
    assert len({row["id"] for row in rows}) == 968
    assert len(set(descriptions)) == 968
    # 简介只需完整表达商品事实，不再用六十字下限逼迫生成器拼接验货建议。
    assert min(len(description) for description in descriptions) >= 8
    assert max(len(description) for description in descriptions) <= 700
    assert all(not any(phrase in description for phrase in BANNED) for description in descriptions)
    assert all(not ADVICE_PATTERN.search(description) for description in descriptions)


def test_quality_report_and_cleanup_sql_cover_the_same_rows():
    rows = load_rows()
    report = json.loads(REPORT.read_text(encoding="utf-8"))
    sql = CLEANUP_SQL.read_text(encoding="utf-8")

    assert report["accepted"] is True
    assert report["commodityCount"] == 968
    assert report["descriptionLength"]["minimum"] >= 8
    assert sum(report["visibleInternalTextCounts"].values()) == 0
    assert sql.count("\n(20") == 968

    update_clause = sql.split("UPDATE commodity c", 1)[1].split(";", 1)[0]
    for protected in (
        "commodityName", "degree", "commodityTypeId", "adminId", "isListed",
        "commodityInventory", "price", "viewNum", "favourNum", "createTime", "isDelete",
    ):
        assert f"c.{protected}=" not in update_clause
    assert "c.updateTime=s.originalUpdateTime" in update_clause
    assert "CURRENT_TIMESTAMP" not in update_clause


def test_real_search_regression_report_has_no_generated_advice():
    cases = json.loads(SEARCH_REPORT.read_text(encoding="utf-8"))
    items = [item for case in cases for item in case["topItems"]]

    assert all(case["badDescriptions"] == 0 for case in cases)
    assert all(case["wrongCategoryItems"] == 0 for case in cases)
    assert all(not ADVICE_PATTERN.search(item["commodityDescription"] or "") for item in items)

    by_case = {case["case"]: case for case in cases}
    assert by_case["kettle"]["topItems"][0]["commodityDescription"] == (
        "小熊电热水壶，宿舍用电热水壶，容量 1.5L，烧水正常。"
    )
    assert by_case["headphones"]["topItems"][0]["commodityDescription"] == (
        "索尼 WH-1000XM5 降噪耳机，头戴式无线降噪耳机，30小时续航，"
        "支持快充，日常使用正常，主要想换新的了。"
    )


def test_post_aligned_seed_contains_only_product_facts():
    namespace = runpy.run_path(str(POST_SEED_SOURCE))
    rows = namespace["ROWS"]
    description_for = namespace["description_for"]
    descriptions = [description_for(row) for row in rows]
    sql = POST_SEED_SQL.read_text(encoding="utf-8")

    # 种子定义已彻底移除第九列验货建议，避免以后被误拼进商品简介。
    assert len(rows) == 60
    assert all(len(row) == 8 for row in rows)
    assert all(not ADVICE_PATTERN.search(description) for description in descriptions)
    assert all(description in sql for description in descriptions)
