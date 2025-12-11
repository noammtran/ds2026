
package mpi;

import mpi.MPI;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileTransferMPI {

    private static final int CHUNK_SIZE = 64 * 1024;

    public static void main(String[] args) throws Exception {
        // MPJ Express returns the application args after stripping its own flags.
        String[] appArgs = MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int world = MPI.COMM_WORLD.Size();

        if (world < 2) {
            if (rank == 0) {
                System.err.println("Need at least 2 processes (sender + receiver)");
            }
            MPI.Finalize();
            return;
        }

        if (rank > 1) {
            MPI.Finalize();
            return;
        }

        if (appArgs.length < 1) {
            if (rank == 0) {
                System.err.println("Usage: mpirun -np 2 java FileTransferMPI <input_file> [output_path_on_receiver]");
            }
            MPI.Finalize();
            return;
        }

        if (rank == 0) {
            runSender(appArgs);
        } else {
            runReceiver(appArgs);
        }

        MPI.Finalize();
    }

    private static void runSender(String[] args) throws IOException {
        String inputPathStr = args[0];
        Path inputPath = Paths.get(inputPathStr).toAbsolutePath();
        if (!Files.isRegularFile(inputPath)) {
            System.err.println("[sender] File not found: " + inputPath);
            MPI.COMM_WORLD.Abort(2);
            return;
        }

        String remoteName;
        if (args.length >= 2) {
            remoteName = args[1];
        } else {
            remoteName = inputPath.getFileName().toString();
        }

        long fileSize = Files.size(inputPath);
        byte[] nameBytes = remoteName.getBytes(StandardCharsets.UTF_8);
        int[] nameLen = new int[]{nameBytes.length};
        long[] sizeArr = new long[]{fileSize};

        System.out.printf("[sender] Sending '%s' (%d bytes) to rank 1%n", remoteName, fileSize);

        // send metadata
        MPI.COMM_WORLD.Send(nameLen, 0, 1, MPI.INT, 1, 0);
        MPI.COMM_WORLD.Send(sizeArr, 0, 1, MPI.LONG, 1, 0);
        MPI.COMM_WORLD.Send(nameBytes, 0, nameBytes.length, MPI.BYTE, 1, 0);

        // send file content in chunks
        byte[] buffer = new byte[CHUNK_SIZE];
        long sent = 0;
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(inputPath.toFile()))) {
            int read;
            while (sent < fileSize && (read = in.read(buffer)) != -1) {
                int toSend = (int) Math.min(read, fileSize - sent);
                MPI.COMM_WORLD.Send(buffer, 0, toSend, MPI.BYTE, 1, 0);
                sent += toSend;
            }
        }

        System.out.printf("[sender] Done. Total sent: %d bytes%n", sent);
    }

    private static void runReceiver(String[] args) throws IOException {
        int[] nameLen = new int[1];
        long[] sizeArr = new long[1];

        MPI.COMM_WORLD.Recv(nameLen, 0, 1, MPI.INT, 0, 0);
        MPI.COMM_WORLD.Recv(sizeArr, 0, 1, MPI.LONG, 0, 0);

        byte[] nameBytes = new byte[nameLen[0]];
        MPI.COMM_WORLD.Recv(nameBytes, 0, nameLen[0], MPI.BYTE, 0, 0);
        String fileName = new String(nameBytes, StandardCharsets.UTF_8);

        String outputPathStr;
        if (args.length >= 2) {
            outputPathStr = args[1];
        } else {
            outputPathStr = fileName;
        }
        Path outputPath = Paths.get(outputPathStr).toAbsolutePath();
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        long fileSize = sizeArr[0];
        System.out.printf("[receiver] Receiving '%s' (%d bytes) -> %s%n", fileName, fileSize, outputPath);

        byte[] buffer = new byte[CHUNK_SIZE];
        long received = 0;
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputPath.toFile()))) {
            while (received < fileSize) {
                int toRecv = (int) Math.min(CHUNK_SIZE, fileSize - received);
                MPI.COMM_WORLD.Recv(buffer, 0, toRecv, MPI.BYTE, 0, 0);
                out.write(buffer, 0, toRecv);
                received += toRecv;
            }
        }

        System.out.printf("[receiver] Done. Stored %s (%d bytes)%n", outputPath, received);
    }
}
