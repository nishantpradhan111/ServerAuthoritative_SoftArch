import { ensureProfile } from "./common.js";
import { openRoomSocket } from "./socket.js";
import { SocketCommand } from "./protocol.js";
import { resolveEndPresentation } from "./game/end-state.js";
import { createHitClaimTracker } from "./game/hit-claim-tracker.js";
import { blend, clamp, distance2D, normalizeDegrees, normalizeVector } from "./game/math.js";
import { navigateToReplayRoom, navigateToRoom } from "./game/room-navigation.js";
import { setReplayStatusBadge } from "./game/replay-ui.js";
import { drawAimTracer, drawArenaGrid, drawPlayer, renderBackdrop, worldToScreen } from "./game/rendering.js";
import { buildReplayUiState, canRequestReplay } from "./game/replay-state.js";
import { advanceBullets, assignShotIdToLatestBullet, clearBullets, drawBullets, resolveBulletCollisions, spawnBullet } from "./game/effects.js";

const profile = ensureProfile();

if (!profile?.roomCode || !profile?.token) {
    window.location.href = "/room.html";
    throw new Error("Room session is missing");
}

const gameTitle = document.querySelector("#game-title");
const gameRoomTitle = document.querySelector("#game-room-title");
const gameSubtitle = document.querySelector("#game-subtitle");
const roomCodeBadge = document.querySelector("#room-code-badge");
const phaseBadge = document.querySelector("#phase-badge");
const tickBadge = document.querySelector("#tick-badge");
const eventBanner = document.querySelector("#event-banner");
const canvas = document.querySelector("#game-canvas");
const fpsStage = document.querySelector("#fps-stage");
const aimCursor = document.querySelector("#aim-cursor");
const hudHealth = document.querySelector("#hud-health");
const hudAmmo = document.querySelector("#hud-ammo");
const hudLatency = document.querySelector("#hud-latency");
const hudCorrection = document.querySelector("#hud-correction");
const fireActionButton = document.querySelector("#fire-action-button");
const scoreboard = document.querySelector("#scoreboard");
const gameLog = document.querySelector("#game-log");
const endOverlay = document.querySelector("#end-overlay");
const endTitle = document.querySelector("#end-title");
const endMessage = document.querySelector("#end-message");
const replayButton = document.querySelector("#replay-button");
const replayStatusBadge = document.querySelector("#replay-status-badge");
const backToRoomButton = document.querySelector("#back-to-room-button");
const returnRoomButton = document.querySelector("#return-room-button");

const context = canvas.getContext("2d");

const CLIENT_SEND_INTERVAL_MS = 16;
const CLIENT_REPLAY_STEP_SECONDS = 1 / 30;
const PLAYER_SPEED = 4.0;
const PLAYER_RADIUS = 0.35;
const FIRE_COOLDOWN_MS = 180;
const MAX_LOG_LINES = 18;
const SNAPSHOT_STALE_MS = 2200;
const RECONNECT_COOLDOWN_MS = 1500;

let socketHandle = null;
let latestSnapshot = null;
let latestEventMessage = null;
let latestAppliedTick = -1;
let latestAckSequence = 0;
let lastSnapshotReceivedAt = 0;
let lastReconnectAttemptAt = 0;

let animationFrameId = null;
let lastAnimationTime = performance.now();
let sendAccumulatorMs = 0;
let watchdogIntervalId = null;

let inputSequence = 0;
let localAimDegrees = 0;
let aimCursorX = 0;
let aimCursorY = 0;
let triggerPressed = false;
let lastFireSentAt = 0;

let opponentPreviousAmmo = null;
let selfPreviousAmmo = null;
const hitClaimTracker = createHitClaimTracker();
let replayAckDeadline = 0;
let returnRoomPending = false;
let selectedEndMessage = null;
let selectedEndMatchKey = null;

