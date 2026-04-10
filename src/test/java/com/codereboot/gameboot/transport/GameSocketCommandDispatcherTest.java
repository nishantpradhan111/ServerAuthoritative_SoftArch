package com.codereboot.gameboot.transport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codereboot.gameboot.application.RoomService;
import com.codereboot.gameboot.application.RoomSessionGateway;
import com.codereboot.gameboot.domain.RoomPhase;
import com.codereboot.gameboot.domain.RoomSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class GameSocketCommandDispatcherTest {

    @Test
    void nonSubscribeCommandWithoutSessionContextReturnsActionableError() {
        RoomService roomService = mock(RoomService.class);
        RoomSessionGateway sessionGateway = mock(RoomSessionGateway.class);
        GameSocketCommandDispatcher dispatcher = new GameSocketCommandDispatcher(roomService, sessionGateway);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());

        dispatcher.dispatch(session, new GameSocketCommand.Ready());

        verify(sessionGateway).sendError(eq(session), eq("Session not registered. Subscribe with roomCode and token first."));
        verify(roomService, never()).setReady(any(), any(), any(Boolean.class));
    }

    @Test
    void subscribeRegistersSessionAndSendsInitialSnapshot() {
        RoomService roomService = mock(RoomService.class);
        RoomSessionGateway sessionGateway = mock(RoomSessionGateway.class);
        GameSocketCommandDispatcher dispatcher = new GameSocketCommandDispatcher(roomService, sessionGateway);

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        RoomSnapshot snapshot = new RoomSnapshot(
                "ABCDE",
                RoomPhase.LOBBY,
                15,
                10,
                0L,
                List.of(),
                null,
                "ready",
                false,
                List.of()
        );
        when(roomService.snapshot("ABCDE", "token-1")).thenReturn(snapshot);

        dispatcher.dispatch(session, new GameSocketCommand.Subscribe("ABCDE", "token-1"));

        verify(sessionGateway).register("ABCDE", "token-1", session);
        verify(sessionGateway).sendSnapshot(session, snapshot);
    }

    @Test
    void replayDispatchSendsRedirectForEachParticipant() {
        RoomService roomService = mock(RoomService.class);
        RoomSessionGateway sessionGateway = mock(RoomSessionGateway.class);
        GameSocketCommandDispatcher dispatcher = new GameSocketCommandDispatcher(roomService, sessionGateway);

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("roomCode", "ABCDE");
        attributes.put("token", "old-a");
        when(session.getAttributes()).thenReturn(attributes);

        when(roomService.requestReplay("ABCDE", "old-a")).thenReturn(List.of(
                new RoomService.ReplayRedirect("old-a", "FGHIJ", "new-a"),
                new RoomService.ReplayRedirect("old-b", "FGHIJ", "new-b")
        ));

        dispatcher.dispatch(session, new GameSocketCommand.Replay());

        verify(sessionGateway, times(1)).sendReplayRedirect("ABCDE", "old-a", "FGHIJ", "new-a");
        verify(sessionGateway, times(1)).sendReplayRedirect("ABCDE", "old-b", "FGHIJ", "new-b");
    }

    @Test
    void reconnectRaceEarlyCommandRejectedThenAcceptedAfterSubscribe() {
        RoomService roomService = mock(RoomService.class);
        RoomSessionGateway sessionGateway = mock(RoomSessionGateway.class);
        GameSocketCommandDispatcher dispatcher = new GameSocketCommandDispatcher(roomService, sessionGateway);

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        RoomSnapshot snapshot = new RoomSnapshot(
                "ABCDE",
                RoomPhase.LOBBY,
                15,
                10,
                0L,
                List.of(),
                null,
                "ready",
                false,
                List.of()
        );
        when(roomService.snapshot("ABCDE", "token-1")).thenReturn(snapshot);

        dispatcher.dispatch(session, new GameSocketCommand.Ready());
        dispatcher.dispatch(session, new GameSocketCommand.Subscribe("ABCDE", "token-1"));
        dispatcher.dispatch(session, new GameSocketCommand.Ready());

        verify(sessionGateway, times(1)).sendError(eq(session), eq("Session not registered. Subscribe with roomCode and token first."));
        verify(sessionGateway, times(1)).register("ABCDE", "token-1", session);
        verify(sessionGateway, times(1)).sendSnapshot(session, snapshot);
        verify(roomService, times(1)).setReady("ABCDE", "token-1", true);
    }
}
