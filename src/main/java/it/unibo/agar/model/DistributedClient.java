package it.unibo.agar.model;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CompletableFuture;

public class DistributedClient {
    private static final String CLIENT_TO_SERVER = "client_to_server";
    private static final String SERVER_BROADCAST = "server_broadcast";
    private static final String REQUEST_QUEUE = "request_queue";
    private static final String UPDATE_QUEUE = "update_queue";

    private final Channel registrationChannel;
    private final Channel confirmationChannel;
    private final Channel inputChannel;
    private final Channel outputChannel;
    private String confirmationQueue = "";

    private final BlockingQueue<Messages.Message> messages = new LinkedBlockingQueue<>();
    private final CompletableFuture<Messages.RegistrationACK> registrationFuture = new CompletableFuture<>();

    private String playerId;
    private ClientGameStateManager stateManager;

    public DistributedClient(final Connection connection) throws IOException, ExecutionException, InterruptedException {
        registrationChannel = connection.createChannel();
        confirmationChannel = connection.createChannel();
        inputChannel = connection.createChannel();
        outputChannel = connection.createChannel();

        setupRabbitMQ();
        messageRoutine(confirmationChannel, confirmationQueue);
        requestRegistration();
        confirm(registrationFuture.get());
    }

    private void requestRegistration(){
        try {
            registrationChannel.basicPublish(CLIENT_TO_SERVER, REQUEST_QUEUE, null, Serializer.serialize(new Messages.RegistrationRequest(confirmationQueue)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupRabbitMQ() throws IOException {
        registrationChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        confirmationChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");
        inputChannel.exchangeDeclare(SERVER_BROADCAST, "fanout");
        outputChannel.exchangeDeclare(CLIENT_TO_SERVER, "direct");

        registrationChannel.queueDeclare(REQUEST_QUEUE, true,false,false,null);
        outputChannel.queueDeclare(UPDATE_QUEUE, true, false, false, null);

        String randomUUID = UUID.randomUUID().toString();
        confirmationQueue = "client.inbox." + randomUUID;
        confirmationChannel.queueDeclare(confirmationQueue, true, false, false, null);

        registrationChannel.queueBind(REQUEST_QUEUE, CLIENT_TO_SERVER, REQUEST_QUEUE);
        confirmationChannel.queueBind(confirmationQueue, CLIENT_TO_SERVER, confirmationQueue);
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

        try {
            inputChannel.queueDeclare(playerId, true, false, false, null);
            inputChannel.queueBind(playerId, SERVER_BROADCAST, playerId);
            messageRoutine(inputChannel, playerId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void tick() {
        List<Messages.Message> s = new ArrayList<>();
        messages.drainTo(s);

        s.forEach(message -> stateManager.updateOthers(((Messages.StateUpdate) message).world()));
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

    public ClientGameStateManager getGameState(){
        return this.stateManager;
    }

}
