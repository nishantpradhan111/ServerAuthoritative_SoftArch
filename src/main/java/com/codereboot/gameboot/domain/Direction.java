package com.codereboot.gameboot.domain;

import java.util.Locale;

public enum Direction {
    UP(0, -1, 270.0),
    DOWN(0, 1, 90.0),
    LEFT(-1, 0, 180.0),
    RIGHT(1, 0, 0.0);

    private final int deltaX;
    private final int deltaY;
    private final double aimDegrees;

    Direction(int deltaX, int deltaY, double aimDegrees) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.aimDegrees = aimDegrees;
    }

    public int deltaX() {
        return deltaX;
    }

    public int deltaY() {
        return deltaY;
    }

    public double aimDegrees() {
        return aimDegrees;
    }

    public static Direction from(String value) {
        return Direction.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public static Direction fromVector(double x, double y) {
        if (Math.abs(x) >= Math.abs(y)) {
            return x < 0 ? LEFT : RIGHT;
        }
        return y < 0 ? UP : DOWN;
    }

    public static Direction fromDegrees(double degrees) {
        double normalized = ((degrees % 360.0) + 360.0) % 360.0;
        if (normalized < 45.0 || normalized >= 315.0) {
            return RIGHT;
        }
        if (normalized < 135.0) {
            return DOWN;
        }
        if (normalized < 225.0) {
            return LEFT;
        }
        return UP;
    }
}