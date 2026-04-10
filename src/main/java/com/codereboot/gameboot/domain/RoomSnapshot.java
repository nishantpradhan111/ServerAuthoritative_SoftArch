package com.codereboot.gameboot.domain;

import java.util.List;

public record RoomSnapshot(String code, RoomPhase phase, int boardWidth, int boardHeight, long simulationTick,
                           List<PlayerSnapshot> players, String winnerToken, String lastEvent, boolean canStart,
                           List<String> replayPendingTokens) {
}