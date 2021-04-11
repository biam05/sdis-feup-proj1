package sdis.t1g06;

/**
 * Reclaim Class
 */
public class Reclaim {
    private final String fileId;
    private final int chunkNo;
    private final int pID;
    private final PeerContainer peerContainer;

    /**
     * Reclaim Constructor
     * @param fileId File ID
     * @param chunkNo Chunk Number
     * @param pId Peer ID
     * @param peerContainer Container from the Peer
     */
    public Reclaim(String fileId, int chunkNo, int pId, PeerContainer peerContainer){
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.pID = pId;
        this.peerContainer = peerContainer;
    }

    /**
     * Function used to perform the space reclaim
     */
    public synchronized void performReclaim() {
        // TODO
    }


}
