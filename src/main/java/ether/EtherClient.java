package ether;

import io.left.rightmesh.id.MeshID;
import io.left.rightmesh.util.EtherUtility;
import org.apache.commons.codec.DecoderException;
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
        String queryNonceString
                = "{\"method\":\"parity_nextNonce\",\"params\":[\"" + address + "\"],\"id\":42,\"jsonrpc\":\"2.0\"}";
        String nonce;
        try {
            nonce = (String) httpAgent.getHttpResponse(queryNonceString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to query the nonce for: " + address);
            }
            return null;
        }

        return new BigInteger(nonce, 16);
    }


    /**
     * Get the balance of ether on the Ethereum network.
     *
     * @param address   the address in the Ethereum Network
     * @param httpAgent The Http wrapper
     * @return the Ether balance
     */
    public static String getEtherBalance(String address, Http httpAgent) throws IOException {

        String queryEtherBalanceString
                = "{\"method\":\"eth_getBalance\",\"params\":[\"" + address + "\"],\"id\":42,\"jsonrpc\":\"2.0\"}";
        //System.out.println("The request string in getEtherBalance is "+requestString);
        String etherBalance;
        try {
            etherBalance = (String) httpAgent.getHttpResponse(queryEtherBalanceString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to query the Ether Balance for: " + address);
            }
            throw e;
        }

        return etherBalance;
    }


    /**
     * Get the balance of Tokens on the Ethereum network.
     *
     * @param address   the address in the Ethereum Network
     * @param httpAgent The Http wrapper
     * @return the Tokens balance
     * @throws IOException
     */
    public static String getTokenBalance(String address, Http httpAgent) throws IOException {
        CallTransaction.Function balanceOf = Settings.TOKEN_CONTRACT.getByName("balanceOf");
        byte[] functionBytes = balanceOf.encode(address);
        String requestString = "{\"method\":\"eth_call\"," +
                "\"params\":[" +
                "{" +
                "\"to\":\"" + Settings.TOKEN_CONTRACT_ADDRESS + "\"," +
                "\"data\":\"" + "0x" + new String(Hex.encodeHex(functionBytes)) + "\"" +
                "}," +
                "\"latest\"" +
                "]," +
                "\"id\":42,\"jsonrpc\":\"2.0\"}";
        if (Settings.DEBUG_INFO) {
            System.out.println("Request in getTokenBalance = " + requestString);
        }
        String tokenBalance;
        try {
            tokenBalance = (String) httpAgent.getHttpResponse(requestString);
        } catch (IOException e) {
            throw e;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("Balance of "
                    + address + " = "
                    + new Float(new BigInteger(tokenBalance.substring(2), 16).doubleValue()
                    / (new BigInteger(Settings.APPENDING_ZEROS_FOR_TOKEN, 10).doubleValue())).toString()
                    + " TKN");
        }

        return tokenBalance;
    }

    /**
     * Gets the payment channel info by Sender and Receiver addresses.
     *
     * @param senderAddress   The sender address in the Ethereum Network
     * @param receiverAddress The receiver address in the Ethereum Network
     * @param httpAgent       Http wrapper
     * @return PaymentChannel object that holds all channel info.
     * @throws DecoderException
     * @throws IOException
     */
    public static EtherUtility.PaymentChannel getChannelInfo(String senderAddress, String receiverAddress
            , Http httpAgent) throws DecoderException, IOException {

        byte[] keyInBytes = EtherUtility.getChannelHash(senderAddress, receiverAddress);
        if(keyInBytes == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to construct the channel Hash: "+senderAddress+"-->"+receiverAddress);
            }
            return null;
        }

        String requestString = getEtherCallRequest("channels", keyInBytes);

        if (Settings.DEBUG_INFO) {
            System.out.println("Request in getChannelInfo = " + requestString);
        }

        String checkChannelAvailableResult;
        try {
            checkChannelAvailableResult = (String) httpAgent.getHttpResponse(requestString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Cannot checkChannelAvailable from " + senderAddress + " to " + receiverAddress);
            }
            throw e;
        }

        //TODO: decode channel info;
        return new EtherUtility.PaymentChannel("0x111",
                "0x222", Settings.MAX_DEPOSIT, 1, null, null);
    }


    /**
     * Checks if channel exists in the Ether network by Sender and Receiver addresses.
     *
     * @param senderAddress   The sender address in the Ethereum Network
     * @param receiverAddress The receiver address in the Ethereum Network
     * @param httpAgent       Http wrapper
     * @return True if channel exists, otherwise returns False
     * @throws DecoderException
     * @throws IOException
     */
    public boolean checkChannelAvailable(String senderAddress, String receiverAddress, Http httpAgent)
            throws DecoderException, IOException {

        byte[] keyInBytes = EtherUtility.getChannelHash(senderAddress, receiverAddress);
        if(keyInBytes == null){
            if (Settings.DEBUG_INFO) {
                System.out.println("Failed to construct the channel Hash: "+senderAddress+"-->"+receiverAddress);
            }
            return false;
        }

        String requestString = getEtherCallRequest("channels", keyInBytes);

        if (Settings.DEBUG_INFO) {
            System.out.println("Request in getTokenBalance = " + requestString);
        }

        String checkChannelAvailableResult;
        try {
            checkChannelAvailableResult = (String) httpAgent.getHttpResponse(requestString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Cannot checkChannelAvailable from " + senderAddress + " to " + receiverAddress);
            }
            throw e;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("There "
                    + (new BigInteger(checkChannelAvailableResult.substring(64), 16).compareTo(BigInteger.ZERO) > 0
                    ? "is" : "isn't") + " a channel from " + senderAddress + " to " + receiverAddress);
        }

        return (new BigInteger(checkChannelAvailableResult.substring(64), 16).compareTo(BigInteger.ZERO) > 0);
    }


    /**
     * Approves Payment Channels Contract to spend Tokens on behalf of the Sender.
     *
     * @param senderAddress      The sender address in the Ethereum Network
     * @param deposit            The approved deposit.
     * @param signedApproveTrans The approve transaction, signed by Sender
     * @param httpAgent          The Http wrapper
     * @return True on success, otherwise return False.
     * @throws NumberFormatException
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public static boolean approve(String senderAddress, BigInteger deposit, String signedApproveTrans, Http httpAgent)
            throws NumberFormatException, IOException, IllegalArgumentException {

        if (Settings.DEBUG_INFO) {
            System.out.println(senderAddress + " tries to approve channel "
                    + Settings.CHANNEL_CONTRACT_ADDRESS + " up to " + deposit + " Tokens at maximum.");
        }

        //TODO: check this
        CallTransaction.Function approve = Settings.TOKEN_CONTRACT.getByName("approve");
        byte[] approveFunctionBytes = approve.encode(Settings.CHANNEL_CONTRACT_ADDRESS, deposit);

        String queryApproveGasString = "{\"method\":\"eth_estimateGas\"," +
                "\"params\":[" +
                "{" +
                "\"from\":\"" + senderAddress + "\"," +
                "\"to\":\"" + Settings.TOKEN_CONTRACT_ADDRESS + "\"," +
                "\"value\":\"" + "0x" + new BigInteger("0", 10).toString(16) + "\"," +
                "\"data\":\"" + "0x" + new String(Hex.encodeHex(approveFunctionBytes)) + "\"" +
                "}" +
                "]," +
                "\"id\":42,\"jsonrpc\":\"2.0\"}";

        if (Settings.DEBUG_INFO) {
            System.out.println("The request string of queryApproveGasString is " + queryApproveGasString);
        }

        String approveGasEstimateStr;
        try {
            approveGasEstimateStr = (String) httpAgent.getHttpResponse(queryApproveGasString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Invoking function with given arguments is not allowed.");
            }
            throw e;
        }

        String senderNonce = getNonce(senderAddress, httpAgent).toString(); //TODO: check base, 10 or 16

        if (Settings.DEBUG_INFO) {
            System.out.println("The estimatedGas of approve is " + approveGasEstimateStr + ".");
            System.out.println("The nonce of " + senderAddress + " is " + senderNonce);
        }

        BigInteger approveGasEstimate = new BigInteger(approveGasEstimateStr); //TODO: check base, 10 or 16

        if (approveGasEstimate.compareTo(Settings.GAS_LIMIT) > 0) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Estimeted GAS for approve transaction is too high. "
                        + "Probably some arguments are Invalid.");
            }
            throw new IllegalArgumentException("Estimeted GAS for approve transaction is too high. "
                    + "Probably some arguments are Invalid.");
        }


        String approveSendRawTransactionString = "{\"method\":\"eth_sendRawTransaction\",\"params\":[\""
                + signedApproveTrans + "\"],\"id\":42,\"jsonrpc\":\"2.0\"}";

        String transactionId;
        try {
            transactionId = (String) httpAgent.getHttpResponse(approveSendRawTransactionString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Fail to execute HTTP request.");
            }
            throw e;
        }

        if (transactionId.equals("")) {
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
     * @param senderAddress          The sender address in the Ethereum Network
     * @param receiverAddress        The receiver address in the Ethereum Network
     * @param deposit                The initial deposit in the channel
     * @param signedOpenChannelTrans The Open Channel transaction, signed by Sender
     * @param httpAgent              The Http wrapper.
     * @return The PaymentChannel object that holds all the information of the created channel
     * @throws IOException
     * @throws IllegalArgumentException
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

        CallTransaction.Function createChannelERC20 = Settings.CHANNEL_CONTRACT.getByName("createChannelERC20");
        byte[] createChannelERC20FunctionBytes = createChannelERC20.encode(receiverAddress, deposit);
        String queryCreatChannelGasString = "{\"method\":\"eth_estimateGas\"," +
                "\"params\":[" +
                "{" +
                "\"from\":\"" + senderAddress + "\"," +
                "\"to\":\"" + Settings.CHANNEL_CONTRACT_ADDRESS + "\"," +
                "\"value\":\"" + "0x" + new BigInteger("0", 10).toString(16) + "\"," +
                "\"data\":\"" + "0x" + new String(Hex.encodeHex(createChannelERC20FunctionBytes)) + "\"" +
                "}" +
                "]," +
                "\"id\":42,\"jsonrpc\":\"2.0\"}";

        if (Settings.DEBUG_INFO) {
            System.out.println("The request string of queryCreatChannelGasString is " + queryCreatChannelGasString);
        }

        String openChannelGasEstimate;
        try {
            openChannelGasEstimate = (String) httpAgent.getHttpResponse(queryCreatChannelGasString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Invoking function with given arguments is not allowed.");
            }
            throw e;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("The estimatedGas of createChannelERC20 is " + openChannelGasEstimate);
        }

        BigInteger estimatedGas = new BigInteger(openChannelGasEstimate, 10);
        if (estimatedGas.compareTo(Settings.GAS_LIMIT) > 0)
            throw new IllegalArgumentException("Exceeded GAS estimation. "
                    + "Probably invalid signed transaction provided.");

        String openChannelSendRawTransactionString = "{\"method\":\"eth_sendRawTransaction\",\"params\":[\""
                + signedOpenChannelTrans + "\"],\"id\":42,\"jsonrpc\":\"2.0\"}";

        String transactionId;
        try {
            transactionId = (String) httpAgent.getHttpResponse(openChannelSendRawTransactionString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Fail to execute HTTP request.");
            }
            throw e;
        }

        if (transactionId.equals("")) {
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

        EtherUtility.PaymentChannel channel;
        try {
            channel = getChannelInfo(senderAddress, receiverAddress, httpAgent);
        } catch (DecoderException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("DecoderException: " + e);
            }
            return null;
        }

        return channel;
    }


    //TODO: Finish
    public static void cooperativeCloseSender(MeshID ownMeshId, String senderAddress, String receiverAddress,
                                              byte[] balance_Msg_Hash_Sig_Sender, byte[] closing_Msg_Hash_Sig_Receiver,
                                              String balance, String signedCooperativeCloseSenderTransParam,
                                              Http httpAgent)
            throws NumberFormatException, IllegalArgumentException, IOException {

        BigInteger tempBalance = parseBalance(balance);

        if (balance_Msg_Hash_Sig_Sender == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Provided balance_Msg_Hash_Sig_Sender is null.");
            }
            throw new IllegalArgumentException("balance_Msg_Hash_Sig_Sender cannot be null.");
        }

        if (closing_Msg_Hash_Sig_Receiver == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Provided closing_Msg_Hash_Sig_Receiver is null.");
            }
            throw new IllegalArgumentException("closing_Msg_Hash_Sig_Receiver cannot be null.");
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("The signed closingMsgHash is 0x" + Hex.encodeHexString(closing_Msg_Hash_Sig_Receiver));
            System.out.println("The signed balanceMsgHash is 0x" + Hex.encodeHexString(balance_Msg_Hash_Sig_Sender));
        }

        byte[] balance_Msg_Hash_Sig_r = Arrays.copyOfRange(balance_Msg_Hash_Sig_Sender, 0, 32);
        byte[] balance_Msg_Hash_Sig_s = Arrays.copyOfRange(balance_Msg_Hash_Sig_Sender, 32, 64);
        byte[] balance_Msg_Hash_Sig_v = Arrays.copyOfRange(balance_Msg_Hash_Sig_Sender, 64, 65);

        byte[] closing_Msg_Hash_Sig_r = Arrays.copyOfRange(closing_Msg_Hash_Sig_Receiver, 0, 32);
        byte[] closing_Msg_Hash_Sig_s = Arrays.copyOfRange(closing_Msg_Hash_Sig_Receiver, 32, 64);
        byte[] closing_Msg_Hash_Sig_v = Arrays.copyOfRange(closing_Msg_Hash_Sig_Receiver, 64, 65);


        CallTransaction.Function cooperativeCloseSender = Settings.CHANNEL_CONTRACT.getByName("cooperativeCloseSender");
        byte[] cooperativeCloseSenderFunctionBytes = cooperativeCloseSender.encode(receiverAddress,
                tempBalance, balance_Msg_Hash_Sig_r, balance_Msg_Hash_Sig_s, new BigInteger(balance_Msg_Hash_Sig_v),
                closing_Msg_Hash_Sig_r, closing_Msg_Hash_Sig_s, new BigInteger(closing_Msg_Hash_Sig_v));

        String querycooperativeCloseGasString = "{\"method\":\"eth_estimateGas\"," +
                "\"params\":[" +
                "{" +
                "\"from\":\"" + senderAddress + "\"," +
                "\"to\":\"" + Settings.CHANNEL_CONTRACT_ADDRESS + "\"," +
                "\"value\":\"" + "0x" + new BigInteger("0", 10).toString(16) + "\"," +
                "\"data\":\"" + "0x" + new String(Hex.encodeHex(cooperativeCloseSenderFunctionBytes)) + "\"" +
                "}" +
                "]," +
                "\"id\":42,\"jsonrpc\":\"2.0\"}";

        if (Settings.DEBUG_INFO) {
            System.out.println("The request string of querycooperativeCloseGasString is "
                    + querycooperativeCloseGasString);
        }

        String cooperativeCloseGasEstimate;
        try {
            cooperativeCloseGasEstimate = (String) httpAgent.getHttpResponse(querycooperativeCloseGasString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Invoking function with given arguments is not allowed.");
            }
            throw e;
        }
        if (Settings.DEBUG_INFO) {
            System.out.println("The estimatedGas of cooperative channel closing is "
                    + cooperativeCloseGasEstimate + ".");
        }


        String signedCooperativeCloseSenderTrans = signedCooperativeCloseSenderTransParam;
        if (signedCooperativeCloseSenderTrans == null || signedCooperativeCloseSenderTrans.isEmpty()) {
            BigInteger senderNonce = getNonce(senderAddress, httpAgent);
            Transaction cooperativeCloseSenderTrans = new Transaction(EtherUtility.bigIntegerToBytes(senderNonce), // nonce
                    EtherUtility.bigIntegerToBytes(Settings.GAS_PRICE), // gas price
                    EtherUtility.bigIntegerToBytes(new BigInteger(cooperativeCloseGasEstimate.substring(2), 16)), // gas limit
                    ByteUtil.hexStringToBytes(Settings.CHANNEL_CONTRACT_ADDRESS), // to id
                    EtherUtility.bigIntegerToBytes(new BigInteger("0", 10)), // value
                    cooperativeCloseSenderFunctionBytes, Settings.CHAIN_ID);// chainid

            ownMeshId.sign(cooperativeCloseSenderTrans);
            signedCooperativeCloseSenderTrans = "0x"
                    + new String(Hex.encodeHex(cooperativeCloseSenderTrans.getEncoded()));
        }

        String cooperativeCloseSenderRawTransString = "{\"method\":\"eth_sendRawTransaction\",\"params\":[\""
                + signedCooperativeCloseSenderTrans + "\"],\"id\":42,\"jsonrpc\":\"2.0\"}";

        String transactionId;
        try {
            transactionId = (String) httpAgent.getHttpResponse(cooperativeCloseSenderRawTransString);
        } catch (IOException e) {
            System.out.println("Fail to execute HTTP request.");
            return;
        }

        if (!"".equals(transactionId)) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Waiting for Network to mine transactions ... ");
            }
            waitingForTransaction(transactionId, httpAgent);
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("\bChannel has been closed.");
        }
    }


    //TODO: Finish
    public static void cooperativeCloseReceiver(MeshID ownMeshId, String senderAddress,
                                                String receiverAddress, byte[] balance_Msg_Hash_Sig_Sender,
                                                byte[] closing_Msg_Hash_Sig_Receiver, String balance,
                                                String signedCooperativeCloseReceiverTransParam, Http httpAgent)
            throws NumberFormatException, IllegalArgumentException, IOException {

        BigInteger tempBalance = parseBalance(balance);

        if (balance_Msg_Hash_Sig_Sender == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Provided balance_Msg_Hash_Sig_Sender is null.");
            }
            throw new IllegalArgumentException("balance_Msg_Hash_Sig_Sender cannot be null.");
        }

        if (closing_Msg_Hash_Sig_Receiver == null) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Provided closing_Msg_Hash_Sig_Receiver is null.");
            }
            throw new IllegalArgumentException("closing_Msg_Hash_Sig_Receiver cannot be null.");
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("The signed closingMsgHash is 0x" + Hex.encodeHexString(closing_Msg_Hash_Sig_Receiver));
            System.out.println("The signed balanceMsgHash is 0x" + Hex.encodeHexString(balance_Msg_Hash_Sig_Sender));
        }

        byte[] balance_Msg_Hash_Sig_r = Arrays.copyOfRange(balance_Msg_Hash_Sig_Sender, 0, 32);
        byte[] balance_Msg_Hash_Sig_s = Arrays.copyOfRange(balance_Msg_Hash_Sig_Sender, 32, 64);
        byte[] balance_Msg_Hash_Sig_v = Arrays.copyOfRange(balance_Msg_Hash_Sig_Sender, 64, 65);

        byte[] closing_Msg_Hash_Sig_r = Arrays.copyOfRange(closing_Msg_Hash_Sig_Receiver, 0, 32);
        byte[] closing_Msg_Hash_Sig_s = Arrays.copyOfRange(closing_Msg_Hash_Sig_Receiver, 32, 64);
        byte[] closing_Msg_Hash_Sig_v = Arrays.copyOfRange(closing_Msg_Hash_Sig_Receiver, 64, 65);

        CallTransaction.Function cooperativeCloseReceiver = Settings.CHANNEL_CONTRACT.getByName("cooperativeCloseReceiver");
        byte[] cooperativeCloseReceiverFunctionBytes = cooperativeCloseReceiver.encode(senderAddress,
                tempBalance, balance_Msg_Hash_Sig_r, balance_Msg_Hash_Sig_s, new BigInteger(balance_Msg_Hash_Sig_v),
                closing_Msg_Hash_Sig_r, closing_Msg_Hash_Sig_s, new BigInteger(closing_Msg_Hash_Sig_v));

        String querycooperativeCloseGasString = "{\"method\":\"eth_estimateGas\"," +
                "\"params\":[" +
                "{" +
                "\"from\":\"" + receiverAddress + "\"," +
                "\"to\":\"" + Settings.CHANNEL_CONTRACT_ADDRESS + "\"," +
                "\"value\":\"" + "0x" + new BigInteger("0", 10).toString(16) + "\"," +
                "\"data\":\"" + "0x" + new String(Hex.encodeHex(cooperativeCloseReceiverFunctionBytes)) + "\"" +
                "}" +
                "]," +
                "\"id\":42,\"jsonrpc\":\"2.0\"}";

        if (Settings.DEBUG_INFO) {
            System.out.println("The request string of querycooperativeCloseGasString is "
                    + querycooperativeCloseGasString);
        }

        String cooperativeCloseGasEstimate;
        try {
            cooperativeCloseGasEstimate = (String) httpAgent.getHttpResponse(querycooperativeCloseGasString);
        } catch (IOException e) {
            System.out.println("Invoking function with given arguments is not allowed.");
            return;
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("The estimatedGas of cooperative channel closing is "
                    + cooperativeCloseGasEstimate + ".");
        }


        String signedCooperativeCloseReceiverTrans = signedCooperativeCloseReceiverTransParam;
        if (signedCooperativeCloseReceiverTrans == null || signedCooperativeCloseReceiverTrans.isEmpty()) {

            BigInteger receiverNounce = getNonce(receiverAddress, httpAgent);
            Transaction cooperativeCloseReceiverTrans = new Transaction(EtherUtility.bigIntegerToBytes(receiverNounce), // nonce
                    EtherUtility.bigIntegerToBytes(Settings.GAS_PRICE), // gas price
                    EtherUtility.bigIntegerToBytes(new BigInteger(cooperativeCloseGasEstimate.substring(2), 16)), // gas limit
                    ByteUtil.hexStringToBytes(Settings.CHANNEL_CONTRACT_ADDRESS), // to id
                    EtherUtility.bigIntegerToBytes(new BigInteger("0", 10)), // value
                    cooperativeCloseReceiverFunctionBytes, 42);// chainid

            ownMeshId.sign(cooperativeCloseReceiverTrans);
            signedCooperativeCloseReceiverTrans = "0x"
                    + new String(Hex.encodeHex(cooperativeCloseReceiverTrans.getEncoded()));
        }

        String cooperativeCloseReceiverRawTransactionString = "{\"method\":\"eth_sendRawTransaction\",\"params\":[\""
                + signedCooperativeCloseReceiverTrans + "\"],\"id\":42,\"jsonrpc\":\"2.0\"}";

        String transactionId;
        try {
            transactionId = (String) httpAgent.getHttpResponse(cooperativeCloseReceiverRawTransactionString);
        } catch (IOException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Fail to execute HTTP request.");
            }
            throw e;
        }

        if (!"".equals(transactionId)) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Waiting for Network to mine transactions ... ");
            }
            waitingForTransaction(transactionId, httpAgent);
        }

        if (Settings.DEBUG_INFO) {
            System.out.println("\bChannel has been closed.");
        }
    }


    /**
     * Validates the balance. The balance should not exceed the Max defined balance.
     *
     * @param balance The balance to validate.
     * @return True balance is valid, otherwise returns False.
     */
    private static boolean validateBalance(BigInteger balance) {
        return (Settings.MAX_DEPOSIT.compareTo(balance) > 0) ? true : false;
    }


    /**
     * Parse the balance from it's String representation
     *
     * @param balance The balance to parse.
     * @return Biginteger representation of the balance.
     * @throws NumberFormatException
     * @throws IllegalArgumentException
     */
    private static BigInteger parseBalance(String balance) throws NumberFormatException, IllegalArgumentException {
        BigInteger initDeposit;
        try {
            initDeposit = EtherUtility.decimalToBigInteger(balance, Settings.APPENDING_ZEROS_FOR_TOKEN);
        } catch (NumberFormatException e) {
            if (Settings.DEBUG_INFO) {
                System.out.println("The provided balance is not valid.");
            }
            throw e;
        }

        if (Settings.MAX_DEPOSIT.compareTo(initDeposit) < 0) {
            if (Settings.DEBUG_INFO) {
                System.out.println("Provided deposit is larger than maximum allowed "
                        + Settings.MAX_DEPOSIT.toString(10));
            }
            throw new IllegalArgumentException("Provided deposit is larger than maximum allowed "
                    + Settings.MAX_DEPOSIT.toString(10));
        }

        return initDeposit;
    }


    /**
     * Waiting for the transaction to get mined
     *
     * @param transacitonId The transaction id.
     * @param httpAgent     The Http wrapper
     * @return The block number in which the transaction was mined.
     */
    private static String waitingForTransaction(String transacitonId, Http httpAgent) {
        if (Settings.DEBUG_INFO) {
            System.out.println("Waiting for Transaction ID: " + transacitonId);
        }

        boolean loop = true;
        String blockNumber = new String();
        Object tempObj;
        String queryTransactionString = "{\"method\":\"eth_getTransactionReceipt\"," +
                "\"params\":[\"" +
                transacitonId +
                "\"]," +
                "\"id\":42,\"jsonrpc\":\"2.0\"}";
        while (loop) {

            try {
                tempObj = httpAgent.getHttpResponse(queryTransactionString);
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
     * Constructs the eth_call request String.
     * @param methodName The name of the method that eth_call should to execute.
     * @param dataToEncode The data to pass to the executed method.
     * @return The constructed String.
     */
    private static String getEtherCallRequest(String methodName, byte[] dataToEncode){

        CallTransaction.Function channels = Settings.CHANNEL_CONTRACT.getByName(methodName);
        byte[] functionBytes = channels.encode(dataToEncode);
        String requestString = "{\"method\":\"eth_call\"," +
                "\"params\":[" +
                "{" +
                "\"to\":\"" + Settings.CHANNEL_CONTRACT_ADDRESS + "\"," +
                "\"data\":\"" + "0x" + new String(Hex.encodeHex(functionBytes)) + "\"" +
                "}," +
                "\"latest\"" +
                "]," +
                "\"id\":42,\"jsonrpc\":\"2.0\"}";

        return requestString;
    }
}
