# PCD Assignment - Optional Exercise with Java RMI

In this repository there is a centralized version of the game Agar.IO implemented in Java. 
The game currently works with a local architecture where the view, game logic, and player interactions all exist within the same JVM.

This assignment focuses on distributing the application using Java RMI,
allowing players to connect from different machines while maintaining the core gameplay mechanics. Students will need to:

1. Split the application into server (GameManager) and client (LocalView) components -- or choose any other splitting such that the player can connect and play
2. Implement remote interfaces for game state management
3. Handle distributed player interactions
4. Ensure proper synchronization of the game state across the network
5. Manage client connections and disconnections gracefully

The goal is to create a distributed version of the game where multiple players can join from different machines.
