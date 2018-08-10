package ether;

import io.left.rightmesh.util.EtherUtility;
import io.reactivex.internal.util.BlockingIgnoringReceiver;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.simple.JSONObject;
import java.math.BigInteger;

/**
 * The JSON wrapper
 */
public final class JSON {

    //private C'tor to prevent initialization
    private JSON() {
    }

    /**
     * Gets the OK response to client.
     *
     * @param resMethod   The response method.
     * @return            The byte array.
     */
    public static byte[] getOkResponse(String resMethod) {
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        response.put("resMethod", resMethod);

        return response.toJSONString().getBytes();
    }


    /**
     * Sends ERROR response to client.
     *
     * @param resMethod   The response method
     * @return            The byte array.
     */
    public static byte[] getErrorResponse(String resMethod, String message) {
        JSONObject response = new JSONObject();
        response.put("status", "error");
        response.put("method", resMethod);
        response.put("message", message);

        return response.toJSONString().getBytes();
    }

    /**
     * Sends message to client.
     *
     * @param channelStatusCode the two digits binary code represents if there is a channel from superpeer to client
     *                          (1, if yes; 0, otherwise) and if there is a channel from client to superpeer
     *                          (1, if yes; 0, otherwise).
     * @param etherBalance             The Ether balance of the Client.
     * @param tokenBalance             The Tokens balance of the Client.
     * @param nonce                    The nonce of the Client.
     * @return        The byte array.
     */
    public static byte[] getMessageToClient(String channelStatusCode,
                                            String etherBalance,
                                            String tokenBalance,
                                            BigInteger nonce) {
        JSONObject response = new JSONObject();
        response.put("resMethod", EtherUtility.MESSAGE_TO_CLIENT);
        response.put("channelCode",channelStatusCode);
        response.put("etherBalance", etherBalance);
        response.put("tokenBalance", tokenBalance);
        response.put("nonce", nonce.toString());

        return response.toJSONString().getBytes();
    }

    /**
     * Sends response to getAll request.
     *
     * @param superPeerToClientChannel The SuperPeer-->Client channel info.
     * @param clientToSuperPeerChannel The Client-->SuperPeer channel info.
     * @param etherBalance             The Ether balance of the Client.
     * @param tokenBalance             The Tokens balance of the Client.
     * @param nonce                    The nonce of the Client.
     * @return                         The byte array.
     */
    public static byte[] sendGetAllResponse(EtherUtility.PaymentChannel superPeerToClientChannel,
                                          EtherUtility.PaymentChannel clientToSuperPeerChannel,
                                          String etherBalance, String tokenBalance, BigInteger nonce) {

        JSONObject spToClient = new JSONObject();
        spToClient.put("sender", superPeerToClientChannel.senderAddress);
        spToClient.put("recv", superPeerToClientChannel.receiverAddress);
        spToClient.put("initDeposit", superPeerToClientChannel.initDeposit.toString());
        spToClient.put("openBlockNum", superPeerToClientChannel.openBlockNum.toString());
        spToClient.put("recvBalance", superPeerToClientChannel.receiverBalance.toString());
        spToClient.put("lastRecvBalanceMsgSig", superPeerToClientChannel.lastRecvBalanceMsgSig);

        JSONObject clientToSp = new JSONObject();
        if (clientToSuperPeerChannel != null) {
            clientToSp.put("sender", clientToSuperPeerChannel.senderAddress);
            clientToSp.put("recv", clientToSuperPeerChannel.receiverAddress);
            clientToSp.put("initDeposit", clientToSuperPeerChannel.initDeposit.toString());
            clientToSp.put("openBlockNum", clientToSuperPeerChannel.openBlockNum.toString());
            clientToSp.put("recvBalance", clientToSuperPeerChannel.receiverBalance.toString());
            clientToSp.put("lastRecvBalanceMsgSig", clientToSuperPeerChannel.lastRecvBalanceMsgSig);
        }

        JSONObject response = new JSONObject();
        response.put("method", EtherUtility.RES_GET_ALL);
        response.put("status", "ok");
        response.put("etherBalance", etherBalance);
        response.put("tokenBalance", tokenBalance);
        response.put("nonce", nonce.toString());
        response.put("spToClient", spToClient);
        response.put("clientToSp", clientToSuperPeerChannel == null ? "none" : clientToSp);

        return response.toJSONString().getBytes();
    }


