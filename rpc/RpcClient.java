import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RpcClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase();
        if (!mode.equals("upload") && !mode.equals("download")) {
            printUsage();
            return;
        }

        if (mode.equals("upload")) {
            doUpload(args);
        } else {
            doDownload(args);
        }
    }

    private static void doUpload(String[] args) throws IOException, NotBoundException {
        String filePathArg = args[1];
        Path filePath = Paths.get(filePathArg).toAbsolutePath();
        if (!Files.isRegularFile(filePath)) {
            System.err.println("File not found: " + filePath);
            return;
        }

        String remoteName = args.length > 2 ? args[2] : filePath.getFileName().toString();
        String host = args.length > 3 ? args[3] : "127.0.0.1";
        int port = args.length > 4 ? Integer.parseInt(args[4]) : 1099;

        byte[] data = Files.readAllBytes(filePath);

        FileService service = lookup(host, port);
        service.upload(remoteName, data);

        System.out.printf("[+] Uploaded %s as %s (%d bytes)%n",
                filePath.getFileName(), remoteName, data.length);
    }

    private static void doDownload(String[] args) throws IOException, NotBoundException {
        String remoteName = args[1];
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        int port = args.length > 3 ? Integer.parseInt(args[3]) : 1099;
        Path outputPath = args.length > 4
                ? Paths.get(args[4]).toAbsolutePath()
                : Paths.get(remoteName).toAbsolutePath();

        FileService service = lookup(host, port);
        byte[] data = service.download(remoteName);

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputPath, data);

        System.out.printf("[+] Downloaded %s to %s (%d bytes)%n",
                remoteName, outputPath, data.length);
    }

    private static FileService lookup(String host, int port) throws NotBoundException, IOException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (FileService) registry.lookup("FileService");
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  Upload:   java -cp rpc RpcClient upload <file_path> [remote_name] [host] [port]");
        System.err.println("  Download: java -cp rpc RpcClient download <remote_name> [host] [port] [output_path]");
        System.err.println("Defaults: host=127.0.0.1, port=1099; output_path defaults to remote name.");
    }
}
