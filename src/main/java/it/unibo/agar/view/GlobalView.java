package it.unibo.agar.view;

import it.unibo.agar.model.DistributedGameStateManager;
import it.unibo.agar.model.GameStateManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class GlobalView extends JFrame {

    private DistributedGameStateManager stateManager;
    private final GamePanel gamePanel;

    public GlobalView(DistributedGameStateManager gameStateManager) {
        stateManager = gameStateManager;
        setTitle("Agar.io - Global View (Java)");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setupWindowCloseListener();
        setPreferredSize(new Dimension(800, 800));

        this.gamePanel = new GamePanel(gameStateManager.getLocalGameStateManager());
        add(this.gamePanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    private void setupWindowCloseListener() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("Closing global view and notifying game over. Performing graceful shutdown...");
                stateManager.notifyClose();
            }
        });
    }

    public void repaintView() {
        if (gamePanel != null) {
            gamePanel.repaint();
        }
    }
}
