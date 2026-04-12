import { apiJson, clearProfile, ensureProfile, loadProfile, saveProfile } from "./common.js";
import { openRoomSocket } from "./socket.js";
import { SocketCommand } from "./protocol.js";

const profile = ensureProfile();
const displayName = profile?.username ?? profile?.name ?? "pilot";

const roomTitle = document.querySelector("#room-title");
const roomSubtitle = document.querySelector("#room-subtitle");
const roomStatusText = document.querySelector("#room-status-text");
const playerCountBadge = document.querySelector("#player-count-badge");
const lobbyStateTitle = document.querySelector("#lobby-state-title");
const phaseBadge = document.querySelector("#phase-badge");
const connectionBadge = document.querySelector("#connection-badge");
const playerList = document.querySelector("#player-list");
const roomNotice = document.querySelector("#room-notice");
const lobbyFeed = document.querySelector("#lobby-feed");
const readyButton = document.querySelector("#ready-button");
const leaveButton = document.querySelector("#leave-button");
const createRoomForm = document.querySelector("#create-room-form");
const joinRoomForm = document.querySelector("#join-room-form");
const joinCodeInput = document.querySelector("#join-code-input");
const pasteCodeButton = document.querySelector("#paste-code-button");
const copyRoomCodeButton = document.querySelector("#copy-room-code-button");
const returnLoginButton = document.querySelector("#return-login-button");

const FEED_LIMIT = 10;

let socketHandle = null;
let currentRoomCode = profile?.roomCode ?? "";
let currentToken = profile?.token ?? "";
let currentSnapshot = null;
let latestEventMessage = "";

window.requestAnimationFrame(() => {
    document.body.classList.add("page-ready");
});

function addFeed(message, tone = "info") {
    if (!lobbyFeed || !message) {
        return;
    }

    if (lobbyFeed.firstElementChild?.textContent === message) {
        return;
    }

    const item = document.createElement("div");
    item.className = `feed-entry ${tone}`;
    item.textContent = message;
    lobbyFeed.prepend(item);

    while (lobbyFeed.childElementCount > FEED_LIMIT) {
        lobbyFeed.removeChild(lobbyFeed.lastElementChild);
    }
}

function roomStateMeta(snapshot) {
    if (!snapshot) {
        return {
            statusText: "Waiting",
            statusTone: "state-waiting",
            title: "No room selected"
        };
    }

    if (snapshot.phase === "COMPLETE") {
        return {
            statusText: "Match Complete",
            statusTone: "state-complete",
            title: "Match complete"
        };
    }

    if (snapshot.phase === "ACTIVE") {
        return {
            statusText: "In Progress",
            statusTone: "state-ready",
            title: "Battle online"
        };
    }

    const allReady = snapshot.players.length >= 2 && snapshot.players.every((player) => player.ready);
    return {
        statusText: allReady ? "Match Ready" : "Waiting",
        statusTone: allReady ? "state-ready" : "state-waiting",
        title: allReady ? "Lobby ready" : "Awaiting players"
    };
}

function setConnectionState(text, tone = "ghost") {
    if (!connectionBadge) {
        return;
    }
    connectionBadge.textContent = text;
    connectionBadge.className = tone === "ghost" ? "badge ghost" : "badge";
}

function setNotice(text, tone = "info") {
    if (!roomNotice) {
        return;
    }
    roomNotice.textContent = text;
    addFeed(text, tone);
}

function setRoomTitle(code) {
    if (!roomTitle) {
        return;
    }

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
    roomStatusText.textContent = "Waiting";
    roomStatusText.className = "badge state-waiting";
    playerCountBadge.textContent = "0/2 Players";
    readyButton.disabled = true;
    setConnectionState("Offline", "ghost");
    renderPlayers({ players: [] });
    joinCodeInput.value = "";
}

function renderPlayers(snapshot) {
    if (!playerList) {
        return;
    }

    if (!snapshot.players.length) {
        playerList.innerHTML = `
            <article class="roster-card">
                <div class="roster-head">
                    <div>
                        <div class="roster-name">Awaiting pilot</div>
                        <div class="roster-meta">No players in room yet.</div>
                    </div>
                    <span class="ping-badge">-- ms</span>
                </div>
                <div class="roster-stats">
                    <span class="roster-stat">HP --</span>
                    <span class="badge ghost">Standby</span>
                </div>
            </article>
        `;
        return;
    }

    playerList.innerHTML = snapshot.players
        .map((player) => {
            const status = player.ready ? "Ready" : "Waiting";
            const roleLabel = player.host ? "Host" : "Guest";
            const isCurrentUser = player.token === currentToken;
            const pingText = Number.isFinite(player.latencyMs) ? `${Math.round(player.latencyMs)} ms` : "-- ms";

            return `
                <article class="roster-card${isCurrentUser ? " current-user" : ""}">
                    <div class="roster-head">
                        <div>
                            <div class="roster-name">${player.name}${isCurrentUser ? " (You)" : ""}</div>
                            <div class="roster-meta">${roleLabel} · ${status}</div>
                        </div>
                        <span class="ping-badge">${pingText}</span>
                    </div>
                    <div class="roster-stats">
                        <span class="roster-stat">HP ${player.health}</span>
                        <span class="badge ${player.ready ? "state-ready" : "ghost"}">${status}</span>
                    </div>
                </article>
            `;
        })
        .join("");
}

