package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.application.RoomService;
import com.codereboot.gameboot.application.RoomSessionGateway;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.codereboot.gameboot.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
class GameSocketCommandDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameSocketCommandDispatcher.class);

    private final RoomService roomService;
    private final RoomSessionGateway sessionGateway;
    private final WebSocketSessionContextRegistry sessionContextRegistry;
    private final JwtTokenService jwtTokenService;

    GameSocketCommandDispatcher(
            RoomService roomService,
            RoomSessionGateway sessionGateway,
            WebSocketSessionContextRegistry sessionContextRegistry,
            JwtTokenService jwtTokenService
    ) {
        this.roomService = roomService;
        this.sessionGateway = sessionGateway;
        this.sessionContextRegistry = sessionContextRegistry;
        this.jwtTokenService = jwtTokenService;
    }

    void dispatch(@NonNull WebSocketSession session, GameSocketCommand command) {
        if (command instanceof GameSocketCommand.Subscribe subscribe) {
            handleSubscribe(session, subscribe);
            return;
        }

        if (!hasSessionContext(session)) {
            sessionGateway.sendError(session, "Session not registered. Subscribe with roomCode and token first.");
            return;
        }

        String roomCode = roomCode(session);
        String token = token(session);
        String username = username(session);

        switch (command) {
            case GameSocketCommand.Ready ignored -> roomService.setReady(roomCode, token, username, true);
            case GameSocketCommand.Move move -> roomService.move(roomCode, token, username, move.direction());
            case GameSocketCommand.Fire ignored -> roomService.fire(roomCode, token, username);
            case GameSocketCommand.Input input -> roomService.applyInput(roomCode, token, username, input.inputFrame());
            case GameSocketCommand.Sync ignored -> {
                RoomSnapshot snapshot = roomService.snapshot(roomCode, token, username);
                sessionGateway.sendSnapshot(session, snapshot);
            }
            case GameSocketCommand.HitClaim hitClaim -> roomService.claimHit(
                    roomCode,
                    token,
                    username,
                    hitClaim.shotId(),
                    hitClaim.snapshotTick()
            );
            case GameSocketCommand.Replay ignored -> {
                for (RoomService.ReplayRedirect redirect : roomService.requestReplay(roomCode, token, username)) {
                    sessionGateway.sendReplayRedirect(roomCode, redirect.oldToken(), redirect.roomCode(), redirect.token());
                }
            }
            case GameSocketCommand.ReturnToRoom ignored -> {
                String message = roomService.requestReturnToRoom(roomCode, token, username);
                sessionGateway.sendRoomReturn(roomCode, message);
            }
            default -> throw new IllegalArgumentException("Unsupported websocket command: " + command.type());
        }
    }

    private void handleSubscribe(WebSocketSession session, GameSocketCommand.Subscribe command) {
        Claims claims = requireValidClaims(command.authToken());
        String username = claims.getSubject();
        SessionContext context = new SessionContext(command.roomCode(), command.token(), username);
        sessionContextRegistry.bind(session.getId(), context);

        try {
            RoomSnapshot snapshot = roomService.snapshot(command.roomCode(), command.token(), username);
            sessionGateway.register(command.roomCode(), command.token(), session);
            sessionGateway.sendSnapshot(session, snapshot);
        } catch (RuntimeException exception) {
            sessionContextRegistry.clear(session.getId());
            LOGGER.debug("Websocket subscribe failed for session {}", session.getId(), exception);
            sessionGateway.sendError(session, "Unable to subscribe to room");
            closeQuietly(session);
        }
    }

    private String roomCode(WebSocketSession session) {
        SessionContext context = sessionContext(session);
        if (context == null) {
            throw new IllegalStateException("Websocket session has no room code");
        }
        return context.roomCode();
    }

    private String token(WebSocketSession session) {
        SessionContext context = sessionContext(session);
        if (context == null) {
            throw new IllegalStateException("Websocket session has no player token");
        }
        return context.token();
    }

    private String username(WebSocketSession session) {
        SessionContext context = sessionContext(session);
        if (context == null) {
            throw new IllegalStateException("Websocket session has no authenticated user");
        }
        return context.username();
    }

    private boolean hasSessionContext(WebSocketSession session) {
        return sessionContext(session) != null;
    }

    private SessionContext sessionContext(WebSocketSession session) {
        return sessionContextRegistry.get(session.getId());
    }

    void clearSessionContext(WebSocketSession session) {
        sessionContextRegistry.clear(session.getId());
    }

    private Claims requireValidClaims(String authToken) {
        if (authToken == null || authToken.isBlank() || !jwtTokenService.isValid(authToken)) {
            throw new IllegalArgumentException("Invalid websocket auth token");
        }

        Claims claims = jwtTokenService.parseClaims(authToken);
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new IllegalArgumentException("Invalid websocket auth token");
        }
        return claims;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // Nothing else to do here.
        }
    }
}