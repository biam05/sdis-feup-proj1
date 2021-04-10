package sdis.t1g06;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// TODO: Fazer função que ao iniciar um peer verifica se o ficheiro state existe, senão, verifica todos os ficheiros da pasta files e adiciona ao storedFiles
//  Fazendo isso, é preciso também na função backup alterar a forma como verifica se um ficheiro já foi backed up. Utilizar o occurrencies do peerContainer
//  ConcurrentHashMaps para acompanhar se o processo de algum subprotocolo ainda decorre, utilizando o key fileid e o value um boolean. Caso o chunk recebido
//  tenha tamanho menor que os 64k, mudar pra false, e ignorar msgs seguintes
//  Separar funções treatMessage por channels
//  Remove all unecessary printlns
//  Create worker thread pools instead doing "new" everywhere

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

    private static PeerContainer peerContainer;

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

        Peer.peerContainer = new PeerContainer(peer_id);
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

        Peer peer = new Peer(prot_version, pID, saccesspoint, mcaddress, mcport, mdbaddress, mdbport, mdraddress, mdrport);

        peer.createDirectories();

        peer.startAutoSave();

        peer.startRMI();

        try {
            openChannels();
        } catch (UnknownHostException e) {
            System.err.println("> Peer " + pID + ": Failed to find channels");
            return;
        }

        System.out.println("> Peer " + pID + ": Ready");
    }

    private void createDirectories() {
        // Create peer directory
        try {
            Files.createDirectories(Paths.get("peer " + peer_id + "\\files"));
            Files.createDirectories(Paths.get("peer " + peer_id + "\\chunks"));
        } catch (Exception e) {
            System.err.println("> Peer " + peer_id + " exception: failed to create peer directory");
        }
    }

    private synchronized void startAutoSave() {
        peerContainer.loadState();
        Executors.newScheduledThreadPool(5).scheduleAtFixedRate(() -> {
            peerContainer.saveState();
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void startRMI() {
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
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(peer_id + "", stub);

            System.out.println("> Peer " + peer_id + ": RMI service registered");
        } catch (Exception e) {
            System.err.println("> Peer " + peer_id + ": Failed to register RMI service");
            e.printStackTrace();
        }
    }

    // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
    //      CRLF == \r\n
    public synchronized static void treatMessage(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength());
        String[] parts = message.split("\\s+"); // returns an array of strings (String[]) without any " " results

        double version = Double.parseDouble(parts[0]);
        String control = parts[1].toUpperCase();
        String file_id = parts[3];

        switch (control) {
            case "PUTCHUNK" -> {
                int chunk_no = Integer.parseInt(parts[4]);
                byte[] content = getBody(packet);
                FileChunk chunk = new FileChunk(file_id, chunk_no, content, content.length);
                if (!peerContainer.addStoredChunk(chunk)) {
                    System.err.println("I already have this chunk, ignoring");
                    break;
                }
                Backup backup = new Backup(chunk, peer_id);
                peerContainer.decFreeSpace(chunk.getSize());
                backup.performBackup();
                peerContainer.incOccurences(file_id, chunk_no);
                int response_time = new Random().nextInt(401);
                new ScheduledThreadPoolExecutor(200).schedule(() -> {
                    String response = version + " STORED " + peer_id + " " + file_id + " " + chunk_no + "\r\n\r\n"; // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
                    mc.sendMessage(response.getBytes());
                }, response_time, TimeUnit.MILLISECONDS);
            }
            case "STORED" -> {
                int chunk_no = Integer.parseInt(parts[4]);
                boolean isOfMyInterest = false;
                for(FileManager file : peerContainer.getStoredFiles()) {
                    if (file.getFileID().equals(file_id)) {
                        isOfMyInterest = true;
                        break;
                    }
                }
                for(FileChunk chunk : peerContainer.getStoredChunks()) {
                    if (chunk.getChunkNo() == chunk_no) {
                        isOfMyInterest = true;
                        break;
                    }
                }
                if(isOfMyInterest) {
                    peerContainer.incOccurences(file_id, chunk_no);
                    System.out.println("Incremented occurence of chunk");
                    System.out.println(peerContainer.getOccurrences().get(PeerContainer.createKey(file_id, chunk_no)));
                }
            }
            case "GETCHUNK" -> {
                int chunk_no = Integer.parseInt(parts[4]);
                for(FileChunk chunk : peerContainer.getStoredChunks()) {
                    if (chunk.getFileID().equals(file_id) && chunk.getChunkNo() == chunk_no) {
                        int response_time = new Random().nextInt(401);
                        String header = version + " CHUNK " + peer_id + " " + file_id + " " + chunk_no + "\r\n\r\n"; // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
                        byte[] response = new byte[header.getBytes().length + chunk.getContent().length];
                        System.arraycopy(header.getBytes(), 0, response, 0, header.getBytes().length);
                        System.arraycopy(chunk.getContent(), 0, response, header.getBytes().length, chunk.getContent().length);
                        new ScheduledThreadPoolExecutor(200).schedule(() -> {
                            mdr.sendMessage(response);
                        }, response_time, TimeUnit.MILLISECONDS);
                        break;
                    }
                }
            }
            case "CHUNK" -> {
                int chunk_no = Integer.parseInt(parts[4]);
                Restore restore = new Restore(file_id, chunk_no, peer_id, peerContainer);
                restore.performRestore(packet);
            }
            case "DELETE" -> {
                Delete delete = new Delete(file_id, peerContainer);
                delete.performDelete();
            }
            default -> System.err.println("> Peer " + peer_id + ": Message with invalid control \"" + control + "\" received");
        }
    }

    public static byte[] getBody(DatagramPacket packet) {
        byte[] message = packet.getData();
        byte[] content = new byte[FileManager.CHUNK_MAX_SIZE];
        for(int i = 0; i < message.length - 3; i++) {
            if(message[i] == 0xD && message[i+1] == 0xA && message[i+2] == 0xD && message[i+3] == 0xA) {
                content = Arrays.copyOfRange(message, i+4, packet.getLength());
                break;
            }
        }
        return content;
    }

    public static String getPeerPath(int pID) {
        return "peer " + pID + "/";
    }

    public synchronized static void openChannels() throws UnknownHostException {
        mc = new Channel(peer_id, mc_maddress, mc_port, mc_maddress + ":" + mc_port, ChannelType.MC);
        mdb = new Channel(peer_id, mdb_maddress, mdb_port, mdb_maddress + ":" + mdb_port, ChannelType.MDB);
        mdr = new Channel(peer_id, mdr_maddress, mdr_port, mdr_maddress + ":" + mdr_port, ChannelType.MDR);

        new Thread(mc).start();
        new Thread(mdb).start();
        new Thread(mdr).start();
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

    public synchronized static PeerContainer getPeerContainer(){ return peerContainer;}

    /**
     * The backup service splits each file in chunks and then backs up each chunk independently,
     * rather than creating multiple files that are a copy of the file to backup.
     */
    @Override
    public synchronized String backup(String file_name, int replicationDegree) { // Called by the initiator peer

        FileManager filemanager = new FileManager(getPeerPath(peer_id) + "files/" + file_name, replicationDegree);
        if(peerContainer.getOccurrences().get(filemanager.getFileID()) != null) {
            System.out.println("This file is already backed up, ignoring command");
            return "Unsuccessful BACKUP of file " + file_name + ", backup of this file already exists";
        }
        if(filemanager.getChunks().size() == 0) return "Unsuccessful BACKUP of file " + file_name + ", this file does not exist on this peer's file system";
        peerContainer.addStoredFile(filemanager);

        for(int i = 0; i < filemanager.getChunks().size(); i++) {
            FileChunk fileChunk = filemanager.getChunks().get(i);
            //Each file is backed up with a desired replication degree
            //The service should try to replicate all the chunks of a file with the desired replication degree
            fileChunk.setDesiredReplicationDegree(replicationDegree);

            // header construction
            // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
            //      CRLF == \r\n
            String header = protocol_version + " PUTCHUNK " + peer_id + " " + filemanager.getFileID()
                    + " " + fileChunk.getChunkNo() + " " + replicationDegree + " \r\n\r\n";

            byte[] message = new byte[header.getBytes().length + fileChunk.getContent().length];
            System.arraycopy(header.getBytes(), 0, message, 0, header.getBytes().length);
            System.arraycopy(fileChunk.getContent(), 0, message, header.getBytes().length, fileChunk.getContent().length);

            sendMessagePUTCHUNKProtocol(message, filemanager, fileChunk, replicationDegree, 1);
        }

        return "Successful BACKUP of file " + file_name;
    }

    @Override
    public String restore(String file_name) throws RemoteException {
        for(FileManager file : peerContainer.getStoredFiles()) {
            if(file.getFile().getName().equals(file_name)) {
                for(FileChunk chunk : file.getChunks()) {
                    // header construction
                    // <Version> GETCHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
                    //      CRLF == \r\n
                    String header = protocol_version + " GETCHUNK " + peer_id + " " + file.getFileID() + " " +
                            chunk.getChunkNo() + " \r\n\r\n";

                    byte[] message = header.getBytes();

                    sendMessageGETCHUNKProtocol(message);
                }
                break;
            }
        }
        return "Successful RESTORE of file " + file_name;
    }

    @Override
    public String delete(String file_name) throws RemoteException{
        for(FileManager file : peerContainer.getStoredFiles()) {
            if(file.getFile().getName().equals(file_name)) {
                // header construction
                // <Version> DELETE <SenderId> <FileId> <CRLF><CRLF>
                //      CRLF == \r\n
                String header = protocol_version + " DELETE " + peer_id + " " + file.getFileID() +
                        " \r\n\r\n";

                byte[] message = header.getBytes();

                sendMessageDELETEProtocol(message);
                peerContainer.clearFileOccurences(file);
                peerContainer.deleteStoredFile(file);
                break;
            }
        }
        return "Successful DELETION of file " + file_name;
    }

    @Override
    public String reclaim(int max_disk_space) throws RemoteException{
        int space_to_free = peerContainer.getFreeSpace() - max_disk_space;
        if(space_to_free > 0){ // has to delete files
            for(FileChunk storedChunk : peerContainer.getStoredChunks()) {
                String key = PeerContainer.createKey(storedChunk.getFileID(), storedChunk.getChunkNo());
                storedChunk.setReplicationDegree(peerContainer.getOccurrences().get(key)); // Current Replication Degree
            }
            int deletedSpace = 0;
            peerContainer.getStoredChunks().sort(Collections.reverseOrder()); // > RD first to avoid backups
            for(FileChunk storedChunk : peerContainer.getStoredChunks()){
                if(deletedSpace >= space_to_free) break;
                deletedSpace += storedChunk.getSize();
                // header construction
                // <Version> REMOVED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
                //      CRLF == \r\n
                String header = protocol_version + " REMOVED " + peer_id + " " + storedChunk.getFileID() +
                        " " + storedChunk.getChunkNo() + " \r\n\r\n";

                byte[] message = header.getBytes();

                sendMessageREMOVEProtocol(message);
            }

        }
        peerContainer.setFreeSpace(max_disk_space - peerContainer.getFreeSpace());
        return "Successful RECLAIM";
    }

    private synchronized void sendMessagePUTCHUNKProtocol(byte[] message, FileManager filemanager, FileChunk fileChunk, int replicationDegree, int waitTime) {
        mdb.sendMessage(message);
        Integer actual_rep_degree = peerContainer.getOccurrences().get(PeerContainer.createKey(filemanager.getFileID(), fileChunk.getChunkNo()));
        new ScheduledThreadPoolExecutor(200).schedule(() -> {
            if(actual_rep_degree == null || actual_rep_degree < replicationDegree) {
                if(waitTime * 2 > 16) return;
                sendMessagePUTCHUNKProtocol(message, filemanager, fileChunk, replicationDegree, waitTime * 2);
            } else updatePeerStateAboutChunk(filemanager.getFileID(), fileChunk.getChunkNo(), peerContainer.getOccurrences().get(PeerContainer.createKey(filemanager.getFileID(), fileChunk.getChunkNo())));
        }, waitTime, TimeUnit.SECONDS);
    }

    private synchronized void sendMessageGETCHUNKProtocol(byte[] message) {
        mc.sendMessage(message);
    }

    private synchronized void sendMessageDELETEProtocol(byte[] message) {
        mc.sendMessage(message);
    }

    private synchronized void sendMessageREMOVEProtocol(byte[] message) {
        mc.sendMessage(message);
    }

    private synchronized void updatePeerStateAboutChunk(String fileID, int chunkNo, int actualRepDegree) {
        peerContainer.saveState();
        System.out.println("Save state here, repDeg: " + actualRepDegree);
    }
}
