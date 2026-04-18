# Architectural Rationale and Trade-Offs

## 1. Purpose

This document captures the architectural intent behind CodeReboot and explains why major decisions were made.
It is designed to support strict academic evaluation criteria around:

- depth of architectural understanding,
- justification of design decisions and trade-offs,
- consistency between design, implementation, and documentation,
- professional quality of artifacts,
- explicit technology selection rationale (including third-party software choices).

## 2. System Context and Constraints

### 2.1 Problem Context

CodeReboot is a realtime, browser-based 1v1 multiplayer game with authenticated room operations and WebSocket gameplay synchronization.

### 2.2 Key Constraints

- Small team and take-home timeline.
- Need for a complete working implementation, not only design diagrams.
- Requirement to demonstrate a concrete architectural pattern in code.
- Preference for low operational overhead in local and demo environments.

### 2.3 Architecturally Significant Requirements

- Correct room lifecycle and player authorization.
- Low-latency gameplay updates.
- Server-authoritative combat outcome.
- Clear maintainable module/layer boundaries.
- Sufficient observability for debugging without heavyweight platform dependencies.

## 3. Architectural Pattern Choice

### 3.1 Chosen Pattern

Layered architecture with explicit API, Application, Domain, Security, Transport, and Infrastructure boundaries.

### 3.2 Why This Pattern

- Matches the course objective for architectural comparison and explanation.
- Keeps business logic insulated from transport and framework concerns.
- Improves testability by isolating behavior into service/domain units.
- Makes future persistence and deployment changes lower risk.

### 3.3 Alternatives Considered

1. Monolithic controller-centric flow:
- Simpler initially.
- Rejected because it mixes policy, orchestration, and transport concerns quickly.

2. Event-driven microservices:
- Better independent scaling and fault isolation.
- Rejected for current scope due to distributed complexity and setup overhead.

3. CQRS + event sourcing:
- Strong audit/replay semantics.
- Rejected for scope/time; cost is disproportionate for present requirements.

## 4. Key Decisions and Trade-Offs

## D1. Stateless JWT Authentication

- Decision: Use JWT bearer tokens for authenticated APIs and socket subscribe flows.
- Benefits:
  - No server session store required.
  - Easy integration with REST and WebSocket boundaries.
- Trade-offs:
  - Token revocation is not immediate without additional infrastructure.
  - Requires careful token validation and ownership checks.
- Evidence:
  - `JwtTokenService`, `JwtAuthenticationFilter`, `SecurityConfig`.

## D2. Server-Authoritative Simulation

- Decision: The server owns movement/combat state; client sends intent, not final outcomes.
- Benefits:
  - Reduced cheating surface.
  - Deterministic conflict resolution.
- Trade-offs:
  - More server logic and state management complexity.
  - Requires efficient snapshot/broadcast design.
- Evidence:
  - `Room`, `RoomCombatLifecycle`, `GameSimulationService`.

## D3. WebSocket for Realtime Flow + REST for Lifecycle

- Decision: Use REST for room/auth lifecycle; WebSocket for gameplay commands and updates.
- Benefits:
  - Each protocol serves a clear purpose.
  - Lower latency for frequent updates.
- Trade-offs:
  - Two communication paths to secure and test.
  - Additional dispatcher/parser complexity.
- Evidence:
  - `RoomController` and `AuthController` for REST,
  - `GameSocketHandler`, `GameSocketCommandDispatcher`, `WebSocketRoomBroadcaster` for WS.

## D4. In-Memory Room Repository, PostgreSQL for User Auth Data

- Decision: Keep room state in memory for speed and scope fit; persist user accounts in PostgreSQL.
- Benefits:
  - Fast gameplay state access.
  - Real persistence where correctness matters for identity.
- Trade-offs:
  - Room state is single-instance and not resilient across restarts.
  - Horizontal scale-out needs shared room state in future.
- Evidence:
  - `InMemoryRoomRepository`, `UserRepository`, datasource configuration in `src/main/resources/application.properties`.

## D5. Lightweight Observability

- Decision: Add request correlation, Actuator health/metrics, and Prometheus export instead of a full dashboard/tracing stack.
- Benefits:
  - Lower operational complexity than a full telemetry backend.
  - Sufficient traceability for project debugging, demos, and metric inspection.
- Trade-offs:
  - Limited tracing depth compared with a full OpenTelemetry backend.
  - Not equivalent to a production observability platform with dashboards and alerting.
- Evidence:
  - `RequestIdFilter`, `RequestIdResolver`, `SystemController`, Actuator configuration, Prometheus export.

## D6. Defensive Broadcast and Session Registry Semantics

