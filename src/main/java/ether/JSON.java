package ether;

import io.left.rightmesh.id.MeshID;
import io.left.rightmesh.mesh.MeshManager;
import io.left.rightmesh.util.EtherUtility;
import io.left.rightmesh.util.MeshUtility;
import io.left.rightmesh.util.RightMeshException;
import org.json.simple.JSONObject;

import java.math.BigInteger;


/**
 * The JSON wrapper
 */
public final class JSON {

    //private C'tor to prevent initialization
    private JSON(){}


    /**
     * Sends OK response to client.
     * @param destination Clients MeshId in the Mesh network.
     * @param meshManager The Mesh manager.
     */
    public static void sendOkResponse(MeshID destination, MeshManager meshManager){
        JSONObject response = new JSONObject();
        response.put("status","ok");

        try {
            meshManager.sendDataReliable(destination, MeshUtility.TRANSACTION_PORT, response.toJSONString().getBytes());
        } catch (RightMeshException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to send response to: "+destination);
            }
        }
    }


    /**
     * Sends ERROR response to client.
     * @param destination Clients MeshId in the Mesh network.
     * @param meshManager The Mesh manager.
     */
    public static void sendErrorResponse(String message, MeshID destination, MeshManager meshManager){
        JSONObject response = new JSONObject();
        response.put("status","error");
        response.put("message", message);

        try {
            meshManager.sendDataReliable(destination, MeshUtility.TRANSACTION_PORT, response.toJSONString().getBytes());
        } catch (RightMeshException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to send response to: "+destination);
            }
        }
    }


    /**
     * Sends response to getAll request.
     * @param superPeerToClientChannel The SuperPeer-->Client channel info.
     * @param clientToSuperPeerChannel The Client-->SuperPeer channel info.
     * @param etherBalance The Ether balance of the Client.
     * @param tokenBalance The Tokens balance of the Client.
     * @param nonce The nonce of the Client.
     * @param destination The CLient's MeshId in the Mesh network.
     * @param meshManager The Mesh manager.
     */
    public static void sendResponse(EtherUtility.PaymentChannel superPeerToClientChannel,
                                    EtherUtility.PaymentChannel clientToSuperPeerChannel,
                                    String etherBalance, String tokenBalance, BigInteger nonce,
                                    MeshID destination, MeshManager meshManager){

        JSONObject spToClient = new JSONObject();
        spToClient.put("sender", superPeerToClientChannel.senderAddress);
        spToClient.put("recv", superPeerToClientChannel.recvAddress);
        spToClient.put("initDeposit", superPeerToClientChannel.initDeposit.toString());
        spToClient.put("openBlockNum", superPeerToClientChannel.openBlockNum.toString());
        spToClient.put("recvBalance", superPeerToClientChannel.recvBalance.toString());
        spToClient.put("lastRecvBalanceMsgSig", superPeerToClientChannel.lastRecvBalanceMsgSig);

        JSONObject clientToSp = new JSONObject();
        if(clientToSuperPeerChannel != null) {
            clientToSp.put("sender", clientToSuperPeerChannel.senderAddress);
            clientToSp.put("recv", clientToSuperPeerChannel.recvAddress);
            clientToSp.put("initDeposit", clientToSuperPeerChannel.initDeposit.toString());
            clientToSp.put("openBlockNum", clientToSuperPeerChannel.openBlockNum.toString());
            clientToSp.put("recvBalance", clientToSuperPeerChannel.recvBalance.toString());
            clientToSp.put("lastRecvBalanceMsgSig", clientToSuperPeerChannel.lastRecvBalanceMsgSig);
        }

        JSONObject response = new JSONObject();
        response.put("status", "ok");
        response.put("etherBalance", etherBalance);
        response.put("tokenBalance", tokenBalance);
        response.put("nonce", nonce.toString());
        response.put("superPeerToClientChannel", spToClient);
        response.put("clientToSuperPeerChannel", clientToSuperPeerChannel == null ? "none" : clientToSp);

        try {
            meshManager.sendDataReliable(destination, MeshUtility.TRANSACTION_PORT, response.toJSONString().getBytes());
        } catch (RightMeshException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to send response to: "+destination);
            }
        }
    }

    /**
     * Sends response to Open channel request.
     * @param clientToSuperPeerChannel The Client-->SuperPeer channel info.
     * @param etherBalance The Ether balance of the Client.
     * @param tokenBalance The Tokens balance of the Client.
     * @param nonce The nonce of the Client.
     * @param destination The CLient's MeshId in the Mesh network.
     * @param meshManager The Mesh manager.
     */
    public static void sendResponse(EtherUtility.PaymentChannel clientToSuperPeerChannel,
                                    String etherBalance, String tokenBalance, BigInteger nonce,
                                    MeshID destination, MeshManager meshManager){

        JSONObject clientToSp = new JSONObject();
        clientToSp.put("sender", clientToSuperPeerChannel.senderAddress);
        clientToSp.put("recv", clientToSuperPeerChannel.recvAddress);
        clientToSp.put("initDeposit", clientToSuperPeerChannel.initDeposit.toString());
        clientToSp.put("openBlockNum", clientToSuperPeerChannel.openBlockNum.toString());
        clientToSp.put("recvBalance", clientToSuperPeerChannel.recvBalance.toString());
        clientToSp.put("lastRecvBalanceMsgSig", clientToSuperPeerChannel.lastRecvBalanceMsgSig);

        JSONObject response = new JSONObject();
        response.put("status", "ok");
        response.put("etherBalance", etherBalance);
        response.put("tokenBalance", tokenBalance);
        response.put("nonce", nonce.toString());
        response.put("clientToSuperPeerChannel", clientToSp);

        try {
            meshManager.sendDataReliable(destination, MeshUtility.TRANSACTION_PORT, response.toJSONString().getBytes());
        } catch (RightMeshException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to send response to: "+destination);
            }
        }
    }
}
