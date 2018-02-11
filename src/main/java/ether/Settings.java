package ether;

import io.left.rightmesh.util.EtherUtility;
import org.ethereum.core.CallTransaction;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Ethereum Global Settings
 * TODO: Except RPC_ADDRESS, need to be moved to jason abi file in the library
 */
public class Settings {
    public final static String RPC_ADDRESS;
    public final static String CHANNEL_CONTRACT_ADDRESS;
    public final static String TOKEN_CONTRACT_ADDRESS;
    public final static String CHANNEL_ABI;
    public final static String TOKEN_ABI;

    public final static CallTransaction.Contract CHANNEL_CONTRACT;
    public final static CallTransaction.Contract TOKEN_CONTRACT;
    public final static String APPENDING_ZEROS_FOR_ETHER;
    public final static String APPENDING_ZEROS_FOR_TOKEN;
    public final static BigInteger MAX_DEPOSIT;
    public final static BigInteger INIT_DEPOSIT;
    public final static BigInteger GAS_PRICE;
    public final static BigInteger GAS_LIMIT;
    public final static boolean DEBUG_INFO;
    public final static long TRANS_CHECK_INTERAVAL;
    public final static int LENGTH_OF_ID_IN_BYTES;
    public final static int CHAIN_ID;


    static {
        String rpcAddress = "";
        String channelContractAddr = "";
        String tokenContractAddr = "";
        String channelABI = "";
        String tokenABI = "";

        CallTransaction.Contract channelContract = null;
        CallTransaction.Contract tokenContract = null;
        String appendingZerosForETH = "";
        String appendingZerosForTKN = "";
        BigInteger maxDeposit = null;
        BigInteger initDeposit = null;
        BigInteger gasPrice = null;
        BigInteger gasLimit = null;
        boolean debugInfo = false;
        long transCheckInterval = 1000;
        int lengthOfIdInBytes = 20;
        int chainId = 20;


        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(new FileReader("rm-ethereum.conf"));
            JSONObject jsonObject = (JSONObject) obj;
            appendingZerosForETH = ((String) jsonObject.get("appendingZerosForETH"));
            appendingZerosForTKN = ((String) jsonObject.get("appendingZerosForTKN"));

            for (Object key : jsonObject.keySet()) {
                switch ((String) key) {
                    case "debugInfo":
                        debugInfo = ((String) jsonObject.get(key)).equals("true") ? true : false;
                        break;
                    case "gasPrice":
                        gasPrice = new BigInteger((String) jsonObject.get(key), 10);
                        if (debugInfo) {
                            System.out.println("The global gas price is set to be " + gasPrice.toString(10));
                        }
                        break;
                    case "gasLimit":
                        gasLimit = new BigInteger((String) jsonObject.get(key), 10);
                        break;
                    case "rpcAddress":
                        rpcAddress = ((String) jsonObject.get(key));
                        if (debugInfo) {
                            System.out.println("rpcAddress = " + rpcAddress);
                        }
                        break;
                    case "channelContractAddr":
                        channelContractAddr = ((String) jsonObject.get(key));
                        if (debugInfo) {
                            System.out.println("channelContractAddr = " + channelContractAddr);
                        }
                        break;
                    case "tokenContractAddr":
                        tokenContractAddr = ((String) jsonObject.get(key));
                        if (debugInfo) {
                            System.out.println("tokenAddr = " + tokenContractAddr);
                        }
                        break;
                    case "channelABI":
                        channelABI = ((String) jsonObject.get(key));
                        if (debugInfo) {
                            System.out.println("channelABI = " + channelABI);
                        }
                        channelContract = new CallTransaction.Contract(channelABI);
                        break;
                    case "tokenABI":
                        tokenABI = ((String) jsonObject.get(key));
                        if (debugInfo) {
                            System.out.println("tokenABI = " + tokenABI);
                        }
                        tokenContract = new CallTransaction.Contract(tokenABI);
                        break;
                    case "maxDepositBits":
                        maxDeposit = EtherUtility.decimalToBigInteger((String) jsonObject.get(key), appendingZerosForTKN);
                        if (debugInfo) {
                            System.out.println("MAX_DEPOSIT =" + maxDeposit.toString(10)); //16?
                        }
                        break;
                    case "initDeposit":
                        initDeposit = EtherUtility.decimalToBigInteger((String) jsonObject.get(key), appendingZerosForTKN);
                        if (debugInfo) {
                            System.out.println("INIT_DEPOSIT =" + initDeposit.toString(10));
                        }
                        break;
                    case "transCheckInterval":
                        transCheckInterval = (long) jsonObject.get(key);
                        if (debugInfo) {
                            System.out.println("TRANS_CHECK_INTERAVAL =" + transCheckInterval);
                        }
                        break;
                    case "lengthOfIdInBytes":
                        lengthOfIdInBytes = (int) jsonObject.get(key);
                        if (debugInfo) {
                            System.out.println("LENGTH_OF_ID_IN_BYTES =" + transCheckInterval);
                        }
                        break;
                    case "chainId":
                        chainId = Integer.parseInt(jsonObject.get(key).toString());
                        if (debugInfo) {
                            System.out.println("CHAIN_ID =" + chainId);
                        }
                        break;


                    default:
                        System.out.println("Unknown key is detected when parsing the configuration files.");
                }
            }

        } catch (FileNotFoundException e) {

        } catch (ParseException e) {
            System.out.println("Couldn't parse contents in m-ethereum.conf as a JSON object." + e);
        } catch (IOException e) {
            System.out.println("Couldn't parse contents in m-ethereum.conf as a JSON object." + e);
        }


        RPC_ADDRESS = rpcAddress;
        CHANNEL_CONTRACT_ADDRESS = channelContractAddr;
        TOKEN_CONTRACT_ADDRESS = tokenContractAddr;
        CHANNEL_ABI = channelABI;
        TOKEN_ABI = tokenABI;

        CHANNEL_CONTRACT = channelContract;
        TOKEN_CONTRACT = tokenContract;
        APPENDING_ZEROS_FOR_ETHER = appendingZerosForETH;
        APPENDING_ZEROS_FOR_TOKEN = appendingZerosForTKN;
        MAX_DEPOSIT = maxDeposit;
        INIT_DEPOSIT = initDeposit;
        GAS_PRICE = gasPrice;
        GAS_LIMIT = gasLimit;
        DEBUG_INFO = debugInfo;
        TRANS_CHECK_INTERAVAL = transCheckInterval;
        LENGTH_OF_ID_IN_BYTES = lengthOfIdInBytes;
        CHAIN_ID = chainId;
    }
}
