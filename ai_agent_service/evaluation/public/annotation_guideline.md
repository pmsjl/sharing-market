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

Scope is determined from the actual query and reliable conversation context, not from isolated technical nouns. Performance, configuration, compatibility, and suitability questions are in scope only when the user explicitly connects them to purchasing, selecting, inspecting, or using a specific commodity. Pure programming, hardware-performance, and system-configuration questions are `out_of_scope`.

Campus lifecycle questions are in scope only when they explicitly concern acquiring, purchasing, reselling, transferring, disposing of, or checking the fit of personal items. General registration procedures, historical registration arrangements, vacation safety, and other campus administration questions are `out_of_scope`.

The public Dev bundle is published from the `golden-v1.2.1-reviewed-20260829` dataset, which incorporates the 2026-08-28 scope corrections and the 2026-08-29 truth alignment of twelve post-purchase cases. Historical benchmark numbers from the v1.1 bundle were not recomputed.

The public records retain only a source label in `provenance` and a frozen status in `review`. Reviewer identities, review notes, construction details, and the hidden Test split are not published.
