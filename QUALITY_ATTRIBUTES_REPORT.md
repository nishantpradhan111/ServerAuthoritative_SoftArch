# Quality Attributes Report

## 1. Purpose

This report provides an evidence-backed quality attribute analysis for CodeReboot.
It converts architectural intent into assessable scenarios, tactics, and verification evidence.

## 2. Evaluation Method

Each attribute is documented with:

1. Quality scenario in stimulus-response format.
2. Tactics implemented in the current architecture.
3. Evidence mapped to concrete code and tests.
4. Acceptance criteria and current confidence level.
5. Residual risk and planned improvements.

## 3. Performance

### Scenario

- Stimulus source: active players in a live room.
- Stimulus: repeated movement/input/fire commands.
- Environment: normal two-player match.
- Artifact: transport, application, and domain execution chain.
- Response: command processed and snapshot broadcast to both clients.
- Response measure target: perceived smooth realtime gameplay without backlog growth.

### Tactics Used

- WebSocket command transport for low-latency command flow.
- In-memory room state to avoid synchronous persistence cost on each tick.
- Central simulation tick scheduling through `GameSimulationService`.

### Evidence

- Components: `GameSocketHandler`, `GameSocketCommandDispatcher`, `GameSimulationService`, `WebSocketRoomBroadcaster`.
- Tests: dispatcher/parser/broadcaster and room-domain tests listed in `UNIT_TEST_CATALOG.txt`.

### Acceptance and Status

- Acceptance criteria: no command-loss behavior in unit-tested dispatcher paths and stable snapshot publication semantics.
- Current status: functionally satisfied in unit scope.
- Confidence: medium-high for coursework scope, pending formal latency instrumentation.

### Residual Risk

- No percentile latency telemetry yet.

## 4. Security

### Scenario

- Stimulus source: unauthorized client.
- Stimulus: attempts to call protected room APIs or misuse room token identity.
- Environment: internet-facing deployment/tunnel.
- Artifact: security filter chain and room authorization checks.
- Response: reject unauthorized operation without exposing sensitive internals.
- Response measure target: protected operations require valid authenticated identity and room ownership checks.

### Tactics Used

- JWT bearer token issue and validation.
- Stateless authentication filter in request pipeline.
- Token-user binding checks in room operations.
- BCrypt password hashing and sanitized public error responses.

### Evidence

- Components: `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenService`, `AuthService`, `ApiExceptionHandler`.
- Tests: `AuthControllerTest`, `AuthServiceTest`, room service/controller authorization-related tests.

### Acceptance and Status

- Acceptance criteria: protected room operations not executable without valid authentication context; invalid credentials rejected.
- Current status: satisfied in documented tests and implementation paths.
- Confidence: high for project scope.

### Residual Risk

- Token revocation and abuse throttling are future hardening areas.

## 5. Modifiability

### Scenario

- Stimulus source: developer request for a new gameplay command.
- Stimulus: extend behavior without broad cross-layer changes.
- Environment: iterative feature development.
- Artifact: layered boundaries and command dispatch abstractions.
- Response: feature implemented with localized edits and stable existing tests.
- Response measure target: changes concentrated in parser/dispatcher/service/domain seams.

### Tactics Used

- Layered packaging (`api`, `application`, `domain`, `security`, `transport`, `infra`).
- Command parser/dispatcher abstraction in transport layer.
- Domain-centered game rule objects.
- Architecture fitness checks that fail the build if package boundaries drift.

### Evidence

- Components: `GameSocketCommandParser`, `GameSocketCommandDispatcher`, `RoomService`, `Room`.
- Tests: parser, dispatcher, room behavior, tracker tests, `ArchitectureLayeringTest`.

### Acceptance and Status

- Acceptance criteria: new command can be added by extending command type + parser + dispatcher + targeted service/domain behavior.
- Current status: architecture supports this path; existing tests demonstrate seam behavior.
- Confidence: medium-high.

### Residual Risk

- `RoomService` may need decomposition as feature count grows.
- Boundary drift is now guarded by architecture tests, but the service will still need decomposition if orchestration breadth expands significantly.

## 6. Reliability

### Scenario

- Stimulus source: transient disconnect/reconnect and stale session churn.
- Stimulus: stale session ids and partial send failures.
- Environment: unstable client network.
- Artifact: session registry and broadcaster.
- Response: avoid state corruption; preserve active replacement sessions.
- Response measure target: stale-session operations do not remove valid replacements; partial delivery is surfaced.

### Tactics Used

- Session-id-aware unregister and remove semantics.
- Defensive broadcaster with delivery accounting.
- Centralized exception mapping for predictable API failures.

### Evidence

- Components: `WebSocketSessionRegistry`, `WebSocketRoomBroadcaster`, `ApiExceptionHandler`.
- Tests: `WebSocketSessionRegistryTest`, `WebSocketRoomBroadcasterTest`, transport dispatcher tests.

### Acceptance and Status

- Acceptance criteria: stale session operations are safe and do not tear down valid active mappings.
- Current status: satisfied by explicit unit tests.
- Confidence: high for single-node scope.

### Residual Risk

- No chaos/soak integration tests yet.

## 7. Observability

### Scenario

- Stimulus source: bug report requiring request tracing.
- Stimulus: need to follow request path through logs and API response context.
- Environment: local/demo deployment without full telemetry platform.
- Artifact: request filter, logging context, and health endpoint.
- Response: operator can correlate request and inspect runtime status quickly.
- Response measure target: request id appears in response headers/log context; health endpoint available.

### Tactics Used

- `X-Request-Id` propagation and MDC logging correlation.
- Lightweight health endpoint via system controller.

### Evidence

- Components: `RequestIdFilter`, `RequestIdResolver`, `SystemController`.
- Tests: `RequestIdFilterTest`, `SystemControllerTest`.

### Acceptance and Status

- Acceptance criteria: request id generation/preservation behavior verified; health endpoint returns runtime snapshot.
- Current status: satisfied in code and tests.
- Confidence: high for project scope.

### Residual Risk

- No centralized metrics/tracing backend.

## 8. Attribute Summary Table

| Attribute | Scenario Defined | Tactics Implemented | Evidence Mapped | Acceptance Criteria Stated | Current Status |
|---|---|---|---|---|---|
| Performance | Yes | Yes | Yes | Yes | Functionally satisfied; telemetry enhancement pending |
| Security | Yes | Yes | Yes | Yes | Satisfied |
| Modifiability | Yes | Yes | Yes | Yes | Satisfied with service-growth caution |
| Reliability | Yes | Yes | Yes | Yes | Satisfied |
| Observability | Yes | Yes | Yes | Yes | Satisfied |

## 9. Conclusion

The current architecture demonstrates explicit and traceable quality tactics across all five attributes. Remaining work is primarily enhancement-level (telemetry depth, scale-out hardening), not foundational correctness gaps for the current project scope.

## 10. Evidence Snapshot (Current Submission)

- Automated test suite status: 68 run, 0 failures, 0 errors.
- Layer coverage present across API, application, domain, security, transport, config, and architecture tests.
- Artifacts are documented across `README.md`, `IMPLEMENTATION_SUMMARY.md`, and `ARCHITECTURAL_RATIONALE.md` for rubric traceability.
