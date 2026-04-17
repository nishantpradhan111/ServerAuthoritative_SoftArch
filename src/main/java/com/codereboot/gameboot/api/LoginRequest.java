package com.codereboot.gameboot.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for user login.
 */
public record LoginRequest(
    @NotBlank
    @Size(min = 3, max = 20)
    String username,

    @NotBlank
    @Size(min = 8, max = 120)
    String password
) {
}
