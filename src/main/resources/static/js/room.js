import { apiJson, clearProfile, ensureProfile, loadProfile, saveProfile } from "./common.js";
import { openRoomSocket } from "./socket.js";

const profile = ensureProfile();
const displayName = profile?.username ?? profile?.name ?? "pilot";

const roomTitle = document.querySelector("#room-title");
const roomSubtitle = document.querySelector("#room-subtitle");
const lobbyStateTitle = document.querySelector("#lobby-state-title");
const phaseBadge = document.querySelector("#phase-badge");
const connectionBadge = document.querySelector("#connection-badge");
const playerList = document.querySelector("#player-list");
const roomNotice = document.querySelector("#room-notice");
const readyButton = document.querySelector("#ready-button");
const leaveButton = document.querySelector("#leave-button");
const createRoomForm = document.querySelector("#create-room-form");
const joinRoomForm = document.querySelector("#join-room-form");
const joinCodeInput = document.querySelector("#join-code-input");
const returnLoginButton = document.querySelector("#return-login-button");

let socketHandle = null;
let currentRoomCode = profile?.roomCode ?? "";
let currentToken = profile?.token ?? "";
let currentSnapshot = null;

function setConnectionState(text, tone = "ghost") {
    connectionBadge.textContent = text;
    connectionBadge.className = tone === "ghost" ? "badge ghost" : "badge";
}

function setNotice(text) {
    roomNotice.textContent = text;
}

function setRoomTitle(code) {
    if (!code) {
        roomTitle.textContent = "Awaiting room";
        return;
    }

    roomTitle.innerHTML = `Room <span class="room-code-glow">${code}</span>`;
}

function updateLeaveButtonState() {
    leaveButton.disabled = !currentRoomCode || !currentToken;
}

function clearRoomSession() {
    const nextProfile = { ...loadProfile() };
    delete nextProfile.roomCode;
    delete nextProfile.token;
    saveProfile(nextProfile);
    currentRoomCode = "";
    currentToken = "";
    currentSnapshot = null;
    updateLeaveButtonState();
}

function resetLobbyUI() {
    setRoomTitle("");
    roomSubtitle.textContent = `Welcome, ${displayName}. Create a room or join with a code.`;
    lobbyStateTitle.textContent = "No room selected";
    phaseBadge.textContent = "IDLE";
    readyButton.disabled = true;
    setConnectionState("Offline", "ghost");
    renderPlayers({ players: [] });
    joinCodeInput.value = "";
}

function renderPlayers(snapshot) {
    if (!snapshot.players.length) {
        playerList.innerHTML = `<div class="player-row"><div class="player-meta"><span class="player-name">Waiting</span><span class="player-subtext">No pilots in the room yet.</span></div></div>`;
        return;
    }

    playerList.innerHTML = snapshot.players
        .map((player) => {
            const status = player.ready ? "Ready" : "Waiting";
            const turnLabel = player.host ? "Host" : "Guest";
            return `
                <div class="player-row">
                    <div class="player-meta">
                        <span class="player-name">${player.name}</span>
                        <span class="player-subtext">${turnLabel} · ${status}</span>
                    </div>
                    <div class="player-flags">
                        <span class="badge ${player.ready ? "" : "ghost"}">${status}</span>
                        <span class="badge ghost">HP ${player.health}</span>
                    </div>
                </div>
            `;
        })
        .join("");
}

function applySnapshot(snapshot) {
    currentSnapshot = snapshot;
    updateLeaveButtonState();
    setRoomTitle(snapshot.code);
    lobbyStateTitle.textContent = snapshot.phase === "ACTIVE" ? "Battle online" : snapshot.phase === "COMPLETE" ? "Match complete" : "Lobby ready";
    phaseBadge.textContent = snapshot.phase;
    setNotice(snapshot.lastEvent ?? "Room updated");
    renderPlayers(snapshot);
    readyButton.disabled = snapshot.phase !== "LOBBY" || snapshot.players.length < 2;

    if (snapshot.phase === "ACTIVE") {
        saveProfile({ ...loadProfile(), roomCode: snapshot.code, token: currentToken });
        setConnectionState("Live");
        roomSubtitle.textContent = "The duel has started. Loading the arena now...";
        window.location.href = "/game.html";
        return true;
    }

    if (snapshot.phase === "COMPLETE") {
        setConnectionState("Complete");
        roomSubtitle.textContent = "The match is over. You can re-enter or create a new room.";
        return false;
    }

    setConnectionState("Live");
    roomSubtitle.textContent = `Room ${snapshot.code} is open. Share the code and get the second pilot ready.`;
    return false;
}

