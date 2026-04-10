package com.codereboot.gameboot.transport;

import com.codereboot.gameboot.domain.Direction;
import com.codereboot.gameboot.domain.GameInputFrame;

interface GameSocketCommand {

    String type();

    record Subscribe(String roomCode, String token) implements GameSocketCommand {
        @Override
        public String type() {
            return "subscribe";
        }
    }

    record Ready() implements GameSocketCommand {
        @Override
        public String type() {
            return "ready";
        }
    }

    record Move(Direction direction) implements GameSocketCommand {
        @Override
        public String type() {
            return "move";
        }
    }

    record Fire() implements GameSocketCommand {
        @Override
        public String type() {
            return "fire";
        }
    }

    record Input(GameInputFrame inputFrame) implements GameSocketCommand {
        @Override
        public String type() {
            return "input";
        }
    }

    record Sync() implements GameSocketCommand {
        @Override
        public String type() {
            return "sync";
        }
    }

    record HitClaim(long shotId, long snapshotTick) implements GameSocketCommand {
        @Override
        public String type() {
            return "hitClaim";
        }
    }
}