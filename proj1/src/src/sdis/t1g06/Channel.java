package sdis.t1g06;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class Channel extends Thread {
    private final int mport;
    private final InetAddress maddress;
    private final String name;
    private MulticastSocket channel;

    public Channel(String maddress, int mport, String name) throws UnknownHostException {
        this.mport = mport;
        this.maddress = InetAddress.getByName(maddress);
        this.name = name;
    }

    public void run() {
        try {
            channel = new MulticastSocket(mport);
            channel.joinGroup(maddress);
        } catch (IOException e) {
            System.err.println("PEER: Failed to open multicast socket \"" + this.name + "\"!");
            return;
        }

        while(true) {
            byte[] buf = new byte[64500];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                channel.receive(packet);
            } catch (IOException e) {
                System.err.println("PEER: Failed on channel \"" + this.name + "\"'s receive()!");
                return;
            }
        }
    }

    public int sendMessage(byte[] buf) {
        DatagramPacket message;
        message = new DatagramPacket(buf, buf.length, this.maddress, this.mport);

        try {
            channel.send(message);
        } catch (IOException e) {
            System.err.println("PEER: Failed to send message on channel \"" + this.name + "\"!");
            return -1;
        }
        return 0;
    }
}
