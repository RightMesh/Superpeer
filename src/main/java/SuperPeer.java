
import ether.TransactionsManager;

import io.left.rightmesh.mesh.JavaMeshManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SuperPeer {

    //TODO: Add logger instead of system.out
    public static final String TAG = SuperPeer.class.getCanonicalName();

    private static final String EXIT_CMD = "exit";
    private static final String CLOSE_CHANNEL_CMD = "close";


    JavaMeshManager mm;
    private boolean isRunning = true;
    private TransactionsManager tm;

    public static void main(String[] args) {
        SuperPeer p = new SuperPeer();
    }

    public SuperPeer() {
        mm = new JavaMeshManager(true);
        System.out.println("Superpeer MeshID: " + mm.getUuid());
        System.out.println("Superpeer is waiting for library ... ");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
        tm = TransactionsManager.getInstance(mm);
        if(tm == null){
            System.out.println("Failed to get TransactionManager from library. Superpeer is shutting down ...");
            mm.stop();
            System.exit(0);
        }
        tm.start();
        System.out.println("Superpeer is ready!");
        String msg;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        do {
            try {
                msg = br.readLine();
                processInput(msg);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        } while (isRunning);

        tm.stop();
        mm.stop();

        System.exit(0);
    }

    private void processInput(String msg) {
        if(msg.equals(EXIT_CMD)) {
            isRunning = false;
            return;
        }

        String[] args = msg.split(" ");
        if(args.length == 0) {
            return;
        }

        switch (args[0]) {
            case CLOSE_CHANNEL_CMD:
                processCloseCmd(args);
                break;

            default:
                System.out.println("Invalid command.");
                break;
        }
    }

    private void processCloseCmd(String[] args) {
        if(args.length != 2) {
            System.out.println("Invalid args.");
            return;
        }

        tm.closeChannels(args[1]);
    }
}
