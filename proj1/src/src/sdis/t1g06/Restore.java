package sdis.t1g06;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;

public class Restore {
    private final String fileId;
    private final int chunkNo;
    private final int pID;
    private final PeerContainer peerContainer;

    public Restore(String fileId, int chunkNo, int pId, PeerContainer peerContainer){
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.pID = pId;
        this.peerContainer = peerContainer;
    }

    public synchronized void performRestore(DatagramPacket packet) {
        for(FileManager file : peerContainer.getStoredFiles()) {
            if(file.getFileID().equals(fileId)) {
                boolean fileRestorationHasAlreadyStarted = false;
                FileManager restored_file = new FileManager(Peer.getPeerPath(pID) + "files/restored_" + file.getFile().getName(), 0);
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
                        System.err.println("> Peer " + pID + ": Schrödinger's file, both exists and doesn't at the same time");
                        return;
                    } catch (IOException e) {
                        System.err.println("> Peer " + pID + ": Failed to create restored file");
                        return;
                    }
                }
                boolean chunkAlreadyReceived = false;
                for(FileChunk chunk : restored_file.getChunks()) {
                    if(chunk.getChunkNo() == chunkNo) {
                        chunkAlreadyReceived = true;
                        System.out.println("> Peer " + pID + ": Chunk nº" + chunkNo + " already processed, ignoring repeat");
                        break;
                    }
                }
                if(!chunkAlreadyReceived) {
                    byte[] content = Peer.getBody(packet);
                    FileChunk chunk = new FileChunk(restored_file.getFileID(), chunkNo, content, content.length);
                    restored_file.getChunks().add(chunk);
                    if(restored_file.getChunks().size() == file.getChunks().size())
                        restored_file.createFile(Path.of(Peer.getPeerPath(pID) + "files/restored_" + file.getFile().getName()), pID);
                }
                break;
            }
        }
    }
}
