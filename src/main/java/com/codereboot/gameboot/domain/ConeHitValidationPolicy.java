package com.codereboot.gameboot.domain;

final class ConeHitValidationPolicy {

    boolean canHit(
            double shooterX,
            double shooterY,
            double shooterAimDegrees,
            double targetX,
            double targetY,
            double fireRange,
            double playerRadius,
            double coneHalfAngleDegrees
    ) {
        double deltaX = targetX - shooterX;
        double deltaY = targetY - shooterY;
        double distance = Math.hypot(deltaX, deltaY);

        if (distance > fireRange + playerRadius) {
            return false;
        }

        if (distance <= playerRadius) {
            return true;
        }

        // Treat the shot as a forward ray with finite range and radius-aware target body intersection.
        double aimRadians = Math.toRadians(shooterAimDegrees);
        double aimX = Math.cos(aimRadians);
        double aimY = Math.sin(aimRadians);

        double projection = (deltaX * aimX) + (deltaY * aimY);
        if (projection < 0.0 || projection > fireRange + playerRadius) {
            return false;
        }

        double perpendicularX = deltaX - (projection * aimX);
        double perpendicularY = deltaY - (projection * aimY);
        double perpendicularDistanceSquared = (perpendicularX * perpendicularX) + (perpendicularY * perpendicularY);
        return perpendicularDistanceSquared <= (playerRadius * playerRadius);
    }
}
