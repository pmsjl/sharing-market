"""Static acceptance tests for the curated campus Post corpus."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = REPO_ROOT / "tools/build_post_corpus.py"
SPEC = importlib.util.spec_from_file_location("build_post_corpus", BUILDER_PATH)
assert SPEC is not None and SPEC.loader is not None
builder = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = builder
SPEC.loader.exec_module(builder)


def test_post_corpus_passes_all_quality_gates():
    posts = builder.read_jsonl(builder.POSTS_PATH)
    report = builder.validate(posts)
    assert report["accepted"] is True
    assert report["postCount"] == 260
    assert report["hanCount"]["total"] >= 275_000
    assert report["similarity"]["legacy20Shingle"]["maximum"] < 0.35
    assert report["similarity"]["strict4Shingle"]["maximum"] < 0.18
    assert (
        report["similarity"]["fullTextTfidfCosine"]["expansionMaximum"]
        < 0.08
    )
    assert report["repeatedLongParagraphCount"] == 0
    assert report["repeatedLongSentenceCount"] == 0


def test_purchase_experience_expansion_matches_platform_scope():
    posts = builder.read_jsonl(builder.POSTS_PATH)
    expansion = [
        post for post in posts
        if post.get("corpusRole") == "purchase_experience"
    ]
    assert len(expansion) == 80
    assert all(post["commodityType"] in builder.ALLOWED_COMMODITY_TYPES
               for post in expansion)
    assert all(not any(term in post["title"] for term in builder.NOTEBOOK_TERMS)
               for post in expansion)
    assert all(
        not any(term in post["title"] + post["content"]
                for term in builder.PURCHASE_FORBIDDEN_TERMS)
        for post in expansion
    )


def test_seed_sql_generation_is_deterministic_and_preserves_interactions():
    posts = builder.read_jsonl(builder.POSTS_PATH)
    first = builder.create_seed_sql(posts)
    second = builder.create_seed_sql(posts)
    assert first == second
    assert first.count("INSERT INTO `post`") == 260
    assert first.count("ON DUPLICATE KEY UPDATE") == 260
    update_clause = first.split("ON DUPLICATE KEY UPDATE", 1)[1].split(";", 1)[0]
    assert "`thumbNum`" not in update_clause
    assert "`favourNum`" not in update_clause
    assert "`createTime`" not in update_clause
    assert "`userId`" not in update_clause
