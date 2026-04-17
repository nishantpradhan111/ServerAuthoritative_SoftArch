package com.codereboot.gameboot.api;

import com.codereboot.gameboot.api.dto.RoomEntryResponse;
import com.codereboot.gameboot.application.RoomEntry;
import com.codereboot.gameboot.application.RoomService;
import com.codereboot.gameboot.domain.RoomSnapshot;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public RoomEntryResponse createRoom(Authentication authentication) {
        RoomEntry roomEntry = roomService.createRoom(authentication.getName());
        return new RoomEntryResponse(roomEntry.roomCode(), roomEntry.token(), roomEntry.snapshot());
    }

    @PostMapping("/join")
    public RoomEntryResponse joinRoom(@Valid @RequestBody JoinRoomRequest request, Authentication authentication) {
        RoomEntry roomEntry = roomService.joinRoom(request.roomCode(), authentication.getName());
        return new RoomEntryResponse(roomEntry.roomCode(), roomEntry.token(), roomEntry.snapshot());
    }

    @GetMapping("/{roomCode}")
    public RoomSnapshot snapshot(
            @PathVariable String roomCode,
            @RequestHeader("X-Player-Token") String playerToken,
            Authentication authentication
    ) {
        return roomService.snapshot(roomCode, playerToken, authentication.getName());
    }

    @PostMapping("/{roomCode}/leave")
    public void leaveRoom(
            @PathVariable String roomCode,
            @RequestHeader("X-Player-Token") String playerToken,
            Authentication authentication
    ) {
        roomService.leaveRoom(roomCode, playerToken, authentication.getName());
    }
}