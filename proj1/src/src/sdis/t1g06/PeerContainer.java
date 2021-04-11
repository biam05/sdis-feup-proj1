package sdis.t1g06;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PeerContainer Class
 */
public class PeerContainer implements Serializable {
    private int pID;
    private ArrayList<FileManager> storedFiles;
    private ArrayList<FileChunk> storedChunks;
    private ConcurrentHashMap<String, Integer> occurrences;
    private int freeSpace;

    /**
     * PeerContainer Constructor
     * @param pid Peer ID
     */
    public PeerContainer(int pid){
        this.storedFiles = new ArrayList<>();
        this.storedChunks = new ArrayList<>();
        this.freeSpace = 8 * 1000000;
        this.occurrences = new ConcurrentHashMap<>();
        this.pID = pid;
    }

    /**
     * Function used to save the state of the Peer Container
     */
    public synchronized void saveState() {
        try {
            FileOutputStream stateFileOut = new FileOutputStream("peer " + pID + "/state.ser");
            ObjectOutputStream out = new ObjectOutputStream(stateFileOut);
            out.writeObject(this);
            out.close();
            stateFileOut.close();
            //System.out.println("> Peer " + pID + ": Serialized state saved in /peer " + pID + "/state.ser");
        } catch (IOException i) {
            System.err.println("> Peer " + pID + ": Failed to save serialized state");
            i.printStackTrace();
        }
    }

    /**
     * Function used to load the state of the Peer Container
     */
    public synchronized void loadState() {
        PeerContainer peerContainer;
        try {
            FileInputStream stateFileIn = new FileInputStream("peer " + pID + "/state.ser");
            ObjectInputStream in = new ObjectInputStream(stateFileIn);
            peerContainer = (PeerContainer) in.readObject();
            in.close();
            stateFileIn.close();
            System.out.println("> Peer " + pID + ": Serialized state of peer loaded successfully");
        } catch (Exception i) {
            System.out.println("> Peer " + pID + ": State file of peer not found, a new one will be created");
            updateState();
            this.saveState();
            return;
        }

        pID = peerContainer.getPeerID();
        storedFiles = peerContainer.getStoredFiles();
        storedChunks = peerContainer.getStoredChunks();
        occurrences = peerContainer.getOccurrences();
        freeSpace = peerContainer.getFreeSpace();
    }

    /**
     * Function used to read the physical state of the Peer's Filesystem and update the Peer's container with it
     */
    public synchronized void updateState() {
        // Register all files
        try {
            Files.walk(Paths.get("peer " + pID + "/files")).forEach(filePath -> {
                if (!filePath.toFile().isDirectory()) {
                    FileManager fileManager = new FileManager(Peer.getPeerPath(pID) + "files/" + filePath.getFileName().toString(), 0);
                    if(!storedFiles.contains(fileManager)) storedFiles.add(fileManager);
                }
            });
        } catch (IOException e) {
            System.err.println("> Peer " + pID + ": Failed to iterate files of peer");
        }

        // Register all chunks
        try {
            Files.walk(Paths.get("peer " + pID + "/chunks")).forEach(filePath -> {
                if (!filePath.toFile().isDirectory()) {
                    String[] parts = filePath.getFileName().toString().split("_");
                    AsynchronousFileChannel fileChannel = null;
                    try {
                        fileChannel = AsynchronousFileChannel.open(filePath, StandardOpenOption.READ);
                    } catch (IOException e) {
                        System.err.println("> Peer " + pID + ": Failed to open a chunk");
                    }
                    byte[] content = new byte[FileManager.CHUNK_MAX_SIZE];
                    assert fileChannel != null;
                    fileChannel.read(ByteBuffer.wrap(content), 0);
                    FileChunk chunk = new FileChunk(parts[0], Integer.parseInt(parts[1]), content, content.length);
                    if(!storedChunks.contains(chunk)) storedChunks.add(chunk);
                }
            });
        } catch (IOException e) {
            System.err.println("> Peer " + pID + ": Failed to iterate chunks of peer");
        }
    }

    /**
     * Peer ID Getter
     * @return Peer ID
     */
    public int getPeerID() {
        return pID;
    }

    /**
     * Stored Files Getter
     * @return Stored Files
     */
    public ArrayList<FileManager> getStoredFiles(){
        return storedFiles;
    }

    /**
     * Stored Chunks Getter
     * @return Stored Chunks
     */
    public synchronized ArrayList<FileChunk> getStoredChunks(){
        return storedChunks;
    }

    /**
     * Occurrences Getter
     * @return Occurrences
     */
    public synchronized ConcurrentHashMap<String, Integer> getOccurrences(){return occurrences;}

    /**
     * Free Space Getter
     * @return Free Space
     */
    public synchronized int getFreeSpace(){
        return freeSpace;
    }

    /**
     * Function used to add a File to the Stored Files array
     * @param file File that is gonna be added
     */
    public void addStoredFile(FileManager file){
        this.storedFiles.add(file);
    }

