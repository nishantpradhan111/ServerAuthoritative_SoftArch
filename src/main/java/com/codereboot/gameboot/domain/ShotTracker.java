package com.codereboot.gameboot.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

final class ShotTracker {

    record TrackedShot(long shotId, String attackerToken, String targetToken, long firedAtMs, long firedTick) {
    }

    private final long maxShotAgeMs;
    private final Map<Long, TrackedShot> pendingShots = new LinkedHashMap<>();
    private final Set<Long> consumedShotIds = new HashSet<>();

    ShotTracker(long maxShotAgeMs) {
        this.maxShotAgeMs = maxShotAgeMs;
    }

    void clear() {
        pendingShots.clear();
        consumedShotIds.clear();
    }

    void register(long shotId, String attackerToken, String targetToken, long firedAtMs, long firedTick) {
        pendingShots.put(shotId, new TrackedShot(shotId, attackerToken, targetToken, firedAtMs, firedTick));
    }

    TrackedShot findActive(long shotId) {
        TrackedShot shot = pendingShots.get(shotId);
        if (shot == null || consumedShotIds.contains(shotId)) {
            return null;
        }
        return shot;
    }

    boolean isExpired(TrackedShot shot, long nowMs) {
        return (nowMs - shot.firedAtMs()) > maxShotAgeMs;
    }

    void markConsumed(long shotId) {
        consumedShotIds.add(shotId);
        pendingShots.remove(shotId);
    }

    void discard(long shotId) {
        pendingShots.remove(shotId);
    }

    void pruneExpired(long nowMs) {
        pendingShots.entrySet().removeIf(entry -> (nowMs - entry.getValue().firedAtMs()) > maxShotAgeMs);
    }
}
