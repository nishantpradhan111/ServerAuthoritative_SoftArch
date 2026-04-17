package com.codereboot.gameboot.application;

public record BroadcastResult(int attempted, int delivered, int failed) {

    public static BroadcastResult none() {
        return new BroadcastResult(0, 0, 0);
    }
}