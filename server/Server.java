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
        System.out.println("Server started on port " + PORT);

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
            while (true) {
                String command = dis.readUTF();
                if (command.equalsIgnoreCase("LIST")) {
                    File dir = new File(FILES_DIR);
                    String[] files = dir.list((d, name) -> new File(d, name).isFile());
                    dos.writeInt(files.length);
                    for (String file : files) dos.writeUTF(file);
                } else if (command.startsWith("GET")) {
                    String[] parts = command.split(" ");
                    for (int i = 1; i < parts.length; i++) {
                        sendFile(parts[i], dos);
                    }
                } else if (command.equalsIgnoreCase("QUIT")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendFile(String fileName, DataOutputStream dos) throws IOException {
        File file = new File(FILES_DIR, fileName);
        if (!file.exists()) {
            dos.writeUTF("ERROR");
            return;
        }

        dos.writeUTF("OK");
        dos.writeUTF(fileName);
        long fileSize = file.length();
        dos.writeLong(fileSize);

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