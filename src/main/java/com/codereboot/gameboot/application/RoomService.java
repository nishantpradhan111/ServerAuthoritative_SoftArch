package com.codereboot.gameboot.application;

import com.codereboot.gameboot.api.dto.RoomEntryResponse;
import com.codereboot.gameboot.domain.Direction;
import com.codereboot.gameboot.domain.GameInputFrame;
import com.codereboot.gameboot.domain.Room;
import com.codereboot.gameboot.domain.RoomPhase;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.codereboot.gameboot.infra.RoomRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private static final String ROOM_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final RoomRepository roomRepository;
    private final RoomEventBroadcaster eventBroadcaster;

    public record ReplayRedirect(String oldToken, String roomCode, String token) {
    }

    public RoomService(RoomRepository roomRepository, RoomEventBroadcaster eventBroadcaster) {
        this.roomRepository = roomRepository;
        this.eventBroadcaster = eventBroadcaster;
    }

    public RoomEntryResponse createRoom(String playerName) {
        String roomCode = generateRoomCode();
        Room room = new Room(roomCode);
        roomRepository.save(room);
        String token = room.addPlayer(normalizeName(playerName));
        RoomSnapshot snapshot = room.snapshot();
        eventBroadcaster.broadcast(snapshot);
        return new RoomEntryResponse(roomCode, token, snapshot);
    }

    public RoomEntryResponse joinRoom(String roomCode, String playerName) {
        Room room = getRoom(roomCode);
        String token = room.addPlayer(normalizeName(playerName));
        RoomSnapshot snapshot = room.snapshot();
        eventBroadcaster.broadcast(snapshot);
        return new RoomEntryResponse(room.code(), token, snapshot);
    }

    public RoomSnapshot snapshot(String roomCode, String token) {
        Room room = getRoom(roomCode);
        room.requirePlayer(token);
        return room.snapshot();
    }

    public void leaveRoom(String roomCode, String token) {
        Room room = getRoom(roomCode);
        room.removePlayer(token);

        if (room.empty()) {
            roomRepository.deleteByCode(room.code());
            return;
        }

        eventBroadcaster.broadcast(room.snapshot());
    }

    public void setReady(String roomCode, String token, boolean ready) {
        Room room = getRoom(roomCode);
        room.setReady(token, ready);
        eventBroadcaster.broadcast(room.snapshot());
    }

    public void move(String roomCode, String token, Direction direction) {
        Room room = getRoom(roomCode);
        room.move(token, direction);
        eventBroadcaster.broadcast(room.snapshot());
    }

    public void fire(String roomCode, String token) {
        Room room = getRoom(roomCode);
        room.fire(token);
        eventBroadcaster.broadcast(room.snapshot());
    }

    public void applyInput(String roomCode, String token, GameInputFrame input) {
        Room room = getRoom(roomCode);
        room.applyInput(token, input);
        eventBroadcaster.broadcast(room.snapshot());
    }

    public void claimHit(String roomCode, String reporterToken, long shotId, long snapshotTick) {
        Room room = getRoom(roomCode);
        room.claimHit(reporterToken, shotId, snapshotTick);
        eventBroadcaster.broadcast(room.snapshot());
    }

    public List<ReplayRedirect> requestReplay(String roomCode, String token) {
        Room room = getRoom(roomCode);
        List<Room.ReplayParticipant> participants = room.requestReplay(token);
        eventBroadcaster.broadcast(room.snapshot());
        if (participants.isEmpty()) {
            return List.of();
        }

        String newRoomCode = generateRoomCode();
        Room replayRoom = new Room(newRoomCode);
        roomRepository.save(replayRoom);

        List<ReplayRedirect> redirects = new ArrayList<>();
        for (Room.ReplayParticipant participant : participants) {
            String newToken = replayRoom.addPlayer(participant.name());
            redirects.add(new ReplayRedirect(participant.token(), newRoomCode, newToken));
        }

        eventBroadcaster.broadcast(replayRoom.snapshot());
        return redirects;
    }

    public String requestReturnToRoom(String roomCode, String token) {
        Room room = getRoom(roomCode);
        if (room.phase() != RoomPhase.COMPLETE) {
            throw new IllegalStateException("Return-to-room broadcast is only available after match completion");
        }

        return room.requirePlayer(token).name() + " returned to room";
    }

    private Room getRoom(String roomCode) {
        return roomRepository.findByCode(normalizeRoomCode(roomCode))
                .orElseThrow(() -> new NoSuchElementException("Room not found"));
    }

    private String normalizeName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "Guest";
        }
        return trimmed.substring(0, Math.min(trimmed.length(), 20));
    }

    private String normalizeRoomCode(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String generateRoomCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(5);
            for (int index = 0; index < 5; index++) {
                int alphabetIndex = ThreadLocalRandom.current().nextInt(ROOM_ALPHABET.length());
                builder.append(ROOM_ALPHABET.charAt(alphabetIndex));
            }
            code = builder.toString();
        } while (roomRepository.findByCode(code).isPresent());
        return code;
    }
}