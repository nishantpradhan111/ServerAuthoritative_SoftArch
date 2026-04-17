package com.codereboot.gameboot.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codereboot.gameboot.transport.GameSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@SuppressWarnings("null")
class WebSocketConfigTest {

    @Test
    void registerWebSocketHandlersDoesNotSetOriginsWhenConfigBlank() {
        GameSocketHandler handler = mock(GameSocketHandler.class);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/ws")).thenReturn(registration);

        WebSocketConfig config = new WebSocketConfig(handler, "   ");
        config.registerWebSocketHandlers(registry);

        verify(registration, never()).setAllowedOriginPatterns(any(String[].class));
    }

    @Test
    void registerWebSocketHandlersTrimsConfiguredOrigins() {
        GameSocketHandler handler = mock(GameSocketHandler.class);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/ws")).thenReturn(registration);

        WebSocketConfig config = new WebSocketConfig(handler, " https://example.com,  ,http://localhost:3000 ");
        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOriginPatterns("https://example.com", "http://localhost:3000");
    }
}
