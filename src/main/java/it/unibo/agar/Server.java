package it.unibo.agar;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import it.unibo.agar.model.DistributedGameStateManager;
import it.unibo.agar.view.GlobalView;
import it.unibo.agar.view.LocalView;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.TimeoutException;

public class Server {
    private static final String RABBIT_MQ_HOST = "localhost";
    private static final int GAME_TICK_RATE_MS = 50;
    private static Optional<GlobalView> globalView = Optional.empty();

    public static void main(String[] args) throws IOException, TimeoutException {
        try{
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RABBIT_MQ_HOST);
            Channel channel;
            Connection connection = factory.newConnection();
            channel = connection.createChannel();


            DistributedGameStateManager distributedManager = new DistributedGameStateManager(channel);
            SwingUtilities.invokeLater(() -> {
                globalView = Optional.of(new GlobalView(distributedManager));
                globalView.ifPresent(x -> x.setVisible(true));
            });

            // Timer per il loop principale del gioco
            Timer gameLoopTimer = new Timer();
            gameLoopTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    distributedManager.tick();

                    SwingUtilities.invokeLater(() -> globalView.ifPresent(GlobalView::repaintView));
                }
            }, 0, GAME_TICK_RATE_MS);

            // Il programma continua a funzionare perché il thread del consumer e il thread del timer sono attivi.
            System.out.println("Game server avviato. Premi CTRL-C per uscire.");
        } catch (Exception e) {
            e.printStackTrace(); // This is the crucial line you need to add
            System.err.println("The program terminated due to an unhandled exception: " + e.getMessage());
        }
    }


}
