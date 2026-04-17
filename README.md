# CodeReboot Arena

Small multiplayer browser duel built as a clean Java backend plus HTML/CSS/JS client.

## What is implemented

- JWT-based login and registration
- Room creation and joining
- Live lobby updates over WebSocket
- Auto-start when both players are ready
- Grid-based 1v1 duel with move and fire actions
- In-memory room/session storage with clear seams for later persistence and scaling work
- Authenticated room ownership and websocket subscribe validation
- Client/server hit validation with replay-friendly claim tracking
- Lightweight runtime visibility through a public health endpoint and request-id tracing

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
- Security boundary for JWT authentication and protected room APIs
- Room and match state in the domain layer
- WebSocket broadcasts for room and match updates
- `/api/system/health` provides a simple runtime check without bringing in Actuator or a monitoring stack
- `X-Request-Id` is propagated through logs and API errors so requests can be traced end to end

## Evaluation Mapping

This project is documented and implemented to align with the course evaluation scheme:

- Correct application of architectural pattern: layered backend with explicit security, application, domain, transport, and infrastructure boundaries.
- Fulfilment of functional requirements: auth, room lifecycle, live multiplayer loop, replay/return flow, and hit-claim behavior.
- Architectural design quality: isolated responsibilities, clean dependency direction, and explicit server/client separation.
- Documentation quality: this README, `IMPLEMENTATION_SUMMARY.md`, `DEPLOYMENT.md`, `SETUP.md`, and `docs/evaluation/architecture_views_mermaid.md` provide views, rationale, and setup guidance.
- Architectural trade-offs: `docs/evaluation/architecture_decision_log.md` records the JWT, in-memory state, and server-authoritative hit-validation decisions.
- Evidence map: `docs/evaluation/rubric_evidence.md` links rubric criteria to concrete code and documentation artifacts.
- Design stance: the project intentionally stays lightweight and principles-driven instead of adding operational machinery that would distract from the core architecture.
- Code quality and maintainability: small focused modules, named services, and passing automated tests.

## First-pass game loop

1. Register or log in to obtain a bearer token.
2. Create or join a room in the lobby.
3. Mark both players ready.
4. The match opens automatically when the second player is ready.
5. Move with WASD or arrows and fire with space.
6. Hit claims are validated against historical tick state to reduce false positives.
