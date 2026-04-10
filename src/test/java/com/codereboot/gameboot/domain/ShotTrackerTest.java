package com.codereboot.gameboot.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShotTrackerTest {

    @Test
    void registersAndFindsActiveShot() {
        ShotTracker tracker = new ShotTracker(2_000L);

        tracker.register(1L, "attacker", "target", 1_000L, 10L);
        ShotTracker.TrackedShot shot = tracker.findActive(1L);

        assertNotNull(shot);
        assertTrue(shot.firedTick() == 10L);
    }

    @Test
    void consumedShotIsNotActive() {
        ShotTracker tracker = new ShotTracker(2_000L);

        tracker.register(1L, "attacker", "target", 1_000L, 10L);
        tracker.markConsumed(1L);

        assertNull(tracker.findActive(1L));
    }

    @Test
    void expiryAndPruneBehaviorAreConsistent() {
        ShotTracker tracker = new ShotTracker(2_000L);

        tracker.register(1L, "attacker", "target", 1_000L, 10L);
        ShotTracker.TrackedShot shot = tracker.findActive(1L);
        assertNotNull(shot);
        assertFalse(tracker.isExpired(shot, 2_999L));
        assertTrue(tracker.isExpired(shot, 3_001L));

        tracker.pruneExpired(3_001L);
        assertNull(tracker.findActive(1L));
    }
}
