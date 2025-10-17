package it.unibo.agar.model;

import java.rmi.RemoteException;

public class DistributedClient {
    private String playerId = "";
    private ClientGameStateManager stateManager;

    private boolean running = false;
    private final GameServerInterface remoteServer;
    private final boolean AI;

    public DistributedClient(GameServerInterface remoteServer, boolean AI) {
        this.remoteServer = remoteServer;
        this.AI = AI;
    }

    public synchronized void registration() throws RemoteException {
        Messages.RegistrationACK result = remoteServer.registerPlayer();
        this.playerId = result.playerId();
        stateManager = new ClientGameStateManager(result.world(), this.playerId);
        AIMovement.moveAI(this.playerId, stateManager);
        this.running = true;
    }

    public synchronized void terminate() throws RemoteException {
        this.remoteServer.unregisterPlayer(this.playerId);
        this.running = false;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void tick() throws RemoteException {
        if (!this.remoteServer.checkGameOver(this.playerId)) {
            this.running = false;
        }
        if(this.running){
            World world = this.remoteServer.getWorld();
            this.stateManager.updateState(world);
            if(this.AI) {
                AIMovement.moveAI(playerId, this.stateManager);
            }
            this.stateManager.tick();
            Player currentPlayer = this.stateManager.getWorld().getPlayerById(this.playerId).get();
            Position directions = this.stateManager.getDirection();
            this.remoteServer.updatePlayer(currentPlayer.getId(), currentPlayer.getX(), currentPlayer.getY(), directions.x(), directions.y());
        }
    }

    public synchronized ClientGameStateManager getGameState(){
        return this.stateManager;
    }
}
