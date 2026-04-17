package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.Direction;
import com.codereboot.gameboot.domain.GameInputFrame;
import com.codereboot.gameboot.domain.Player;
import com.codereboot.gameboot.domain.Room;
import com.codereboot.gameboot.domain.RoomPhase;
import com.codereboot.gameboot.domain.RoomSnapshot;
import com.codereboot.gameboot.infra.RoomRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomService.class);

    private static final String ROOM_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 5;
    private static final int MAX_ROOM_CODE_ATTEMPTS = 100;

    private final RoomRepository roomRepository;
    private final RoomEventBroadcaster eventBroadcaster;
    private final AppClock clock;

    public record ReplayRedirect(String oldToken, String roomCode, String token) {
    }

    private record AuthorizedRoom(Room room, Player player) {
    }

    public RoomService(RoomRepository roomRepository, RoomEventBroadcaster eventBroadcaster, AppClock clock) {
        this.roomRepository = roomRepository;
        this.eventBroadcaster = eventBroadcaster;
        this.clock = clock;
    }

    public RoomEntry createRoom(String authenticatedUsername) {
        String roomCode = generateRoomCode();
        Room room = new Room(roomCode, clock::nowMillis);
        roomRepository.save(room);
        String token = room.addPlayer(normalizeName(authenticatedUsername));
        RoomSnapshot snapshot = room.snapshot();
        safeBroadcast(snapshot);
        return new RoomEntry(roomCode, token, snapshot);
    }

    public RoomEntry joinRoom(String roomCode, String authenticatedUsername) {
        Room room = getRoom(roomCode);
        String token = room.addPlayer(normalizeName(authenticatedUsername));
        RoomSnapshot snapshot = room.snapshot();
        safeBroadcast(snapshot);
        return new RoomEntry(room.code(), token, snapshot);
    }

    public RoomSnapshot snapshot(String roomCode, String token, String authenticatedUsername) {
        return authorizedRoom(roomCode, token, authenticatedUsername).room().snapshot();
    }

    public void leaveRoom(String roomCode, String token, String authenticatedUsername) {
        Room room = authorizedRoom(roomCode, token, authenticatedUsername).room();
        room.removePlayer(token);

        if (room.empty()) {
            roomRepository.deleteByCode(room.code());
            return;
        }

        safeBroadcast(room.snapshot());
    }

    public void setReady(String roomCode, String token, String authenticatedUsername, boolean ready) {
        mutateAndBroadcast(roomCode, token, authenticatedUsername, room -> room.setReady(token, ready));
    }

    public void move(String roomCode, String token, String authenticatedUsername, Direction direction) {
        mutateAndBroadcast(roomCode, token, authenticatedUsername, room -> room.move(token, direction));
    }

    public void fire(String roomCode, String token, String authenticatedUsername) {
        mutateAndBroadcast(roomCode, token, authenticatedUsername, room -> room.fire(token));
    }

    public void applyInput(String roomCode, String token, String authenticatedUsername, GameInputFrame input) {
        mutateAndBroadcast(roomCode, token, authenticatedUsername, room -> room.applyInput(token, input));
    }

    public void claimHit(
            String roomCode,
            String reporterToken,
            String authenticatedUsername,
            long shotId,
            long snapshotTick
    ) {
        mutateAndBroadcast(
                roomCode,
                reporterToken,
                authenticatedUsername,
                room -> room.claimHit(reporterToken, shotId, snapshotTick)
        );
    }

    public List<ReplayRedirect> requestReplay(String roomCode, String token, String authenticatedUsername) {
        Room room = authorizedRoom(roomCode, token, authenticatedUsername).room();
        List<Room.ReplayParticipant> participants = room.requestReplay(token);
        safeBroadcast(room.snapshot());
        if (participants.isEmpty()) {
            return List.of();
        }

        ReplayRoom replayRoom = createReplayRoom(participants);
        safeBroadcast(replayRoom.snapshot());
        return replayRoom.redirects();
    }

    public String requestReturnToRoom(String roomCode, String token, String authenticatedUsername) {
        AuthorizedRoom authorizedRoom = authorizedRoom(roomCode, token, authenticatedUsername);
        Room room = authorizedRoom.room();
        if (room.phase() != RoomPhase.COMPLETE) {
            throw new IllegalStateException("Return-to-room broadcast is only available after match completion");
        }

        return authorizedRoom.player().name() + " returned to room";
    }

    private Room getRoom(String roomCode) {
        return roomRepository.findByCode(normalizeRoomCode(roomCode))
                .orElseThrow(() -> new NoSuchElementException("Room not found"));
    }

    private void mutateAndBroadcast(
            String roomCode,
            String token,
            String authenticatedUsername,
            Consumer<Room> mutation
    ) {
        Room room = authorizedRoom(roomCode, token, authenticatedUsername).room();
        mutation.accept(room);
        safeBroadcast(room.snapshot());
    }

    private AuthorizedRoom authorizedRoom(String roomCode, String token, String authenticatedUsername) {
        Room room = getRoom(roomCode);
        return new AuthorizedRoom(room, requireAuthorizedPlayer(room, token, authenticatedUsername));
    }

    private ReplayRoom createReplayRoom(List<Room.ReplayParticipant> participants) {
        String roomCode = generateRoomCode();
        Room room = new Room(roomCode, clock::nowMillis);
        roomRepository.save(room);

        List<ReplayRedirect> redirects = new ArrayList<>();
        for (Room.ReplayParticipant participant : participants) {
            String newToken = room.addPlayer(participant.name());
            redirects.add(new ReplayRedirect(participant.token(), roomCode, newToken));
        }

        return new ReplayRoom(room.snapshot(), redirects);
    }

    private Player requireAuthorizedPlayer(Room room, String token, String authenticatedUsername) {
        Player player = room.requirePlayer(token);
        String normalizedUsername = normalizeName(authenticatedUsername);
        if (!player.name().equals(normalizedUsername)) {
            throw new AccessDeniedException("Player token is not bound to authenticated user");
        }
        return player;
    }

    private void safeBroadcast(RoomSnapshot snapshot) {
        BroadcastResult result = eventBroadcaster.broadcast(snapshot);
        if (result.failed() > 0) {
            LOGGER.warn(
                    "Snapshot broadcast completed with failures: roomCode={}, attempted={}, delivered={}, failed={}",
                    snapshot.code(),
                    result.attempted(),
                    result.delivered(),
                    result.failed()
            );
        }
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

    private record ReplayRoom(RoomSnapshot snapshot, List<ReplayRedirect> redirects) {
    }

    private String generateRoomCode() {
        for (int attempt = 0; attempt < MAX_ROOM_CODE_ATTEMPTS; attempt++) {
            StringBuilder builder = new StringBuilder(ROOM_CODE_LENGTH);
            for (int index = 0; index < ROOM_CODE_LENGTH; index++) {
                int alphabetIndex = ThreadLocalRandom.current().nextInt(ROOM_ALPHABET.length());
                builder.append(ROOM_ALPHABET.charAt(alphabetIndex));
            }
            String code = builder.toString();
            if (roomRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }

        throw new IllegalStateException("Unable to allocate a unique room code");
    }
}