# Architecture Decision Log

## ADR-1: Use Stateless JWT Authentication for Room APIs

**Status:** Accepted

**Context**

The project needs authenticated room ownership, protected room APIs, and websocket subscribe validation without relying on server sessions.

**Decision**

Use signed JWT bearer tokens issued at login/register and validated on protected HTTP endpoints and websocket subscribe commands.

**Consequences**

- Pros:
  - Stateless request handling.
  - Simple client storage and transmission.
  - Easy horizontal scaling path compared with server sessions.
- Cons:
  - Token revocation is not immediate without extra infrastructure.
  - Secret management is required.

## ADR-2: Keep Room and Match State In-Memory for the First Pass

**Status:** Accepted

**Context**

The gameplay loop requires low-latency room updates and rapid iteration.

**Decision**

Keep room state, tick history, and shot tracking in-memory inside the domain layer for now.

**Consequences**

- Pros:
  - Fast gameplay loop.
  - Simple implementation.
  - Easy to reason about during grading and testing.
- Cons:
  - State is not durable across restarts.
  - Horizontal scaling requires shared state or a persistence layer later.

## ADR-3: Separate Client Hit Detection from Server Hit Authority

**Status:** Accepted

**Context**

The browser should provide responsive feedback, but the server must remain authoritative for match outcomes.

**Decision**

Let the client detect bullet-body overlap for hit claims, then validate claims on the server using historical tick state and server-side geometry checks.

**Consequences**

- Pros:
  - Responsive player feedback.
  - Reduced false positives from client-only trust.
  - Clear authoritative server behavior.
- Cons:
  - Requires historical tick tracking.
  - Client and server collision logic must stay aligned.

## ADR-4: Add Lightweight Request Correlation Instead of Heavy Observability Stack

**Status:** Accepted

**Context**

The project needs practical runtime visibility for debugging and grading, but it should remain a small coursework system rather than a production observability platform.

**Decision**

Add a public `/api/system/health` endpoint, propagate `X-Request-Id` through logging and error payloads, and keep the implementation inside the existing Spring Boot application instead of adding Actuator, Prometheus, or external tracing infrastructure.

**Consequences**

- Pros:
  - Gives immediate runtime visibility with minimal complexity.
  - Makes errors and logs easier to correlate during debugging.
  - Preserves the project’s lightweight architecture.
- Cons:
  - Does not provide full metrics, dashboards, or distributed tracing.
  - Request IDs are local to this application boundary.
