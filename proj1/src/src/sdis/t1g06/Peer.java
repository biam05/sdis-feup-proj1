package sdis.t1g06;

import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Peer implements ServiceInterface {

    private static double protocol_version;
    private static int peer_id;
    private static String peer_path;
    private static String service_access_point;

    private static String mc_maddress;
    private static int mc_port;
    private static String mdb_maddress;
    private static int mdb_port;
    private static String mdr_maddress;
    private static int mdr_port;

    private static Channel mc;
    private static Channel mdb;
    private static Channel mdr;

    private Peer(double protocol_version, int peer_id, String service_access_point, String mc_maddress,
                 int mc_port, String mdb_maddress, int mdb_port, String mdr_maddress, int mdr_port) {
        Peer.protocol_version = protocol_version;
        Peer.peer_id = peer_id;
        Peer.peer_path = "peer " + peer_id + "/";
        Peer.service_access_point = service_access_point;

        Peer.mc_maddress = mc_maddress;
        Peer.mc_port = mc_port;
        Peer.mdb_maddress = mdb_maddress;
        Peer.mdb_port = mdb_port;
        Peer.mdr_maddress = mdr_maddress;
        Peer.mdr_port = mdr_port;
    }

    public static void main(String[] args) {
        // check usage
        if (args.length < 9) {
            System.out.println("Usage: java Peer <protocol_version> <peer_id> <service_access_point> " +
                    "<mc_maddress> <mc_port> <mdb_maddress> <mdb_port> <mdr_maddress> <mdr_port>");
            return;
        }

        double prot_version;
        int pID, mcport, mdbport, mdrport;
        String saccesspoint, mcaddress, mdbaddress, mdraddress;

        try {
            pID = Integer.parseInt(args[1]);
        } catch(NumberFormatException e){
            System.err.println("> Peer ? exception: error parsing <peer_id>.");
            return;
        }

        try {
            prot_version = Double.parseDouble(args[0]);
        } catch(NumberFormatException e){
            System.err.println("> Peer " + pID + " exception: error parsing <protocol_version>.");
            return;
        }

        try {
            mcport = Integer.parseInt(args[4]);
            mdbport = Integer.parseInt(args[6]);
            mdrport = Integer.parseInt(args[8]);
        } catch(NumberFormatException e){
            System.err.println("> Peer " + pID + " exception: error parsing multicast channel's ports.");
            return;
        }

        saccesspoint = args[2];
        mcaddress = args[3];
        mdbaddress = args[5];
        mdraddress = args[7];

        // Create peer directory
        try {
            Files.createDirectories(Paths.get("peer " + pID));
        } catch (Exception e) {
            System.err.println("> Peer " + pID + " exception: failed to create peer directory");
            return;
        }

        Peer peer = new Peer(prot_version, pID, saccesspoint, mcaddress, mcport, mdbaddress, mdbport,
                mdraddress, mdrport);

        if(pID == 1) peer.startRMI(); // initiator-peer

        try {
            openChannels();
        } catch (UnknownHostException e) {
            System.err.println("> Peer " + pID + ": Failed to find channels");
            return;
        }

        int attempts_mc = 0, attempts_mdb = 0, attempts_mdr = 0;
        while(!mc.isActive() || !mdb.isActive() || !mdr.isActive()) { // Peer is ready only when the 3 channels are active
            if(mc.hasFailed())  {
                if(attempts_mc >= 3) {
                    System.err.println("> Peer " + pID + ": Failed to connect to \"MC\" channel");
                    return;
                }
                mc.start();
                attempts_mc++;
            }
            if(mdb.hasFailed()) {
                if(attempts_mdb >= 3) {
                    System.err.println("> Peer " + pID + ": Failed to connect to \"MDB\" channels");
                    return;
                }
                mdb.start();
                attempts_mdb++;
            }
            if(mdr.hasFailed()) {
                if(attempts_mdr >= 3) {
                    System.err.println("> Peer " + pID + ": Failed to connect to \"MDR\" channels");
                    return;
                }
                mdr.start();
                attempts_mdr++;
            }
        }

        ExecutorService mdbWorkerThreads = Executors.newFixedThreadPool(5);

        mdbWorkerThreads.submit(() -> {
            while(true) {
                if(mdb.hasMessage()) {
                    treatMessage(mdb.getPacket());
                    mdb.setHasMessage(false);
                }
            }
        });

        System.out.println("> Peer " + pID + ": Ready");
    }

    public void startRMI() {
        String codeBasePath = "out/production/proj1/sdis/t1g06/";
        String policyfilePath = "rmipolicy/my.policy/";
        System.setProperty("java.rmi.server.codebase", codeBasePath);
        System.setProperty("java.security.policy", policyfilePath);
        if(System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }

        try {
            ServiceInterface stub = (ServiceInterface) UnicastRemoteObject.exportObject(this, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.createRegistry(4445);
            registry.rebind("ServiceInterface", stub);

            System.out.println("> Peer " + peer_id + ": RMI service registered");
        } catch (Exception e) {
            System.err.println("> Peer " + peer_id + ": Failed to register RMI service");
        }
    }

    public static void treatMessage(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength());
        String[] parts = message.split("\\s+"); // returns an array of strings (String[]) without any " " results

        // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
        //      CRLF == \r\n

        double version = Double.parseDouble(parts[0]);
        String control = parts[1];
        int sender_id = Integer.parseInt(parts[2]);
        String file_id = parts[3];
        int chunk_no = Integer.parseInt(parts[4]);
        int rep_degree = Integer.parseInt(parts[5]);
        String content = message.substring(message.indexOf("\r\n\r\n") + 4);

        if(control.equalsIgnoreCase("PUTCHUNK")) {
            System.out.println("Chegou aqui");
        } else {
            System.err.println("> Peer " + peer_id + ": Message with invalid control \"" + control + "\" received");
        }
    }

    public static void openChannels() throws UnknownHostException {
        mc = new Channel(peer_id, mc_maddress, mc_port, "MC");
        mdb = new Channel(peer_id, mdb_maddress, mdb_port, "MDB");
        mdr = new Channel(peer_id, mdr_maddress, mdr_port, "MDR");

        mc.start();
        mdb.start();
        mdr.start();
    }

    public static double getProtocolVersion() {
        return protocol_version;
    }

    public static int getPeerID() {
        return peer_id;
    }

    public static String getServiceAccessPoint() {
        return service_access_point;
    }

    /**
     * The backup service splits each file in chunks and then backs up each chunk independently,
     * rather than creating multiple files that are a copy of the file to backup.
     */
    @Override
    public String backup(String filePath, int replicationDegree) {
        FileManager filemanager = new FileManager(peer_path + filePath, replicationDegree);

        for(int i = 0; i < filemanager.getChunks().size(); i++){
            FileChunk fileChunk = filemanager.getChunks().get(i);
            //Each file is backed up with a desired replication degree
            //The service should try to replicate all the chunks of a file with the desired replication degree
            fileChunk.setDesiredReplicationDegree(replicationDegree);

            // header construction
            // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
            //      CRLF == \r\n
            String header = protocol_version + " PUTCHUNK " + peer_id + " " + filemanager.getFileID()
                    + " " + fileChunk.getChunkNo() + " " + replicationDegree + "\r\n\r\n";

            //System.out.println("> Peer " + peer_id + ": sent: " + header)

            byte[] message = new byte[header.getBytes().length + fileChunk.getContent().length];
            System.arraycopy(header.getBytes(), 0, message, 0, header.getBytes().length);
            System.arraycopy(fileChunk.getContent(), 0, message, header.getBytes().length, fileChunk.getContent().length);

            mdb.sendMessage(message);
        }

        return null;
    }
}
