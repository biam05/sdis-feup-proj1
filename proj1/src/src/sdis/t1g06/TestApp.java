package sdis.t1g06;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Locale;
import java.util.Scanner;

public class TestApp {

    public static void main(String[] args) throws RemoteException {

        // check usage
        if (args.length < 2) {
            System.out.println("Usage: java TestApp <peer_ap> <sub_protocol> <opnds>");
            return;
        }

        ServiceInterface stub;

        try {
            Registry registry = LocateRegistry.getRegistry();
            stub = (ServiceInterface) registry.lookup(args[0]);
        } catch (Exception e) {
            System.err.println("TestApp: App exception: " + e.toString());
            return;
        }

        switch (args[1].toUpperCase(Locale.ROOT)) {
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
                System.out.println("response: " + response);
            }
            case "RESTORE" -> {
                System.out.println("> TestApp: RESTORE Operation");
                if (args.length != 3) {
                    System.err.println("Wrong number of arguments given for RESTORE operation");
                    return;
                }
                String file_name = args[2];
                String response = stub.restore(file_name);
                System.out.println("response: " + response);
            }
            case "DELETE" -> {
                System.out.println("> TestApp: DELETE Operation");
                if (args.length != 3) {
                    System.err.println("Wrong number of arguments given for RESTORE operation");
                    return;
                }
                String file_name = args[2];
                String response = stub.delete(file_name);
                System.out.println("response: " + response);
            }
            case "RECLAIM" -> System.out.println("> TestApp: RECLAIM Operation");
            case "STATE" -> System.out.println("> TestApp: STATE Operation");
            default -> System.err.println("TestApp: Invalid operation requested");
        }

        Scanner myObj = new Scanner(System.in);  // Create a Scanner object
        String userName = myObj.nextLine();  // Read user input
    }
}
