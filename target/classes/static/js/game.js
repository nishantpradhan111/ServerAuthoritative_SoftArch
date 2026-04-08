import { ensureProfile } from "./common.js";
import { openRoomSocket } from "./socket.js";

const profile = ensureProfile();

if (!profile?.roomCode || !profile?.token) {
    window.location.href = "/room.html";
    throw new Error("Room session is missing");
}

const gameTitle = document.querySelector("#game-title");
const gameSubtitle = document.querySelector("#game-subtitle");
const roomCodeBadge = document.querySelector("#room-code-badge");
const phaseBadge = document.querySelector("#phase-badge");
const eventBanner = document.querySelector("#event-banner");
const board = document.querySelector("#board");
const scoreboard = document.querySelector("#scoreboard");
const gameLog = document.querySelector("#game-log");
const endOverlay = document.querySelector("#end-overlay");
const endTitle = document.querySelector("#end-title");
const endMessage = document.querySelector("#end-message");
const backToRoomButton = document.querySelector("#back-to-room-button");
const returnRoomButton = document.querySelector("#return-room-button");

let socketHandle = null;
let latestSnapshot = null;

function logLine(message) {
    if (!message) {
        return;
    }
    const entry = document.createElement("div");
    entry.className = "event-log-entry";
    entry.textContent = message;
    gameLog.prepend(entry);
}

function buildBoard(snapshot) {
    board.innerHTML = "";
    board.style.gridTemplateColumns = `repeat(${snapshot.boardWidth}, minmax(0, 1fr))`;
    board.style.gridTemplateRows = `repeat(${snapshot.boardHeight}, minmax(72px, 1fr))`;

    for (let row = 0; row < snapshot.boardHeight; row++) {
        for (let column = 0; column < snapshot.boardWidth; column++) {
            const cell = document.createElement("div");
            cell.className = "cell";
            board.appendChild(cell);
        }
    }

    snapshot.players.forEach((player) => {
        const marker = document.createElement("div");
        marker.className = `player-ship ${player.token === profile.token ? "self" : "enemy"}`;
        marker.style.gridColumn = String(player.x + 1);
        marker.style.gridRow = String(player.y + 1);
        marker.innerHTML = `<span>${player.name.slice(0, 2).toUpperCase()}</span>`;
        board.appendChild(marker);
    });
}

function renderScoreboard(snapshot) {
    scoreboard.innerHTML = snapshot.players
        .map((player) => {
            return `
                <div class="score-row">
                    <div class="score-meta">
                        <span class="score-name">${player.name}${player.token === profile.token ? " (you)" : ""}</span>
                        <span class="score-subtext">Facing ${player.facing}</span>
                    </div>
                    <div class="score-meta" style="min-width: 92px; text-align: right;">
                        <span class="score-name">HP ${player.health}</span>
                        <span class="score-subtext">${"▮".repeat(player.health)}${"▯".repeat(3 - player.health)}</span>
                    </div>
                </div>
            `;
        })
        .join("");
}

function showEndState(snapshot) {
    if (snapshot.phase !== "COMPLETE") {
        endOverlay.classList.remove("is-visible");
        return;
    }

    endOverlay.classList.add("is-visible");
    const didWin = snapshot.winnerToken === profile.token;
    endTitle.textContent = didWin ? "Victory" : "Defeat";
    endMessage.textContent = snapshot.lastEvent ?? (didWin ? "You closed out the duel." : "The rival closed the arena.");
}

function renderSnapshot(snapshot) {
    latestSnapshot = snapshot;
    gameTitle.textContent = `Neon Duel · Room ${snapshot.code}`;
    roomCodeBadge.textContent = `Room ${snapshot.code}`;
    phaseBadge.textContent = snapshot.phase;
    eventBanner.textContent = snapshot.lastEvent ?? "The arena is quiet.";
    gameSubtitle.textContent = snapshot.phase === "COMPLETE"
        ? "The duel has ended. You can return to the room to rematch or create a fresh lobby."
        : "Move with WASD or arrows. Fire with space. Hold your lane and pressure the rival.";

    buildBoard(snapshot);
    renderScoreboard(snapshot);
    showEndState(snapshot);

    if (snapshot.lastEvent) {
        logLine(snapshot.lastEvent);
    }
}

function connectRoom() {
    if (socketHandle) {
        socketHandle.close();
    }

    socketHandle = openRoomSocket({
        roomCode: profile.roomCode,
        token: profile.token,
        onSnapshot: renderSnapshot,
        onError: (message) => {
            eventBanner.textContent = message;
            logLine(message);
        }
    });
}

function sendAction(message) {
    if (!socketHandle) {
        return;
    }
    socketHandle.send(message);
}

document.addEventListener("keydown", (event) => {
    if (event.repeat) {
        return;
    }

    const key = event.key.toLowerCase();
    const mapping = {
        arrowup: { type: "move", direction: "up" },
        w: { type: "move", direction: "up" },
        arrowdown: { type: "move", direction: "down" },
        s: { type: "move", direction: "down" },
        arrowleft: { type: "move", direction: "left" },
        a: { type: "move", direction: "left" },
        arrowright: { type: "move", direction: "right" },
        d: { type: "move", direction: "right" },
        " ": { type: "fire" },
        enter: { type: "fire" }
    };

    const action = mapping[key];
    if (!action) {
        return;
    }

    event.preventDefault();
    sendAction(action);
});

document.querySelectorAll("[data-direction]").forEach((button) => {
    button.addEventListener("click", () => {
        sendAction({ type: "move", direction: button.dataset.direction });
    });
});

document.querySelectorAll("[data-action='fire']").forEach((button) => {
    button.addEventListener("click", () => {
        sendAction({ type: "fire" });
    });
});

backToRoomButton.addEventListener("click", () => {
    if (socketHandle) {
        socketHandle.close();
    }
    window.location.href = "/room.html";
});

returnRoomButton.addEventListener("click", () => {
    if (socketHandle) {
        socketHandle.close();
    }
    window.location.href = "/room.html";
});

connectRoom();