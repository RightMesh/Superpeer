package ether;

import static io.left.rightmesh.mesh.MeshManager.TRANSACTION_RECEIVED;

import io.left.rightmesh.id.MeshID;
import io.left.rightmesh.mesh.JavaMeshManager;
import io.left.rightmesh.mesh.MeshManager;
import io.left.rightmesh.proto.MeshTransaction;
import io.left.rightmesh.util.EtherUtility;
import io.left.rightmesh.util.MeshUtility;
import io.left.rightmesh.util.RightMeshException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.*;


/**
 * The Transactions manager, responsible to process incoming Ether Transactions from Clients in the Mesh network.
 */
public final class TransactionsManager {

    private JavaMeshManager meshManager;
    private BlockingQueue<MeshManager.MeshTransactionEvent> transactionsQueue = new LinkedBlockingQueue<>();
    private Thread queueThread = null;
    private volatile boolean isRunning = false;
    private Http httpAgent;
    private MeshID ownMeshId;


    private static volatile TransactionsManager instance = null;

    public static TransactionsManager getInstance(MeshManager mm) {
        if (instance == null) {
            synchronized (TransactionsManager.class) {
                if (instance == null) {
                    instance = new TransactionsManager(mm);
                }
            }
        }

        return instance;
    }

    private TransactionsManager(MeshManager mm) {
        httpAgent = new Http(Settings.RPC_ADDRESS, Settings.DEBUG_INFO);
        meshManager = (JavaMeshManager) mm;
        ownMeshId = mm.getUuid();
        mm.on(TRANSACTION_RECEIVED, this::handleTransactionPacket);
    }


    /**
     * Handles Transaction packets from the Mesh network.
     *
     * @param rmEvent   The RightMesh event.
     */
    private void handleTransactionPacket(MeshManager.RightMeshEvent rmEvent) {
        if (Settings.DEBUG_INFO) {
            System.out.println("Transaction received.");
        }

        MeshManager.MeshTransactionEvent event = (MeshManager.MeshTransactionEvent) rmEvent;
        insertTransaction(event);
    }

    /**
     * Returns Running state of the Transaction Manager
     *
     * @return  True if running, otherwise returns False.
     */
    public synchronized boolean isRunning() {
        return isRunning;
    }


    /**
     * Starts the Transactions Manager
     */
    public synchronized void start() {
        if (!isRunning) {
            queueThread = new Thread(this::processTransactionsQueue);
            queueThread.start();
            isRunning = true;
        }
    }


    /**
     * Stops the Transactions Manager and cleans resources.
     */
    public synchronized void stop() {
        isRunning = false;
        insertStopMessage();

        if (queueThread != null && queueThread.isAlive()) {
            try {
                queueThread.join();
            } catch (InterruptedException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("InterruptedException: " + e);
                }
            }
        }

