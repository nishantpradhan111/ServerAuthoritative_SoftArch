package com.codereboot.gameboot.api;

import com.codereboot.gameboot.api.dto.RoomEntryResponse;
import com.codereboot.gameboot.application.RoomService;
import com.codereboot.gameboot.domain.RoomSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public RoomEntryResponse createRoom(@RequestBody CreateRoomRequest request) {
        return roomService.createRoom(request.name());
    }

    @PostMapping("/join")
    public RoomEntryResponse joinRoom(@RequestBody JoinRoomRequest request) {
        return roomService.joinRoom(request.roomCode(), request.name());
    }

    @GetMapping("/{roomCode}")
    public RoomSnapshot snapshot(@PathVariable String roomCode, @RequestParam String token) {
        return roomService.snapshot(roomCode, token);
    }
}