package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.RoomSnapshot;
import org.springframework.web.socket.WebSocketSession;

public interface RoomBroadcastGateway {

    void register(String roomCode, String token, WebSocketSession session);

    void unregister(WebSocketSession session);

    void broadcast(RoomSnapshot snapshot);

    void sendSnapshot(WebSocketSession session, RoomSnapshot snapshot);

    void sendError(WebSocketSession session, String message);
}