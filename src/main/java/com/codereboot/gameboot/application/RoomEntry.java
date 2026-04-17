package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.RoomSnapshot;

public record RoomEntry(String roomCode, String token, RoomSnapshot snapshot) {
}
