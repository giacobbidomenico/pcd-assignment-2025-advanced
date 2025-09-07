package it.unibo.agar.model;

public class Player extends AbstractEntity {
    public Player(final String id, final double x, final double y, final double mass) {
        super(id, x, y, mass);
    }


    public Player grow(Entity entity) {
        return new Player(getId(), getX(), getY(), getMass() + entity.getMass());
    }

    public Player moveTo(double newX, double newY) {
        return new Player(getId(), newX, newY, getMass());
    }
}