let predictedSelf = null;
let displaySelf = null;
let correctionMagnitude = 0;
let smoothedLatencyMs = null;
const pendingInputs = [];
const sentInputTimes = new Map();

const keyState = {
    forward: false,
    backward: false,
    left: false,
    right: false,
    fireKey: false
};

const worldMetrics = {
    boardWidth: 7,
    boardHeight: 5
};

function logLine(message) {
    if (!message) {
        return;
    }
    if (gameLog.firstElementChild?.textContent === message) {
        return;
    }
    const entry = document.createElement("div");
    entry.className = "event-log-entry";
    entry.textContent = message;
    gameLog.prepend(entry);

    while (gameLog.childElementCount > MAX_LOG_LINES) {
        gameLog.removeChild(gameLog.lastElementChild);
    }
}

function resetClientPredictionState() {
    latestAppliedTick = -1;
    latestAckSequence = 0;
    inputSequence = 0;
    predictedSelf = null;
    displaySelf = null;
    correctionMagnitude = 0;
    pendingInputs.length = 0;
    sentInputTimes.clear();
    sendAccumulatorMs = 0;
    clearBullets();
    opponentPreviousAmmo = null;
    selfPreviousAmmo = null;
    hitClaimTracker.clear();
    replayAckDeadline = 0;
    returnRoomPending = false;
    if (replayButton) {
        replayButton.disabled = false;
    }
    if (returnRoomButton) {
        returnRoomButton.disabled = false;
    }
    setReplayStatus("", "idle");
}

function setReplayStatus(text, tone = "idle") {
    setReplayStatusBadge(replayStatusBadge, text, tone);
}

function mapKeyboardState(event, isPressed) {
    const key = event.key.toLowerCase();
    if (key === "w" || key === "arrowup") {
        keyState.forward = isPressed;
        return true;
    }
    if (key === "s" || key === "arrowdown") {
        keyState.backward = isPressed;
        return true;
    }
    if (key === "a" || key === "arrowleft") {
        keyState.left = isPressed;
        return true;
    }
    if (key === "d" || key === "arrowright") {
        keyState.right = isPressed;
        return true;
    }
    if (key === " " || key === "enter") {
        keyState.fireKey = isPressed;
        return true;
    }
    return false;
}

function updateAimFromPointer(clientX, clientY) {
    const rect = fpsStage.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
        return;
    }

    const localX = clamp(clientX - rect.left, 0, rect.width);
    const localY = clamp(clientY - rect.top, 0, rect.height);
    const centerX = rect.width * 0.5;
    const centerY = rect.height * 0.5;
    const angleRad = Math.atan2(localY - centerY, localX - centerX);
    localAimDegrees = normalizeDegrees((angleRad * 180) / Math.PI);
    aimCursorX = localX;
    aimCursorY = localY;

    if (aimCursor) {
        aimCursor.style.left = `${localX}px`;
        aimCursor.style.top = `${localY}px`;
        aimCursor.style.setProperty("--cursor-rotation", `${localAimDegrees}deg`);
    }
}

function centerAimCursor() {
    const rect = fpsStage.getBoundingClientRect();
    updateAimFromPointer(rect.left + (rect.width * 0.5), rect.top + (rect.height * 0.5));
}

function movementVector() {
    let x = 0;
    let y = 0;

    if (keyState.left) {
        x -= 1;
    }
    if (keyState.right) {
        x += 1;
    }
    if (keyState.forward) {
        y -= 1;
    }
    if (keyState.backward) {
        y += 1;
    }

    return normalizeVector(x, y);
}

function shouldFire(nowMs) {
    const wantsFire = triggerPressed || keyState.fireKey;
    if (!wantsFire) {
        return false;
    }

    const self = latestSnapshot?.players.find((player) => player.token === profile.token);
    if (!self || self.ammo <= 0) {
        return false;
    }

    if ((nowMs - lastFireSentAt) < FIRE_COOLDOWN_MS) {
        return false;
    }

    lastFireSentAt = nowMs;
    return true;
}

