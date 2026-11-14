# Issue #21: 1000 VU partial test - safety stop

**ABORTED: not a completed 30-second test.** Available physical RAM fell below 64 MiB; k6 stopped through its REST API (exit 103). No retry or Spike Test.

RUN_ID: 48d87476-3e28-4d24-b35d-ac35f071b4ab

| Metric | Partial result |
|---|---:|
| Actual k6 metric duration (seconds) | 13.422856 |
| attempts | 4,213.00 |
| completedRequests | 3,214.00 |
| created | 2,496.00 |
| actualRps | 239.44 |
| averageMs | 2,782.71 |
| p95Ms | 4,591.98 |
| successPercent | 77.66 |
| errorPercent | 22.34 |
| networkFailures | 718.00 |
| httpFailures | 0.00 |

Interrupted attempts: 999. Success/error percentages use 3214 completed responses, excluding interrupted attempts. Network failures: 718 connection refusals; HTTP errors: 0.

| Resource | Sample average | Sample maximum |
|---|---:|---:|
| systemCpuPercent | 96.80 | 100.00 |
| serverCpuPercent | 12.14 | 14.06 |
| k6CpuPercent | 8.68 | 27.17 |
| serverWorkingSetMiB | 205.62 | 220.25 |
| serverPrivateMiB | 436.40 | 436.80 |
| k6WorkingSetMiB | 401.60 | 423.13 |
| systemUsedMemoryMiB | 7,754.18 | 7,878.03 |

| DB phase | Total events | Service events | Run events | Distinct run events | Duplicate groups |
|---|---:|---:|---:|---:|---:|
| before_auth | 173913 | 153640 | 0 | 0 | 0 |
| before_load | 173914 | 153641 | 0 | 0 | 0 |
| after_load | 177409 | 157136 | 3495 | 3495 | 0 |

DB load delta 3495 matches unique run events. Including preflight: 3496 new rows. DB/client success difference 999 matches interrupted attempts.

| VU | Status | k6 elapsed-time RPS | Avg ms | p95 ms | Success % | Error % | Network failures |
|---|---|---:|---:|---:|---:|---:|---:|
| 200 | Completed (30s planned) | 184.47 | 1,067.89 | 2,004.99 | 100.00 | 0.00 | 0 |
| 300 | Completed (30s planned) | 270.73 | 1,085.71 | 1,531.02 | 100.00 | 0.00 | 0 |
| 400 | Completed (30s planned) | 186.19 | 2,074.65 | 3,389.87 | 100.00 | 0.00 | 0 |
| 500 | Completed (30s planned) | 319.37 | 1,499.35 | 2,040.41 | 97.44 | 2.56 | 262 |
| 1000 | ABORTED (13.42s) | 239.44 | 2,782.71 | 4,591.98 | 77.66 | 22.34 | 718 |

After stop: authenticated server GET HTTP 200 (6052.40 ms), original Java PID 6324 remained listening, no active k6.

One load execution only. Planned 1000 VU x 30s; stopped early after 13.422856 seconds of k6 metric time because available physical RAM fell below 64 MiB. No retry, restart, server tuning or Spike Test.
Same 500 VU authentication, 100 user IDs, event workload, 10s request timeout, dashboard=true/open=false/port=-1/period=5s and resource collection method. Added a loopback k6 REST stop API and occasional authenticated read-only GET health probes (10s timeout). These safety probes add a small amount of server work outside k6 request metrics.
k6 exit code 103 and log test run stopped from REST API confirm the safety stop. The fallback force-stop was not needed. CPU 100% alone was not a stop condition; the available RAM threshold triggered.
Completed response metrics: 3214 responses = 2496 client-confirmed successes + 718 network errors. 999 of 4213 started attempts were interrupted and excluded from success/error rate denominators and completed-request RPS. No HTTP error responses or response validation failures were recorded.
All 718 Request Failed warning lines report connection refusal. The script logged detailed EVENT FAILURE lines only on each VU first iteration (706 lines, error code 1212), so those detail lines are not a complete failure counter. Warnings and detail lines must not be added together.
DB contains 3495 unique run events, 999 more than client-confirmed successes. This exactly matches interrupted attempts; server processing can persist requests after k6 stops waiting for responses. 3495 DB rows + 718 refused connections account for all 4213 started attempts. This is not evidence of duplicate or missing records.
The raw k6 Markdown count/30 figures (140.43 attempted/s, 83.20 client-confirmed created/s) are not measured throughput because the run was aborted. Use completed-request RPS 239.44 over actual elapsed time for this partial observation; do not compare it as a completed 30s capacity test.
Same PC: Java PID 6324 and k6 PID 32572, 8 logical CPUs, API localhost:8080. Whole-system CPU and memory pressure include k6, Java, MySQL/Docker, monitoring and other programs. The observed limit includes shared-host resource competition and cannot isolate server-only capacity.
Resource averages are unweighted averages of 5 samples through the safety stop, including startup. One-second sleep plus CIM overhead caused irregular intervals. Memory denotes resident/private committed memory, not Java heap. CPU is normalized to all 8 logical processors.
Spring Boot retained its original PID/start time and was listening after the stop. Authenticated GET returned HTTP 200 with matching service/user, taking 6052.40 ms; reachability recovered but low-latency recovery is not established by this one check.
Existing DB records were not updated or deleted. Load added 3495 rows, authenticated POST preflight added one more. Health GET requests did not create records. HTML and JSON counters/rates/latencies match within dashboard rounding; dashboard RPS uses a separate reporting interval.

Safe-stop API reference: https://grafana.com/docs/k6/latest/reference/k6-rest-api/
