package sdis.t1g06;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class Channel extends Thread {
    private final int peer_id;
    private final int mport;
    private final InetAddress maddress;
    private final String name;
    private MulticastSocket channel;

    private boolean isActive = false;
    private boolean hasFailed = false;

    private boolean hasMessage = false;
    private DatagramPacket packet;

    public Channel(int peer_id, String maddress, int mport, String name) throws UnknownHostException {
        this.peer_id = peer_id;
        this.mport = mport;
        this.maddress = InetAddress.getByName(maddress);
        this.name = name;
    }

    public void run() {
        try {
            channel = new MulticastSocket(mport);
            channel.joinGroup(maddress);
            System.out.println("> Peer " + peer_id + ": Connected to channel \"" + this.name + "\"");
            isActive = true;
        } catch (IOException e) {
            System.err.println("> Peer " + peer_id + ": Failed to open channel \"" + this.name + "\"");
            isActive = true;
            return;
        }

        while(true) {
            byte[] buf = new byte[64500];
            DatagramPacket p = new DatagramPacket(buf, buf.length);

            try {
                channel.receive(p);
            } catch (IOException e) {
                System.err.println("> Peer " + peer_id + ": Failed on channel \"" + this.name + "\"'s receive()");
                return;
            }

            // Check if packet has not been sent by himself
            String message = new String(p.getData(), 0, p.getLength());
            String[] parts = message.split(" ");
            if(!parts[2].equals(String.valueOf(peer_id))) {
                packet = p;
                hasMessage = true;
            }
        }
    }

    public boolean hasMessage() {
        return hasMessage;
    }

    public void setHasMessage(boolean hasMessage) {
        this.hasMessage = hasMessage;
    }

    public DatagramPacket getPacket() {
        return packet;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean hasFailed() {
        return hasFailed;
    }

    public int sendMessage(byte[] buf) {
        if(!isActive) return -1;
        DatagramPacket message;
        message = new DatagramPacket(buf, buf.length, this.maddress, this.mport);

        try {
            channel.send(message);
        } catch (Exception e) {
            System.err.println("> Peer " + peer_id + ": Failed to send message on channel \"" + this.name + "\"");
            e.printStackTrace();
            return -1;
        }
        System.out.println("> Peer " + peer_id + ": Sent message");
        return 0;
    }
}
