# Issue #21: 500 VU x 30 seconds final analysis

RUN_ID: 4f8fbf55-c380-412f-b2cc-f0c0325e1120

Completed; k6 exit code 99 (threshold violation), not a process crash. Spring Boot remained running.

| Metric | Value |
|---|---:|
| attempts | 10,253.00 |
| created | 9,991.00 |
| plannedRps | 341.77 |
| actualRps | 319.37 |
| createdRps | 333.03 |
| averageMs | 1,499.35 |
| p95Ms | 2,040.41 |
| successPercent | 97.44 |
| errorPercent | 2.56 |
| httpFailures | 0.00 |
| networkFailures | 262.00 |

Failures: 262 network failures, HTTP status 0, error_code 1212, message `dial: connection refused`. All were logged at startup (20:10:37 +09:00); HTTP errors: 0; response validation failures: 0.

| Resource | Sample average | Sample maximum |
|---|---:|---:|
| systemCpuPercent | 91.35 | 100.00 |
| serverCpuPercent | 18.76 | 23.46 |
| serverWorkingSetMiB | 177.91 | 185.71 |
| serverPrivateMiB | 432.45 | 436.25 |
| systemUsedMemoryMiB | 7,447.68 | 7,502.34 |
| k6CpuPercent | 5.62 | 27.09 |
| k6WorkingSetMiB | 242.78 | 264.95 |

| DB phase | All events | Service events | Run events | Distinct run events | Duplicate key groups |
|---|---:|---:|---:|---:|---:|
| before_auth | 163921 | 143648 | 0 | 0 | 0 |
| before_load | 163922 | 143649 | 0 | 0 | 0 |
| after_load | 173913 | 153640 | 9991 | 9991 | 0 |

Load-only DB delta: 9,991, matching successful API responses. Including the authenticated preflight: 9,992. Run has 9,991 distinct external event IDs; duplicate groups across the entire table: 0.

| VU | Request RPS (count/30) | Created/s | Avg ms | p95 ms | Success % | Error % | Network failures |
|---|---:|---:|---:|---:|---:|---:|---:|
| 200 | 189.27 | 189.27 | 1,067.89 | 2,004.99 | 100.00 | 0.00 | 0 |
| 300 | 279.27 | 279.27 | 1,085.71 | 1,531.02 | 100.00 | 0.00 | 0 |
| 400 | 196.97 | 196.97 | 2,074.65 | 3,389.87 | 100.00 | 0.00 | 0 |
| 500 | 341.77 | 333.03 | 1,499.35 | 2,040.41 | 97.44 | 2.56 | 262 |

400 -> 500 VU: request RPS 73.51%; created/s 69.08%; mean latency -27.73%; p95 -39.81%.

Execution evidence: Java PID 6324, k6 PID 30652; same Windows computer; API origin http://localhost:8080. No active k6 after completion.

Only one 500 VU x 30 second load run was executed. Initial root DB authentication failed before preflight/load; the existing runtime DB credentials were then used via stdin without changing credentials or saving secrets.
Same 400 VU workload, dashboard=true, open=false, port=-1, period=5s, 100 users and one-second sleep plus CIM resource collection. Extra DB queries were executed outside the load interval; k6 CPU/memory sampling was added.
All 262 logged failures occurred at 20:10:37 +09:00 (startup). They match the network failure counter. HTTP status=0 and error_code=1212 with dial: connection refused; no HTTP error responses or validation failures were recorded. Request Failed warnings and EVENT FAILURE lines describe the same requests, not separate failures.
Connection refusal is observed evidence; accept-queue overflow, server scheduling or other OS causes cannot be distinguished by this test. The Java PID/start time remained unchanged and port 8080 was listening after the test.
k6 and Spring Boot ran as local processes on the same Windows PC using localhost:8080 and 8 logical CPUs. They compete with each other, MySQL/Docker and other processes for CPU and memory; resource sampling also adds overhead. High system CPU does not establish Java-only CPU saturation.
500 VU delivered higher throughput and lower latency than the 400 VU single run but failed the success>=99% and error<=1% criteria. The non-monotonic throughput and lower whole-system CPU average argue against treating these single runs as a reliable server capacity curve. Fast failed connections are included in aggregate latency and total-request RPS; successful created/s is reported separately.
Resource statistics are unweighted sample averages/maxima in the first 30 seconds after launching k6, including startup. Sampling intervals exceed one second because CIM collection takes time. CPU is normalized to all 8 logical CPUs; resident and private committed memory are not Java heap measurements.
No existing DB records were updated/deleted; no server performance settings changed. New events were created by the authenticated preflight and load workload. No 1000 VU test was run.
