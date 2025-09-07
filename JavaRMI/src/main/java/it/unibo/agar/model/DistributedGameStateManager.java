package it.unibo.agar.model;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DistributedGameStateManager implements GameServerInterface {

    private static final int WORLD_WIDTH = 1000;
    private static final int WORLD_HEIGHT = 1000;
    private static final int INITIAL_FOOD_COUNT = 150;
    private static final double INITIAL_PLAYER_MASS = 120.0;

    private final ServerGameStateManager localGameStateManager;
    private final AtomicInteger playerCounter = new AtomicInteger(0);
    private final List<String> clients = new ArrayList<>();
    private boolean running = false;

    public DistributedGameStateManager() {
        List<Food> initialFoods = GameInitializer.initialFoods(INITIAL_FOOD_COUNT, WORLD_WIDTH, WORLD_HEIGHT);
        World initialWorld = new World(WORLD_WIDTH, WORLD_HEIGHT, List.of(), initialFoods);
        this.localGameStateManager = new ServerGameStateManager(initialWorld);
        this.running = true;
    }

    @Override
    public synchronized Messages.RegistrationACK registerPlayer() throws RemoteException {
        String playerId = "p" + playerCounter.incrementAndGet();
        World currentWorld = localGameStateManager.getWorld();
        Player newPlayer = new Player(playerId, (int) (Math.random() * currentWorld.getWidth()), (int) (Math.random() * currentWorld.getHeight()), INITIAL_PLAYER_MASS);

        List<Player> updatedPlayers = new ArrayList<>(currentWorld.getPlayers());
        updatedPlayers.add(newPlayer);
        localGameStateManager.updateWorld(new World(currentWorld.getWidth(), currentWorld.getHeight(), updatedPlayers, currentWorld.getFoods()));

        clients.add(playerId);
        return new Messages.RegistrationACK(playerId, localGameStateManager.getWorld());
    }

    @Override
    public synchronized void updatePlayer(String playerId, double posX, double posY, double dirX, double dirY) throws RemoteException {
        localGameStateManager.movePlayer(playerId, posX, posY);
        localGameStateManager.setPlayerDirection(playerId, dirX, dirY);
    }

    @Override
    public synchronized void unregisterPlayer(String playerId) throws RemoteException {
        this.localGameStateManager.removePlayer(playerId);
        this.clients.remove(playerId);
    }

    @Override
    public synchronized World getWorld() throws RemoteException {
        return this.localGameStateManager.getWorld();
    }

    @Override
    public boolean checkGameOver(String playerId) throws RemoteException {
        return clients.contains(playerId);
    }

    private void notifyGameOver(List<Player> playersToRemove){
        playersToRemove.forEach(player -> {
                clients.remove(player.getId());
        });
    }

    public synchronized void terminate(){
        clients.clear();
        this.running = false;
    }

    public synchronized void tick() {
        if (this.running) {
            this.localGameStateManager.tick();
            this.notifyGameOver(this.localGameStateManager.getPlayersToRemove());
        }
    }

    public ServerGameStateManager getLocalGameStateManager() {
        return localGameStateManager;
    }

    public boolean isRunning() {
        return this.running;
    }
}