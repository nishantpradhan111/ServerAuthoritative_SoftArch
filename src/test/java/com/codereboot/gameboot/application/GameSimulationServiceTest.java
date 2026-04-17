package com.codereboot.gameboot.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codereboot.gameboot.domain.Room;
import com.codereboot.gameboot.domain.RoomPhase;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.codereboot.gameboot.infra.RoomRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameSimulationServiceTest {

    @Test
    void completedRoomIsDeletedAfterTtl() {
        RoomRepository repository = mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = mock(RoomEventBroadcaster.class);
        AppClock clock = mock(AppClock.class);
        GameSimulationService service = new GameSimulationService(repository, broadcaster, clock);
        when(broadcaster.broadcast(any())).thenReturn(BroadcastResult.none());

        Room room = mock(Room.class);
        when(repository.findAll()).thenReturn(List.of(room));
        when(room.tick(Room.SIMULATION_STEP_SECONDS)).thenReturn(false);
        when(room.phase()).thenReturn(RoomPhase.COMPLETE);
        when(room.code()).thenReturn("ABCDE");
        when(clock.nowMillis()).thenReturn(1_000L, 31_001L);

        service.tickRooms();
        service.tickRooms();

        verify(repository, times(1)).deleteByCode("ABCDE");
    }

    @Test
    void changedRoomBroadcastsSnapshot() {
        RoomRepository repository = mock(RoomRepository.class);
        RoomEventBroadcaster broadcaster = mock(RoomEventBroadcaster.class);
        AppClock clock = mock(AppClock.class);
        GameSimulationService service = new GameSimulationService(repository, broadcaster, clock);
        when(broadcaster.broadcast(any())).thenReturn(BroadcastResult.none());

        Room room = mock(Room.class);
        RoomSnapshot snapshot = new RoomSnapshot(
                "ABCDE",
                RoomPhase.ACTIVE,
                15,
                10,
                1L,
                List.of(),
                null,
                "tick",
                false,
                List.of()
        );
        when(repository.findAll()).thenReturn(List.of(room));
        when(room.tick(Room.SIMULATION_STEP_SECONDS)).thenReturn(true);
        when(room.snapshot()).thenReturn(snapshot);
        when(room.phase()).thenReturn(RoomPhase.ACTIVE);
        when(room.code()).thenReturn("ABCDE");
        when(clock.nowMillis()).thenReturn(1_000L);

        service.tickRooms();

        verify(broadcaster, times(1)).broadcast(any(RoomSnapshot.class));
    }
}