package Shared;

public class Message {
    // enum simulation (Simulating it for simplicity)
    public static final int LOGIN = 0;     // Login request
    public static final int CHAT = 1;      // chat message
    public static final int FILE_LIST = 2; // Request a list of files
    public static final int FILE_UPLOAD = 3; // File upload
    public static final int FILE_DOWNLOAD = 4; // Download the file
    public static final int LOGIN_RESPONSE = 5; // login response
    public static final int LOGOUT = 6;    // Logout request

    public int type;         // Message type
    public String sender;    // sender
    public String content;   // Content (e.g. chat text, file name, or JSON data)
    public long fileLength;  // File length (for upload and download)

    public Message() {}

    public Message(int type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
    }

    public Message(int type, String sender, String content, long fileLength) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.fileLength = fileLength;
    }
}