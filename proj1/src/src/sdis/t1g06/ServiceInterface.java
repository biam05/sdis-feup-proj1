package sdis.t1g06;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServiceInterface extends Remote {
    String backup(String file_name, int replicationDegree) throws RemoteException;
    String restore(String file_name) throws RemoteException;
    String delete(String file_name) throws RemoteException;
}
