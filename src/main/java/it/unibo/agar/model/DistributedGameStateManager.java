package it.unibo.agar.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;

public class DistributedGameStateManager implements GameStateManager {

    private static final String CMD_QUEUE_NAME = "game_commands";
    private static final String STATE_EXCHANGE_NAME = "game_state";
    private static final String REG_QUEUE_NAME = "player_registration";
    private static final int WORLD_WIDTH = 1000;
    private static final int WORLD_HEIGHT = 1000;
    private static final int INITIAL_FOOD_COUNT = 150;
    private static final double INITIAL_PLAYER_MASS = 120.0;

    private final ServerGameStateManager localGameStateManager;
    private final Channel channel;
    private final AtomicInteger playerCounter = new AtomicInteger(0);

    public DistributedGameStateManager(final Channel channel) throws IOException {
        this.channel = channel;

        List<Food> initialFoods = GameInitializer.initialFoods(INITIAL_FOOD_COUNT, WORLD_WIDTH, WORLD_HEIGHT);
        World initialWorld = new World(WORLD_WIDTH, WORLD_HEIGHT, List.of(), initialFoods);
        this.localGameStateManager = new ServerGameStateManager(initialWorld);

        setupRabbitMQ();

        startCommandConsumer();

        System.out.println("Distributed Game State Manager pronto.");
    }

    private void setupRabbitMQ() throws IOException {
        channel.queueDeclare(CMD_QUEUE_NAME, false, false, false, null);
        channel.queueDeclare(REG_QUEUE_NAME, false, false, false, null);
        channel.exchangeDeclare(STATE_EXCHANGE_NAME, BuiltinExchangeType.FANOUT);
    }

    private void startCommandConsumer() throws IOException {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // L'intero blocco di elaborazione dei messaggi è stato racchiuso in un try-catch
            try {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("Ricevuto comando: " + message);

                if (message.startsWith("REGISTER")) {
                    registerPlayer();
                } else if (message.startsWith("MOVE")) {
                    String[] parts = message.substring(5).split(",");
                    if (parts.length == 5) {
                        localGameStateManager.movePlayer(parts[0], Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]));
                        setPlayerDirection(parts[0], Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
                    }
                } else if (message.startsWith("UNREGISTER")) {
                    String playerId = message.substring(11);
                    unregisterPlayer(playerId);
                }
            } catch (Exception e) {
                // Gestisce qualsiasi eccezione in modo da non chiudere il canale RabbitMQ
                System.err.println("Errore durante l'elaborazione del comando: " + e.getMessage());
                e.printStackTrace();
            }
        };
        channel.basicConsume(CMD_QUEUE_NAME, true, deliverCallback, consumerTag -> {});
    }

    private void registerPlayer() throws IOException {
        String playerId = "p" + playerCounter.incrementAndGet();
        World currentWorld = localGameStateManager.getWorld();
        Player newPlayer = new Player(playerId, (int) (Math.random() * currentWorld.getWidth()), (int) (Math.random() * currentWorld.getHeight()), INITIAL_PLAYER_MASS);

        List<Player> updatedPlayers = new java.util.ArrayList<>(currentWorld.getPlayers());
        updatedPlayers.add(newPlayer);
        localGameStateManager.updateWorld(new World(currentWorld.getWidth(), currentWorld.getHeight(), updatedPlayers, currentWorld.getFoods()));

        System.out.println("Nuovo giocatore registrato: " + playerId);
        channel.basicPublish(
                "",
                REG_QUEUE_NAME,
                null,
                Serializer.serialize(new Messages.RegistrationACK(playerId, localGameStateManager.getWorld()))
        );
    }

    private void unregisterPlayer(final String playerId) {
        World currentWorld = localGameStateManager.getWorld();
        List<Player> updatedPlayers = currentWorld.getPlayers().stream()
                .filter(p -> !p.getId().equals(playerId))
                .toList();
        localGameStateManager.updateWorld(new World(currentWorld.getWidth(), currentWorld.getHeight(), updatedPlayers, currentWorld.getFoods()));
        System.out.println("Giocatore disconnesso: " + playerId);
    }

    @Override
    public World getWorld() {
        return this.localGameStateManager.getWorld();
    }

    @Override
    public void setPlayerDirection(final String playerId, final double dx, final double dy) {
        this.localGameStateManager.setPlayerDirection(playerId, dx, dy);
    }

    @Override
    public void tick() {
        this.localGameStateManager.tick();
        try {
            byte[] worldBytes = Serializer.serialize(this.getWorld());
            channel.basicPublish(STATE_EXCHANGE_NAME, "", null, worldBytes);
        } catch (IOException e) {
            System.err.println("Errore nella trasmissione dello stato del gioco: " + e.getMessage());
        }
    }
}