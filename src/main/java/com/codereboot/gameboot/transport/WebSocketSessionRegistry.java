package com.codereboot.gameboot.transport;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
class WebSocketSessionRegistry {

    private final ConcurrentMap<String, ConcurrentMap<String, WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionRef> sessionRefs = new ConcurrentHashMap<>();

    void register(String roomCode, String token, String sessionId, @NonNull WebSocketSession session) {
        ConcurrentMap<String, WebSocketSession> roomSessions =
                sessionsByRoom.computeIfAbsent(roomCode, ignored -> new ConcurrentHashMap<>());
        WebSocketSession previous = roomSessions.put(token, session);

        if (previous != null) {
            String previousSessionId = previous.getId();
            if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
                sessionRefs.remove(previousSessionId);
            }
        }

        sessionRefs.put(sessionId, new SessionRef(roomCode, token));
    }

    void unregister(String sessionId) {
        if (sessionId == null) {
            return;
        }

        SessionRef ref = sessionRefs.remove(sessionId);
        if (ref == null) {
            return;
        }

        removeIfCurrent(ref.roomCode(), ref.token(), sessionId);
    }

    ConcurrentMap<String, WebSocketSession> sessionsForRoom(String roomCode) {
        return sessionsByRoom.get(roomCode);
    }

    WebSocketSession sessionFor(String roomCode, String token) {
        ConcurrentMap<String, WebSocketSession> roomSessions = sessionsByRoom.get(roomCode);
        if (roomSessions == null) {
            return null;
        }
        return roomSessions.get(token);
    }

    void remove(String roomCode, String token, String sessionId) {
        if (sessionId != null) {
            sessionRefs.remove(sessionId);
        }

        removeIfCurrent(roomCode, token, sessionId);
    }

    private void removeIfCurrent(String roomCode, String token, String expectedSessionId) {
        ConcurrentMap<String, WebSocketSession> roomSessions = sessionsByRoom.get(roomCode);
        if (roomSessions == null) {
            return;
        }

        WebSocketSession current = roomSessions.get(token);
        if (current == null) {
            return;
        }

        if (expectedSessionId != null && !expectedSessionId.equals(current.getId())) {
            return;
        }

        roomSessions.remove(token, current);
        if (roomSessions.isEmpty()) {
            sessionsByRoom.remove(roomCode, roomSessions);
        }
    }

    private record SessionRef(String roomCode, String token) {
    }
}