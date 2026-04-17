# CodeReboot Architecture Views (Mermaid)

These views are aligned with the evaluation scheme expectation of architectural documentation using views, rationale, and quality considerations.

## 1) System Context View

```mermaid
graph TD
    PlayerA[Player Browser A] -->|HTTPS REST + WSS| App[CodeReboot Server\nSpring Boot]
    PlayerB[Player Browser B] -->|HTTPS REST + WSS| App

    App -->|JPA| Postgres[(PostgreSQL)]

    App --> Auth[Auth Module\nJWT + BCrypt]
    App --> Room[Room Service\nMatch orchestration]
    App --> Transport[WebSocket Transport\nLive snapshots/events]
    App --> Ops[System API\nHealth + request correlation]

    Tunnel[Optional Public Tunnel\nCloudflare/ngrok] --> App
```

## 2) Container View

```mermaid
graph LR
    subgraph BrowserClient[Browser Client]
        UI[HTML/CSS UI]
        JS[Vanilla JS Controllers\nauth.js room.js game.js]
        WSClient[WebSocket Client\nsocket.js]
    end

    subgraph SpringServer[Spring Boot Server]
        API[REST Controllers\nAuthController RoomController]
        AppSvc[Application Services\nAuthService RoomService\nGameSimulationService]
        Domain[Domain Model\nRoom Player CombatLifecycle]
        Security[Security\nSecurityConfig JWT Filter\nJwtTokenService RequestIdFilter BCrypt]
        WS[WebSocket Layer\nGameSocketHandler/Dispatcher\nWebSocketRoomBroadcaster]
        Ops[System API\nSystemController]
        Infra[Repositories\nUserRepository RoomRepository]
    end

    DB[(PostgreSQL)]

    UI --> JS
    JS -->|HTTP JSON| API
    JS -->|HTTP health check| Ops
    JS -->|WSS frames| WS
    API --> AppSvc
    WS --> AppSvc
    AppSvc --> Domain
    AppSvc --> Infra
    Security -. protects .-> API
    Security -. validates .-> WS
    Security -. correlates .-> Ops
    Infra --> DB
```

## 3) Component (Server Internal) View

```mermaid
graph TD
    SecurityConfig[SecurityConfig]
    JwtFilter[JwtAuthenticationFilter]
    RequestId[RequestIdFilter]
    JwtToken[JwtTokenService]

    AuthCtrl[AuthController]
    RoomCtrl[RoomController]
    SystemCtrl[SystemController]
    SocketHandler[GameSocketHandler]
    SocketDispatcher[GameSocketCommandDispatcher]

    AuthSvc[AuthService]
    RoomSvc[RoomService]
    SimSvc[GameSimulationService]

    RoomDomain[Room + RoomCombatLifecycle\nShotTracker TickHistoryTracker]
    Broadcaster[WebSocketRoomBroadcaster]

    UserRepo[UserRepository]
    RoomRepo[RoomRepository]

    SecurityConfig --> JwtFilter
    SecurityConfig --> RequestId
    JwtFilter --> JwtToken

    AuthCtrl --> AuthSvc
    AuthCtrl --> JwtToken

    RoomCtrl --> RoomSvc
    SystemCtrl --> RequestId

    SocketHandler --> SocketDispatcher
    SocketDispatcher --> JwtToken
    SocketDispatcher --> RoomSvc

    SimSvc --> RoomRepo
    SimSvc --> RoomSvc

    RoomSvc --> RoomRepo
    RoomSvc --> Broadcaster
    RoomSvc --> RoomDomain

    AuthSvc --> UserRepo
```

## 4) Runtime Sequence View (Login + Room Create + Match Loop)

```mermaid
sequenceDiagram
    autonumber
    participant U as User Browser
    participant A as AuthController
    participant AS as AuthService
    participant J as JwtTokenService
    participant R as RoomController
    participant RS as RoomService
    participant W as GameSocketHandler/Dispatcher

    U->>A: POST /api/auth/login
    A->>AS: authenticate(username,password)
    AS-->>A: User
    A->>J: issueToken(user)
    J-->>A: JWT
    A-->>U: AuthResponse + accessToken

    U->>R: POST /api/rooms (Bearer JWT)
    R->>RS: createRoom(authenticatedUsername)
    RS-->>R: RoomEntryResponse(roomCode,playerToken,snapshot)
    R-->>U: 200 room entry

    U->>W: WS subscribe(roomCode,playerToken,authToken)
    W->>J: validate authToken
    W->>RS: snapshot(roomCode,playerToken,username)
    RS-->>W: snapshot
    W-->>U: snapshot event

    loop gameplay ticks
        U->>W: input/move/fire/hitClaim
        W->>RS: apply command
        RS-->>W: updated snapshot
        W-->>U: snapshot broadcast
    end
```

## 5) Deployment View

```mermaid
graph TB
    subgraph ClientSide[Client Side]
        P1[Player Device 1\nBrowser]
        P2[Player Device 2\nBrowser]
    end

    subgraph HostMachine[Host Laptop / VM]
        App[CodeReboot Spring Boot App\nPort 8080]
        DB[(PostgreSQL)]
    end

    subgraph OptionalEdge[Optional Internet Exposure]
        T[Tunnel / Reverse Proxy\nTLS termination]
    end

    P1 -->|HTTPS/WSS| T
    P2 -->|HTTPS/WSS| T
    T -->|HTTP/WS| App
    App --> DB
```

## Quality Attribute Traceability Notes

- Performance and responsiveness:
  - WebSocket snapshots and command dispatch reduce polling overhead.
  - In-memory room state avoids DB write path for per-frame gameplay.

- Security:
  - JWT authentication for protected room APIs and websocket subscribe.
  - BCrypt password hashing and validation boundaries in AuthService.
    - Request ID propagation keeps logs and API errors traceable without external observability tooling.

- Modifiability:
  - Clear layering: API -> Application -> Domain -> Infrastructure.
  - Transport concerns are isolated from domain combat rules.

- Testability:
  - Unit tests cover domain combat/room logic and websocket parsing/dispatch behavior.
    - MVC and filter tests cover the health endpoint and request correlation path.
