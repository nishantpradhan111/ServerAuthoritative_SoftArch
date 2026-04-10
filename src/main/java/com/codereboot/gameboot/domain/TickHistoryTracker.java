package com.codereboot.gameboot.domain;

import java.util.LinkedHashMap;
import java.util.Map;

final class TickHistoryTracker {

    private final int maxHistory;
    private final LinkedHashMap<Long, Map<String, PlayerTickState>> historyByTick = new LinkedHashMap<>();

    TickHistoryTracker(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    void clear() {
        historyByTick.clear();
    }

    void record(long simulationTick, Iterable<Player> players) {
        Map<String, PlayerTickState> snapshot = new LinkedHashMap<>();
        for (Player player : players) {
            snapshot.put(
                    player.token(),
                    new PlayerTickState(player.positionX(), player.positionY(), player.aimDegrees())
            );
        }

        historyByTick.put(simulationTick, snapshot);
        while (historyByTick.size() > maxHistory) {
            Long oldestTick = historyByTick.keySet().iterator().next();
            historyByTick.remove(oldestTick);
        }
    }

    Map<String, PlayerTickState> snapshotAt(long simulationTick) {
        return historyByTick.get(simulationTick);
    }
}
