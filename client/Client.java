package client;

import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    public static final String SERVER_HOST = "localhost";
    public static final int SERVER_PORT = 5001;
    public static final int CHUNK_SIZE = 1024 * 1024;

    public static void main(String[] args) {
        Random random = new Random();
        int clientId = 100 + random.nextInt(900);
        String clientDirName = "client_" + clientId + "_files";

        File clientDir = new File(clientDirName);
        if (!clientDir.exists()) {
            clientDir.mkdirs();
        }

        System.out.println("\nClient ID: " + clientId);
        System.out.println("Download directory: " + clientDirName);

        try (
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in)
            ) {
                dos.writeInt(clientId);
                dos.flush();
                while (true) {
                System.out.println("\nCommands: list, get <file1> <file2>..., quit" + "\n");
                System.out.print("> ");
                String input = scanner.nextLine();
                dos.writeUTF(input);

                if (input.equalsIgnoreCase("list")) {
                    int count = dis.readInt();
                    System.out.println("\nAvailable files:");
                    for (int i = 0; i < count; i++) {
                        System.out.println("- " + dis.readUTF());
                    }
                } else if (input.startsWith("get")) {
                    String[] parts = input.split(" ");
                    for (int i = 1; i < parts.length; i++) {
                        receiveFile(dis, clientDir);
                    }
                } else if (input.equalsIgnoreCase("quit")) {
                    break;
                } else {
                    System.out.println("\nUnknown command. Please try again.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receiveFile(DataInputStream dis, File downloadDir) throws IOException {
        String status = dis.readUTF();
        String fileName = dis.readUTF();
        if (!status.equals("OK")) {
            System.out.println("File '" + fileName + "' does not exist.");
            return;
        }

        long fileSize = dis.readLong();
        long received = 0;

        try (FileOutputStream fos = new FileOutputStream(new File(downloadDir, fileName))) {
            while (received < fileSize) {
                int part = dis.readInt();
                int chunkSize = dis.readInt();
                byte[] buffer = new byte[chunkSize];
                dis.readFully(buffer);

                fos.write(buffer);
                received += chunkSize;

                int percent = (int) ((received * 100) / fileSize);
                System.out.printf("Downloading %s part %d .... %d%%%n", fileName, part, percent);
            }
        }

        System.out.println("Download complete: " + fileName);
    }
}
