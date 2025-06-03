package Client;

import Shared.Message;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Scanner;

public class Client {
    private static DataOutputStream out;
    private static String username;
    private static String fileList;
    private static String loginResponse;
    private static Socket socket;
    private static Message metadata;
    private static byte[] downloadedFileData;

    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket("localhost", 12345)) {
            Client.socket = socket;
            out = new DataOutputStream(socket.getOutputStream());

            ClientReceiver receiver = new ClientReceiver(socket);
            new Thread(receiver).start();

            Scanner scanner = new Scanner(System.in);

            System.out.println("===== Welcome to CS Music Room =====");

            boolean loggedIn = false;
            while (!loggedIn) {
                System.out.print("Username: ");
                username = scanner.nextLine();
                System.out.print("Password: ");
                String password = scanner.nextLine();

                sendLoginRequest(username, password);

                while (loginResponse == null) {
                    Thread.sleep(100);
                }

                if (loginResponse.equals("success")) {
                    loggedIn = true;
                    System.out.println("Successfully connected to the server! Welcome, " + username + "!");
                    loginResponse = null;
                } else {
                    System.out.println("Invalid username or password. Please try again.");
                    loginResponse = null;
                }
            }

            while (true) {
                printMenu();
                System.out.print("Enter choice: ");
                String choice = scanner.nextLine();

                switch (choice) {
                    case "1" -> enterChat(scanner);
                    case "2" -> uploadFile(scanner);
                    case "3" -> requestDownload(scanner);
                    case "0" -> {
                        System.out.println("Exiting...");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            }

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println("\n--- Main Menu ---");
        System.out.println("1. Enter chat box");
        System.out.println("2. Upload a file");
        System.out.println("3. Download a file");
        System.out.println("0. Exit");
    }

    private static void sendLoginRequest(String username, String password) throws IOException {
        Gson gson = new Gson();
        Message message = new Message(Message.LOGIN, username, password);
        out.writeBytes(gson.toJson(message) + "\n");
        out.flush();
    }

    private static void enterChat(Scanner scanner) throws IOException {
        System.out.println("You have entered the chat");

        String messageString = "";
        while (!messageString.equalsIgnoreCase("/exit")) {
            messageString = scanner.nextLine();

            if (!messageString.equalsIgnoreCase("/exit")) {
                sendChatMessage(messageString);
            } else {
                Gson gson = new Gson();
                Message logoutMessage = new Message(Message.LOGOUT, username, "");
                out.writeBytes(gson.toJson(logoutMessage) + "\n");
                out.flush();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.out.println("Interrupted while waiting for logout: " + e.getMessage());
                }
            }
        }

        socket.close();
    }

    private static void sendChatMessage(String messageToSend) throws IOException {
        Gson gson = new Gson();
        Message message = new Message(Message.CHAT, username, messageToSend);
        out.writeBytes(gson.toJson(message) + "\n");
        out.flush();
    }

    private static void uploadFile(Scanner scanner) throws IOException {
        File clientDir;
        String resourcePath = "Client/" + username;
        try {
            URL resourceUrl = Client.class.getClassLoader().getResource(resourcePath);
            if (resourceUrl == null) {
                throw new FileNotFoundException("Resource directory not found: " + resourcePath);
            }
            clientDir = new File(resourceUrl.toURI());
        } catch (Exception e) {
            System.out.println("Error accessing client resource directory: " + e.getMessage());
            return;
        }

        File[] files = clientDir.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No files found in " + clientDir.getAbsolutePath() + ". Please add files to src/main/resources/Client/" + username + " and try again.");
            return;
        }

        System.out.println("Select a file to upload:");
        for (int i = 0; i < files.length; i++) {
            System.out.println((i + 1) + ". " + files[i].getName());
        }

        System.out.print("Enter file number: ");
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine()) - 1;
        } catch (NumberFormatException e) {
            System.out.println("Invalid choice. Please enter a valid number.");
            return;
        }

        if (choice < 0 || choice >= files.length) {
            System.out.println("Invalid choice. Please select a number between 1 and " + files.length + ".");
            return;
        }

        File file = files[choice];
        byte[] fileData = readFileToByteArray(file);

        Gson gson = new Gson();
        Message metadata = new Message(Message.FILE_UPLOAD, username, file.getName(), file.length());
        out.writeBytes(gson.toJson(metadata) + "\n");
        out.flush();

        out.write(fileData);
        out.flush();

        System.out.println("File " + file.getName() + " uploaded successfully.");
        System.out.println("Please refresh the project in IntelliJ (Ctrl+Alt+Y) to see the updated files.");
    }

    private static byte[] readFileToByteArray(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        }
    }

    private static void requestDownload(Scanner scanner) throws IOException {
        Gson gson = new Gson();
        Message request = new Message(Message.FILE_LIST, username, "");
        out.writeBytes(gson.toJson(request) + "\n");
        out.flush();
        while (fileList == null) {
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }

        if (fileList.equals("NO_FILES") || fileList.isEmpty()) {
            System.out.println("No files available on server.");
            fileList = null;
            return;
        }

        String[] files = fileList.split(",");
        System.out.println("Available files on server:");
        for (int i = 0; i < files.length; i++) {
            System.out.println((i + 1) + ". " + files[i]);
        }

        System.out.print("Select file number to download: ");
        int choice = Integer.parseInt(scanner.nextLine()) - 1;

        if (choice < 0 || choice >= files.length) {
            System.out.println("Invalid choice.");
            fileList = null;
            return;
        }

        String fileName = files[choice];
        Message downloadRequest = new Message(Message.FILE_DOWNLOAD, username, fileName);
        out.writeBytes(gson.toJson(downloadRequest) + "\n");
        out.flush();

        while (metadata == null || downloadedFileData == null) {
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }

        File clientDir = new File("src/main/resources/Client/" + username);
        if (!clientDir.exists()) clientDir.mkdirs();

        File file = new File(clientDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(downloadedFileData);
            System.out.println("File downloaded successfully to: " + file.getAbsolutePath());
            System.out.println("Please refresh the project in IntelliJ (Ctrl+Alt+Y) to see the updated files.");
        } catch (IOException e) {
            System.err.println("Failed to download file " + file.getAbsolutePath() + ": " + e.getMessage());
            throw e;
        }

        // Request updated file list after download
        fileList = null;
        Message refreshRequest = new Message(Message.FILE_LIST, username, "");
        out.writeBytes(gson.toJson(refreshRequest) + "\n");
        out.flush();

        fileList = null;
        metadata = null;
        downloadedFileData = null;
    }

    public static void setFileList(String fileList) {
        Client.fileList = fileList;
    }

    public static void setLoginResponse(String response) {
        Client.loginResponse = response;
    }

    public static void setMetadata(Message metadata) {
        Client.metadata = metadata;
    }

    public static void setDownloadedFileData(byte[] data) {
        Client.downloadedFileData = data;
    }
}