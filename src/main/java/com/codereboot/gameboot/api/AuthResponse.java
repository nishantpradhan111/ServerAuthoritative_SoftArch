package com.codereboot.gameboot.api;

/**
 * Response DTO for authentication endpoints.
 * Returned on successful login or registration.
 */
public record AuthResponse(
    Long userId,
    String username,
    String email,
    String message,
    String accessToken
) {
}