- Decision: Explicit session registry and safe broadcast behavior with partial delivery accounting.
- Benefits:
  - Predictable behavior during disconnect/reconnect races.
  - Better resilience in realtime collaboration scenarios.
- Trade-offs:
  - More code in transport layer.
  - Additional test burden for edge cases.
- Evidence:
  - `WebSocketSessionRegistry`, `WebSocketRoomBroadcaster`, transport tests.

## D7. API-Boundary DTO Mapping

- Decision: Keep room entry data as an application-layer contract and map it to HTTP DTOs inside the API controller.
- Benefits:
  - Prevents the application layer from depending on transport-specific response models.
  - Keeps serialization concerns at the HTTP boundary.
  - Removes a common source of package cycles as the codebase grows.
- Trade-offs:
  - Adds a small amount of mapping code in the controller.
  - Requires one more type to maintain for room entry responses.
- Evidence:
  - `RoomService`, `RoomEntry`, `RoomController`, `RoomEntryResponse`, `ArchitectureLayeringTest`.

## 5. Third-Party Software Selection and Rationale

This section documents external frameworks and libraries used in the solution and explains why each is architecturally appropriate.

1. Spring Boot (framework runtime)
- Role: application bootstrap, dependency injection, web runtime, scheduling, and configuration management.
- Why selected: provides mature layered-application support and fast delivery for a course project while preserving clean package boundaries.
- Trade-off considered: runtime abstraction adds framework coupling.
- Reason acceptable here: boundaries are enforced in package design (`api`, `application`, `domain`, `security`, `transport`, `infra`), so domain and service code remain testable and evolvable.

2. Spring Web MVC + WebSocket
- Role: REST API lifecycle endpoints and low-latency realtime room/game updates.
- Why selected: integrates cleanly into one runtime while separating lifecycle traffic (HTTP) from high-frequency gameplay traffic (WebSocket).
- Trade-off considered: dual protocol surface increases validation/security consistency requirements.
- Reason acceptable here: parser/dispatcher/session abstractions and targeted tests make behavior explicit and verifiable.

3. Spring Security
- Role: request filtering and route protection for authenticated API access.
- Why selected: standard, production-proven security primitives with clear integration points for JWT filters.
- Trade-off considered: security-chain configuration complexity.
- Reason acceptable here: centralized `SecurityConfig` and focused filter tests reduce misconfiguration risk.

4. JJWT (JSON Web Token library)
- Role: token issuance, signature verification, and claims parsing.
- Why selected: explicit JWT primitives and predictable API for stateless auth flows.
- Trade-off considered: key/expiry policy discipline is mandatory.
- Reason acceptable here: token validation and ownership checks are consistently applied across controller/service paths.

5. Spring Data JPA + PostgreSQL
- Role: durable persistence for user identity and credential data.
- Why selected: relational consistency and mature Java persistence tooling for auth-critical records.
- Trade-off considered: ORM and database setup overhead.
- Reason acceptable here: persistence is limited to identity/auth concerns; transient room state remains in-memory for low-latency gameplay and simpler operational scope.

6. BCrypt (password hashing)
- Role: secure password storage and verification.
- Why selected: adaptive hashing standard widely accepted for credential security.
- Trade-off considered: intentionally higher compute cost for password operations.
- Reason acceptable here: auth event frequency is low relative to gameplay traffic, so security benefit dominates.

7. Jackson (JSON serialization)
- Role: REST and WebSocket payload serialization/deserialization.
- Why selected: de facto standard in Spring ecosystem with robust mapping support.
- Trade-off considered: payload schema discipline is required to avoid silent drift.
- Reason acceptable here: typed DTO/command parser flow and parser tests keep message contracts explicit.

8. JUnit 5 + Mockito + Spring Test
- Role: unit and slice testing across layers.
- Why selected: supports fast, isolated verification of architectural seams and behavior contracts.
- Trade-off considered: mocking can hide integration defects if overused.
- Reason acceptable here: tests are distributed across API, application, domain, security, and transport, validating seam behavior comprehensively for project scope.

9. Maven (build lifecycle)
- Role: reproducible build, test execution, dependency management.
- Why selected: stable Java ecosystem default and straightforward course-evaluation reproducibility.
- Trade-off considered: plugin/config maintenance overhead.
- Reason acceptable here: single-command verification path is clear and documented.

10. k6 (third-party benchmarking software)
- Role: reproducible load testing for architecture-level latency, throughput, and error behavior evidence.
- Why selected: lightweight script-based benchmarking with deterministic thresholds and evaluator-friendly summaries.
- Trade-off considered: benchmark quality depends on scenario design and environment control.
- Reason acceptable here: benchmark scenarios and reporting guidance are standardized in `benchmarks/README.md`.

