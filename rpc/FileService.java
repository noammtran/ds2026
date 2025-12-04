import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Simple RPC interface for file transfer.
 */
public interface FileService extends Remote {
    /**
     * Upload a file to the server.
     *
     * @param name desired filename on the server
     * @param data file content
     */
    void upload(String name, byte[] data) throws RemoteException;

    /**
     * Download a file from the server.
     *
     * @param name filename stored on the server
     * @return file content
     * @throws FileNotFoundException when the file does not exist
     */
    byte[] download(String name) throws RemoteException, FileNotFoundException;
}
