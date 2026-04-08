package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.User;
import com.codereboot.gameboot.infra.UserRepository;
import com.codereboot.gameboot.security.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authentication service handling user registration and login.
 * Enforces validation rules and password security.
 */
@Service
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Validation regex patterns
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    private static final int MIN_PASSWORD_LENGTH = 8;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user.
     * Validates username, email, and password uniqueness/format.
     * 
     * @param username desired username (3-20 alphanumeric+underscore)
     * @param email user email (valid format required)
     * @param rawPassword plaintext password (8+ chars, will be hashed)
     * @return newly created User
     * @throws IllegalArgumentException if validation fails or user exists
     */
    public User register(String username, String email, String rawPassword) {
        // Validate inputs
        validateUsername(username);
        validateEmail(email);
        validatePassword(rawPassword);

        // Check uniqueness
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Hash password and persist
        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = new User(username, email, passwordHash);
        return userRepository.save(user);
    }

    /**
     * Authenticate a user by username/password.
     * 
     * @param username username to find
     * @param rawPassword plaintext password to verify
     * @return authenticated User
     * @throws IllegalArgumentException if user not found or password mismatch
     */
    public User authenticate(String username, String rawPassword) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return user;
    }

    /**
     * Validate username format.
     * Allowed: 3-20 alphanumeric and underscore characters.
     */
    private void validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                "Username must be 3-20 characters (alphanumeric and underscore only)"
            );
        }
    }

    /**
     * Validate email format.
     */
    private void validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    /**
     * Validate password strength.
     * Minimum 8 characters.
     */
    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"
            );
        }
    }
}