function sendInstantFire() {
    if (!socketHandle || latestSnapshot?.phase !== "ACTIVE") {
        return;
    }

    const self = latestSnapshot?.players.find((player) => player.token === profile.token);
    if (!self || self.ammo <= 0) {
        return;
    }

    const nowMs = performance.now();
    if ((nowMs - lastFireSentAt) < FIRE_COOLDOWN_MS) {
        return;
    }

    lastFireSentAt = nowMs;
    const movement = movementVector();
    const inputFrame = {
        type: SocketCommand.INPUT,
        sequence: ++inputSequence,
        moveX: movement.x,
        moveY: movement.y,
        aimDegrees: normalizeDegrees(localAimDegrees),
        firing: true
    };

    socketHandle.send(inputFrame);
    sentInputTimes.set(inputFrame.sequence, nowMs);
    pendingInputs.push(inputFrame);
    spawnLocalBullet(inputFrame.aimDegrees);

    if (predictedSelf) {
        applyInputToState(predictedSelf, inputFrame, CLIENT_SEND_INTERVAL_MS / 1000);
    }
}

function spawnLocalBullet(aimDegrees) {
    const localPlayer = displaySelf ?? predictedSelf ?? latestSnapshot?.players.find((player) => player.token === profile.token);
    if (!localPlayer) {
        return;
    }

    spawnBullet({
        x: localPlayer.x ?? localPlayer.positionX,
        y: localPlayer.y ?? localPlayer.positionY,
        aimDegrees: aimDegrees ?? localPlayer.aimDegrees ?? 0,
        ownerToken: profile.token
    });
}

function sendHitClaim(shotId, snapshotTick, bulletId) {
    if (!socketHandle || latestSnapshot?.phase !== "ACTIVE") {
        return;
    }

    if (!shotId || snapshotTick == null || snapshotTick < 0) {
        return;
    }

    if (!hitClaimTracker.register(shotId, snapshotTick)) {
        return;
    }

    socketHandle.send({
        type: SocketCommand.HIT_CLAIM,
        shotId,
        snapshotTick
    });
}

function handleBulletImpact(impact) {
    if (!impact?.bullet || !latestSnapshot) {
        return;
    }

    const selfToken = profile.token;
    const claimedTick = impact.snapshotTick ?? latestSnapshot.simulationTick;
    const shotId = impact.bullet.shotId;
    if (!shotId) {
        return;
    }

    if (impact.reason === "hit-opponent" && impact.bullet.ownerToken === selfToken) {
        sendHitClaim(shotId, claimedTick, impact.bullet.id);
        return;
    }

    if (impact.reason === "hit-self" && impact.bullet.ownerToken && impact.bullet.ownerToken !== selfToken) {
        sendHitClaim(shotId, claimedTick, impact.bullet.id);
    }
}

function applyInputToState(state, inputFrame, deltaSeconds) {
    const clampedX = clamp(inputFrame.moveX, -1, 1);
    const clampedY = clamp(inputFrame.moveY, -1, 1);
    state.vx = clampedX * PLAYER_SPEED;
    state.vy = clampedY * PLAYER_SPEED;
    state.aimDegrees = normalizeDegrees(inputFrame.aimDegrees ?? state.aimDegrees ?? 0);

    state.x += state.vx * deltaSeconds;
    state.y += state.vy * deltaSeconds;

    state.x = clamp(state.x, PLAYER_RADIUS, worldMetrics.boardWidth - 1 - PLAYER_RADIUS);
    state.y = clamp(state.y, PLAYER_RADIUS, worldMetrics.boardHeight - 1 - PLAYER_RADIUS);
}

