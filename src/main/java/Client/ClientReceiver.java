package Client;

import Shared.Message;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;

public class ClientReceiver implements Runnable {
    private final DataInputStream in;

    public ClientReceiver(Socket socket) throws Exception {
        this.in = new DataInputStream(socket.getInputStream());
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

                if (message.type == Message.CHAT) {
                    System.out.println(message.content);
                } else if (message.type == Message.FILE_LIST) {
                    Client.setFileList(message.content);
                } else if (message.type == Message.LOGIN_RESPONSE) {
                    Client.setLoginResponse(message.content);
                } else if (message.type == Message.FILE_DOWNLOAD) {
                    if (message.content.startsWith("File not found")) {
                        System.out.println(message.content);
                    } else {
                        Client.setMetadata(message);
                        receiveFileData(message.content, (int) message.fileLength);
                    }
                } else if (message.type == Message.LOGOUT) {
                    System.out.println("Received logout signal from server.");
                    break; // Exit the loop to stop the thread
                }
            }
        } catch (Exception e) {
            System.out.println("Error in ClientReceiver: " + e.getMessage());
        } finally {
            try {
                in.close();
            } catch (Exception e) {
                System.out.println("Error closing DataInputStream: " + e.getMessage());
            }
        }
    }

    private void receiveFileData(String fileName, int fileLength) throws IOException {
        byte[] data = new byte[fileLength];
        int totalBytesRead = 0;
        while (totalBytesRead < fileLength) {
            int bytesRead = in.read(data, totalBytesRead, fileLength - totalBytesRead);
            if (bytesRead == -1) break;
            totalBytesRead += bytesRead;
        }
        System.out.println("Received file data for " + fileName + " (" + totalBytesRead + " bytes)");
        if (totalBytesRead == fileLength) {
            Client.setDownloadedFileData(data);
        } else {
            System.out.println("Incomplete file download: " + fileName);
            Client.setDownloadedFileData(null); // Indicate failure
        }
    }
}