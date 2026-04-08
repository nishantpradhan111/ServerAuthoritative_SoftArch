package com.codereboot.gameboot.config;

import com.codereboot.gameboot.transport.GameSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final @NonNull GameSocketHandler gameSocketHandler;

    public WebSocketConfig(@NonNull GameSocketHandler gameSocketHandler) {
        this.gameSocketHandler = gameSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(gameSocketHandler, "/ws").setAllowedOriginPatterns("*");
    }
}