export function getReplayPendingTokens(snapshot) {
    return Array.isArray(snapshot?.replayPendingTokens) ? snapshot.replayPendingTokens : [];
}

export function canRequestReplay(snapshot, selfToken) {
    if (!snapshot || snapshot.phase !== "COMPLETE") {
        return false;
    }

    const pendingTokens = getReplayPendingTokens(snapshot);
    return !pendingTokens.includes(selfToken);
}

export function buildReplayUiState(snapshot, selfToken, replayAckDeadlineMs, nowMs) {
    const pendingTokens = getReplayPendingTokens(snapshot);
    const selfReplayPending = pendingTokens.includes(selfToken);
    const opponentReplayPending = pendingTokens.some((token) => token !== selfToken);
    const awaitingReplayAck = replayAckDeadlineMs > nowMs;
    const normalizedEvent = String(snapshot?.lastEvent ?? "").toLowerCase();

    if (selfReplayPending) {
        return {
            selfReplayPending,
            opponentReplayPending,
            awaitingReplayAck,
            statusText: "Replay pending... waiting for opponent",
            statusTone: "pending"
        };
    }

    if (opponentReplayPending) {
        return {
            selfReplayPending,
            opponentReplayPending,
            awaitingReplayAck,
            statusText: "Opponent requested replay. Press Replay to join.",
            statusTone: "pending"
        };
    }

    if (awaitingReplayAck) {
        return {
            selfReplayPending,
            opponentReplayPending,
            awaitingReplayAck,
            statusText: "Sending replay request...",
            statusTone: "pending"
        };
    }

    if (normalizedEvent.includes("expired")) {
        return {
            selfReplayPending,
            opponentReplayPending,
            awaitingReplayAck,
            statusText: "Replay expired. Press Replay again.",
            statusTone: "expired"
        };
    }

    return {
        selfReplayPending,
        opponentReplayPending,
        awaitingReplayAck,
        statusText: "",
        statusTone: "idle"
    };
}
