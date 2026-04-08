package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.application.RoomBroadcastGateway;
import com.codereboot.gameboot.application.RoomService;
import com.codereboot.gameboot.domain.Direction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private static final String ROOM_CODE_KEY = "roomCode";
    private static final String TOKEN_KEY = "token";

    private final ObjectMapper objectMapper;
    private final RoomService roomService;
    private final RoomBroadcastGateway broadcastGateway;

    public GameSocketHandler(ObjectMapper objectMapper, RoomService roomService, RoomBroadcastGateway broadcastGateway) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
        this.broadcastGateway = broadcastGateway;
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String type = text(payload, "type");

            switch (type) {
                case "subscribe" -> subscribe(session, payload);
                case "ready" -> roomService.setReady(roomCode(session), token(session), true);
                case "move" -> roomService.move(roomCode(session), token(session), Direction.from(text(payload, "direction")));
                case "fire" -> roomService.fire(roomCode(session), token(session));
                case "sync" -> broadcastGateway.sendSnapshot(session, roomService.snapshot(roomCode(session), token(session)));
                default -> broadcastGateway.sendError(session, "Unknown websocket command: " + type);
            }
        } catch (RuntimeException exception) {
            broadcastGateway.sendError(session, exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        broadcastGateway.unregister(session);
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        // Clients register themselves with an explicit subscribe message.
    }

    private void subscribe(WebSocketSession session, JsonNode payload) {
        String roomCode = text(payload, "roomCode");
        String token = text(payload, "token");
        session.getAttributes().put(ROOM_CODE_KEY, roomCode);
        session.getAttributes().put(TOKEN_KEY, token);
        try {
            roomService.snapshot(roomCode, token);
            broadcastGateway.register(roomCode, token, session);
            broadcastGateway.sendSnapshot(session, roomService.snapshot(roomCode, token));
        } catch (NoSuchElementException | IllegalStateException exception) {
            broadcastGateway.sendError(session, exception.getMessage());
            try {
                session.close();
            } catch (IOException ignored) {
                // Nothing else to do here.
            }
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

    private String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return value.asText();
    }
}