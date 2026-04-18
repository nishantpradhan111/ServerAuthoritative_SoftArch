package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.Room;
import com.codereboot.gameboot.domain.RoomPhase;
import com.codereboot.gameboot.infra.RoomRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Counter simulationTickCounter;
    private final Counter completedRoomCleanupCounter;
    private final Timer simulationTickDuration;

        @Autowired
        public GameSimulationService(
            RoomRepository roomRepository,
            RoomEventBroadcaster eventBroadcaster,
            AppClock clock,
            MeterRegistry meterRegistry
    ) {
        this.roomRepository = roomRepository;
        this.eventBroadcaster = eventBroadcaster;
        this.clock = clock;
        this.simulationTickCounter = Counter.builder("codereboot.simulation.ticks.total")
                .description("Total simulation ticks executed")
                .register(meterRegistry);
        this.completedRoomCleanupCounter = Counter.builder("codereboot.rooms.cleanup.completed.total")
                .description("Total completed rooms removed after TTL")
                .register(meterRegistry);
        this.simulationTickDuration = Timer.builder("codereboot.simulation.tick.duration")
                .description("Simulation tick execution duration")
                .publishPercentileHistogram()
                .register(meterRegistry);

        Gauge.builder("codereboot.rooms.active", roomRepository, repository -> repository.findAll().size())
                .description("Current active room count")
                .register(meterRegistry);
    }

    public GameSimulationService(
            RoomRepository roomRepository,
            RoomEventBroadcaster eventBroadcaster,
            AppClock clock
    ) {
        this(roomRepository, eventBroadcaster, clock, Metrics.globalRegistry);
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    public void tickRooms() {
        long startedAtNanos = System.nanoTime();
        simulationTickCounter.increment();
        long now = clock.nowMillis();
        try {
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
                    completedRoomCleanupCounter.increment();
                }
            }
        } finally {
            simulationTickDuration.record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
        }
    }
}