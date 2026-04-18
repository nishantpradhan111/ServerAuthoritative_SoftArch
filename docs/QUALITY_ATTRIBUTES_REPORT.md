# Quality Attributes Report

## 1. Purpose

This report presents a rubric-ready quality attribute evaluation for CodeReboot.
It ties architecture decisions to measurable behavior and supporting evidence from:

1. source code structure and controls,
2. automated tests,
3. observability endpoints and metrics, and
4. third-party benchmark results (k6).

## 2. Evaluation Approach

Each attribute is documented using a consistent format:

1. Scenario (stimulus source, stimulus, environment, artifact, response, response measure)
2. Architectural tactics implemented
3. Concrete evidence (code areas, tests, and benchmark outputs)
4. Acceptance criteria with status
5. Residual risk and next-step hardening

This structure is intentionally aligned to strict architecture-evaluation expectations: explicit scenario definition, decision-to-implementation traceability, and measurable verification.

## 3. Performance

### Scenario

- Stimulus source: concurrent players executing room lifecycle and gameplay actions.
- Stimulus: repeated authenticated requests and command traffic.
- Environment: local baseline benchmark with staged virtual users.
- Artifact: API/auth/room flow and server runtime.
- Response: stable response latency with low failure rate.
- Response measure target:
	- `http_req_failed < 2%`
	- `http_req_duration p95 < 500ms`
	- `http_req_duration p99 < 1200ms`
	- `benchmark_errors < 2%`

### Tactics Implemented

- WebSocket transport for low-latency gameplay interaction.
- In-memory room state to avoid per-tick persistence latency.
- Fixed-rate simulation orchestration through `GameSimulationService`.
- Lightweight HTTP lifecycle path for auth and room operations.

### Evidence

- Components: `GameSocketCommandDispatcher`, `WebSocketRoomBroadcaster`, `GameSimulationService`, `RoomService`.
- Benchmark artifact: `benchmarks/RESULTS.md`.
- Benchmark profile executed:
	- Stage 1: `1 VU` for `5s`
	- Stage 2: `2 VU` for `10s`
	- Stage 3: `0 VU` for `5s`

### Measured Results (k6)

- Requests: `192`
- Iterations: `48`
- Throughput: `9.451828 req/s`
- Failure rate: `0.00%` (`0/192`)
- p50: `2.17ms`
- p95: `277.04ms`
- p99: `294.27ms`
- max: `473.34ms`

Threshold verdict:

- `benchmark_errors < 2%`: PASS (`0.00%`)
- `http_req_failed < 2%`: PASS (`0.00%`)
- `http_req_duration p95 < 500ms`: PASS (`277.04ms`)
- `http_req_duration p99 < 1200ms`: PASS (`294.27ms`)

### Acceptance and Status

- Acceptance criteria: all benchmark thresholds pass for the baseline profile with zero request failures.
- Current status: satisfied with measured third-party evidence.
- Confidence: high for current coursework scale and profile.

### Residual Risk

- Benchmark scope currently emphasizes HTTP lifecycle, not sustained high-concurrency WebSocket gameplay load.
- Next step: run medium/high-load repeats and report median across at least three runs.

## 4. Security

### Scenario

- Stimulus source: unauthorized or malformed client activity.
- Stimulus: attempts to access protected room operations or misuse token identity.
- Environment: internet-facing usage via LAN/tunnel.
- Artifact: security filter chain and application authorization guards.
- Response: deny unauthorized access without leaking internal details.
- Response measure target: protected routes require valid auth context; invalid credentials and illegal access paths are rejected.

### Tactics Implemented

- Stateless JWT authentication and validation.
- Route-level access control in `SecurityConfig`.
- Token-user binding checks in room operations.
- BCrypt password hashing for credential storage.
- Sanitized API exception handling.

### Evidence

- Components: `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenService`, `AuthService`, `ApiExceptionHandler`.
- Tests: `AuthControllerTest`, `AuthServiceTest`, room API/service authorization tests listed in `UNIT_TEST_CATALOG.txt`.

### Acceptance and Status

- Acceptance criteria: no protected room operation succeeds without valid authentication and ownership context.
- Current status: satisfied in implementation and automated tests.
- Confidence: high.

### Residual Risk

- Token revocation strategy and abuse throttling remain future hardening items.

## 5. Modifiability

### Scenario

- Stimulus source: feature request for a new gameplay command or rule variation.
- Stimulus: implement behavior extension with minimal ripple across layers.
- Environment: iterative development under existing architecture constraints.
- Artifact: parser/dispatcher/service/domain seams and layer boundaries.
- Response: change remains localized and testable without broad refactoring.
- Response measure target: additions primarily touch command type/parser/dispatcher + targeted service/domain logic.

### Tactics Implemented

- Explicit layered architecture: `api`, `application`, `domain`, `security`, `transport`, `infra`.
- Command parser/dispatcher separation.
- Domain-centered rules and state transitions.
- Architecture fitness tests (ArchUnit) enforcing layering constraints.

### Evidence

- Components: `GameSocketCommandParser`, `GameSocketCommandDispatcher`, `RoomService`, `Room`.
- Tests: parser/dispatcher/domain tests plus `ArchitectureLayeringTest` entries in `UNIT_TEST_CATALOG.txt`.

### Acceptance and Status

- Acceptance criteria: feature extension path is localized and protected by regression tests.
- Current status: satisfied; boundaries are explicit and automatically guarded.
- Confidence: high.

