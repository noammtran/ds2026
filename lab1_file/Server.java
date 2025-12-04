import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server {
    private static final int CHUNK_SIZE = 64 * 1024;

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "0.0.0.0";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9000;
        String outputDir = args.length > 2 ? args[2] : "received_files";

        Path outDir = Paths.get(outputDir).toAbsolutePath();
        Files.createDirectories(outDir);

        try (ServerSocket serverSocket =
                new ServerSocket(port, 1, InetAddress.getByName(host))) {
            System.out.println("[+] Listening on " + host + ":" + port + ", saving to " + outDir);
            try (Socket socket = serverSocket.accept()) {
                System.out.println("[+] Connected: " + socket.getInetAddress() + ":" + socket.getPort());
                handleClient(socket, outDir);
            }
        }
    }

    private static void handleClient(Socket socket, Path outDir) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        byte op = in.readByte();
        if (op == 'U') {
            handleUpload(in, out, outDir);
        } else if (op == 'D') {
            handleDownload(in, out, outDir);
        } else {
            System.out.println("[!] Unknown operation: " + (char) op);
        }
    }

    private static void handleUpload(DataInputStream in, DataOutputStream out, Path outDir) throws IOException {
        int nameLength = in.readInt();
        long fileSize = in.readLong();

        byte[] nameBytes = in.readNBytes(nameLength);
        String rawName = new String(nameBytes, "UTF-8");
        String safeName = Paths.get(rawName).getFileName().toString();
        Path destination = outDir.resolve(safeName);

        long remaining = fileSize;
        byte[] buffer = new byte[CHUNK_SIZE];
        try (BufferedOutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(destination))) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = in.read(buffer, 0, toRead);
                if (read == -1) {
                    throw new IOException("Client disconnected with " + remaining + " bytes left");
                }
                fileOut.write(buffer, 0, read);
                remaining -= read;
            }
        }

        System.out.printf("[+] Received '%s' (%d bytes)%n", destination.getFileName(), fileSize);
        out.write(("OK " + destination.getFileName() + " " + fileSize + " bytes\n").getBytes("UTF-8"));
        out.flush();
    }

    private static void handleDownload(DataInputStream in, DataOutputStream out, Path outDir) throws IOException {
        int nameLength = in.readInt();
        byte[] nameBytes = in.readNBytes(nameLength);
        String rawName = new String(nameBytes, "UTF-8");
        Path filePath = outDir.resolve(Paths.get(rawName).getFileName().toString());

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            out.writeByte(1); // not found
            out.flush();
            System.out.println("[!] Download requested but file not found: " + filePath);
            return;
        }

        long size = Files.size(filePath);
        out.writeByte(0); // ok
        out.writeLong(size);

        byte[] buffer = new byte[CHUNK_SIZE];
        try (var fileIn = Files.newInputStream(filePath)) {
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        out.flush();

        System.out.printf("[+] Sent '%s' (%d bytes)%n", filePath.getFileName(), size);
    }
}
