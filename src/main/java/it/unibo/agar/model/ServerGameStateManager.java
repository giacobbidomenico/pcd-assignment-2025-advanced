package it.unibo.agar.model;

import java.util.List;
import java.util.stream.Collectors;

public class ServerGameStateManager extends DefaultGameStateManager{
    public ServerGameStateManager(World initialWorld) {
        super(initialWorld);
    }

    @Override
    public synchronized World getWorld() {
        return super.getWorld();
    }

    @Override
    public synchronized void setPlayerDirection(String playerId, double dx, double dy) {
        super.setPlayerDirection(playerId, dx, dy);
    }

    @Override
    public void tick() {
        super.world = this.handleEating(super.world);
    }

    @Override
    public synchronized World handleEating(World currentWorld) {
        return super.handleEating(currentWorld);
    }

    @Override
    public synchronized void updateWorld(World world) {
        super.updateWorld(world);
    }

    public synchronized void movePlayer(String playerId, double newX, double newY) {
        final List<Player> updatedPlayers = super.world.getPlayers().stream()
                .map(player -> {
                    return player.getId().equals(playerId) ?  player.moveTo(newX, newY) : player;
                })
                .collect(Collectors.toList());

        super.world = new World(super.world.getWidth(), super.world.getHeight(), updatedPlayers,
                super.world.getFoods());
    }


}
