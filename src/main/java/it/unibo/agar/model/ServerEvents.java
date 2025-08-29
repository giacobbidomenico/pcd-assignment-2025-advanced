package it.unibo.agar.model;

public class ServerEvents {

    public record Registration () implements ServerEvent {};
    public record Unregistration () implements ServerEvent {};
    public record PlayerUpdate () implements ServerEvent {};

    public interface ServerEvent {};
}
