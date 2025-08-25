package it.unibo.agar.model;

import java.util.List;
import java.util.stream.Collectors;

public class ServerGameStateManager extends DefaultGameStateManager{
    public ServerGameStateManager(World initialWorld) {
        super(initialWorld);
    }

    @Override
    public void tick() {
        super.world = this.handleEating(super.world);
    }

    public void movePlayer(String playerId, double newX, double newY) {
        final List<Player> updatedPlayers = super.world.getPlayers().stream()
                .map(player -> {
                    return player.getId().equals(playerId) ?  player.moveTo(newX, newY) : player;
                })
                .collect(Collectors.toList());

        super.world = new World(super.world.getWidth(), super.world.getHeight(), updatedPlayers,
                super.world.getFoods());
    }
}
