package Server;

import Shared.Message;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final List<ClientHandler> allClients;
    private String username;

    public ClientHandler(Socket socket, List<ClientHandler> allClients) {
        this.socket = socket;
        this.allClients = allClients;
        try {
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String msg) throws IOException {
        out.writeBytes(msg + "\n");
        out.flush();
    }

    @Override
    public void run() {
        try {
            while (true) {
                StringBuilder jsonBuilder = new StringBuilder();
                int byteRead;
                while ((byteRead = in.read()) != -1) {
                    char c = (char) byteRead;
                    if (c == '\n') break;
                    jsonBuilder.append(c);
                }
                if (byteRead == -1) break;

                String json = jsonBuilder.toString();
                Gson gson = new Gson();
                Message message = gson.fromJson(json, Message.class);

                switch (message.type) {
                    case Message.LOGIN:
                        handleLogin(message.sender, message.content);
                        break;
                    case Message.CHAT:
                        broadcast(message.sender + ": " + message.content);
                        break;
                    case Message.FILE_LIST:
                        sendFileList();
                        break;
                    case Message.FILE_UPLOAD:
                        receiveFile(message.content, (int) message.fileLength);
                        break;
                    case Message.FILE_DOWNLOAD:
                        sendFile(message.content);
                        break;
                    case Message.LOGOUT:
                        allClients.remove(this);
                        if (username != null) {
                            broadcast(username + " left the chat.");
                        }
                        socket.close();
                        return; // Exit the run loop
                }
            }
        } catch (Exception e) {
            System.out.println("Error in ClientHandler: " + e.getMessage());
        } finally {
            try {
                allClients.remove(this);
                if (username != null) {
                    broadcast(username + " left the chat.");
                }
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    private void sendFile(String fileName) throws IOException {
        // Check in src/main/resources/Server/Files
        File serverDir = new File("src/main/resources/Server/Files");
        File serverFile = new File(serverDir, fileName);
        if (serverFile.exists()) {
            sendFileFromPath(serverFile);
            return;
        }

        // Also check via classpath in case it's not in the source directory yet
        try {
            URL resourceUrl = getClass().getClassLoader().getResource("Server/Files/" + fileName);
            if (resourceUrl != null) {
                File file = new File(resourceUrl.toURI());
                sendFileFromPath(file);
                return;
            }
        } catch (Exception e) {
            System.out.println("Error accessing resource file " + fileName + ": " + e.getMessage());
        }

        // If not found, send error
        Message error = new Message(Message.FILE_DOWNLOAD, "Server", "File not found: " + fileName);
        sendMessage(new Gson().toJson(error));
    }

    private void sendFileFromPath(File file) throws IOException {
        Gson gson = new Gson();
        Message metadata = new Message(Message.FILE_DOWNLOAD, "Server", file.getName(), file.length());
        sendMessage(gson.toJson(metadata));

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesSent = 0;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;
            }
            out.flush();
            System.out.println("Sent file " + file.getName() + " (" + totalBytesSent + " bytes)");
        }
    }

    private void receiveFile(String filename, int fileLength) throws IOException {
        byte[] data = new byte[fileLength];
        int totalBytesRead = 0;
        while (totalBytesRead < fileLength) {
            int bytesRead = in.read(data, totalBytesRead, fileLength - totalBytesRead);
            if (bytesRead == -1) break;
            totalBytesRead += bytesRead;
        }
        System.out.println("Received " + totalBytesRead + " bytes for file " + filename);
        if (totalBytesRead == fileLength) {
            saveUploadedFile(filename, data);
            broadcast(username + " uploaded file " + filename);
            // Immediately update the file list for all clients
            sendFileListToAllClients();
        } else {
            System.out.println("Incomplete file upload: " + filename);
        }
    }

    private void saveUploadedFile(String filename, byte[] data) throws IOException {
        File serverDir = new File("src/main/resources/Server/Files");
        if (!serverDir.exists()) {
            serverDir.mkdirs();
            System.out.println("Created directory: " + serverDir.getAbsolutePath());
        }

        File file = new File(serverDir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            System.out.println("Saved file to: " + file.getAbsolutePath());
            System.out.println("Please refresh the project in IntelliJ (Ctrl+Alt+Y) to see the updated files.");
        } catch (IOException e) {
            System.err.println("Failed to save file " + file.getAbsolutePath() + ": " + e.getMessage());
            throw e;
        }
    }

    private void sendFileList() throws IOException {
        List<String> fileNames = new ArrayList<>();

        // Get files from src/main/resources/Server/Files
        File serverDir = new File("src/main/resources/Server/Files");
        if (serverDir.exists()) {
            File[] serverFiles = serverDir.listFiles();
            if (serverFiles != null) {
                System.out.println("Found " + serverFiles.length + " items in directory: " + serverDir.getAbsolutePath());
                for (File file : serverFiles) {
                    System.out.println("Item: " + file.getName() + " | isFile: " + file.isFile() + " | isDirectory: " + file.isDirectory());
                    if (file.isFile()) {
                        fileNames.add(file.getName());
                    }
                }
            }
        } else {
            System.out.println("Directory does not exist: " + serverDir.getAbsolutePath());
        }

        // Also check via classpath to include any files not yet in the source directory
        try {
            URL resourceUrl = getClass().getClassLoader().getResource("Server/Files");
            if (resourceUrl != null) {
                File resourceDir = new File(resourceUrl.toURI());
                File[] resourceFiles = resourceDir.listFiles();
                if (resourceFiles != null) {
                    System.out.println("Found " + resourceFiles.length + " items in resource directory: " + resourceDir.getAbsolutePath());
                    for (File file : resourceFiles) {
                        System.out.println("Item: " + file.getName() + " | isFile: " + file.isFile() + " | isDirectory: " + file.isDirectory());
                        if (file.isFile() && !fileNames.contains(file.getName())) {
                            fileNames.add(file.getName());
                        }
                    }
                }
            } else {
                System.out.println("Resource directory not found: Server/Files");
            }
        } catch (Exception e) {
            System.out.println("Error accessing resource directory: " + e.getMessage());
        }

        String fileListStr = fileNames.isEmpty() ? "NO_FILES" : String.join(",", fileNames);
        System.out.println("Sending file list to client: " + fileListStr);
        Gson gson = new Gson();
        Message message = new Message(Message.FILE_LIST, "Server", fileListStr);
        sendMessage(gson.toJson(message));
    }

    private void sendFileListToAllClients() throws IOException {
        List<String> fileNames = new ArrayList<>();

        // Get files from src/main/resources/Server/Files
        File serverDir = new File("src/main/resources/Server/Files");
        if (serverDir.exists()) {
            File[] serverFiles = serverDir.listFiles();
            if (serverFiles != null) {
                for (File file : serverFiles) {
                    if (file.isFile()) {
                        fileNames.add(file.getName());
                    }
                }
            }
        }

        // Also check via classpath
        try {
            URL resourceUrl = getClass().getClassLoader().getResource("Server/Files");
            if (resourceUrl != null) {
                File resourceDir = new File(resourceUrl.toURI());
                File[] resourceFiles = resourceDir.listFiles();
                if (resourceFiles != null) {
                    for (File file : resourceFiles) {
                        if (file.isFile() && !fileNames.contains(file.getName())) {
                            fileNames.add(file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error accessing resource directory: " + e.getMessage());
        }

        String fileListStr = fileNames.isEmpty() ? "NO_FILES" : String.join(",", fileNames);
        Gson gson = new Gson();
        Message message = new Message(Message.FILE_LIST, "Server", fileListStr);
        synchronized (allClients) {
            for (ClientHandler client : allClients) {
                client.sendMessage(gson.toJson(message));
            }
        }
    }

    private void broadcast(String msg) throws IOException {
        Gson gson = new Gson();
        Message message = new Message(Message.CHAT, "Server", msg);
        String json = gson.toJson(message);
        synchronized (allClients) {
            for (ClientHandler client : allClients) {
                if (client != this) {
                    client.sendMessage(json);
                }
            }
        }
    }

    private void handleLogin(String username, String password) throws IOException {
        boolean authenticated = Server.authenticate(username, password);
        Gson gson = new Gson();
        Message response = new Message(Message.LOGIN_RESPONSE, "Server", authenticated ? "success" : "failure");
        sendMessage(gson.toJson(response));

        if (authenticated) {
            this.username = username;
            System.out.println("Client connected successfully: " + username + " (" + socket.getInetAddress() + ")");
            broadcast(username + " joined the chat!");
        }
    }
}