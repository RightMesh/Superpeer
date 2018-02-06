package ether;

import io.left.rightmesh.id.MeshID;
import io.left.rightmesh.mesh.MeshManager;
import io.left.rightmesh.proto.MeshTransaction;
import io.left.rightmesh.util.EtherUtility;
import io.left.rightmesh.util.MeshUtility;
import io.left.rightmesh.util.RightMeshException;
import org.apache.commons.codec.DecoderException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.*;
import static io.left.rightmesh.mesh.MeshManager.TRANSACTION_RECEIVED;


/**
 * The Transactions manager, responsible to process incoming Ether Transactions from Clients in the Mesh network.
 */
public final class TransactionsManager {

    private static final int NUM_OF_THREADS = 100;
    private static final int WAIT_TERMINATION_TIMEOUT = 5; //sec

    private MeshManager meshManager;
    private BlockingQueue<MeshManager.MeshTransactionEvent> transactionsQueue = new LinkedBlockingQueue<>();
    private ConcurrentHashMap<String, EtherUtility.PaymentChannel> channelsMap = new ConcurrentHashMap<>();
    private ExecutorService executor = Executors.newFixedThreadPool(NUM_OF_THREADS);
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
        meshManager = mm;
        ownMeshId = mm.getUuid();
        mm.on(TRANSACTION_RECEIVED, this::handleTransactionPacket);
    }


    /**
     * Handles Transaction packets from the Mesh network.
     *
     * @param rmEvent
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
     * @return
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

        try {
            executor.shutdown();
            executor.awaitTermination(WAIT_TERMINATION_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("InterruptedException: " + e);
            }
        } finally {
            if (!executor.isTerminated()) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Cancel all unfinished tasks.");
                }
            }
            executor.shutdownNow();
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
     * @param event
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

            //execute on a separate Thread
            executor.execute(() -> processTransaction(event));
        }
    }


    /**
     * Process the Mesh network trnasctions from Clients-Remote Peers.
     *
     * @param event
     */
    private void processTransaction(MeshManager.MeshTransactionEvent event) {
        MeshTransaction transaction = event.transaction;
        byte[] transactionData = new byte[0]; //TODO: transaction.data;

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

            case EtherUtility.METHOD_BALANCE_MSG_SIG:
                processBalanceMessageSignatureRequest(event.peerUuid, jsonObject);
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
     * @param sourceId
     */
    private void processGetAllRequest(MeshID sourceId) {

        //Check if SuperPeer-->Client channel already exists in the Map.
        EtherUtility.PaymentChannel outChannel = getChannelFromMap(ownMeshId.toString(), sourceId.toString());

        if (outChannel == null) {

            //If SuperPeer-->Client channel doesn't exist in the map, check if exists in the Ether network.
            outChannel = getChannelFromEtherNetwork(ownMeshId.toString(), sourceId.toString());
            if(outChannel == null) {

                //If SuperPeer-->Client channel doesn't exist in the Ether network, lets try to open it.
                outChannel = openChannel(ownMeshId, sourceId);
                if (outChannel == null) {
                    if (Settings.DEBUG_INFO) {
                        System.out.println("Fatal error, cannot establish SuperPeer-->Client channel: "
                                + ownMeshId + "-->" + sourceId);
                    }
                    byte[] data = JSON.getErrorResponse(EtherUtility.RES_GET_ALL,
                            "Failed to establish channel: SuperPeer-->Client.");
                    sendTransaction(sourceId, data);
                    return;
                }
            }
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("SuperPeer-->Client: " + outChannel);
        }

        //Check if Client-->SuperPeer channel already exists in the Map.
        EtherUtility.PaymentChannel inChannel = getChannelFromMap(sourceId.toString(), ownMeshId.toString());

        //If Client-->SuperPeer channel doesn't exist in the map, check if exists in the Ether network.
        if (inChannel == null) {
            inChannel = getChannelFromEtherNetwork(sourceId.toString(), ownMeshId.toString());
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("Client-->SuperPeer: " + inChannel == null ? "null" : inChannel);
        }

        //Get client EtherBalance
        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent);
        } catch (IOException e) {
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
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent);
        } catch (IOException e) {
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

        byte[] data = JSON.sendGetAllResponse(outChannel, inChannel, clientEtherBalance, clientTokenBalance, clientNonce);
        sendTransaction(sourceId, data);
    }


    /**
     * tries to get the payment channel from map.
     * @param senderAddress The sender address.
     * @param recvAddress The receiver address.
     * @return The Payment channel if exists in the map, otherwise returns null.
     */
    private EtherUtility.PaymentChannel getChannelFromMap(String senderAddress, String recvAddress){
        //Calculate the payment channel hash
        String superPeerToClientHash = EtherUtility.getChannelHashStr(senderAddress, recvAddress);
        if(superPeerToClientHash == null || superPeerToClientHash.equals("")){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to calculate channel hash for: " + senderAddress + "-->" + recvAddress);
            }
            return null;
        }

        return channelsMap.get(superPeerToClientHash);
    }


    /**
     * Tries to get the payment channel from Ether network.
     * @param senderAddress The sender address.
     * @param recvAddress The receiver address.
     * @return The Payment channel if exists in the Ether network, otherwise returns null.
     */
    private EtherUtility.PaymentChannel getChannelFromEtherNetwork(String senderAddress, String recvAddress) {

        EtherUtility.PaymentChannel channel;
        try {
            channel = EtherClient.getChannelInfo(senderAddress, recvAddress, httpAgent);
        } catch (DecoderException | IOException e) {
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
        String signedApproveTransaction = EtherUtility.getSignedApproveTrans(sender, Settings.MAX_DEPOSIT,
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
            res = EtherClient.approve(senderAddress, Settings.MAX_DEPOSIT, signedApproveTransaction, httpAgent);
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

        //Create and sign open sender-->receiver channel transaction
        String signedOpenChannelTrans = EtherUtility.getSignedOpenChannelTrans(sender, recvAddress,
                Settings.MAX_DEPOSIT, senderNonce, Settings.CHANNEL_ABI, Settings.GAS_PRICE, Settings.GAS_LIMIT,
                Settings.CHANNEL_CONTRACT_ADDRESS, Settings.CHAIN_ID);

        if (signedOpenChannelTrans == null || signedOpenChannelTrans == "") {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get signedOpenChannelTrans for: " + senderAddress + "-->" + recvAddress);
            }
            return null;
        }

        //Send the signed open payment channel transaction to the Ether network.
        try {
            channel = EtherClient.openChannel(senderAddress, recvAddress, Settings.MAX_DEPOSIT,
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

        //Payment channel opened, update channelsMap
        channelsMap.put(channel.channelHash, channel);

        return channel;
    }


    /**
     * Processes Open Client to SuperPeer request from a Client-Remote Peer
     *
     * @param sourceId The MeshId of the remote peer.
     * @param jsonObject The Transaction data.
     */
    private void processOpenInChannelRequest(MeshID sourceId, JSONObject jsonObject) {

        Object signedApproveTransaction = jsonObject.get("signedApproveTransaction");
        if (signedApproveTransaction == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("No signed approve transaction in the open channel request from client.");
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "No signed approve transaction in the open channel request from client.");
            sendTransaction(sourceId, data);
            return;
        }


        Object signedOpenChannelTransaction = jsonObject.get("signedOpenChannelTransaction");
        if (signedOpenChannelTransaction == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("No signed transaction in the open channel request from client.");
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "No signed transaction in the open channel request from client.");
            sendTransaction(sourceId, data);
            return;
        }

        //Check if already exists in the map
        EtherUtility.PaymentChannel inChannel = getChannelFromMap(sourceId.toString(), ownMeshId.toString());
        if(inChannel != null){
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Client-->SuperPeer channel already open.");
            sendTransaction(sourceId, data);
            return;
        }

        //Check if already exists in the Ether network.
        inChannel = getChannelFromEtherNetwork(sourceId.toString(), ownMeshId.toString());
        if (inChannel != null) {
            channelsMap.put(inChannel.channelHash, inChannel);
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Client-->SuperPeer channel already open.");
            sendTransaction(sourceId, data);
            return;
        }

        //Tries to open Client-->SuperPeer channel
        if (Settings.DEBUG_INFO) {
            System.out.println("Trying to open " + ownMeshId + "-->" + sourceId + " channel.");
        }

        inChannel = openChannel(sourceId, ownMeshId);
        if(inChannel == null){
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER,
                    "Failed to open channel Client-->SuperPeer.");
            sendTransaction(sourceId, data);
            return;
        }

        //Update channelsMap
        channelsMap.put(inChannel.channelHash, inChannel);

        //Get client EtherBalance
        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent);
        } catch (IOException e) {
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
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent);
        } catch (IOException e) {
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

        byte[] data = JSON.sendOpenClientToSpResponse(inChannel, clientEtherBalance, clientTokenBalance, clientNonce);
        sendTransaction(sourceId, data);
    }


    /**
     * Processes the Balance Message Signature from Client-Remote Peer.
     *
     * @param sourceId The MeshID of the remote peer, the sender.
     * @param jsonObject The transaction data.
     */
    private void processBalanceMessageSignatureRequest(MeshID sourceId, JSONObject jsonObject) {

        //Get the Client-->SuperPeer channel object
        EtherUtility.PaymentChannel inChannel = getChannelFromMap(sourceId.toString(), ownMeshId.toString());

        if (inChannel == null) {
            //If channel doesn't exist in the map, check in the Ether network
            try {
                inChannel = EtherClient.getChannelInfo(sourceId.toString(), ownMeshId.toString(), httpAgent);
            } catch (DecoderException | IOException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Failed to execute the getChannelInfo request. "
                            + e.getClass().getCanonicalName() + ": "+e.getMessage());
                }
                byte data[] = JSON.getErrorResponse(EtherUtility.RES_Balance_MSG_SIG,
                        "Failed to execute the getChannelInfo request on the Ether network.");
                sendTransaction(sourceId, data);
                return;
            }

            //If doesn't exist in the Ether network, send error response
            if (inChannel == null) {
                byte[] data = JSON.getErrorResponse(EtherUtility.RES_Balance_MSG_SIG,
                        "Client-->SuperPeer channel doesn't exist in the Ether network.");
                sendTransaction(sourceId, data);
                return;
            }

            //else update the channels map
            channelsMap.put(inChannel.channelHash, inChannel);
        }

        //Parse the json request data
        String lastRecvBalanceMsgSig = jsonObject.get("lastRecvBalanceMsgSig").toString();
        if (lastRecvBalanceMsgSig == null || lastRecvBalanceMsgSig == "") {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to decode lastRecvBalanceMsgSig from json request from client: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_Balance_MSG_SIG,
                    "Failed to decode lastRecvBalanceMsgSig.");
            sendTransaction(sourceId, data);
            return;
        }

        //Parse the new receiver balance
        String recvBalanceStr = jsonObject.get("recvBalance").toString();
        if (recvBalanceStr == null || recvBalanceStr == "") {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to decode recvBalance from json request from client: " + sourceId);
            }
            byte[] data = JSON.getErrorResponse(EtherUtility.RES_Balance_MSG_SIG,
                    "Failed to decode recvBalance.");
            sendTransaction(sourceId, data);
            return;
        }

        BigInteger recvBalance = new BigInteger(recvBalanceStr, 10); //TODO: check which base - 10 or 16

        inChannel.lastRecvBalanceMsgSig = lastRecvBalanceMsgSig;
        inChannel.recvBalance = recvBalance;

        byte[] data = JSON.getOkResponse(EtherUtility.RES_Balance_MSG_SIG);

        try {
            meshManager.sendDataReliable(sourceId, MeshUtility.TRANSACTION_PORT, data);
        } catch (RightMeshException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to send response to: " + sourceId);
            }
        }
    }


    /**
     * Send payment to the Peer
     *
     * @param recvAddress Receiver address
     * @param amount
     * @return True on success, otherwise False.
     */
    public boolean sendPayment(String recvAddress, BigInteger amount) {

        EtherUtility.PaymentChannel outChannel = getChannelFromMap(ownMeshId.toString(), recvAddress);
        if(outChannel == null){
            //If channel doesn't exist in the map, try to get it from the Ether network.
            outChannel = getChannelFromEtherNetwork(ownMeshId.toString(), recvAddress);

            if(outChannel == null){
                if (Settings.DEBUG_INFO) {
                    System.out.println("Channel doesn't exists.");
                }
                return false;
            }
        }

        BigInteger newBalance = outChannel.recvBalance.add(amount);
        if (newBalance.compareTo(Settings.MAX_DEPOSIT) > 0) {
            if (Settings.DEBUG_INFO) {
                System.out.println("No more balance in channel available. Please close this channel");
            }
            return false;
        }

        byte[] newBalanceBytes = EtherUtility.getBalanceBytes(newBalance.toString(), Settings.APPENDING_ZEROS_FOR_TOKEN);
        if (newBalance == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to convert new balance to Byte Array.");
            }
            return false;
        }

        byte[] channelAddressBytes = null;
        try {
            channelAddressBytes = EtherUtility.etherAddressToByteArray(Settings.CHANNEL_CONTRACT_ADDRESS);
        } catch (DecoderException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to decode address: " + Settings.CHANNEL_CONTRACT_ADDRESS +
                        ". DecoderException" + e);
            }
        }

        if (channelAddressBytes == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to convert Channel Contract Address to Byte Array.");
            }
            return false;
        }

        byte[] recvAddressBytes;
        try {
            recvAddressBytes = EtherUtility.etherAddressToByteArray(recvAddress);
        } catch (DecoderException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to decode recv address: " + recvAddress + ". DecoderException" + e);
            }
            return false;
        }

        String balanceMsgSig = EtherUtility.getBalanceMsgHashSig(recvAddressBytes, newBalanceBytes,
                channelAddressBytes, ownMeshId);
        if (balanceMsgSig == null || balanceMsgSig == "") {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed calculate BalanceMsgHashSig.");
            }
            return false;
        }

        MeshID recvMeshId;
        try {
            recvMeshId = new MeshID(recvAddress);
        } catch (RightMeshException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to constract MeshId from: " + recvAddress);
            }
            return false;
        }

        byte[] data = JSON.sendBalanceMsgSig(newBalance, balanceMsgSig);
        sendTransaction(recvMeshId, data);

        outChannel.recvBalance = newBalance;
        outChannel.lastRecvBalanceMsgSig = balanceMsgSig;

        return true;
    }


    /**
     * Sends the transaction to Peer.
     * @param destination The Peers address.
     * @param transaction The transaction.
     */
    private void sendTransaction(MeshID destination, byte[] transaction) {
        try {
            meshManager.sendDataReliable(destination, MeshUtility.TRANSACTION_PORT, transaction);
        } catch (RightMeshException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to send transaction to: " + destination);
            }
        }
    }
}
