import hashlib
import re
from pathlib import Path

from app.rag.models import DocumentMeta, KnowledgeChunk, PostSnapshot

H2_PATTERN = re.compile(r"(?m)^##\s+(.+?)\s*$")
COURSE_SECTIONS = {"教材", "参考资料", "软件环境", "实验器材"}


def read_body(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    if text.startswith("---\n"):
        _, _, text = text.split("---", 2)
        #这里sub为替换，搜索text将一级标题替换为空字符串
    return re.sub(r"(?m)^#\s+.+?\s*$", "", text, count=1).strip()


"""
按二级标题切分document
"""


def split_h2(body: str) -> list[tuple[str | None, str]]:

    matches = list(H2_PATTERN.finditer(body))
    if not matches:
        return [(None, body)]

    sections: list[tuple[str | None, str]] = []
    for index, match in enumerate(matches):
        start = match.end()
        end = matches[index +
                      1].start() if index + 1 < len(matches) else len(body)
        sections.append((match.group(1).strip(), body[start:end].strip()))
        # group(0)表示正则表达式匹配的完整内容，
        # 而group(1)表示的是正则表达式第一个分组（即第一个括号）所捕获的内容，
        # group(2)依次类推
        #这里的(.+?)是非贪婪匹配，空格会交给后面的\s*,\s是空白字符的意思

    return sections


def split_post_sections(body: str) -> list[tuple[str | None, str]]:
    """保留首个 H2 之前的开场，并按 H2 切分帖子正文。"""
    matches = list(H2_PATTERN.finditer(body))
    if not matches:
        return [(None, body)] if body else []

    sections: list[tuple[str | None, str]] = []
    introduction = body[:matches[0].start()].strip()
    if introduction:
        sections.append((None, introduction))
    for index, match in enumerate(matches):
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(body)
        content = body[start:end].strip()
        if content:
            sections.append((match.group(1).strip(), content))
    return sections


def chunk_document(
    knowledge_root: Path,
    meta: DocumentMeta,
) -> list[KnowledgeChunk]:
    body = read_body(knowledge_root / meta.relative_path)

    if meta.category == "course_purchase_policy":
        sections = [(None, body)]
    else:
        sections = split_h2(body)

    if meta.category == "course_materials":
        sections = [(section, content) for section, content in sections
                    if section in COURSE_SECTIONS]

    chunks: list[KnowledgeChunk] = []
    for section, content in sections:
        if not content:
            continue
        for part_index, part in enumerate(_split_long_section(content)):
            chunk_index = len(chunks)
            stable_key = f"{meta.document_id}|{section}|{part_index}"
            suffix = hashlib.sha256(stable_key.encode()).hexdigest()[:12]
            chunks.append(
                KnowledgeChunk(
                    chunk_id=f"{meta.document_id}#{suffix}",
                    document_id=meta.document_id,
                    source_type="GUIDE",
                    source_id=meta.document_id.removeprefix("GUIDE:"),
                    category=meta.category,
                    title=meta.title,
                    section=section,
                    chunk_index=chunk_index,
                    content=part,
                    embedding_text=_embedding_text(meta, section, part),
                    # 上方的数据则是用于结合content用来embedding
                    #这里的metadata用于后续query construction过滤
                    metadata={
                        "last_verified_at":
                        meta.last_verified_at,
                        "invalidation_condition":
                        meta.invalidation_condition,
                        "evidence_scope":
                        meta.evidence_scope,
                        "repo_id":
                        meta.repo_id,
                        "course_codes":
                        meta.course_codes,
                        "majors":
                        meta.majors,
                        "entry_years":
                        meta.entry_years,
                        "section_source_ids":
                        (meta.section_source_ids.get(section or "", [])),
                    },
                ))
    return chunks


def chunk_post(post: PostSnapshot) -> list[KnowledgeChunk]:
    """把一篇 Java Post 快照切成可检索且可追溯的稳定 chunk。"""
    body = re.sub(
        r"(?m)^#\s+.+?\s*$",
        "",
        post.content,
        count=1,
    ).strip()
    document_id = f"POST:{post.id}"
    chunks: list[KnowledgeChunk] = []
    for section, content in split_post_sections(body):
        for part_index, part in enumerate(_split_long_section(content)):
            stable_key = (
                f"{document_id}|{post.source_version}|{section}|{part_index}"
            )
            suffix = hashlib.sha256(stable_key.encode("utf-8")).hexdigest()[:12]
            chunks.append(
                KnowledgeChunk(
                    chunk_id=f"{document_id}#{suffix}",
                    document_id=document_id,
                    source_type="POST",
                    source_id=str(post.id),
                    category="community_post",
                    title=post.title,
                    section=section,
                    chunk_index=len(chunks),
                    content=part,
                    embedding_text="\n".join([
                        post.title,
                        "community_post",
                        *post.tags,
                        *([section] if section else []),
                        part,
                    ]),
                    metadata={
                        "sourceVersion": post.source_version,
                        "tags": post.tags,
                        "createTime": post.create_time,
                        "updateTime": post.update_time,
                    },
                )
            )
    return chunks


def _embedding_text(
    meta: DocumentMeta,
    section: str | None,
    content: str,
) -> str:
    labels = [meta.title, meta.category]
    if meta.topic:
        labels.append(meta.topic)
    if meta.course_codes:
        labels.extend(meta.course_codes)
    if meta.repo_id:
        labels.append(meta.repo_id)
    if section:
        labels.append(section)
    return "\n".join(labels + [content])


def _split_long_section(content: str, limit: int = 1200) -> list[str]:
    paragraphs = [
        part.strip() for part in content.split("\n\n") if part.strip()
    ]
    units: list[str] = []
    for paragraph in paragraphs:
        if len(paragraph) <= limit:
            units.append(paragraph)
            continue

        # 现有平台规则经常是连续项目符号，中间没有空行；
        # 单个“自然段”超长时继续按完整行拆，不能留下 2000 字大块。
        current_lines: list[str] = []
        current_length = 0
        for line in paragraph.splitlines():
            line_parts = [
                line[start:start + limit]
                for start in range(0, len(line), limit)
            ] or [""]
            for line_part in line_parts:
                added_length = len(line_part) + (1 if current_lines else 0)
                if current_lines and current_length + added_length > limit:
                    units.append("\n".join(current_lines))
                    current_lines = [line_part]
                    current_length = len(line_part)
                else:
                    current_lines.append(line_part)
                    current_length += added_length
        if current_lines:
            units.append("\n".join(current_lines))

    results: list[str] = []
    current = ""
    for unit in units:
        candidate = unit if not current else f"{current}\n\n{unit}"
        if current and len(candidate) > limit:
            results.append(current)
            current = unit
        else:
            current = candidate
    if current:
        results.append(current)
    return results
