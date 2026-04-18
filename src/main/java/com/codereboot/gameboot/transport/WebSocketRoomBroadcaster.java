package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.application.BroadcastResult;
import com.codereboot.gameboot.application.RoomEventBroadcaster;
import com.codereboot.gameboot.application.RoomSessionGateway;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final WebSocketSessionRegistry sessionRegistry;
    private final Counter wsRegistrations;
    private final Counter wsUnregistrations;
    private final Counter wsBroadcastAttempted;
    private final Counter wsBroadcastDelivered;
    private final Counter wsBroadcastFailed;

    @Autowired
    public WebSocketRoomBroadcaster(
            ObjectMapper objectMapper,
            WebSocketSessionRegistry sessionRegistry,
            MeterRegistry meterRegistry
        ) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.wsRegistrations = Counter.builder("codereboot.ws.sessions.registrations")
            .description("Total websocket room/session registrations")
            .register(meterRegistry);
        this.wsUnregistrations = Counter.builder("codereboot.ws.sessions.unregistrations")
            .description("Total websocket room/session unregistrations")
            .register(meterRegistry);
        this.wsBroadcastAttempted = Counter.builder("codereboot.ws.broadcast.messages.attempted")
            .description("Total websocket broadcast message attempts")
            .register(meterRegistry);
        this.wsBroadcastDelivered = Counter.builder("codereboot.ws.broadcast.messages.delivered")
            .description("Total websocket broadcast message deliveries")
            .register(meterRegistry);
        this.wsBroadcastFailed = Counter.builder("codereboot.ws.broadcast.messages.failed")
            .description("Total websocket broadcast message failures")
            .register(meterRegistry);

        Gauge.builder("codereboot.ws.sessions.active", sessionRegistry, WebSocketSessionRegistry::activeSessionCount)
            .description("Current active websocket sessions")
            .register(meterRegistry);
        Gauge.builder("codereboot.ws.rooms.active", sessionRegistry, WebSocketSessionRegistry::activeRoomCount)
            .description("Current active websocket rooms with registered sessions")
            .register(meterRegistry);
    }

    WebSocketRoomBroadcaster(ObjectMapper objectMapper, WebSocketSessionRegistry sessionRegistry) {
        this(objectMapper, sessionRegistry, Metrics.globalRegistry);
    }

    @Override
    public void register(String roomCode, String token, @NonNull WebSocketSession session) {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT_BYTES
        );
        sessionRegistry.register(roomCode, token, session.getId(), safeSession);
        wsRegistrations.increment();
    }

    @Override
    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }

        sessionRegistry.unregister(session.getId());
        wsUnregistrations.increment();
    }

    @Override
    public BroadcastResult broadcast(RoomSnapshot snapshot) {
        ConcurrentMap<String, WebSocketSession> roomSessions = sessionRegistry.sessionsForRoom(snapshot.code());
        if (roomSessions == null) {
            return BroadcastResult.none();
        }

        int attempted = 0;
        int delivered = 0;
        int failed = 0;
        for (Map.Entry<String, WebSocketSession> entry : roomSessions.entrySet()) {
            attempted++;
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                removeSession(snapshot.code(), entry.getKey(), session);
                failed++;
                continue;
            }
            if (send(session, envelope("snapshot", snapshot))) {
                delivered++;
            } else {
                failed++;
            }
        }

        wsBroadcastAttempted.increment(attempted);
        wsBroadcastDelivered.increment(delivered);
        wsBroadcastFailed.increment(failed);

        return new BroadcastResult(attempted, delivered, failed);
    }

    @Override
    public void sendSnapshot(@NonNull WebSocketSession session, RoomSnapshot snapshot) {
        send(session, envelope("snapshot", snapshot));
    }

    @Override
    public void sendError(@NonNull WebSocketSession session, String message) {
        send(session, envelope("error", message));
    }

    @Override
    public void sendReplayRedirect(String roomCode, String token, String newRoomCode, String newToken) {
        WebSocketSession session = sessionRegistry.sessionFor(roomCode, token);
        if (session == null || !session.isOpen()) {
            removeSession(roomCode, token, session);
            return;
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "replayRedirect");
        message.put("roomCode", newRoomCode);
        message.put("token", newToken);
        send(session, message);
    }

    @Override
    public void sendRoomReturn(String roomCode, String message) {
        ConcurrentMap<String, WebSocketSession> roomSessions = sessionRegistry.sessionsForRoom(roomCode);
        if (roomSessions == null) {
            return;
        }

        for (Map.Entry<String, WebSocketSession> entry : roomSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                removeSession(roomCode, entry.getKey(), session);
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "roomReturn");
            payload.put("message", message);
            send(session, payload);
        }
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
    private boolean send(WebSocketSession session, Object payload) {
        try {
            String encoded = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(encoded.getBytes(StandardCharsets.UTF_8)));
            return true;
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to encode websocket payload for session {}", session.getId(), exception);
            return false;
        } catch (IOException | IllegalStateException exception) {
            LOGGER.debug("WebSocket send failed for session {}", session.getId(), exception);
            closeQuietly(session);
            unregister(session);
            return false;
        }
    }

    private void removeSession(String roomCode, String token, WebSocketSession session) {
        sessionRegistry.remove(roomCode, token, session == null ? null : session.getId());
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
}