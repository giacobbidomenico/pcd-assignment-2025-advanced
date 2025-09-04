package it.unibo.agar.view;

import javax.swing.*;
import java.awt.*;

public class GameOverPanel extends JPanel {

    public GameOverPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.BLACK);

        // Game Over Label
        JLabel gameOverLabel = new JLabel("Game Over!");
        gameOverLabel.setForeground(Color.RED);
        gameOverLabel.setFont(new Font("Arial", Font.BOLD, 50));
        gameOverLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        add(Box.createVerticalGlue());
        add(gameOverLabel);
        add(Box.createRigidArea(new Dimension(0, 20))); // Spacing
        add(Box.createRigidArea(new Dimension(0, 40))); // Spacing
        add(Box.createVerticalGlue());
    }
}