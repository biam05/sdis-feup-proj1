package sdis.t1g06;

import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Peer Class - Represents a Peer used in the Distributed Backup Service
 */
public class Peer implements ServiceInterface {
    public static final int MAX_THREADS = 200;

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

    private static ServiceInterface stub = null;
    private static Registry registry = null;

    private static final ScheduledThreadPoolExecutor peerExecutors = new ScheduledThreadPoolExecutor(MAX_THREADS);

    private static ConcurrentHashMap<String, ScheduledFuture<String>> activeOps;

    private static PeerContainer peerContainer;

    /**
     * Peer Constructor
     * @param protocol_version version of the protocols this peer is using
     * @param peer_id unique number identifier that represents the peer
     * @param service_access_point name of the interface that supplies the client with the peer's functions
     * @param mc_maddress address of the Control Multicast Channel
     * @param mc_port port of the Control Multicast Channel
     * @param mdb_maddress address of the Backup Multicast Channel
     * @param mdb_port port of the Control Backup Channel
     * @param mdr_maddress address of the Restore Multicast Channel
     * @param mdr_port port of the Control Restore Channel
     */
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

        Peer.activeOps = new ConcurrentHashMap<>();

        Peer.peerContainer = new PeerContainer(peer_id);
    }

    /**
     * Main function
     * @param args - arguments used by the peer
     */
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

    /**
     * Function used to create Peer Directories
     */
    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get("peer " + peer_id + "\\files"));
            Files.createDirectories(Paths.get("peer " + peer_id + "\\chunks"));
        } catch (Exception e) {
            System.err.println("> Peer " + peer_id + " exception: failed to create peer directory");
        }
    }

    /**
     * Function used to load the state of the peer from a file state.ser, update it with any changes on the physical file system,
     * and initialize a thread that auto-saves the state of the peer every 3 seconds
     */
    private synchronized void startAutoSave() {
        peerContainer.loadState();
        peerContainer.updateState();
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            peerContainer.saveState();
        }, 0, 3, TimeUnit.SECONDS);
    }

    /**
     * Function used to setup the RMI Registry
     */
    private void startRMI() {
        String codeBasePath = "out/production/proj1/sdis/t1g06/";
        String policyfilePath = "rmipolicy/my.policy/";
        System.setProperty("java.rmi.server.codebase", codeBasePath);
        System.setProperty("java.security.policy", policyfilePath);

        if(System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }
        try {
            stub = (ServiceInterface) UnicastRemoteObject.exportObject(this, 0);
            registry = LocateRegistry.getRegistry();
            registry.rebind(service_access_point, stub);
            System.out.println("> Peer " + peer_id + ": RMI service registered");
        } catch (Exception e) {
            System.err.println("> Peer " + peer_id + ": Failed to register RMI service");
            e.printStackTrace();
        }
    }

    /**
     * Function used to treat the message that in a DatagramPacket
     * @param packet DatagramPacket with the message sent
     *               the "control" is the second argument of the message and specifies the type of message sent
     * "PUTCHUNK" - used in the BACKUP; sent by the initiator-peer to the MDB to backup a chunk
     * "STORED" - used in the BACKUP; sent by a peer that stored the chunk upon receiving the PUTCHUNK
     * "GETCHUNK" - used in the RESTORE; sent by the initiator-peer to the MC to get a chunk to restore
     * "CHUNK" - used in the RESTORE; sent by the peer to the MDR that has a copy of the specified chunk
     * "DELETE" - used in the DELETE; sent to the MC to delete the chunks belonging to the specified file and the file
     * "REMOVED" - used in the RECLAIM; sent to the MC when a peer deletes a copy of a chunk it has backed up
     */
    public static synchronized void treatMessage(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength());
        String[] parts = message.split("\\s+"); // returns an array of strings (String[]) without any " " results

        double version = Double.parseDouble(parts[0]);
        String control = parts[1].toUpperCase();
        String file_id = parts[3];

        switch (control) {
            // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
            case "PUTCHUNK" -> {
                int chunk_no = Integer.parseInt(parts[4]);
                int desired_rep_degree = Integer.parseInt(parts[5]);
                byte[] content = getBody(packet);
                FileChunk chunk = new FileChunk(file_id, chunk_no, content, content.length);
                chunk.setDesiredReplicationDegree(desired_rep_degree);
                String ChunkKey = PeerContainer.createKey(file_id, chunk_no);
                if(activeOps.containsKey(ChunkKey)) {
                    activeOps.get(ChunkKey).cancel(true);
                    activeOps.remove(ChunkKey);
                }
                if(peerContainer.getFreeSpace() - chunk.getSize() < 0) {
                    System.err.println("> Peer " + peer_id + ": I don't have enough space to store this chunk. Ignoring it");
                    break;
                }
                boolean ownedFile = false;
                for(FileManager fileManager : peerContainer.getStoredFiles()) {
                    if(fileManager.getFileID().equals(file_id)) {
                        System.err.println("> Peer " + peer_id + ": I own this file, thus I won't save it's chunks. Ignoring");
                        ownedFile = true;
                        break;
                    }
                }
                if(ownedFile) break;
                if (!peerContainer.addStoredChunk(chunk)) {
                    System.err.println("> Peer " + peer_id + ": I already have this Chunk. Ignoring it");
                    break;
                }
                Backup backup = new Backup(chunk, peer_id);
                peerContainer.decFreeSpace(chunk.getSize());
                backup.performBackup();
                peerContainer.incOccurrences(file_id, chunk_no);
                chunk.setReplicationDegree(chunk.getReplicationDegree() + 1);
                int response_time = new Random().nextInt(401);
                peerExecutors.schedule(() -> {
                    // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
                    String response = version + " STORED " + peer_id + " " + file_id + " " + chunk_no + "\r\n\r\n";
                    mc.sendMessage(response.getBytes());
                }, response_time, TimeUnit.MILLISECONDS);
            }
            // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
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
                        chunk.setReplicationDegree(chunk.getReplicationDegree() + 1);
                        break;
                    }
                }
                if(isOfMyInterest) {
                    peerContainer.incOccurrences(file_id, chunk_no);
                }
            }
            // <Version> GETCHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            case "GETCHUNK" -> {
                int chunk_no = Integer.parseInt(parts[4]);
                for(FileChunk chunk : peerContainer.getStoredChunks()) {
                    if (chunk.getFileID().equals(file_id) && chunk.getChunkNo() == chunk_no) {
                        int response_time = new Random().nextInt(401);
                        // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
                        String header = version + " CHUNK " + peer_id + " " + file_id + " " + chunk_no + "\r\n\r\n";
                        byte[] response = new byte[header.getBytes().length + chunk.getContent().length];
                        System.arraycopy(header.getBytes(), 0, response, 0, header.getBytes().length);
                        System.arraycopy(chunk.getContent(), 0, response, header.getBytes().length, chunk.getContent().length);
                        peerExecutors.schedule(() -> mdr.sendMessage(response), response_time, TimeUnit.MILLISECONDS);
                        break;
                    }
                }
            }
            // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
            case "CHUNK" -> {
                int chunk_no = Integer.parseInt(parts[4]);
                Restore restore = new Restore(file_id, chunk_no, peer_id, peerContainer);
                restore.performRestore(packet);
            }
            // <Version> DELETE <SenderId> <FileId> <CRLF><CRLF>
            case "DELETE" -> {
                Delete delete = new Delete(file_id, peerContainer);
                delete.performDelete();
            }
            // <Version> REMOVED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            case "REMOVED" -> {
                int chunk_no = Integer.parseInt(parts[4]);
                Reclaim reclaim = new Reclaim(file_id, chunk_no, protocol_version, peer_id, peerContainer, peerExecutors, activeOps);
                reclaim.performReclaim();
            }
            default -> System.err.println("> Peer " + peer_id + ": Message with invalid control \"" + control + "\" received");
        }
    }

    /**
     * Auxiliary Function used to get the body from a message
     * @param packet message that contains a body
     * @return body/content of the message
     */
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

    /**
     * Auxiliary Function used to get the path of a peer
     * @param pID Peer ID
     * @return path of the Peer
     */
    public static String getPeerPath(int pID) {
        return "peer " + pID + "/";
    }

    /**
     * Function used to open the Multicast Channels
     */
    public synchronized static void openChannels() throws UnknownHostException {
        mc = new Channel(peer_id, mc_maddress, mc_port, ChannelType.MC);
        mdb = new Channel(peer_id, mdb_maddress, mdb_port, ChannelType.MDB);
        mdr = new Channel(peer_id, mdr_maddress, mdr_port, ChannelType.MDR);

        new Thread(mc).start();
        new Thread(mdb).start();
        new Thread(mdr).start();
    }

    /**
     * Peer ID Getter
     * @return Peer ID
     */
    public static int getPeerID() {
        return peer_id;
    }

    @Override
    public synchronized String backup(String file_name, int replicationDegree) { // Called by the initiator peer
        peerContainer.updateFilesState();
        FileManager filemanager = null;
        for(FileManager file : peerContainer.getStoredFiles())
            if(file.getFile().getName().equals(file_name)) filemanager = file;
        if(filemanager == null) return "Unsuccessful BACKUP of file " + file_name + ", this file does not exist on this peer's file system";
        if(filemanager.isAlreadyBackedUp()) {
            System.out.println("This file is already backed up, ignoring command");
            return "Unsuccessful BACKUP of file " + file_name + ", backup of this file already exists";
        }

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

            byte[] message;
            if(fileChunk.getContent() == null) {
                message = header.getBytes();
            } else {
                message = new byte[header.getBytes().length + fileChunk.getContent().length];
                System.arraycopy(header.getBytes(), 0, message, 0, header.getBytes().length);
                System.arraycopy(fileChunk.getContent(), 0, message, header.getBytes().length, fileChunk.getContent().length);
            }

            sendMessagePUTCHUNKProtocol(message, fileChunk, replicationDegree, 1);
            System.out.println("> Peer " + peer_id + ": Started BACKUP protocol of chunk nº" + fileChunk.getChunkNo() + " of file with file ID: " + fileChunk.getFileID());
        }

        filemanager.setAlreadyBackedUp(true);
        return "BACKUP protocol of file " + file_name + " successfully initiated";
    }

    @Override
    public String restore(String file_name) throws RemoteException {
        peerContainer.updateFilesState();
        for(FileManager file : peerContainer.getStoredFiles()) {
            if(file.getFile().getName().equals(file_name)) {
                long file_size = (long) (file.getChunks().size() - 1) * FileManager.CHUNK_MAX_SIZE + file.getChunks().get(file.getChunks().size()-1).getSize();
                if(peerContainer.getFreeSpace() - file_size < 0) {
                    return "Unsuccessful RESTORE of file " + file_name + ", not enough free space";
                }
                for(FileChunk chunk : file.getChunks()) {
                    // <Version> GETCHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
                    String header = protocol_version + " GETCHUNK " + peer_id + " " + file.getFileID() + " " +
                            chunk.getChunkNo() + " \r\n\r\n";

                    byte[] message = header.getBytes();

                    sendMessageGETCHUNKProtocol(message);
                }
                break;
            }
        }
        return "RESTORE protocol of file " + file_name + " successfully initiated";
    }

    @Override
    public String delete(String file_name) throws RemoteException{
        peerContainer.updateFilesState();
        for(FileManager file : peerContainer.getStoredFiles()) {
            if(file.getFile().getName().equals(file_name)) {
                // <Version> DELETE <SenderId> <FileId> <CRLF><CRLF>
                String header = protocol_version + " DELETE " + peer_id + " " + file.getFileID() +
                        " \r\n\r\n";

                byte[] message = header.getBytes();

                sendMessageDELETEProtocol(message);
                peerContainer.clearFileOccurences(file);
                file.setAlreadyBackedUp(false);
                break;
            }
        }
        return "DELETE protocol of file " + file_name + " successfully initiated";
    }

    @Override
    public String reclaim(long max_disk_space) throws RemoteException {
        peerContainer.updateFilesState();
        long space_to_free = peerContainer.getFreeSpace() - max_disk_space;
        ArrayList<FileChunk> toBeDeleted = new ArrayList<>();
        if(space_to_free > 0) { // has to delete files
            long space_occupied = peerContainer.getMaxSpace()-peerContainer.getFreeSpace();
            peerContainer.setFreeSpace(max_disk_space - space_occupied);
            peerContainer.setMaxSpace(max_disk_space);
            int deletedSpace = 0;
            peerContainer.getStoredChunks().sort(Collections.reverseOrder()); // > RD first to avoid backups
            for(FileChunk storedChunk : peerContainer.getStoredChunks()) {
                if(deletedSpace >= space_to_free) break;
                deletedSpace += storedChunk.getSize();
                peerContainer.clearChunkOccurence(storedChunk.getFileID(), storedChunk.getChunkNo());
                toBeDeleted.add(storedChunk);
                peerContainer.deleteStoredChunk(storedChunk);
                peerContainer.incFreeSpace(storedChunk.getSize());
                System.out.println(storedChunk.getDesiredReplicationDegree());
                // <Version> REMOVED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
                String header = protocol_version + " REMOVED " + peer_id + " " + storedChunk.getFileID() +
                        " " + storedChunk.getChunkNo() + " \r\n\r\n";

                byte[] message = header.getBytes();

                sendMessageREMOVEProtocol(message);
                System.out.println("> Peer " + peer_id + ": Started BACKUP protocol of chunk nº" + storedChunk.getChunkNo() + " of file with file ID: " + storedChunk.getFileID());
            }
            for(FileChunk chunk : toBeDeleted) {
                peerContainer.getStoredChunks().removeIf(c -> c.equals(chunk));
            }
        } else {
            peerContainer.setFreeSpace(peerContainer.getFreeSpace() - (peerContainer.getMaxSpace() - max_disk_space));
            peerContainer.setMaxSpace(max_disk_space);
        }
        return "RECLAIM protocol successfully initiated";
    }

    @Override
    public String state() throws RemoteException{
        peerContainer.updateFilesState();
        int nFile = 1;
        int nChunk = 1;
        StringBuilder state = new StringBuilder();
        state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
        state.append(":::                                   PEER ").append(peer_id).append("                                  :::\n");
        state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
        state.append(":::                              BACKED UP FILES                              :::\n");
        // each file whose backup has initiated
        for(FileManager fileManager : peerContainer.getStoredFiles()){
            if(fileManager.isAlreadyBackedUp()) {
                state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
                state.append("::: FILE ").append(nFile).append("                                                                    :::\n");
                state.append(":::                                                                           :::\n");
                state.append("::: PATH: ").append(fileManager.getFile().getPath()).append("                                             :::\n");
                state.append("::: FILE_ID: ").append(fileManager.getFileID()).append(" :::\n");
                state.append("::: DESIRED REPLICATION DEGREE: ").append(fileManager.getChunks().get(0).getDesiredReplicationDegree()).append("                                             :::\n");
                state.append(":::                                                                           :::\n");
                state.append("::: CHUNKS OF FILE ").append(nFile).append("                                                          :::\n");
                for(FileChunk chunk : fileManager.getChunks()){
                    state.append(":::                                                                           :::\n");
                    state.append("::: CHUNK ").append(nChunk).append("                                                                   :::\n");
                    state.append(":::       ID: ").append(chunk.getChunkNo()).append("                                                               :::\n");
                    state.append(":::       CURRENT REPLICATION DEGREE: ").append(chunk.getReplicationDegree()).append("                                       :::\n");
                    nChunk++;
                }
                nFile++;
                nChunk = 1;
            }
        }
        if(nFile == 1){
            state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
            state.append("::: NO BACKED UP FILES                                                        :::\n");
        }
        // each chunk it stores
        state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
        state.append(":::                               STORED CHUNKS                               :::\n");
        for(FileChunk storedChunk : peerContainer.getStoredChunks()){
            state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
            state.append("::: CHUNK ").append(nChunk).append("                                                                   :::\n");
            state.append(":::                                                                           :::\n");
            state.append(":::       ID: ").append(storedChunk.getChunkNo()).append("                                                               :::\n");
            state.append(":::       SIZE: ").append(storedChunk.getSize()).append("                                                         :::\n");
            state.append(":::       DESIRED REPLICATION DEGREE: ").append(storedChunk.getDesiredReplicationDegree()).append("                                       :::\n");
            state.append(":::       CURRENT REPLICATION DEGREE: ").append(storedChunk.getReplicationDegree()).append("                                       :::\n");
            nChunk++;
        }
        if(nChunk == 1){
            state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
            state.append("::: NO STORED CHUNKS                                                          :::\n");
        }
        // peer storage capacity
        state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
        state.append(":::                           PEER STORAGE CAPACITY                           :::\n");
        state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::\n");
        state.append("::: TOTAL DISK SPACE: ").append(peerContainer.getMaxSpace() / 1000).append(" KB                                                 :::\n");
        state.append("::: FREE SPACE: ").append(peerContainer.getFreeSpace() / 1000).append(" KB                                                       :::\n");
        state.append(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::");

        return state.toString();
    }

    /**
     * Function used to send the PUTCHUNK message
     * @param message Body of the message
     * @param fileChunk Chunk send in the message
     * @param replicationDegree Replication Degree of the Chunk
     * @param waitTime Wait time (used if there's an error sending the message)
     */
    public static synchronized void sendMessagePUTCHUNKProtocol(byte[] message, FileChunk fileChunk, int replicationDegree, int waitTime) {
        mdb.sendMessage(message);
        Integer actual_rep_degree = peerContainer.getOccurrences().get(PeerContainer.createKey(fileChunk.getFileID(), fileChunk.getChunkNo()));
        peerExecutors.schedule(() -> {
            if(actual_rep_degree == null || actual_rep_degree < replicationDegree) {
                if(waitTime * 2 > 16) {
                    System.out.println("> Peer " + peer_id + ": BACKUP protocol of chunk nº" + fileChunk.getChunkNo() + " of file with file ID: " + fileChunk.getFileID() + " finished, but desired replication degree not met");
                    return;
                }
                sendMessagePUTCHUNKProtocol(message, fileChunk, replicationDegree, waitTime * 2);
            } else {
                fileChunk.setReplicationDegree(actual_rep_degree);
                peerContainer.saveState();
                System.out.println("> Peer " + peer_id + ": BACKUP protocol of chunk nº" + fileChunk.getChunkNo() + " of file with file ID: " + fileChunk.getFileID() + " finished");
            }
        }, waitTime, TimeUnit.SECONDS);
    }

    /**
     * Function used to send the GETCHUNK message
     * @param message message sent
     */
    private synchronized void sendMessageGETCHUNKProtocol(byte[] message) {
        mc.sendMessage(message);
    }

    /**
     * Function used to send the DELETE message
     * @param message message sent
     */
    private synchronized void sendMessageDELETEProtocol(byte[] message) {
        mc.sendMessage(message);
    }

    /**
     * Function used to send the REMOVE message
     * @param message message sent
     */
    private synchronized void sendMessageREMOVEProtocol(byte[] message) {
        mc.sendMessage(message);
    }
}
