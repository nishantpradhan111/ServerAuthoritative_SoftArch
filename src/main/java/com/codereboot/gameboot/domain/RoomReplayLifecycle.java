package com.codereboot.gameboot.domain;

import java.util.List;
import java.util.function.LongSupplier;

final class RoomReplayLifecycle {

    static final String REPLAY_MATCHED_EVENT = "Replay matched. Entering new room...";
    static final String REPLAY_EXPIRED_EVENT = "Replay request expired. Press Replay again within 10 seconds.";

    private final ReplayRequestTracker replayRequests;
    private final LongSupplier nowMs;

    RoomReplayLifecycle(ReplayRequestTracker replayRequests, LongSupplier nowMs) {
        this.replayRequests = replayRequests;
        this.nowMs = nowMs;
    }

    void clear() {
        replayRequests.clear();
    }

    void remove(String token) {
        replayRequests.remove(token);
    }

    String cleanupExpiredForSnapshot(RoomPhase phase) {
        if (phase != RoomPhase.COMPLETE) {
            return null;
        }

        if (replayRequests.cleanupExpired(nowMs.getAsLong())) {
            return REPLAY_EXPIRED_EVENT;
        }
        return null;
    }

    ReplayOutcome requestReplay(Player requester, RoomPhase phase, Iterable<Player> players, Iterable<String> playerTokens) {
        if (phase != RoomPhase.COMPLETE) {
            throw new IllegalStateException("Replay can only be requested after match completion");
        }

        long now = nowMs.getAsLong();
        replayRequests.cleanupExpired(now);
        replayRequests.record(requester.token(), now);

        List<String> tokens = java.util.stream.StreamSupport.stream(playerTokens.spliterator(), false).toList();
        if (isReplayReady(tokens)) {
            List<Room.ReplayParticipant> participants = toReplayParticipants(players);
            replayRequests.clear();
            return new ReplayOutcome(participants, REPLAY_MATCHED_EVENT);
        }

        return new ReplayOutcome(List.of(), requester.name() + " requested replay. Waiting for opponent...");
    }

    List<String> pendingTokens() {
        return replayRequests.pendingTokens();
    }

    private boolean isReplayReady(List<String> playerTokens) {
        return playerTokens.size() == 2 && replayRequests.isReady(playerTokens);
    }

    private List<Room.ReplayParticipant> toReplayParticipants(Iterable<Player> players) {
        return java.util.stream.StreamSupport.stream(players.spliterator(), false)
                .map(player -> new Room.ReplayParticipant(player.token(), player.name()))
                .toList();
    }

    record ReplayOutcome(List<Room.ReplayParticipant> participants, String event) {
    }
}