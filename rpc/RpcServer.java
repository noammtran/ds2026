import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RpcServer {
    public static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0] : "received_files_rpc";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        Path baseDir = Paths.get(outputDir).toAbsolutePath();
        FileService service = new FileServiceImpl(baseDir);

        Registry registry = LocateRegistry.createRegistry(port);
        registry.rebind("FileService", service);

        System.out.printf("[+] RPC registry on port %d%n", port);
        System.out.printf("[+] FileService bound, storing files at %s%n", baseDir);
        System.out.println("[+] Press Ctrl+C to stop");
    }
}
