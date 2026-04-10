package com.codereboot.gameboot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
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
    void validatedHitClaimReducesOpponentHealth() {
        Room room = new Room("pulse");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);
        room.fire(host);
        long shotId = room.snapshot().players().stream()
            .filter(player -> player.token().equals(host))
            .findFirst()
            .orElseThrow()
            .lastShotId();
        room.claimHit(host, shotId, room.snapshot().simulationTick());

        PlayerSnapshot target = room.snapshot().players().stream()
                .filter(player -> player.token().equals(guest))
                .findFirst()
                .orElseThrow();

        assertEquals(Room.STARTING_HEALTH - 1, target.health());
    }

    @Test
    void timeExpiredShotIsRejected() {
        AtomicLong nowMs = new AtomicLong(1_000L);
        Room room = new Room("clock", nowMs::get);
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);

        Player shooter = room.requirePlayer(host);
        Player target = room.requirePlayer(guest);
        shooter.moveTo(2.0, 1.0);
        target.moveTo(4.0, 3.0);
        shooter.face(45.0);

        room.fire(host);
        long shotId = room.snapshot().players().stream()
                .filter(player -> player.token().equals(host))
                .findFirst()
                .orElseThrow()
                .lastShotId();

        nowMs.addAndGet(2_500L);
        room.claimHit(host, shotId, room.snapshot().simulationTick());

        PlayerSnapshot targetSnapshot = room.snapshot().players().stream()
                .filter(player -> player.token().equals(guest))
                .findFirst()
                .orElseThrow();

        assertEquals(Room.STARTING_HEALTH, targetSnapshot.health());
    }

    @Test
    void preFireSnapshotTickCannotRegisterHit() {
        Room room = new Room("preTick");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);

        Player shooter = room.requirePlayer(host);
        Player target = room.requirePlayer(guest);
        shooter.moveTo(2.0, 1.0);
        target.moveTo(4.0, 3.0);
        shooter.face(45.0);

        long staleTick = room.snapshot().simulationTick();
        room.tick(Room.SIMULATION_STEP_SECONDS);
        room.fire(host);
        long shotId = room.snapshot().players().stream()
                .filter(player -> player.token().equals(host))
                .findFirst()
                .orElseThrow()
                .lastShotId();

        room.claimHit(host, shotId, staleTick);

        PlayerSnapshot targetSnapshot = room.snapshot().players().stream()
                .filter(player -> player.token().equals(guest))
                .findFirst()
                .orElseThrow();
        assertEquals(Room.STARTING_HEALTH, targetSnapshot.health());
    }

    @Test
    void fireDecrementsShooterAmmo() {
        Room room = new Room("ammo");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);
        room.fire(host);

        PlayerSnapshot shooter = room.snapshot().players().stream()
                .filter(player -> player.token().equals(host))
                .findFirst()
                .orElseThrow();

        assertEquals(Room.STARTING_AMMO - 1, shooter.ammo());
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

        RoomSnapshot after = room.snapshot();

        assertTrue(player.positionX() > before.players().get(0).positionX());
        assertEquals(before.simulationTick() + 1, after.simulationTick());
        assertTrue(player.velocityX() > 0.0);
        assertEquals(1L, player.lastProcessedInputSequence());
        assertEquals(after.simulationTick(), player.snapshotTick());
    }

    @Test
    void diagonalAimCanHitWithinRange() {
        Room room = new Room("diag");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);

        Player shooter = room.requirePlayer(host);
        Player target = room.requirePlayer(guest);
        shooter.moveTo(2.0, 1.0);
        target.moveTo(4.0, 3.0);
        shooter.face(45.0);

        room.fire(host);
        long shotId = room.snapshot().players().stream()
            .filter(player -> player.token().equals(host))
            .findFirst()
            .orElseThrow()
            .lastShotId();
        room.claimHit(host, shotId, room.snapshot().simulationTick());

        PlayerSnapshot targetSnapshot = room.snapshot().players().stream()
                .filter(player -> player.token().equals(guest))
                .findFirst()
                .orElseThrow();
        assertEquals(Room.STARTING_HEALTH - 1, targetSnapshot.health());
    }

    @Test
    void aimingAwayStillMisses() {
        Room room = new Room("miss");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.setReady(host, true);
        room.setReady(guest, true);

        Player shooter = room.requirePlayer(host);
        Player target = room.requirePlayer(guest);
        shooter.moveTo(2.0, 1.0);
        target.moveTo(4.0, 3.0);
        shooter.face(225.0);

        room.fire(host);
        long shotId = room.snapshot().players().stream()
            .filter(player -> player.token().equals(host))
            .findFirst()
            .orElseThrow()
            .lastShotId();
        room.claimHit(host, shotId, room.snapshot().simulationTick());

        PlayerSnapshot targetSnapshot = room.snapshot().players().stream()
                .filter(player -> player.token().equals(guest))
                .findFirst()
                .orElseThrow();
        assertEquals(Room.STARTING_HEALTH, targetSnapshot.health());
    }

    @Test
    void leavingLobbyFreesSlotForAnotherPlayer() {
        Room room = new Room("lobby");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");

        room.removePlayer(guest);
        String replacement = room.addPlayer("Kai");

        RoomSnapshot snapshot = room.snapshot();
        assertEquals(2, snapshot.players().size());
        assertTrue(snapshot.players().stream().anyMatch(player -> player.token().equals(host)));
        assertTrue(snapshot.players().stream().anyMatch(player -> player.token().equals(replacement)));
    }

    @Test
    void replayRequestAppearsInSnapshotForAllClients() {
        Room room = new Room("rply1");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");
        finishMatch(room, host, guest);

        room.requestReplay(host);
        RoomSnapshot snapshot = room.snapshot();

        assertTrue(snapshot.replayPendingTokens().contains(host));
        assertFalse(snapshot.replayPendingTokens().contains(guest));
    }

    @Test
    void replayPendingClearsWhenReplayIsMatched() {
        Room room = new Room("rply2");
        String host = room.addPlayer("Ada");
        String guest = room.addPlayer("Lin");
        finishMatch(room, host, guest);

        room.requestReplay(host);
        var participants = room.requestReplay(guest);

        assertEquals(2, participants.size());
        assertTrue(room.snapshot().replayPendingTokens().isEmpty());
    }

    private void finishMatch(Room room, String attacker, String targetToken) {
        room.setReady(attacker, true);
        room.setReady(targetToken, true);

        Player shooter = room.requirePlayer(attacker);
        Player target = room.requirePlayer(targetToken);
        shooter.moveTo(2.0, 1.0);
        target.moveTo(4.0, 3.0);
        shooter.face(45.0);

        for (int hit = 0; hit < Room.STARTING_HEALTH; hit++) {
            room.fire(attacker);
            long shotId = room.snapshot().players().stream()
                    .filter(player -> player.token().equals(attacker))
                    .findFirst()
                    .orElseThrow()
                    .lastShotId();
            room.claimHit(attacker, shotId, room.snapshot().simulationTick());
        }

        assertEquals(RoomPhase.COMPLETE, room.snapshot().phase());
    }
}
