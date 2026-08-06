import json
from pathlib import Path

import yaml

from app.rag.models import DocumentMeta

MANIFEST_FILES = (
    "normalized/platform_rag_document_manifest.jsonl",
    "normalized/campus_guidance_manifest.jsonl",
    "normalized/rag_document_manifest.jsonl",
)


def load_document_metas(knowledge_root: Path) -> list[DocumentMeta]:
    records: list[DocumentMeta] = []
    seen_ids: set[str] = set()

    for relative_manifest in MANIFEST_FILES:
        manifest_path = knowledge_root / relative_manifest
        for line in manifest_path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            raw = json.loads(line)
            meta = DocumentMeta.model_validate(raw)
            if meta.document_id in seen_ids:
                raise ValueError(f"重复 document_id：{meta.document_id}")
            seen_ids.add(meta.document_id)
            _validate_document(knowledge_root, meta)
            records.append(meta)

    return records


def _validate_document(knowledge_root: Path, meta: DocumentMeta) -> None:
    root = knowledge_root.resolve()
    path = (root / meta.relative_path).resolve()
    if not path.is_relative_to(root / "documents" / "effective"):
        raise ValueError(f"非 effective 文档不得建索引：{meta.relative_path}")
    if not path.is_file():
        raise ValueError(f"文档不存在：{meta.relative_path}")

    text = path.read_text(encoding="utf-8")
    if not text.startswith("---\n"):
        raise ValueError(f"文档缺少 front matter：{meta.relative_path}")
    _, front_matter, _ = text.split("---", 2)
    header = yaml.safe_load(front_matter)
    #把文件开头的yaml内容转换为字典，检查文件内容和jsonl的meta信息是否一致
    for key in ("document_id", "category", "status", "title"):
        if header.get(key) != getattr(meta, key):
            raise ValueError(f"manifest 与文档 {key} 不一致：{meta.relative_path}")
