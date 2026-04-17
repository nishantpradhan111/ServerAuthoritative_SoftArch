package com.codereboot.gameboot.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codereboot.gameboot.api.dto.RoomEntryResponse;
import com.codereboot.gameboot.application.RoomService;
import com.codereboot.gameboot.domain.RoomPhase;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.codereboot.gameboot.security.JwtTokenService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomController.class)
@SuppressWarnings("null")
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomService roomService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @Test
    @WithMockUser(username = "Ada")
    void createRoomReturnsEntryResponse() throws Exception {
        RoomSnapshot snapshot = sampleSnapshot("ABCDE");
        RoomEntryResponse response = new RoomEntryResponse("ABCDE", "token-a", snapshot);
        when(roomService.createRoom("Ada")).thenReturn(response);

        mockMvc.perform(post("/api/rooms")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode", is("ABCDE")))
                .andExpect(jsonPath("$.token", is("token-a")))
                .andExpect(jsonPath("$.snapshot.code", is("ABCDE")));
    }

    @Test
    @WithMockUser(username = "Ada")
    void joinRoomReturnsEntryResponse() throws Exception {
        RoomSnapshot snapshot = sampleSnapshot("ABCDE");
        RoomEntryResponse response = new RoomEntryResponse("ABCDE", "token-b", snapshot);
        when(roomService.joinRoom("ABCDE", "Ada")).thenReturn(response);

        mockMvc.perform(post("/api/rooms/join")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"roomCode\":\"ABCDE\",\"name\":\"Lin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode", is("ABCDE")))
                .andExpect(jsonPath("$.token", is("token-b")));
    }

    @Test
    @WithMockUser(username = "Ada")
    void snapshotReturnsRoomSnapshot() throws Exception {
        RoomSnapshot snapshot = sampleSnapshot("ABCDE");
        when(roomService.snapshot("ABCDE", "token-a", "Ada")).thenReturn(snapshot);

        mockMvc.perform(get("/api/rooms/ABCDE")
                        .header("X-Player-Token", "token-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("ABCDE")))
                .andExpect(jsonPath("$.phase", is("LOBBY")))
                .andExpect(jsonPath("$.boardWidth", is(15)));
    }

    @Test
    @WithMockUser(username = "Ada")
    void leaveRoomCallsService() throws Exception {
        mockMvc.perform(post("/api/rooms/ABCDE/leave")
                .with(csrf())
                        .header("X-Player-Token", "token-a"))
                .andExpect(status().isOk());

        verify(roomService).leaveRoom("ABCDE", "token-a", "Ada");
    }

    private RoomSnapshot sampleSnapshot(String roomCode) {
        return new RoomSnapshot(
                roomCode,
                RoomPhase.LOBBY,
                15,
                10,
                0L,
                List.of(),
                null,
                "room-created",
                true,
                List.of()
        );
    }
}
