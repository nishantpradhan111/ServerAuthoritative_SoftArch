package com.codereboot.gameboot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoomTest {

    @Test
    void startsWhenBothPlayersAreReady() {
        Room room = new Room("neon");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);

        assertEquals(RoomPhase.ACTIVE, room.snapshot().phase());
        assertEquals(2, room.snapshot().players().size());
        assertNotNull(room.snapshot().players().get(0).token());
    }

    @Test
    void fireReducesOpponentHealth() {
        Room room = new Room("pulse");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);
        room.fire(host);

        PlayerSnapshot target = room.snapshot().players().stream()
                .filter(player -> player.token().equals(guest))
                .findFirst()
                .orElseThrow();

        assertEquals(2, target.health());
    }

    @Test
    void movementStaysInsideBoard() {
        Room room = new Room("edge");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");
        room.setReady(host, true);
        room.setReady(guest, true);
        room.move(host, Direction.LEFT);

        PlayerSnapshot player = room.snapshot().players().get(0);
        assertEquals(0, player.x());
    }

    @Test
    void inputFrameAdvancesContinuousPosition() {
        Room room = new Room("fps");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);

        RoomSnapshot before = room.snapshot();
        room.applyInput(host, new GameInputFrame(1L, 1.0, 0.0, null, false));
        room.tick(Room.SIMULATION_STEP_SECONDS);

        PlayerSnapshot player = room.snapshot().players().stream()
                .filter(snapshot -> snapshot.token().equals(host))
                .findFirst()
                .orElseThrow();

        assertTrue(player.positionX() > before.players().get(0).positionX());
        assertEquals(before.simulationTick() + 1, room.snapshot().simulationTick());
        assertTrue(player.velocityX() > 0.0);
    }
}