function buildInputFrame(nowMs) {
    const movement = movementVector();
    const firing = shouldFire(nowMs);

    return {
        type: SocketCommand.INPUT,
        sequence: ++inputSequence,
        moveX: movement.x,
        moveY: movement.y,
        aimDegrees: normalizeDegrees(localAimDegrees),
        firing
    };
}

function reconcileSelf(authoritativeSelf) {
    if (!authoritativeSelf) {
        return;
    }

    const previousPrediction = predictedSelf
        ? { ...predictedSelf }
        : {
            x: authoritativeSelf.positionX,
            y: authoritativeSelf.positionY,
            vx: authoritativeSelf.velocityX,
            vy: authoritativeSelf.velocityY,
            aimDegrees: authoritativeSelf.aimDegrees
        };

    predictedSelf = {
        x: authoritativeSelf.positionX,
        y: authoritativeSelf.positionY,
        vx: authoritativeSelf.velocityX,
        vy: authoritativeSelf.velocityY,
        aimDegrees: authoritativeSelf.aimDegrees
    };

    const acknowledgedSequence = authoritativeSelf.lastProcessedInputSequence ?? 0;

    const acknowledgedAt = sentInputTimes.get(acknowledgedSequence);
    if (acknowledgedAt) {
        const roundTrip = performance.now() - acknowledgedAt;
        smoothedLatencyMs = smoothedLatencyMs == null
            ? roundTrip
            : blend(smoothedLatencyMs, roundTrip, 0.2);
    }

    while (pendingInputs.length > 0 && pendingInputs[0].sequence <= acknowledgedSequence) {
        const removed = pendingInputs.shift();
        sentInputTimes.delete(removed.sequence);
    }

    for (const queuedInput of pendingInputs) {
        applyInputToState(predictedSelf, queuedInput, CLIENT_REPLAY_STEP_SECONDS);
    }

    correctionMagnitude = distance2D(previousPrediction, predictedSelf);

    if (!displaySelf) {
        displaySelf = { ...predictedSelf };
    }
}

function renderScoreboard(snapshot) {
    scoreboard.innerHTML = snapshot.players
        .map((player) => {
            const isSelf = player.token === profile.token;
            const speed = Math.hypot(player.velocityX, player.velocityY).toFixed(2);
            return `
                <div class="score-row">
                    <div class="score-meta">
                        <span class="score-name">${player.name}${isSelf ? " (you)" : ""}</span>
                        <span class="score-subtext">Aim ${Math.round(player.aimDegrees)}° | Speed ${speed}</span>
                    </div>
                    <div class="score-meta score-stats">
                        <span class="score-name">HP ${player.health}</span>
                        <span class="score-subtext">Ammo ${player.ammo}</span>
                    </div>
                </div>
            `;
        })
        .join("");
}

function showEndState(snapshot) {
    const endPresentation = resolveEndPresentation(snapshot, profile.token, selectedEndMatchKey, selectedEndMessage);
    if (!endPresentation.visible) {
        endOverlay.classList.remove("is-visible");
        replayAckDeadline = 0;
        returnRoomPending = false;
        selectedEndMessage = null;
        selectedEndMatchKey = null;
        if (replayButton) {
            replayButton.disabled = false;
        }
        if (returnRoomButton) {
            returnRoomButton.disabled = false;
        }
        setReplayStatus("", "idle");
        return;
    }

    endOverlay.classList.add("is-visible");
    selectedEndMessage = endPresentation.message;
    selectedEndMatchKey = endPresentation.matchKey;
    endTitle.textContent = endPresentation.title;
    endMessage.textContent = endPresentation.message;

    const replayUi = buildReplayUiState(snapshot, profile.token, replayAckDeadline, performance.now());
    if (replayUi.selfReplayPending) {
        replayAckDeadline = 0;
    }
    setReplayStatus(replayUi.statusText, replayUi.statusTone);

    if (replayButton) {
        replayButton.disabled = replayUi.selfReplayPending || replayUi.awaitingReplayAck;
    }
    if (returnRoomButton) {
        returnRoomButton.disabled = returnRoomPending;
    }
}

