package com.codereboot.gameboot.application;

import com.codereboot.gameboot.domain.RoomSnapshot;

public interface RoomEventBroadcaster {

    void broadcast(RoomSnapshot snapshot);
}