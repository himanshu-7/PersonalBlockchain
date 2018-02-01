package nl.tudelft.cs4160.trustchain_android.main;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ListView;

import java.util.ArrayList;

import nl.tudelft.cs4160.trustchain_android.BlockDescriptionAdapter;
import nl.tudelft.cs4160.trustchain_android.BlockInfo;
import nl.tudelft.cs4160.trustchain_android.R;
import nl.tudelft.cs4160.trustchain_android.connection.Communication;
import nl.tudelft.cs4160.trustchain_android.database.BlockDescription;
import nl.tudelft.cs4160.trustchain_android.database.TrustChainDBHelper;
import nl.tudelft.cs4160.trustchain_android.database.Types;

/**
 * Created by Himanshu on 28-01-2018.
 */

public class MyAuthenticationsActivity extends AppCompatActivity {

    private TrustChainDBHelper dbHelper;
    private static final String TAG = MyAuthenticationsActivity.class.getName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_all);


        dbHelper = Communication.getDbHelper();
        ArrayList<BlockInfo> blockInfo = new ArrayList<BlockInfo>();
        ArrayList<BlockDescription> storedInfo = dbHelper.getBlockDescriptionStored();// new ArrayList<BlockDescription>();
        Types types = new Types();
        for(BlockDescription currentBlock : storedInfo) {
            blockInfo.add(new BlockInfo(types.findDescriptionByTypeID(currentBlock.typeID), currentBlock.value,Integer.toString(currentBlock.sequence_number) ));
            //blockInfo.add(new BlockInfo("Surname","Shah","3"));
        }
        BlockDescriptionAdapter blockAdapter = new BlockDescriptionAdapter(this, blockInfo);
        ListView listView = (ListView) findViewById(R.id.my_authentications);
        listView.setAdapter(blockAdapter);

    }

}
