package it.unibo.agar.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.rabbitmq.client.*;

public class DistributedGameStateManager implements GameStateManager {

    private static final String CLIENT_TO_SERVER = "client_to_server";
    private static final String SERVER_BROADCAST = "server_broadcast";
    private static final String REQUEST_QUEUE = "request_queue";
    private static final String UPDATE_QUEUE = "update_queue";
    private static final int WORLD_WIDTH = 1000;
    private static final int WORLD_HEIGHT = 1000;
    private static final int INITIAL_FOOD_COUNT = 150;
    private static final double INITIAL_PLAYER_MASS = 120.0;

    private final ServerGameStateManager localGameStateManager;
    private final Connection connection;
    private final AtomicInteger playerCounter = new AtomicInteger(0);

    private final Channel registrationChannel;
    private final Channel confirmationChannel;
    private final Channel inputChannel;
    private final Channel outputChannel;

    private final BlockingQueue<Messages.Message> messages = new LinkedBlockingQueue<>();

    public DistributedGameStateManager(final Connection connection) throws IOException {
        this.connection = connection;

        List<Food> initialFoods = GameInitializer.initialFoods(INITIAL_FOOD_COUNT, WORLD_WIDTH, WORLD_HEIGHT);
        World initialWorld = new World(WORLD_WIDTH, WORLD_HEIGHT, List.of(), initialFoods);
        this.localGameStateManager = new ServerGameStateManager(initialWorld);

        registrationChannel = connection.createChannel();
        confirmationChannel = connection.createChannel();
        inputChannel = connection.createChannel();
        outputChannel = connection.createChannel();

        setupRabbitMQ();

        messageRoutine(registrationChannel, REQUEST_QUEUE);
        messageRoutine(inputChannel, UPDATE_QUEUE);

        System.out.println("Distributed Game State Manager pronto.");
    }

    private void setupRabbitMQ() throws IOException {
        registrationChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        confirmationChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        inputChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        outputChannel.exchangeDeclare(SERVER_BROADCAST, "fanout");

        registrationChannel.queueDeclare(REQUEST_QUEUE, true,false,false,null);
        inputChannel.queueDeclare(UPDATE_QUEUE, true, false, false, null);

        registrationChannel.queueBind(REQUEST_QUEUE, CLIENT_TO_SERVER, REQUEST_QUEUE);
        inputChannel.queueBind(UPDATE_QUEUE, CLIENT_TO_SERVER, UPDATE_QUEUE);
    }

    private void messageRoutine(Channel channel, String queueName) throws IOException {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                Messages.Message message = (Messages.Message) Serializer.deserialize(delivery.getBody());
                messages.add(message);
            } catch (Exception e) {
                System.err.println("Errore durante l'elaborazione del comando: " + e.getMessage());
                e.printStackTrace();
            }
        };
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

//    private void startCommandConsumer() throws IOException {
//        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
//            // L'intero blocco di elaborazione dei messaggi è stato racchiuso in un try-catch
//            try {
//                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
//                System.out.println("Ricevuto comando: " + message);
//
//                if (message.startsWith("REGISTER")) {
//                    registerPlayer(delivery);
//                } else if (message.startsWith("MOVE")) {
//                    String[] parts = message.substring(5).split(",");
//                    if (parts.length == 5) {
//                        localGameStateManager.movePlayer(parts[0], Double.parseDouble(parts[1]),
//                                Double.parseDouble(parts[2]));
//                        setPlayerDirection(parts[0], Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
//                    }
//                } else if (message.startsWith("UNREGISTER")) {
//                    String playerId = message.substring(11);
//                    unregisterPlayer(playerId);
//                }
//            } catch (Exception e) {
//                // Gestisce qualsiasi eccezione in modo da non chiudere il canale RabbitMQ
//                System.err.println("Errore durante l'elaborazione del comando: " + e.getMessage());
//                e.printStackTrace();
//            }
//        };
//        channel.basicConsume(CMD_QUEUE_NAME, true, deliverCallback, consumerTag -> {});
//    }

    private void registerPlayer(Messages.RegistrationRequest registration) throws IOException {
        String playerId = "p" + playerCounter.incrementAndGet();
        World currentWorld = localGameStateManager.getWorld();
        Player newPlayer = new Player(playerId, (int) (Math.random() * currentWorld.getWidth()), (int) (Math.random() * currentWorld.getHeight()), INITIAL_PLAYER_MASS);

        List<Player> updatedPlayers = new ArrayList<>(currentWorld.getPlayers());
        updatedPlayers.add(newPlayer);
        localGameStateManager.updateWorld(new World(currentWorld.getWidth(), currentWorld.getHeight(), updatedPlayers, currentWorld.getFoods()));

        registrationChannel.queueBind(registration.queue(), CLIENT_TO_SERVER, registration.queue());

        System.out.println("NEW PLAYER REGISTERED: " + playerId);
        System.out.println("Player presenti: " + this.getWorld().getPlayers() + " ID: " + (this.getWorld().getPlayers().size() == 1 ? this.getWorld().getPlayers().get(0).getId() : "")  );
        registrationChannel.basicPublish(
                CLIENT_TO_SERVER,
                registration.queue(),
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
        List<Messages.Message> s = new ArrayList<>();
        messages.drainTo(s);

        s.forEach(message -> {
            switch (message){
                case Messages.RegistrationRequest registration -> {
                    try {
                        registerPlayer(registration);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                case Messages.PlayerUpdate m -> {
                    System.out.println("PLAYER UPDATE: " + m.playerId() + " " + m.posX() + " " + m.posY() + " " + m.dirX() + " " + m.dirY());
                    localGameStateManager.movePlayer(m.playerId(), m.posX(), m.posY());
                    localGameStateManager.setPlayerDirection(m.playerId(), m.dirX(), m.dirY());
                }
                case Messages.RegistrationACK unregistration -> {}
                default -> {}
            }
        });

        this.localGameStateManager.tick();
        try {
            System.out.println("Player presenti: " + this.getWorld().getPlayers() + " ID: " + (this.getWorld().getPlayers().size() == 1 ? this.getWorld().getPlayers().get(0).getId() : "")  );
            outputChannel.basicPublish(SERVER_BROADCAST, "", null, Serializer.serialize(new Messages.StateUpdate(this.getWorld())));
        } catch (IOException e) {
            System.err.println("Errore nella trasmissione dello stato del gioco: " + e.getMessage());
        }
    }


}