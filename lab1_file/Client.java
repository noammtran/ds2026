import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Client {
    private static final int CHUNK_SIZE = 64 * 1024;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String mode;
        if (args[0].equalsIgnoreCase("upload") || args[0].equalsIgnoreCase("download")) {
            mode = args[0].toLowerCase();
        } else {
            mode = "upload"; // backward compatibility: first arg is file path
        }

        if (mode.equals("upload")) {
            int offset = args[0].equalsIgnoreCase("upload") ? 1 : 0;
            if (args.length - offset < 1) {
                printUsage();
                return;
            }
            Path filePath = Paths.get(args[offset]).toAbsolutePath();
            if (!Files.isRegularFile(filePath)) {
                System.err.println("File not found: " + filePath);
                return;
            }
            String host = args.length > offset + 1 ? args[offset + 1] : "127.0.0.1";
            int port = args.length > offset + 2 ? Integer.parseInt(args[offset + 2]) : 9000;
            String remoteName = args.length > offset + 3 ? args[offset + 3] : filePath.getFileName().toString();
            sendFile(host, port, filePath, remoteName);
        } else { // download
            if (args.length < 2) {
                printUsage();
                return;
            }
            String remoteName = args[1];
            String host = args.length > 2 ? args[2] : "127.0.0.1";
            int port = args.length > 3 ? Integer.parseInt(args[3]) : 9000;
            Path outputPath = args.length > 4
                    ? Paths.get(args[4]).toAbsolutePath()
                    : Paths.get(remoteName).toAbsolutePath();
            downloadFile(host, port, remoteName, outputPath);
        }
    }

    private static void sendFile(String host, int port, Path filePath, String remoteName) throws IOException {
        long fileSize = Files.size(filePath);
        byte[] nameBytes = remoteName.getBytes("UTF-8");

        try (Socket socket = new Socket(host, port)) {
            System.out.println("[+] Connected to " + host + ":" + port);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            out.writeByte('U');
            out.writeInt(nameBytes.length);
            out.writeLong(fileSize);
            out.write(nameBytes);

            byte[] buffer = new byte[CHUNK_SIZE];
            try (BufferedInputStream fileIn = new BufferedInputStream(Files.newInputStream(filePath))) {
                int read;
                while ((read = fileIn.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            out.flush();

            System.out.printf("[+] Sent '%s' (%d bytes)%n", remoteName, fileSize);

            byte[] reply = in.readNBytes(1024);
            if (reply.length > 0) {
                System.out.println("[+] Server reply: " + new String(reply, "UTF-8").trim());
            } else {
                System.out.println("[!] No acknowledgement received");
            }
        }
    }

    private static void downloadFile(String host, int port, String remoteName, Path outputPath) throws IOException {
        byte[] nameBytes = remoteName.getBytes("UTF-8");
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (Socket socket = new Socket(host, port)) {
            System.out.println("[+] Connected to " + host + ":" + port);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            out.writeByte('D');
            out.writeInt(nameBytes.length);
            out.write(nameBytes);
            out.flush();

            byte status = in.readByte();
            if (status != 0) {
                System.err.println("[!] Server reported file not found: " + remoteName);
                return;
            }

            long size = in.readLong();
            byte[] buffer = new byte[CHUNK_SIZE];
            long remaining = size;

            try (var fileOut = Files.newOutputStream(outputPath)) {
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = in.read(buffer, 0, toRead);
                    if (read == -1) {
                        throw new IOException("Connection closed unexpectedly with " + remaining + " bytes left");
                    }
                    fileOut.write(buffer, 0, read);
                    remaining -= read;
                }
            }

            System.out.printf("[+] Downloaded '%s' to '%s' (%d bytes)%n",
                    remoteName, outputPath, size);
        }
    }

    private static void printUsage() {
        System.err.println("Upload: java Client upload <file_path> [host] [port] [remote_name]");
        System.err.println("Download: java Client download <remote_name> [host] [port] [output_path]");
        System.err.println("Default mode (no 'upload' word): first arg is file_path for upload.");
    }
}
