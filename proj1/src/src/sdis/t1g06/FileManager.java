package sdis.t1g06;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;

public class FileManager {
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

    private void split(){
        int chunkNo = 1; // number of the first chunk
        int maxSize = 64000; // max size of chunk = 64kB
        byte[] buffer = new byte[maxSize]; // buffer with the size of the chunk

        // inside try(HERE) so "throws FileNotFound" is not necessary
        try(FileInputStream fileInputStream = new FileInputStream(this.file);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);){
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

    private String id(){
        String filename = this.file.getName();                      // file name
        String filedate = String.valueOf(this.file.lastModified()); // date modified
        String fileowner = this.file.getParent();                   // owner

        String originalString = filename + ":" + filedate + ":" + fileowner;
        return sha256(originalString); // sha-256 encryption
    }

    private static String sha256(String originalString){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(originalString.getBytes(StandardCharsets.UTF_8));
            StringBuffer hexString = new StringBuffer();
            // convert a byte array to a string of hex digits
            for (int i = 0; i < hash.length; i++){
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) hexString.append('0'); // 1 digit hexadecimal
                hexString.append(hex);
            }
            return hexString.toString();

        }catch(Exception e){
            System.err.println("Error in SHA-256 Encryptation.\n");
            throw new RuntimeException(e);
        }

    }
}