    /**
     * Function used to delete a File from the Stored Files Array
     * @param file file that is gonna eb deleted
     */
    public synchronized void deleteStoredFile(FileManager file){
        try {
            Files.deleteIfExists(Path.of("peer " + pID + "\\" + "files\\" + file.getFile().getName()));
            System.out.println("> Peer " + pID + ": DELETE of file " + file.getFile().getName() + " finished");
        } catch (IOException e) {
            System.err.println("> Peer " + pID + ": Failed to delete file " + file.getFile().getName());
            e.printStackTrace();
        }
    }

    /**
     * Function used to add a Chunk to the Stored Chunks array
     * @param chunk Chunk that is gonna be stored
     * @return true if the chunk wasn't already stored; false otherwise
     */
    public synchronized boolean addStoredChunk(FileChunk chunk){
        for(FileChunk storedChunk : this.storedChunks){
            if(chunk.equals(storedChunk)) return false; // cant store equal chunks
        }
        this.storedChunks.add(chunk);
        return true;
    }

    /**
     * Function used to delete a Chunk from the Stored Chunks array
     * @param chunk Chunk that is gonna be deleted
     */
    public synchronized void deleteStoredChunk(FileChunk chunk){
        try {
            Files.deleteIfExists(Path.of("peer " + pID + "\\" + "chunks\\" + chunk.getFileID() + "_" + chunk.getChunkNo()));
            System.out.println("> Peer " + pID + ": Succeeded to delete chunk " + chunk.getChunkNo());
        } catch (IOException e) {
            System.err.println("> Peer " + pID + ": Failed to delete chunk " + chunk.getChunkNo());
            e.printStackTrace();
        }
    }

    /**
     * Auxiliary Function used to create a Key in the format used in the Occurrences CconcurrentHashMap
     * @param fileID File ID
     * @param chunkNo Chunk Number
     * @return the Key
     */
    public static String createKey(String fileID, int chunkNo){
        return fileID + "/" + chunkNo;
    }

    /**
     * Auxiliary Function used to know if a Key already occurs in the Occurrences ConcurrentHashMap
     * @param key Key that will be checked
     * @return they if the key is already present in the occurrences; false otherwise
     */
    public synchronized boolean containsOccurrence(String key){
        return this.occurrences.containsKey(key);
    }

    /**
     * Function used to increment the occurences of a chunk in a peer
     * @param fileID File ID from the chunk
     * @param chunkNo Chunk Number from the chunk
     */
    public synchronized void incOccurences(String fileID, int chunkNo){
        String key = createKey(fileID, chunkNo);
        if(!containsOccurrence(key)) {
            this.occurrences.put(key, 1);
        } else {
            this.occurrences.replace(key, this.occurrences.get(key) + 1);
        }
    }

    /**
     * Function used to clear the file occurrences in a peer
     * @param file File that will have the occurrences cleared
     */
    public synchronized void clearFileOccurences(FileManager file) {
        for(int chunkNo = 0; chunkNo < file.getChunks().size(); chunkNo++) {
            String key = createKey(file.getFileID(), chunkNo);
            this.occurrences.remove(key);
            System.out.println("> Peer " + pID + " deleted occurence of file " + file.getFileID() + " chunk No: " + chunkNo);
        }
        this.storedFiles.removeIf(f -> f.equals(file));
        this.saveState();
    }

    /**
     * Function used to clear the chunk occurrences in a peer
     * @param fileID File ID of the Chunk
     * @param chunkNo Chunk Number of the Chunk
     */
    public synchronized void clearChunkOccurence(String fileID, int chunkNo) {
        String key = createKey(fileID, chunkNo);
        this.occurrences.remove(key);
        System.out.println("> Peer " + pID + " deleted occurence of chunk No: " + chunkNo + " from file " + fileID);
        this.saveState();
    }

    /**
     * Free Space Setter
     * @param freeSpace new Free Space
     */
    public synchronized void setFreeSpace(int freeSpace){
        this.freeSpace = freeSpace;
    }

    /**
     * Function used to decrement the free space
     * @param size amount of space that will be decremented
     */
    public synchronized void decFreeSpace(int size){
        this.freeSpace -= size;
    }

    /**
     * Function used to increment the free space
     * @param fileID File ID of the chunk
     * @param chunkNo Chunk Number of the chunk
     */
    public synchronized void incFreeSpace(String fileID, int chunkNo){
        // chunk belongs to this peer
        for(FileChunk storedChunk : this.storedChunks){
            if(storedChunk.getFileID().equals(fileID) && storedChunk.getChunkNo() == chunkNo)
                this.freeSpace += storedChunk.getSize();
        }
    }

    /**
     * Function used to get the Occupied Space
     * @return Occupied Space
     */
    public synchronized int getTotalOccupiedSpace(){
        return 8 * 1000000 - this.freeSpace;
    }

}
