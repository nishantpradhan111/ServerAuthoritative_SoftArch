package com.codereboot.gameboot.api;

/**
 * Request DTO for user login.
 */
public record LoginRequest(
    String username,
    String password
) {
}
