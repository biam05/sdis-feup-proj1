package sdis.t1g06;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;

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
        int chunkNo = 0;
        int maxSize = 64000; // max size of chunk = 64kB
        byte[] buffer = new byte[maxSize];
        try(){

        }
        catch(IOException e){
            System.err.println("Error splitting " + this.file.getName() + " file in chunks.\n");
            throw e;
        }

    }

    private String id(){
        String filename = this.file.getName();                      // file name
        String filedata = String.valueOf(this.file.lastModified()); // date modified
        String fileowner = this.file.getParent();                   // owner

        String originalString = filename + ":" + filedata + ":" + fileowner;
        return sha256(originalString); // sha-256 encryptation
    }
    // !!!!!!!!!!!!!!!!!!
    // Perguntar ao professor se podemos utilizar esta função
    // https://www.geeksforgeeks.org/sha-256-hash-in-java/
    private static String sha256(String originalString){
        try{
            // Static getInstance method is called with hashing SHA
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // digest() method called
            // to calculate message digest of an input
            // and return array of byte
            byte[] hash = md.digest(
                    originalString.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, hash);

            // Convert message digest into hex value
            StringBuilder hexString = new StringBuilder(number.toString(16));

            // Pad with leading zeros
            while (hexString.length() < 32)
            {
                hexString.insert(0, '0');
            }
            return hexString.toString();

        }catch(Exception e){
            System.err.println("Error in SHA-256 Encryptation.\n");
            throw new RuntimeException(e);
        }

    }
}
