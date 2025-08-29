package it.unibo.agar.model;

public class Messages {

    public record RegistrationACK(String playerId, World world) implements java.io.Serializable, Message {};
    public record RegistrationRequest(String queue) implements java.io.Serializable, Message {};
    public record PlayerUpdate(String playerId, Double posX, Double posY, Double dirX, Double dirY) implements java.io.Serializable,
            Message {};
    public record StateUpdate(World world) implements java.io.Serializable, Message {};

    public interface Message {};
}
