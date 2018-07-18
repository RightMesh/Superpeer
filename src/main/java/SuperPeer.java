import ether.TransactionsManager;

import io.left.rightmesh.mesh.JavaMeshManager;
import io.left.rightmesh.util.MeshUtility;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import io.github.cdimascio.dotenv.Dotenv;

public class SuperPeer {
    public static final String TAG = SuperPeer.class.getCanonicalName();

    private static final String EXIT_CMD = "exit";
    private static final String CLOSE_CHANNEL_CMD = "close";

    JavaMeshManager mm;
    private boolean isRunning = true;
    private TransactionsManager tm;

    private Dotenv dotenv = null;

    public static void main(String[] args)
    {
        if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--headless"))) {
            // Doesn't read from STDIN if run with `-h` or `--headless`.
            // Useful for backgrounding.
            SuperPeer serviceMode = new SuperPeer(false);
        } else {
            // Default condition, runs in interactive mode.
            SuperPeer interactiveMode = new SuperPeer(true);
        }
    }

    public SuperPeer(boolean interactive)
    {
        dotenv = Dotenv.configure()
                .directory("/Users/rachel/Sites/SuperpeerVis/src/.env")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        mm = new JavaMeshManager(true);

        MeshUtility.Log(TAG, "Superpeer MeshID: " + mm.getUuid());
        MeshUtility.Log(TAG, "Superpeer is waiting for library ... ");
        try {
            Thread.sleep(200);

        } catch (InterruptedException ignored) { }


        tm = TransactionsManager.getInstance(mm);
        if (tm == null){
            MeshUtility.Log(TAG,"Failed to get TransactionManager from library. Superpeer is shutting down ...");
            mm.stop();
            System.exit(0);
        }
        tm.start();
        MeshUtility.Log(TAG, "Superpeer is ready!");

        // Start visualization
        if (dotenv.get("VISUALIZATION").equals("1")) {
            Visualization vis = new Visualization(dotenv);
        }

        // Stop everything when runtime is killed.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SuperPeer.this.finish();
        }));

        if (interactive) {
            // Block for user input if running in interactive mode.
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

            // Clean up and exit when exit command is entered.
            finish();
            System.exit(0);
        } else {
            // Infinitely loop if run in quiet mode.
            while (true) { /* Loop until killed. */ }
        }
    }

    /**
     * Default to interactive mode.
     */
    public SuperPeer() {
        this(true);
    }

    /**
     * Shut down mesh functionality cleanly. Must be run on exit or port will remain bound.
     */
    private void finish() {
        tm.stop();
        mm.stop();
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
                MeshUtility.Log(TAG, "Invalid command.");
                break;
        }
    }

    private void processCloseCmd(String[] args) {
        if(args.length != 2) {
            MeshUtility.Log(TAG, "Invalid args.");
            return;
        }

        tm.closeChannels(args[1]);
    }
}