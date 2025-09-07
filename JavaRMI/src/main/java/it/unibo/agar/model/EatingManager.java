package it.unibo.agar.model;

public class EatingManager {

    private static final double MASS_MARGIN = 1.1; // 10% bigger to eat

    private static boolean collides(final Entity e1, final Entity e2) {
        return e1.distanceTo(e2) < (e1.getRadius() + e2.getRadius());
    }

    public static boolean canEatFood(final Player player, final Food food) {
        return collides(player, food) && player.getMass() > food.getMass();
    }

    public static boolean canEatPlayer(final Player player, final Player other) {
        return collides(player, other) && player.getMass() > other.getMass() * MASS_MARGIN;
    }
}
