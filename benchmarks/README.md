# Benchmarking Guide (k6)

This guide provides a reproducible third-party benchmarking workflow for CodeReboot.
The goal is to produce evaluator-friendly performance evidence for strict grading.

## 1. Why this exists

Architectural performance claims should be validated with measurements, not just rationale.
Using k6 adds independent, repeatable evidence for latency, throughput, and error behavior.

## 2. Tooling

- Benchmark tool: k6 (third-party load testing software)
- Script: `benchmarks/k6/http-room-lifecycle.js`
- Target system: Spring Boot server running locally (default `http://localhost:8080`)

## 3. Install k6 (Windows)

Option A (winget):

```powershell
winget install k6.k6
```

Option B (Chocolatey):

```powershell
choco install k6
```

Verify:

```powershell
k6 version
```

## 4. Start the application

```powershell
./run.ps1 -SkipBuild
```

## 5. Run benchmark

Default target (`http://localhost:8080`):

```powershell
k6 run benchmarks/k6/http-room-lifecycle.js
```

Custom target:

```powershell
$env:BASE_URL = "http://localhost:8081"
k6 run benchmarks/k6/http-room-lifecycle.js
```

Custom think time (milliseconds):

```powershell
$env:THINK_TIME_MS = "100"
k6 run benchmarks/k6/http-room-lifecycle.js
```

Quick proof run (short profile for rapid evidence capture):

```powershell
$env:STAGE1_DURATION = "10s"
$env:STAGE1_TARGET = "2"
$env:STAGE2_DURATION = "15s"
$env:STAGE2_TARGET = "4"
$env:STAGE3_DURATION = "10s"
$env:STAGE3_TARGET = "0"
k6 run benchmarks/k6/http-room-lifecycle.js
```

## 6. Scenario covered

Each virtual user iteration executes:

1. Register a unique user (`POST /api/auth/register`)
2. Create room (`POST /api/rooms`)
3. Read room snapshot (`GET /api/rooms/{roomCode}`)
4. Leave room (`POST /api/rooms/{roomCode}/leave`)

This validates authenticated room lifecycle behavior under load.

## 7. Pass/Fail thresholds

Defined in script options:

- `http_req_failed < 2%`
- `http_req_duration p95 < 500ms`
- `http_req_duration p99 < 1200ms`
- `benchmark_errors < 2%`

If thresholds fail, treat as an architecture signal to investigate bottlenecks or error paths.

## 8. Evidence to include in submission

Copy the k6 summary output into your report and include:

- environment details (CPU, RAM, OS, Java version)
- target URL and app profile used
- scenario/stages configuration
- request latency: p50, p95, p99
- throughput: requests/sec
- error/failure rates
- short interpretation and architectural implication

## 9. Recommended reporting table

| Run ID | VU Profile | p50 (ms) | p95 (ms) | p99 (ms) | req/s | failed % | Result |
|---|---|---:|---:|---:|---:|---:|---|
| Baseline (2026-04-17) | 1->2->0 over 20s | 2.17 | 277.04 | 294.27 | 9.45 | 0.00 | PASS ✅ |
| Repeat 1 | 1->2->0 over 20s | _fill_ | _fill_ | _fill_ | _fill_ | _fill_ | Pass/Fail |
| Repeat 2 | 1->2->0 over 20s | _fill_ | _fill_ | _fill_ | _fill_ | _fill_ | Pass/Fail |

## 10. Notes and limitations

- This benchmark focuses on HTTP room lifecycle, not WebSocket gameplay streams.
- Run at least 3 times and report median values to reduce noise.
- Keep the same environment across runs for fair comparison.
