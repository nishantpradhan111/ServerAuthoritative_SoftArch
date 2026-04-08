package com.codereboot.gameboot.api;

import com.codereboot.gameboot.application.AuthService;
import com.codereboot.gameboot.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST API endpoints.
 * Handles user registration and login.
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register
     * Register a new user.
     * 
     * @param request RegisterRequest with username, email, password
     * @return AuthResponse with user details on success
     * @throws IllegalArgumentException if validation fails or user exists (caught by ApiExceptionHandler → 400)
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        User user = authService.register(request.username(), request.email(), request.password());
        AuthResponse response = new AuthResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            "Registration successful"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login
     * Authenticate a user.
     * 
     * @param request LoginRequest with username, password
     * @return AuthResponse with user details on success
     * @throws IllegalArgumentException if credentials invalid (caught by ApiExceptionHandler → 400)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        User user = authService.authenticate(request.username(), request.password());
        AuthResponse response = new AuthResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            "Login successful"
        );
        return ResponseEntity.ok(response);
    }
}
