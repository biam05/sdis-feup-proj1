package sdis.t1g06;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;

public class FileManager implements Serializable {
    public final static int CHUNK_MAX_SIZE = 64000;

    private String fileID;
    private File file;
    private int replicationDegree;
    private ArrayList<FileChunk> chunks;

    public FileManager(String path, int replicationDegree){
        this.file = new File(path);
        this.replicationDegree = replicationDegree;
        this.chunks = new ArrayList<>();
        this.fileID = id();
        split();
    }

    public String getFileID(){
        return fileID;
    }

    public File getFile(){
        return file;
    }

    public int getReplicationDegree(){
        return replicationDegree;
    }

    public ArrayList<FileChunk> getChunks(){
        return chunks;
    }

    private synchronized void split() {
        int chunkNo = 0; // number of the first chunk
        int maxSize = CHUNK_MAX_SIZE; // max size of chunk = 64kB
        byte[] buffer = new byte[maxSize]; // buffer with the size of the chunk

        try(FileInputStream fileInputStream = new FileInputStream(this.file);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)){
            int size;
            while((size = bufferedInputStream.read(buffer)) > 0) { // an entire chunk left (at least)
                byte[] content = Arrays.copyOf(buffer, size); // copy content to create chunk
                FileChunk fileChunk = new FileChunk(this.fileID, chunkNo, content, size);
                this.chunks.add(fileChunk);
                chunkNo++; // number of the next chunk
                buffer = new byte[maxSize]; // prepare to get next chunk
            }
            // If the file size is a multiple of the chunk size, the last chunk has size 0
            if(this.file.length() % maxSize == 0){
                FileChunk fileChunk = new FileChunk(this.fileID, chunkNo + 1, null, 0);
                this.chunks.add(fileChunk);
            }
        } catch (FileNotFoundException fe) {
            System.err.println("The file " + this.file.getName() + " does not exist in this peer");
        } catch(Exception e) {
            System.err.println("Error splitting " + this.file.getName() + " file in chunks.\n");
            e.printStackTrace();
        }
    }

    private synchronized String id(){
        String filename = this.file.getName();                      // file name
        String filedate = String.valueOf(this.file.lastModified()); // date modified
        String fileowner = this.file.getParent();                   // owner

        String originalString = filename + ":" + filedate + ":" + fileowner;
        return sha256(originalString); // sha-256 encryption
    }

    private synchronized static String sha256(String originalString){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(originalString.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            // convert a byte array to a string of hex digits
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0'); // 1 digit hexadecimal
                hexString.append(hex);
            }
            return hexString.toString();

        }catch(Exception e){
            System.err.println("Error in SHA-256 Encryptation.\n");
            throw new RuntimeException(e);
        }

    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FileManager)) {
            return false;
        }

        FileManager fm = (FileManager) o;

        return this.fileID.equals(fm.fileID);
    }

    public synchronized void createFile(Path path, int pID) {
        byte[] content = new byte[0];
        for(int i = 0; i < chunks.size(); i++) {
            for (FileChunk chunk : chunks) {
                if(chunk.getChunkNo() == i) {
                    byte[] tmp = new byte[content.length];
                    System.arraycopy(content, 0, tmp, 0, content.length);
                    content = new byte[tmp.length + chunk.getContent().length];
                    System.arraycopy(tmp, 0, content, 0, tmp.length);
                    System.arraycopy(chunk.getContent(), 0, content, tmp.length, chunk.getContent().length);
                    break;
                }
            }
        }

        try {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            fileChannel.write(ByteBuffer.wrap(content), 0);
            fileChannel.close();
            System.out.println("> Peer " + pID + ": File at " + path + " was created successfully");
        } catch (IOException e) {
            System.err.println("> Peer " + pID + ": File at " + path + " was not created successfully");
            e.printStackTrace();
        }
    }
}
