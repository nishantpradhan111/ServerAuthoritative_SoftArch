package com.codereboot.gameboot.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codereboot.gameboot.application.AuthService;
import com.codereboot.gameboot.domain.User;
import com.codereboot.gameboot.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
@SuppressWarnings("null")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @Test
    void registerReturnsCreatedUserResponse() throws Exception {
        User user = new User("nova_strike", "nova@example.com", "hashed-password");
        ReflectionTestUtils.setField(user, "id", 42L);
        when(authService.register(anyString(), anyString(), anyString())).thenReturn(user);
        when(jwtTokenService.issueToken(user)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{" +
                                "\"username\":\"nova_strike\"," +
                                "\"email\":\"nova@example.com\"," +
                                "\"password\":\"SecurePass123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(42)))
                .andExpect(jsonPath("$.username", is("nova_strike")))
                .andExpect(jsonPath("$.email", is("nova@example.com")))
                .andExpect(jsonPath("$.message", is("Registration successful")))
                .andExpect(jsonPath("$.accessToken", is("jwt-token")));
    }

    @Test
    void loginReturnsOkUserResponse() throws Exception {
        User user = new User("nova_strike", "nova@example.com", "hashed-password");
        ReflectionTestUtils.setField(user, "id", 42L);
        when(authService.authenticate("nova_strike", "SecurePass123")).thenReturn(user);
        when(jwtTokenService.issueToken(user)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{" +
                                "\"username\":\"nova_strike\"," +
                                "\"password\":\"SecurePass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(42)))
                .andExpect(jsonPath("$.username", is("nova_strike")))
                .andExpect(jsonPath("$.email", is("nova@example.com")))
                .andExpect(jsonPath("$.message", is("Login successful")))
                .andExpect(jsonPath("$.accessToken", is("jwt-token")));
    }

    @Test
    void duplicateRegistrationReturnsBadRequestMessage() throws Exception {
        when(authService.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Username already taken"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{" +
                                "\"username\":\"nova_strike\"," +
                                "\"email\":\"nova@example.com\"," +
                                "\"password\":\"SecurePass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("User credentials conflict with an existing account")));
    }
}