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
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// TODO: Fazer função que ao iniciar um peer verifica se o ficheiro state existe, senão, verifica todos os ficheiros da pasta files e adiciona ao storedFiles
//  Fazendo isso, é preciso também na função backup alterar a forma como verifica se um ficheiro já foi backed up. Utilizar o occurencies do peerContainer
//  ConcurrentHashMaps para acompanhar se o processo de algum subprotocolo ainda decorre, utilizando o key fileid e o value um boolean. Caso o chunk recebido
//  tenha tamanho menor que os 64k, mudar pra false, e ignorar msgs seguintes
//  Separar funções treatMessage por channels

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

    private static PeerContainer peerContainer;

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
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
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
        int sender_id = Integer.parseInt(parts[2]);
        String file_id = parts[3];
        int chunk_no = Integer.parseInt(parts[4]);

        //int rep_degree = Integer.parseInt(parts[5]);
        switch (control) {
            case "PUTCHUNK" -> {
                String content = message.substring(message.indexOf("\r\n\r\n") + 4);
                FileChunk chunk = new FileChunk(file_id, chunk_no, content.getBytes(), content.getBytes().length);
                if (!peerContainer.addStoredChunk(chunk)) {
                    System.err.println("I already have this chunk, ignoring");
                    break;
                }
                Backup backup = new Backup(sender_id, chunk, peer_id);
                peerContainer.decFreeSpace(chunk.getSize());
                backup.performBackup();
                peerContainer.incOccurences(file_id, chunk_no);
                int response_time = new Random().nextInt(401);
                Executors.newScheduledThreadPool(10).schedule(() -> {
                    String response = version + " STORED " + peer_id + " " + file_id + " " + chunk_no + "\r\n\r\n"; // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
                    mc.sendMessage(response.getBytes());
                }, response_time, TimeUnit.MILLISECONDS);
            }
            case "STORED" -> {
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
                    System.out.println(peerContainer.getOccurences().get(PeerContainer.createKey(file_id, chunk_no)));
                }
            }
            case "GETCHUNK" -> {
                for(FileChunk chunk : peerContainer.getStoredChunks()) {
                    if (chunk.getFileID().equals(file_id) && chunk.getChunkNo() == chunk_no) {
                        int response_time = new Random().nextInt(401);
                        String header = version + " CHUNK " + peer_id + " " + file_id + " " + chunk_no + "\r\n\r\n"; // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
                        byte[] response = new byte[header.getBytes().length + chunk.getContent().length];
                        System.arraycopy(header.getBytes(), 0, response, 0, header.getBytes().length);
                        System.arraycopy(chunk.getContent(), 0, response, header.getBytes().length, chunk.getContent().length);
                        Executors.newScheduledThreadPool(10).schedule(() -> {
                            mdr.sendMessage(response);
                            System.out.println(response_time + " ms :: " + Arrays.toString(response));
                        }, response_time, TimeUnit.MILLISECONDS);
                        break;
                    }
                }
            }
            case "CHUNK" -> {
                for(FileManager file : peerContainer.getStoredFiles()) {
                    if(file.getFileID().equals(file_id)) {
                        boolean fileRestorationHasAlreadyStarted = false;
                        FileManager restored_file = new FileManager(peer_path + "files/restored_" + file.getFile().getName(), 0);
                        for(FileManager tmp : peerContainer.getStoredFiles()) {
                            if(tmp.getFile().getName().equals(restored_file.getFile().getName())) {
                                fileRestorationHasAlreadyStarted = true;
                                restored_file = tmp;
                                break;
                            }
                        }
                        if(!fileRestorationHasAlreadyStarted)
                            peerContainer.addStoredFile(restored_file);
                        if(!restored_file.getFile().exists()) {
                            try {
                                if(!restored_file.getFile().createNewFile()) throw new FileAlreadyExistsException("");
                            } catch (FileAlreadyExistsException e) {
                                System.err.println("> Peer " + peer_id + ": Schrödinger's file, both exists and doesn't at the same time");
                                return;
                            } catch (IOException e) {
                                System.err.println("> Peer " + peer_id + ": Failed to create restored file");
                                return;
                            }
                        }
                        boolean chunkAlreadyReceived = false;
                        for(FileChunk chunk : restored_file.getChunks()) {
                            if(chunk.getChunkNo() == chunk_no) {
                                chunkAlreadyReceived = true;
                                System.out.println("> Peer " + peer_id + ": Chunk nº" + chunk_no + " already processed, ignoring repeat");
                                break;
                            }
                        }
                        if(!chunkAlreadyReceived) {
                            String content = message.substring(message.indexOf("\r\n\r\n") + 4);
                            FileChunk chunk = new FileChunk(restored_file.getFileID(), chunk_no, content.getBytes(), content.getBytes().length);
                            restored_file.getChunks().add(chunk);
                            System.out.println("Chunk nº" + chunk.getChunkNo() + " size: " + chunk.getSize());
                            if(restored_file.getChunks().size() == file.getChunks().size())
                                restored_file.createFile(Path.of(peer_path + "files/restored_" + file.getFile().getName()), peer_id);
                        }
                        break;
                    }
                }
            }
            case "DELETE" -> {
                for(FileChunk chunk : peerContainer.getStoredChunks()) {
                    if (chunk.getFileID().equals(file_id) && chunk.getChunkNo() == chunk_no) {
                        peerContainer.incFreeSpace(chunk.getFileID(), chunk.getChunkNo());
                        peerContainer.deleteStoredChunk(chunk);
                    }
                }
            }
            default -> System.err.println("> Peer " + peer_id + ": Message with invalid control \"" + control + "\" received");
        }
    }

    public synchronized static void openChannels() throws UnknownHostException {
        mc = new Channel(peer_id, mc_maddress, mc_port, mc_maddress + ":" + mc_port, ChannelType.MC);
        mdb = new Channel(peer_id, mdb_maddress, mdb_port, mdb_maddress + ":" + mdb_port, ChannelType.MDB);
        mdr = new Channel(peer_id, mdr_maddress, mdr_port, mdr_maddress + ":" + mdr_port, ChannelType.MDR);

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

    public synchronized static PeerContainer getPeerContainer(){ return peerContainer;}

    /**
     * The backup service splits each file in chunks and then backs up each chunk independently,
     * rather than creating multiple files that are a copy of the file to backup.
     */
    @Override
    public synchronized String backup(String file_name, int replicationDegree) { // Called by the initiator peer

        FileManager filemanager = new FileManager(peer_path + "files/" + file_name, replicationDegree);
        if(peerContainer.getStoredFiles().contains(filemanager)) {
            System.out.println("This file is already backed up, ignoring command");
            return "Unsuccessful BACKUP of file " + file_name + ", backup of this file already exists";
        }
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
                for(FileChunk chunk : file.getChunks()) {
                    // header construction
                    // <Version> DELETE <SenderId> <FileId> <CRLF><CRLF>
                    //      CRLF == \r\n
                    String header = protocol_version + " DELETE " + peer_id + " " + file.getFileID() +
                            " \r\n\r\n";

                    byte[] message = header.getBytes();

                    sendMessageDELETEProtocol(message);
                }
                break;
            }
        }
        return "Successful DELETION of file " + file_name;
    }

    private synchronized void sendMessagePUTCHUNKProtocol(byte[] message, FileManager filemanager, FileChunk fileChunk, int replicationDegree, int waitTime) {
        mdb.sendMessage(message);
        Executors.newScheduledThreadPool(10).schedule(() -> {
            if(peerContainer.getOccurences().get(PeerContainer.createKey(filemanager.getFileID(), fileChunk.getChunkNo())) < replicationDegree) {
                if(waitTime * 2 > 16) return;
                sendMessagePUTCHUNKProtocol(message, filemanager, fileChunk, replicationDegree, waitTime * 2);
            } else updatePeerStateAboutChunk(filemanager.getFileID(), fileChunk.getChunkNo(), peerContainer.getOccurences().get(PeerContainer.createKey(filemanager.getFileID(), fileChunk.getChunkNo())));
        }, waitTime, TimeUnit.SECONDS);
    }

    private synchronized void sendMessageGETCHUNKProtocol(byte[] message) {
        mc.sendMessage(message);
    }

    private synchronized void sendMessageDELETEProtocol(byte[] message) {
        mc.sendMessage(message);
    }

    private synchronized void updatePeerStateAboutChunk(String fileID, int chunkNo, int actualRepDegree) {
        peerContainer.saveState();
        System.out.println("Save state here, repDeg: " + actualRepDegree);
    }
}
