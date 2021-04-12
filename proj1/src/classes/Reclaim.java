package sdis.t1g06;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Reclaim Class
 */
public class Reclaim {
    private final String fileId;
    private final int chunkNo;
    private final double protocol_version;
    private final int pID;
    private final PeerContainer peerContainer;
    private final ScheduledThreadPoolExecutor peerExecutors;
    private final ConcurrentHashMap<String, ScheduledFuture<String>> activeOps;

    /**
     * Reclaim Constructor
     * @param fileId File ID
     * @param chunkNo Chunk Number
     * @param protocol_version Protocol version
     * @param pId Peer ID
     * @param peerContainer Peer container
     * @param peerExecutors Peer executor pool
     * @param activeOps active putchunk operations
     */
    public Reclaim(String fileId, int chunkNo, double protocol_version, int pId, PeerContainer peerContainer, ScheduledThreadPoolExecutor peerExecutors, ConcurrentHashMap<String, ScheduledFuture<String>> activeOps){
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.protocol_version = protocol_version;
        this.pID = pId;
        this.peerContainer = peerContainer;
        this.peerExecutors = peerExecutors;
        this.activeOps = activeOps;
    }

    /**
     * Function used to perform the space reclaim
     */
    public synchronized void performReclaim() {
        peerContainer.decOccurrences(fileId, chunkNo);
        boolean ownedChunk = false;
        for(FileChunk chunk : peerContainer.getStoredChunks()) {
            if(chunk.getFileID().equals(fileId) && chunk.getChunkNo() == chunkNo) {
                ownedChunk = true;
                chunk.setReplicationDegree(chunk.getReplicationDegree() - 1);
                if(chunk.getReplicationDegree() < chunk.getDesiredReplicationDegree()) {
                    int response_time = new Random().nextInt(401);
                    ScheduledFuture<String> task = peerExecutors.schedule(() -> {
                        // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
                        String header = protocol_version + " PUTCHUNK " + pID + " " + fileId
                                + " " + chunkNo + " " + chunk.getDesiredReplicationDegree() + " \r\n\r\n";

                        byte[] reply_message = new byte[header.getBytes().length + chunk.getContent().length];
                        System.arraycopy(header.getBytes(), 0, reply_message, 0, header.getBytes().length);
                        System.arraycopy(chunk.getContent(), 0, reply_message, header.getBytes().length, chunk.getContent().length);

                        Peer.sendMessagePUTCHUNKProtocol(reply_message, chunk, chunk.getDesiredReplicationDegree(), 1);
                        return "";
                    }, response_time, TimeUnit.MILLISECONDS);
                    activeOps.put(PeerContainer.createKey(fileId, chunkNo), task);
                }
            }
        }
        if(!ownedChunk) {
            for(FileManager file : peerContainer.getStoredFiles()) {
                if(file.getFileID().equals(fileId)) {
                    boolean fileNoLongerBackedUp = true;
                    for(FileChunk chunk : file.getChunks()) {
                        if(chunk.getChunkNo() == chunkNo) {
                            chunk.setReplicationDegree(peerContainer.getOccurrences().get(PeerContainer.createKey(fileId, chunkNo)));
                        }
                        if(chunk.getReplicationDegree() > 0) fileNoLongerBackedUp = false;
                    }
                    if(fileNoLongerBackedUp) {
                        System.out.println("> Peer " + pID + ": After this RECLAIM operation, no other peer's contained copies of the chunk so the file " + file.getFile().getName() + " is no longer backed up");
                        file.setAlreadyBackedUp(false);
                    }
                }
            }
        }
    }
}
