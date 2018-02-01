package nl.tudelft.cs4160.trustchain_android;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import nl.tudelft.cs4160.trustchain_android.database.TrustChainDBHelper;
import nl.tudelft.cs4160.trustchain_android.database.Types;

import static android.content.ContentValues.TAG;

/**
 * Created by Himanshu on 29-01-2018.
 */

public class BlockDescriptionAdapter extends ArrayAdapter<BlockInfo> {

    private TrustChainDBHelper dbHelper;

    Types types = new Types();

    public BlockDescriptionAdapter(Activity context, ArrayList<BlockInfo> Block) {
        super(context, 0, Block);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listItemView = convertView;
        if (listItemView == null) {
            listItemView = LayoutInflater.from(getContext()).inflate(
                    R.layout.list_item, parent, false);
        }


        //ArrayList<BlockDescription> blockInfo = dbHelper.getBlockDescriptionStored();
        Types types = new Types();

        // Get the {@link BlockInfo} object located at this position in the list.
        BlockInfo currentBlock = getItem(position);

        // Find the TextView in the list_item.xml layout with the ID type.
        TextView typeTextView = (TextView) listItemView.findViewById(R.id.type);
        // Get the type from the current BlockInfo object and
        // set this text on the name TextView
        Log.e(TAG, "typeID: " + currentBlock.getType());
        typeTextView.setText(currentBlock.getType());


        // Find the TextView in the list_item.xml layout with the ID value.
        Log.e(TAG, "Value: " + currentBlock.getValue());
        TextView valueTextView = (TextView) listItemView.findViewById(R.id.value);
        // Get the value of the information from the current BlockInfo object and
        // set this text on the number TextView
        valueTextView.setText(currentBlock.getValue());

        // Find the TextView in the list_item.xml layout with the ID sequence.
        TextView sequenceTextView = (TextView) listItemView.findViewById(R.id.sequence);
        // Get the sequence number from the current BlockInfo object and
        // set this text on the number TextView
        Log.e(TAG, "Value: " + currentBlock.getSequence());
        sequenceTextView.setText(currentBlock.getSequence());

        // Return the whole list item layout (containing 3 TextViews)
        // so that it can be shown in the ListView
        return listItemView;
    }
}

