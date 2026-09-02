# Golden v1.2.1 Frozen Baseline Summary

The frozen evaluation contains 200 cases: 140 public Dev cases and a withheld 60-case Test split. The following aggregate baseline was produced on the full frozen set (`golden-v1.2.1-reviewed-20260829`) on 2026-08-29, using the frozen index `20260819T151857Z-b1c54bb0e56f49e89251135abebc4c71` and the `gpt-5.6-terra` router and answer model.

## Retrieval (all 200 cases)

- Recall@1: 70.93%
- Recall@3: 87.21%
- Recall@5: 92.44%
- MRR: 0.769
- nDCG@5: 0.790

Retrieval metrics over the public Dev split only: Recall@5 91.74%, MRR 0.766, nDCG@5 0.786.

## Routing and end-to-end answers

- Router route accuracy: 94.00% (188/200)
- Successful generations: 200/200
- Final pass rate: 95.00% (190 pass, 10 fail)

## Domain results

| Domain | Pass rate | Retrieval Recall@5 |
|---|---:|---:|
| Boundary | 95.00% | 90.00% |
| Campus | 75.00% | 94.12% |
| Course | 98.57% | 92.86% |
| Platform | 95.00% | 85.00% |
| Post | 98.00% | 97.96% |

## Limitations

- Generation and judging used the same model family with different instructions and evidence windows.
- Commodity search and preference tools used fixed empty-inventory and cold-start fixtures.
- The evaluation is single-turn and does not cover multi-turn memory, rewrite retries, online latency, or live inventory changes.
- The public Dev split is not an independent benchmark once used for tuning; hidden Test cases and raw evaluation artifacts are not published.
