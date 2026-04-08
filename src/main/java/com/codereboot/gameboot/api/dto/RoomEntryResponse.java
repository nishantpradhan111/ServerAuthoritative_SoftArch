package com.codereboot.gameboot.api.dto;

import com.codereboot.gameboot.domain.RoomSnapshot;

public record RoomEntryResponse(String roomCode, String token, RoomSnapshot snapshot) {
}