        transactionsQueue.clear();
    }

    /**
     * Injects dummy stop message, to unblock queue take blocking method.
     */
    private void insertStopMessage() {
        JSONObject stopMessage = new JSONObject();
        stopMessage.put("method", "stop");
        MeshTransaction dummyStopTransaction = new MeshTransaction(stopMessage.toJSONString().getBytes());
        MeshManager.MeshTransactionEvent event = new MeshManager.MeshTransactionEvent(dummyStopTransaction, ownMeshId);
        insertTransaction(event);
    }

    /**
     * Inserts transaction to the transactions queue.
     *
     * @param event     The Mesh transaction evet.
     */
    private void insertTransaction(MeshManager.MeshTransactionEvent event) {
        if (!transactionsQueue.offer(event)) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to add transaction to the queue. Probably the queue is full.");
                System.out.println("Num of elements in the queue: " + transactionsQueue.size());
            }
        }
    }

    /**
     * Processes Transactions queue in a separate managed Thread.
     */
    private void processTransactionsQueue() {
        while (isRunning) {
            MeshManager.MeshTransactionEvent event;
            try {
                event = transactionsQueue.take();
            } catch (InterruptedException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("InterruptedException: " + e);
                }
                continue;
            }

            processTransaction(event);
        }
    }


    /**
     * Process the Mesh network trnasctions from Clients-Remote Peers.
     *
     * @param event     The Mesh transaction event.
     */
    private void processTransaction(MeshManager.MeshTransactionEvent event) {
        MeshTransaction transaction = event.transaction;
        byte[] transactionData = transaction.data;

        JSONObject jsonObject;
        JSONParser parser = new JSONParser();
        try {
            jsonObject = (JSONObject) parser.parse(new String(transactionData));
        } catch (ParseException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to parse transaction, ParseException: " + e);
            }
            return;
        }

        String method = (String) jsonObject.get("method");
        switch (method) {
            case "stop":
                //Dummy packet - Do nothing
                break;
            case EtherUtility.METHOD_GET_ALL:
                processGetAllRequest(event.peerUuid);
                break;

            case EtherUtility.METHOD_OPEN_CLIENT_TO_SUPER_PEER:
                processOpenInChannelRequest(event.peerUuid, jsonObject);
                break;
            case EtherUtility.METHOD_ACTIVE_UPDATE:
                processActiveUpdateReq(event.peerUuid,jsonObject);
                break;
            case EtherUtility.METHOD_CLOSE_CLIENT_TO_SUPERPEER:
                processCloseClientToSuperpeerReq(event.peerUuid,jsonObject);
                break;
            case EtherUtility.METHOD_CLOSE_SUPERPEER_TO_CLIENT:
                processCloseSuperpeerToClientReq(event.peerUuid,jsonObject);
                break;
            default:
                if (Settings.DEBUG_INFO) {
                    System.out.println("default case in processTransaction method.");
                }
                break;
        }
    }


    /**
     * Processes the Get All request from Client-Remote Peer
     *
     * @param sourceId  The source id.
     */
    private void processGetAllRequest(MeshID sourceId) {

        System.out.println("GetAll received from " + sourceId);
        System.out.println("Checking if Out-Channel " + ownMeshId + "-->" + sourceId + " exists.");

        //Check if SuperPeer-->Client exists in the Ether Network.
        EtherUtility.PaymentChannel outChannel = getChannelFromEtherNetwork(ownMeshId, sourceId);
        if(outChannel == null) {

            System.out.println("Out-Channel doesn't exist, trying to open " + ownMeshId + "-->" + sourceId);

            //If SuperPeer-->Client channel doesn't exist in the Ether network, lets try to open it.
            outChannel = openChannel(ownMeshId, sourceId);
            if (outChannel == null) {

                System.out.println("Failed to open channel.");

                if (Settings.DEBUG_INFO) {
                    System.out.println("Fatal error, cannot establish SuperPeer-->Client channel: "
                            + ownMeshId + "-->" + sourceId);
                }
                byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL,
                        "Failed to establish channel: SuperPeer-->Client.");
                sendTransaction(sourceId, data);
                return;
            }
            //For a new channel created, we set the balance to be 0 and create the BPS signature.
            ImmutablePair<byte[], BigInteger> balanceProofPair = meshManager.getTransactionManager()
                    .calculateNewBalanceProofToReceiver(BigInteger.ZERO,sourceId.getRawUuid());
            meshManager.getTransactionManager().removeMostRecentBillToReceiver(sourceId);
            meshManager.getTransactionManager().putNewBalanceProofToReceiver(sourceId.getRawUuid(),balanceProofPair);
            outChannel.setSignaturePair(balanceProofPair);
            System.out.println("Out-Channel OPENED: " + ownMeshId + "-->" + sourceId);
        }
        else {
            //For now, we have not yet implemented DB to save channel balance. Try to look from memory.
            ImmutablePair<byte[], BigInteger> balanceProofPair = null;
            try{
                ImmutablePair<ImmutablePair<byte[],BigInteger>,ImmutablePair<byte[],BigInteger>>
                        bill = meshManager.getTransactionManager().getMostRecentBillToReceiver(sourceId.getRawUuid());
                balanceProofPair=bill.getLeft();
            }catch (RightMeshException e){
                //do nothing
            }
            if(balanceProofPair==null){
                balanceProofPair=meshManager.getTransactionManager()
                        .calculateNewBalanceProofToReceiver(BigInteger.ZERO,sourceId.getRawUuid());
                meshManager.getTransactionManager().putNewBalanceProofToReceiver(sourceId.getRawUuid(),balanceProofPair);
            }
            outChannel.setSignaturePair(balanceProofPair);
            System.out.println("Out-Channel already exist " + ownMeshId + "-->" + sourceId);
        }


        if (Settings.DEBUG_INFO) {
            System.out.println("SuperPeer-->Client: " + outChannel);
        }

        System.out.println("Checking In-Channel: " + sourceId + "-->" + ownMeshId);

        //Check if Client-->SuperPeer channel exists in the Ether Network.
        EtherUtility.PaymentChannel inChannel = getChannelFromEtherNetwork(sourceId, ownMeshId);

        if (Settings.DEBUG_INFO) {
            System.out.println("Client-->SuperPeer: " + inChannel == null ? "null" : inChannel);
        }

        if(inChannel == null) {
            System.out.println("In-Channel doesn't exist");
        }
        else {
            ImmutablePair<byte[], BigInteger> closingHashPair = null;
            try{
                ImmutablePair<ImmutablePair<byte[],BigInteger>,ImmutablePair<byte[],BigInteger>>
                        bill = meshManager.getTransactionManager().getMostRecentBillFromSender(sourceId.getRawUuid());
                closingHashPair=bill.getRight();
            }catch (RightMeshException e){
                //do nothing
            }
            if(closingHashPair==null){
                closingHashPair=meshManager.getTransactionManager()
                        .calculateNewClosingHashFromSender(BigInteger.ZERO,sourceId.getRawUuid());
                meshManager.getTransactionManager().putNewClosingHashFromSender(sourceId.getRawUuid(),closingHashPair);
            }
            inChannel.setSignaturePair(closingHashPair);
            System.out.println("In-Channel already exist.");
        }

        System.out.println("Collecting data... ");

        //Get client EtherBalance
        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Ether balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get Ether balance.");
            sendTransaction(sourceId, data);
            return;
        }

        //Get client Token balance
        String clientTokenBalance;
        try {
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get Token balance.");
            sendTransaction(sourceId, data);
            return;
        }

        BigInteger clientNonce = EtherClient.getNonce(sourceId.toString(), httpAgent);
        if (clientNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get nonce.");
            sendTransaction(sourceId, data);
            return;
        }

        System.out.println("Remote Peer Ether balance: " + clientEtherBalance);
        System.out.println("Remote Peer Token balance: " + clientTokenBalance);
        System.out.println("Remote Peer Nonce: " + clientNonce);

        byte[] data = JSON.sendGetAllResponse(outChannel, inChannel, clientEtherBalance, clientTokenBalance, clientNonce);
        sendTransaction(sourceId, data);

        System.out.println("Response sent.");
    }

    /**
     * Processes the Get All request from Client-Remote Peer
     *
     * @param sourceId  The source id.
     */
    private void processActiveUpdateReq(MeshID sourceId, JSONObject jsonObject) {

        System.out.println("ActiveUpdateRequest received from " + sourceId);
        Object closingHashBalance = jsonObject.get("closingHashBalance");
        Object closingHashSignature = jsonObject.get("closingHashSignature");

        ImmutablePair<byte[], BigInteger> closingHashPairAtSender=null;
        try{
            closingHashPairAtSender
                    =meshManager.getTransactionManager().getMostRecentBillToReceiver(sourceId.getRawUuid()).getRight();
            if(closingHashBalance!=null&&closingHashSignature!=null){
                BigInteger chb=new BigInteger((String)closingHashBalance);
                System.out.println("The balance in active update from "+sourceId+" is "+chb);
                byte[] chs=null;
                try{
                    chs=Hex.decodeHex(((String)closingHashSignature).toCharArray());
                }catch(DecoderException e){
                    //do nothing;
                }
                if(chb!=null&&chs!=null&&(chb.compareTo(closingHashPairAtSender.right)>0)){
                    meshManager.getTransactionManager().putNewClosingHashToReceiver(
                            sourceId.getRawUuid(),new ImmutablePair(chs,chb));
                }
            }
        }catch (RightMeshException e){
            System.out.println("Cannot find a outgoing channel from superpeer to "+sourceId);
        }


        ImmutablePair<byte[], BigInteger> closingHashPairAtReceiver=null;
        try{
            closingHashPairAtReceiver
                    =meshManager.getTransactionManager().getMostRecentBillFromSender(sourceId.getRawUuid()).getRight();
        }catch (RightMeshException e){
            //do nothing
        }


        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Ether balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get Ether balance.");
            sendTransaction(sourceId, data);
            return;
        }

        //Get client Token balance
        String clientTokenBalance;
        try {
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get Token balance.");
            sendTransaction(sourceId, data);
            return;
        }

        BigInteger clientNonce = EtherClient.getNonce(sourceId.toString(), httpAgent);
        if (clientNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get nonce.");
            sendTransaction(sourceId, data);
            return;
        }

        System.out.println("Remote Peer Ether balance: " + clientEtherBalance);
        System.out.println("Remote Peer Token balance: " + clientTokenBalance);
        System.out.println("Remote Peer Nonce: " + clientNonce);


        byte[] data = JSON.sendActiveUpdateResponse(closingHashPairAtReceiver, clientEtherBalance, clientTokenBalance, clientNonce);
        sendTransaction(sourceId, data);

        System.out.println("Response sent.");
    }

    /**
     * Processes Open Client to SuperPeer request from a Client-Remote Peer.
     *
     * @param sourceId      The MeshId of the remote peer.
     * @param jsonObject    The Transaction data.
     */
    private void processOpenInChannelRequest(MeshID sourceId, JSONObject jsonObject) {

        System.out.println("Open In-Channel received from " + sourceId);
        System.out.println("Checking if In-Channel " + sourceId + "-->" + ownMeshId + " exists.");

        Object signedApproveTransaction = jsonObject.get("signedApproveTrans");
        if (signedApproveTransaction == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("No signed approve transaction in the open channel request from client.");
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "No signed approve transaction in the open channel request from client.");
            sendTransaction(sourceId, data);
            return;
        }


        Object signedOpenChannelTransaction = jsonObject.get("signedOpenChannelTrans");
        if (signedOpenChannelTransaction == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("No signed transaction in the open channel request from client.");
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "No signed transaction in the open channel request from client.");
            sendTransaction(sourceId, data);
            return;
        }

        Object signature = jsonObject.get("zeroBalanceProofSignature");
        if (signature == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("No zeroBalanceProofSignature.");
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "No zeroBalanceProofSignature.");
            sendTransaction(sourceId, data);
            return;
        }
        byte [] zeroBalanceProofSignature=null;
        try{
            zeroBalanceProofSignature=Hex.decodeHex(((String)signature).toCharArray());
        }catch (DecoderException e){
            System.out.println("Signature in process open channel cannot be decoded.");
        }


        //Check if already exists in the Ether network.
        EtherUtility.PaymentChannel inChannel = getChannelFromEtherNetwork(sourceId, ownMeshId);
        if (inChannel != null) {

            System.out.println("Error: In-Channel already exist");

            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Client-->SuperPeer channel already open.");
            sendTransaction(sourceId, data);
            return;
        }

        System.out.println("In-Channel doesn't exist, trying to open " + sourceId + "-->" + ownMeshId);

        //Tries to open Client-->SuperPeer channel
        if (Settings.DEBUG_INFO) {
            System.out.println("Trying to open " + ownMeshId + "-->" + sourceId + " channel.");
        }

        inChannel = openChannel(sourceId, ownMeshId, signedApproveTransaction.toString(),
                signedOpenChannelTransaction.toString());
        if(inChannel == null){

            System.out.println("Failed to open In-Channel: " + sourceId + "-->" + ownMeshId);

            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Failed to open channel Client-->SuperPeer.");
            sendTransaction(sourceId, data);
            return;
        }

        //For a new channel created, we set the balance to be 0 and create the CHS signature.
        ImmutablePair<byte[], BigInteger> closingHashPair =meshManager.getTransactionManager()
                .calculateNewClosingHashFromSender(BigInteger.ZERO,sourceId.getRawUuid());
        meshManager.getTransactionManager().removeMostRecentBillFromSender(sourceId);
        meshManager.getTransactionManager().putNewClosingHashFromSender(sourceId.getRawUuid(),closingHashPair);
        meshManager.getTransactionManager().putNewBalanceProofFromSender(sourceId.getRawUuid(),new ImmutablePair<>(zeroBalanceProofSignature,BigInteger.ZERO));
        inChannel.setSignaturePair(closingHashPair);

        System.out.println("In-Channel Opened: " + sourceId + "-->" + ownMeshId);
        System.out.println("Collecting data... ");

        //Get client EtherBalance
        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Ether balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Failed to get Ether balance.");
            sendTransaction(sourceId, data);
            return;
        }

        //Get client Token balance
        String clientTokenBalance;
        try {
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Failed to get Token balance.");
            sendTransaction(sourceId, data);
            return;
        }

        BigInteger clientNonce = EtherClient.getNonce(sourceId.toString(), httpAgent);
        if (clientNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Failed to get nonce.");
            sendTransaction(sourceId, data);
            return;
        }


        System.out.println("Remote Peer Ether balance: " + clientEtherBalance);
        System.out.println("Remote Peer Token balance: " + clientTokenBalance);
        System.out.println("Remote Peer Nonce: " + clientNonce);

        byte[] data = JSON.sendOpenClientToSpResponse(inChannel, clientEtherBalance, clientTokenBalance, clientNonce);
        sendTransaction(sourceId, data);

        System.out.println("Response sent.");
    }

    private void processCloseClientToSuperpeerReq(MeshID sourceId, JSONObject jsonObject){
        System.out.println("Close client to superpeer request is received from " + sourceId);

        Object signedCloseClientToSuperTransaction = jsonObject.get("closeClientToSuperSig");
        if (signedCloseClientToSuperTransaction == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("No signed close to superpeer channel transaction in the request from client.");
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_TO_SUPER_PEER,
                    "No signed transaction.");
            sendTransaction(sourceId, data);
            return;
        }

        System.out.println("Checking if In-Channel " + sourceId + "-->" + ownMeshId + " exists.");

        //Check if already exists in the Ether network.
        EtherUtility.PaymentChannel inChannel = getChannelFromEtherNetwork(sourceId, ownMeshId);
        if(inChannel == null) {

            System.out.println("Error: Channel from "+sourceId+" to superpeer does not exist");

            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_TO_SUPER_PEER,
                    "Not channel found.");
            sendTransaction(sourceId, data);
            return;
        }

        boolean result;
        try{
            result=EtherClient.closeChannel(sourceId.toString(),ownMeshId.toString(),(String)signedCloseClientToSuperTransaction,httpAgent);
        }catch (IOException | IllegalArgumentException e){
            result =false;
        }


        if(!result){

            System.out.println("Failed to close client to superpeer channel: " + sourceId + "-->" + ownMeshId);

            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_TO_SUPER_PEER,
                    "Failed close channel Client-->SuperPeer.");
            sendTransaction(sourceId, data);
            return;
        }

        meshManager.getTransactionManager().removeMostRecentBillFromSender(sourceId);

        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Ether balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_TO_SUPER_PEER,
                    "Failed to get Ether balance.");
            sendTransaction(sourceId, data);
            return;
        }

        //Get client Token balance
        String clientTokenBalance;
        try {
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_TO_SUPER_PEER,
                    "Failed to get Token balance.");
            sendTransaction(sourceId, data);
            return;
        }

        BigInteger clientNonce = EtherClient.getNonce(sourceId.toString(), httpAgent);
        if (clientNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Failed to get nonce.");
            sendTransaction(sourceId, data);
            return;
        }


        System.out.println("Remote Peer Ether balance: " + clientEtherBalance);
        System.out.println("Remote Peer Token balance: " + clientTokenBalance);
        System.out.println("Remote Peer Nonce: " + clientNonce);

        byte[] data = JSON.sendCloseClientToSuperResponse(clientEtherBalance, clientTokenBalance, clientNonce);
        sendTransaction(sourceId, data);

        System.out.println("Response sent.");
    }

    private void processCloseSuperpeerToClientReq(MeshID sourceId, JSONObject jsonObject){
        System.out.println("Close superpeer to client request is received from " + sourceId);

        Object signedCloseSuperToClientTransaction = jsonObject.get("closeSuperToClientSig");
        if (signedCloseSuperToClientTransaction == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("No signed close from superpeer channel transaction in the request from client.");
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_FROM_SUPER_PEER,
                    "No signed transaction.");
            sendTransaction(sourceId, data);
            return;
        }

        System.out.println("Checking if Out-Channel " + ownMeshId + "-->" + sourceId + " exists.");

        //Check if already exists in the Ether network.
        EtherUtility.PaymentChannel outChannel = getChannelFromEtherNetwork(ownMeshId,sourceId);
        if(outChannel == null) {

            System.out.println("Error: Channel from "+sourceId+" to superpeer does not exist");

            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_FROM_SUPER_PEER,
                    "Not channel found.");
            sendTransaction(sourceId, data);
            return;
        }

        boolean result;
        try{
            result=EtherClient.closeChannel(ownMeshId.toString(), sourceId.toString(),(String)signedCloseSuperToClientTransaction,httpAgent);
        }catch (IOException | IllegalArgumentException e){
            result =false;
        }


        if(!result){

            System.out.println("Failed to close superpeer to client channel: " + ownMeshId + "-->" + sourceId);

            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_FROM_SUPER_PEER,
                    "Failed close channel Client-->SuperPeer.");
            sendTransaction(sourceId, data);
            return;
        }

        meshManager.getTransactionManager().removeMostRecentBillToReceiver(sourceId);

        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Ether balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_FROM_SUPER_PEER,
                    "Failed to get Ether balance.");
            sendTransaction(sourceId, data);
            return;
        }

        //Get client Token balance
        String clientTokenBalance;
        try {
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_FROM_SUPER_PEER,
                    "Failed to get Token balance.");
            sendTransaction(sourceId, data);
            return;
        }

        BigInteger clientNonce = EtherClient.getNonce(sourceId.toString(), httpAgent);
        if (clientNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_CLOSE_CHANNEL_FROM_SUPER_PEER,
                    "Failed to get nonce.");
            sendTransaction(sourceId, data);
            return;
        }


        System.out.println("Remote Peer Ether balance: " + clientEtherBalance);
        System.out.println("Remote Peer Token balance: " + clientTokenBalance);
        System.out.println("Remote Peer Nonce: " + clientNonce);

        byte[] data = JSON.sendCloseSuperToClientResponse(clientEtherBalance, clientTokenBalance, clientNonce);
        sendTransaction(sourceId, data);

        System.out.println("Response sent.");
    }

    /**
     * Tries to get the payment channel from Ether network.
     * @param senderID The sender address.
     * @param receiverID The receiver address.
     * @return The Payment channel if exists in the Ether network, otherwise returns null.
     */
    private EtherUtility.PaymentChannel getChannelFromEtherNetwork(MeshID senderID, MeshID receiverID) {

        EtherUtility.PaymentChannel channel;
        try {
            channel = EtherClient.getChannelInfo(senderID.toString(), receiverID.toString(), httpAgent);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to execute getChannelInfo request. "
                        + e.getClass().getCanonicalName() + ": " + e.getMessage());
            }
            return null;
        }

        return channel;
    }

    /**
     * Tries to open payment channel in the Ether network.
     * @param sender The channel's sender.
     * @param receiver The channel's receiver.
     * @return Returns PaymentChannel objects if succeeded, otherwise returns null.
     */
    private EtherUtility.PaymentChannel openChannel(MeshID sender, MeshID receiver) {

        String senderAddress = sender.toString();
        String recvAddress = receiver.toString();

        EtherUtility.PaymentChannel channel;

        //Try to get nonce of the sender
        BigInteger senderNonce = EtherClient.getNonce(senderAddress, httpAgent);
        if (senderNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for address: " + senderAddress);
            }
            return null;
        }

        //Approve Channel contract to transfer tokens to the newly created payment channel.
        String signedApproveTransaction = EtherUtility.getSignedApproveTrans(sender, Settings.INIT_DEPOSIT,
                senderNonce, Settings.TOKEN_ABI, Settings.GAS_PRICE, Settings.GAS_LIMIT,
                Settings.CHANNEL_CONTRACT_ADDRESS, Settings.TOKEN_CONTRACT_ADDRESS, Settings.CHAIN_ID);

        if (signedApproveTransaction == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to construct approve transaction.");
            }
            return null;
        }

        boolean res;
        try {
            res = EtherClient.approve(senderAddress, Settings.INIT_DEPOSIT, signedApproveTransaction, httpAgent);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to submit approve transaction. IOException: " + e.getMessage());
            }
            return null;
        }

        if (!res) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to submit approve transaction.");
            }
            return null;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("Trying to open " + senderAddress + "-->" + recvAddress + " channel.");
        }


        //Increment by 1, as approve transaction executed
        senderNonce = senderNonce.add(BigInteger.ONE);

        //Create and sign open sender-->receiver channel transaction
        String signedOpenChannelTrans = EtherUtility.getSignedOpenChannelTrans(sender, recvAddress,
                Settings.INIT_DEPOSIT, senderNonce, Settings.CHANNEL_ABI, Settings.GAS_PRICE, Settings.GAS_LIMIT,
                Settings.CHANNEL_CONTRACT_ADDRESS, Settings.CHAIN_ID);

        if (signedOpenChannelTrans == null || signedOpenChannelTrans == "") {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get signedOpenChannelTrans for: " + senderAddress + "-->" + recvAddress);
            }
            return null;
        }

        //Send the signed open payment channel transaction to the Ether network.
        try {
            channel = EtherClient.openChannel(senderAddress, recvAddress, Settings.INIT_DEPOSIT,
                    signedOpenChannelTrans, httpAgent);
        } catch (IOException | IllegalArgumentException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to open channel " + senderAddress + "-->" + recvAddress + ", "
                        + e.getClass().getCanonicalName() + ": " + e.getMessage());
            }
            return null;
        }

        //Failed to open the payment channel in the Ether network, has no more ideas
        if (channel == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to open channel: " + senderAddress + "-->" + recvAddress);
            }
            return null;
        }

        return channel;
    }


    private EtherUtility.PaymentChannel openChannel(MeshID sender, MeshID receiver,
                                                    String signedApproveTrans, String signedOpenChannelTrans) {

        String senderAddress = sender.toString();
        String recvAddress = receiver.toString();

        EtherUtility.PaymentChannel channel;

        //Try to get nonce of the sender
        BigInteger senderNonce = EtherClient.getNonce(senderAddress, httpAgent);
        if (senderNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for address: " + senderAddress);
            }
            return null;
        }

        //Approve Channel contract to transfer tokens to the newly created payment channel.
        String signedApproveTransaction = signedApproveTrans;

        if (signedApproveTransaction == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to construct approve transaction.");
            }
            return null;
        }

        boolean res = true;
        try {
            res = EtherClient.approve(senderAddress, Settings.INIT_DEPOSIT, signedApproveTransaction, httpAgent);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to submit approve transaction. IOException: " + e.getMessage());
            }
            return null;
        }

        if (!res) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to submit approve transaction.");
            }
            return null;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("Trying to open " + senderAddress + "-->" + recvAddress + " channel.");
        }

        //Increment by 1, as approve transaction executed
        senderNonce = senderNonce.add(BigInteger.ONE);

        if (signedOpenChannelTrans == null || signedOpenChannelTrans == "") {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get signedOpenChannelTrans for: " + senderAddress + "-->" + recvAddress);
            }
            return null;
        }

        //Send the signed open payment channel transaction to the Ether network.
        try {
            channel = EtherClient.openChannel(senderAddress, recvAddress, Settings.INIT_DEPOSIT,
                    signedOpenChannelTrans, httpAgent);
        } catch (IOException | IllegalArgumentException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to open channel " + senderAddress + "-->" + recvAddress + ", "
                        + e.getClass().getCanonicalName() + ": " + e.getMessage());
            }
            return null;
        }

        //Failed to open the payment channel in the Ether network, has no more ideas
        if (channel == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to open channel: " + senderAddress + "-->" + recvAddress);
            }
            return null;
        }

        return channel;
    }


    /**
     * Closes In-Channel and Out-Channel of the remote peer.
     *
     * @param remotePeerAddress     The remote peer address.
     */
    public void closeChannels(String remotePeerAddress) {

        MeshID remotePeerMeshId;
        try {
            remotePeerMeshId = new MeshID(remotePeerAddress);
        }catch (RightMeshException e){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to parse MeshId from address: " + remotePeerAddress);
            }
            return;
        }

        //Check for In-Channel
        ImmutablePair<ImmutablePair<byte[], BigInteger>, ImmutablePair<byte[], BigInteger>> bill = null;
        try {
            bill = meshManager.getTransactionManager().getMostRecentBillFromSender(remotePeerMeshId.getRawUuid());
        } catch (RightMeshException e){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get the most recent bill from sender, RightMeshException: "
                        + e.getMessage());
            }
        }

        if(bill != null) {
            //In-Channel exist, lets try to close it.
            System.out.println(ownMeshId + " --> " + remotePeerAddress
                    + " bill from sender found, trying to close In-Channel...");

            ImmutablePair<byte[], BigInteger> balanceProofSig = bill.getLeft();
            ImmutablePair<byte[], BigInteger> closingSig = bill.getRight();

            //validate the balance in both pairs (balanceProofSig and closingSig)
            //In the In-Channel both balances should be the same, as the balanceProof received from the remote peer
            //and the ClosingSig should be generated locally.
            if(!balanceProofSig.right.equals(closingSig.right)) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Error closing In-Channel: balance in BalanceProofSig not equal to balance in ClosingSig."
                            + "This, currently could happen in the Out-Channel but not in the In-Channel.");

                    System.out.println("Regenerating ClosingHashFromSender for balance: " + balanceProofSig.right);
                }

                closingSig = meshManager.getTransactionManager()
                        .calculateNewClosingHashFromSender(balanceProofSig.right, remotePeerMeshId.getRawUuid());
            }

            //Double check the balance, should be ok now
            if(balanceProofSig.right.equals(closingSig.right)) {
                if(EtherClient.cooperativeCloseReceiver(ownMeshId, remotePeerAddress, closingSig.right,
                       balanceProofSig.left, closingSig.left, httpAgent)) {
                    meshManager.getTransactionManager().removeMostRecentBillFromSender(remotePeerMeshId);
                    System.out.println("In-Channel has been closed: " + remotePeerAddress + " --> " + ownMeshId);
                } else {
                    System.out.println("Failed to close In-Channel: " + remotePeerAddress + " --> " + ownMeshId);
                }
            } else {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Fatal Error: In-Channel balance is not equal after regenerating a new ClosingSig.");
                }
            }
        }

        //Check for Out-Channel
        bill = null;
        try {
            bill = meshManager.getTransactionManager().getMostRecentBillToReceiver(remotePeerMeshId.getRawUuid());
        } catch (RightMeshException e){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get the most recent bill to receiver, RightMeshException: "
                        + e.getMessage());
            }
        }

        if(bill != null) {
            //Out-Channel exist, lets try to close it.
            System.out.println( ownMeshId + " --> " + remotePeerAddress
                    + " bill to receiver found, trying to close this channel...");

            ImmutablePair<byte[], BigInteger> balanceProofSig = bill.getLeft();
            ImmutablePair<byte[], BigInteger> closingSig = bill.getRight();

            //validate the balance in both pairs (balanceProofSig and closingSig)
            //This situation could happen in the Out-Channel,
            // as we are waiting for the CloseSig to arrive from the remote peer.
            if(!balanceProofSig.right.equals(closingSig.right)) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Error closing Out-Channel: "
                            + "balance in BalanceProofSig: "+balanceProofSig.right+" not equal to balance in ClosingSig: "+closingSig.right
                            + ". This, currently could happen in the Out-Channel.");

                    System.out.println("Regenerating BalanceProof for balance: " + balanceProofSig.right);
                }

                balanceProofSig = meshManager.getTransactionManager()
                        .calculateNewBalanceProofToReceiver(closingSig.right, remotePeerMeshId.getRawUuid());
            }

            //Double check the balance, should be ok now
            if(balanceProofSig.right.equals(closingSig.right)) {
                if(EtherClient.cooperativeCloseSender(ownMeshId, remotePeerAddress, closingSig.right,
                        balanceProofSig.left, closingSig.left, httpAgent)) {
                    meshManager.getTransactionManager().removeMostRecentBillToReceiver(remotePeerMeshId);
                    System.out.println("Out-Channel has been closed: " + ownMeshId + " --> " + remotePeerAddress);
                } else {
                    System.out.println("Failed to close Out-Channel: " + ownMeshId
                            + " --> " + remotePeerAddress);
                }
            } else {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Fatal Error: "
                            + "Out-Channel balance is not equal after regenerating a new BalanceProof.");
                }
            }
        }

        boolean hasInChannel=false;
        boolean hasOutChannel=false;
        try {
            meshManager.getTransactionManager().getMostRecentBillToReceiver(remotePeerMeshId.getRawUuid());
            hasOutChannel=true;
        } catch (RightMeshException e){
            //do nothing
        }
        try {
            meshManager.getTransactionManager().getMostRecentBillFromSender(remotePeerMeshId.getRawUuid());
            hasInChannel=true;
        } catch (RightMeshException e){
            //do nothing
        }
        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(remotePeerMeshId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Ether balance for: " + remotePeerMeshId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get Ether balance.");
            sendTransaction(remotePeerMeshId, data);
            return;
        }

        //Get client Token balance
        String clientTokenBalance;
        try {
            clientTokenBalance = EtherClient.getTokenBalance(remotePeerMeshId.toString(), httpAgent).toString();
        } catch (IOException | NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for: " + remotePeerMeshId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get Token balance.");
            sendTransaction(remotePeerMeshId, data);
            return;
        }

        BigInteger clientNonce = EtherClient.getNonce(remotePeerMeshId.toString(), httpAgent);
        if (clientNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for: " + remotePeerMeshId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL, "Failed to get nonce.");
            sendTransaction(remotePeerMeshId, data);
            return;
        }
        String channelStatusCode=new String(hasOutChannel?"1":"0")+new String(hasInChannel?"1":"0");
        byte[] data=JSON.getMessageToClient(channelStatusCode,clientEtherBalance,clientTokenBalance,clientNonce);
        sendTransaction(remotePeerMeshId,data);

    }


    /**
     * Sends the transaction to Peer.
     * @param destination The Peers address.
     * @param transaction The transaction.
     */
    private void sendTransaction(MeshID destination, byte[] transaction) {
        meshManager.sendDataReliable(destination, MeshUtility.TRANSACTION_PORT, transaction);
    }
}
