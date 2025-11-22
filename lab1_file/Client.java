import java.io.BufferedInputStream;
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
            System.err.println("Usage: java Client <file_path> [host] [port] [remote_name]");
            return;
        }
        Path filePath = Paths.get(args[0]).toAbsolutePath();
        if (!Files.isRegularFile(filePath)) {
            System.err.println("File not found: " + filePath);
            return;
        }
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 9000;
        String remoteName = args.length > 3 ? args[3] : filePath.getFileName().toString();

        sendFile(host, port, filePath, remoteName);
    }

    private static void sendFile(String host, int port, Path filePath, String remoteName) throws IOException {
        long fileSize = Files.size(filePath);
        byte[] nameBytes = remoteName.getBytes("UTF-8");

        try (Socket socket = new Socket(host, port)) {
            System.out.println("[+] Connected to " + host + ":" + port);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            out.writeInt(nameBytes.length);
            out.writeLong(fileSize);
            out.write(nameBytes);

            byte[] buffer = new byte[CHUNK_SIZE];
            long start = System.nanoTime();

            try (BufferedInputStream fileIn = new BufferedInputStream(Files.newInputStream(filePath))) {
                int read;
                while ((read = fileIn.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            out.flush();

            double durationSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
            double speedMiB = (fileSize / 1024.0 / 1024.0) / Math.max(durationSeconds, 1e-6);

            System.out.printf("[+] Sent '%s' (%d bytes) in %.2fs (%.2f MiB/s)%n",
                    remoteName, fileSize, durationSeconds, speedMiB);

            byte[] reply = socket.getInputStream().readNBytes(1024);
            if (reply.length > 0) {
                System.out.println("[+] Server reply: " + new String(reply, "UTF-8").trim());
            } else {
                System.out.println("[!] No acknowledgement received");
            }
        }
    }
}
