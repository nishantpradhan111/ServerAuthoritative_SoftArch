package com.codereboot.gameboot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayRequestTrackerTest {

    @Test
    void recordsPendingTokensInInsertionOrder() {
        ReplayRequestTracker tracker = new ReplayRequestTracker(10_000L);

        tracker.record("a", 100L);
        tracker.record("b", 110L);

        assertEquals(List.of("a", "b"), tracker.pendingTokens());
    }

    @Test
    void cleanupExpiredReturnsTrueOnlyWhenTrackerBecomesEmpty() {
        ReplayRequestTracker tracker = new ReplayRequestTracker(50L);
        tracker.record("a", 100L);
        tracker.record("b", 140L);

        boolean emptied = tracker.cleanupExpired(155L);
        assertFalse(emptied);
        assertEquals(List.of("b"), tracker.pendingTokens());

        emptied = tracker.cleanupExpired(250L);
        assertTrue(emptied);
        assertTrue(tracker.pendingTokens().isEmpty());
    }

    @Test
    void readinessRequiresAllRequestedTokens() {
        ReplayRequestTracker tracker = new ReplayRequestTracker(10_000L);
        tracker.record("a", 100L);

        assertFalse(tracker.isReady(List.of()));
        assertFalse(tracker.isReady(List.of("a", "b")));

        tracker.record("b", 120L);
        assertTrue(tracker.isReady(List.of("a", "b")));
    }
}
