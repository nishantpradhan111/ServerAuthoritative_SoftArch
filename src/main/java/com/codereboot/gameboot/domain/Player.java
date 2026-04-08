package com.codereboot.gameboot.domain;

public class Player {

    private final String token;
    private final String name;
    private int x;
    private int y;
    private Direction facing = Direction.RIGHT;
    private int health = Room.STARTING_HEALTH;
    private boolean ready;

    public Player(String token, String name) {
        this.token = token;
        this.name = name;
    }

    public String token() {
        return token;
    }

    public String name() {
        return name;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public Direction facing() {
        return facing;
    }

    public int health() {
        return health;
    }

    public boolean ready() {
        return ready;
    }

    public void ready(boolean ready) {
        this.ready = ready;
    }

    public void place(int x, int y, Direction facing) {
        this.x = x;
        this.y = y;
        this.facing = facing;
    }

    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void face(Direction direction) {
        this.facing = direction;
    }

    public void resetForMatch(int x, int y, Direction direction) {
        this.x = x;
        this.y = y;
        this.facing = direction;
        this.health = Room.STARTING_HEALTH;
    }

    public void damage(int amount) {
        this.health = Math.max(0, health - amount);
    }

    public boolean defeated() {
        return health <= 0;
    }
}