package com.codereboot.gameboot.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

@SuppressWarnings("null")
class WebSocketSessionRegistryTest {

    @Test
    void registerStoresSessionByRoomAndToken() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession session = mock(WebSocketSession.class);

        registry.register("ABCDE", "token-a", "session-a", session);

        assertEquals(session, registry.sessionFor("ABCDE", "token-a"));
        assertEquals(1, registry.sessionsForRoom("ABCDE").size());
    }

    @Test
    void unregisterRemovesSessionAndEmptiesRoomBucket() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-a");

        registry.register("ABCDE", "token-a", "session-a", session);
        registry.unregister(session.getId());

        assertNull(registry.sessionFor("ABCDE", "token-a"));
        assertNull(registry.sessionsForRoom("ABCDE"));
    }

    @Test
    void removeClearsMappedSessionId() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-a");

        registry.register("ABCDE", "token-a", "session-a", session);
        registry.remove("ABCDE", "token-a", "session-a");

        assertNull(registry.sessionFor("ABCDE", "token-a"));
        assertNull(registry.sessionsForRoom("ABCDE"));

        // No-op verify for double removal path safety.
        registry.unregister("session-a");
    }

    @Test
    void unregisterOfStaleSessionDoesNotRemoveReplacement() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession oldSession = mock(WebSocketSession.class);
        WebSocketSession newSession = mock(WebSocketSession.class);
        when(oldSession.getId()).thenReturn("session-old");
        when(newSession.getId()).thenReturn("session-new");

        registry.register("ABCDE", "token-a", "session-old", oldSession);
        registry.register("ABCDE", "token-a", "session-new", newSession);

        registry.unregister("session-old");

        assertSame(newSession, registry.sessionFor("ABCDE", "token-a"));
        assertEquals(1, registry.sessionsForRoom("ABCDE").size());
    }

    @Test
    void removeWithStaleSessionIdDoesNotRemoveReplacement() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession oldSession = mock(WebSocketSession.class);
        WebSocketSession newSession = mock(WebSocketSession.class);
        when(oldSession.getId()).thenReturn("session-old");
        when(newSession.getId()).thenReturn("session-new");

        registry.register("ABCDE", "token-a", "session-old", oldSession);
        registry.register("ABCDE", "token-a", "session-new", newSession);

        registry.remove("ABCDE", "token-a", "session-old");

        assertSame(newSession, registry.sessionFor("ABCDE", "token-a"));
        assertEquals(1, registry.sessionsForRoom("ABCDE").size());
    }
}