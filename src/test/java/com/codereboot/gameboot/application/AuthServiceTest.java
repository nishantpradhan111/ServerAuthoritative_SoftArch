package com.codereboot.gameboot.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codereboot.gameboot.domain.User;
import com.codereboot.gameboot.infra.UserRepository;
import com.codereboot.gameboot.security.PasswordEncoder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

@SuppressWarnings("null")
class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void registerHashesPasswordAndSavesUser() {
        when(passwordEncoder.encode("SecurePass123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = authService.register("nova_strike", "nova@example.com", "SecurePass123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("nova_strike", captor.getValue().getUsername());
        assertEquals("nova@example.com", captor.getValue().getEmail());
        assertEquals("hashed-password", captor.getValue().getPasswordHash());
        assertEquals("nova_strike", user.getUsername());
        assertEquals("nova@example.com", user.getEmail());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(passwordEncoder.encode("SecurePass123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(userRepository.existsByUsername("nova_strike")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register("nova_strike", "nova@example.com", "SecurePass123")
        );

        assertEquals("Username already taken", exception.getMessage());
    }

    @Test
    void registerTrimsUsernameAndEmailBeforePersisting() {
        when(passwordEncoder.encode("SecurePass123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = authService.register("  nova_strike  ", "  nova@example.com  ", "SecurePass123");

        assertEquals("nova_strike", user.getUsername());
        assertEquals("nova@example.com", user.getEmail());
    }

    @Test
    void authenticateReturnsUserForValidCredentials() {
        User user = new User("nova_strike", "nova@example.com", "hashed-password");
        when(userRepository.findByUsername("nova_strike")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecurePass123", "hashed-password")).thenReturn(true);

        User result = authService.authenticate("nova_strike", "SecurePass123");

        assertEquals(user, result);
    }

    @Test
    void authenticateRejectsWrongPassword() {
        User user = new User("nova_strike", "nova@example.com", "hashed-password");
        when(userRepository.findByUsername("nova_strike")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.authenticate("nova_strike", "wrong-password")
        );

        assertEquals("Invalid username or password", exception.getMessage());
    }

    @Test
    void registerTranslatesDataIntegrityViolationToDuplicateMessage() {
        when(userRepository.existsByUsername("nova_strike")).thenReturn(true);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register("nova_strike", "nova@example.com", "SecurePass123")
        );

        assertEquals("Username already taken", exception.getMessage());
    }
}