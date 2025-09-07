package it.unibo.agar.model;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GameServerInterface extends Remote {

    Messages.RegistrationACK registerPlayer() throws RemoteException;

    void updatePlayer(String playerId, double posX, double posY, double dirX, double dirY) throws RemoteException;

    void unregisterPlayer(String playerId) throws RemoteException;

    World getWorld() throws RemoteException;

    boolean checkGameOver(String playerId) throws RemoteException;
}