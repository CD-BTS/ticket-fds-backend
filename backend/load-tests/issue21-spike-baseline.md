# Issue #21: pre-Redis Spike Baseline

RUN_ID: 7e3352c8-b82d-4034-8048-6f250fa5d430
Status: COMPLETED, k6 exit 0, actual metric time 42.15s.

| Phase | Seconds | Starts/s | Confirmed/s | Avg ms | p95 ms | Success % | Error % | Interrupted |
|---|---|---|---|---|---|---|---|---|
| baseline | 0-10 | 287.20 | 287.20 | 174.53 | 317.50 | 100.00 | 0.00 | 0 |
| surge | 10-11 | 694.00 | 694.00 | 903.87 | 1393.29 | 100.00 | 0.00 | 0 |
| peak | 11-21 | 302.30 | 268.60 | 1672.73 | 1972.61 | 100.00 | 0.00 | 337 |
| decline | 21-22 | 145.00 | 32.00 | 1556.86 | 1709.89 | 100.00 | 0.00 | 113 |
| recovery | 22-42 | 229.90 | 229.90 | 209.36 | 304.12 | 100.00 | 0.00 | 0 |

| Phase | First request s | Last request s | Active VU min / median / max | DB events |
|---|---|---|---|---|
| baseline | 0.007 | 9.997 | 14 / 50 / 50 | 2872 |
| surge | 10.003 | 10.996 | 51 / 270.5 / 498 | 694 |
| peak | 11 | 20.999 | 499 / 500 / 500 | 3023 |
| decline | 21 | 21.971 | 64 / 394 / 500 | 145 |
| recovery | 22.002 | 41.998 | 50 / 50 / 50 | 4598 |

| Recovery | Confirmed/s | Avg ms | p95 ms | Success % | Error % |
|---|---|---|---|---|---|
| early | 207.30 | 224.67 | 341.17 | 100.00 | 0.00 |
| late | 252.50 | 196.79 | 272.98 | 100.00 | 0.00 |

Late recovery vs baseline: mean 12.75%, p95 -14.02%, confirmed/s -12.08%.

| Resource | Sample average | Sample maximum |
|---|---|---|
| systemCpuPercent | 94.78 | 100.00 |
| serverCpuPercent | 17.00 | 21.95 |
| k6CpuPercent | 4.52 | 11.51 |
| systemUsedMemoryMiB | 7458.69 | 7690.85 |
| systemAvailableMemoryMiB | 434.78 | 599.08 |
| serverWorkingSetMiB | 141.36 | 145.78 |
| serverPrivateMiB | 435.93 | 436.88 |
| k6WorkingSetMiB | 286.22 | 294.27 |

Resource samples: 18; minimum available physical memory 202.62 MiB.

| DB phase | Total | Service | Run | Distinct run | Duplicate groups |
|---|---|---|---|---|---|
| before_auth | 177409 | 157136 | 0 | 0 | 0 |
| before_load | 177410 | 157137 | 0 | 0 | 0 |
| after_load | 188742 | 168469 | 11332 | 11332 | 0 |

Load inserted 11332 events; including authentication preflight 11333. Client confirmed 10882, interrupted 450; every started request is present once in DB.

| Stress VU | Status | k6 elapsed RPS | Avg ms | p95 ms | Success % | Network errors |
|---|---|---|---|---|---|---|
| 200 | Completed | 184.47 | 1067.89 | 2004.99 | 100.00 | 0 |
| 300 | Completed | 270.73 | 1085.71 | 1531.02 | 100.00 | 0 |
| 400 | Completed | 186.19 | 2074.65 | 3389.87 | 100.00 | 0 |
| 500 | Completed | 319.37 | 1499.35 | 2040.41 | 97.44 | 262 |
| 1000 | ABORTED | 239.44 | 2782.71 | 4591.98 | 77.66 | 718 |

Spike peak: 302.30 starts/s, 268.60 confirmed/s, 1672.73ms mean, 1972.61ms p95. Do not equate this start-attributed phase rate to the Stress elapsed-time completion rate.

- Pre-Redis Baseline: one run, original local API authentication and 100 user IDs, existing server/DB tuning, unchanged persisted records. No Redis dependency or integration was found in build.gradle/src/main. New events are test-generated inserts only.
- One continuous ramping-vus scenario: 50 VU for 10s, ramp to 500 over 1s, hold 500 for 10s, ramp to 50 over 1s, hold 50 for 20s. All request-start samples fall inside their intended phase boundaries; all 42 seconds have VU samples.
- Scheduling deviations: initial activation samples range from 14 to 50 active VUs; at exactly the peak boundary one sample observes 499 before 500. Peak median/max are 500; recovery min/max are exactly 50. These are observed scheduler transitions, not a claim of perfect instantaneous VU activation.
- gracefulRampDown=0s enforces the 1s decline. It intentionally interrupted 450 requests (337 attributed to peak, 113 to decline). They are not counted as HTTP/network errors and are excluded from completed-response success/error denominators. All 450 nevertheless persisted in the DB.
- RPS per phase is request starts divided by planned phase seconds; client-confirmed/s is shown separately. Responses are attributed to request start time and may complete in the next phase or final 10s grace. During a 1s transition RPS can exceed steady-state throughput. Per-phase request starts are not per-second completions.
- Zero observed HTTP errors, network errors and connection refusals; 100% success is among completed responses only. k6 thresholds PASS does not mean zero interrupted work or an independently defined latency SLA.
- Recovery early/late windows are 22-32s and 32-42s based on request starts. p95 and error rate recovered; late mean latency was still above baseline, and late confirmed throughput was lower. This is partial recovery evidence, not proof of complete recovery.
- CPU uses all 8 logical CPUs as 100%. Resource averages are unweighted samples with irregular intervals (1s sleep plus CIM overhead). Resource phase allocation uses collector elapsed time including startup and is approximate; exact phase timing/VU checks use k6 scenario-relative telemetry.
- k6 and Spring Boot run on the same Windows PC with MySQL/Docker and other processes, so CPU and memory competition affect the result. Dashboard aggregation is 1s instead of the Stress tests 5s, with extra phase/VU telemetry and occasional authenticated health GETs; these add monitoring overhead.
- Stress 500 VU begins at 500 immediately and runs 30s, while Spike is warmed at 50 VU and holds 500 for 10s. Stress had 262 startup connection refusals; Spike has none. This difference does not establish improved server capacity or a Redis benefit. Stress 1000 VU is an aborted memory-pressure observation only.
- Safety limits: free physical RAM below 64 MiB, original Java process exit, failed/10s-timeout authenticated health GET, or 75s process deadline trigger stopping only k6. No safety stop or retry was needed in this run. Dashboard HTTP is disabled (port=-1) to avoid browser-connected exit delays.

Artifacts are under load-tests/: issue21-spike-dashboard.html, issue21-spike-baseline.json, and results-spike-7e3352c8-b82d-4034-8048-6f250fa5d430.* (raw Markdown/JSON, resources CSV/JSON, DB snapshots and phase counts, execution, health, postcheck, logs).

Use this exact workload, measurement definitions, source hashes and shared-PC caveats for a later Redis comparison. A single run is an exploratory baseline, not a reproducible capacity limit or proof that Redis will resolve the bottleneck.
