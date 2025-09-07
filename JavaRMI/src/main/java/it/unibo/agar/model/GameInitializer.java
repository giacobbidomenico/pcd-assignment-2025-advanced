package it.unibo.agar.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class GameInitializer {

    private static final Random random = new Random();

    private GameInitializer() { }

    public static List<Player> initialPlayers(final int numPlayers,final int width, final int height, final double initialMass) {
        return IntStream.rangeClosed(1, numPlayers)
            .mapToObj(i -> new Player("p" + i, random.nextInt(width), random.nextInt(height), initialMass))
            .toList();
    }

    public static List<Player> initialPlayers(final int numPlayers, final int width, final int height) {
        return initialPlayers(numPlayers, width, height, 120.0);
    }

    public static List<Food> initialFoods(final int numFoods, final int width, final int height, final double initialMass) {
        return IntStream.rangeClosed(1, numFoods)
                .mapToObj(i -> new Food("f" + i, random.nextInt(width), random.nextInt(height), initialMass))
                .toList();
    }

    public static List<Food> initialFoods(int numFoods, int width, int height) {
        return initialFoods(numFoods, width, height, Food.DEFAULT_MASS);
    }
}
