package sdis.t1g06;

import java.io.Serializable;

/**
 * FileChunk Class
 */
public class FileChunk implements Serializable, Comparable<FileChunk> {
    private final String fileID;
    private final int chunkNo;
    private final int size;
    private final byte[] content;

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
     * Replication Degree Getter
     * @return Replication Degree of the Chunk
     */
    public int getReplicationDegree() {
        return replicationDegree;
    }

    /**
     * Desired Replication Degree Setter
     * @param desiredReplicationDegree new Desired Replication Degree
     */
    public void setDesiredReplicationDegree(int desiredReplicationDegree) {
        this.desiredReplicationDegree = desiredReplicationDegree;
    }

    /**
     * Desired Replication Degree Getter
     * @return Desired Replication Degree of the Chunk
     */
    public int getDesiredReplicationDegree() {
        return desiredReplicationDegree;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FileChunk)) {
            return false;
        }

        FileChunk c = (FileChunk) o;

        return (this.chunkNo == c.chunkNo && this.fileID.equals(c.fileID));
    }

    @Override
    public int compareTo(FileChunk c) {
        return Integer.compare(this.replicationDegree, c.replicationDegree);
    }
}
