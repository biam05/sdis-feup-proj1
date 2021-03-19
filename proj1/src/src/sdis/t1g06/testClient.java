package sdis.t1g06;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class testClient {

    public static void main(String[] args) {
        // check usage
        if (args.length < 2) {
            System.out.println("Usage: java TestApp <peer_ap> <sub_protocol> <opnds>");
            return;
        }

        String host = "localhost";
        ServiceInterface stub;

        try {
            Registry registry = LocateRegistry.getRegistry(host, 4445);
            stub = (ServiceInterface) registry.lookup(args[1]);
        } catch (Exception e) {
            System.err.println("TestClient: Client exception: " + e.toString());
            e.printStackTrace();
            return;
        }

        switch (args[1]) {
            case "BACKUP":
                if (args.length == 4) {
                    System.err.println("Not enough arguments given for BACKUP operation");
                    return;
                }
                String filePath = args[2];
                int replicationDegree;
                try {
                    replicationDegree = Integer.parseInt(args[3]);
                } catch(NumberFormatException e) {
                    System.err.println("Replication degree given is not a number");
                    return;
                }
                String response = stub.backup(filePath, replicationDegree);
                System.out.println("response: " + response);
                break;
            case "RESTORE":
                break;
            case "DELETE":
                break;
            case "RACLAIM":
                break;
            case "STATE":
                break;
            default:
                System.err.println("TestClient: Invalid operation requested");
        }
    }
}
