package it.unibo.agar.model;

import java.util.List;
import java.util.stream.Collectors;

public class ClientGameStateManager extends DefaultGameStateManager{
    private final String playerId;

    public ClientGameStateManager(World initialWorld, String playerId) {
        super(initialWorld);
        this.playerId = playerId;
    }

    private World movePlayer(final World currentWorld) {
        final List<Player> updatedPlayers = currentWorld.getPlayers().stream()
                .map(player -> {
                    if(playerId.equals(player.getId())){
                        Position direction = super.playerDirections.getOrDefault(player.getId(), Position.ZERO);
                        final double newX = player.getX() + direction.x() * PLAYER_SPEED;
                        final double newY = player.getY() + direction.y() * PLAYER_SPEED;
                        return player.moveTo(newX, newY);
                    } else {
                        return player;
                    }
                })
                .collect(Collectors.toList());

        return new World(currentWorld.getWidth(), currentWorld.getHeight(), updatedPlayers, currentWorld.getFoods());
    }

    @Override
    public void tick() {
        super.world = this.movePlayer(super.world);
    }

    public Position getDirection(){
        return super.playerDirections.get(playerId);
    }
}
