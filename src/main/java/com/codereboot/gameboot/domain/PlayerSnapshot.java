package com.codereboot.gameboot.domain;

public record PlayerSnapshot(String token, String name, int x, int y, Direction facing, int health, boolean ready,
                             boolean host) {
}