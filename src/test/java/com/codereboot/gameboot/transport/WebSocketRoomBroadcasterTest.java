package com.codereboot.gameboot.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codereboot.gameboot.application.BroadcastResult;
import com.codereboot.gameboot.domain.RoomPhase;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class WebSocketRoomBroadcasterTest {

    @Test
    void broadcastReturnsNoneWhenNoSessionsRegistered() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketRoomBroadcaster broadcaster = new WebSocketRoomBroadcaster(new ObjectMapper(), registry);

        BroadcastResult result = broadcaster.broadcast(snapshot("ABCDE"));

        assertEquals(0, result.attempted());
        assertEquals(0, result.delivered());
        assertEquals(0, result.failed());
    }

    @Test
    void closedSessionIsCountedAsFailed() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketRoomBroadcaster broadcaster = new WebSocketRoomBroadcaster(new ObjectMapper(), registry);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-a");
        when(session.isOpen()).thenReturn(false);

        broadcaster.register("ABCDE", "token-a", session);
        BroadcastResult result = broadcaster.broadcast(snapshot("ABCDE"));

        assertEquals(1, result.attempted());
        assertEquals(0, result.delivered());
        assertEquals(1, result.failed());
    }

    private RoomSnapshot snapshot(String roomCode) {
        return new RoomSnapshot(
                roomCode,
                RoomPhase.LOBBY,
                15,
                10,
                0L,
                List.of(),
                null,
                "test",
                false,
                List.of()
        );
    }
}