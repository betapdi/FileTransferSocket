# File Transfer Socket

A robust client-server file transfer application built using Java socket programming with multi-threading support. The system enables efficient transfer of files between multiple clients and a centralized server using a custom binary protocol.

## Architecture

**Client-Server Model**: TCP socket-based communication on port 5001
**Multi-Threading**: Server spawns dedicated threads for each client connection
**Protocol**: Custom binary protocol using DataInputStream/DataOutputStream
**Chunking**: 1MB chunk size for memory-efficient large file transfers

## Features

### Core Functionality
- **Multi-client support**: Concurrent client connections with thread-per-client model
- **Directory listing**: Browse available files on server
- **Batch downloads**: Download multiple files in a single command
- **Chunked transfer**: Automatic 1MB chunking for large files
- **Progress tracking**: Real-time download progress with percentage display
- **Unique client directories**: Isolated download spaces with random client IDs

### Technical Features
- **Error handling**: Graceful handling of missing files and connection issues
- **Memory efficiency**: Stream-based file transfer prevents memory overflow
- **Client identification**: Random 3-digit client IDs (100-999)
- **Binary protocol**: Efficient data transfer using structured binary format

## Compilation

Before running the server or client, compile the Java source files. From the project root directory, run:

```bash
javac server/*.java client/*.java
```

This will compile all the necessary files in both **server** and **client** packages.

## Communication Protocol

The system uses a structured binary protocol over TCP:

### Data Types
- **Int**: Client IDs, file counts, chunk sizes, part numbers
- **UTF**: Commands, filenames, status messages
- **Long**: File sizes  
- **Bytes**: File data chunks

### Command Flow
1. **Client Connection**: Client sends unique ID to server
2. **Command Processing**: Client sends UTF command strings
3. **Response Handling**: Server responds with structured binary data

### File Transfer Protocol
```
1. Client: "get filename1 filename2"
2. Server: For each file:
   - Status: "OK" or "ERROR" 
   - Filename: UTF string
   - File size: Long (if OK)
   - Data chunks: [part_number][chunk_size][chunk_data]
```

## How to Run

**Start Server**
```bash
java server.Server
```
Server starts on port 5001 and waits for client connections.

**Start Client(s)**
```bash
java client.Client
```
Each client gets a unique ID and download directory.

### Available Commands
- `list` - Display all available files on server
- `get <file1> <file2>...` - Download one or more files
- `quit` - Disconnect from server

### Usage Examples
```
> list
Available files:
- hamachi.msi
- PPL.pdf  
- README.md

> get hamachi.msi PPL.pdf
Downloading hamachi.msi part 1 .... 25%
Downloading hamachi.msi part 2 .... 50%
...
Download complete: hamachi.msi
Download complete: PPL.pdf

> quit
```

## File Structure

```
FileTransferSocket/
├── server/
│   ├── Server.java     # Multi-threaded server implementation
│   └── Server.class    # Compiled server
├── client/
│   ├── Client.java     # Client implementation with progress tracking
│   └── Client.class    # Compiled client
├── server_files/       # Files available for download
│   ├── hamachi.msi
│   ├── PPL.pdf
│   └── README.md
└── client_XXX_files/   # Auto-created client download directories
```

## Technical Specifications

- **Port**: 5001 (configurable via SERVER_PORT constant)
- **Chunk Size**: 1MB (1024 * 1024 bytes)
- **Client ID Range**: 100-999 (randomly generated)
- **Threading Model**: One thread per client connection
- **Memory Usage**: Streaming with fixed 1MB buffer
- **File Size Limit**: No explicit limit (depends on available disk space)

## Code Explanation - How It Works

### **Server.java - Line-by-Line Breakdown**

#### **Class Constants and Setup**
```java
public static final int PORT = 5001;
public static final String FILES_DIR = "server_files";
public static final int CHUNK_SIZE = 1024 * 1024; // 1MB
```
- **PORT**: Defines server port (5001) - clients must connect to this exact port
- **FILES_DIR**: Sets directory where server looks for files to serve
- **CHUNK_SIZE**: Defines chunk size (1MB) for breaking large files into manageable pieces

