package it.unibo.agar.model;

public class Messages {

    public record RegistrationACK(String playerId, World world) implements java.io.Serializable, Message {};

    public interface Message {};
}
