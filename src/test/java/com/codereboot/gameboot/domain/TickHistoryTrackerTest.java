package com.codereboot.gameboot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TickHistoryTrackerTest {

    @Test
    void recordsPlayerStateForTick() {
        TickHistoryTracker tracker = new TickHistoryTracker(3);
        Player player = new Player("p1", "Ada");
        player.place(2.0, 3.0, Direction.RIGHT);
        player.face(90.0);

        tracker.record(10L, List.of(player));
        Map<String, PlayerTickState> tickState = tracker.snapshotAt(10L);

        assertNotNull(tickState);
        assertEquals(1, tickState.size());
        assertNotNull(tickState.get("p1"));
        assertEquals(2.0, tickState.get("p1").positionX());
        assertEquals(3.0, tickState.get("p1").positionY());
        assertEquals(90.0, tickState.get("p1").aimDegrees());
    }

    @Test
    void enforcesMaxHistoryWithFifoEviction() {
        TickHistoryTracker tracker = new TickHistoryTracker(2);
        Player player = new Player("p1", "Ada");
        player.place(1.0, 1.0, Direction.RIGHT);

        tracker.record(1L, List.of(player));
        tracker.record(2L, List.of(player));
        tracker.record(3L, List.of(player));

        assertNull(tracker.snapshotAt(1L));
        assertNotNull(tracker.snapshotAt(2L));
        assertNotNull(tracker.snapshotAt(3L));
    }
}