#### **Main Server Loop**
```java
public static void main(String[] args) throws IOException {
    ServerSocket serverSocket = new ServerSocket(PORT);
    System.out.println("\nServer started on port " + PORT + "\n");

    while (true) {
        Socket socket = serverSocket.accept();
        new Thread(() -> handleClient(socket)).start();
    }
}
```
- **`ServerSocket serverSocket = new ServerSocket(PORT)`**: Creates server socket bound to port 5001 - this is the "listening" socket
- **`while (true)`**: Infinite loop to continuously accept new client connections
- **`socket = serverSocket.accept()`**: **BLOCKING CALL** - waits until a client connects, then returns client socket
- **`new Thread(() -> handleClient(socket)).start()`**: **KEY CONCURRENCY LINE** - creates new thread for each client to handle multiple clients simultaneously

#### **Client Handler Method**
```java
private static void handleClient(Socket socket) {
    try (
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
    ) {
        int clientId = dis.readInt();
        System.out.println("Client connected: " + clientId + "\n");
```
- **DataInputStream/DataOutputStream**: Creates binary data streams for efficient communication (not text-based)
- **`int clientId = dis.readInt()`**: **FIRST COMMUNICATION** - reads client ID that client sends upon connection
- **System.out.println**: Logs which client connected for server monitoring

#### **Command Processing Loop**
```java
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
```
- **`String command = dis.readUTF()`**: Reads UTF command string from client
- **`dir.list((d, name) -> new File(d, name).isFile())`**: Gets list of files (filtering out directories)
- **`dos.writeInt(files.length)`**: **PROTOCOL STEP 1** - sends count of files first so client knows how many to expect
- **`dos.writeUTF(file)`**: **PROTOCOL STEP 2** - sends each filename as UTF string
- **`command.split(" ")`**: Parses "get" command and extracts filenames from command
- **`sendFile(parts[i], dos, clientId)`**: Loops through each requested file and sends it

#### **File Transfer Method**
```java
private static void sendFile(String fileName, DataOutputStream dos, int clientId) throws IOException {
    File file = new File(FILES_DIR, fileName);
    if (!file.exists()) {
        dos.writeUTF("ERROR");
        dos.writeUTF(fileName);
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
```
- **`File file = new File(FILES_DIR, fileName)`**: Creates File object pointing to requested file in server directory
- **`if (!file.exists())`**: **ERROR HANDLING** - if file doesn't exist, send error status and filename
- **`dos.writeUTF("OK")`**: **SUCCESS PATH** - send "OK" status, filename, and file size
- **`byte[] buffer = new byte[CHUNK_SIZE]`**: Creates 1MB buffer for reading file chunks
- **`while ((bytesRead = fis.read(buffer)) != -1)`**: **KEY LOOP** - reads file in chunks until end of file (-1)
- **`dos.writeInt(part)`**: Sends part number so client can track progress
- **`dos.writeInt(bytesRead)`**: Sends actual bytes read (last chunk may be smaller than 1MB)
- **`dos.write(buffer, 0, bytesRead)`**: Sends the actual file data chunk

### **Client.java - Line-by-Line Breakdown**

#### **Client Constants and Setup**
```java
public static final String SERVER_HOST = "localhost";
public static final int SERVER_PORT = 5001;
public static final int CHUNK_SIZE = 1024 * 1024;
```
- **SERVER_HOST**: Server address - hardcoded to localhost (same machine)
- **SERVER_PORT**: Must match server's port exactly for connection to work
- **CHUNK_SIZE**: Chunk size must match server's for proper file reconstruction

