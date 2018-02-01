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

    public BlockDescriptionAdapter(Activity context, ArrayList<BlockInfo> Block){
        super(context,0, Block);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent){
        View listItemView = convertView;
        if(listItemView == null) {
            listItemView = LayoutInflater.from(getContext()).inflate(
                    R.layout.list_item, parent, false);
        }


        //ArrayList<BlockDescription> blockInfo = dbHelper.getBlockDescriptionStored();
        Types types = new Types();

        // Get the {@link AndroidFlavor} object located at this position in the list
        Log.e(TAG,"CheckPoint 1");
         BlockInfo currentBlock = getItem(position);

            // Find the TextView in the list_item.xml layout with the ID version_name
            TextView typeTextView = (TextView) listItemView.findViewById(R.id.type);
            // Get the version name from the current AndroidFlavor object and
            // set this text on the name TextView
        Log.e(TAG,"typeID: "+ currentBlock.getType());
            typeTextView.setText(currentBlock.getType());


        // Find the TextView in the list_item.xml layout with the ID version_number
        Log.e(TAG,"Value: "+ currentBlock.getValue());
            TextView valueTextView = (TextView) listItemView.findViewById(R.id.value);
            // Get the version number from the current AndroidFlavor object and
            // set this text on the number TextView
            valueTextView.setText(currentBlock.getValue());

            // Find the TextView in the list_item.xml layout with the ID version_number
            TextView sequenceTextView = (TextView) listItemView.findViewById(R.id.sequence);
            // Get the version number from the current AndroidFlavor object and
            // set this text on the number TextView
        Log.e(TAG,"Value: "+ currentBlock.getSequence());
            sequenceTextView.setText(currentBlock.getSequence());

        // Return the whole list item layout (containing 2 TextViews and an ImageView)
        // so that it can be shown in the ListView
        return listItemView;
    }
}

