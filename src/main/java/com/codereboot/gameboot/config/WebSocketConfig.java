package com.codereboot.gameboot.config;

import com.codereboot.gameboot.transport.GameSocketHandler;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final @NonNull GameSocketHandler gameSocketHandler;
    private final String allowedOrigins;

    public WebSocketConfig(
            @NonNull GameSocketHandler gameSocketHandler,
            @Value("${app.websocket.allowed-origins:}") String allowedOrigins
    ) {
        this.gameSocketHandler = gameSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(gameSocketHandler, "/ws");
        String[] originPatterns = resolveAllowedOrigins();
        if (originPatterns.length > 0) {
            registration.setAllowedOriginPatterns(originPatterns);
        }
    }

    private String[] resolveAllowedOrigins() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return new String[0];
        }

        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }
}