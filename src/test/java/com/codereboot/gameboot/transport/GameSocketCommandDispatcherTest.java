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
import com.codereboot.gameboot.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

@SuppressWarnings("null")
class GameSocketCommandDispatcherTest {

    @Test
    void nonSubscribeCommandWithoutSessionContextReturnsActionableError() {
        RoomService roomService = mock(RoomService.class);
        RoomSessionGateway sessionGateway = mock(RoomSessionGateway.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        WebSocketSessionContextRegistry sessionContextRegistry = new WebSocketSessionContextRegistry();
        GameSocketCommandDispatcher dispatcher =
            new GameSocketCommandDispatcher(roomService, sessionGateway, sessionContextRegistry, jwtTokenService);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-a");

        dispatcher.dispatch(session, new GameSocketCommand.Ready());

        verify(sessionGateway).sendError(eq(session), eq("Session not registered. Subscribe with roomCode and token first."));
        verify(roomService, never()).setReady(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void subscribeRegistersSessionAndSendsInitialSnapshot() {
        RoomService roomService = mock(RoomService.class);
        RoomSessionGateway sessionGateway = mock(RoomSessionGateway.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        Claims claims = mock(Claims.class);
        WebSocketSessionContextRegistry sessionContextRegistry = new WebSocketSessionContextRegistry();
        GameSocketCommandDispatcher dispatcher =
            new GameSocketCommandDispatcher(roomService, sessionGateway, sessionContextRegistry, jwtTokenService);

        when(jwtTokenService.isValid("jwt-token")).thenReturn(true);
        when(jwtTokenService.parseClaims("jwt-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("Ada");

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-a");

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
        when(roomService.snapshot("ABCDE", "token-1", "Ada")).thenReturn(snapshot);

        dispatcher.dispatch(session, new GameSocketCommand.Subscribe("ABCDE", "token-1", "jwt-token"));

        verify(sessionGateway).register("ABCDE", "token-1", session);
        verify(sessionGateway).sendSnapshot(session, snapshot);
    }

    @Test
    void replayDispatchSendsRedirectForEachParticipant() {
        RoomService roomService = mock(RoomService.class);
        RoomSessionGateway sessionGateway = mock(RoomSessionGateway.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        WebSocketSessionContextRegistry sessionContextRegistry = new WebSocketSessionContextRegistry();
        GameSocketCommandDispatcher dispatcher =
            new GameSocketCommandDispatcher(roomService, sessionGateway, sessionContextRegistry, jwtTokenService);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-a");
        sessionContextRegistry.bind("session-a", new SessionContext("ABCDE", "old-a", "Ada"));

        when(roomService.requestReplay("ABCDE", "old-a", "Ada")).thenReturn(List.of(
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
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        Claims claims = mock(Claims.class);
        WebSocketSessionContextRegistry sessionContextRegistry = new WebSocketSessionContextRegistry();
        GameSocketCommandDispatcher dispatcher =
            new GameSocketCommandDispatcher(roomService, sessionGateway, sessionContextRegistry, jwtTokenService);

        when(jwtTokenService.isValid("jwt-token")).thenReturn(true);
        when(jwtTokenService.parseClaims("jwt-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("Ada");

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-a");

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
        when(roomService.snapshot("ABCDE", "token-1", "Ada")).thenReturn(snapshot);

        dispatcher.dispatch(session, new GameSocketCommand.Ready());
        dispatcher.dispatch(session, new GameSocketCommand.Subscribe("ABCDE", "token-1", "jwt-token"));
        dispatcher.dispatch(session, new GameSocketCommand.Ready());

        verify(sessionGateway, times(1)).sendError(eq(session), eq("Session not registered. Subscribe with roomCode and token first."));
        verify(sessionGateway, times(1)).register("ABCDE", "token-1", session);
        verify(sessionGateway, times(1)).sendSnapshot(session, snapshot);
        verify(roomService, times(1)).setReady("ABCDE", "token-1", "Ada", true);
    }
}
