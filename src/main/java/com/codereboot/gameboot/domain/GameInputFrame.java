package com.codereboot.gameboot.domain;

/**
 * Immutable input command for the first FPS migration step.
 *
 * The current browser client still sends discrete move/fire commands,
 * but this frame gives the transport and simulation layers a stable shape
 * for continuous movement, aiming, and trigger state.
 */
public record GameInputFrame(
        long sequence,
        double moveX,
        double moveY,
        Double aimDegrees,
        boolean firing
) {
}