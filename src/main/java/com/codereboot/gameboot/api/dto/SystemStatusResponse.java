package com.codereboot.gameboot.api.dto;

public record SystemStatusResponse(
        String status,
        String service,
        String javaVersion,
        long uptimeMs,
        String requestId
) {
}