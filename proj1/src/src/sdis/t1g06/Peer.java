package sdis.t1g06;

public class Peer implements ServiceInterface{

    private static int id;

    private Peer() {
    }

    public static void main(String[] args) {

        Peer peer = new Peer();

    }

    @Override
    public String backup(String fileName, int replicationDegree) {

        return null;
    }
}
