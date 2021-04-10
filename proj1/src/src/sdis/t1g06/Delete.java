package sdis.t1g06;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Delete Class
 */
public class Delete {
    private final String fileId;
    private final PeerContainer peerContainer;

    /**
     * Delete Constructor
     * @param fileId File ID
     * @param peerContainer Peer Container
     */
    public Delete(String fileId, PeerContainer peerContainer){
        this.fileId = fileId;
        this.peerContainer = peerContainer;
    }

    /**
     * Function used to perform the deletion of a chunk
     */
    public synchronized void performDelete() {
        ArrayList<FileChunk> toBeDeleted = new ArrayList<>();
        // Delete From FileSystem
        for(FileChunk chunk : peerContainer.getStoredChunks()) {
            if(chunk.getFileID().equals(fileId)){
                peerContainer.incFreeSpace(chunk.getFileID(), chunk.getChunkNo());
                Executors.newScheduledThreadPool(5).schedule(() -> {
                    peerContainer.deleteStoredChunk(chunk);
                }, 0, TimeUnit.SECONDS);
                toBeDeleted.add(chunk);
            }
        }
        // Delete From Memory
        for(FileChunk chunk : toBeDeleted) {
            peerContainer.getStoredChunks().removeIf(c -> c.equals(chunk));
            peerContainer.clearChunkOccurence(chunk.getFileID(), chunk.getChunkNo());
        }
    }
}
