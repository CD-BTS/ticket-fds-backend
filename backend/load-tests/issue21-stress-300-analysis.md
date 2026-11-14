# Issue #21: 300 VU x 30 seconds

RUN_ID: d7670ad9-e74a-4323-aa99-fded01c09d19
Baseline RUN_ID: b6536e3d-f6a4-4a3f-9f49-da299e3a732f

| Metric | 200 VU | 300 VU |
|---|---:|---:|
| attempts | 5,678.00 | 8,378.00 |
| created | 5,678.00 | 8,378.00 |
| plannedRps | 189.27 | 279.27 |
| actualRps | 184.47 | 270.73 |
| averageMs | 1,067.89 | 1,085.71 |
| p95Ms | 2,004.99 | 1,531.02 |
| successPercent | 100.00 | 100.00 |
| errorPercent | 0.00 | 0.00 |
| httpFailedPercent | 0.00 | 0.00 |
| networkFailures | 0.00 | 0.00 |

RPS change: 47.55%; average latency change: 1.67%; p95 change: -23.64%.

Planned RPS = attempts / 30s, including graceful completions, matching the baseline report. Actual RPS uses the k6 elapsed-time metric.

Resource samples: first 30 seconds after launching k6, approximately the active phase including startup. Browser dashboard shutdown waiting is excluded. CPU is normalized to the whole machine (8 logical processors). Working set is resident memory; private memory is committed memory.

| Resource | Average | Maximum |
|---|---:|---:|
| serverCpuPercent | 16.42 | 20.25 |
| serverWorkingSetMiB | 145.46 | 149.20 |
| serverPrivateMiB | 372.59 | 372.88 |
| systemCpuPercent | 95.18 | 100.00 |
| systemUsedMemoryMiB | 7,531.52 | 7,736.80 |

No server tuning or database cleanup was performed. The existing POST workload and authenticated preflight create new test events; existing records were not updated or deleted.

Dashboard during execution: http://localhost:5665
HTML: load-tests/issue21-stress-300-dashboard.html
