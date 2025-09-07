package it.unibo.agar.model;


import java.io.Serializable;

public abstract class AbstractEntity implements Entity, Serializable {
    private final String id;
    private final double x;
    private final double y;
    private final double mass;
    private final double radius;

    protected AbstractEntity(final String id, final double x, final double y, final double mass) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.mass = mass;
        this.radius = Math.sqrt(mass / Math.PI);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getMass() {
        return mass;
    }

    @Override
    public double getRadius() {
        return radius;
    }
}