package sdis.t1g06;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Peer implements ServiceInterface {

    private static double protocol_version;
    private static int peer_id;
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
            prot_version = Double.parseDouble(args[0]);
        } catch(NumberFormatException e){
            System.err.println("Peer exception: error parsing <protocol_version>.");
            return;
        }

        try {
            pID = Integer.parseInt(args[1]);
        } catch(NumberFormatException e){
            System.err.println("Peer exception: error parsing <peer_id>.");
            return;
        }

        try {
            mcport = Integer.parseInt(args[4]);
            mdbport = Integer.parseInt(args[6]);
            mdrport = Integer.parseInt(args[8]);
        } catch(NumberFormatException e){
            System.err.println("Peer exception: error parsing multicast channel's ports.");
            return;
        }

        saccesspoint = args[3];
        mcaddress = args[5];
        mdbaddress = args[7];
        mdraddress = args[9];

        Peer peer = new Peer(prot_version, pID, saccesspoint, mcaddress, mcport, mdbaddress, mdbport,
                mdraddress, mdrport);

        System.out.println("> Peer Ready\n");

        try {
            openChannels();
        } catch (UnknownHostException e) {
            System.err.println("Peer exception: failed to open channels.");
            return;
        }
    }

    public static void openChannels() throws UnknownHostException {
        mc = new Channel(mc_maddress, mc_port, "MC");
        mdb = new Channel(mdb_maddress, mdb_port, "MDB");
        mdr = new Channel(mdr_maddress, mdr_port, "MDR");

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

    @Override
    /**
     * The backup service splits each file in chunks and then backs up each chunk independently,
     * rather than creating multiple files that are a copy of the file to backup.
     */
    public String backup(String filePath, int replicationDegree) {
        FileManager filemanager = new FileManager(filePath, replicationDegree);

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
            System.out.println("> Sent: " + header);

            // TODO: finish backup

        }


        return null;
    }
}
