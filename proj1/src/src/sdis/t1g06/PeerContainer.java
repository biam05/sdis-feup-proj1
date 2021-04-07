package sdis.t1g06;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class PeerContainer implements Serializable {
    private int pID;
    private ArrayList<FileManager> storedFiles;
    private ArrayList<FileChunk> storedChunks;
    private ConcurrentHashMap<String, Integer> occurences;
    private int freeSpace;

    public PeerContainer(int pid){
        this.storedFiles = getFilesFromFolder(null);
        this.storedChunks = getChunksFromFolder(null);
        this.freeSpace = 8 * 1000000;
        this.occurences = new ConcurrentHashMap<>();
        this.pID = pid;
    }

    public synchronized void saveState() {
        try {
            FileOutputStream stateFileOut = new FileOutputStream("peer " + pID + "/state.ser");
            ObjectOutputStream out = new ObjectOutputStream(stateFileOut);
            out.writeObject(this);
            out.close();
            stateFileOut.close();
            //System.out.println("Serialized state saved in /peer " + pID + "/state.ser");
        } catch (IOException i) {
            System.err.println("Failed to save serialized state of peer " + pID);
            i.printStackTrace();
        }
    }

    public synchronized void loadState() {
        PeerContainer peerContainer = null;
        try {
            FileInputStream stateFileIn = new FileInputStream("peer " + pID + "/state.ser");
            ObjectInputStream in = new ObjectInputStream(stateFileIn);
            peerContainer = (PeerContainer) in.readObject();
            in.close();
            stateFileIn.close();
            System.out.println("Serialized state of peer " + pID + " loaded successfully");
        } catch (Exception i) {
            System.out.println("State file of peer " + pID + " not found, a new one will be created");
            this.saveState();
            return;
        }

        pID = peerContainer.getPeerID();
        storedFiles = peerContainer.getStoredFiles();
        storedChunks = peerContainer.getStoredChunks();
        occurences = peerContainer.getOccurences();
        freeSpace = peerContainer.getFreeSpace();
    }

    public int getPeerID() {
        return pID;
    }

    public static ArrayList<FileManager> getFilesFromFolder(State state){
        return new ArrayList<>();
    }

    public static ArrayList<FileChunk> getChunksFromFolder(State state){
        return new ArrayList<>();
    }

    public ArrayList<FileManager> getStoredFiles(){
        return storedFiles;
    }
    public synchronized ArrayList<FileChunk> getStoredChunks(){
        return storedChunks;
    }
    public synchronized ConcurrentHashMap<String, Integer> getOccurences(){return occurences;}
    public synchronized int getFreeSpace(){
        return freeSpace;
    }

    public void addStoredFile(FileManager file){
        this.storedFiles.add(file);
    }

    public synchronized boolean addStoredChunk(FileChunk chunk){
        for(FileChunk storedChunk : this.storedChunks){
            if(storedChunk == chunk) return false; // cant store equal chunks
        }
        this.storedChunks.add(chunk);
        return true;
    }

    public static String createKey(String fileID, int chunkNo){
        return fileID + "/" + chunkNo;
    }

    public synchronized boolean containsOccurence(String key){
        return this.occurences.containsKey(key);
    }

    public synchronized void incOccurences(String fileID, int chunkNo){
        String key = createKey(fileID, chunkNo);
        if(!containsOccurence(key)) {
            this.occurences.put(key, 1);
        } else {
            this.occurences.replace(key, this.occurences.get(key) + 1);
        }
    }

    public synchronized void setFreeSpace(int freeSpace){
        this.freeSpace = freeSpace;
    }

    public synchronized void decFreeSpace(int size){
        this.freeSpace -= size;
    }

    public synchronized void incFreeSpace(String fileID, int chunkNo){
        // chunk belongs to this peer
        for(FileChunk storedChunk : this.storedChunks){
            if(storedChunk.getFileID().equals(fileID) && storedChunk.getChunkNo() == chunkNo)
                this.freeSpace += storedChunk.getSize();
        }
    }

    public synchronized int getTotalOccupiedSpace(){
        return 8 * 1000000 - this.freeSpace;
    }



}
