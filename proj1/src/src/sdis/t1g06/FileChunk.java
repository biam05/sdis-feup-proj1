package sdis.t1g06;

public class FileChunk {
    private String fileID;
    private int chunkNo;
    private int size; // max 64kB
    private byte[] content;

    public FileChunk(String id, int n, byte[] content, int size){
        this.fileID = id;
        this.chunkNo = n;
        this.content = content;
        this.size = size;
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
}
