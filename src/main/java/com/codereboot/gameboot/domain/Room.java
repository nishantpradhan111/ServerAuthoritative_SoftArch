package com.codereboot.gameboot.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Room {

    private static final Logger LOG = LoggerFactory.getLogger(Room.class);
    private static final boolean HIT_CLAIM_DIAGNOSTICS_ENABLED =
            Boolean.parseBoolean(System.getProperty("codereboot.diagnostics.hitClaims", "false"));

    public static final int BOARD_WIDTH = 15;
    public static final int BOARD_HEIGHT = 10;
    public static final int STARTING_HEALTH = 5;
    public static final int STARTING_AMMO = 30;
    public static final int FIRE_RANGE = 100;
    public static final double FIRE_CONE_HALF_ANGLE_DEGREES = 45;
    public static final double PLAYER_SPEED = 4.0;
    public static final double PLAYER_RADIUS = 0.35;
    public static final double SIMULATION_STEP_SECONDS = 1.0 / 30.0;
    private static final int MAX_TICK_HISTORY = 180;
    private static final long MAX_SHOT_AGE_MS = 2_000L;
    private static final long REPLAY_REQUEST_TIMEOUT_MS = 10_000L;

    private final String code;
    private final LongSupplier nowMs;
    private final ConeHitValidationPolicy hitValidation;
    private final LinkedHashMap<String, Player> players = new LinkedHashMap<>();
    private RoomPhase phase = RoomPhase.LOBBY;
    private String winnerToken;
    private String lastEvent = "Create or join a room to begin";
    private long simulationTick;
    private long nextShotId = 1L;
    private final TickHistoryTracker tickHistory;
    private final ShotTracker shots;
    private final ReplayRequestTracker replayRequests;

    public record ReplayParticipant(String token, String name) {
    }

    public Room(String code) {
        this(code, System::currentTimeMillis, new ConeHitValidationPolicy());
    }

    Room(String code, LongSupplier nowMs) {
        this(code, nowMs, new ConeHitValidationPolicy());
    }

    Room(String code, LongSupplier nowMs, ConeHitValidationPolicy hitValidation) {
        this.code = code.toUpperCase();
        this.nowMs = Objects.requireNonNull(nowMs, "nowMs");
        this.hitValidation = Objects.requireNonNull(hitValidation, "hitValidation");
        this.tickHistory = new TickHistoryTracker(MAX_TICK_HISTORY);
        this.shots = new ShotTracker(MAX_SHOT_AGE_MS);
        this.replayRequests = new ReplayRequestTracker(REPLAY_REQUEST_TIMEOUT_MS);
    }

    public String code() {
        return code;
    }

    public synchronized RoomPhase phase() {
        return phase;
    }

    public synchronized String addPlayer(String name) {
        if (phase != RoomPhase.LOBBY) {
            throw new IllegalStateException("The match already started");
        }
        if (players.size() >= 2) {
            throw new IllegalStateException("Room is full");
        }

        String token = UUID.randomUUID().toString();
        Player player = new Player(token, name);
        if (players.isEmpty()) {
            player.place(1.0, BOARD_HEIGHT / 2.0, Direction.RIGHT);
        } else {
            player.place(BOARD_WIDTH - 2.0, BOARD_HEIGHT / 2.0, Direction.LEFT);
        }
        players.put(token, player);
        lastEvent = name + " entered the arena";
        return token;
    }

    public synchronized void removePlayer(String token) {
        if (phase == RoomPhase.ACTIVE) {
            throw new IllegalStateException("Cannot leave room during an active match");
        }

        Player leavingPlayer = requirePlayer(token);
        players.remove(token);
        replayRequests.remove(token);

        if (players.isEmpty()) {
            winnerToken = null;
            phase = RoomPhase.LOBBY;
            simulationTick = 0L;
            tickHistory.clear();
            shots.clear();
            replayRequests.clear();
            lastEvent = leavingPlayer.name() + " left the room";
            return;
        }

        lastEvent = leavingPlayer.name() + " left the room";
    }

    public synchronized boolean empty() {
        return players.isEmpty();
    }

    public synchronized void setReady(String token, boolean ready) {
        Player player = requirePlayer(token);
        if (phase != RoomPhase.LOBBY) {
            throw new IllegalStateException("Readiness is locked once the match starts");
        }
        player.ready(ready);
        lastEvent = player.name() + (ready ? " is ready" : " is waiting");
        if (canStart()) {
            startMatch();
        }
    }

    public synchronized void move(String token, Direction direction) {
        ensureActive();
        Player player = requirePlayer(token);
        double nextX = clamp(player.positionX() + direction.deltaX(), 0.0, BOARD_WIDTH - 1.0);
        double nextY = clamp(player.positionY() + direction.deltaY(), 0.0, BOARD_HEIGHT - 1.0);
        if (!isCellFree(nextX, nextY, token)) {
            player.face(direction);
            lastEvent = player.name() + " tried to move but the path was blocked";
            return;
        }
        player.moveTo(nextX, nextY);
        player.face(direction);
        lastEvent = player.name() + " moved " + direction.name().toLowerCase();
    }

    public synchronized void applyInput(String token, GameInputFrame input) {
        ensureActive();
        Player player = requirePlayer(token);
        if (input.sequence() <= player.lastInputSequence()) {
            return;
        }

        player.setLastInputSequence(input.sequence());
        double moveX = clamp(input.moveX(), -1.0, 1.0);
        double moveY = clamp(input.moveY(), -1.0, 1.0);
        player.setVelocity(moveX * PLAYER_SPEED, moveY * PLAYER_SPEED);
        if (input.aimDegrees() != null) {
            player.face(input.aimDegrees());
        } else if (moveX != 0.0 || moveY != 0.0) {
            player.face(Direction.fromVector(moveX, moveY));
        }

        if (input.firing()) {
            fire(token);
        }
    }

    public synchronized void fire(String token) {
        ensureActive();
        Player shooter = requirePlayer(token);
        if (shooter.ammo() <= 0) {
            lastEvent = shooter.name() + " tried to fire but has no ammo";
            return;
        }

        recordTickState();
        shooter.consumeAmmo(1);
        Player target = players.values().stream()
                .filter(player -> !player.token().equals(token))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Second player is missing"));

        long shotId = nextShotId++;
        shooter.setLastShotId(shotId);
        shots.register(shotId, shooter.token(), target.token(), nowMs(), simulationTick);
        shots.pruneExpired(nowMs());
        lastEvent = shooter.name() + " fired a pulse";
    }

    public synchronized void claimHit(String reporterToken, long shotId, long snapshotTick) {
        ensureActive();
        Player reporter = requirePlayer(reporterToken);
        ShotTracker.TrackedShot shot = shots.findActive(shotId);
        if (shot == null) {
            logHitClaimRejection("SHOT_NOT_FOUND_OR_CONSUMED", reporter, shotId, snapshotTick, null);
            return;
        }

        if (shots.isExpired(shot, nowMs())) {
            lastEvent = reporter.name() + " reported an expired hit";
            logHitClaimRejection("SHOT_EXPIRED", reporter, shotId, snapshotTick, shot);
            shots.discard(shotId);
            return;
        }

        if (snapshotTick < shot.firedTick()) {
            lastEvent = reporter.name() + " reported an invalid hit";
            logHitClaimRejection("SNAPSHOT_BEFORE_FIRE", reporter, shotId, snapshotTick, shot);
            return;
        }

        Player attacker = requirePlayer(shot.attackerToken());
        Player target = requirePlayer(shot.targetToken());
        if (!reporter.token().equals(attacker.token()) && !reporter.token().equals(target.token())) {
            throw new IllegalArgumentException("Reporter must be attacker or target");
        }

        Map<String, PlayerTickState> tickState = tickHistory.snapshotAt(snapshotTick);
        if (tickState == null) {
            lastEvent = reporter.name() + " reported a stale hit";
            logHitClaimRejection("TICK_NOT_FOUND", reporter, shotId, snapshotTick, shot);
            return;
        }

        PlayerTickState attackerAtTick = tickState.get(attacker.token());
        PlayerTickState targetAtTick = tickState.get(target.token());
        if (attackerAtTick == null || targetAtTick == null) {
            lastEvent = reporter.name() + " reported an invalid hit";
            logHitClaimRejection("PLAYER_STATE_MISSING", reporter, shotId, snapshotTick, shot);
            return;
        }

        if (!canHit(attackerAtTick, targetAtTick)) {
            lastEvent = reporter.name() + " reported an invalid hit";
            logHitClaimRejection("CONE_VALIDATION_FAILED", reporter, shotId, snapshotTick, shot);
            return;
        }

        shots.markConsumed(shotId);
        target.damage(1);
        if (target.defeated()) {
            winnerToken = attacker.token();
            phase = RoomPhase.COMPLETE;
            lastEvent = attacker.name() + " landed the final pulse";
            return;
        }

        lastEvent = attacker.name() + " hit " + target.name();
    }

    public synchronized boolean tick(double deltaSeconds) {
        if (phase != RoomPhase.ACTIVE || deltaSeconds <= 0.0) {
            return false;
        }

        boolean changed = false;
        double[] previousX = new double[players.size()];
        double[] previousY = new double[players.size()];
        int index = 0;
        for (Player player : players.values()) {
            previousX[index] = player.positionX();
            previousY[index] = player.positionY();
            index++;
        }

        for (Player player : players.values()) {
            changed |= player.advance(deltaSeconds);
            changed |= clampPlayer(player);
        }

        changed |= resolveOverlap(previousX, previousY);
        simulationTick++;
        recordTickState();
        shots.pruneExpired(nowMs());
        return changed;
    }

    public synchronized List<ReplayParticipant> requestReplay(String token) {
        Player requester = requirePlayer(token);
        if (phase != RoomPhase.COMPLETE) {
            throw new IllegalStateException("Replay can only be requested after match completion");
        }

        long now = System.currentTimeMillis();
        cleanupExpiredReplayRequests(now);
        replayRequests.record(token, now);

        if (isReplayReady()) {
            List<ReplayParticipant> participants = players.values().stream()
                    .map(player -> new ReplayParticipant(player.token(), player.name()))
                    .toList();
            replayRequests.clear();
            lastEvent = "Replay matched. Entering new room...";
            return participants;
        }

        lastEvent = requester.name() + " requested replay. Waiting for opponent...";
        return List.of();
    }

    public synchronized RoomSnapshot snapshot() {
        if (phase == RoomPhase.COMPLETE) {
            cleanupExpiredReplayRequests(System.currentTimeMillis());
        }

        List<PlayerSnapshot> playerSnapshots = new ArrayList<>();
        String hostToken = players.keySet().stream().findFirst().orElse(null);
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            Player player = entry.getValue();
            boolean host = hostToken != null && hostToken.equals(player.token());
            playerSnapshots.add(new PlayerSnapshot(
                    player.token(),
                    player.name(),
                    player.x(),
                    player.y(),
                    player.positionX(),
                    player.positionY(),
                    player.velocityX(),
                    player.velocityY(),
                    player.facing(),
                    player.aimDegrees(),
                    player.health(),
                    player.ready(),
                    host,
                    player.ammo(),
                    player.lastInputSequence(),
                    simulationTick,
                    player.lastShotId()
            ));
        }
                List<String> replayPendingTokens = replayRequests.pendingTokens();
                return new RoomSnapshot(
                    code,
                    phase,
                    BOARD_WIDTH,
                    BOARD_HEIGHT,
                    simulationTick,
                    playerSnapshots,
                    winnerToken,
                    lastEvent,
                    canStart(),
                    replayPendingTokens
                );
    }

    public synchronized Player requirePlayer(String token) {
        Player player = players.get(token);
        if (player == null) {
            throw new NoSuchElementException("Player is not part of this room");
        }
        return player;
    }

    private void startMatch() {
        phase = RoomPhase.ACTIVE;
        winnerToken = null;
        simulationTick = 0L;
        nextShotId = 1L;
        lastEvent = "Match started";
        shots.clear();
        tickHistory.clear();
        replayRequests.clear();

        int index = 0;
        for (Player player : players.values()) {
            if (index == 0) {
                player.resetForMatch(1, BOARD_HEIGHT / 2, Direction.RIGHT);
            } else {
                player.resetForMatch(BOARD_WIDTH - 2, BOARD_HEIGHT / 2, Direction.LEFT);
            }
            index++;
        }

        recordTickState();
    }

    private boolean canStart() {
        return phase == RoomPhase.LOBBY
                && players.size() == 2
                && players.values().stream().allMatch(Player::ready);
    }

    private void ensureActive() {
        if (phase != RoomPhase.ACTIVE) {
            throw new IllegalStateException("The match is not active");
        }
    }

    private boolean isCellFree(double x, double y, String movingToken) {
        return players.values().stream()
                .filter(player -> !player.token().equals(movingToken))
                .noneMatch(player -> player.x() == Math.round(x) && player.y() == Math.round(y));
    }

    private boolean canHit(Player shooter, Player target) {
        return canHit(
                new PlayerTickState(shooter.positionX(), shooter.positionY(), shooter.aimDegrees()),
                new PlayerTickState(target.positionX(), target.positionY(), target.aimDegrees())
        );
    }

    private boolean canHit(PlayerTickState shooter, PlayerTickState target) {
        return hitValidation.canHit(
                shooter.positionX(),
                shooter.positionY(),
                shooter.aimDegrees(),
                target.positionX(),
                target.positionY(),
                FIRE_RANGE,
                PLAYER_RADIUS,
                FIRE_CONE_HALF_ANGLE_DEGREES
        );
    }

    private void recordTickState() {
        if (phase != RoomPhase.ACTIVE) {
            return;
        }
        tickHistory.record(simulationTick, players.values());
    }

    private void cleanupExpiredReplayRequests(long nowMs) {
        if (replayRequests.cleanupExpired(nowMs)) {
            lastEvent = "Replay request expired. Press Replay again within 10 seconds.";
        }
    }

    private boolean isReplayReady() {
        return players.size() == 2 && replayRequests.isReady(players.keySet());
    }

    private boolean clampPlayer(Player player) {
        double clampedX = clamp(player.positionX(), PLAYER_RADIUS, BOARD_WIDTH - 1.0 - PLAYER_RADIUS);
        double clampedY = clamp(player.positionY(), PLAYER_RADIUS, BOARD_HEIGHT - 1.0 - PLAYER_RADIUS);
        boolean changed = clampedX != player.positionX() || clampedY != player.positionY();
        if (changed) {
            player.moveTo(clampedX, clampedY);
        }
        return changed;
    }

    private boolean resolveOverlap(double[] previousX, double[] previousY) {
        if (players.size() < 2) {
            return false;
        }

        Player[] pair = players.values().toArray(new Player[0]);
        Player first = pair[0];
        Player second = pair[1];
        double deltaX = first.positionX() - second.positionX();
        double deltaY = first.positionY() - second.positionY();
        double minimumSeparation = PLAYER_RADIUS * 2.0;
        if ((deltaX * deltaX) + (deltaY * deltaY) >= minimumSeparation * minimumSeparation) {
            return false;
        }

        first.moveTo(previousX[0], previousY[0]);
        second.moveTo(previousX[1], previousY[1]);
        first.stop();
        second.stop();
        return true;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private long nowMs() {
        return nowMs.getAsLong();
    }

    private void logHitClaimRejection(
            String reason,
            Player reporter,
            long shotId,
            long snapshotTick,
            ShotTracker.TrackedShot shot
    ) {
        if (!HIT_CLAIM_DIAGNOSTICS_ENABLED || !LOG.isInfoEnabled()) {
            return;
        }

        if (shot == null) {
            LOG.info(
                    "Hit claim rejected: reason={} reporterToken={} reporterName={} shotId={} snapshotTick={} simulationTick={}",
                    reason,
                    reporter.token(),
                    reporter.name(),
                    shotId,
                    snapshotTick,
                    simulationTick
            );
            return;
        }

        LOG.info(
                "Hit claim rejected: reason={} reporterToken={} reporterName={} shotId={} attackerToken={} targetToken={} firedTick={} snapshotTick={} simulationTick={}",
                reason,
                reporter.token(),
                reporter.name(),
                shotId,
                shot.attackerToken(),
                shot.targetToken(),
                shot.firedTick(),
                snapshotTick,
                simulationTick
        );
    }
}