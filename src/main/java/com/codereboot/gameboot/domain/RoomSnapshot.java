package com.codereboot.gameboot.domain;

import java.util.List;

public record RoomSnapshot(String code, RoomPhase phase, int boardWidth, int boardHeight, List<PlayerSnapshot> players,
                           String winnerToken, String lastEvent, boolean canStart) {
}