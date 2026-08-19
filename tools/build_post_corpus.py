"""Validate the curated campus Post corpus and deterministically build seed SQL.

Runtime truth remains MySQL. The reviewed authoring source is
``tools/post_corpus/posts.jsonl``; this builder never calls a model.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
CORPUS_DIR = Path(__file__).resolve().parent / "post_corpus"
POSTS_PATH = CORPUS_DIR / "posts.jsonl"
HEADER_PATH = CORPUS_DIR / "seed_header.sql"
REPORT_PATH = CORPUS_DIR / "quality_report.json"
SEED_SQL_PATH = ROOT / "market_backend/sql/20260815_seed_campus_trade_posts.sql"

EXPECTED_TOPICS = {
    "digital": 46,
    "course_office": 52,
    "dorm_daily": 46,
    "clothing_sports_travel": 30,
    "books_food_beauty_pet": 38,
    "transaction": 48,
}
EXPECTED_BANDS = {"standard": 182, "detailed": 54, "comprehensive": 24}
BAND_LIMITS = {
    "standard": (675, None),
    "detailed": (990, None),
    "comprehensive": (1440, None),
}
EXPANSION_START_INDEX = 180
ALLOWED_COMMODITY_TYPES = {
    "数码家电类", "课本书籍类", "办公用品类", "电器类",
    "日常用品类", "服装鞋帽类", "食品类", "宠物用品类",
}
NOTEBOOK_TERMS = ("笔记本", "电脑", "MacBook", "游戏本", "轻薄本")
BANNED_PHRASES = (
    "作为 AI", "作为AI", "综上所述", "在当今社会", "来都来了",
    "价格应该跟着事实走", "能在不对劲的时候转头走",
    "同校身份只能说明见面方便", "发现这类情况也别上来就乱砍",
    "我会先停下来核对这几步", "古玩", "古董", "文玩", "收藏品",
)
ACTION_TERMS = (
    "检查", "确认", "核对", "测试", "查看", "测量", "计算", "记录", "观察",
    "询问", "退出", "停止", "放弃", "拒绝", "更换", "拆开", "插拔", "运行",
    "拍摄", "对照", "预留", "估算", "验证", "清洁", "闻", "按下", "连接",
    "保存", "清空", "列出", "说明", "移出", "保留", "复查", "提交", "整理", "分类",
)


@dataclass(frozen=True)
class SimilarityResult:
    score: float
    left: str | None
    right: str | None


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def han_count(value: str) -> int:
    return len(re.findall(r"[\u4e00-\u9fff]", value))


def normalized_text(value: str) -> str:
    return re.sub(r"\s+", "", value)


def character_shingles(value: str, width: int) -> set[str]:
    value = normalized_text(value)
    return {value[index:index + width] for index in range(max(0, len(value) - width + 1))}


def maximum_similarity(posts: list[dict[str, Any]], width: int) -> SimilarityResult:
    sets = [character_shingles(item["content"], width) for item in posts]
    best = SimilarityResult(0.0, None, None)
    for left in range(len(posts)):
        for right in range(left + 1, len(posts)):
            union = sets[left] | sets[right]
            score = len(sets[left] & sets[right]) / len(union) if union else 0.0
            if score > best.score:
                best = SimilarityResult(score, posts[left]["title"], posts[right]["title"])
    return best


def compact_unit(value: str) -> str:
    return re.sub(r"[\s`*_#>·•\-—:：,，;；]+", "", value).strip()


def repeated_units(posts: list[dict[str, Any]]) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    paragraphs: dict[str, list[str]] = collections.defaultdict(list)
    sentences: dict[str, list[str]] = collections.defaultdict(list)
    for post in posts:
        title = post["title"]
        for paragraph in re.split(r"\n\s*\n", post["content"]):
            normalized = compact_unit(paragraph)
            if han_count(normalized) >= 35 and not paragraph.lstrip().startswith("##"):
                paragraphs[normalized].append(title)
        for sentence in re.split(r"[。！？!?\n]+", post["content"]):
            normalized = compact_unit(sentence)
            if han_count(normalized) >= 24 and not sentence.lstrip().startswith("##"):
                sentences[normalized].append(title)
    return (
        {key: value for key, value in paragraphs.items() if len(set(value)) > 1},
        {key: value for key, value in sentences.items() if len(set(value)) > 2},
    )


def title_family(title: str) -> str:
    # Natural titles may share a product noun; this gate catches templated openings.
    cleaned = re.sub(r"[0-9A-Za-z]+", "X", title)
    cleaned = re.sub(r"[：:，,。！？?、《》“”\s]", "", cleaned)
    return cleaned[:10]


def validate(posts: list[dict[str, Any]]) -> dict[str, Any]:
    errors: list[str] = []
    if len(posts) != 260:
        errors.append(f"expected 260 posts, got {len(posts)}")
    indexes = [item.get("index") for item in posts]
    if indexes != list(range(260)):
        errors.append("indexes must be exactly 0..259 in order")

    titles = [str(item.get("title", "")).strip() for item in posts]
    if len(set(titles)) != len(titles):
        errors.append("titles are not unique")
    if any(not title or len(title) > 80 for title in titles):
        errors.append("title is empty or longer than 80 characters")

    content_hashes = [hashlib.sha256(item.get("content", "").encode("utf-8")).hexdigest() for item in posts]
    if len(set(content_hashes)) != len(content_hashes):
        errors.append("duplicate content hash")

    topic_counts = collections.Counter(item.get("topic") for item in posts)
    band_counts = collections.Counter(item.get("lengthBand") for item in posts)
    if dict(topic_counts) != EXPECTED_TOPICS:
        errors.append(f"topic quota mismatch: {dict(topic_counts)}")
    if dict(band_counts) != EXPECTED_BANDS:
        errors.append(f"length band quota mismatch: {dict(band_counts)}")

    lengths: list[int] = []
    heading_counts: list[int] = []
    for item in posts:
        content = str(item.get("content", "")).strip()
        length = han_count(content)
        lengths.append(length)
        band = item.get("lengthBand")
        if band not in BAND_LIMITS:
            errors.append(f"post {item.get('index')} has invalid length band {band!r}")
        else:
            low, _ = BAND_LIMITS[band]
            if length < low:
                errors.append(f"post {item.get('index')} Han count {length} below minimum {low}")
        tags = item.get("tags")
        if not isinstance(tags, list) or not 3 <= len(tags) <= 6 or len(set(tags)) != len(tags):
            errors.append(f"post {item.get('index')} has invalid tags")
        if any(not isinstance(tag, str) or not tag.strip() or len(tag) > 20 for tag in tags or []):
            errors.append(f"post {item.get('index')} has malformed tag")
        if item.get("index", -1) >= EXPANSION_START_INDEX:
            commodity_type = str(item.get("commodityType", "")).strip()
            if commodity_type not in ALLOWED_COMMODITY_TYPES:
                errors.append(
                    f"post {item.get('index')} has unsupported commodity type {commodity_type!r}"
                )
            if not str(item.get("productFamily", "")).strip():
                errors.append(f"post {item.get('index')} has no product family")
            if not str(item.get("intent", "")).strip():
                errors.append(f"post {item.get('index')} has no retrieval intent")
            identity_text = " ".join(str(item.get(key, "")) for key in ("title", "subtopic", "readerQuestion"))
            if any(term in identity_text for term in NOTEBOOK_TERMS):
                errors.append(f"post {item.get('index')} adds another notebook/computer topic")
        headings = re.findall(r"(?m)^##\s+\S.+$", content)
        heading_counts.append(len(headings))
        if not 2 <= len(headings) <= 6:
            errors.append(f"post {item.get('index')} has {len(headings)} H2 headings")
        found_banned = [phrase for phrase in BANNED_PHRASES if phrase in content or phrase in titles[item["index"]]]
        if found_banned:
            errors.append(f"post {item.get('index')} contains banned phrases: {found_banned}")
        action_count = sum(1 for term in ACTION_TERMS if term in content)
        if action_count < 5:
            errors.append(f"post {item.get('index')} has too few actionable terms ({action_count})")

    total_han = sum(lengths)
    if total_han < 280_000:
        errors.append(f"total Han count too low: {total_han}")

    families = collections.Counter(title_family(title) for title in titles)
    repeated_families = {key: value for key, value in families.items() if value > 3}
    if repeated_families:
        errors.append(f"title family used more than three times: {repeated_families}")

    duplicate_paragraphs, duplicate_sentences = repeated_units(posts)
    if duplicate_paragraphs:
        errors.append(f"repeated long paragraphs: {len(duplicate_paragraphs)}")
    if duplicate_sentences:
        errors.append(f"long sentences appearing in more than two posts: {len(duplicate_sentences)}")

    similarity20 = maximum_similarity(posts, 20)
    similarity4 = maximum_similarity(posts, 4)
    if similarity20.score >= 0.35:
        errors.append(f"20-shingle similarity too high: {similarity20}")
    if similarity4.score >= 0.18:
        errors.append(f"4-shingle similarity too high: {similarity4}")

    report = {
        "postCount": len(posts),
        "uniqueTitleCount": len(set(titles)),
        "authorCount": len(set(item.get("authorAccount") for item in posts)),
        "topicCounts": dict(topic_counts),
        "lengthBandCounts": dict(band_counts),
        "hanCount": {
            "total": total_han,
            "minimum": min(lengths, default=0),
            "maximum": max(lengths, default=0),
            "average": round(total_han / len(lengths), 2) if lengths else 0,
        },
        "headingCount": {
            "minimum": min(heading_counts, default=0),
            "maximum": max(heading_counts, default=0),
        },
        "similarity": {
            "legacy20Shingle": {
                "maximum": round(similarity20.score, 6),
                "pair": [similarity20.left, similarity20.right],
                "limitExclusive": 0.35,
            },
            "strict4Shingle": {
                "maximum": round(similarity4.score, 6),
                "pair": [similarity4.left, similarity4.right],
                "limitExclusive": 0.18,
            },
        },
        "repeatedLongParagraphCount": len(duplicate_paragraphs),
        "repeatedLongSentenceCount": len(duplicate_sentences),
        "titleFamilyOveruse": repeated_families,
        "corpusSha256": hashlib.sha256(POSTS_PATH.read_bytes()).hexdigest() if POSTS_PATH.exists() else None,
        "errors": errors,
        "accepted": not errors,
    }
    if errors:
        raise ValueError(json.dumps(report, ensure_ascii=False, indent=2))
    return report


def sql_literal(value: str) -> str:
    return "_utf8mb4'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def create_seed_sql(posts: list[dict[str, Any]]) -> str:
    header = HEADER_PATH.read_text(encoding="utf-8").rstrip() + "\n\n"
    blocks: list[str] = [header]
    for item in posts:
        index = item["index"]
        days = 151 + index
        tags = json.dumps(item["tags"], ensure_ascii=False, separators=(",", ":"))
        blocks.append(
            f"SET @seed_author_id = (SELECT `id` FROM `user` WHERE `userAccount` = {sql_literal(item['authorAccount'])} LIMIT 1);\n"
            "INSERT INTO `post` (`id`, `title`, `content`, `tags`, `thumbNum`, `favourNum`, `userId`, `createTime`, `updateTime`, `isDelete`)\n"
            f"SELECT @seed_post_id_base + {index} * @seed_snowflake_step, {sql_literal(item['title'])}, {sql_literal(item['content'])}, {sql_literal(tags)}, 0, 0, @seed_author_id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL {days} DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL {days} DAY), 0\n"
            "WHERE @seed_author_id IS NOT NULL\n"
            "ON DUPLICATE KEY UPDATE\n"
            "  `title` = VALUES(`title`),\n"
            "  `content` = VALUES(`content`),\n"
            "  `tags` = VALUES(`tags`),\n"
            "  `updateTime` = CURRENT_TIMESTAMP,\n"
            "  `isDelete` = 0;\n"
        )

    blocks.append(
        "CREATE TEMPORARY TABLE `seed_post_content_guard` (\n"
        "  `isValid` TINYINT NOT NULL,\n"
        "  CONSTRAINT `chk_seed_post_content_guard` CHECK (`isValid` = 1)\n"
        ");\n"
        "INSERT INTO `seed_post_content_guard` (`isValid`)\n"
        "SELECT CASE WHEN\n"
        "  (SELECT COUNT(*) FROM `post` p JOIN `user` u ON u.`id` = p.`userId`\n"
        "   WHERE p.`isDelete` = 0 AND u.`userAccount` REGEXP _utf8mb4'^seed_post_author_(0[1-9]|1[0-9]|20)$') = 260\n"
        "  AND (SELECT COUNT(DISTINCT p.`title`) FROM `post` p JOIN `user` u ON u.`id` = p.`userId`\n"
        "       WHERE p.`isDelete` = 0 AND u.`userAccount` REGEXP _utf8mb4'^seed_post_author_(0[1-9]|1[0-9]|20)$') = 260\n"
        "  AND (SELECT COALESCE(SUM(CHAR_LENGTH(p.`content`)), 0) FROM `post` p JOIN `user` u ON u.`id` = p.`userId`\n"
        "       WHERE p.`isDelete` = 0 AND u.`userAccount` REGEXP _utf8mb4'^seed_post_author_(0[1-9]|1[0-9]|20)$') >= 280000\n"
        "  AND NOT EXISTS (SELECT 1 FROM `post` p JOIN `user` u ON u.`id` = p.`userId`\n"
        "                  WHERE p.`isDelete` = 0 AND u.`userAccount` REGEXP _utf8mb4'^seed_post_author_(0[1-9]|1[0-9]|20)$'\n"
        "                    AND (NOT JSON_VALID(p.`tags`) OR JSON_LENGTH(p.`tags`) < 3 OR JSON_LENGTH(p.`tags`) > 6))\n"
        "THEN 1 ELSE 0 END;\n"
        "DROP TEMPORARY TABLE `seed_post_content_guard`;\n\n"
        "COMMIT;\n"
        "SET @seed_post_author_password_hash = NULL;\n"
    )
    return "\n".join(blocks)


def main(check_only: bool) -> None:
    posts = read_jsonl(POSTS_PATH)
    report = validate(posts)
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not check_only:
        SEED_SQL_PATH.write_text(create_seed_sql(posts), encoding="utf-8", newline="\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    main(args.check_only)
