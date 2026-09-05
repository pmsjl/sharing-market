"""公开演示商品 seed 验收测试。"""
from __future__ import annotations

import re
import runpy
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
POST_SEED_SOURCE = REPO / "tools/post_aligned_commodity_seed.py"
POST_SEED_SQL = REPO / "market_backend/sql/seed/03_commodities.sql"
DEMO_USERS_SQL = REPO / "market_backend/sql/seed/01_demo_users.sql"
COMMODITY_TYPES_SQL = REPO / "market_backend/sql/seed/02_commodity_types.sql"

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


def test_post_aligned_seed_contains_only_product_facts():
    namespace = runpy.run_path(str(POST_SEED_SOURCE))
    rows = namespace["ROWS"]
    description_for = namespace["description_for"]
    descriptions = [description_for(row) for row in rows]
    sql = POST_SEED_SQL.read_text(encoding="utf-8")

    # 种子定义已彻底移除第九列验货建议，避免以后被误拼进商品简介。
    assert len(rows) == 60
    assert all(len(row) == 8 for row in rows)
    assert all(not any(phrase in description for phrase in BANNED)
               for description in descriptions)
    assert all(not ADVICE_PATTERN.search(description) for description in descriptions)
    assert all(description in sql for description in descriptions)
    assert sql == namespace["build_seed"]()


def test_public_demo_seed_has_all_dependencies():
    namespace = runpy.run_path(str(POST_SEED_SOURCE))
    rows = namespace["ROWS"]
    seller_id = namespace["SELLER_ID"]
    seller_account = namespace["SELLER_ACCOUNT"]
    users_sql = DEMO_USERS_SQL.read_text(encoding="utf-8")
    types_sql = COMMODITY_TYPES_SQL.read_text(encoding="utf-8")

    assert str(seller_id) in users_sql
    assert seller_account in users_sql
    assert users_sql.count("seed_post_author_") == 20
    assert "公开演示账号，不对应真实用户" in users_sql
    assert all(category in types_sql for category in {row[0] for row in rows})
