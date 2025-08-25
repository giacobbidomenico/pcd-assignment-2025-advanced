package it.unibo.agar;

import com.rabbitmq.client.*;
import it.unibo.agar.model.*;
import it.unibo.agar.view.LocalView;
import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

public class GameClient {
    private static final String RABBIT_MQ_HOST = "localhost";
    private static final int GAME_TICK_RATE_MS = 50;
    private static final String CMD_QUEUE_NAME = "game_commands";
    private static final String REG_QUEUE_NAME = "player_registration";
    private static final String STATE_EXCHANGE_NAME = "game_state";
    private static Channel channel;
    private static LocalView localView;
    private static ClientGameStateManager gameStateManager;
    private static String playerId;

    public static void main(String[] args) throws IOException, TimeoutException {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RABBIT_MQ_HOST);
            Connection connection = factory.newConnection();
            channel = connection.createChannel();

            // Dichiarazione delle code e degli exchange per essere sicuri che esistano
            channel.queueDeclare(CMD_QUEUE_NAME, false, false, false, null);
            channel.queueDeclare(REG_QUEUE_NAME, false, false, false, null);
            channel.exchangeDeclare(STATE_EXCHANGE_NAME, "fanout");
            String stateQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(stateQueueName, STATE_EXCHANGE_NAME, "");

            // Invia il comando di registrazione
            channel.basicPublish("", CMD_QUEUE_NAME, null, "REGISTER".getBytes(StandardCharsets.UTF_8));
            System.out.println("Comando di registrazione inviato. In attesa della risposta...");

            // Attende la conferma di registrazione usando basicGet()
            GetResponse response = channel.basicGet(REG_QUEUE_NAME, true);
            if (response != null) {
                Messages.RegistrationACK body = (Messages.RegistrationACK) Serializer.deserialize(response.getBody());
                playerId = body.playerId();
                gameStateManager = new ClientGameStateManager(body.world(), playerId);
                localView = new LocalView(gameStateManager, playerId);
                localView.setVisible(true);
                System.out.println("Registrato con ID: " + playerId);
            } else {
                System.err.println("Nessuna risposta di registrazione ricevuta. Il server potrebbe non essere attivo.");
                throw new IllegalStateException("Impossibile registrarsi al server.");
            }

            // Inizializzazione del loop di gioco
            Timer gameLoopTimer = new Timer();
            gameLoopTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        GetResponse response = channel.basicGet(stateQueueName, true);
                        if (response != null) {
                            World newWorld = (World) Serializer.deserialize(response.getBody());
                            gameStateManager.updateWorld(newWorld);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    gameStateManager.tick();
                    gameStateManager.getWorld().getPlayerById(playerId).ifPresent(player -> {
                        String str = "MOVE," +
                                player.getId() + "," +
                                player.getX() + "," +
                                player.getY() + "," +
                                gameStateManager.getDirection().x() + "," +
                                gameStateManager.getDirection().y();
                        try {
                            channel.basicPublish("", CMD_QUEUE_NAME, null, str.getBytes(StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    SwingUtilities.invokeLater(localView::repaintView);
                }
            }, 0, GAME_TICK_RATE_MS);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Il client è terminato a causa di un'eccezione: " + e.getMessage());
        }
    }
}