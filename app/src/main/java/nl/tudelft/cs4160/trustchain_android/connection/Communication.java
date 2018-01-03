package nl.tudelft.cs4160.trustchain_android.connection;

import android.util.Log;

import com.google.protobuf.ByteString;

import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.tudelft.cs4160.trustchain_android.Peer;
import nl.tudelft.cs4160.trustchain_android.ZeroKnowledge.ZkpHashChain;
import nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock;
import nl.tudelft.cs4160.trustchain_android.block.ValidationResult;
import nl.tudelft.cs4160.trustchain_android.connection.network.NetworkCommunication;
import nl.tudelft.cs4160.trustchain_android.database.TrustChainDBHelper;
import nl.tudelft.cs4160.trustchain_android.main.AuthenticationActivity;
import nl.tudelft.cs4160.trustchain_android.main.MainActivity;
import nl.tudelft.cs4160.trustchain_android.message.MessageProto;

import static nl.tudelft.cs4160.trustchain_android.Peer.bytesToHex;
import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.GENESIS_SEQ;
import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.builderFullBlock;
import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.builderFullBlockLocal;
import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.builderHalfBlock;
import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.pubKeyToString;
import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.sign;
import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.validateFullBlock;
import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.validateHalfBlock;
import static nl.tudelft.cs4160.trustchain_android.block.ValidationResult.NO_INFO;
import static nl.tudelft.cs4160.trustchain_android.block.ValidationResult.PARTIAL;
import static nl.tudelft.cs4160.trustchain_android.block.ValidationResult.PARTIAL_NEXT;
import static nl.tudelft.cs4160.trustchain_android.block.ValidationResult.PARTIAL_PREVIOUS;
import static nl.tudelft.cs4160.trustchain_android.block.ValidationResult.VALID;
import static nl.tudelft.cs4160.trustchain_android.message.MessageProto.Message.newBuilder;


/**
 * Class that is responsible for the communication.
 */
public abstract class Communication {

    private static final String TAG = Communication.class.getName();
    private Map<String, byte[]> peers;
    private TrustChainDBHelper dbHelper;
    private KeyPair keyPair;
    private CommunicationListener listener;

    private static MessageProto.TrustChainBlock blockInVerification;
    private static MessageProto.UtilComm prevUtilCommBlock;

    // Fixme: if any parameter with 'null' is passed all members of block is created as null.
    // Hence this workaround to pass a "null" byte array.
    private final static byte [] NullByte = "null".getBytes();

    private static int CurrBlockType;
    private static byte[] CurrTransactionValue;


    public Communication(TrustChainDBHelper dbHelper, KeyPair kp, CommunicationListener listener) {
        this.dbHelper = dbHelper;
        this.keyPair = kp;
        this.listener = listener;
        this.peers = new HashMap<>();

    }

    public CommunicationListener getListener() {
        return listener;
    }

    /**
     * Send a crawl request to the peer.
     *
     * @param peer      The peer.
     * @param publicKey Public key of me.
     * @param seqNum    Requested sequence number.
     */
    public void sendCrawlRequest(Peer peer, byte[] publicKey, int seqNum) {
        int sq = seqNum;
        if (seqNum == 0) {
            MessageProto.TrustChainBlock block = dbHelper.getBlock(publicKey,
                    dbHelper.getMaxSeqNum(publicKey));
            if (block != null) {
                sq = block.getSequenceNumber();
            } else {
                sq = GENESIS_SEQ;
            }
        }

        if (sq >= 0) {
            sq = Math.max(GENESIS_SEQ, sq);
        }

        Log.i(TAG, "Requesting crawl of node " + bytesToHex(publicKey) + ":" + sq);

        MessageProto.CrawlRequest crawlRequest =
                MessageProto.CrawlRequest.newBuilder()
                        .setPublicKey(ByteString.copyFrom(getMyPublicKey()))
                        .setRequestedSequenceNumber(sq)
                        .setLimit(100).build();

        // send the crawl request
        MessageProto.Message message = newBuilder().setCrawlRequest(crawlRequest).build();
        sendMessage(peer, message);
    }


    /**
     * Sends a block to the connected peer.
     *
     * @param block - The block to be send
     */
    public void sendBlock(Peer peer, MessageProto.TrustChainBlock block) {
        MessageProto.Message message = newBuilder().setHalfBlock(block).build();
        sendMessage(peer, message);
    }


