package com.codereboot.gameboot.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.LongSupplier;

final class RoomCombatLifecycle {

    interface HitClaimLogger {
        void log(String reason, Player reporter, long shotId, long snapshotTick, ShotTracker.TrackedShot shot);
    }

    record FireOutcome(long nextShotId, String event) {
    }

    record HitOutcome(boolean complete, String winnerToken, String event) {
    }

    private final ShotTracker shots;
    private final TickHistoryTracker tickHistory;
    private final ConeHitValidationPolicy hitValidation;
    private final LongSupplier nowMs;
    private final int fireRange;
    private final double playerRadius;
    private final double fireConeHalfAngleDegrees;

    RoomCombatLifecycle(
            ShotTracker shots,
            TickHistoryTracker tickHistory,
            ConeHitValidationPolicy hitValidation,
            LongSupplier nowMs,
            int fireRange,
            double playerRadius,
            double fireConeHalfAngleDegrees
    ) {
        this.shots = shots;
        this.tickHistory = tickHistory;
        this.hitValidation = hitValidation;
        this.nowMs = nowMs;
        this.fireRange = fireRange;
        this.playerRadius = playerRadius;
        this.fireConeHalfAngleDegrees = fireConeHalfAngleDegrees;
    }

    FireOutcome fire(LinkedHashMap<String, Player> players, String token, long simulationTick, long nextShotId) {
        Player shooter = requirePlayer(players, token);
        if (shooter.ammo() <= 0) {
            return new FireOutcome(nextShotId, shooter.name() + " tried to fire but has no ammo");
        }

        shooter.consumeAmmo(1);
        Player target = players.values().stream()
                .filter(player -> !player.token().equals(token))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Second player is missing"));

        long shotId = nextShotId;
        shooter.setLastShotId(shotId);
        shots.register(shotId, shooter.token(), target.token(), nowMs(), simulationTick);
        pruneExpiredShots();
        return new FireOutcome(shotId + 1L, shooter.name() + " fired a pulse");
    }

    HitOutcome claimHit(
            LinkedHashMap<String, Player> players,
            String reporterToken,
            long shotId,
            long snapshotTick,
            long simulationTick,
            HitClaimLogger logger
    ) {
        Player reporter = requirePlayer(players, reporterToken);
        ShotTracker.TrackedShot shot = shots.findActive(shotId);
        if (shot == null) {
            logger.log("SHOT_NOT_FOUND_OR_CONSUMED", reporter, shotId, snapshotTick, null);
            return new HitOutcome(false, null, null);
        }

        if (shots.isExpired(shot, nowMs())) {
            logger.log("SHOT_EXPIRED", reporter, shotId, snapshotTick, shot);
            shots.discard(shotId);
            return new HitOutcome(false, null, reporter.name() + " reported an expired hit");
        }

        if (snapshotTick < shot.firedTick()) {
            logger.log("SNAPSHOT_BEFORE_FIRE", reporter, shotId, snapshotTick, shot);
            return new HitOutcome(false, null, reporter.name() + " reported an invalid hit");
        }

        Player attacker = requirePlayer(players, shot.attackerToken());
        Player target = requirePlayer(players, shot.targetToken());
        if (!reporter.token().equals(attacker.token()) && !reporter.token().equals(target.token())) {
            throw new IllegalArgumentException("Reporter must be attacker or target");
        }

        Map<String, PlayerTickState> fireTickState = tickHistory.snapshotAt(shot.firedTick());
        if (fireTickState == null) {
            logger.log("FIRE_TICK_NOT_FOUND", reporter, shotId, snapshotTick, shot);
            return new HitOutcome(false, null, reporter.name() + " reported a stale hit");
        }

        Map<String, PlayerTickState> claimedTickState = tickHistory.snapshotAt(snapshotTick);
        if (claimedTickState == null) {
            logger.log("CLAIMED_TICK_NOT_FOUND", reporter, shotId, snapshotTick, shot);
            return new HitOutcome(false, null, reporter.name() + " reported a stale hit");
        }

        // Shooter direction is locked at fire-time; target position is evaluated at claimed impact tick.
        PlayerTickState attackerAtTick = fireTickState.get(attacker.token());
        PlayerTickState targetAtTick = claimedTickState.get(target.token());
        if (attackerAtTick == null || targetAtTick == null) {
            logger.log("PLAYER_STATE_MISSING", reporter, shotId, snapshotTick, shot);
            return new HitOutcome(false, null, reporter.name() + " reported an invalid hit");
        }

        if (!canHit(attackerAtTick, targetAtTick)) {
            logger.log("CONE_VALIDATION_FAILED", reporter, shotId, snapshotTick, shot);
            return new HitOutcome(false, null, reporter.name() + " reported an invalid hit");
        }

        shots.markConsumed(shotId);
        target.damage(1);
        if (target.defeated()) {
            return new HitOutcome(true, attacker.token(), attacker.name() + " landed the final pulse");
        }

        return new HitOutcome(false, null, attacker.name() + " hit " + target.name());
    }

    void clear() {
        shots.clear();
        tickHistory.clear();
    }

    void recordTickState(RoomPhase phase, long simulationTick, Iterable<Player> players) {
        if (phase != RoomPhase.ACTIVE) {
            return;
        }
        tickHistory.record(simulationTick, players);
    }

    void pruneExpiredShots() {
        shots.pruneExpired(nowMs());
    }

    private boolean canHit(PlayerTickState shooter, PlayerTickState target) {
        return hitValidation.canHit(
                shooter.positionX(),
                shooter.positionY(),
                shooter.aimDegrees(),
                target.positionX(),
                target.positionY(),
                fireRange,
                playerRadius,
                fireConeHalfAngleDegrees
        );
    }

    private Player requirePlayer(LinkedHashMap<String, Player> players, String token) {
        Player player = players.get(token);
        if (player == null) {
            throw new NoSuchElementException("Player is not part of this room");
        }
        return player;
    }

    private long nowMs() {
        return nowMs.getAsLong();
    }
}