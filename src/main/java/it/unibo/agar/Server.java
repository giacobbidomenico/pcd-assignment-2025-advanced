package it.unibo.agar;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import it.unibo.agar.model.DistributedGameStateManager;
import it.unibo.agar.view.GlobalView;

import javax.swing.*;
import java.io.IOException;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

public class Server {
    private static final String RABBIT_MQ_HOST = "localhost";
    private static final int GAME_TICK_RATE_MS = 30;
    private static Optional<GlobalView> globalView = Optional.empty();
    private static DistributedGameStateManager distributedManager = null;

    public static void main(String[] args) throws IOException, TimeoutException {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RABBIT_MQ_HOST);
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            distributedManager = new DistributedGameStateManager(connection);

            SwingUtilities.invokeLater(() -> {
                globalView = Optional.of(new GlobalView(distributedManager));
                globalView.ifPresent(x -> x.setVisible(true));
            });

            Timer gameLoopTimer = new Timer();
            gameLoopTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if(!distributedManager.isRunning()){
                        this.cancel();
                    }
                    distributedManager.tick();
                    SwingUtilities.invokeLater(() -> globalView.ifPresent(GlobalView::repaintView));
                }
            }, 0, GAME_TICK_RATE_MS);

            System.out.println("Game server started. Press CTRL-C to exit.");
        } catch (Exception e) {
            e.printStackTrace();
            if (distributedManager != null) {
                distributedManager.notifyClose();
                distributedManager.tick();
            }
            System.err.println(e.getMessage());
        }
    }
}