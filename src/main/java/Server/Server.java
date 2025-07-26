package Server;

import Shared.User;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Server {
    private static final User[] users = {
            new User("user1", "1234"),
            new User("user2", "1234"),
            new User("user3", "1234"),
            new User("user4", "1234"),
            new User("user5", "1234"),
    };

    public static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(12345);
            System.out.println("Server running on port 12345");

            while (true) {
                // Accept a new client connection
                Socket clientSocket = serverSocket.accept();
                try {
                    ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                } catch (Exception e) {
                    // If an error occurs while handling the client, close the socket
                    try {
                        clientSocket.close();
                    } catch (Exception closeException) {
                        System.err.println("Error closing client socket: " + closeException.getMessage());
                    }
                    System.err.println("Error handling client: " + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error running server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (Exception e) {
                    System.err.println("Error closing server: " + e.getMessage());
                }
            }
        }
    }

    public static boolean authenticate(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                return true;
            }
        }
        return false;
    }
}