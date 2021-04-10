package sdis.t1g06;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Service Interface Class
 */
public interface ServiceInterface extends Remote {
    /**
     * Function used to Backup a File
     * @param file_name name of the file that is gonna be backed up
     * @param replicationDegree replication degree of the chunks
     * @return "Successfull BACKUP of the file"
     */
    String backup(String file_name, int replicationDegree) throws RemoteException;

    /**
     * Function used to Restore a File
     * @param file_name name of the file that is gonna be restored
     * @return "Successfull RESTORE of the file"
     */
    String restore(String file_name) throws RemoteException;

    /**
     * Function used to delete a File
     * @param file_name name of the file that is gonna be deleted
     * @return "Successfull DELETE of the file"
     */
    String delete(String file_name) throws RemoteException;

    /**
     * Function used to reclaim space in a peer
     * @param max_disk_space maximum amount of disk space (in KByte) that the service can use to store
     *                       the chunks
     * @return "Successful RECLAIM of space"
     */
    String reclaim(int max_disk_space) throws RemoteException;
}
