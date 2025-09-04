package it.unibo.agar;

import com.rabbitmq.client.*;
import it.unibo.agar.model.*;
import it.unibo.agar.view.LocalView;
import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class Client {
    private static final String RABBIT_MQ_HOST = "localhost";
    private static final int GAME_TICK_RATE_MS = 30;
    private static Channel channel;
    private static LocalView localView;
    private static String playerId;
    private static DistributedClient client = null;


    public static void main(String[] args) throws IOException, TimeoutException {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RABBIT_MQ_HOST);
            Connection connection = factory.newConnection();

            client = new DistributedClient(connection);

            if(!client.isRunning()){
                System.exit(0);
            }
            localView = new LocalView(client, client.getGameState().getPlayerId());
            localView.setVisible(true);

            // Inizializzazione del loop di gioco
            Timer gameLoopTimer = new Timer();
            gameLoopTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if(!client.isRunning()){
                        this.cancel();
                        SwingUtilities.invokeLater(localView::showGameOver);
                    }
                    client.tick();
                    SwingUtilities.invokeLater(localView::repaintView);
                }
            }, 0, GAME_TICK_RATE_MS);

        } catch (Exception e) {
            e.printStackTrace();
            if(client != null){
                client.terminate();
            }
            System.err.println("Il client è terminato a causa di un'eccezione: " + e.getMessage());
        }
    }
}