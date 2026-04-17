package com.codereboot.gameboot.transport;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
class WebSocketSessionContextRegistry {

    private final ConcurrentMap<String, SessionContext> contextBySessionId = new ConcurrentHashMap<>();

    void bind(String sessionId, SessionContext context) {
        if (sessionId == null || context == null) {
            return;
        }
        contextBySessionId.put(sessionId, context);
    }

    SessionContext get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return contextBySessionId.get(sessionId);
    }

    void clear(String sessionId) {
        if (sessionId == null) {
            return;
        }
        contextBySessionId.remove(sessionId);
    }
}