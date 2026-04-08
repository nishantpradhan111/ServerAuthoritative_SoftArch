package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.application.RoomBroadcastGateway;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketRoomBroadcaster implements RoomBroadcastGateway {

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ConcurrentMap<String, WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public WebSocketRoomBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void register(String roomCode, String token, WebSocketSession session) {
        sessionsByRoom.computeIfAbsent(roomCode, ignored -> new ConcurrentHashMap<>()).put(token, session);
    }

    @Override
    public void unregister(WebSocketSession session) {
        sessionsByRoom.values().forEach(roomSessions -> roomSessions.entrySet().removeIf(entry -> entry.getValue().equals(session)));
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
                roomSessions.remove(entry.getKey());
                continue;
            }
            sendSnapshot(session, snapshot);
        }
    }

    @Override
    public void sendSnapshot(WebSocketSession session, RoomSnapshot snapshot) {
        send(session, envelope("snapshot", snapshot));
    }

    @Override
    public void sendError(WebSocketSession session, String message) {
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
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to send websocket payload", exception);
        }
    }
}