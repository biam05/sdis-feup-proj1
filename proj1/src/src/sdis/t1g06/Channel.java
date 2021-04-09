package sdis.t1g06;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

enum ChannelType {
    MC,
    MDB,
    MDR;

    public String toString() {
        return switch (this) {
            case MC -> "MC";
            case MDB -> "MDB";
            case MDR -> "MDR";
        };
    }
}

public class Channel extends Thread {
    private final int peer_id;
    private final int mport;
    private final InetAddress maddress;
    private final String name;
    private MulticastSocket channel;
    private final ChannelType channelType;

    private int timeout = 3;

    public Channel(int peer_id, String maddress, int mport, String name, ChannelType channelType) throws UnknownHostException {
        this.peer_id = peer_id;
        this.mport = mport;
        this.maddress = InetAddress.getByName(maddress);
        this.name = name;
        this.channelType = channelType;
    }

    public void run() {
        try {
            channel = new MulticastSocket(mport);
            channel.joinGroup(maddress);
            System.out.println("> Peer " + peer_id + ": Connected to channel \"" + this.channelType.toString() + "\"");
        } catch (IOException e) {
            if(timeout == 0) {
                System.err.println("> Peer " + peer_id + ": Failed to open channel \"" + this.channelType.toString() + "\"");
                System.exit(-1);
            } else {
                timeout--;
                this.start();
            }
        }

        while(true) {
            byte[] buf = new byte[FileManager.CHUNK_MAX_SIZE + 500];
            DatagramPacket p = new DatagramPacket(buf, buf.length);

            try {
                channel.receive(p);
            } catch (IOException e) {
                System.err.println("> Peer " + peer_id + ": Failed on channel \"" + this.channelType.toString() + "\"'s receive()");
                System.exit(-1);
            }

            // Check if packet has not been sent by himself
            String message = new String(p.getData(), 0, p.getLength());
            String[] parts = message.split("\\s+");
            if(!parts[2].equals(String.valueOf(peer_id))) {
                Executors.newScheduledThreadPool(200).execute(() -> {
                    System.out.println("> Peer " + Peer.getPeerID() + ": Catched message on channel " + channelType.toString() + " from peer " + parts[2]);
                    Peer.treatMessage(p);
                });
            }
        }
    }

    public synchronized void sendMessage(byte[] buf) {
        DatagramPacket message;
        message = new DatagramPacket(buf, buf.length, this.maddress, this.mport);

        try {
            channel.send(message);
        } catch (Exception e) {
            System.err.println("> Peer " + peer_id + ": Failed to send message on channel \"" + this.channelType.toString() + "\"");
            e.printStackTrace();
            return;
        }
        System.out.println("> Peer " + peer_id + ": Sent message to the \"" + this.channelType.toString() + "\" channel");
    }
}
