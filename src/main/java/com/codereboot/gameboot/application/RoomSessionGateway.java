package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.RoomSnapshot;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketSession;

public interface RoomSessionGateway {

    void register(String roomCode, String token, @NonNull WebSocketSession session);

    void unregister(WebSocketSession session);

    void sendSnapshot(@NonNull WebSocketSession session, RoomSnapshot snapshot);

    void sendError(@NonNull WebSocketSession session, String message);

    void sendReplayRedirect(String roomCode, String token, String newRoomCode, String newToken);

    void sendRoomReturn(String roomCode, String message);
}