    /**
     * Sends response to Open channel request.
     *
     * @param clientToSuperPeerChannel The Client-->SuperPeer channel info.
     * @param etherBalance             The Ether balance of the Client.
     * @param tokenBalance             The Tokens balance of the Client.
     * @param nonce                    The nonce of the Client.
     * @return                         The byte array.
     */
    public static byte[] sendOpenClientToSpResponse(EtherUtility.PaymentChannel clientToSuperPeerChannel,
                                                  String etherBalance, String tokenBalance, BigInteger nonce) {

        JSONObject clientToSp = new JSONObject();
        clientToSp.put("sender", clientToSuperPeerChannel.senderAddress);
        clientToSp.put("recv", clientToSuperPeerChannel.receiverAddress);
        clientToSp.put("initDeposit", clientToSuperPeerChannel.initDeposit.toString());
        clientToSp.put("openBlockNum", clientToSuperPeerChannel.openBlockNum.toString());
        clientToSp.put("recvBalance", clientToSuperPeerChannel.receiverBalance.toString());
        clientToSp.put("lastreceiverBalanceMsgSig", clientToSuperPeerChannel.lastRecvBalanceMsgSig);

        JSONObject response = new JSONObject();
        response.put("method", EtherUtility.RES_OPEN_CLIENT_TO_SUPER_PEER);
        response.put("status", "ok");
        response.put("etherBalance", etherBalance);
        response.put("tokenBalance", tokenBalance);
        response.put("nonce", nonce.toString());
        response.put("clientToSp", clientToSp);

        return response.toJSONString().getBytes();
    }


    /**
     * Sends the Balance Message Signature,
     * @param newBalance        The new balance.
     * @param balanceMsgSig     The signature.
     * @return                  The byte array.
     */
    public static byte[] sendBalanceMsgSig(BigInteger newBalance, String balanceMsgSig) {
        JSONObject msg = new JSONObject();
        msg.put("method", EtherUtility.METHOD_BALANCE_MSG_SIG);
        msg.put("status", "ok");
        msg.put("newBalance", newBalance.toString());
        msg.put("balanceMsgSig", balanceMsgSig);

        return msg.toJSONString().getBytes();
    }

    public static byte[] sendActiveUpdateResponse(ImmutablePair<byte[], BigInteger> closingHashPair,
                                                  String clientEtherBalance, String clientTokenBalance,
                                                  BigInteger clientNonce){
        JSONObject response = new JSONObject();
        response.put("method", EtherUtility.RES_ACTIVE_UPDATE);
        response.put("status", "ok");
        response.put("etherBalance", clientEtherBalance);
        response.put("tokenBalance", clientTokenBalance);
        response.put("nonce", clientNonce.toString());
        if(closingHashPair!=null){
            response.put("closingHashBalance", closingHashPair.right.toString());
            response.put("closingHashSignature", Hex.encodeHexString(closingHashPair.left));
        }

        return response.toJSONString().getBytes();
    }

    public static byte[] sendCloseClientToSuperResponse(String clientEtherBalance, String clientTokenBalance,
                                                        BigInteger clientNonce){
        JSONObject response = new JSONObject();
        response.put("method", EtherUtility.RES_CLOSE_CHANNEL_TO_SUPER_PEER);
        response.put("status", "ok");
        response.put("etherBalance", clientEtherBalance);
        response.put("tokenBalance", clientTokenBalance);
        response.put("nonce", clientNonce.toString());

        return response.toJSONString().getBytes();
    }

    public static byte[] sendCloseSuperToClientResponse(String clientEtherBalance, String clientTokenBalance,
                                                        BigInteger clientNonce){
        JSONObject response = new JSONObject();
        response.put("method", EtherUtility.RES_CLOSE_CHANNEL_FROM_SUPER_PEER);
        response.put("status", "ok");
        response.put("etherBalance", clientEtherBalance);
        response.put("tokenBalance", clientTokenBalance);
        response.put("nonce", clientNonce.toString());

        return response.toJSONString().getBytes();
    }
}
