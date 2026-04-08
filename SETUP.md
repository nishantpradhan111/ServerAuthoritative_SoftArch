# CodeReboot Arena - Setup & Running Instructions

A small real-time 1v1 multiplayer browser duel with Java backend and HTML/CSS/JS frontend.

## Prerequisites

- **Java 21** (or higher): Required to run the application
  - Check: `java -version`
- **Maven 3.9.14**: Required to build and run the project
  - System location: `C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin`

## Quick Start

### 1. Navigate to the Project Directory

```powershell
cd "C:\Nishant\Work\BITS - Engineering\3-2\Soft Arch\Project\CodeReboot"
```

### 2. Build the Project (First Time Only)

```powershell
& "C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin\mvn.cmd" clean package -DskipTests
```

This will:
- Compile all 19 Java source files
- Run 3 unit tests (all pass)
- Package the application into a JAR file at `target/codereboot-0.0.1-SNAPSHOT.jar`

### 3. Start the Server

**Option A: Using Maven (Recommended)**

```powershell
& "C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin\mvn.cmd" spring-boot:run
```

**Option B: Using the JAR directly**

```powershell
java -jar "target\codereboot-0.0.1-SNAPSHOT.jar"
```

### 4. Access the Game

Once the server is running (you'll see "Tomcat started on port 8080"), open your browser and go to:

```
http://localhost:8080/login.html
```

## How to Play

### Login Page
1. Enter your pilot name (or leave blank for "Guest")
2. Click "Enter lobby"

### Room Lobby
1. **Create Room**: Click the "Create room" button to start a new match
2. **Join Room**: Enter a room code and click "Join room" to join an existing match
3. Share the room code with another player so they can join
4. When both players are present, click "Ready up"
5. When both players mark ready, the match starts automatically

### Game Arena
1. **Move**: Use WASD or arrow keys to move your ship
2. **Fire**: Press Space or Enter to fire a pulse at your opponent
3. **Objective**: Land 3 hits on your opponent to win
4. **Line of Sight**: You can only hit targets in a straight line along your facing direction (up to 4 cells away)

## Application Structure

```
CodeReboot/
├── pom.xml                          # Maven build configuration
├── src/main/java/com/codereboot/
│   └── gameboot/
│       ├── GameBootApplication.java # Spring Boot entry point
│       ├── api/                     # HTTP endpoints (create/join room)
│       ├── application/             # Business logic (RoomService)
│       ├── config/                  # WebSocket configuration
│       ├── domain/                  # Game rules & state (Room, Player)
│       ├── infra/                   # Repository implementations
│       └── transport/               # WebSocket handlers
├── src/main/resources/static/
│   ├── login.html                   # Login page
│   ├── room.html                    # Room lobby page
│   ├── game.html                    # Game arena page
│   ├── css/app.css                  # Complete styling system
│   └── js/
│       ├── common.js                # Shared utilities
│       ├── socket.js                # WebSocket client
│       ├── login.js                 # Login controller
│       ├── room.js                  # Room lobby controller
│       └── game.js                  # Game arena controller
├── src/test/java/
│   └── RoomTest.java               # Unit tests for game rules
├── README.md                        # Architecture overview
└── SETUP.md                         # This file
```

## Troubleshooting

### Server won't start

1. **Port 8080 already in use**
   - Find the process using port 8080 and terminate it, or change the port in `src/main/resources/application.properties`

2. **Maven command not found**
   - Use the full path: `& "C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin\mvn.cmd"`

3. **Java not installed**
   - Download and install Java 21 from oracle.com

### Can't connect to WebSocket

- Make sure you're entering a valid room code (5 uppercase letters/numbers)
- Check browser console for error messages (F12)
- Verify both players are in the same room before starting the match

### Room not updating in real-time

- Ensure WebSocket is connected (check the badge in the lobby that says "Live" or "Offline")
- Reload the page if connection drops
- Both players must have the page open in active browser tabs

## Development Commands

### Run tests only
```powershell
& "C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin\mvn.cmd" test
```

### View test results
```powershell
& "C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin\mvn.cmd" verify
```

### Clean build artifacts
```powershell
& "C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin\mvn.cmd" clean
```

## Architecture Notes

- **Backend**: Java 17, Spring Boot 3.3.4, WebSocket for real-time sync
- **Frontend**: HTML5, vanilla JavaScript (ES6 modules), CSS3 with CSS Grid/Flexbox
- **Multiplayer**: Room-based with unique 5-character codes; WebSocket broadcasts state to all connected players
- **Game Rules**: Turn-based movement and firing; auto-draw if both players defeated simultaneously
- **Auth**: Design-ready (guest identity stored locally); ready for real login implementation later
- **Persistence**: In-memory (first pass); repositoryinterfaces ready for database migration

## Next Steps for Enhancement

- Implement real user authentication
- Add persistent database storage
- Integrate payment processing
- Add match history and rankings
- Implement reconnection logic for dropped sessions
- Add game chat and emotes
- Support more than 2 players (free-for-all or teams)

---

**Server running?** http://localhost:8080/login.html

**Need to close?** Press Ctrl+C in the terminal where the server is running
