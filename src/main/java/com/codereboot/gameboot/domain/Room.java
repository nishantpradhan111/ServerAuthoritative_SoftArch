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
    private final LinkedHashMap<String, Player> players = new LinkedHashMap<>();
    private RoomPhase phase = RoomPhase.LOBBY;
    private String winnerToken;
    private String lastEvent = "Create or join a room to begin";
    private long simulationTick;
    private long nextShotId = 1L;
    private final ReplayRequestTracker replayRequests;
    private final RoomMovementRules movementRules;
    private final RoomReplayLifecycle replayLifecycle;
    private final RoomCombatLifecycle combatLifecycle;

    public record ReplayParticipant(String token, String name) {
    }

    public Room(String code) {
        this(code, System::currentTimeMillis, new ConeHitValidationPolicy());
    }

    public Room(String code, LongSupplier nowMs) {
        this(code, nowMs, new ConeHitValidationPolicy());
    }

    Room(String code, LongSupplier nowMs, ConeHitValidationPolicy hitValidation) {
        this.code = code.toUpperCase();
        LongSupplier clock = Objects.requireNonNull(nowMs, "nowMs");
        ConeHitValidationPolicy validation = Objects.requireNonNull(hitValidation, "hitValidation");
        TickHistoryTracker tickHistory = new TickHistoryTracker(MAX_TICK_HISTORY);
        ShotTracker shots = new ShotTracker(MAX_SHOT_AGE_MS);
        this.replayRequests = new ReplayRequestTracker(REPLAY_REQUEST_TIMEOUT_MS);
        this.movementRules = new RoomMovementRules(BOARD_WIDTH, BOARD_HEIGHT, PLAYER_RADIUS);
        this.replayLifecycle = new RoomReplayLifecycle(replayRequests, clock);
        this.combatLifecycle = new RoomCombatLifecycle(
            shots,
            tickHistory,
            validation,
            clock,
            FIRE_RANGE,
            PLAYER_RADIUS,
            FIRE_CONE_HALF_ANGLE_DEGREES
        );
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
        replayLifecycle.remove(token);

        if (players.isEmpty()) {
            winnerToken = null;
            phase = RoomPhase.LOBBY;
            simulationTick = 0L;
            combatLifecycle.clear();
            replayLifecycle.clear();
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
        if (!movementRules.tryMove(players, player, direction)) {
            lastEvent = player.name() + " tried to move but the path was blocked";
            return;
        }

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
        combatLifecycle.recordTickState(phase, simulationTick, players.values());
        RoomCombatLifecycle.FireOutcome outcome = combatLifecycle.fire(players, token, simulationTick, nextShotId);
        nextShotId = outcome.nextShotId();
        if (outcome.event() != null) {
            lastEvent = outcome.event();
        }
    }

    public synchronized void claimHit(String reporterToken, long shotId, long snapshotTick) {
        ensureActive();
        RoomCombatLifecycle.HitOutcome outcome = combatLifecycle.claimHit(
                players,
                reporterToken,
                shotId,
                snapshotTick,
                simulationTick,
                this::logHitClaimRejection
        );
        if (outcome.complete()) {
            winnerToken = outcome.winnerToken();
            phase = RoomPhase.COMPLETE;
        }
        if (outcome.event() != null) {
            lastEvent = outcome.event();
        }
    }

    public synchronized boolean tick(double deltaSeconds) {
        if (phase != RoomPhase.ACTIVE || deltaSeconds <= 0.0) {
            return false;
        }

        boolean changed = movementRules.tick(players, deltaSeconds);
        simulationTick++;
        combatLifecycle.recordTickState(phase, simulationTick, players.values());
        combatLifecycle.pruneExpiredShots();
        return changed;
    }

    public synchronized List<ReplayParticipant> requestReplay(String token) {
        Player requester = requirePlayer(token);
        RoomReplayLifecycle.ReplayOutcome outcome =
                replayLifecycle.requestReplay(requester, phase, players.values(), players.keySet());
        lastEvent = outcome.event();
        return outcome.participants();
    }

    public synchronized RoomSnapshot snapshot() {
        String replayEvent = replayLifecycle.cleanupExpiredForSnapshot(phase);
        if (replayEvent != null) {
            lastEvent = replayEvent;
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
            List<String> replayPendingTokens = replayLifecycle.pendingTokens();
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
        combatLifecycle.clear();
        replayLifecycle.clear();

        int index = 0;
        for (Player player : players.values()) {
            if (index == 0) {
                player.resetForMatch(1, BOARD_HEIGHT / 2, Direction.RIGHT);
            } else {
                player.resetForMatch(BOARD_WIDTH - 2, BOARD_HEIGHT / 2, Direction.LEFT);
            }
            index++;
        }

        combatLifecycle.recordTickState(phase, simulationTick, players.values());
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

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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