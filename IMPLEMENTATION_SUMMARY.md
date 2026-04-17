# Architecture and Implementation Summary

## 1. System Overview

CodeReboot is a layered multiplayer browser game implemented with Spring Boot and a browser-native frontend.
The system uses stateless JWT authentication, token-bound room ownership checks, WebSocket realtime gameplay updates, and server-authoritative hit validation.

The design intentionally stays lightweight:
- no external session/cache layer,
- no heavyweight observability stack,
- clear seams for future scale-out.

## 2. Architectural Pattern and Layering

### 2.1 API Layer (`api/`)
Responsibilities:
- Receives HTTP requests.
- Validates request shapes (DTO + bean validation).
- Delegates business operations to application services.
- Returns stable JSON responses.

Key classes:
- `AuthController`
- `RoomController`
- `SystemController`
- `ApiExceptionHandler`

### 2.2 Application Layer (`application/`)
Responsibilities:
- Orchestrates room lifecycle operations.
- Enforces token-user ownership constraints.
- Coordinates broadcast side effects.

Key classes:
- `AuthService`
- `RoomService`
- `GameSimulationService`

### 2.3 Domain Layer (`domain/`)
Responsibilities:
- Contains gameplay and room invariants.
- Maintains authoritative combat and movement rules.
- Tracks shot and tick history for reliable hit validation.

Key classes:
- `Room`
- `RoomCombatLifecycle`
- `Player`
- `ShotTracker`
- `TickHistoryTracker`
- `ReplayRequestTracker`

### 2.4 Transport Layer (`transport/`)
Responsibilities:
- Parses and dispatches websocket commands.
- Maintains room session registry.
- Broadcasts snapshots/events to connected clients.

Key classes:
- `GameSocketHandler`
- `GameSocketCommandParser`
- `GameSocketCommandDispatcher`
- `WebSocketRoomBroadcaster`

### 2.5 Security Layer (`security/`)
Responsibilities:
- Issues and validates JWTs.
- Protects room APIs via bearer authentication.
- Propagates request correlation ids.

Key classes:
- `SecurityConfig`
- `JwtAuthenticationFilter`
- `JwtTokenService`
- `RequestIdFilter`
- `BCryptPasswordEncoderImpl`

### 2.6 Infrastructure Layer (`infra/`)
Responsibilities:
- Abstracts persistence concerns.
- Provides in-memory room storage and JPA user repository.

Key classes:
- `UserRepository`
- `RoomRepository`
- `InMemoryRoomRepository`

## 3. Functional Coverage

Implemented end-to-end capabilities:
- User registration and login with JWT issuance.
- Room create/join/leave via protected APIs.
- Realtime room and gameplay synchronization over WebSocket.
- Continuous input handling and server-authoritative simulation ticks.
- Hit claim validation against historical tick snapshots.
- Replay request and replay-room redirect flow.
- Lightweight health endpoint (`GET /api/system/health`).
- Request correlation with `X-Request-Id` in logs and API error payloads.

## 4. Security and Reliability

Security controls:
- BCrypt password hashing.
- Stateless bearer-token authentication for protected APIs.
- Token-user binding checks in room operations.
- Sanitized public error messages for sensitive conflicts.

Reliability controls:
- Centralized exception mapping (`ApiExceptionHandler`).
- Safe broadcast wrapper with partial-delivery warning logs.
- Session-id-aware websocket unregister behavior.
- Room simulation isolated in domain/application layers.

## 5. Realtime and Gameplay Design

Gameplay architecture:
- Client submits movement/input intent.
- Server remains authoritative for room state and combat outcome.
- Client can propose hit claims, but server validates via historical state.

This design gives:
- responsive UX on client,
- deterministic outcome on server,
- reduced false positive hit registration.

## 6. Operational Visibility (Lightweight)

Instead of heavy platform additions, the project provides:
- `GET /api/system/health` for basic runtime introspection.
- `RequestIdFilter` to generate/propagate `X-Request-Id`.
- request id included in structured API errors.
- request id available in console log pattern via MDC.

This satisfies practical debugging traceability while keeping architecture simple.

## 7. Documentation Set

Primary project artifacts:
- `README.md`
- `FUNCTION_CATALOG.txt`
- `DEPLOYMENT.md`
- `SETUP.md`
- `POSTGRES_SETUP.md`

## 8. Test Evidence

Representative coverage areas:
- domain combat validity and replay behavior,
- room orchestration and broadcast side effects,
- websocket parser and dispatcher behavior,
- request correlation filter behavior,
- health endpoint payload contract.

## 9. Scope Boundaries

Known intentional scope boundaries:
- single-instance in-memory room state,
- no distributed cache/queue/orchestration stack,
- lightweight operational visibility over enterprise observability platform.
