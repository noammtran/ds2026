import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        OutputStream out = socket.getOutputStream();

        int nameLength = in.readInt();
        long fileSize = in.readLong();

        byte[] nameBytes = in.readNBytes(nameLength);
        String rawName = new String(nameBytes, "UTF-8");
        String safeName = Paths.get(rawName).getFileName().toString();
        Path destination = uniquePath(outDir, safeName);

        long remaining = fileSize;
        byte[] buffer = new byte[CHUNK_SIZE];
        long start = System.nanoTime();

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

        double durationSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
        double speedMiB = (fileSize / 1024.0 / 1024.0) / Math.max(durationSeconds, 1e-6);

        System.out.printf("[+] Received '%s' (%d bytes) in %.2fs (%.2f MiB/s)%n",
                destination.getFileName(), fileSize, durationSeconds, speedMiB);
        out.write(("OK " + destination.getFileName() + " " + fileSize + " bytes\n").getBytes("UTF-8"));
        out.flush();
    }

    private static Path uniquePath(Path dir, String name) throws IOException {
        Path candidate = dir.resolve(name);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String base;
        String ext;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        } else {
            base = name;
            ext = "";
        }
        int counter = 1;
        while (true) {
            Path alt = dir.resolve(base + "_" + counter + ext);
            if (!Files.exists(alt)) {
                return alt;
            }
            counter++;
        }
    }
}
