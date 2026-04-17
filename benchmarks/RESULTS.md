# Benchmark Results

## Run: 2026-04-17 Quick Baseline

Tooling:
- k6 v1.7.1 (`C:\Program Files\k6\k6.exe`)
- Script: `benchmarks/k6/http-room-lifecycle.js`
- Target: `http://localhost:8080`

Profile used:
- `STAGE1_DURATION=5s`, `STAGE1_TARGET=1`
- `STAGE2_DURATION=10s`, `STAGE2_TARGET=2`
- `STAGE3_DURATION=5s`, `STAGE3_TARGET=0`
- `THINK_TIME_MS=100`

Threshold outcome:
- `benchmark_errors < 2%`: PASS (`0.00%`)
- `http_req_failed < 2%`: PASS (`0.00%`)
- `http_req_duration p95 < 500ms`: PASS (`277.04ms`)
- `http_req_duration p99 < 1200ms`: PASS (`294.27ms`)

Observed metrics:
- Requests: `192`
- Iterations: `48`
- Request rate: `9.451828 req/s`
- p50 (median): `2.17ms`
- p95: `277.04ms`
- p99: `294.27ms`
- Max: `473.34ms`
- Failed requests: `0/192`

Interpretation:
- The HTTP auth/room lifecycle path remained stable under the quick two-VU profile with zero request failures.
- Tail latency remained below the declared thresholds for this baseline scenario.
- This run is suitable as initial performance evidence; additional medium/high load profiles should be executed for stronger submission-grade benchmarking evidence.
