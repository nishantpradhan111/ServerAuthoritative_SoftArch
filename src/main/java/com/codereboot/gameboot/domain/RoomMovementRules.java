package com.codereboot.gameboot.domain;

import java.util.LinkedHashMap;

final class RoomMovementRules {

    private final int boardWidth;
    private final int boardHeight;
    private final double playerRadius;

    RoomMovementRules(int boardWidth, int boardHeight, double playerRadius) {
        this.boardWidth = boardWidth;
        this.boardHeight = boardHeight;
        this.playerRadius = playerRadius;
    }

    boolean tryMove(LinkedHashMap<String, Player> players, Player movingPlayer, Direction direction) {
        double nextX = clamp(movingPlayer.positionX() + direction.deltaX(), 0.0, boardWidth - 1.0);
        double nextY = clamp(movingPlayer.positionY() + direction.deltaY(), 0.0, boardHeight - 1.0);
        if (!isCellFree(players, nextX, nextY, movingPlayer.token())) {
            movingPlayer.face(direction);
            return false;
        }

        movingPlayer.moveTo(nextX, nextY);
        movingPlayer.face(direction);
        return true;
    }

    boolean tick(LinkedHashMap<String, Player> players, double deltaSeconds) {
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

        changed |= resolveOverlap(players, previousX, previousY);
        return changed;
    }

    private boolean isCellFree(LinkedHashMap<String, Player> players, double x, double y, String movingToken) {
        return players.values().stream()
                .filter(player -> !player.token().equals(movingToken))
                .noneMatch(player -> player.x() == Math.round(x) && player.y() == Math.round(y));
    }

    private boolean clampPlayer(Player player) {
        double clampedX = clamp(player.positionX(), playerRadius, boardWidth - 1.0 - playerRadius);
        double clampedY = clamp(player.positionY(), playerRadius, boardHeight - 1.0 - playerRadius);
        boolean changed = clampedX != player.positionX() || clampedY != player.positionY();
        if (changed) {
            player.moveTo(clampedX, clampedY);
        }
        return changed;
    }

    private boolean resolveOverlap(LinkedHashMap<String, Player> players, double[] previousX, double[] previousY) {
        if (players.size() < 2) {
            return false;
        }

        Player[] pair = players.values().toArray(new Player[0]);
        Player first = pair[0];
        Player second = pair[1];
        double deltaX = first.positionX() - second.positionX();
        double deltaY = first.positionY() - second.positionY();
        double minimumSeparation = playerRadius * 2.0;
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
}