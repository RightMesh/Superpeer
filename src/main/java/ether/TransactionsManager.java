package ether;
import io.left.rightmesh.id.MeshID;
import io.left.rightmesh.mesh.MeshManager;
import io.left.rightmesh.proto.MeshTransaction;
import io.left.rightmesh.util.EtherUtility;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
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

    private MeshManager meshManager;
    private LinkedBlockingQueue<MeshManager.MeshTransactionEvent> transactionsQueue = new LinkedBlockingQueue<>();
    private ConcurrentHashMap<String, EtherUtility.PaymentChannel> channelsMap = new ConcurrentHashMap<>();
    private ExecutorService executor = Executors.newFixedThreadPool(NUM_OF_THREADS);
    private Thread queueThread = null;
    private volatile boolean isRunning = false;
    private Http httpAgent;
    private MeshID ownMeshId;


    private static volatile TransactionsManager instance = null;

    public static TransactionsManager getInstance(MeshManager mm) {
        if (instance == null) {
            synchronized(TransactionsManager.class) {
                if (instance == null) {
                    instance = new TransactionsManager(mm);
                }
            }
        }

        return instance;
    }

    private TransactionsManager(MeshManager mm){
        httpAgent = new Http(Settings.RPC_ADDRESS, Settings.DEBUG_INFO);
        meshManager = mm;
        ownMeshId = mm.getUuid();
        mm.on(TRANSACTION_RECEIVED, this::handleTransactionPacket);
    }


    /**
     * Handles Transaction packets from the Mesh network.
     * @param rmEvent
     */
    private void handleTransactionPacket(MeshManager.RightMeshEvent rmEvent) {
        if(Settings.DEBUG_INFO) {
            System.out.println("Transaction received.");
        }

        MeshManager.MeshTransactionEvent event = (MeshManager.MeshTransactionEvent)rmEvent;
        insertTransaction(event);
    }

    /**
     * Returns Running state of the Transaction Manager
     * @return
     */
    public synchronized boolean isRunning(){
        return isRunning;
    }


    /**
     * Starts the Transactions Manager
     */
    public synchronized void start(){
        if(!isRunning){
            queueThread = new Thread(this::processTransactionsQueue);
            queueThread.start();
            isRunning = true;
        }
    }


    /**
     * Stops the Transactions Manager and cleans resources.
     */
    public synchronized void stop(){
        isRunning = false;
        insertStopMessage();

        if(queueThread != null && queueThread.isAlive()) {
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
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("InterruptedException: " + e);
            }
        }
        finally {
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
    private void insertStopMessage(){
        JSONObject stopMessage = new JSONObject();
        stopMessage.put("method", "stop");
        MeshTransaction dummyStopTransaction = new MeshTransaction(stopMessage.toJSONString().getBytes());
        MeshManager.MeshTransactionEvent event = new MeshManager.MeshTransactionEvent(dummyStopTransaction, ownMeshId);
        insertTransaction(event);
    }

    /**
     * Inserts transaction to the transactions queue.
     * @param event
     */
    private void insertTransaction(MeshManager.MeshTransactionEvent event){
        if(!transactionsQueue.offer(event)){
            if(Settings.DEBUG_INFO){
                System.out.println("Failed to add transaction to the queue. Probably the queue is full.");
                System.out.println("Num of elements in the queue: " + transactionsQueue.size());
            }
        }
    }

    /**
     * Processes Transactions queue in a separate managed Thread.
     */
    private void processTransactionsQueue(){
        while(isRunning){
            MeshManager.MeshTransactionEvent event;
            try {
                event = transactionsQueue.take();
            } catch (InterruptedException e) {
                if(Settings.DEBUG_INFO){
                    System.out.println("InterruptedException: "+e);
                }
                continue;
            }

            //execute on a separate Thread
            executor.execute(() -> processTransaction(event));
        }
    }


    /**
     * Process the Mesh network trnasctions from Clients-Remote Peers.
     * @param event
     */
    private void processTransaction(MeshManager.MeshTransactionEvent event){
        MeshTransaction transaction = event.transaction;
        byte[] transactionData = new byte[0]; //TODO: transaction.data;

        JSONObject jsonObject;
        JSONParser parser = new JSONParser();
        try {
            jsonObject = (JSONObject) parser.parse(new String(transactionData));
        } catch (ParseException e) {
            if(Settings.DEBUG_INFO){
                System.out.println("Failed to parse transaction, ParseException: "+e);
            }
            return;
        }

        String method = (String)jsonObject.get("method");
        switch(method) {
            case "stop":
                //Dummy packet - Do nothing
                break;
            case EtherUtility.METHOD_GET_ALL:
                processGetAllRequest(event.peerUuid);
                break;

            case EtherUtility.METHOD_OPEN_CLIENT_TO_SUPER_PEER:
                processOpenClientToSuperPeerRequest(event.peerUuid, jsonObject);
                break;

            case EtherUtility.METHOD_BALANCE_MSG_SIG:
                processBalanceMessageSignatureRequest(event.peerUuid, jsonObject);
                break;

            default:
                if(Settings.DEBUG_INFO){
                    System.out.println("default case in processTransaction method.");
                }
                break;
        }
    }


    /**
     * Processes the Get All request from Client-Remote Peer
     * @param sourceId
     */
    private void processGetAllRequest(MeshID sourceId) {

        //Calculate SuperPeer-->Client channel hash
        byte[] superPeerToClientHash = EtherUtility.getChannelHash(ownMeshId.toString(), sourceId.toString());
        if(superPeerToClientHash == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to calculate superPeerToClientChannel hash for: "+ownMeshId+"-->"+sourceId);
            }
            JSON.sendErrorResponse("Failed to calculate SuperPeer-->Client Hash.", sourceId, meshManager);
            return;
        }

        String superPeerToClientHashStr = new String(Hex.encodeHex(superPeerToClientHash));
        //Check if SuperPeer-->Client channel already exists in the Map.
        EtherUtility.PaymentChannel superPeerToClientChannel = channelsMap.get(superPeerToClientHashStr);

        //If SuperPeer-Client channel doesn't exist in the map, check if exists in the Ether network.
        if(superPeerToClientChannel == null) {
            try {
                superPeerToClientChannel = EtherClient.getChannelInfo(ownMeshId.toString(), sourceId.toString(), httpAgent);
            } catch (DecoderException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Failed to decode the getChannelInfo request. DecoderException: " + e);
                }
                JSON.sendErrorResponse("Failed to decode the getChannelInfo request.", sourceId, meshManager);
                return;
            } catch (IOException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Failed to decode the getChannelInfo request. IOException: " + e);
                }
                JSON.sendErrorResponse("Failed to decode the getChannelInfo request.", sourceId, meshManager);
                return;
            }

            //SuperPeer-->Client channel doesn't exist in the Ether network, lets try to open it.
            if (superPeerToClientChannel == null) {

                //Try to get nonce of the SuperPeer
                BigInteger nonce = EtherClient.getNonce(ownMeshId.toString(), httpAgent);
                if (nonce == null) {
                    if (Settings.DEBUG_INFO) {
                        System.out.println("Failed to get nonce for address: " + ownMeshId);
                    }
                    JSON.sendErrorResponse("Failed to get nonce of the SuperPeer.", sourceId, meshManager);
                    return;
                }

                //Approve Channel contract to spend SuperPeer tokens
                String signedApproveTrasaction = EtherUtility.getSignedApproveTrans(ownMeshId, Settings.MAX_DEPOSIT, nonce,
                        Settings.TOKEN_ABI, Settings.GAS_PRICE, Settings.GAS_LIMIT, Settings.CHANNEL_ADDRESS,
                        Settings.TOKEN_ADDRESS, Settings.CHAIN_ID);

                if(signedApproveTrasaction == null){
                    if (Settings.DEBUG_INFO) {
                        System.out.println("Failed to construct SuperPeer approve transaction.");
                    }
                    JSON.sendErrorResponse("Failed to construct SuperPeer approve transaction.", sourceId, meshManager);
                    return;
                }

                boolean res = false;
                try {
                    res = EtherClient.approve(ownMeshId.toString(), Settings.MAX_DEPOSIT, signedApproveTrasaction, httpAgent);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if(!res){
                    if (Settings.DEBUG_INFO) {
                        System.out.println("Failed to submit SuperPeer approve transaction.");
                    }
                    JSON.sendErrorResponse("Failed to submit SuperPeer approve transaction.", sourceId, meshManager);
                    return;
                }

                if (Settings.DEBUG_INFO) {
                    System.out.println("Trying to open " + ownMeshId + "-->" + sourceId.toString() + " channel.");
                }



                //Create and sign open SuperPeer-->Client channel transaction by SuperPeer
                String signedOpenChannelTrans = EtherUtility.getSignedOpenChannelTrans(ownMeshId, sourceId.toString(), Settings.MAX_DEPOSIT, nonce, Settings.CHANNEL_ABI,
                        Settings.GAS_PRICE, Settings.GAS_LIMIT, Settings.CHANNEL_ADDRESS, Settings.CHAIN_ID);
                if (signedOpenChannelTrans == null || signedOpenChannelTrans == "") {
                    if (Settings.DEBUG_INFO) {
                        System.out.println("Failed to get signedOpenChannelTrans for address: " + ownMeshId);
                    }
                    JSON.sendErrorResponse("Failed to create and sign the Open channel transaction from the SuperPeer.", sourceId, meshManager);
                    return;
                }

                //Send the signed transaction to the Ether network, in order to create the SuperPeer-->Client channel
                try {
                    superPeerToClientChannel = EtherClient.openChannel(ownMeshId.toString(), sourceId.toString(), Settings.MAX_DEPOSIT, signedOpenChannelTrans, httpAgent);
                } catch (IOException e) {
                    if (Settings.DEBUG_INFO) {
                        System.out.println("Failed to open channel " + ownMeshId + "-->" + sourceId + ", due to bad http request.");
                    }
                    JSON.sendErrorResponse("Failed to open SuperPeer-->Client channel in the Ether network.", sourceId, meshManager);
                    return;
                } catch (IllegalArgumentException e) {
                    if (Settings.DEBUG_INFO) {
                        System.out.println("Failed to open channel " + ownMeshId + "-->" + sourceId + ", due to illegal arguments.");
                    }
                    JSON.sendErrorResponse("Failed to open SuperPeer-->Client channel in the Ether network.", sourceId, meshManager);
                    return;
                }

                //Failed to open the SuperPeer-->Client channel in the Ether network, has no more ideas
                if (superPeerToClientChannel == null) {
                    if (Settings.DEBUG_INFO) {
                        System.out.println("Failed to open channel " + ownMeshId + "-->" + sourceId);
                    }
                    JSON.sendErrorResponse("Failed to open SuperPeer-->Client channel in the Ether network.", sourceId, meshManager);
                    return;
                }

                //Update channelsMap
                channelsMap.put(superPeerToClientHashStr, superPeerToClientChannel);
            }
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("SuperPeer-->Client: "+superPeerToClientChannel);
        }


        //Calculate Client-->SuperPeer hash
        byte[] clientToSuperPeerHash = EtherUtility.getChannelHash(sourceId.toString(), ownMeshId.toString());
        if(clientToSuperPeerHash == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to calculate channel hash for: "+ownMeshId+"-->"+sourceId);
            }
            JSON.sendErrorResponse("Failed to calculate Client-->SuperPeer channel hash.", sourceId, meshManager);
            return;
        }

        //Check if Client-->SuperPeer channel already exists in the Map.
        EtherUtility.PaymentChannel clientToSuperPeerChannel = channelsMap.get(clientToSuperPeerHash);

        //If Client-->SuperPeer channel doesn't exist in the map, check if exists in the Ether network.
        if(clientToSuperPeerChannel == null) {
            try {
                clientToSuperPeerChannel = EtherClient.getChannelInfo(sourceId.toString(), ownMeshId.toString(), httpAgent);
            } catch (DecoderException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Failed to decode the getChannelInfo request. DecoderException: " + e);
                }
                JSON.sendErrorResponse("Failed to decode the getChannelInfo request.", sourceId, meshManager);
                return;
            } catch (IOException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Failed to decode the getChannelInfo request. IOException: " + e);
                }
                JSON.sendErrorResponse("Failed to decode the getChannelInfo request.", sourceId, meshManager);
                return;
            }
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("Client-->SuperPeer: " + clientToSuperPeerChannel == null ? "null" : clientToSuperPeerChannel);
        }

        //Get client EtherBalance
        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent);
        }catch (IOException e){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Ether balance for: " + sourceId);
            }
            JSON.sendErrorResponse("Failed to get Ether balance.", sourceId, meshManager);
            return;
        }

        //Get client Token balance
        String clientTokenBalance;
        try {
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent);
        }catch (IOException e){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for: " + sourceId);
            }
            JSON.sendErrorResponse("Failed to get Token balance.", sourceId, meshManager);
            return;
        }

        BigInteger clientNonce = EtherClient.getNonce(sourceId.toString(), httpAgent);
        if(clientNonce == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for: " + sourceId);
            }
            JSON.sendErrorResponse("Failed to get nonce.", sourceId, meshManager);
            return;
        }

        JSON.sendResponse(superPeerToClientChannel, clientToSuperPeerChannel, clientEtherBalance, clientTokenBalance, clientNonce, sourceId, meshManager);
    }


    /**
     * Processes Open Client request from a Client-Remote Peer
     * @param sourceId
     * @param jsonObject
     */
    private void processOpenClientToSuperPeerRequest(MeshID sourceId, JSONObject jsonObject){

        Object signedApproveTransaction = jsonObject.get("signedApproveTransaction");
        if(signedApproveTransaction == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("No signed approve transaction in the open channel request from client.");
            }
            JSON.sendErrorResponse("No signed approve transaction in the open channel request from client.", sourceId, meshManager);
            return;
        }


        Object signedOpenChannelTransaction = jsonObject.get("signedOpenChannelTransaction");
        if(signedOpenChannelTransaction == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("No signed transaction in the open channel request from client.");
            }
            JSON.sendErrorResponse("No signed transaction in the open channel request from client.", sourceId, meshManager);
            return;
        }


        //Calculate Client-->SuperPeer hash
        byte[] clientToSuperPeerHash = EtherUtility.getChannelHash(sourceId.toString(), ownMeshId.toString());
        if(clientToSuperPeerHash == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to calculate channel hash for: "+ownMeshId+"-->"+sourceId);
            }
            JSON.sendErrorResponse("Failed to calculate Client-->SuperPeer channel hash.", sourceId, meshManager);
            return;
        }

        String clientToSuperPeerHashStr = new String(Hex.encodeHex(clientToSuperPeerHash));

        //Check if already exists in the channels map.
        if(channelsMap.containsKey(clientToSuperPeerHashStr)) {
            JSON.sendErrorResponse("Client-->SuperPeer channel already exists in the channels map.", sourceId, meshManager);
            return;
        }

        //Check if already exists in the Ether network.
        EtherUtility.PaymentChannel clientToSuperPeerChannel = null;
        try {
            clientToSuperPeerChannel = EtherClient.getChannelInfo(sourceId.toString(), ownMeshId.toString(), httpAgent);
        } catch (DecoderException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to decode the getChannelInfo request. DecoderException: " + e);
            }
            JSON.sendErrorResponse("Failed to decode the getChannelInfo request.", sourceId, meshManager);
            return;
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to decode the getChannelInfo request. IOException: " + e);
            }
            JSON.sendErrorResponse("Failed to decode the getChannelInfo request.", sourceId, meshManager);
            return;
        }

        //If channel already exists in the Ether network
        if(clientToSuperPeerChannel != null) {
            JSON.sendErrorResponse("Client-->SuperPeer channel already exists in the Ether network.", sourceId, meshManager);
            return;
        }


        //Open Client-->SuperPeer channel
        if (Settings.DEBUG_INFO) {
            System.out.println("Trying to open " + ownMeshId + "-->" + sourceId.toString() + " superPeerToClientChannel.");
        }


        //Approve Channel Contract to spend Client tokens
        boolean res = false;
        try {
            EtherClient.approve(sourceId.toString(), Settings.MAX_DEPOSIT, signedApproveTransaction.toString(), httpAgent);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to submit approve transaction.");
            }
            JSON.sendErrorResponse("Failed to submit approve transaction.", sourceId, meshManager);
            return;
        }

        if(!res){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to submit approve transaction.");
            }
            JSON.sendErrorResponse("Failed to submit approve transaction.", sourceId, meshManager);
            return;
        }


        //Send the signed transaction to the Ether network, in order to create the Client-->SuperPeer channel
        try {
            clientToSuperPeerChannel = EtherClient.openChannel(sourceId.toString(), ownMeshId.toString(), Settings.MAX_DEPOSIT, signedOpenChannelTransaction.toString(), httpAgent);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to open channel " + ownMeshId + "-->" + sourceId + ", due to bad http request.");
            }
            JSON.sendErrorResponse("Failed to open Client-->SuperPeer channel in the Ether network.", sourceId, meshManager);
            return;
        } catch (IllegalArgumentException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to open channel " + ownMeshId + "-->" + sourceId + ", due to illegal arguments.");
            }
            JSON.sendErrorResponse("Failed to open Client-->SuperPeer channel in the Ether network.", sourceId, meshManager);
            return;
        }

        //If failed to open the Client-->SuperPeer channel in the Ether network
        if (clientToSuperPeerChannel == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to open channel " + ownMeshId + "-->" + sourceId);
            }
            JSON.sendErrorResponse("Failed to open Client-->SuperPeer channel in the Ether network.", sourceId, meshManager);
            return;
        }

        //Update channelsMap
        channelsMap.put(clientToSuperPeerHashStr, clientToSuperPeerChannel);

        //Get client EtherBalance
        String clientEtherBalance;
        try {
            clientEtherBalance = EtherClient.getEtherBalance(sourceId.toString(), httpAgent);
        }catch (IOException e){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Ether balance for: " + sourceId);
            }
            JSON.sendErrorResponse("Failed to get Ether balance.", sourceId, meshManager);
            return;
        }

        //Get client Token balance
        String clientTokenBalance;
        try {
            clientTokenBalance = EtherClient.getTokenBalance(sourceId.toString(), httpAgent);
        }catch (IOException e){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for: " + sourceId);
            }
            JSON.sendErrorResponse("Failed to get Token balance.", sourceId, meshManager);
            return;
        }

        BigInteger clientNonce = EtherClient.getNonce(sourceId.toString(), httpAgent);
        if(clientNonce == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for: " + sourceId);
            }
            JSON.sendErrorResponse("Failed to get nonce.", sourceId, meshManager);
            return;
        }

        JSON.sendResponse(clientToSuperPeerChannel, clientEtherBalance, clientTokenBalance, clientNonce, sourceId, meshManager);
    }


    /**
     * Processes the Balance Message Signature from Client-Remote Peer.
     * @param sourceId
     * @param jsonObject
     */
    private void processBalanceMessageSignatureRequest(MeshID sourceId, JSONObject jsonObject){

        //Calculate Client-->SuperPeer hash
        byte[] clientToSuperPeerHash = EtherUtility.getChannelHash(sourceId.toString(), ownMeshId.toString());
        if(clientToSuperPeerHash == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to calculate channel hash for: "+ownMeshId+"-->"+sourceId);
            }
            JSON.sendErrorResponse("Failed to calculate Client-->SuperPeer channel hash.", sourceId, meshManager);
            return;
        }

        String clientToSuperPeerHashStr = new String(Hex.encodeHex(clientToSuperPeerHash));
        //Get the Client-->SuperPeer channel object
        EtherUtility.PaymentChannel clientToSuperPeerChannel = channelsMap.get(clientToSuperPeerHashStr);

        //If channel doesn't exist in the channels map, check the Ether network
        if(clientToSuperPeerChannel == null) {
            try {
                clientToSuperPeerChannel = EtherClient.getChannelInfo(sourceId.toString(), ownMeshId.toString(), httpAgent);
            } catch (DecoderException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Failed to decode the getChannelInfo request. DecoderException: " + e);
                }
                JSON.sendErrorResponse("Failed to decode the getChannelInfo request.", sourceId, meshManager);
                return;
            } catch (IOException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Failed to decode the getChannelInfo request. IOException: " + e);
                }
                JSON.sendErrorResponse("Failed to decode the getChannelInfo request.", sourceId, meshManager);
                return;
            }

            //If doesn't exist in the Ether network, send error response
            if (clientToSuperPeerChannel == null) {
                JSON.sendErrorResponse("Client-->SuperPeer channel already exists in the Ether network.", sourceId, meshManager);
                return;
            }

            //else update the channels map
            channelsMap.put(clientToSuperPeerHashStr, clientToSuperPeerChannel);
        }

        //Parse the json request data
        String lastRecvBalanceMsgSig = jsonObject.get("lastRecvBalanceMsgSig").toString();
        if(lastRecvBalanceMsgSig == null || lastRecvBalanceMsgSig == ""){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to decode lastRecvBalanceMsgSig from json request from client: "+sourceId);
            }
            JSON.sendErrorResponse("Failed to decode lastRecvBalanceMsgSig", sourceId, meshManager);
            return;
        }


        String recvBalanceStr = jsonObject.get("recvBalance").toString();
        if(recvBalanceStr == null || recvBalanceStr == ""){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to decode recvBalance from json request from client: "+sourceId);
            }
            JSON.sendErrorResponse("Failed to decode recvBalance", sourceId, meshManager);
            return;
        }

        BigInteger recvBalance = new BigInteger(recvBalanceStr, 10); //TODO: check which base - 10 or 16

        clientToSuperPeerChannel.lastRecvBalanceMsgSig = lastRecvBalanceMsgSig;
        clientToSuperPeerChannel.recvBalance = recvBalance;

        JSON.sendOkResponse(sourceId, meshManager);
    }

}
