package ether;

import io.left.rightmesh.id.MeshID;
import io.left.rightmesh.util.EtherUtility;
import org.apache.commons.codec.binary.Hex;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;


/**
 * A Wrapper static class to communicate with Ether node.
 */
public final class EtherClient {

    //private C'tor to prevent initialization
    private EtherClient() {
    }

    /**
     * Gets the nonce by Ether address.
     *
     * @param address   The address in the Ether network.
     * @param httpAgent Http wrapper
     * @return The nonce
     */
    public static BigInteger getNonce(String address, Http httpAgent) {

        String request = getEtherRequest("parity_nextNonce", address);
        String nonce;
        try {
            nonce = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to query the nonce for: " + address);
            }
            return null;
        }

        if(nonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get response from the Ether node.");
            }
            return null;
        }

        nonce = nonce.substring(2);
        return new BigInteger(nonce, 16);
    }

    /**
     * Get the balance of ether on the Ethereum network.
     *
     * @param address                   The address in the Ethereum network.
     * @param httpAgent                 The Http wrapper.
     * @return                          The Ethereum balance.
     * @throws IOException              Thrown if fails to get http response from a remote mode.
     * @throws NumberFormatException    Thrown if fails to parse the balance.
     */
    public static BigInteger getEtherBalance(String address, Http httpAgent) throws IOException, NumberFormatException {

        String request = getEtherRequest("eth_getBalance", address);
        String weiBalanceStr;
        try {
            weiBalanceStr = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to query the Ether Balance for: " + address);
            }
            throw e;
        }

        if(weiBalanceStr == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get response from the Ether node.");
            }
            return null;
        }

        BigInteger weiBalanceInt = new BigInteger(weiBalanceStr.substring(2), 16);
        return weiBalanceInt;
    }

    /**
     * Get the balance of Tokens on the Ethereum network.
     *
     * @param address                   The address in the Ethereum Network.
     * @param httpAgent                 The Http wrapper.
     * @return                          The Ethereum balance.
     * @throws IOException              Thrown if fails to get http response from a remote mode.
     * @throws NumberFormatException    Thrown if fails to parse the balance.
     */
    public static BigInteger getTokenBalance(String address, Http httpAgent) throws IOException, NumberFormatException {

        CallTransaction.Function func = Settings.TOKEN_CONTRACT.getByName("balanceOf");
        byte[] funcBytes = func.encode(address);
        String funcBytesStr = "0x" + new String(Hex.encodeHex(funcBytes));
        String request = getEtherRequest("eth_call", null, Settings.TOKEN_CONTRACT_ADDRESS,
                null, funcBytesStr);

        if (Settings.DEBUG_INFO) {
            System.out.println("Request in getTokenBalance = " + request);
        }

        String weiBalanceStr;
        try {
            weiBalanceStr = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            throw e;
        }

        if(weiBalanceStr == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get Token balance for address: " + address);
            }
            return null;
        }

        BigInteger weiBalanceInt = new BigInteger(weiBalanceStr.substring(2), 16);
        return weiBalanceInt;
    }

    /**
     * Gets the payment channel info by Sender and Receiver addresses.
     *
     * @param senderAddress         The sender address in the Ethereum Network
     * @param receiverAddress       The receiver address in the Ethereum Network
     * @param httpAgent             Http wrapper
     * @return                      PaymentChannel object that holds all channel info.
     * @throws IOException          Thrown if fails to get http response from a remote mode.
     */
    public static EtherUtility.PaymentChannel getChannelInfo(String senderAddress, String receiverAddress
            , Http httpAgent) throws IOException {

        byte[] keyInBytes = EtherUtility.getChannelHash(senderAddress, receiverAddress);
        if (keyInBytes == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to construct the channel Hash: " + senderAddress + "-->" + receiverAddress);
            }
            return null;
        }

        CallTransaction.Function func = Settings.CHANNEL_CONTRACT.getByName("channels");
        byte[] funcBytes = func.encode(keyInBytes);
        String functionBytesStr = "0x" + new String(Hex.encodeHex(funcBytes));
        String request = getEtherRequest("eth_call", null,
                Settings.CHANNEL_CONTRACT_ADDRESS, null, functionBytesStr);

        if (Settings.DEBUG_INFO) {
            System.out.println("Request in getChannelInfo = " + request);
        }

        String response;
        try {
            response = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Cannot checkChannelAvailable from " + senderAddress + " to " + receiverAddress);
            }
            throw e;
        }

        if (response == null || response == "") {
            return null;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("checkChannelAvailable response: " + response);
        }

        response = response.substring(2);
        BigInteger res = new BigInteger(response, 16);
        if (res.equals(BigInteger.ZERO)) {
            return null;
        }

        //The response contains the Channel struct, struct is returned as an array of bytes
        //Channel struct contains 2 members.
        String depositStr = response.substring(0, response.length() / 2);
        String openBlockStr = response.substring(response.length() / 2);

        BigInteger depositInt = new BigInteger(depositStr, 16);
        Integer openBlockInt = Integer.parseInt(openBlockStr, 16);

        return new EtherUtility.PaymentChannel(senderAddress,
                receiverAddress, depositInt, openBlockInt, new BigInteger("0"), "");
    }


    /**
     * Checks if channel exists in the Ether network by Sender and Receiver addresses.
     *
     * @param senderAddress     The sender address in the Ethereum Network
     * @param receiverAddress   The receiver address in the Ethereum Network
     * @param httpAgent         Http wrapper
     * @return                  True if channel exists, otherwise returns False
     * @throws IOException      Thrown if fails to get http response from a remote mode.
     */
    public boolean checkChannelAvailable(String senderAddress, String receiverAddress, Http httpAgent)
            throws IOException {

        byte[] keyInBytes = EtherUtility.getChannelHash(senderAddress, receiverAddress);
        if (keyInBytes == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to construct the channel Hash: " + senderAddress + "-->" + receiverAddress);
            }
            return false;
        }


        CallTransaction.Function func = Settings.CHANNEL_CONTRACT.getByName("channels");
        byte[] funcBytes = func.encode(keyInBytes);
        String funcBytesStr = "0x" + new String(Hex.encodeHex(funcBytes));
        String request = getEtherRequest("eth_call", null,
                Settings.CHANNEL_CONTRACT_ADDRESS, null, funcBytesStr);

        if (Settings.DEBUG_INFO) {
            System.out.println("Request in getTokenBalance = " + request);
        }

        String response;
        try {
            response = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Cannot checkChannelAvailable from " + senderAddress + " to " + receiverAddress);
            }
            throw e;
        }

        if(response == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get response from the Ether node.");
            }
            return false;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("There "
                    + (new BigInteger(response.substring(64), 16).compareTo(BigInteger.ZERO) > 0
                    ? "is" : "isn't") + " a channel from " + senderAddress + " to " + receiverAddress);
        }

        return (new BigInteger(response.substring(64), 16).compareTo(BigInteger.ZERO) > 0);
    }


    /**
     * Approves Payment Channels Contract to spend Tokens on behalf of the Sender.
     *
     * @param senderAddress                 The sender address in the Ethereum Network
     * @param deposit                       The approved deposit.
     * @param signedApproveTrans            The approve transaction, signed by Sender
     * @param httpAgent                     The Http wrapper
     * @return                              True on success, otherwise return False.
     * @throws IOException                  Thrown if fails to get http response from a remote mode.
     * @throws IllegalArgumentException     Thrown if supplied invalid parameter.
     */
    public static boolean approve(String senderAddress, BigInteger deposit, String signedApproveTrans, Http httpAgent)
            throws IOException, IllegalArgumentException {

        if (Settings.DEBUG_INFO) {
            System.out.println(senderAddress + " tries to approve channel "
                    + Settings.CHANNEL_CONTRACT_ADDRESS + " up to " + deposit + " Tokens at maximum.");
        }

        CallTransaction.Function func = Settings.TOKEN_CONTRACT.getByName("approve");
        byte[] funcEncodedData = func.encode(Settings.CHANNEL_CONTRACT_ADDRESS, deposit);
        String funcEncodedDataStr = "0x" + new String(Hex.encodeHex(funcEncodedData));
        String request = getEtherRequest("eth_estimateGas", senderAddress,
                Settings.TOKEN_CONTRACT_ADDRESS, "0x0", funcEncodedDataStr);

        if (Settings.DEBUG_INFO) {
            System.out.println("The request string of queryApproveGasString is " + request);
        }

        String gasEstimateRes;
        try {
            gasEstimateRes = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Invoking function with given arguments is not allowed.");
            }
            throw e;
        }

        if (gasEstimateRes == null || gasEstimateRes == "") {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to estimate GAS for the approve transaction. "
                        + "Probably the spender already approved by the owner.");
            }
            return false;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("The estimatedGas of approve is " + gasEstimateRes + ".");
        }

        gasEstimateRes = gasEstimateRes.substring(2);
        BigInteger estimatedGas = new BigInteger(gasEstimateRes, 16);

        //TODO: need to adjust the Maximum GAS limit.
        if (estimatedGas.compareTo(Settings.GAS_LIMIT) > 0) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Estimeted GAS for approve transaction is too high. "
                        + "Probably some arguments are Invalid.");
            }
            throw new IllegalArgumentException("Estimeted GAS for approve transaction is too high. "
                    + "Probably some arguments are Invalid.");
        }

        request = getEtherRequest("eth_sendRawTransaction", signedApproveTrans);

        System.out.println("Submitting approve Transaction...");

        String transactionId;
        try {
            transactionId = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Fail to execute HTTP request.");
            }
            throw e;
        }

        if (transactionId == null || transactionId.equals("")) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Fail to submit the approve transaction.");
            }
            return false;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("Waiting for network to mine transactions ... ");
        }

        waitingForTransaction(transactionId, httpAgent);

        if (Settings.DEBUG_INFO) {
            System.out.println("\bApproving funding transfer is done.");
        }

        return true;
    }


    /**
     * Opens Payment Channel in Ether network.
     *
     * @param senderAddress                 The sender address in the Ethereum Network.
     * @param receiverAddress               The receiver address in the Ethereum Network.
     * @param deposit                       The initial deposit in the channel.
     * @param signedOpenChannelTrans        The Open Channel transaction, signed by Sender.
     * @param httpAgent                     The Http wrapper.
     * @return                              The PaymentChannel object.
     * @throws IOException                  Thrown if fails to get http response from a remote mode.
     * @throws IllegalArgumentException     Thrown if supplied invalid parameter.
     */
    public static EtherUtility.PaymentChannel openChannel(String senderAddress, String receiverAddress,
                                                          BigInteger deposit, String signedOpenChannelTrans,
                                                          Http httpAgent)
            throws IOException, IllegalArgumentException {

        if (Settings.DEBUG_INFO) {
            System.out.println(senderAddress + " tries to open a channel to pay " + receiverAddress + " up to "
                    + deposit + " Tokens at maximum.");
        }

        if (!validateBalance(deposit)) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Invalid Balance provided: " + deposit);
            }

            throw new IllegalArgumentException("Invalid balance provided.");
        }

        CallTransaction.Function func = Settings.CHANNEL_CONTRACT.getByName("createChannelERC20");
        byte[] funcBytes = func.encode(receiverAddress, deposit);
        String funcEncodedDataStr = "0x" + new String(Hex.encodeHex(funcBytes));
        String request = getEtherRequest("eth_estimateGas", senderAddress,
                Settings.CHANNEL_CONTRACT_ADDRESS, "0x0", funcEncodedDataStr);

        if (Settings.DEBUG_INFO) {
            System.out.println("The request string of queryCreatChannelGasString is " + request);
        }


        //TODO: fix the gas estmation
