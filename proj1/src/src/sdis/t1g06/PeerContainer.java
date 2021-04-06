package sdis.t1g06;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class PeerContainer {
    private ArrayList<FileManager> storedFiles;
    private ArrayList<FileChunk> storedChunks;
    private ConcurrentHashMap<String, Integer> occurences;
    private int freeSpace;

    public PeerContainer(){
        this.storedFiles = getFilesFromFolder(null);
        this.storedChunks = getChunksFromFolder(null);
        this.freeSpace = 8 * 1000000;
        this.occurences = new ConcurrentHashMap<>();
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
