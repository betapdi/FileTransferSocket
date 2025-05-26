package client;
import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    public static final String SERVER_HOST = "localhost";
    public static final int SERVER_PORT = 5001;
    public static final int CHUNK_SIZE = 1024 * 1024;
    

    public static void main(String[] args) {
        try (
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in)
        ) {
            while (true) {
                System.out.println("\nCommands: LIST, GET <file1> <file2>..., QUIT");
                System.out.print("> ");
                String input = scanner.nextLine();
                dos.writeUTF(input);

                if (input.equalsIgnoreCase("LIST")) {
                    int count = dis.readInt();
                    System.out.println("Available files:");
                    for (int i = 0; i < count; i++) {
                        System.out.println("- " + dis.readUTF());
                    }
                } else if (input.startsWith("GET")) {
                    String[] parts = input.split(" ");
                    for (int i = 1; i < parts.length; i++) {
                        receiveFile(dis);
                    }
                } else if (input.equalsIgnoreCase("QUIT")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receiveFile(DataInputStream dis) throws IOException {
        String status = dis.readUTF();
        if (!status.equals("OK")) {
            System.out.println("Error: File not found.");
            return;
        }

        String fileName = dis.readUTF();
        long fileSize = dis.readLong();
        long received = 0;

        try (FileOutputStream fos = new FileOutputStream("client_" + fileName)) {
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
