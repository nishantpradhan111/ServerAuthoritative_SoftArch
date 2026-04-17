package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.Room;
import com.codereboot.gameboot.domain.RoomPhase;
import com.codereboot.gameboot.infra.RoomRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Advances active rooms on a fixed server tick.
 * This is the first step toward a server-authoritative FPS simulation.
 */
@Service
public class GameSimulationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameSimulationService.class);
    private static final long TICK_INTERVAL_MS = 33L;
    private static final long COMPLETED_ROOM_TTL_MS = 30_000L;

    private final RoomRepository roomRepository;
    private final RoomEventBroadcaster eventBroadcaster;
    private final AppClock clock;
    private final Map<String, Long> completedSinceByRoom = new ConcurrentHashMap<>();

    public GameSimulationService(
            RoomRepository roomRepository,
            RoomEventBroadcaster eventBroadcaster,
            AppClock clock
    ) {
        this.roomRepository = roomRepository;
        this.eventBroadcaster = eventBroadcaster;
        this.clock = clock;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    public void tickRooms() {
        long now = clock.nowMillis();
        for (Room room : roomRepository.findAll()) {
            if (room.tick(Room.SIMULATION_STEP_SECONDS)) {
                BroadcastResult result = eventBroadcaster.broadcast(room.snapshot());
                if (result.failed() > 0) {
                    LOGGER.warn(
                            "Tick broadcast completed with failures: roomCode={}, attempted={}, delivered={}, failed={}",
                            room.code(),
                            result.attempted(),
                            result.delivered(),
                            result.failed()
                    );
                }
            }

            if (room.phase() != RoomPhase.COMPLETE) {
                completedSinceByRoom.remove(room.code());
                continue;
            }

            completedSinceByRoom.putIfAbsent(room.code(), now);
            long completedSince = completedSinceByRoom.getOrDefault(room.code(), now);
            if ((now - completedSince) >= COMPLETED_ROOM_TTL_MS) {
                roomRepository.deleteByCode(room.code());
                completedSinceByRoom.remove(room.code());
            }
        }
    }
}