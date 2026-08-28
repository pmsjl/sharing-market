# Public RAG Evaluation

This directory contains the public, reproducible portion of the Sharing Market RAG evaluation.

The published dataset contains 140 development cases with document-level qrels. The independent 60-case test split, raw model outputs, traces, request identifiers, usage records, human-review material, and historical run snapshots are intentionally withheld.

Public files:

- `public/dev_v1_1.jsonl`: sanitized development cases.
- `public/manifest.json`: counts, checksum, index configuration, and publication limits.
- `public/annotation_guideline.md`: label semantics.
- `public/benchmark_summary.md`: aggregate frozen-baseline results and limitations.
- `schemas/golden_case.schema.json`: JSON Schema for every public case.

Validate the bundle from the repository root:

```bash
python tools/validate_public_evaluation.py
```

The public Dev split is suitable for implementation checks and reproducibility. It must not be presented as an untouched hidden benchmark after being used for development.

On 2026-08-28, four public Dev cases received a reviewed scope correction. The filename remains `dev_v1_1.jsonl` for compatibility, while `public/manifest.json` identifies the corrected dataset version and exact Case IDs. Frozen metrics published from the 2026-08-21 pre-correction run remain historical until the corrected set is rerun.
