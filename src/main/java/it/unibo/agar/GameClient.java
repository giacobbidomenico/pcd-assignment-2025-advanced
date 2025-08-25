package it.unibo.agar;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import it.unibo.agar.model.*;
import it.unibo.agar.view.GlobalView;
import it.unibo.agar.view.LocalView;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
// ...altre importazioni

public class GameClient {
    private static final String RABBIT_MQ_HOST = "localhost";
    private static final int GAME_TICK_RATE_MS = 50;
    private static final String CMD_QUEUE_NAME = "game_commands";
    private static final String REG_QUEUE_NAME = "player_registration";
    private static final String STATE_EXCHANGE_NAME = "game_state";
    private static final int MAX_REG_POLL = 100;
    private static Channel channel;
    private static LocalView localView;
    private static ClientGameStateManager gameStateManager;
    private static final AtomicReference<World> latestWorld = new AtomicReference<>();
    private static String playerId;

    public static void main(String[] args)  throws IOException, TimeoutException {
        // 1. Connessione a RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT_MQ_HOST);
        Connection connection = factory.newConnection();
        channel = connection.createChannel();


        // 2. Registrazione del Consumer per lo stato di gioco
        // Crea una coda temporanea per ricevere gli aggiornamenti dal server
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, STATE_EXCHANGE_NAME, "");

        // 3. Invia il comando di registrazione al server
        channel.basicPublish("", CMD_QUEUE_NAME, null, "REGISTER".getBytes(StandardCharsets.UTF_8));

        channel.queueDeclare(REG_QUEUE_NAME, false, false, false, null);

        Messages.RegistrationACK body = null;
        int count = 0;
        while (body == null && count < MAX_REG_POLL) {
            GetResponse response = channel.basicGet(REG_QUEUE_NAME, true);
            if (response != null) {
                body = (Messages.RegistrationACK) Serializer.deserialize(response.getBody());
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            count++;
        }

        if(count == MAX_REG_POLL && body == null){
            throw new IllegalStateException();
        }

        playerId = body.playerId();
        gameStateManager = new ClientGameStateManager(body.world(), playerId);
        localView = new LocalView(gameStateManager, playerId);
        System.out.println("Registered with ID: " + playerId);

        Timer gameLoopTimer = new Timer();
        gameLoopTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Poll per aggiornamenti dal server (nuovo stato del mondo)
                    GetResponse response = channel.basicGet(queueName, true);
                    if (response != null) {
                        World newWorld = (World) Serializer.deserialize(response.getBody());
                        gameStateManager.updateWorld(newWorld); // Ora avviene nello stesso thread del game loop
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                gameStateManager.tick();
                gameStateManager.getWorld().getPlayerById(playerId).ifPresent(player -> {
                    String str = "MOVE, " +
                            player.getId() + ", " +
                            player.getX() + ", " +
                            player.getY() + ", " +
                            gameStateManager.getDirection().x() + ", " +
                            gameStateManager.getDirection().y();
                    try {
                        channel.basicPublish("", CMD_QUEUE_NAME, null, str.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                SwingUtilities.invokeLater(localView::repaintView);
            }
        }, 0, GAME_TICK_RATE_MS);
    }
}