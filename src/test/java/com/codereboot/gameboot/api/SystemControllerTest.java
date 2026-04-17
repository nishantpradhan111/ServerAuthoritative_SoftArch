package com.codereboot.gameboot.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.codereboot.gameboot.security.JwtAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("null")
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void healthReturnsRuntimeSnapshot() throws Exception {
        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("CodeReboot")))
                .andExpect(jsonPath("$.javaVersion", notNullValue()))
                .andExpect(jsonPath("$.uptimeMs", notNullValue()));
    }
}