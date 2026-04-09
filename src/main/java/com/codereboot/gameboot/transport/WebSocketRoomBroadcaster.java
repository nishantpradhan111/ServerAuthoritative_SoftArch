package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.application.RoomEventBroadcaster;
import com.codereboot.gameboot.application.RoomSessionGateway;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Component
public class WebSocketRoomBroadcaster implements RoomEventBroadcaster, RoomSessionGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketRoomBroadcaster.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;
    private static final CloseStatus SEND_FAILURE_STATUS = new CloseStatus(1011, "Server send failure");

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ConcurrentMap<String, WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionRef> sessionRefs = new ConcurrentHashMap<>();

    public WebSocketRoomBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void register(String roomCode, String token, @NonNull WebSocketSession session) {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT_BYTES
        );
        sessionsByRoom.computeIfAbsent(roomCode, ignored -> new ConcurrentHashMap<>()).put(token, safeSession);
        sessionRefs.put(session.getId(), new SessionRef(roomCode, token));
    }

    @Override
    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }

        SessionRef ref = sessionRefs.remove(session.getId());
        if (ref == null) {
            return;
        }

        ConcurrentMap<String, WebSocketSession> roomSessions = sessionsByRoom.get(ref.roomCode());
        if (roomSessions == null) {
            return;
        }

        roomSessions.remove(ref.token());
        if (roomSessions.isEmpty()) {
            sessionsByRoom.remove(ref.roomCode(), roomSessions);
        }
    }

    @Override
    public void broadcast(RoomSnapshot snapshot) {
        ConcurrentMap<String, WebSocketSession> roomSessions = sessionsByRoom.get(snapshot.code());
        if (roomSessions == null) {
            return;
        }
        for (Map.Entry<String, WebSocketSession> entry : roomSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                removeSession(snapshot.code(), entry.getKey(), session);
                continue;
            }
            sendSnapshot(session, snapshot);
        }
    }

    @Override
    public void sendSnapshot(@NonNull WebSocketSession session, RoomSnapshot snapshot) {
        send(session, envelope("snapshot", snapshot));
    }

    @Override
    public void sendError(@NonNull WebSocketSession session, String message) {
        send(session, envelope("error", message));
    }

    private Map<String, Object> envelope(String type, Object payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        if (payload != null) {
            if (payload instanceof String text) {
                message.put("message", text);
            } else {
                message.put("snapshot", payload);
            }
        }
        return message;
    }

    @SuppressWarnings("null")
    private void send(WebSocketSession session, Object payload) {
        try {
            String encoded = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(encoded.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode websocket payload", exception);
        } catch (IOException | IllegalStateException exception) {
            LOGGER.debug("WebSocket send failed for session {}", session.getId(), exception);
            closeQuietly(session);
            unregister(session);
        }
    }

    private void removeSession(String roomCode, String token, WebSocketSession session) {
        ConcurrentMap<String, WebSocketSession> roomSessions = sessionsByRoom.get(roomCode);
        if (roomSessions != null) {
            roomSessions.remove(token);
            if (roomSessions.isEmpty()) {
                sessionsByRoom.remove(roomCode, roomSessions);
            }
        }

        if (session != null) {
            sessionRefs.remove(session.getId());
        }
    }

    @SuppressWarnings("null")
    private void closeQuietly(WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(SEND_FAILURE_STATUS);
        } catch (IOException ignored) {
            // Nothing to do.
        }
    }

    private record SessionRef(String roomCode, String token) {
    }
}