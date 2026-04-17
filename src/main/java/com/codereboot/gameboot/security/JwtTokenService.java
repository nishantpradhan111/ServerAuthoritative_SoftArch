package com.codereboot.gameboot.security;

import com.codereboot.gameboot.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenService.class);

    private final SecretKey signingKey;
    private final Duration tokenTtl;

    public JwtTokenService(
            @Value("${app.auth.jwt-secret:}") String jwtSecret,
            @Value("${app.auth.token-ttl-minutes:120}") long tokenTtlMinutes
    ) {
        this.signingKey = buildSigningKey(resolveSecret(jwtSecret));
        this.tokenTtl = Duration.ofMinutes(Math.max(tokenTtlMinutes, 5));
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenTtl);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            Date expiration = claims.getExpiration();
            return expiration != null && expiration.toInstant().isAfter(Instant.now());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private SecretKey buildSigningKey(String rawSecret) {
        String trimmed = rawSecret == null ? "" : rawSecret.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("JWT secret must not be empty");
        }

        try {
            byte[] decoded = Decoders.BASE64.decode(trimmed);
            if (decoded.length >= 32) {
                return Keys.hmacShaKeyFor(decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // Not base64-encoded input; treat as plain text.
        }

        byte[] fallback = trimmed.getBytes(StandardCharsets.UTF_8);
        if (fallback.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(fallback);
    }

    private String resolveSecret(String configuredSecret) {
        String trimmed = configuredSecret == null ? "" : configuredSecret.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }

        byte[] secretBytes = new byte[64];
        new SecureRandom().nextBytes(secretBytes);
        String generated = Base64.getEncoder().encodeToString(secretBytes);
        LOGGER.warn(
                "APP_JWT_SECRET is not configured. Using an ephemeral in-memory JWT secret for this process only. " +
                        "Set APP_JWT_SECRET to avoid invalidating tokens on restart."
        );
        return generated;
    }
}
