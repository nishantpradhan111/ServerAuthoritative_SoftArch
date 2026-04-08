package com.codereboot.gameboot.infra;

import com.codereboot.gameboot.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA Repository for User persistence.
 * All queries use parameterized SQL, preventing SQL injection automatically.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Find user by username (case-sensitive).
     * Uses parameterized query: SELECT * FROM users WHERE username = ?
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email (case-sensitive).
     * Uses parameterized query: SELECT * FROM users WHERE email = ?
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if username already exists.
     * Uses parameterized query: SELECT COUNT(*) FROM users WHERE username = ?
     */
    boolean existsByUsername(String username);

    /**
     * Check if email already exists.
     * Uses parameterized query: SELECT COUNT(*) FROM users WHERE email = ?
     */
    boolean existsByEmail(String email);
}
