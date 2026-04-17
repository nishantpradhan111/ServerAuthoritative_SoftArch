package com.codereboot.gameboot.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codereboot.gameboot.domain.Direction;
import com.codereboot.gameboot.domain.Room;
import com.codereboot.gameboot.infra.RoomRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoomServiceTest {

    @Test
    void setReadyBroadcastsSnapshotAfterMutation() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        AppClock clock = org.mockito.Mockito.mock(AppClock.class);
        when(clock.nowMillis()).thenReturn(1_000L);
        RoomService service = new RoomService(repository, broadcaster, clock);
        when(broadcaster.broadcast(any())).thenReturn(BroadcastResult.none());

        Room room = new Room("ABCDE");
        String host = room.addPlayer("Ada");
        room.addPlayer("Lin");
        when(repository.findByCode("ABCDE")).thenReturn(Optional.of(room));

        service.setReady("abcde", host, "Ada", true);

        assertEquals(true, room.requirePlayer(host).ready());
        verify(broadcaster, times(1)).broadcast(any());
    }

    @Test
    void moveBroadcastsSnapshotAfterMutation() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        AppClock clock = org.mockito.Mockito.mock(AppClock.class);
        when(clock.nowMillis()).thenReturn(1_000L);
        RoomService service = new RoomService(repository, broadcaster, clock);
        when(broadcaster.broadcast(any())).thenReturn(BroadcastResult.none());

        Room room = new Room("ABCDE");
        String host = room.addPlayer("Ada");
        room.addPlayer("Lin");
        room.setReady(host, true);
        for (var player : room.snapshot().players()) {
            if (!player.token().equals(host)) {
                room.setReady(player.token(), true);
                break;
            }
        }

        when(repository.findByCode("ABCDE")).thenReturn(Optional.of(room));

        service.move("ABCDE", host, "Ada", Direction.RIGHT);

        verify(broadcaster, times(1)).broadcast(any());
    }

    @Test
    void leaveRoomDeletesWhenRoomBecomesEmpty() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        AppClock clock = org.mockito.Mockito.mock(AppClock.class);
        when(clock.nowMillis()).thenReturn(1_000L);
        RoomService service = new RoomService(repository, broadcaster, clock);
        when(broadcaster.broadcast(any())).thenReturn(BroadcastResult.none());

        Room room = new Room("ABCDE");
        String host = room.addPlayer("Ada");
        when(repository.findByCode("ABCDE")).thenReturn(Optional.of(room));

        service.leaveRoom("ABCDE", host, "Ada");

        verify(repository, times(1)).deleteByCode("ABCDE");
        verify(broadcaster, never()).broadcast(any());
    }

    @Test
    void requestReturnToRoomRejectsNonCompletePhase() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        AppClock clock = org.mockito.Mockito.mock(AppClock.class);
        when(clock.nowMillis()).thenReturn(1_000L);
        RoomService service = new RoomService(repository, broadcaster, clock);
        when(broadcaster.broadcast(any())).thenReturn(BroadcastResult.none());

        Room room = new Room("ABCDE");
        String host = room.addPlayer("Ada");
        when(repository.findByCode("ABCDE")).thenReturn(Optional.of(room));

        assertThrows(IllegalStateException.class, () -> service.requestReturnToRoom("ABCDE", host, "Ada"));
    }

    @Test
    void createRoomFailsAfterTooManyCodeCollisions() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        AppClock clock = org.mockito.Mockito.mock(AppClock.class);
        when(clock.nowMillis()).thenReturn(1_000L);
        RoomService service = new RoomService(repository, broadcaster, clock);
        when(broadcaster.broadcast(any())).thenReturn(BroadcastResult.none());

        when(repository.findByCode(any())).thenReturn(Optional.of(new Room("ABCDE")));

        assertThrows(IllegalStateException.class, () -> service.createRoom("Ada"));
    }

    @Test
    void mutationFailsWhenBroadcastThrows() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        AppClock clock = org.mockito.Mockito.mock(AppClock.class);
        when(clock.nowMillis()).thenReturn(1_000L);
        RoomService service = new RoomService(repository, broadcaster, clock);

        Room room = new Room("ABCDE");
        String host = room.addPlayer("Ada");
        room.addPlayer("Lin");
        when(repository.findByCode("ABCDE")).thenReturn(Optional.of(room));
        when(broadcaster.broadcast(any())).thenThrow(new IllegalStateException("transport down"));

        assertThrows(IllegalStateException.class, () -> service.setReady("ABCDE", host, "Ada", true));
        assertEquals(true, room.requirePlayer(host).ready());
    }
}
