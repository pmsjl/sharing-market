# Golden v1.2.1 Human-Adjudicated Baseline Summary

The evaluation contains 200 cases: 140 public Dev cases and a withheld 60-case Test split. These metrics use the 2026-09-05 human-adjudicated truth, frozen index `20260819T151857Z-b1c54bb0e56f49e89251135abebc4c71`, current code `5d6dbfbb444353e4dbf4cd18661035c56624c392`, fresh outputs for the four changed cases, and single-case `v2_current_runtime` judging.

## Retrieval (all 200 cases)

- Recall@1: 74.29%
- Recall@3: 92.00%
- Recall@5: 92.57%
- MRR: 0.805
- nDCG@5: 0.814

Retrieval metrics over the public Dev split only: Recall@5 93.55%, MRR 0.821, nDCG@5 0.830.

## Routing and end-to-end answers

- Router route accuracy: 96.50% (193/200)
- Successful generations: 200/200
- Final pass rate: 97.00% (194 pass, 6 fail)

## Domain results

| Domain | Pass rate | Retrieval Recall@5 |
|---|---:|---:|
| Boundary | 100.00% | 100.00% |
| Campus | 100.00% | 88.24% |
| Course | 95.71% | 94.92% |
| Platform | 97.50% | 87.50% |
| Post | 96.00% | 93.88% |

## Limitations

- Generation and judging used the same model family with different instructions and evidence windows.
- Commodity search and preference tools used fixed empty-inventory and cold-start fixtures.
- The full 200-case totals recompose 196 unchanged prior records with four freshly rerun human-adjudicated records for each code version.
- The public Dev split is not an independent benchmark once used for tuning; hidden Test cases and raw evaluation artifacts are not published.
