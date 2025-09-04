package it.unibo.agar.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ServerGameStateManager extends DefaultGameStateManager{

    private List<Player> playersToRemove;

    public ServerGameStateManager(World initialWorld) {
        super(initialWorld);
        this.playersToRemove = new ArrayList<>();
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

    public List<Player> getPlayersToRemove(){
        List<Player> players = new ArrayList<>(this.playersToRemove);
        this.playersToRemove.clear();
        return players;
    }

    @Override
    public synchronized World handleEating(World currentWorld) {
        final List<Player> updatedPlayers = currentWorld.getPlayers().stream()
                .map(player -> growPlayer(currentWorld, player))
                .toList();

        final List<Food> foodsToRemove = currentWorld.getPlayers().stream()
                .flatMap(player -> eatenFoods(currentWorld, player).stream())
                .distinct()
                .toList();

        playersToRemove.addAll(currentWorld.getPlayers().stream()
                .flatMap(player -> eatenPlayers(currentWorld, player).stream())
                .distinct()
                .toList());

        return new World(currentWorld.getWidth(), currentWorld.getHeight(), updatedPlayers, currentWorld.getFoods())
                .removeFoods(foodsToRemove)
                .removePlayers(playersToRemove);
    }

    @Override
    public synchronized void updateWorld(World world) {
        super.updateWorld(world);
    }

    public synchronized void removePlayer(String playerId) {
        List<Player> updatedPlayers = this.world.getPlayers().stream()
                .filter(p -> !p.getId().equals(playerId))
                .collect(Collectors.toList());
        this.world = new World(this.world.getWidth(), this.world.getHeight(), updatedPlayers, this.world.getFoods());
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
