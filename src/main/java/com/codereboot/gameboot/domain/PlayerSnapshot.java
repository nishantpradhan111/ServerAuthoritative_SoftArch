package com.codereboot.gameboot.domain;

public record PlayerSnapshot(String token, String name, int x, int y, double positionX, double positionY,
                             double velocityX, double velocityY, Direction facing, double aimDegrees, int health,
                             boolean ready, boolean host, int ammo, long lastProcessedInputSequence,
                             long snapshotTick) {
}