package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.domain.Direction;
import com.codereboot.gameboot.domain.GameInputFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
class GameSocketCommandParser {

    private final ObjectMapper objectMapper;

    GameSocketCommandParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    GameSocketCommand parse(String payloadText) {
        JsonNode payload = parsePayload(payloadText);
        String type = text(payload, "type");

        return switch (type) {
            case "subscribe" -> new GameSocketCommand.Subscribe(text(payload, "roomCode"), text(payload, "token"));
            case "ready" -> new GameSocketCommand.Ready();
            case "move" -> new GameSocketCommand.Move(Direction.from(text(payload, "direction")));
            case "fire" -> new GameSocketCommand.Fire();
            case "input" -> new GameSocketCommand.Input(inputFrame(payload));
            case "sync" -> new GameSocketCommand.Sync();
            default -> throw new IllegalArgumentException("Unknown websocket command: " + type);
        };
    }

    private JsonNode parsePayload(String payloadText) {
        try {
            return objectMapper.readTree(payloadText);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid websocket payload", exception);
        }
    }

    private String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return value.asText();
    }

    private GameInputFrame inputFrame(JsonNode payload) {
        long sequence = payload.path("sequence").asLong(0L);
        double moveX = payload.path("moveX").asDouble(0.0);
        double moveY = payload.path("moveY").asDouble(0.0);
        Double aimDegrees = payload.hasNonNull("aimDegrees") ? payload.path("aimDegrees").asDouble() : null;
        boolean firing = payload.path("firing").asBoolean(false);
        return new GameInputFrame(sequence, moveX, moveY, aimDegrees, firing);
    }
}