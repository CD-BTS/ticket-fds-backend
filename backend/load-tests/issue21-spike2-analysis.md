# Issue #21: second Spike Test (20s peak)

RUN_ID: cb71518d-91c1-4a3c-ae67-31147d4792ec
Status: COMPLETED; k6 exit 0; metric duration 52.14s.

- Status COMPLETED; HTTP errors 0, network errors 0, connection refusals 0.
- Longer peak vs first run: starts/s -27.11%; client-confirmed/s -25.82%; average latency 33.39%; p95 66.34%. Single-run/shared-host confounders prevent attributing all differences solely to the longer peak.
- Interrupted response confirmations 449; persisted without client confirmation 449. They match=true; duplicate key groups 0. Cancellation is not counted as server HTTP/network failure.
- Late recovery vs own initial baseline: average latency 8.12%; p95 -1.36%; throughput -8.42%; error rate 0%. Evaluate these separately rather than claiming complete recovery.

Pre-run: system CPU 38.00%; available RAM 435.08 MiB; authenticated server GET OK=true.

| Phase | Seconds | Starts/s | Confirmed/s | Avg ms | p95 ms | Success % | Error % | HTTP errors | Network errors | Refused | Interrupted |
|---|---|---|---|---|---|---|---|---|---|---|---|
| baseline | 0-10 | 305.30 | 305.30 | 163.91 | 275.99 | 100.00 | 0.00 | 0 | 0 | 0 | 0 |
| surge | 10-11 | 691.00 | 691.00 | 964.69 | 1627.07 | 100.00 | 0.00 | 0 | 0 | 0 | 0 |
| peak | 11-31 | 220.35 | 199.25 | 2231.19 | 3281.16 | 100.00 | 0.00 | 0 | 0 | 0 | 422 |
| decline | 31-32 | 34.00 | 7.00 | 2025.06 | 2433.53 | 100.00 | 0.00 | 0 | 0 | 0 | 27 |
| recovery | 32-52 | 259.90 | 259.90 | 183.43 | 280.25 | 100.00 | 0.00 | 0 | 0 | 0 | 0 |

| Phase | First request s | Last request s | Active VU min / median / max | DB events |
|---|---|---|---|---|
| baseline | 0.007 | 9.994 | 50 / 50 / 50 | 3053 |
| surge | 10 | 10.998 | 50 / 278 / 499 | 691 |
| peak | 11.001 | 30.986 | 500 / 500 / 500 | 4407 |
| decline | 31.002 | 31.931 | 82 / 350.5 / 500 | 34 |
| recovery | 32.103 | 51.996 | 50 / 50 / 50 | 5198 |

| Recovery window | Confirmed/s | Avg ms | p95 ms | Error % |
|---|---|---|---|---|
| early (32-42s) | 240.20 | 190.65 | 296.58 | 0.00 |
| late (42-52s) | 279.60 | 177.22 | 272.25 | 0.00 |

Late recovery vs own baseline: average 8.12%; p95 -1.36%; throughput -8.42%.

| Phase | First starts/s | Second starts/s | First avg ms | Second avg ms | First p95 ms | Second p95 ms | First error % | Second error % |
|---|---|---|---|---|---|---|---|---|
| baseline | 287.20 | 305.30 | 174.53 | 163.91 | 317.50 | 275.99 | 0.00 | 0.00 |
| surge | 694.00 | 691.00 | 903.87 | 964.69 | 1393.29 | 1627.07 | 0.00 | 0.00 |
| peak | 302.30 | 220.35 | 1672.73 | 2231.19 | 1972.61 | 3281.16 | 0.00 | 0.00 |
| decline | 145.00 | 34.00 | 1556.86 | 2025.06 | 1709.89 | 2433.53 | 0.00 | 0.00 |
| recovery | 229.90 | 259.90 | 209.36 | 183.43 | 304.12 | 280.25 | 0.00 | 0.00 |

| Resource | First avg | Second avg | First max | Second max |
|---|---|---|---|---|
| systemCpuPercent | 94.78 | 96.03 | 100.00 | 100.00 |
| serverCpuPercent | 17.00 | 15.84 | 21.95 | 22.79 |
| k6CpuPercent | 4.52 | 4.57 | 11.51 | 14.86 |
| serverWorkingSetMiB | 141.36 | 154.87 | 145.78 | 181.53 |
| serverPrivateMiB | 435.93 | 479.04 | 436.88 | 496.02 |
| k6WorkingSetMiB | 286.22 | 292.61 | 294.27 | 302.90 |
| systemUsedMemoryMiB | 7458.69 | 7565.62 | 7690.85 | 7741.61 |
| systemAvailableMemoryMiB | 434.78 | 327.85 | 599.08 | 527.31 |

Second-run samples 30; minimum free RAM 151.86 MiB vs first-run 202.62 MiB.

| DB phase | Total | Service | Run | Distinct run | Duplicate groups |
|---|---|---|---|---|---|
| before_auth | 188742 | 168469 | 0 | 0 | 0 |
| before_load | 188743 | 168470 | 0 | 0 | 0 |
| after_load | 202126 | 181853 | 13383 | 13383 | 0 |

Load-added 13383; including authentication 13384; client-confirmed 12934; interrupted 449; persisted without confirmation 449; DB duplicates 0.

Server postcheck: authenticatedGetOk=true, listening=true, activeK6Count=0.

- Exactly one second Spike execution, maximum 500 VU. Same event API authentication, 100 users, DB verification, resource sampling, dashboard options and zero-second ramp-down grace. First-run artifacts are preserved.
- Scenario: baseline 0-10s (50 VU), surge 10-11s (50 to 500), peak 11-31s (500), decline 31-32s (500 to 50), recovery 32-52s (50). Request-start timing and active-VU observations are saved for each phase and scenario second.
- RPS per phase means request starts / phase seconds. Confirmed/s is client-confirmed successes / phase seconds. Both attribute responses to start phase and may include responses completed after phase end. Whole-test k6 elapsed-time RPS is a different metric.
- Zero-second ramp-down grace preserves the one-second decline by cancelling retiring VU requests. Interrupted attempts are excluded from completed-response success/error/latency metrics and must not be reported as server HTTP or network failures. DB phase counts determine whether they nevertheless persisted.
- If safety-stopped, report partial observations only; no retry. Safety thresholds remain available physical RAM below 64 MiB, Java process exit, authenticated health GET failure or 10s timeout. Process deadline is 85s to accommodate the longer schedule and reporting.
- k6 and Spring Boot share this 8-logical-CPU Windows PC with MySQL/Docker and other programs. CPU competition, memory pressure, different pre-run state and warm caches confound comparisons. This single longer-peak run cannot isolate a causal duration effect or establish server-only capacity.
- Resource statistics are unweighted sample averages/maxima. One-second sleep plus CIM overhead gives irregular sample intervals. Memory is resident/private committed memory, not JVM heap; CPU is normalized to all logical CPUs.
- Recovery is compared both with this run baseline and with the first-run recovery windows. Mean, p95, throughput and errors must be considered separately; no arbitrary recovery latency SLA was introduced.
- Existing DB records and server performance settings were not modified. Authentication preflight and workload add new events; authenticated health GETs do not. No Redis or higher VU test was introduced.

Artifacts: load-tests/issue21-spike2-dashboard.html, issue21-spike2-analysis.json, issue21-spike2-timeline.json, and results-spike2-cb71518d-91c1-4a3c-ae67-31147d4792ec.* (raw reports, resources, DB/phase counts, precheck/execution/health/postcheck, logs).
