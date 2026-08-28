# Public Dev Annotation Guide

Each JSONL record describes one retrieval-routing case.

- `expectedRoute`: `retrieve`, `clarify`, `out_of_scope`, or `skip_rag`.
- `expectedKnowledgeState`: whether indexed evidence can answer the request.
- `preferredSourceType` and `allowedSourceTypes`: permitted GUIDE/Post evidence lanes.
- `qrels`: document-level graded relevance. Relevance 3 is core evidence, 2 is important support, 1 is background only, and 0 is disallowed or irrelevant.
- `required`: marks evidence that must be retrieved for the case to count as fully covered.
- `supportingChunkIds`: frozen evidence locations; document IDs remain the primary ranking unit.
- `mustNotUse` and `forbiddenDocumentPrefixes`: evidence exclusions.

GUIDE content takes priority for formal platform and course rules. Posts may support experience-oriented questions but cannot override formal rules. Real-time inventory, price, condition, and account-specific facts must not be inferred from static RAG documents.

The public records retain only a source label in `provenance` and a frozen status in `review`. Reviewer identities, review notes, construction details, and the hidden Test split are not published.
