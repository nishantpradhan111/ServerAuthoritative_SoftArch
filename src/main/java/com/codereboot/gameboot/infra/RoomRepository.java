package com.codereboot.gameboot.infra;

import com.codereboot.gameboot.domain.Room;
import java.util.Collection;
import java.util.Optional;

public interface RoomRepository {

    void save(Room room);

    Optional<Room> findByCode(String roomCode);

    Collection<Room> findAll();
}