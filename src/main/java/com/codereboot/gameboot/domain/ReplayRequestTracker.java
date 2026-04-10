package com.codereboot.gameboot.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReplayRequestTracker {

    private final long timeoutMs;
    private final Map<String, Long> requestsByToken = new LinkedHashMap<>();

    ReplayRequestTracker(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    void clear() {
        requestsByToken.clear();
    }

    void remove(String token) {
        requestsByToken.remove(token);
    }

    void record(String token, long nowMs) {
        requestsByToken.put(token, nowMs);
    }

    boolean cleanupExpired(long nowMs) {
        int before = requestsByToken.size();
        requestsByToken.entrySet().removeIf(entry -> (nowMs - entry.getValue()) > timeoutMs);
        return before > 0 && requestsByToken.isEmpty();
    }

    boolean isReady(Collection<String> requiredTokens) {
        if (requiredTokens.isEmpty()) {
            return false;
        }

        for (String token : requiredTokens) {
            if (!requestsByToken.containsKey(token)) {
                return false;
            }
        }
        return true;
    }

    List<String> pendingTokens() {
        return new ArrayList<>(requestsByToken.keySet());
    }
}
