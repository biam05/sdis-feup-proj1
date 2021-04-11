package sdis.t1g06;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Locale;

/**
 * Test App Class
 */
public class TestApp {

    /**
     * Main Function
     * @param args arguments passed in the command line
     */
    public static void main(String[] args) throws RemoteException {

        /**
         * Check Usage
         * java TestApp <peer_ap> <sub_protocol> <opnd_1> <opnd_2>
         *     <peer_ap> Is the peer's access point. This depends on the implementation
         *     <operation> Is the operation the peer of the backup service must execute. It can be either the triggering
         *     of the subprotocol to test, or the retrieval of the peer's internal state. In the first case it must be
         *     one of: BACKUP, RESTORE, DELETE, RECLAIM. To retrieve the internal state, the value of this argument must
         *     be STATE
         *     <opnd_1> Is either the path name of the file to backup/restore/delete, for the respective 3 subprotocols,
         *     or, in the case of RECLAIM the maximum amount of disk space (in KByte) that the service can use to store
         *     the chunks. In the latter case, the peer should execute the RECLAIM protocol, upon deletion of any chunk.
         *     The STATE operation takes no operands.
         *     <opnd_2> This operand is an integer that specifies the desired replication degree and applies only to the
         *     backup protocol (or its enhancement)
         */
        if (args.length < 2) {
            System.out.println("Usage: java TestApp <peer_ap> <sub_protocol> <opnds>");
            return;
        }

        ServiceInterface stub;

        try {
            Registry registry = LocateRegistry.getRegistry();
            stub = (ServiceInterface) registry.lookup(args[0]);
        } catch (Exception e) {
            System.err.println("TestApp: App exception: " + e);
            return;
        }

        // Different Commands
        switch (args[1].toUpperCase(Locale.ROOT)) {
            // Backup a File
            case "BACKUP" -> {
                System.out.println("> TestApp: BACKUP Operation");
                if (args.length != 4) {
                    System.err.println("Wrong number of arguments given for BACKUP operation");
                    return;
                }
                String file_name = args[2];
                int replicationDegree;
                try {
                    replicationDegree = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    System.err.println("Replication degree given is not a number");
                    return;
                }
                String response = stub.backup(file_name, replicationDegree);
                System.out.println("Response: " + response);
            }
            // Restore a File
            case "RESTORE" -> {
                System.out.println("> TestApp: RESTORE Operation");
                if (args.length != 3) {
                    System.err.println("Wrong number of arguments given for RESTORE operation");
                    return;
                }
                String file_name = args[2];
                String response = stub.restore(file_name);
                System.out.println("Response: " + response);
            }
            // Delete a File
            case "DELETE" -> {
                System.out.println("> TestApp: DELETE Operation");
                if (args.length != 3) {
                    System.err.println("Wrong number of arguments given for RESTORE operation");
                    return;
                }
                String file_name = args[2];
                String response = stub.delete(file_name);
                System.out.println("Response: " + response);
            }
            // Reclaim Space
            case "RECLAIM" -> {
                System.out.println("> TestApp: RECLAIM Operation");
                if (args.length != 3){
                    System.err.println("Wrong number of arguments given for RECLAIM operation");
                    return;
                }
                int space = Integer.parseInt(args[2]);
                String response = stub.reclaim(space); // TODO - TEST RECLAIM
                System.out.println("Response: " + response);
            }
            // Get Internal State
            case "STATE" -> System.out.println("> TestApp: STATE Operation");
            default -> System.err.println("TestApp: Invalid operation requested");
        }
    }
}
