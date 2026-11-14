# Issue #21: 200 / 300 / 400 / 500 / 1000 VU comparison

Planned workloads: 30 seconds, 100 reused users, existing API authentication. 1000 VU was STOPPED EARLY because available physical RAM fell below 64 MiB; its partial results are not a completed 30-second test.

| VU | Status | Attempts | Completed requests | RPS (count/30) | k6 RPS | Avg ms | p95 ms | Success % | Error % | Network failures |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 200 | Completed | 5678 | 5678 | 189.27 | 184.47 | 1,067.89 | 2,004.99 | 100.00 | 0.00 | 0 |
| 300 | Completed | 8378 | 8378 | 279.27 | 270.73 | 1,085.71 | 1,531.02 | 100.00 | 0.00 | 0 |
| 400 | Completed | 5909 | 5909 | 196.97 | 186.19 | 2,074.65 | 3,389.87 | 100.00 | 0.00 | 0 |
| 500 | Completed | 10253 | 10253 | 341.77 | 319.37 | 1,499.35 | 2,040.41 | 97.44 | 2.56 | 262 |
| 1000 | ABORTED | 4213 | 3214 | N/A | 239.44 | 2,782.71 | 4,591.98 | 77.66 | 22.34 | 718 |

| VU | Samples | Java CPU avg/max % | Java resident avg/max MiB | Java private avg/max MiB | System CPU avg/max % | System used RAM avg/max MiB |
|---|---:|---:|---:|---:|---:|---:|
| 300 | 11 | 16.42/20.25 | 145.46/149.20 | 372.59/372.88 | 95.18/100.00 | 7,531.52/7,736.80 |
| 400 | 8 | 12.58/18.56 | 121.69/127.65 | 372.51/372.83 | 96.25/100.00 | 7,483.77/7,573.66 |
| 500 | 17 | 18.76/23.46 | 177.91/185.71 | 432.45/436.25 | 91.35/100.00 | 7,447.68/7,502.34 |
| 1000 | 5 | 12.14/14.06 | 205.62/220.25 | 436.40/436.80 | 96.80/100.00 | 7,754.18/7,878.03 |

200 -> 300 VU: throughput 47.55%; mean latency 1.67%; p95 -23.64%.

300 -> 400 VU: throughput -29.47%; mean latency 91.09%; p95 121.41%.

400 -> 500 VU: throughput 73.51%; mean latency -27.73%; p95 -39.81%.

Planned RPS includes graceful completions divided by 30 seconds; actualRps uses k6 elapsed time. Dashboard RPS has its own reporting interval and can differ.
HTML metrics match JSON within dashboard rounding (0.1 ms latency tolerance).
Resources use samples in the first 30 seconds after process launch, including startup; dashboard shutdown waiting is excluded. One second sleep plus Windows CIM collection overhead means actual intervals exceed one second. CPU percentages are normalized to the whole machine (8 logical CPUs).
200 VU resource measurements are unavailable. 300 VU exit code was not captured; its process exited and all thresholds passed.
No existing DB records were updated/deleted and no server tuning changed; normal test POST requests and authentication probes created test events. Sequential single runs cannot establish a reproducible server limit; system saturation and shared load-generator resources affect comparison.

Verified files:
- load-tests/results-stress-200-b6536e3d-f6a4-4a3f-9f49-da299e3a732f.md
- load-tests/results-stress-200-b6536e3d-f6a4-4a3f-9f49-da299e3a732f.json
- load-tests/results-stress-300-d7670ad9-e74a-4323-aa99-fded01c09d19.md
- load-tests/results-stress-300-d7670ad9-e74a-4323-aa99-fded01c09d19.json
- load-tests/issue21-stress-300-dashboard.html
- load-tests/results-stress-300-d7670ad9-e74a-4323-aa99-fded01c09d19.resources.csv
- load-tests/results-stress-300-d7670ad9-e74a-4323-aa99-fded01c09d19.resources.json
- load-tests/results-stress-400-62ea94d5-f972-41bb-b90b-9e995a879da4.md
- load-tests/results-stress-400-62ea94d5-f972-41bb-b90b-9e995a879da4.json
- load-tests/issue21-stress-400-dashboard.html
- load-tests/results-stress-400-62ea94d5-f972-41bb-b90b-9e995a879da4.resources.csv
- load-tests/results-stress-400-62ea94d5-f972-41bb-b90b-9e995a879da4.resources.json
- load-tests/results-stress-500-4f8fbf55-c380-412f-b2cc-f0c0325e1120.md
- load-tests/results-stress-500-4f8fbf55-c380-412f-b2cc-f0c0325e1120.json
- load-tests/issue21-stress-500-dashboard.html
- load-tests/results-stress-500-4f8fbf55-c380-412f-b2cc-f0c0325e1120.resources.csv
- load-tests/results-stress-500-4f8fbf55-c380-412f-b2cc-f0c0325e1120.resources.json
- load-tests/results-stress-1000-48d87476-3e28-4d24-b35d-ac35f071b4ab.md
- load-tests/results-stress-1000-48d87476-3e28-4d24-b35d-ac35f071b4ab.json
- load-tests/issue21-stress-1000-dashboard.html
- load-tests/results-stress-1000-48d87476-3e28-4d24-b35d-ac35f071b4ab.resources.csv
- load-tests/results-stress-1000-48d87476-3e28-4d24-b35d-ac35f071b4ab.resources.json

1000 VU: 13.42 seconds of partial metrics; success/error rates use 3,214 completed responses. 999 attempts were interrupted and are excluded from those rates. The DB contains 3,495 run events versus 2,496 client-confirmed successes, a difference of 999. Do not use counts divided by planned 30 seconds as measured throughput for this aborted run.