    /**
     * Sends a block to the connected peer.
     *
     * @param block - The block to be send
     */
    public void sendBlock(Peer peer, MessageProto.UtilComm block) {
        MessageProto.Message message = newBuilder().setUtilComm(block).build();
        sendMessage(peer, message);
    }


    /**
     * Send either a crawl request of a block to a peer.
     *
     * @param peer    The peer
     * @param message The message.
     */
    public abstract void sendMessage(Peer peer, MessageProto.Message message);

    /**
     * Start listening for messages.
     */
    public abstract void start();


    /**
     * Stop listening to messages
     */
    public abstract void stop();

    /**
     * Sign a half block and send block.
     * Reads from database and inserts a new block in the database.
     * <p>
     * Either a linked half block is given to the function or a transaction that needs to be send
     * <p>
     * Similar to signblock of https://github.com/qstokkink/py-ipv8/blob/master/ipv8/attestation/trustchain/community.pyhttps://github.com/qstokkink/py-ipv8/blob/master/ipv8/attestation/trustchain/community.py
     */
    public MessageProto.TrustChainBlock createFullBlock(Peer peer, MessageProto.TrustChainBlock linkedBlock, int blockType, byte[] transcation_value) {
        // assert that the linked block is not null
        if (linkedBlock == null) {
            Log.e(TAG, "signBlock: Linked block is null.");
            return null;
        }
        // do nothing if linked block is not addressed to me
        if (!Arrays.equals(linkedBlock.getLinkPublicKey().toByteArray(), getMyPublicKey())) {
            Log.e(TAG, "signBlock: Linked block not addressed to me.");
            return null;
        }
        // do nothing if block is not a request
        if (linkedBlock.getLinkSequenceNumber() != TrustChainBlock.UNKNOWN_SEQ) {
            Log.e(TAG, "signBlock: Block is not a request.");
            return null;
        }

        MessageProto.TrustChainBlock blockLocal = builderFullBlockLocal(
                dbHelper,
                getMyPublicKey(),
                linkedBlock,
                peer.getPublicKey(),
                blockType,
                transcation_value);
        //dbHelper.insertInDB(blockLocal);

        MessageProto.TrustChainBlock block = builderFullBlock(
                dbHelper,
                getMyPublicKey(),
                linkedBlock,
                peer.getPublicKey(),
                blockType,
                transcation_value);


        //remove : here the sign should be stored in an another fields double sign
        block = sign(block, keyPair.getPrivate());

        Log.e(TAG, "The full block sign is: "+ bytesToHex(block.getSignature().toByteArray()));

        ValidationResult validation;
        try {
            validation = validateFullBlock(block, dbHelper);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        Log.i(TAG, "Signed block to " + bytesToHex(block.getLinkPublicKey().toByteArray()) +
                ", validation result: " + validation.toString());

        // only send block if validated correctly
        // If you want to test the sending of blocks and don't care whether or not blocks are valid, remove the next check.
        if (validation != null && validation.getStatus() != PARTIAL_NEXT && validation.getStatus() != VALID) {
            Log.e(TAG, "Signed block did not validate. Result: " + validation.toString() + ". Errors: "
                    + validation.getErrors().toString());
            return null;
        } else {
            dbHelper.insertInDB(blockLocal);
            return block;
        }
    }


    /**
     * Builds a half block with the transaction.
     * Reads from database and inserts new halfblock in database.
     *
     * @param transaction - a transaction which should be embedded in the block
     */
    public MessageProto.TrustChainBlock createHalfBlock(byte[] transaction, Peer peer) {
        if (transaction == null) {
            Log.e(TAG, "signBlock: Null transaction given.");
        } else {
            Log.e(TAG, "I'm ready to build a 1/2 block ");
        }

        MessageProto.TrustChainBlock block =
                builderHalfBlock(
                        transaction,
                        dbHelper,
                        getMyPublicKey(),
                        peer.getPublicKey()
                );
        block = sign(block, keyPair.getPrivate());

        Log.e(TAG,"Peer public key after creating block: " + Arrays.toString(block.getLinkPublicKey().toByteArray()));
        Log.e(TAG, "The half block sign is: "+ bytesToHex(block.getSignature().toByteArray()));

        block = sign(block, keyPair.getPrivate());
        Log.e(TAG, "The half block sign 2 is: "+ bytesToHex(block.getSignature().toByteArray()));


        ValidationResult validation;
        try {
            validation = validateHalfBlock(block, dbHelper);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        Log.i(TAG, "Signed block to " + bytesToHex(block.getLinkPublicKey().toByteArray()) +
                ", validation result: " + validation.toString());

        // only send block if validated correctly
        // If you want to test the sending of blocks and don't care whether or not blocks are valid, remove the next check.
        if (validation != null && validation.getStatus() != PARTIAL_NEXT && validation.getStatus() != VALID) {
            Log.e(TAG, "Signed block did not validate. Result: " + validation.toString() + ". Errors: "
                    + validation.getErrors().toString());
        } else {
            //Simulation I'm not sure why, maybe to check the result later?? removed
            //dbHelper.insertInDB(block);
            return block;
        }
        return null;
    }


    /**
     * Checks if we should sign the block. For now there is no reason to not sign a block.
     *
     * @param block - The block for which we might want to sign.
     * @return true
     */
    public static boolean shouldSign(MessageProto.TrustChainBlock block) {
        return true;
    }

    /**
     * We have received a crawl request, this function handles what to do next.
     *
     * @param peer         - peer
     * @param crawlRequest - received crawl request
     */
    public void receivedCrawlRequest(Peer peer, MessageProto.CrawlRequest crawlRequest) {
        int sq = crawlRequest.getRequestedSequenceNumber();

        Log.i(TAG, "Received crawl request from peer with IP: " + peer.getIpAddress() + ":" + peer.getPort() +
                " and public key: \n" + bytesToHex(peer.getPublicKey()) + "\n for sequence number " + sq);

        // a negative sequence number indicates that the requesting peer wants an offset of blocks
        // starting with the last block
        if (sq < 0) {
            MessageProto.TrustChainBlock lastBlock = dbHelper.getLatestBlock(getMyPublicKey());

            if (lastBlock != null) {
                sq = Math.max(GENESIS_SEQ, lastBlock.getSequenceNumber() + sq + 1);
            } else {
                sq = GENESIS_SEQ;
            }
        }

        List<MessageProto.TrustChainBlock> blockList = dbHelper.crawl(getMyPublicKey(), sq);

        for (MessageProto.TrustChainBlock block : blockList) {
            sendBlock(peer, block);
        }

        Log.i(TAG, "Sent " + blockList.size() + " blocks");
    }

    /**
     * Act like we received a crawl request to send information about us to the peer.
     */
    public void sendLatestBlocksToPeer(Peer peer) {
        MessageProto.CrawlRequest crawlRequest =
                MessageProto.CrawlRequest.newBuilder()
                        .setPublicKey(ByteString.copyFrom(peer.getPublicKey()))
                        .setRequestedSequenceNumber(-5)
                        .setLimit(100).build();

        receivedCrawlRequest(peer, crawlRequest);
    }


    /**
     * Process a received message. Checks if the message is a crawl request or a half block
     * and continues with the appropriate action,
     *
     * @param message The message.
     * @param peer    From the peer.
     */
    public void receivedMessage(MessageProto.Message message, Peer peer) {
        MessageProto.TrustChainBlock block = message.getHalfBlock();
        MessageProto.CrawlRequest crawlRequest = message.getCrawlRequest();
        MessageProto.UtilComm utilComm = message.getUtilComm();


        String messageLog = "";


        if (block.getPublicKey().size() > 0 && crawlRequest.getPublicKey().size() == 0 && utilComm.getTransactionValue().size() == 0) {

                peer.setPublicKey(block.getPublicKey().toByteArray());
                peer.setPort(NetworkCommunication.DEFAULT_PORT);


                if (block.getSequenceNumber() == 1) {
                    listener.updateLog("\n I got an ack block and it's a genesis block with pk: " + pubKeyToString(block.getPublicKey().toByteArray(), 32) + "\n");
                    Log.e(TAG, "I got an ack block and it's a genesis block with pk: " + pubKeyToString(block.getPublicKey().toByteArray(), 32) + "\n");
                    addNewPublicKey(peer);
                    return;
                }

                if (block.getLinkSequenceNumber() == TrustChainBlock.UNKNOWN_SEQ) {
                    // In case we received a half block
                    if (prevUtilCommBlock.getBlockType() == TrustChainBlock.AUTHENTICATION) {
                        this.CurrBlockType = TrustChainBlock.AUTHENTICATION;
                        messageLog += "half block received from: " + peer.getIpAddress() + ":" + peer.getPort() + "\n" + TrustChainBlock.toShortString(block);
                        listener.updateLog("\n  half block recived: " + messageLog);
                        addNewPublicKey(peer);
                        this.synchronizedReceivedHalfBlock(peer, block);
                        prevUtilCommBlock = null;
                    }
                    else if (prevUtilCommBlock.getBlockType() == TrustChainBlock.AUTHENTICATION_ZKP)
                    {
                        this.CurrBlockType = TrustChainBlock.AUTHENTICATION_ZKP;
                        messageLog += "case for zkp authentication half block\n";
                        listener.updateLog("\n Transaction value: " + messageLog + "with transaction :" +  block.getTransaction().toStringUtf8());
                        Log.e(TAG, "case for zkp authentication half block\n");

                        //on receiving utilcomm block get the attribute details, generate a random number and send another utilcomm block to the user.

                        if(prevUtilCommBlock != null) {
                            String[] attribute_value = prevUtilCommBlock.getTransactionValue().toStringUtf8().split("\\s+");
                            ZkpHashChain zeroKnowledgeObject = new ZkpHashChain();
                            zeroKnowledgeObject.zkpAuthenticate(Integer.parseInt(attribute_value[1]));
                            MessageProto.UtilComm utilCommToUser = createUtilCommBlock("Authentication Successful!!".getBytes(), zeroKnowledgeObject.getRandomProof().getBytes(), NullByte, TrustChainBlock.RANDOM_PROOF_UTILCOMM);
                            Log.e(TAG, "Sending UtilComm block back to the user along with the zkp random number");
                            listener.updateLog("\n  Sending UtilComm block.......... ");
                            sendBlock(peer, utilCommToUser);
                            prevUtilCommBlock = null;
                            this.CurrTransactionValue = zeroKnowledgeObject.getSignedDigest();
                            Log.e(TAG, "Signed digest is " + this.CurrTransactionValue);
                            // Create a full block now, and send it to the peer.
                            this.synchronizedReceivedHalfBlock(peer, block);
                        }
                        else
                        {
                            throw new NullPointerException("prevUtilCommBlock is NULL");
                        }

                    }
                }
                else {
                //In case we received a full block
                listener.updateLog("\n  I got a full block bro");
                listener.updateLog("\n" + TrustChainBlock.toShortString(block));
                Log.e(TAG, "I got a full block from: " + peer.getIpAddress());

                if (blockInVerification != null) {
                    //Check if we have this block out for verification (the comparison should be on the signature1 )
                    if (block.getLinkPublicKey().equals(blockInVerification.getLinkPublicKey())) {
                        //checking of the sign2
                        Log.e(TAG,"Full Block Transaction value "+ Arrays.toString(block.getTransaction().toByteArray()));
                        listener.updateLog("\n Full block verified and saved");
                        blockInVerification = null;
                        dbHelper.insertInDB(block);
                        return;
                    }
                } else {
                    listener.updateLog("\n It'a a ack block with pk: " + pubKeyToString(block.getPublicKey().toByteArray(), 32) + "\n");
                    Log.e(TAG, " It'a a ack block  with pk: " + pubKeyToString(block.getPublicKey().toByteArray(), 32) + "\n");
                    addNewPublicKey(peer);
                }
            }

        } else if (block.getPublicKey().size() == 0 && crawlRequest.getPublicKey().size() > 0 && utilComm.getTransactionValue().size() == 0) {
            // In case we received a crawlrequest

            messageLog += "crawlrequest received from: " + peer.getIpAddress() + ":"
                    + peer.getPort();
            listener.updateLog("\n  Server: " + messageLog);

            peer.setPublicKey(crawlRequest.getPublicKey().toByteArray());
            this.receivedCrawlRequest(peer, crawlRequest);


        } else if (block.getPublicKey().size() == 0 && crawlRequest.getPublicKey().size() == 0 && utilComm.getTransactionValue().size() > 0) {
            // If we received utilcomm block
            prevUtilCommBlock = utilComm;
            messageLog += "UtilComm block received from: " + peer.getIpAddress() + ":"
                    + peer.getPort();
            listener.updateLog("\n Server: " + messageLog);
            if(utilComm.getBlockType() == TrustChainBlock.RANDOM_PROOF_UTILCOMM) {
                Log.e(TAG, " It'a a UtilComm block  with transaction_value : " +  (utilComm.getTransactionValue().toStringUtf8()) + "\n zkpRandomNumber :" +  (utilComm.getZkpRandomNumber().toStringUtf8()) + "\n zkpProofHash :" +  (utilComm.getZkpProofHash().toStringUtf8()) + "\n and the type of block is :" + utilComm.getBlockType());
            }

        }
    }

    /**
     * A half block was send to us and received by us. Someone wants this peer to create the other half
     * and send it back. This method handles that 'request'.
     * - Checks if the block is valid and puts it in the database if not invalid.
     * - Checks if the block is addressed to me.
     * - Determines if we should sign the block
     * - Check if block matches with its previous block, send crawl request if more information is needed
     */
    public void synchronizedReceivedHalfBlock(Peer peer, MessageProto.TrustChainBlock block) {
        Log.i(TAG, "Received half block from peer with IP: " + peer.getIpAddress() + ":" + peer.getPort() +
                " and public key: " + bytesToHex(peer.getPublicKey()));

        String validatorText = AuthenticationActivity.getValidatorText();

        //addNewPublicKey(peer);

        // Do this validation only for a normal half block, this is not applicable to zkp half block
        if(this.CurrBlockType == TrustChainBlock.AUTHENTICATION) {
            if (validatorText.compareTo(block.getTransaction().toStringUtf8()) != 0) {
                Log.e(TAG, "\n Error: The message has is different: " + block.getTransaction().toStringUtf8() + " != " + validatorText);
                listener.updateLog("\n Error: The message has is different: " + block.getTransaction().toStringUtf8() + " != " + validatorText);
                return;
            }
        }

        ValidationResult validation;
        try {
            validation = validateHalfBlock(block, dbHelper);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Log.i(TAG, "Received block validation result " + validation.toString() + "("
                + TrustChainBlock.toString(block) + ")");

        if (validation.getStatus() == ValidationResult.INVALID) {
            for (String error : validation.getErrors()) {
                Log.e(TAG, "Validation error: " + error);
            }
            return;
        } else {
            //I'm not sure why whats is the point? maybe to check the result later??  removed
            //dbHelper.insertInDB(block);
        }

        // check if block matches up with its previous block
        // At this point gaps cannot be tolerated. If we detect a gap we send crawl requests to fill
        // the gap and delay the method until the gap is filled.
        // Note that this code does not cover the scenario where we obtain this block indirectly,
        // because the code does nothing with this block after the crawlRequest was received.
        if (validation.getStatus() == PARTIAL_PREVIOUS || validation.getStatus() == PARTIAL ||
                validation.getStatus() == NO_INFO) {
            Log.e(TAG, "Request block could not be validated sufficiently, requested crawler. " +
                    validation.toString());
            // send a crawl request, requesting the last 5 blocks before the received halfblock (if available) of the peer
            sendCrawlRequest(peer, block.getPublicKey().toByteArray(), Math.max(GENESIS_SEQ, block.getSequenceNumber() - 5));
        } else {
            block = createFullBlock(peer, block, this.CurrBlockType, this.CurrTransactionValue);
            if (block != null) {
                //Not sure if we want to store this block
                //dbHelper.insertInDB(block);
                Log.e(TAG, "I'm sending back the full block");
                listener.updateLog("\n  Sending back the full block");
                sendBlock(peer, block);
            }
        }
    }


    /**
     * Connect with a peer, either unknown or known.
     * If the peer is not known, this will send a crawl request, otherwise a half block.
     *
     * @param peer - The peer that we want to connect to
     */

    /* This method is now called createNewBlock
    public void connectToPeer(Peer peer) {
        String identifier = peer.getIpAddress();
        if (peer.getDevice() != null) {
            identifier = peer.getDevice().getAddress();
        }
        Log.e(TAG, "Identifier: " + identifier);
        if (hasPublicKey(identifier)) {

            listener.updateLog("Sending half block to known peer");
            peer.setPublicKey(getPublicKey(identifier));

            Log.e(TAG, "public key remote: " + getPublicKey(identifier));

            //Simulation: i'm not interested in sending my latest (max 5) block
            //sendLatestBlocksToPeer(peer);

            try {
                createHalfBlock(MainActivity.TRANSACTION.getBytes("UTF-8"), peer);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        } else {
            Log.e(TAG, "Unknown peer, sending crawl request, when received press connect again ");

            listener.updateLog("Unknown peer, sending crawl request, when received press connect again");
            sendCrawlRequest(peer, getMyPublicKey(), -5);
        }
    }
*/

    /**
     * Builds a utilComm block with the parameters passed.
     *
     * @param  -
     */
    public MessageProto.UtilComm createUtilCommBlock(byte[] transaction_value, byte[] zkpRandomNumber, byte[] zkpProofHash, int blockType) {

        MessageProto.UtilComm block =
                TrustChainBlock.builderUtilCommBlock(
                        transaction_value,
                        zkpRandomNumber,
                        zkpProofHash,
                        blockType
                );

        Log.e(TAG,"Created a UtilComm Block with transaction value: " + Arrays.toString(block.getTransactionValue().toByteArray()));

        return block;

    }


    public void createNewBlock(Peer peer, String transactionMessage,int typeOfBlock) {

        if (blockInVerification != null) {
            Log.e(TAG, "There is another block out for the verification, please try later");
            listener.updateLog("There is another block out for the verification, please try later");
            return;
        }

        String identifier = peer.getIpAddress();
        if (peer.getDevice() != null) {
            identifier = peer.getDevice().getAddress();
        }

        Log.e(TAG, "Identifier: " + identifier);
        if (hasPublicKey(identifier)) {
            peer.setPublicKey(getPublicKey(identifier));

            // Send the UtilComm block first, so that the reciever knows which kind of halfblock it is
            /////////////////////////////////////////////////////////only for testing/////////////////////////////////////
            byte[] byte_msg = new byte[0];
            try {
                byte_msg = transactionMessage.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            byte[] zkp_byte = NullByte;
            byte[] proof_byte = NullByte;
            MessageProto.UtilComm utilComm = createUtilCommBlock(byte_msg,zkp_byte,proof_byte,typeOfBlock);
            Log.e(TAG,"Sending UtilComm block ");
            listener.updateLog("\n  Sending UtilComm block ");
            sendBlock(peer, utilComm);

            ////////////////////////////////////////////////////////////////only for testing/////////////////////////////

            listener.updateLog("Creation of the half block with the transaction: \"" + transactionMessage + "\"");
            Log.e(TAG, "\n Ready to be sent to pk: "+ pubKeyToString(getPublicKey(identifier), 32));
            //listener.updateLog("\n Ready to be sent to pk: "+ pubKeyToString(getPublicKey(identifier), 32));

            try {
                MessageProto.TrustChainBlock halfBlock = createHalfBlock(transactionMessage.getBytes("UTF-8"), peer);
                if (halfBlock != null) {
                    blockInVerification = halfBlock;
                    Log.e(TAG,"Sending half block in blockInVerification");
                    listener.updateLog("\n  Sending half block ");
                    sendBlock(peer, halfBlock);
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        } else {
            Log.e(TAG, "Unknown peer, sending crawl request, when received press connect again ");
            listener.updateLog("Unknown peer, sending crawl request, when received press connect again");
            sendCrawlRequest(peer, getMyPublicKey(), -5);
        }
    }

    public byte[] getMyPublicKey() {
        return keyPair.getPublic().getEncoded();
    }


    protected Map<String, byte[]> getPeers() {
        return peers;
    }


    public boolean hasPublicKey(String identifier) {
        return peers.containsKey(identifier);
    }

    public byte[] getPublicKey(String identifier) {
        return getPeers().get(identifier);
    }

    public abstract void addNewPublicKey(Peer p);


    public boolean simAddPublicKey(String address, byte[] key) {
        getPeers().put(address, key);
        return true;
    }


}
