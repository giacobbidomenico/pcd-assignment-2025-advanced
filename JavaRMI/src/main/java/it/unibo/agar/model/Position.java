package it.unibo.agar.model;

public record Position(double x, double y) {

    public static Position ZERO = new Position(0.0, 0.0);

    public static Position of(final double x, final double y) {
        return new Position(x, y);
    }
}
