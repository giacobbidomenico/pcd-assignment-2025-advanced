package it.unibo.agar;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import it.unibo.agar.model.DistributedGameStateManager;
import it.unibo.agar.model.GameServerInterface;
import it.unibo.agar.view.GlobalView;

import javax.swing.*;
import java.io.IOException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

public class Server {
    private static final int GAME_TICK_RATE_MS = 30;
    private static Optional<GlobalView> globalView = Optional.empty();
    private static DistributedGameStateManager distributedManager = null;

    public static void main(String[] args) {
        try {
            distributedManager = new DistributedGameStateManager();
            var stub = (GameServerInterface) UnicastRemoteObject.exportObject(distributedManager, 0);
            var registry = LocateRegistry.getRegistry();
            registry.rebind("remoteServer", stub);


            SwingUtilities.invokeLater(() -> {
                globalView = Optional.of(new GlobalView(distributedManager));
                globalView.ifPresent(x -> x.setVisible(true));
            });

            System.out.println("Game server started. Press CTRL-C to exit.");

            Timer gameLoopTimer = new Timer();
            gameLoopTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if(!distributedManager.isRunning()){
                        this.cancel();
                        try {
                            registry.unbind("remoteServer");
                        } catch (RemoteException | NotBoundException e) {
                            throw new RuntimeException(e);
                        }
                        try {
                            UnicastRemoteObject.unexportObject(distributedManager, true);
                        } catch (NoSuchObjectException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    distributedManager.tick();
                    SwingUtilities.invokeLater(() -> globalView.ifPresent(GlobalView::repaintView));
                }
            }, 0, GAME_TICK_RATE_MS);

        } catch (Exception e) {
            e.printStackTrace();
            if (distributedManager != null) {
                distributedManager.terminate();
                distributedManager.tick();
            }
            System.err.println(e.getMessage());
        }
    }
}