package it.unibo.agar;

import com.rabbitmq.client.*;
import it.unibo.agar.model.*;
import it.unibo.agar.view.LocalView;
import javax.swing.*;
import java.io.IOException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

public class Client {
    private static final int GAME_TICK_RATE_MS = 30;
    private static LocalView localView;
    private static DistributedClient client = null;

    public static void main(String[] args) throws IOException, TimeoutException {
        boolean AI = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-AI")) {
                AI = true;
                break;
            }
        }

        try {
            var registry = LocateRegistry.getRegistry();
            GameServerInterface remoteServer = null;
            try {
                remoteServer = (GameServerInterface) registry.lookup("remoteServer");
            } catch (NotBoundException e) {
                System.err.println("Server error: could not reach the server.");
                System.exit(0);
            }

            client = new DistributedClient(remoteServer, AI);
            try {
                client.registration();
            } catch (RuntimeException e) {
                System.err.println("Server error: could not reach the server.");
                System.exit(0);
            }

            localView = new LocalView(client, client.getGameState().getPlayerId());
            localView.setVisible(true);

            Timer gameLoopTimer = new Timer();
            gameLoopTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if(!client.isRunning()){
                        this.cancel();
                        SwingUtilities.invokeLater(localView::showGameOver);
                    }
                    try {
                        client.tick();
                    } catch (RemoteException e) {
                        System.err.println("Server error: could not reach the server.");
                        this.cancel();
                        SwingUtilities.invokeLater(localView::showGameOver);
                    }
                    SwingUtilities.invokeLater(localView::repaintView);
                }
            }, 0, GAME_TICK_RATE_MS);

        } catch (Exception e) {
            e.printStackTrace();
            if(client != null){
                client.terminate();
            }
            System.err.println(e.getMessage());
        }
    }
}