package com.codereboot.gameboot.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

public class Room {

    public static final int BOARD_WIDTH = 7;
    public static final int BOARD_HEIGHT = 5;
    public static final int STARTING_HEALTH = 3;
    public static final int FIRE_RANGE = 4;

    private final String code;
    private final LinkedHashMap<String, Player> players = new LinkedHashMap<>();
    private RoomPhase phase = RoomPhase.LOBBY;
    private String winnerToken;
    private String lastEvent = "Create or join a room to begin";

    public Room(String code) {
        this.code = code.toUpperCase();
    }

    public String code() {
        return code;
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
            player.place(1, BOARD_HEIGHT / 2, Direction.RIGHT);
        } else {
            player.place(BOARD_WIDTH - 2, BOARD_HEIGHT / 2, Direction.LEFT);
        }
        players.put(token, player);
        lastEvent = name + " entered the arena";
        return token;
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
        int nextX = clamp(player.x() + direction.deltaX(), 0, BOARD_WIDTH - 1);
        int nextY = clamp(player.y() + direction.deltaY(), 0, BOARD_HEIGHT - 1);
        if (!isCellFree(nextX, nextY, token)) {
            player.face(direction);
            lastEvent = player.name() + " tried to move but the path was blocked";
            return;
        }
        player.moveTo(nextX, nextY);
        player.face(direction);
        lastEvent = player.name() + " moved " + direction.name().toLowerCase();
    }

    public synchronized void fire(String token) {
        ensureActive();
        Player shooter = requirePlayer(token);
        Player target = players.values().stream()
                .filter(player -> !player.token().equals(token))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Second player is missing"));

        if (!canHit(shooter, target)) {
            lastEvent = shooter.name() + " fired a pulse and missed";
            return;
        }

        target.damage(1);
        if (target.defeated()) {
            winnerToken = shooter.token();
            phase = RoomPhase.COMPLETE;
            lastEvent = shooter.name() + " landed the final pulse";
            return;
        }

        lastEvent = shooter.name() + " hit " + target.name();
    }

    public synchronized RoomSnapshot snapshot() {
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
                    player.facing(),
                    player.health(),
                    player.ready(),
                    host
            ));
        }
        return new RoomSnapshot(code, phase, BOARD_WIDTH, BOARD_HEIGHT, playerSnapshots, winnerToken, lastEvent, canStart());
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
        lastEvent = "Match started";

        int index = 0;
        for (Player player : players.values()) {
            if (index == 0) {
                player.resetForMatch(1, BOARD_HEIGHT / 2, Direction.RIGHT);
            } else {
                player.resetForMatch(BOARD_WIDTH - 2, BOARD_HEIGHT / 2, Direction.LEFT);
            }
            index++;
        }
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

    private boolean isCellFree(int x, int y, String movingToken) {
        return players.values().stream()
                .filter(player -> !player.token().equals(movingToken))
                .noneMatch(player -> player.x() == x && player.y() == y);
    }

    private boolean canHit(Player shooter, Player target) {
        if (shooter.x() == target.x()) {
            int deltaY = target.y() - shooter.y();
            if (deltaY > 0 && shooter.facing() == Direction.DOWN) {
                return deltaY <= FIRE_RANGE;
            }
            if (deltaY < 0 && shooter.facing() == Direction.UP) {
                return -deltaY <= FIRE_RANGE;
            }
            return false;
        }

        if (shooter.y() == target.y()) {
            int deltaX = target.x() - shooter.x();
            if (deltaX > 0 && shooter.facing() == Direction.RIGHT) {
                return deltaX <= FIRE_RANGE;
            }
            if (deltaX < 0 && shooter.facing() == Direction.LEFT) {
                return -deltaX <= FIRE_RANGE;
            }
        }
        return false;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}