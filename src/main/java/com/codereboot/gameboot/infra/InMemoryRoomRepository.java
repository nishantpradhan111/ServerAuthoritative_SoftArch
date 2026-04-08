package com.codereboot.gameboot.infra;

import com.codereboot.gameboot.domain.Room;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryRoomRepository implements RoomRepository {

    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public void save(Room room) {
        rooms.put(room.code(), room);
    }

    @Override
    public Optional<Room> findByCode(String roomCode) {
        return Optional.ofNullable(rooms.get(roomCode.toUpperCase()));
    }

    @Override
    public Collection<Room> findAll() {
        return rooms.values();
    }
}