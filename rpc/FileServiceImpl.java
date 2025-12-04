import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class FileServiceImpl extends UnicastRemoteObject implements FileService {
    private final Path baseDir;

    public FileServiceImpl(Path baseDir) throws RemoteException {
        super();
        this.baseDir = baseDir;
    }

    @Override
    public void upload(String name, byte[] data) throws RemoteException {
        String safeName = Paths.get(name).getFileName().toString();
        Path target = baseDir.resolve(safeName);
        try {
            Files.createDirectories(baseDir);
            Files.write(target, data);
            System.out.printf("[+] Stored %s (%d bytes)%n", safeName, data.length);
        } catch (IOException e) {
            throw new RemoteException("Failed to save file: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] download(String name) throws RemoteException, FileNotFoundException {
        String safeName = Paths.get(name).getFileName().toString();
        Path target = baseDir.resolve(safeName);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new FileNotFoundException("File not found: " + safeName);
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            System.out.printf("[+] Served %s (%d bytes)%n", safeName, bytes.length);
            return bytes;
        } catch (IOException e) {
            throw new RemoteException("Failed to read file: " + e.getMessage(), e);
        }
    }
}
