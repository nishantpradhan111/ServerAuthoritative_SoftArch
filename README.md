# CodeReboot Arena

Small multiplayer browser duel built as a clean Java backend plus HTML/CSS/JS client.

## What is implemented

- Login placeholder with local guest profile storage
- Room creation and joining
- Live lobby updates over WebSocket
- Auto-start when both players are ready
- Grid-based 1v1 duel with move and fire actions
- In-memory room/session storage with clear seams for later auth, persistence, and paywall work

## Run

```bash
mvn spring-boot:run
```

Then open:

- http://localhost:8080/login.html

## Architecture

- Java server in `src/main/java`
- Static browser pages in `src/main/resources/static`
- Room and match state in the domain layer
- WebSocket broadcasts for room and match updates

## First-pass game loop

1. Enter a name on the login page.
2. Create or join a room in the lobby.
3. Mark both players ready.
4. The match opens automatically when the second player is ready.
5. Move with WASD or arrows and fire with space.
