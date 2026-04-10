package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.application.RoomService;
import com.codereboot.gameboot.application.RoomSessionGateway;
import com.codereboot.gameboot.domain.RoomSnapshot;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
class GameSocketCommandDispatcher {

    private static final String ROOM_CODE_KEY = "roomCode";
    private static final String TOKEN_KEY = "token";

    private final RoomService roomService;
    private final RoomSessionGateway sessionGateway;

    GameSocketCommandDispatcher(RoomService roomService, RoomSessionGateway sessionGateway) {
        this.roomService = roomService;
        this.sessionGateway = sessionGateway;
    }

    void dispatch(@NonNull WebSocketSession session, GameSocketCommand command) {
        if (command instanceof GameSocketCommand.Subscribe subscribe) {
            handleSubscribe(session, subscribe);
            return;
        }

        if (command instanceof GameSocketCommand.Ready) {
            roomService.setReady(roomCode(session), token(session), true);
            return;
        }

        if (command instanceof GameSocketCommand.Move move) {
            roomService.move(roomCode(session), token(session), move.direction());
            return;
        }

        if (command instanceof GameSocketCommand.Fire) {
            roomService.fire(roomCode(session), token(session));
            return;
        }

        if (command instanceof GameSocketCommand.Input input) {
            roomService.applyInput(roomCode(session), token(session), input.inputFrame());
            return;
        }

        if (command instanceof GameSocketCommand.Sync) {
            RoomSnapshot snapshot = roomService.snapshot(roomCode(session), token(session));
            sessionGateway.sendSnapshot(session, snapshot);
            return;
        }

        if (command instanceof GameSocketCommand.HitClaim hitClaim) {
            roomService.claimHit(
                    roomCode(session),
                    token(session),
                    hitClaim.shotId(),
                    hitClaim.snapshotTick()
            );
            return;
        }

        if (command instanceof GameSocketCommand.Replay) {
            String currentRoomCode = roomCode(session);
            for (RoomService.ReplayRedirect redirect : roomService.requestReplay(currentRoomCode, token(session))) {
                sessionGateway.sendReplayRedirect(currentRoomCode, redirect.oldToken(), redirect.roomCode(), redirect.token());
            }
            return;
        }

        if (command instanceof GameSocketCommand.ReturnToRoom) {
            String currentRoomCode = roomCode(session);
            String message = roomService.requestReturnToRoom(currentRoomCode, token(session));
            sessionGateway.sendRoomReturn(currentRoomCode, message);
            return;
        }

        throw new IllegalArgumentException("Unsupported websocket command: " + command.type());
    }

    private void handleSubscribe(WebSocketSession session, GameSocketCommand.Subscribe command) {
        session.getAttributes().put(ROOM_CODE_KEY, command.roomCode());
        session.getAttributes().put(TOKEN_KEY, command.token());

        try {
            RoomSnapshot snapshot = roomService.snapshot(command.roomCode(), command.token());
            sessionGateway.register(command.roomCode(), command.token(), session);
            sessionGateway.sendSnapshot(session, snapshot);
        } catch (RuntimeException exception) {
            sessionGateway.sendError(session, exception.getMessage());
            closeQuietly(session);
        }
    }

    private String roomCode(WebSocketSession session) {
        Object value = session.getAttributes().get(ROOM_CODE_KEY);
        if (value == null) {
            throw new IllegalStateException("Websocket session has no room code");
        }
        return value.toString();
    }

    private String token(WebSocketSession session) {
        Object value = session.getAttributes().get(TOKEN_KEY);
        if (value == null) {
            throw new IllegalStateException("Websocket session has no player token");
        }
        return value.toString();
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // Nothing else to do here.
        }
    }
}