function handleReplayRedirect({ roomCode, token }) {
    if (!roomCode || !token) {
        return;
    }

    setReplayStatus("Replay matched. Redirecting...", "matched");
    navigateToReplayRoom(roomCode, token, cleanupAndExit);
}

function handleRoomReturn(message) {
    if (message) {
        logLine(message);
    }
    returnRoomPending = false;
    setReplayStatus("Returning to room...", "matched");
    navigateToRoom(cleanupAndExit);
}

function updateSelfDisplay() {
    if (!predictedSelf) {
        return;
    }
    if (!displaySelf) {
        displaySelf = { ...predictedSelf };
        return;
    }

    displaySelf.x = blend(displaySelf.x, predictedSelf.x, 0.22);
    displaySelf.y = blend(displaySelf.y, predictedSelf.y, 0.22);
    displaySelf.vx = blend(displaySelf.vx, predictedSelf.vx, 0.2);
    displaySelf.vy = blend(displaySelf.vy, predictedSelf.vy, 0.2);
    displaySelf.aimDegrees = normalizeDegrees(blend(displaySelf.aimDegrees, predictedSelf.aimDegrees, 0.24));
}

function drawScene() {
    if (!latestSnapshot) {
        return;
    }

    const width = canvas.width;
    const height = canvas.height;
    renderBackdrop(context, width, height);

    const selfSnapshot = latestSnapshot.players.find((player) => player.token === profile.token);
    if (!selfSnapshot) {
        return;
    }

    updateSelfDisplay();

    const cameraX = displaySelf ? displaySelf.x : selfSnapshot.positionX;
    const cameraY = displaySelf ? displaySelf.y : selfSnapshot.positionY;
    const scale = Math.max(40, Math.min(width / worldMetrics.boardWidth, height / worldMetrics.boardHeight) * 1.22);
    const selfRenderX = displaySelf ? displaySelf.x : selfSnapshot.positionX;
    const selfRenderY = displaySelf ? displaySelf.y : selfSnapshot.positionY;
    const selfScreen = worldToScreen(selfRenderX, selfRenderY, cameraX, cameraY, scale, width, height);

    drawArenaGrid(context, cameraX, cameraY, scale, width, height, worldMetrics);

    latestSnapshot.players.forEach((player) => {
        const renderPlayer = player.token === profile.token && displaySelf
            ? {
                ...player,
                positionX: displaySelf.x,
                positionY: displaySelf.y,
                velocityX: displaySelf.vx,
                velocityY: displaySelf.vy,
                aimDegrees: displaySelf.aimDegrees
            }
            : player;

        drawPlayer(context, renderPlayer, player.token === profile.token, cameraX, cameraY, scale, width, height, PLAYER_RADIUS);
    });

    drawBullets(context, cameraX, cameraY, scale, width, height);

    drawAimTracer(context, selfScreen.x, selfScreen.y, cameraX, cameraY, scale, width, height, worldMetrics, aimCursorX, aimCursorY);
}

function resizeCanvasIfNeeded() {
    const width = Math.floor(fpsStage.clientWidth);
    const height = Math.floor(fpsStage.clientHeight);

    if (canvas.width === width && canvas.height === height) {
        return;
    }

    canvas.width = width;
    canvas.height = height;
    centerAimCursor();
}

function updateHud() {
    const self = latestSnapshot?.players.find((player) => player.token === profile.token);
    if (self) {
        hudHealth.textContent = String(self.health);
        hudAmmo.textContent = String(self.ammo);
    }

    hudCorrection.textContent = correctionMagnitude.toFixed(3);
    hudLatency.textContent = smoothedLatencyMs == null ? "-- ms" : `${Math.round(smoothedLatencyMs)} ms`;
}

