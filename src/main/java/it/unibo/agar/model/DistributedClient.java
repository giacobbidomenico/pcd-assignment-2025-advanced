package it.unibo.agar.model;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class DistributedClient {
    private static final String CLIENT_TO_SERVER = "client_to_server";
    private static final String SERVER_BROADCAST = "server_broadcast";
    private static final String REQUEST_QUEUE = "request_queue";
    private static final String UPDATE_QUEUE = "update_queue";

    private final Connection connection;
    private final Channel registrationChannel;
    private final Channel notificationChannel;
    private final Channel inputChannel;
    private final Channel outputChannel;
    private String notificationQueue = "";

    private final BlockingQueue<Messages.Message> messages = new LinkedBlockingQueue<>();
    private final CompletableFuture<Messages.RegistrationACK> registrationFuture = new CompletableFuture<>();

    private String playerId = "";
    private ClientGameStateManager stateManager;

    private boolean running = false;

    public DistributedClient(final Connection connection) throws IOException, ExecutionException, InterruptedException {
        this.connection = connection;
        registrationChannel = this.connection.createChannel();
        notificationChannel = this.connection.createChannel();
        inputChannel = this.connection.createChannel();
        outputChannel = this.connection.createChannel();

        setupRabbitMQ();
        messageRoutine(notificationChannel, notificationQueue);
        requestRegistration();
        Messages.RegistrationACK result;
        try {
            // Provo a prendere il risultato entro 2 secondi
            result = registrationFuture.get(10, TimeUnit.SECONDS);
            System.out.println("Risultato: " + result);
            confirm(result);
        } catch (TimeoutException e) {
            System.out.println("No Server Response received.");
            registrationFuture.cancel(true);
            terminate();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

    }

    private void requestRegistration(){
        try {
            registrationChannel.basicPublish(CLIENT_TO_SERVER, REQUEST_QUEUE, null, Serializer.serialize(new Messages.RegistrationRequest(notificationQueue)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupRabbitMQ() throws IOException {
        registrationChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        notificationChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        inputChannel.exchangeDeclare(SERVER_BROADCAST, "fanout");
        outputChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");

        registrationChannel.queueDeclare(REQUEST_QUEUE, false, false, true, null);
        outputChannel.queueDeclare(UPDATE_QUEUE, false, false, true, null);

        String randomUUID = UUID.randomUUID().toString();
        notificationQueue = "client.inbox." + randomUUID;
        notificationChannel.queueDeclare(notificationQueue, false, false, true, null);

        registrationChannel.queueBind(REQUEST_QUEUE, CLIENT_TO_SERVER, REQUEST_QUEUE);
        notificationChannel.queueBind(notificationQueue, CLIENT_TO_SERVER, notificationQueue);
        outputChannel.queueBind(UPDATE_QUEUE, CLIENT_TO_SERVER, UPDATE_QUEUE);
    }

    private void messageRoutine(Channel channel, String queueName) throws IOException {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                Messages.Message message = (Messages.Message) Serializer.deserialize(delivery.getBody());
                if (message instanceof Messages.RegistrationACK ack) {
                    registrationFuture.complete(ack);
                } else {
                    messages.add(message);
                }
            } catch (Exception e) {
                System.err.println("Errore durante l'elaborazione del comando: " + e.getMessage() + " Tipo di " +
                        "messaggio ricevuto: " + delivery.getBody().getClass() );
                e.printStackTrace();
            }
        };
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

    private void confirm(Messages.RegistrationACK registration) {
        this.playerId = registration.playerId();
        this.stateManager = new ClientGameStateManager(registration.world(), this.playerId);

        this.running = true;
        try {
            inputChannel.queueDeclare(playerId, false, false, true, null);
            inputChannel.queueBind(playerId, SERVER_BROADCAST, playerId);
            messageRoutine(inputChannel, playerId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void terminate(){
        try {
            if (registrationChannel != null && registrationChannel.isOpen()) {
                if (playerId.isEmpty()) {
                    registrationChannel.queueDelete(REQUEST_QUEUE);
                }
                registrationChannel.close();
            }
            if (notificationChannel != null && notificationChannel.isOpen()) {
                notificationChannel.queueDelete(notificationQueue);
                notificationChannel.close();
            }
            if (inputChannel != null && inputChannel.isOpen()) {
                if(!this.playerId.isEmpty())
                    inputChannel.queueDelete(this.playerId);
                inputChannel.close();
            }
            if (outputChannel != null && outputChannel.isOpen()) {
                if (this.playerId.isEmpty()) {
                    outputChannel.queueDelete(UPDATE_QUEUE);
                }
                outputChannel.basicPublish(CLIENT_TO_SERVER, UPDATE_QUEUE, null,
                        Serializer.serialize(new Messages.UnRegistration(this.playerId)));
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

    public boolean isRunning() {
        return running;
    }

    public void tick() {
        List<Messages.Message> s = new ArrayList<>();
        messages.drainTo(s);

        s.forEach(message -> {
            if (this.isRunning()){
                switch (message) {
                    case Messages.StateUpdate stateMessage -> stateManager.updateState(((Messages.StateUpdate) stateMessage).world());
                    case Messages.GameOver ignored -> terminate();
                    default -> {}
                }
            }
        });

        if(this.running){
            this.stateManager.tick();

            try {
                Player currentPlayer = this.stateManager.getWorld().getPlayerById(this.playerId).get();
                Position directions = this.stateManager.getDirection();
                System.out.println("INVIO POSIZIONE AGGIORNATA: " + currentPlayer.getX() + " " + currentPlayer.getY() +
                        " DIREZIONI: " + stateManager.playerDirections.get(this.playerId).x() + " " + stateManager.playerDirections.get(this.playerId).y());
                outputChannel.basicPublish(CLIENT_TO_SERVER, UPDATE_QUEUE, null,
                        Serializer.serialize( new Messages.PlayerUpdate(this.playerId, currentPlayer.getX(),
                                currentPlayer.getY(), directions.x(), directions.y())));
            } catch (IOException e) {
                System.err.println("Errore nella trasmissione dello stato del gioco: " + e.getMessage());
            }
        }
    }

    public ClientGameStateManager getGameState(){
        return this.stateManager;
    }

}
