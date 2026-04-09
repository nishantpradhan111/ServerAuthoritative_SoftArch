package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.Room;
import com.codereboot.gameboot.infra.RoomRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Advances active rooms on a fixed server tick.
 * This is the first step toward a server-authoritative FPS simulation.
 */
@Service
public class GameSimulationService {
    private static final long TICK_INTERVAL_MS = 33L;

    private final RoomRepository roomRepository;
    private final RoomBroadcastGateway broadcastGateway;

    public GameSimulationService(RoomRepository roomRepository, RoomBroadcastGateway broadcastGateway) {
        this.roomRepository = roomRepository;
        this.broadcastGateway = broadcastGateway;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    public void tickRooms() {
        for (Room room : roomRepository.findAll()) {
            if (room.tick(Room.SIMULATION_STEP_SECONDS)) {
                broadcastGateway.broadcast(room.snapshot());
            }
        }
    }
}