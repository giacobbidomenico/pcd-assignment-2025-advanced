package it.unibo.agar.model;

import java.util.ArrayList;
import java.util.List;

public class ClientGameStateManager extends DefaultGameStateManager{
    private final String playerId;

    public String getPlayerId() {
        return playerId;
    }

    public ClientGameStateManager(World initialWorld, String playerId) {
        super(initialWorld);
        this.playerId = playerId;
    }

    private World movePlayer(final World currentWorld) {
        Player player = currentWorld.getPlayerById(playerId).get();
        Position direction = super.playerDirections.getOrDefault(player.getId(), Position.ZERO);
        final double newX = player.getX() + direction.x() * PLAYER_SPEED;
        final double newY = player.getY() + direction.y() * PLAYER_SPEED;
        Player movedPlayer = player.moveTo(newX, newY);
        List<Player> updatedPlayers = new ArrayList<>(currentWorld.removePlayers(List.of(player)).getPlayers());
        updatedPlayers.add(movedPlayer);
        return new World(currentWorld.getWidth(), currentWorld.getHeight(), updatedPlayers, currentWorld.getFoods());
    }

    public void updateState(World world){
        if(world.getPlayerById(this.playerId).isPresent()){
            System.out.println("ID DEL PLAYER: " + this.playerId + "PLAYER PRESENTI: " + world.getPlayers() + " ID: " + (world.getPlayers().size() == 1 ? world.getPlayers().get(0).getId() : ""));
            List<Player> updatedPlayers = new ArrayList<>(world.removePlayers(List.of(world.getPlayerById(this.playerId).get())).getPlayers());
            Player currentPlayer = super.getWorld().getPlayerById(this.playerId).get();
            updatedPlayers.add(new Player(currentPlayer.getId(), currentPlayer.getX(), currentPlayer.getY(),
                    world.getPlayerById(this.playerId).get().getMass()));
            super.updateWorld(new World(world.getWidth(), world.getHeight(), updatedPlayers, world.getFoods()));
        }
    }

    @Override
    public void tick() {
        super.world = this.movePlayer(super.world);
    }

    public Position getDirection(){
        return super.playerDirections.get(playerId);
    }
}
