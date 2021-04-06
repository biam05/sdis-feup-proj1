package sdis.t1g06;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Backup {
    private final String fileId;
    private final int senderId;
    private final int chunkNo;
    private final byte[] content;
    private final int pID;
    public Backup(int senderId, FileChunk chunk, int pId){
        this.fileId = chunk.getFileID();
        this.senderId = senderId;
        this.chunkNo = chunk.getChunkNo();
        this.content = chunk.getContent();
        this.pID = pId;
    }

    public void performBackup() {
        try {
            FileWriter writer = new FileWriter("peer " + pID + "\\" + "chunks\\" + fileId + "_" + senderId + "_" + chunkNo);
            String contentStr = new String(content, StandardCharsets.UTF_8);
            writer.write(contentStr);
            writer.close();
            System.out.println("> Peer " + pID + ": saved chunk nº" + chunkNo + " of file with fileID: " + fileId);
        } catch (IOException e) {
            System.err.println("> Peer " + pID + " exception: failed to save chunk " +  chunkNo);
            e.printStackTrace();
        }
    }
}
