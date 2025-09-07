package it.unibo.agar.view;

import it.unibo.agar.model.Entity;
import it.unibo.agar.model.Food;
import it.unibo.agar.model.Player;
import it.unibo.agar.model.World;

import java.awt.*;

public class AgarViewUtils {

    private static final Color PLAYER_BORDER_COLOR = Color.BLACK;
    private static final int PLAYER_LABEL_OFFSET_X = 10;
    private static final int PLAYER_LABEL_OFFSET_Y = 0;
    private static final Color[] PLAYER_PALETTE = {
            Color.BLUE, Color.ORANGE, Color.CYAN, Color.PINK,
            Color.YELLOW, Color.RED, Color.GREEN, Color.LIGHT_GRAY
    };

    private static Color getPlayerColor(String id) {
        if (id != null && id.startsWith("p")) {
            try {
                int index = Integer.parseInt(id.substring(1));
                return PLAYER_PALETTE[Math.abs(index -1) % PLAYER_PALETTE.length]; // abs for safety, -1 as p1 is often index 0
            } catch (NumberFormatException e) {
                // Fallback for non-numeric part after 'p'
                return Color.GRAY;
            }
        }
        return Color.GRAY; // Default color if ID format is unexpected
    }

    private record PositioningInfo(int x, int y, int radius) {
        public int diameter() {
            return radius * 2;
        }
    }

    private static PositioningInfo getPositioningInfo(final Entity entity, final double offsetX, final double offsetY) {
       final int radius = (int) entity.getRadius();
       final int x = (int) (entity.getX() - offsetX - radius);
       final int y = (int) (entity.getY() - offsetY - radius);
       return new PositioningInfo(x, y, radius);
    }
    public static void drawWorld(final Graphics2D g, final World world, final double offsetX, final double offsetY) {
        // Draw foods
        g.setColor(Color.GREEN);
        for (Food food : world.getFoods()) {
            var positioning = getPositioningInfo(food, offsetX, offsetY);
            g.fillOval(positioning.x, positioning.y, positioning.diameter(), positioning.diameter());
        }

        // Draw players
        for (Player player : world.getPlayers()) {
            var positioning = getPositioningInfo(player, offsetX, offsetY);
            g.setColor(getPlayerColor(player.getId()));
            g.fillOval(positioning.x, positioning.y, positioning.diameter(), positioning.diameter());
            // Draw player ID
            g.setColor(PLAYER_BORDER_COLOR);
            // Adjust label position to be relative to the player's actual center on screen
            int labelX = positioning.x - PLAYER_LABEL_OFFSET_X;
            int labelY = positioning.y - PLAYER_LABEL_OFFSET_Y;
            g.drawString(player.getId(), labelX, labelY);
        }
    }
}
