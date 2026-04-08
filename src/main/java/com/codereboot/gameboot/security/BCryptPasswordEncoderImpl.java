package com.codereboot.gameboot.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt password encoder implementation with strength 12.
 * Provides automatic salt generation and timing attack resistance.
 * Strength 12 offers good security/performance balance (~150ms encode on modern CPU).
 */
@Component
public class BCryptPasswordEncoderImpl implements PasswordEncoder {
    private final BCryptPasswordEncoder delegate;

    public BCryptPasswordEncoderImpl() {
        // Strength 12: ~150ms on modern CPU, strong against GPU brute-force
        this.delegate = new BCryptPasswordEncoder(12);
    }

    @Override
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null) {
            return false;
        }
        return delegate.matches(rawPassword, encodedPassword);
    }
}
