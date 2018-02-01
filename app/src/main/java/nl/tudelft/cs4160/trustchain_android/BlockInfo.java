package nl.tudelft.cs4160.trustchain_android;

/**
 * Created by Himanshu on 29-01-2018.
 * {@link BlockInfo} represents the the block which contains the successful authentications of the user.
 * It contains type, value and sequence number in blockchain of the information authenticated.
 */


public class BlockInfo {

    /**Type of the information authenticated**/
    private String mType;

    /** Value of the information authenticated**/
    private String mValue;

    /** The sequence number information is present in blockchain**/
    private String mSequence;

    public BlockInfo(String type, String value, String sequence) {
        mType = type;
        mValue = value;
        mSequence = sequence;
    }

    /**
     * Get the type of information stored.
     */
    public String getType(){
        return mType;
    }

    /*
     * Get the value of information stored.
     */
    public String getValue(){
        return mValue;
    }

    /*
     * Get the sequence number of the information in the blockchain.
     */
    public String getSequence(){
        return mSequence;
    }
}

