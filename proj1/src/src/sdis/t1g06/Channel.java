package sdis.t1g06;

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
            isActive = true;
            System.out.println("> Peer " + peer_id + ": Connected to multicast socket \"" + this.name + "\"");
        } catch (IOException e) {
            System.err.println("> Peer " + peer_id + ": Failed to open multicast socket \"" + this.name + "\"");
            e.printStackTrace();
            return;
        }

        while(true) {
            byte[] buf = new byte[64500];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                channel.receive(packet);
            } catch (IOException e) {
                System.err.println("> Peer " + peer_id + ": Failed on channel \"" + this.name + "\"'s receive()");
                e.printStackTrace();
                return;
            }
        }
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
