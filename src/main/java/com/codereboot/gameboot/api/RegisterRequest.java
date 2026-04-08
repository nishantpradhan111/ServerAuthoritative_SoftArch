package com.codereboot.gameboot.api;

/**
 * Request DTO for user registration.
 */
public record RegisterRequest(
    String username,
    String email,
    String password
) {
}
