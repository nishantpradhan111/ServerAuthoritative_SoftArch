package com.codereboot.gameboot.security;

/**
 * Abstraction for password encoding/verification.
 * Allows swapping implementations (BCrypt, PBKDF2, Argon2) without changing services.
 */
public interface PasswordEncoder {
    /**
     * Encode a plaintext password into a hash.
     * 
     * @param rawPassword the plaintext password
     * @return the encoded (hashed) password
     */
    String encode(String rawPassword);

    /**
     * Verify a plaintext password against an encoded hash.
     * 
     * @param rawPassword the plaintext password to check
     * @param encodedPassword the stored hash
     * @return true if password matches hash
     */
    boolean matches(String rawPassword, String encodedPassword);
}