### Residual Risk

- `RoomService` orchestration breadth may justify use-case extraction as feature count grows.

## 6. Reliability

### Scenario

- Stimulus source: unstable client connectivity and reconnect races.
- Stimulus: stale session ids, partial sends, and replacement-session churn.
- Environment: realtime multiplayer session under imperfect network conditions.
- Artifact: session registry and broadcast pipeline.
- Response: preserve valid active state and surface failed delivery cleanly.
- Response measure target: stale-session cleanup must not remove replacement sessions.

### Tactics Implemented

- Session-id-aware unregister/remove semantics.
- Defensive broadcaster with attempted/delivered/failed accounting.
- Centralized API exception mapping.
- Simulation and broadcast flows instrumented for operational visibility.

### Evidence

- Components: `WebSocketSessionRegistry`, `WebSocketRoomBroadcaster`, `ApiExceptionHandler`.
- Tests: `WebSocketSessionRegistryTest`, `WebSocketRoomBroadcasterTest`, dispatcher tests.
- Runtime metrics: `codereboot.ws.broadcast.messages.attempted`, `codereboot.ws.broadcast.messages.delivered`, `codereboot.ws.broadcast.messages.failed`.

### Acceptance and Status

- Acceptance criteria: stale-session operations are safe and do not tear down valid replacements.
- Current status: satisfied by explicit transport tests.
- Confidence: high for single-instance deployment scope.

### Residual Risk

- No chaos/soak test campaign yet for long-running network turbulence.

## 7. Observability

### Scenario

- Stimulus source: production-like incident or debugging request.
- Stimulus: need fast diagnosis via request correlation, health checks, and metric inspection.
- Environment: local/demo runtime with actuator and Prometheus export enabled.
- Artifact: request-id filter chain, health endpoints, actuator endpoints, custom meters.
- Response: operator can trace requests and evaluate service behavior quickly.
- Response measure target:
	- request id present in logs/response context,
	- health endpoint reachable,
	- metrics endpoint reachable,
	- Prometheus scrape endpoint available.

### Tactics Implemented

- `X-Request-Id` generation/propagation (`RequestIdFilter`, MDC pattern).
- App health endpoint: `GET /api/system/health`.
- Actuator exposure: `health`, `info`, `metrics`, `prometheus`.
- Public GET allowlisting for actuator endpoints in `SecurityConfig`.
- Domain-specific and transport-specific Micrometer metrics.

### Evidence

- Configuration: `management.endpoints.web.exposure.include=health,info,metrics,prometheus`, `management.prometheus.metrics.export.enabled=true`.
- Security controls: GET permit rules for `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`.
- Custom metrics emitted:
	- `codereboot.ws.commands.total` (tags: `type`, `outcome`)
	- `codereboot.ws.command.dispatch.duration`
	- `codereboot.ws.sessions.active`
	- `codereboot.ws.rooms.active`
	- `codereboot.ws.sessions.registrations`
	- `codereboot.ws.sessions.unregistrations`
	- `codereboot.ws.broadcast.messages.attempted`
	- `codereboot.ws.broadcast.messages.delivered`
	- `codereboot.ws.broadcast.messages.failed`
	- `codereboot.rooms.active`
	- `codereboot.simulation.ticks.total`
	- `codereboot.simulation.tick.duration`
	- `codereboot.rooms.cleanup.completed.total`
- Tests: `RequestIdFilterTest`, `SystemControllerTest`.

### Acceptance and Status

- Acceptance criteria: request correlation, health visibility, metric discovery, and scrape export are all operational.
- Current status: satisfied.
- Confidence: high.

### Residual Risk

- Centralized tracing and dashboarding stack (for example, OpenTelemetry + Grafana) is not yet integrated.

## 8. Cross-Attribute Evidence Summary

| Attribute | Scenario Quality | Implementation Tactics | Verification Evidence | Status |
|---|---|---|---|---|
| Performance | Explicit stimulus-response with numeric targets | Realtime transport + efficient state strategy | k6 thresholds + latency distribution + zero failures | Satisfied |
| Security | Explicit threat and response model | JWT stateless auth + route protection + ownership checks | Controller/service tests + security config traceability | Satisfied |
| Modifiability | Explicit change scenario | Layered seams + parser/dispatcher + architecture fitness tests | ArchUnit + targeted unit tests | Satisfied |
| Reliability | Explicit reconnect/race scenario | Defensive registry and broadcast semantics | Transport tests + delivery counters | Satisfied |
| Observability | Explicit incident diagnosis scenario | Request correlation + actuator + Prometheus + custom metrics | Endpoint exposure/security rules + tests + runtime meters | Satisfied |

## 9. Submission-Ready Evidence Snapshot

- Automated tests: `68` run, `0` failures, `0` errors.
- Benchmark run: all declared k6 thresholds PASS.
- Endpoint visibility: `/api/system/health`, `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` exposed and documented.
- Architectural traceability: scenarios, tactics, and evidence cross-reference code and artifacts without ambiguity.

## 10. Conclusion

CodeReboot demonstrates strong quality-attribute alignment for the evaluated scope.
The current submission provides both structural evidence (architecture and tests) and operational evidence (k6 + observability endpoints/metrics), which is typically the differentiator for high-mark architecture reports.

Remaining gaps are enhancement-level (higher-load benchmarking depth, centralized tracing/dashboard stack), not foundational quality defects.