function updateHeroStatus(snapshot) {
    const meta = roomStateMeta(snapshot);
    roomStatusText.textContent = meta.statusText;
    roomStatusText.className = `badge ${meta.statusTone}`;
    lobbyStateTitle.textContent = meta.title;

    const count = snapshot?.players?.length ?? 0;
    playerCountBadge.textContent = `${count}/2 Players`;
}

function updateActionBar(snapshot) {
    const players = snapshot?.players ?? [];
    readyButton.disabled = snapshot.phase !== "LOBBY" || players.length < 2;
}

function applySnapshot(snapshot) {
    currentSnapshot = snapshot;
    updateLeaveButtonState();
    setRoomTitle(snapshot.code);
    phaseBadge.textContent = snapshot.phase;
    updateHeroStatus(snapshot);
    renderPlayers(snapshot);
    updateActionBar(snapshot);

    if (snapshot.lastEvent && snapshot.lastEvent !== latestEventMessage) {
        latestEventMessage = snapshot.lastEvent;
        setNotice(snapshot.lastEvent, "info");
    }

    if (snapshot.phase === "ACTIVE") {
        saveProfile({ ...loadProfile(), roomCode: snapshot.code, token: currentToken });
        setConnectionState("Live");
        roomSubtitle.textContent = "The duel has started. Loading the arena now...";
        window.location.href = "/game.html";
        return true;
    }

    if (snapshot.phase === "COMPLETE") {
        const didWin = snapshot.winnerToken === currentToken;
        setConnectionState(didWin ? "Won" : "Lost");
        roomSubtitle.textContent = "Match complete. Ready up again for another round.";
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
        onError: (message) => setNotice(message, "warn"),
        onOpen: () => setConnectionState("Connecting"),
        onClose: () => setConnectionState("Offline", "ghost")
    });
}

async function joinRoom(roomCode) {
    const normalizedCode = roomCode.trim().toUpperCase();
    if (!normalizedCode) {
        setNotice("Enter a room code first.", "warn");
        return;
    }

    try {
        setNotice(`Joining ${normalizedCode}...`);
        const response = await apiJson("/api/rooms/join", {
            method: "POST",
            body: JSON.stringify({ roomCode: normalizedCode, name: displayName })
        });
        currentRoomCode = response.roomCode;
        currentToken = response.token;
        saveProfile({ ...loadProfile(), roomCode: currentRoomCode, token: currentToken });
        const shouldRedirect = applySnapshot(response.snapshot);
        if (!shouldRedirect) {
            connectRoom(currentRoomCode, currentToken);
        }
    } catch (error) {
        setNotice(error.message, "warn");
    }
}

async function hydrateFromStorage() {
    if (!profile?.roomCode || !profile?.token) {
        roomSubtitle.textContent = `Welcome, ${displayName}. Create a room or join with a code.`;
        setConnectionState("Offline", "ghost");
        updateLeaveButtonState();
        addFeed("No active room. Create one or join with a code.");
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
        setNotice(error.message, "warn");
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
        setNotice(error.message, "warn");
    }
});

joinRoomForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    await joinRoom(joinCodeInput.value);
});

joinCodeInput.addEventListener("input", () => {
    joinCodeInput.value = joinCodeInput.value.toUpperCase();
});

pasteCodeButton?.addEventListener("click", async () => {
    if (!navigator.clipboard?.readText) {
        setNotice("Clipboard paste is unavailable in this browser.", "warn");
        return;
    }

    try {
        const text = await navigator.clipboard.readText();
        joinCodeInput.value = text.trim().toUpperCase().slice(0, 5);
        setNotice("Code pasted.");
    } catch {
        setNotice("Unable to read clipboard.", "warn");
    }
});

copyRoomCodeButton?.addEventListener("click", async () => {
    if (!currentRoomCode) {
        setNotice("No active room code to copy.", "warn");
        return;
    }

    try {
        await navigator.clipboard.writeText(currentRoomCode);
        setNotice(`Copied room code ${currentRoomCode}.`);
    } catch {
        setNotice("Unable to copy room code.", "warn");
    }
});

readyButton.addEventListener("click", () => {
    if (!socketHandle) {
        setNotice("Connect to a room first.", "warn");
        return;
    }
    socketHandle.send({ type: SocketCommand.READY });
    setNotice("Readiness sent. Waiting on the second pilot.");
});

leaveButton.addEventListener("click", () => {
    if (!currentRoomCode || !currentToken) {
        setNotice("You're not in a room yet. Create one or join with a code.", "warn");
        updateLeaveButtonState();
        return;
    }

    const roomCode = currentRoomCode;
    const token = currentToken;
    apiJson(`/api/rooms/${encodeURIComponent(roomCode)}/leave?token=${encodeURIComponent(token)}`, {
        method: "POST"
    })
        .catch((error) => {
            setNotice(error.message, "warn");
        })
        .finally(() => {
            if (socketHandle) {
                socketHandle.close();
                socketHandle = null;
            }

            clearRoomSession();
            resetLobbyUI();
            setNotice("Left room. Create a new room or join with a code.");
        });
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
