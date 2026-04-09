package com.codereboot.gameboot.domain;

public class Player {

    private final String token;
    private final String name;
    private double positionX;
    private double positionY;
    private double velocityX;
    private double velocityY;
    private double aimDegrees;
    private Direction facing = Direction.RIGHT;
    private int health = Room.STARTING_HEALTH;
    private int ammo = Room.STARTING_AMMO;
    private boolean ready;
    private long lastInputSequence;

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
        return (int) Math.round(positionX);
    }

    public int y() {
        return (int) Math.round(positionY);
    }

    public double positionX() {
        return positionX;
    }

    public double positionY() {
        return positionY;
    }

    public double velocityX() {
        return velocityX;
    }

    public double velocityY() {
        return velocityY;
    }

    public double aimDegrees() {
        return aimDegrees;
    }

    public Direction facing() {
        return facing;
    }

    public int health() {
        return health;
    }

    public int ammo() {
        return ammo;
    }

    public long lastInputSequence() {
        return lastInputSequence;
    }

    public boolean ready() {
        return ready;
    }

    public void ready(boolean ready) {
        this.ready = ready;
    }

    public void place(int x, int y, Direction facing) {
        place((double) x, (double) y, facing);
    }

    public void place(double x, double y, Direction facing) {
        this.positionX = x;
        this.positionY = y;
        face(facing);
        stop();
    }

    public void moveTo(int x, int y) {
        moveTo((double) x, (double) y);
    }

    public void moveTo(double x, double y) {
        this.positionX = x;
        this.positionY = y;
    }

    public void translate(double deltaX, double deltaY) {
        this.positionX += deltaX;
        this.positionY += deltaY;
    }

    public void face(Direction direction) {
        this.facing = direction;
        this.aimDegrees = direction.aimDegrees();
    }

    public void face(double degrees) {
        this.aimDegrees = normalizeDegrees(degrees);
        this.facing = Direction.fromDegrees(this.aimDegrees);
    }

    public void setVelocity(double velocityX, double velocityY) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    public void stop() {
        this.velocityX = 0.0;
        this.velocityY = 0.0;
    }

    public boolean advance(double deltaSeconds) {
        if (velocityX == 0.0 && velocityY == 0.0) {
            return false;
        }
        this.positionX += velocityX * deltaSeconds;
        this.positionY += velocityY * deltaSeconds;
        return true;
    }

    public void setLastInputSequence(long sequence) {
        this.lastInputSequence = sequence;
    }

    public void resetForMatch(int x, int y, Direction direction) {
        this.positionX = x;
        this.positionY = y;
        this.velocityX = 0.0;
        this.velocityY = 0.0;
        face(direction);
        this.health = Room.STARTING_HEALTH;
        this.ammo = Room.STARTING_AMMO;
        this.lastInputSequence = 0L;
    }

    public void damage(int amount) {
        this.health = Math.max(0, health - amount);
    }

    public boolean defeated() {
        return health <= 0;
    }

    private double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0 ? normalized + 360.0 : normalized;
    }
}