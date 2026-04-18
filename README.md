# CodeReboot Arena

Small multiplayer browser duel built as a clean Java backend plus HTML/CSS/JS client.

Author: Nishant Pradhan

GitHub Link: https://github.com/nishantpradhan111/ServerAuthoritative_SoftArch


## What is implemented

- JWT-based login and registration
- Room creation and joining
- Live lobby updates over WebSocket
- Auto-start when both players are ready
- Grid-based 1v1 duel with move and fire actions
- In-memory room/session storage with clear seams for later persistence and scaling work
- Authenticated room ownership and websocket subscribe validation
- Application-layer room entry contracts with API-side DTO mapping to keep transport models at the boundary
- Architecture fitness checks that block package-cycle regressions
- Client/server hit validation with replay-friendly claim tracking
- Runtime observability through Actuator health/metrics, Prometheus export, and request-id tracing

## Run

```bash
mvn spring-boot:run
```

Windows launcher options:

```powershell
# Build + run on default port 8080
./run.ps1

# Skip build (faster local iteration)
./run.ps1 -SkipBuild

# Run on a custom port
./run.ps1 -SkipBuild -Port 8081

# Enable hit-claim diagnostics logging
./run.ps1 -SkipBuild -EnableHitClaimDiagnostics

# Start only Cloudflare tunnel (outside-LAN sharing)
./run-public.ps1
```

Notes:

- `run.ps1` performs a preflight port check and fails fast with an actionable message if the selected port is already in use.
- Hit-claim diagnostics are off by default and can be enabled only when debugging intermittent hit validation reports.

Then open:

- http://localhost:8080/login.html

## Public access

For outside-LAN access without a domain, use a tunnel service pointed at your local server:

```powershell
./run.ps1 -SkipBuild
cloudflared tunnel --url http://localhost:8080
```

Or use the helper script that starts only the tunnel (start `run.ps1` separately first):

```powershell
./run-public.ps1
```

Then open the tunnel URL from a device outside your LAN. If you later buy a domain, you can switch to a reverse proxy or router port forwarding, but you do not need that for the first public test.

## Architecture

- Layered Java server in `src/main/java`
- Static browser pages in `src/main/resources/static`
- Client-server split: browser pages and JS handle presentation/input, while the Spring Boot server handles auth, room APIs, WebSocket events, and authoritative game state
- Security boundary for JWT authentication and protected room APIs
- API controllers map application return types to HTTP DTOs so transport models stay out of the application layer
- Room and match state in the domain layer
- WebSocket broadcasts for room and match updates
- `/api/system/health` provides a simple app-specific runtime check alongside Actuator health/metrics and Prometheus export
- `X-Request-Id` is propagated through logs and API errors so requests can be traced end to end

## Observability

Runtime observability endpoints are exposed for dashboard tooling:

- `GET /api/system/health` - lightweight application health snapshot
- `GET /actuator/health` - Spring Boot actuator health
- `GET /actuator/metrics` - available metric names
- `GET /actuator/prometheus` - Prometheus scrape endpoint

Custom game/realtime metrics now emitted include:

- `codereboot.ws.commands.total` (tags: `type`, `outcome`)
- `codereboot.ws.command.dispatch.duration`
- `codereboot.ws.sessions.active`
- `codereboot.ws.rooms.active`
- `codereboot.ws.broadcast.messages.attempted`
- `codereboot.ws.broadcast.messages.delivered`
- `codereboot.ws.broadcast.messages.failed`
- `codereboot.rooms.active`
- `codereboot.simulation.ticks.total`
- `codereboot.simulation.tick.duration`
- `codereboot.rooms.cleanup.completed.total`
- ArchUnit-based architecture tests enforce the package layering rules

## Documentation

Core project docs:

- `README.md` - project overview and quick start
- `SETUP.md` - local setup and prerequisites
- `docs/POSTGRES_SETUP.md` - PostgreSQL initialization details
- `docs/DEPLOYMENT.md` - public sharing and deployment notes
- `docs/IMPLEMENTATION_SUMMARY.md` - architecture and implementation overview
- `docs/ARCHITECTURAL_RATIONALE.md` - design decisions, alternatives, and trade-offs
- `docs/QUALITY_ATTRIBUTES_REPORT.md` - completed quality-attribute evidence report
- `evaluation_scheme.txt` - rubric used for strict assessment
- `benchmarks/README.md` - third-party performance benchmarking workflow and reporting template
- `benchmarks/RESULTS.md` - captured benchmark run results and interpretations
- `docs/diagramnet-views/client-server-view.drawio` - client-server architecture view
- `FUNCTION_CATALOG.txt` - concise function/class purpose index
- `UNIT_TEST_CATALOG.txt` - complete unit test inventory with paths and line numbers

## First-pass game loop

1. Register or log in to obtain a bearer token.
2. Create or join a room in the lobby.
3. Mark both players ready.
4. The match opens automatically when the second player is ready.
5. Move with WASD or arrows and fire with space.
6. Hit claims are validated against historical tick state to reduce false positives.