function animationStep(now) {
    const deltaMs = Math.min(64, now - lastAnimationTime);
    lastAnimationTime = now;
    sendAccumulatorMs += deltaMs;
    const isActive = latestSnapshot?.phase === "ACTIVE";

    advanceBullets(deltaMs / 1000);
    resolveBulletCollisions(latestSnapshot, profile.token, worldMetrics, handleBulletImpact);

    if (!isActive && pendingInputs.length > 0) {
        pendingInputs.length = 0;
        sentInputTimes.clear();
    }

    while (sendAccumulatorMs >= CLIENT_SEND_INTERVAL_MS && socketHandle && isActive) {
        const inputFrame = buildInputFrame(now);
        socketHandle.send(inputFrame);

        sentInputTimes.set(inputFrame.sequence, performance.now());
        pendingInputs.push(inputFrame);
        if (inputFrame.firing) {
            spawnLocalBullet(inputFrame.aimDegrees);
        }

        if (predictedSelf) {
            applyInputToState(predictedSelf, inputFrame, CLIENT_SEND_INTERVAL_MS / 1000);
        }

        sendAccumulatorMs -= CLIENT_SEND_INTERVAL_MS;
    }

    resizeCanvasIfNeeded();
    drawScene();
    updateHud();
    animationFrameId = window.requestAnimationFrame(animationStep);
}

function renderSnapshot(snapshot) {
    lastSnapshotReceivedAt = performance.now();

    const self = snapshot.players.find((player) => player.token === profile.token);
    const opponent = snapshot.players.find((player) => player.token !== profile.token);
    const receivedAckSequence = self?.lastProcessedInputSequence ?? latestAckSequence;

    if (snapshot.simulationTick < latestAppliedTick) {
        return;
    }

    if (self && selfPreviousAmmo !== null && self.ammo < selfPreviousAmmo && self.lastShotId > 0) {
        assignShotIdToLatestBullet(self.token, self.lastShotId);
    }

    if (opponent && opponentPreviousAmmo !== null && opponent.ammo < opponentPreviousAmmo) {
        if (opponentPreviousAmmo > 0) {
            spawnBullet({
                x: opponent.positionX,
                y: opponent.positionY,
                aimDegrees: opponent.aimDegrees,
                color: "#00ffcc",
                firedBy: "opponent",
                ownerToken: opponent.token
            });
            if (opponent.lastShotId > 0) {
                assignShotIdToLatestBullet(opponent.token, opponent.lastShotId);
            }
        }
    }
    if (self) {
        selfPreviousAmmo = self.ammo;
    }
    if (opponent) {
        opponentPreviousAmmo = opponent.ammo;
    }

    latestSnapshot = snapshot;
    latestAppliedTick = Math.max(latestAppliedTick, snapshot.simulationTick);
    if (self) {
        latestAckSequence = receivedAckSequence;
    }
    worldMetrics.boardWidth = snapshot.boardWidth;
    worldMetrics.boardHeight = snapshot.boardHeight;

    gameTitle.textContent = "Neon Duel";
    gameRoomTitle.textContent = `Room ${snapshot.code}`;
    roomCodeBadge.textContent = `Room ${snapshot.code}`;
    phaseBadge.textContent = snapshot.phase;
    tickBadge.textContent = `Tick ${snapshot.simulationTick}`;
    eventBanner.textContent = snapshot.lastEvent ?? "The arena is quiet.";
    gameSubtitle.textContent = snapshot.phase === "COMPLETE"
        ? "The duel has ended. Return to the room to rematch."
        : "WASD to strafe, mouse to look, click or space to fire.";

    renderScoreboard(snapshot);
    showEndState(snapshot);

    if (self) {
        localAimDegrees = self.aimDegrees;
        reconcileSelf(self);
    }

    if (snapshot.lastEvent && snapshot.lastEvent !== latestEventMessage) {
        latestEventMessage = snapshot.lastEvent;
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
        onReplayRedirect: handleReplayRedirect,
        onRoomReturn: handleRoomReturn,
        onError: (message) => {
            replayAckDeadline = 0;
            returnRoomPending = false;
            eventBanner.textContent = message;
            logLine(message);
        },
        onClose: () => {
            if (document.visibilityState === "visible") {
                maybeReconnect("Socket closed");
            }
        }
    });
}