//        String gasEstimateRes;
//        try {
//            gasEstimateRes = (String) httpAgent.getHttpResponse(request);
//        } catch (IOException e) {
//            if (Settings.DEBUG_INFO) {
//                System.out.println("Invoking function with given arguments is not allowed.");
//            }
//            throw e;
//        }
//
//        if(gasEstimateRes == null) {
//            if (Settings.DEBUG_INFO) {
//                System.out.println("Failed to get response from the Ether node.");
//            }
//            return null;
//        }
//
//        if (Settings.DEBUG_INFO) {
//            System.out.println("The estimated Gas of createChannelERC20 is " + gasEstimateRes);
//        }
//
//        gasEstimateRes = gasEstimateRes.substring(2);
//        BigInteger estimatedGas = new BigInteger(gasEstimateRes, 16);
//        if (estimatedGas.compareTo(Settings.GAS_LIMIT) > 0)
//            throw new IllegalArgumentException("Exceeded GAS estimation. "
//                    + "Probably invalid signed transaction provided.");

        request = getEtherRequest("eth_sendRawTransaction", signedOpenChannelTrans);

        System.out.println("Submitting Open-Channel Transaction...");

        String transactionId;
        try {
            transactionId = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Fail to execute HTTP request.");
            }
            throw e;
        }

        if (transactionId == null || transactionId.equals("")) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to open channel. " + senderAddress + " --> " + receiverAddress);
            }

            return null;
        }

        String blockNumberHex = waitingForTransaction(transactionId, httpAgent);
        if (Settings.DEBUG_INFO) {
            System.out.println(senderAddress + " --> " + receiverAddress + " channel has been opened in block "
                    + new BigInteger(blockNumberHex.substring(2), 16).toString(10));
        }

        return getChannelInfo(senderAddress, receiverAddress, httpAgent);
    }

    public static boolean closeChannel(String senderAddress, String receiverAddress,
                                                           String signedCloseToSuperTrans, Http httpAgent)
            throws IOException, IllegalArgumentException {



        String request = getEtherRequest("eth_sendRawTransaction", signedCloseToSuperTrans);

        System.out.println("Submitting close client to super channel transaction...");

        String transactionId;
        try {
            transactionId = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Fail to execute HTTP request.");
            }
            throw e;
        }

        if (transactionId == null || transactionId.equals("")) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to close channel. " + senderAddress + " --> " + receiverAddress);
            }

            return false;
        }

        String blockNumberHex = waitingForTransaction(transactionId, httpAgent);
        if (Settings.DEBUG_INFO) {
            System.out.println(senderAddress + " --> " + receiverAddress + " channel has been closed in block "
                    + new BigInteger(blockNumberHex.substring(2), 16).toString(10));
        }

        return true;
    }

    /**
     * Closes a payment channel by receiver.
     *
     * @param sigMeshId         The Receiver's MeshID.
     * @param senderAddress     The Sender's Ether address.
     * @param balance           The balance of the receiver.
     * @param balanceSig        The balance signature, signed by the sender.
     * @param closingSig        The closing signature, signed by the receiver..
     * @param httpAgent         The http agent to send the request to the Ether node.
     * @return                  True on success, otherwise returns False.
     */
    public static boolean cooperativeCloseReceiver(MeshID sigMeshId, String senderAddress, BigInteger balance,
                                                   byte[] balanceSig, byte[] closingSig, Http httpAgent) {

        byte[] balance_Msg_Hash_Sig_r = Arrays.copyOfRange(balanceSig, 0, 32);
        byte[] balance_Msg_Hash_Sig_s = Arrays.copyOfRange(balanceSig, 32, 64);
        byte[] balance_Msg_Hash_Sig_v = Arrays.copyOfRange(balanceSig, 64, 65);
        byte[] closing_Msg_Hash_Sig_r = Arrays.copyOfRange(closingSig, 0, 32);
        byte[] closing_Msg_Hash_Sig_s = Arrays.copyOfRange(closingSig, 32, 64);
        byte[] closing_Msg_Hash_Sig_v = Arrays.copyOfRange(closingSig, 64, 65);

        if (Settings.DEBUG_INFO) {
            System.out.println("Cooperative close channel by receiver: " + senderAddress + " --> " + sigMeshId
                    + " , receiver balance = " + balance);
        }

        CallTransaction.Function func = Settings.CHANNEL_CONTRACT.getByName("cooperativeCloseReceiver");
        byte[] funcBytes = func.encode(senderAddress, balance,
                balance_Msg_Hash_Sig_r, balance_Msg_Hash_Sig_s, new BigInteger(balance_Msg_Hash_Sig_v),
                closing_Msg_Hash_Sig_r, closing_Msg_Hash_Sig_s, new BigInteger(closing_Msg_Hash_Sig_v));

        String funcBytesStr = "0x" + new String(Hex.encodeHex(funcBytes));
        String request = getEtherRequest("eth_estimateGas", sigMeshId.toString(),
                Settings.CHANNEL_CONTRACT_ADDRESS, "0x0", funcBytesStr);

        if (Settings.DEBUG_INFO) {
            System.out.println("The request string of queryEstimateGas is " + request);
        }

        String estimateGasRes;
        try {
            estimateGasRes = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            System.out.println("Invoking function with given arguments is not allowed.");
            return false;
        }

        if(estimateGasRes == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get response from the Ether node.");
            }
            return false;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("The estimatedGas of cooperative channel closing is " + estimateGasRes + ".");
        }

        //Try to get nonce of the receiver
        BigInteger recvNonce = EtherClient.getNonce(sigMeshId.toString(), httpAgent);
        if (recvNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for address: " + sigMeshId.toString());
            }
            return false;
        }

        Transaction trans = new Transaction(EtherUtility.bigIntegerToBytes(recvNonce), // nonce
                EtherUtility.bigIntegerToBytes(Settings.GAS_PRICE), // gas price
                EtherUtility.bigIntegerToBytes(new BigInteger(estimateGasRes.substring(2), 16)), // gas limit
                ByteUtil.hexStringToBytes(Settings.CHANNEL_CONTRACT_ADDRESS), // to id
                EtherUtility.bigIntegerToBytes(new BigInteger("0", 10)), // value
                funcBytes,
                Settings.CHAIN_ID);// chainid

        sigMeshId.sign(trans);

        String signedTrans = "0x" + new String(Hex.encodeHex(trans.getEncoded()));
        request = getEtherRequest("eth_sendRawTransaction", signedTrans);

        String transId;
        try {
            transId = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            System.out.println("Fail to execute HTTP request.");
            return false;
        }

        if (!"".equals(transId)) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Waiting for Kovan to mine transactions ... ");
            }
            waitingForTransaction(transId, httpAgent);
        }

        if(Settings.DEBUG_INFO) {
            System.out.println("Channel has been closed: " + senderAddress + " --> " + sigMeshId);
        }

        return true;
    }


    /**
     * Closes a payment channel by receiver.
     *
     * @param sigMeshId         The Receiver's MeshID.
     * @param recvAddress       The Receiver's Ether address.
     * @param balance           The balance of the receiver.
     * @param balanceSig        The balance signature, signed by the sender.
     * @param closingSig        The closing signature, signed by the receiver..
     * @param httpAgent         The http agent to send the request to the Ether node.
     * @return                  True on success, otherwise returns False.
     */
    public static boolean cooperativeCloseSender(MeshID sigMeshId, String recvAddress, BigInteger balance,
                                                 byte[] balanceSig, byte[] closingSig, Http httpAgent) {

        byte[] balance_Msg_Hash_Sig_r = Arrays.copyOfRange(balanceSig, 0, 32);
        byte[] balance_Msg_Hash_Sig_s = Arrays.copyOfRange(balanceSig, 32, 64);
        byte[] balance_Msg_Hash_Sig_v = Arrays.copyOfRange(balanceSig, 64, 65);
        byte[] closing_Msg_Hash_Sig_r = Arrays.copyOfRange(closingSig, 0, 32);
        byte[] closing_Msg_Hash_Sig_s = Arrays.copyOfRange(closingSig, 32, 64);
        byte[] closing_Msg_Hash_Sig_v = Arrays.copyOfRange(closingSig, 64, 65);

        if (Settings.DEBUG_INFO) {
            System.out.println("Cooperative close channel by sender: " + sigMeshId + " --> " + recvAddress
                    + ", receiver balance = " + balance);
        }

        CallTransaction.Function func = Settings.CHANNEL_CONTRACT.getByName("cooperativeCloseSender");
        byte[] funcBytes = func.encode(recvAddress, balance,
                balance_Msg_Hash_Sig_r, balance_Msg_Hash_Sig_s, new BigInteger(balance_Msg_Hash_Sig_v),
                closing_Msg_Hash_Sig_r, closing_Msg_Hash_Sig_s, new BigInteger(closing_Msg_Hash_Sig_v));

        String funcBytesStr = "0x" + new String(Hex.encodeHex(funcBytes));
        String request = getEtherRequest("eth_estimateGas", sigMeshId.toString(),
                Settings.CHANNEL_CONTRACT_ADDRESS, "0x0", funcBytesStr);

        if (Settings.DEBUG_INFO) {
            System.out.println("The request string of queryEstimateGas is " + request);
        }

        String estimateGasRes;
        try {
            estimateGasRes = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            System.out.println("Invoking function with given arguments is not allowed.");
            return false;
        }

        if(estimateGasRes == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get response from the Ether node.");
            }
            return false;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("The estimatedGas of cooperative channel closing is " + estimateGasRes + ".");
        }

        //Try to get nonce of the sender
        BigInteger senderNonce = EtherClient.getNonce(sigMeshId.toString(), httpAgent);
        if (senderNonce == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to get nonce for address: " + sigMeshId.toString());
            }
            return false;
        }

        Transaction trans = new Transaction(EtherUtility.bigIntegerToBytes(senderNonce), // nonce
                EtherUtility.bigIntegerToBytes(Settings.GAS_PRICE), // gas price
                EtherUtility.bigIntegerToBytes(new BigInteger(estimateGasRes.substring(2), 16)), // gas limit
                ByteUtil.hexStringToBytes(Settings.CHANNEL_CONTRACT_ADDRESS), // to id
                EtherUtility.bigIntegerToBytes(new BigInteger("0", 10)), // value
                funcBytes,
                Settings.CHAIN_ID);// chainid

        sigMeshId.sign(trans);

        String signedTrans = "0x" + new String(Hex.encodeHex(trans.getEncoded()));
        request = getEtherRequest("eth_sendRawTransaction", signedTrans);

        String transId;
        try {
            transId = (String) httpAgent.getHttpResponse(request);
        } catch (IOException e) {
            System.out.println("Fail to execute HTTP request.");
            return false;
        }

        if (!"".equals(transId)) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Waiting for Kovan to mine transactions ... ");
            }
            waitingForTransaction(transId, httpAgent);
        }

        if(Settings.DEBUG_INFO) {
            System.out.println("Channel has been closed: " + sigMeshId + " --> " + recvAddress);
        }

        return true;
    }


    /**
     * Waiting for the transaction to get mined
     *
     * @param transacitonId     The transaction id.
     * @param httpAgent         The Http wrapper
     * @return                  The block number in which the transaction was mined.
     */
    private static String waitingForTransaction(String transacitonId, Http httpAgent) {
        if (Settings.DEBUG_INFO) {
            System.out.println("Waiting for Transaction ID: " + transacitonId);
        }

        System.out.println("Waiting for Transaction " + transacitonId + " to be mined...");

        boolean loop = true;
        String blockNumber = "";
        Object tempObj;
        String request = getEtherRequest("eth_getTransactionReceipt", transacitonId);

        while (loop) {

            try {
                tempObj = httpAgent.getHttpResponse(request);
            } catch (IOException e) {
                if (Settings.DEBUG_INFO) {
                    System.out.println("Fail to execute HTTP request.");
                }
                return "";
            }

            if (tempObj == null) {
                //do nothing
                try {
                    Thread.sleep(Settings.TRANS_CHECK_INTERAVAL);
                } catch (InterruptedException e) {

                }
            } else {
                loop = false;
                JSONObject jsonObject = (JSONObject) tempObj;
                //The jsonObject can be further parsed to get more information.
                blockNumber = (String) jsonObject.get("blockNumber");
            }
        }

        return blockNumber;
    }

    /**
     * Validates the balance. The balance should not exceed the Max defined balance.
     *
     * @param balance   The balance to validate.
     * @return          True if balance is valid, otherwise returns False.
     */
    private static boolean validateBalance(BigInteger balance) {
        return (balance.compareTo(Settings.MAX_DEPOSIT) <= 0);
    }

    /**
     * Constructs the request to the Ether network.
     *
     * @param method    Method name to be execute by the Ether network.
     * @param toAddress the To address.
     * @param data      the Encoded data.
     * @return          The constructed String.
     */
    private static String getEtherRequest(String method, String fromAddress, String toAddress,
                                          String value, String data) {
        return "{\"method\":\""
                + method
                + "\",\"params\":["
                + "{"
                + ((fromAddress == null || fromAddress == "") ? "" : "\"from\":\"" + fromAddress + "\",")
                + ((toAddress == null || toAddress == "") ? "" : "\"to\":\"" + toAddress + "\",")
                + ((value == null || value == "") ? "" : "\"value\":\"" + value + "\",")
                + ((data == null || data == "") ? "" : "\"data\":\"" + data + "\"")
                + "}," + "\"latest\"" + "],"
                + "\"id\":"
                + Settings.CHAIN_ID
                + ",\"jsonrpc\":\"2.0\"}";
    }


    /**
     * Constructs the Ether request.
     *
     * @param method        The method name.
     * @param transaction   The Ether transaction.
     * @return              The constructed Ether request.
     */
    private static String getEtherRequest(String method, String transaction) {

        return "{\"method\":\""
                + method
                + "\",\"params\":[\""
                + transaction
                + "\"],\"id\":"
                + Settings.CHAIN_ID
                + ",\"jsonrpc\":\"2.0\"}";
    }
}
