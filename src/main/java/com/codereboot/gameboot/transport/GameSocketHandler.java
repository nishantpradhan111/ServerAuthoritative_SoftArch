package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.application.RoomSessionGateway;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private final GameSocketCommandParser commandParser;
    private final GameSocketCommandDispatcher commandDispatcher;
    private final RoomSessionGateway sessionGateway;

    public GameSocketHandler(
            GameSocketCommandParser commandParser,
            GameSocketCommandDispatcher commandDispatcher,
            RoomSessionGateway sessionGateway
    ) {
        this.commandParser = commandParser;
        this.commandDispatcher = commandDispatcher;
        this.sessionGateway = sessionGateway;
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        try {
            GameSocketCommand command = commandParser.parse(message.getPayload());
            commandDispatcher.dispatch(session, command);
        } catch (RuntimeException exception) {
            sessionGateway.sendError(session, exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessionGateway.unregister(session);
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        // Clients register themselves with an explicit subscribe message.
    }
}