function maybeReconnect(reason) {
    const now = performance.now();
    if ((now - lastReconnectAttemptAt) < RECONNECT_COOLDOWN_MS) {
        return;
    }

    lastReconnectAttemptAt = now;
    resetClientPredictionState();
    eventBanner.textContent = `${reason}. Reconnecting...`;
    logLine(`${reason}. Reconnecting...`);
    connectRoom();
}

function cleanupAndExit() {
    if (animationFrameId) {
        window.cancelAnimationFrame(animationFrameId);
    }
    if (watchdogIntervalId) {
        window.clearInterval(watchdogIntervalId);
    }
    if (socketHandle) {
        socketHandle.close();
    }
}

document.addEventListener("keydown", (event) => {
    const handled = mapKeyboardState(event, true);
    if (handled) {
        event.preventDefault();
    }
});

document.addEventListener("keyup", (event) => {
    const handled = mapKeyboardState(event, false);
    if (handled) {
        event.preventDefault();
    }
});

canvas.addEventListener("click", () => {
    centerAimCursor();
});

canvas.addEventListener("mousedown", (event) => {
    if (event.button === 0) {
        triggerPressed = true;
        updateAimFromPointer(event.clientX, event.clientY);
    }
});

fireActionButton.addEventListener("click", (event) => {
    event.preventDefault();
    sendInstantFire();
});

replayButton.addEventListener("click", () => {
    if (!socketHandle || !canRequestReplay(latestSnapshot, profile.token)) {
        return;
    }

    replayAckDeadline = performance.now() + 2500;
    replayButton.disabled = true;
    setReplayStatus("Sending replay request...", "pending");
    eventBanner.textContent = "Replay request sent. Waiting for opponent...";
    logLine("Replay request sent. Waiting for opponent...");
    socketHandle.send({ type: SocketCommand.REPLAY });
});

document.addEventListener("mouseup", (event) => {
    if (event.button === 0) {
        triggerPressed = false;
    }
});

fpsStage.addEventListener("mousemove", (event) => {
    updateAimFromPointer(event.clientX, event.clientY);
});

document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
        const now = performance.now();
        const snapshotIsStale = lastSnapshotReceivedAt > 0 && (now - lastSnapshotReceivedAt) > SNAPSHOT_STALE_MS;
        if (snapshotIsStale) {
            maybeReconnect("Snapshot stream stalled after tab switch");
        }
    }
});

backToRoomButton.addEventListener("click", () => {
    navigateToRoom(cleanupAndExit);
});

returnRoomButton.addEventListener("click", () => {
    if (!socketHandle || latestSnapshot?.phase !== "COMPLETE") {
        navigateToRoom(cleanupAndExit);
        return;
    }

    if (returnRoomPending) {
        return;
    }

    returnRoomPending = true;
    replayButton.disabled = true;
    returnRoomButton.disabled = true;
    setReplayStatus("Returning both players to room...", "matched");
    socketHandle.send({ type: SocketCommand.RETURN_TO_ROOM });
});

window.addEventListener("beforeunload", cleanupAndExit);

connectRoom();
centerAimCursor();
animationFrameId = window.requestAnimationFrame(animationStep);
watchdogIntervalId = window.setInterval(() => {
    if (document.visibilityState !== "visible") {
        return;
    }
    if (!latestSnapshot) {
        return;
    }

    const now = performance.now();
    if ((now - lastSnapshotReceivedAt) > SNAPSHOT_STALE_MS) {
        maybeReconnect("Snapshot stream timeout");
    }
}, 1000);
