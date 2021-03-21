package sdis.t1g06;

public class FileChunk {
    private String fileID;
    private int chunkNo;
    private int size; // max 64kB
    private byte[] content;
    private int replicationDegree; // number of peers backing up a chunk
    private int desiredReplicationDegree; // number of peers backing up a chunk

    // for split in FileManager
    public FileChunk(String id, int n, byte[] content, int size){
        this.fileID = id;
        this.chunkNo = n;
        this.content = content;
        this.size = size;
    }

    // for backup in Peer
    public FileChunk(String id, int n, int desiredReplicationDegree, int size){
        this.fileID = id;
        this.chunkNo = n;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.size = size;

        this.replicationDegree = 0;
    }

    public String getFileID(){
        return fileID;
    }

    public int getChunkNo(){
        return chunkNo;
    }

    public int getSize(){
        return size;
    }

    public byte[] getContent(){
        return content;
    }

    public int getReplicationDegree() {
        return replicationDegree;
    }

    public int getDesiredReplicationDegree(){
        return desiredReplicationDegree;
    }

    public void setReplicationDegree(int replicationDegree) {
        this.replicationDegree = replicationDegree;
    }

    public void setDesiredReplicationDegree(int desiredReplicationDegree) {
        this.desiredReplicationDegree = desiredReplicationDegree;
    }
}
