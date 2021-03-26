package sdis.t1g06;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServiceInterface extends Remote {
    String backup(String filePath, int replicationDegree) throws RemoteException;
}
