package com.codereboot.gameboot.domain;

import java.util.Locale;

public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    private final int deltaX;
    private final int deltaY;

    Direction(int deltaX, int deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    public int deltaX() {
        return deltaX;
    }

    public int deltaY() {
        return deltaY;
    }

    public static Direction from(String value) {
        return Direction.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}