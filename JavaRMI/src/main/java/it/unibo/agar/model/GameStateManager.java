package it.unibo.agar.model;

public interface GameStateManager {
    World getWorld();
    void setPlayerDirection(final String playerId, final double dx, final double dy);
    void tick();
}