Software intentionally not included (with rationale):
- Redis/message broker/distributed cache stack: intentionally deferred to avoid distributed complexity outside current coursework scope.
- Full dashboard/tracing platform (Grafana/OpenTelemetry backend): intentionally deferred in favor of Actuator health/metrics, Prometheus export, and request correlation to maintain low operational overhead.

## 6. Consistency Matrix (Design -> Implementation -> Tests)

1. Layered architecture:
- Design: Layered views and module decomposition.
- Implementation: package boundaries in `api`, `application`, `domain`, `security`, `transport`, `infra`.
- Tests: Layer-specific tests across API, application, domain, security, transport, and architecture fitness checks.

2. Authorization and ownership constraints:
- Design: token-user binding and protected room operations.
- Implementation: JWT filter + ownership checks in service/controller paths.
- Tests: controller and service tests for authorization-related behavior.

3. Realtime reliability:
- Design: socket session registry and broadcaster abstraction.
- Implementation: dispatcher/parser/broadcaster + registry behavior.
- Tests: parser, dispatcher, registry, and broadcaster unit tests.

4. Server-authoritative hit validation:
- Design: claim validation against authoritative state history.
- Implementation: shot tracker + tick history + combat lifecycle checks.
- Tests: `RoomTest`, `ShotTrackerTest`, `TickHistoryTrackerTest`.

## 7. Architecture Evolution Risks and Controls

1. Evolution risk: orchestration complexity can concentrate in `RoomService` as feature breadth grows.
- Control: use-case extraction path is already defined and can be applied incrementally (replay and command orchestration first).

2. Evolution risk: divergence between REST and WebSocket validation paths.
- Control: keep authorization and core rules centralized in service/domain layers and reuse parser/dispatcher policy seams.

3. Evolution risk: single-instance room state limits availability/scale at higher concurrency tiers.
- Control: introduce shared room-state backing (cache/store) when moving beyond single-node deployment requirements.

4. Evolution risk: current observability profile is optimized for coursework/demo rather than large-scale production telemetry.
- Control: current Actuator/Prometheus exposure covers the immediate debugging and metric needs; a staged dashboard/tracing backend can be added later.

5. Evolution risk: package boundaries drift as the codebase accumulates more API and application response types.
- Control: ArchUnit fitness checks now enforce acyclic package layering and block outer-layer dependencies.

## 8. Scope Boundaries (Intentional)

- Single-instance authoritative room state.
- No distributed coordination/cache/queue at current stage.
- Lightweight operations model optimized for coursework and controlled demos.

## 9. Recommended Next Architecture Steps

1. Extract explicit use-case services from `RoomService` for replay lifecycle and command orchestration.
2. Introduce explicit command validation policy objects shared across REST and WebSocket entry points.
3. Keep the architecture fitness checks current as new packages and entry types are added.
4. Add persistence strategy for room state if multi-instance deployment is needed.

## 10. Cross-Reference Map

- System overview and implementation details: `IMPLEMENTATION_SUMMARY.md`
- Setup and runtime instructions: `SETUP.md`
- Deployment guidance: `DEPLOYMENT.md`
- Function-level inventory: `FUNCTION_CATALOG.txt`
- Unit test inventory: `UNIT_TEST_CATALOG.txt`
- Quality attribute evidence report: `QUALITY_ATTRIBUTES_REPORT.md`
- Evaluation rubric source: `evaluation_scheme.txt`
- Benchmarking workflow and evidence template: `benchmarks/README.md`
- Benchmark execution evidence: `benchmarks/RESULTS.md`
- Architecture diagrams: `docs/diagramnet-views/`
- Client-server architecture view: `docs/diagramnet-views/client-server-view.drawio`

## 11. Strict Evaluation Readiness Gates

The following gates are included so a strict evaluator can verify submission quality quickly:

1. Pattern gate:
- Chosen pattern explicitly named and justified.
- Alternatives and rejection rationale documented.

2. Consistency gate:
- Design to implementation to tests traceability documented in section 6.

3. Trade-off gate:
- Every major architectural decision includes benefits and costs.

4. Quality attribute gate:
- Attribute scenarios, tactics, acceptance criteria, and evidence documented in `QUALITY_ATTRIBUTES_REPORT.md`.

5. Technology rationale gate:
- Third-party software selection, role, and trade-offs are documented in section 5.

6. Verification gate:
- Automated unit test evidence documented and cross-referenced via `UNIT_TEST_CATALOG.txt`.

7. Benchmarking gate:
- Reproducible third-party performance benchmarking workflow and thresholds documented in `benchmarks/README.md`.

8. Professional artifact gate:
- Setup, deployment, architecture views, rationale, and catalogs are all present and cross-linked.