function connectRoom(roomCode, token) {
    if (socketHandle) {
        socketHandle.close();
    }

    socketHandle = openRoomSocket({
        roomCode,
        token,
        onSnapshot: applySnapshot,
        onError: setNotice,
        onOpen: () => setConnectionState("Connecting"),
        onClose: () => setConnectionState("Offline", "ghost")
    });
}

async function hydrateFromStorage() {
    if (!profile?.roomCode || !profile?.token) {
        roomSubtitle.textContent = `Welcome, ${displayName}. Create a room or join with a code.`;
        setConnectionState("Offline", "ghost");
        updateLeaveButtonState();
        return;
    }

    currentRoomCode = profile.roomCode;
    currentToken = profile.token;
    updateLeaveButtonState();

    try {
        const snapshot = await apiJson(`/api/rooms/${encodeURIComponent(profile.roomCode)}?token=${encodeURIComponent(profile.token)}`);
        const shouldRedirect = applySnapshot(snapshot);
        if (!shouldRedirect) {
            connectRoom(profile.roomCode, profile.token);
        }
    } catch (error) {
        setNotice(error.message);
        setConnectionState("Offline", "ghost");
    }
}

createRoomForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        setNotice("Creating room...");
        const response = await apiJson("/api/rooms", {
            method: "POST",
            body: JSON.stringify({ name: displayName })
        });
        currentRoomCode = response.roomCode;
        currentToken = response.token;
        saveProfile({ ...loadProfile(), roomCode: currentRoomCode, token: currentToken });
        const shouldRedirect = applySnapshot(response.snapshot);
        if (!shouldRedirect) {
            connectRoom(currentRoomCode, currentToken);
        }
    } catch (error) {
        setNotice(error.message);
    }
});

joinRoomForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const roomCode = joinCodeInput.value.trim().toUpperCase();
    if (!roomCode) {
        setNotice("Enter a room code first.");
        return;
    }

    try {
        setNotice(`Joining ${roomCode}...`);
        const response = await apiJson("/api/rooms/join", {
            method: "POST",
            body: JSON.stringify({ roomCode, name: displayName })
        });
        currentRoomCode = response.roomCode;
        currentToken = response.token;
        saveProfile({ ...loadProfile(), roomCode: currentRoomCode, token: currentToken });
        const shouldRedirect = applySnapshot(response.snapshot);
        if (!shouldRedirect) {
            connectRoom(currentRoomCode, currentToken);
        }
    } catch (error) {
        setNotice(error.message);
    }
});

readyButton.addEventListener("click", () => {
    if (!socketHandle) {
        setNotice("Connect to a room first.");
        return;
    }
    socketHandle.send({ type: "ready" });
    setNotice("Readiness sent. Waiting on the second pilot.");
});

leaveButton.addEventListener("click", () => {
    if (!currentRoomCode || !currentToken) {
        setNotice("You're not in a room yet. Create one or join with a code.");
        updateLeaveButtonState();
        return;
    }

    if (socketHandle) {
        socketHandle.close();
        socketHandle = null;
    }

    clearRoomSession();
    resetLobbyUI();
    setNotice("Left room. Create a new room or join with a code.");
});

returnLoginButton.addEventListener("click", () => {
    if (socketHandle) {
        socketHandle.close();
    }
    clearProfile();
    window.location.href = "/login.html";
});

updateLeaveButtonState();
hydrateFromStorage();