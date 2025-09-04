package it.unibo.agar.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final Channel notificationChannel;
    private final Channel inputChannel;
    private final Channel outputChannel;

    private final BlockingQueue<Messages.Message> messages = new LinkedBlockingQueue<>();
    private final ConcurrentMap<String, String> clientsQueues = new ConcurrentHashMap<>();

    private boolean running = false;

    public DistributedGameStateManager(final Connection connection) throws IOException {
        this.connection = connection;

        List<Food> initialFoods = GameInitializer.initialFoods(INITIAL_FOOD_COUNT, WORLD_WIDTH, WORLD_HEIGHT);
        World initialWorld = new World(WORLD_WIDTH, WORLD_HEIGHT, List.of(), initialFoods);
        this.localGameStateManager = new ServerGameStateManager(initialWorld);

        registrationChannel = this.connection.createChannel();
        notificationChannel = this.connection.createChannel();
        inputChannel = this.connection.createChannel();
        outputChannel = this.connection.createChannel();

        setupRabbitMQ();

        messageRoutine(registrationChannel, REQUEST_QUEUE);
        messageRoutine(inputChannel, UPDATE_QUEUE);

        this.running = true;

        System.out.println("Distributed Game State Manager pronto.");
    }

    private void setupRabbitMQ() throws IOException {
        registrationChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        notificationChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        inputChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        outputChannel.exchangeDeclare(SERVER_BROADCAST, "fanout");

        registrationChannel.queueDeclare(REQUEST_QUEUE, false, false, true, null);
        inputChannel.queueDeclare(UPDATE_QUEUE, false, false, true, null);

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

    private void registerPlayer(Messages.RegistrationRequest registration) throws IOException {
        String playerId = "p" + playerCounter.incrementAndGet();
        World currentWorld = localGameStateManager.getWorld();
        Player newPlayer = new Player(playerId, (int) (Math.random() * currentWorld.getWidth()), (int) (Math.random() * currentWorld.getHeight()), INITIAL_PLAYER_MASS);

        List<Player> updatedPlayers = new ArrayList<>(currentWorld.getPlayers());
        updatedPlayers.add(newPlayer);
        localGameStateManager.updateWorld(new World(currentWorld.getWidth(), currentWorld.getHeight(), updatedPlayers, currentWorld.getFoods()));

        registrationChannel.queueBind(registration.queue(), CLIENT_TO_SERVER, registration.queue());

        clientsQueues.put(playerId, registration.queue());
        System.out.println("NEW PLAYER REGISTERED: " + playerId);
        System.out.println("Player presenti: " + this.getWorld().getPlayers() + " ID: " + (this.getWorld().getPlayers().size() == 1 ? this.getWorld().getPlayers().get(0).getId() : "")  );
        registrationChannel.basicPublish(
                CLIENT_TO_SERVER,
                registration.queue(),
                null,
                Serializer.serialize(new Messages.RegistrationACK(playerId, localGameStateManager.getWorld()))
        );
    }

    @Override
    public World getWorld() {
        return this.localGameStateManager.getWorld();
    }

    @Override
    public void setPlayerDirection(final String playerId, final double dx, final double dy) {
        this.localGameStateManager.setPlayerDirection(playerId, dx, dy);
    }

    private void notifyGameOver(List<Player> playersToRemove){
        playersToRemove.forEach(player -> {
            try {
                notificationChannel.basicPublish(CLIENT_TO_SERVER,
                        clientsQueues.get(player.getId()), null, Serializer.serialize(new Messages.GameOver()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void terminate(){
        try {
            if (registrationChannel != null && registrationChannel.isOpen()) {
                registrationChannel.queueDelete(REQUEST_QUEUE);
                registrationChannel.close();
            }
            if (notificationChannel != null && notificationChannel.isOpen()) {
                clientsQueues.forEach( (id, queue) -> {
                    try {
                        notificationChannel.basicPublish(CLIENT_TO_SERVER, queue, null,
                         Serializer.serialize(new Messages.GameOver()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                notificationChannel.close();
            }
            if (inputChannel != null && inputChannel.isOpen()) {
                inputChannel.queueDelete(UPDATE_QUEUE);
                inputChannel.close();
            }
            if (outputChannel != null && outputChannel.isOpen()) {
                outputChannel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            System.err.println("Errore durante la chiusura delle risorse RabbitMQ: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            this.running = false;
        }
    }

    public void notifyClose(){
        messages.add(new Messages.GameOver());
    }

    @Override
    public void tick() {
        List<Messages.Message> s = new ArrayList<>();
        messages.drainTo(s);

        s.forEach(message -> {
            if (this.running){
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
                    case Messages.UnRegistration m -> {
                        this.localGameStateManager.removePlayer(m.playerId());
                        this.clientsQueues.remove(m.playerId());
                    }
                    case Messages.GameOver ignored -> this.terminate();
                    default -> {}
                }
            }
        });

        if (this.running) {
            this.localGameStateManager.tick();
            this.notifyGameOver(this.localGameStateManager.getPlayersToRemove());
            try {
                System.out.println("Player presenti: " + this.getWorld().getPlayers() + " ID: " + (this.getWorld().getPlayers().size() == 1 ? this.getWorld().getPlayers().get(0).getId() : "")  );
                outputChannel.basicPublish(SERVER_BROADCAST, "", null, Serializer.serialize(new Messages.StateUpdate(this.getWorld())));
            } catch (IOException e) {
                System.err.println("Errore nella trasmissione dello stato del gioco: " + e.getMessage());
            }
        }
    }

    public ServerGameStateManager getLocalGameStateManager() {
        return localGameStateManager;
    }

    public boolean isRunning() {
        return this.running;
    }
}