package sdis.t1g06;

import java.io.Serializable;

/**
 * FileChunk Class
 */
public class FileChunk implements Serializable {
    private final String fileID;
    private final int chunkNo;
    private final int size;
    private byte[] content;
    private int replicationDegree;
    private int desiredReplicationDegree;

    /**
     * FileChunk Constructor
     * @param id File ID
     * @param n Number of the Chunk
     * @param content Content of the Chunk
     * @param size Size of the Chunk (MAX = 64KB)
     */
    public FileChunk(String id, int n, byte[] content, int size){
        this.fileID = id;
        this.chunkNo = n;
        this.content = content;
        this.size = size;
    }

    /**
     * File ID Getter
     * @return File ID
     */
    public String getFileID(){
        return fileID;
    }

    /**
     * Chunk Number Getter
     * @return Chunk Number
     */
    public int getChunkNo(){
        return chunkNo;
    }

    /**
     * Size of the Chunk Getter
     * @return Size of the Chunk
     */
    public int getSize(){
        return size;
    }

    /**
     * Content of the Chunk Getter
     * @return Content of the Chunk
     */
    public byte[] getContent(){
        return content;
    }

    /**
     * Replication Degree Setter
     * @param replicationDegree new Replication Degree
     */
    public void setReplicationDegree(int replicationDegree) {
        this.replicationDegree = replicationDegree;
    }

    /**
     * Desired Replication Degree Setter
     * @param desiredReplicationDegree new Desired Replication Degree
     */
    public void setDesiredReplicationDegree(int desiredReplicationDegree) {
        this.desiredReplicationDegree = desiredReplicationDegree;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FileChunk)) {
            return false;
        }

        FileChunk c = (FileChunk) o;

        return (this.chunkNo == c.chunkNo && this.fileID.equals(c.fileID));
    }
}
