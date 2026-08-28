# Golden v1.1 Frozen Baseline Summary

The frozen evaluation contains 200 cases: 140 public Dev cases and a withheld 60-case Test split. The following aggregate baseline was produced on the full frozen set on 2026-08-21.

## Retrieval

- Recall@1: 59.14%
- Recall@3: 74.19%
- Recall@5: 83.87%
- MRR: 0.621
- nDCG@5: 0.651

## End-to-end answers

- Successful generations: 200/200
- Final pass rate: 74.50% (149 pass, 51 fail)
- Knowledge-state accuracy: 94.00%

## Domain results

| Domain | Pass rate | Retrieval Recall@5 |
|---|---:|---:|
| Boundary | 75.00% | 90.00% |
| Campus | 90.00% | 100.00% |
| Course | 68.57% | 61.76% |
| Platform | 97.50% | 97.50% |
| Post | 58.00% | 96.00% |

## Limitations

- Generation and judging used the same model family with different instructions and evidence windows.
- Commodity search and preference tools used fixed empty-inventory and cold-start fixtures.
- The evaluation is single-turn and does not cover multi-turn memory, rewrite retries, online latency, or live inventory changes.
- The public Dev split is not an independent benchmark once used for tuning; hidden Test cases and raw evaluation artifacts are not published.