#### **Client Initialization**
```java
Random random = new Random();
int clientId = 100 + random.nextInt(900);
String clientDirName = "client_" + clientId + "_files";

File clientDir = new File(clientDirName);
if (!clientDir.exists()) {
    clientDir.mkdirs();
}
```
- **`int clientId = 100 + random.nextInt(900)`**: **UNIQUE ID GENERATION** - creates random 3-digit ID (100-999)
- **`String clientDirName = "client_" + clientId + "_files"`**: Creates unique directory name for this client instance
- **`clientDir.mkdirs()`**: **DIRECTORY CREATION** - ensures download directory exists

#### **Connection Establishment**
```java
try (
    Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
    DataInputStream dis = new DataInputStream(socket.getInputStream());
    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
    Scanner scanner = new Scanner(System.in)
    ) {
        dos.writeInt(clientId);
        dos.flush();
```
- **`Socket socket = new Socket(SERVER_HOST, SERVER_PORT)`**: **CONNECTION ATTEMPT** - tries to connect to server (will fail if server not running)
- **`DataInputStream/DataOutputStream`**: Creates binary data streams matching server's streams
- **`dos.writeInt(clientId)`**: **FIRST MESSAGE** - sends client ID to server for identification
- **`dos.flush()`**: **CRITICAL** - forces immediate send of data (without this, data might be buffered)

#### **Command Processing Loop**
```java
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
    }
}
```
- **`dos.writeUTF(input)`**: **SENDS COMMAND** - transmits user command to server
- **`int count = dis.readInt()`**: **LIST HANDLING** - reads file count first
- **`dis.readUTF()`**: Reads each filename from server
- **`receiveFile(dis, clientDir)`**: **GET HANDLING** - calls receiveFile for each requested file

#### **File Reception Method**
```java
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
}
```
- **`String status = dis.readUTF()`**: **RECEIVES STATUS** - "OK" or "ERROR" from server
- **`String fileName = dis.readUTF()`**: **RECEIVES FILENAME** - server echoes back the requested filename
- **`long fileSize = dis.readLong()`**: Receives total file size to know when download is complete
- **`while (received < fileSize)`**: **MAIN DOWNLOAD LOOP** - continues until all bytes received
- **`dis.readFully(buffer)`**: **CRITICAL** - ensures all chunk bytes are read (regular `read()` might not get all bytes)
- **`fos.write(buffer)`**: Writes chunk to file and updates received byte counter
- **`(received * 100) / fileSize`**: **PROGRESS CALCULATION** - calculates and displays percentage complete

### **Communication Protocol Flow**

#### **Connection Phase**
1. **Server**: `serverSocket.accept()` - waits for client
2. **Client**: `new Socket(SERVER_HOST, SERVER_PORT)` - connects to server
3. **Client**: `dos.writeInt(clientId)` - sends unique ID
4. **Server**: `dis.readInt()` - receives client ID and logs connection

#### **Command Phase - "list" Command**
```
Client → Server: "list" (UTF string)
Server → Client: file_count (int)
Server → Client: filename1 (UTF string)
Server → Client: filename2 (UTF string)
... (repeat for each file)
```

#### **Command Phase - "get" Command**
```
Client → Server: "get file1.txt file2.pdf" (UTF string)

For each requested file:
Server → Client: status ("OK" or "ERROR") (UTF string)
Server → Client: filename (UTF string)

If status == "OK":
Server → Client: file_size (long)

Then for each chunk:
Server → Client: part_number (int)
Server → Client: chunk_size (int)  
Server → Client: chunk_data (byte array)
... (repeat until all chunks sent)
```

### **Critical Lines for Functionality**

- **`dis.readFully(buffer)`**: Without this, file corruption occurs because regular `read()` doesn't guarantee all bytes are read
- **`dos.flush()`**: Without this, client ID might not be sent immediately, causing server to hang
- **`serverSocket.accept()`**: Blocking call that waits for clients - server won't work without this
- **`new Thread(() -> handleClient(socket)).start()`**: Without threading, only one client can connect at a time
- **Progress calculation**: `(received * 100) / fileSize` - shows download progress
- **Status check**: Prevents crashes when files don't exist
- **Unique directories**: Prevents file conflicts between multiple clients

> **Note:** Add your files to the `server_files` directory to make them available for download.
