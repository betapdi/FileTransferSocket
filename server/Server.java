package server;
import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    public static final int PORT = 5001;
    public static final String FILES_DIR = "server_files";
    public static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("\nServer started on port " + PORT + "\n");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handleClient(socket)).start();
        }
    }

    private static void handleClient(Socket socket) {
        try (
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
        ) {
            int clientId = dis.readInt();
            System.out.println("Client connected: " + clientId + "\n");
            while (true) {
                String command = dis.readUTF();
                if (command.equalsIgnoreCase("list")) {
                    File dir = new File(FILES_DIR);
                    String[] files = dir.list((d, name) -> new File(d, name).isFile());
                    dos.writeInt(files.length);
                    for (String file : files) dos.writeUTF(file);
                } else if (command.startsWith("get")) {
                    String[] parts = command.split(" ");
                    for (int i = 1; i < parts.length; i++) {
                        sendFile(parts[i], dos, clientId);
                    }
                } else if (command.equalsIgnoreCase("quit")) {
                    System.out.println("Client " + clientId + " disconnected." + "\n");
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendFile(String fileName, DataOutputStream dos, int clientId) throws IOException {
        File file = new File(FILES_DIR, fileName);
        if (!file.exists()) {
            dos.writeUTF("ERROR");
            dos.writeUTF(fileName);
            System.out.println("Client " + clientId + " requested non-exist file: " + fileName + "\n");
            return;
        }

        dos.writeUTF("OK");
        dos.writeUTF(fileName);
        long fileSize = file.length();
        dos.writeLong(fileSize);

        System.out.println("Sending file '" + fileName + "' to client " + clientId + "\n");

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int part = 1;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.writeInt(part);
                dos.writeInt(bytesRead);
                dos.write(buffer, 0, bytesRead);
                part++;
            }
        }
    }
}