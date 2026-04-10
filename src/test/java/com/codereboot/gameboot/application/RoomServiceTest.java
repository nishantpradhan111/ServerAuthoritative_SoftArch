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
        RoomService service = new RoomService(repository, broadcaster);

        Room room = new Room("ABCDE");
        String host = room.addPlayer("Ada");
        room.addPlayer("Lin");
        when(repository.findByCode("ABCDE")).thenReturn(Optional.of(room));

        service.setReady("abcde", host, true);

        assertEquals(true, room.requirePlayer(host).ready());
        verify(broadcaster, times(1)).broadcast(any());
    }

    @Test
    void moveBroadcastsSnapshotAfterMutation() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        RoomService service = new RoomService(repository, broadcaster);

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

        service.move("ABCDE", host, Direction.RIGHT);

        verify(broadcaster, times(1)).broadcast(any());
    }

    @Test
    void leaveRoomDeletesWhenRoomBecomesEmpty() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        RoomService service = new RoomService(repository, broadcaster);

        Room room = new Room("ABCDE");
        String host = room.addPlayer("Ada");
        when(repository.findByCode("ABCDE")).thenReturn(Optional.of(room));

        service.leaveRoom("ABCDE", host);

        verify(repository, times(1)).deleteByCode("ABCDE");
        verify(broadcaster, never()).broadcast(any());
    }

    @Test
    void requestReturnToRoomRejectsNonCompletePhase() {
        RoomRepository repository = org.mockito.Mockito.mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = org.mockito.Mockito.mock(RoomEventBroadcaster.class);
        RoomService service = new RoomService(repository, broadcaster);

        Room room = new Room("ABCDE");
        String host = room.addPlayer("Ada");
        when(repository.findByCode("ABCDE")).thenReturn(Optional.of(room));

        assertThrows(IllegalStateException.class, () -> service.requestReturnToRoom("ABCDE", host));
    }
}
