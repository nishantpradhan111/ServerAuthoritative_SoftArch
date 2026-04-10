package com.codereboot.gameboot.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GameSocketCommandParserTest {

    private final GameSocketCommandParser parser = new GameSocketCommandParser(new ObjectMapper());

    @Test
    void malformedJsonIsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("{\"type\":\"ready\"")
        );

        assertEquals("Invalid websocket payload", error.getMessage());
    }

    @Test
    void missingTypeFieldIsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("{}")
        );

        assertEquals("Missing field: type", error.getMessage());
    }

    @Test
    void unknownCommandTypeIsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("{\"type\":\"warpDrive\"}")
        );

        assertEquals("Unknown websocket command: warpDrive", error.getMessage());
    }

    @Test
    void moveWithoutDirectionIsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("{\"type\":\"move\"}")
        );

        assertEquals("Missing field: direction", error.getMessage());
    }

    @Test
    void subscribeRequiresRoomCodeAndToken() {
        IllegalArgumentException missingRoom = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("{\"type\":\"subscribe\",\"token\":\"t1\"}")
        );
        assertEquals("Missing field: roomCode", missingRoom.getMessage());

        IllegalArgumentException missingToken = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("{\"type\":\"subscribe\",\"roomCode\":\"ABCDE\"}")
        );
        assertEquals("Missing field: token", missingToken.getMessage());
    }

    @Test
    void inputParsesWithDefaultsWhenOptionalFieldsOmitted() {
        GameSocketCommand parsed = parser.parse("{\"type\":\"input\",\"sequence\":3}");
        GameSocketCommand.Input input = assertInstanceOf(GameSocketCommand.Input.class, parsed);

        assertEquals(3L, input.inputFrame().sequence());
        assertEquals(0.0, input.inputFrame().moveX());
        assertEquals(0.0, input.inputFrame().moveY());
        assertEquals(null, input.inputFrame().aimDegrees());
    }
}
