package nl.tudelft.cs4160.trustchain_android.database;

/**
 * This class describe the elementet stored in the table called block_description and is neccessary to store the position&informazione inside the block.
 */

public class BlockDescription {
    public int typeID;
    public String value;
    public int sequence_number;


    public BlockDescription (int typeID, String value, int sequence_number){
        this.typeID = typeID;
        this.value = value;
        this.sequence_number = sequence_number;
    }
}
