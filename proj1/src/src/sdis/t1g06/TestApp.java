package sdis.t1g06;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Locale;

public class TestApp {

    public static void main(String[] args) throws RemoteException {
        // check usage
        if (args.length < 2) {
            System.out.println("Usage: java TestApp <peer_ap> <sub_protocol> <opnds>");
            return;
        }

        String host = "localhost";
        ServiceInterface stub;

        try {
            Registry registry = LocateRegistry.getRegistry(host, 4445);
            stub = (ServiceInterface) registry.lookup("ServiceInterface");
        } catch (Exception e) {
            System.err.println("TestApp: App exception: " + e.toString());
            e.printStackTrace();
            return;
        }

        switch (args[1].toUpperCase(Locale.ROOT)) {
            case "BACKUP" -> {
                System.out.println("> TestApp: BACKUP Operation");
                if (args.length != 4) {
                    System.err.println("Not enough arguments given for BACKUP operation");
                    return;
                }
                String filePath = args[2];
                int replicationDegree;
                try {
                    replicationDegree = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    System.err.println("Replication degree given is not a number");
                    return;
                }
                String response = stub.backup(filePath, replicationDegree);
                System.out.println("response: " + response);
            }
            case "RESTORE" -> System.out.println("> TestApp: RESTORE Operation");
            case "DELETE" -> System.out.println("> TestApp: DELETE Operation");
            case "RECLAIM" -> System.out.println("> TestApp: RECLAIM Operation");
            case "STATE" -> System.out.println("> TestApp: STATE Operation");
            default -> System.err.println("TestApp: Invalid operation requested");
        }
    }
}
