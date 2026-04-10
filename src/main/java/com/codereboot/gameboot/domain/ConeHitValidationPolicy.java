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

        if (distance > fireRange) {
            return false;
        }

        if (distance <= playerRadius * 2.0) {
            return true;
        }

        double directionX = deltaX / distance;
        double directionY = deltaY / distance;
        double aimRadians = Math.toRadians(shooterAimDegrees);
        double aimX = Math.cos(aimRadians);
        double aimY = Math.sin(aimRadians);

        double alignment = (aimX * directionX) + (aimY * directionY);
        double minimumAlignment = Math.cos(Math.toRadians(coneHalfAngleDegrees));
        return alignment >= minimumAlignment;
    }
}
