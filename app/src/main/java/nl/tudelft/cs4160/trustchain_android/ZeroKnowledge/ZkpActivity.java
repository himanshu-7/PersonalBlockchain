package nl.tudelft.cs4160.trustchain_android.ZeroKnowledge;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import nl.tudelft.cs4160.trustchain_android.Peer;
import nl.tudelft.cs4160.trustchain_android.R;
import nl.tudelft.cs4160.trustchain_android.Util.Key;
import nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock;
import nl.tudelft.cs4160.trustchain_android.main.MainActivity;

// TODO: Create a common class for scanning qr codes and halfblock creation
public class ZkpActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.toString();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zkp);
    }

    public void onClickScanZkpAuth(View view) {
        IntentIntegrator intIntegrator = new IntentIntegrator(this);
        intIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        intIntegrator.setPrompt("Scan Code");
        intIntegrator.setCameraId(0);
        intIntegrator.setBeepEnabled(false);
        //intIntegrator.setBarcodeImageEnabled(false);
        intIntegrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        IntentResult res = IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(res != null)
        {
            if(res.getContents() == null)
            {
                Toast.makeText(this,"Cancelled the scan",Toast.LENGTH_LONG).show();
            }
            else
            {
                // Got text, extract data and connect to the peer now
                String qrContents;
                qrContents = res.getContents();
                // Reading the transaction message here.
                EditText attribute_text = findViewById(R.id.zkp_attribute);
                EditText value_text = findViewById(R.id.zkp_value);
                String zkp_transaction = attribute_text.getText().toString() + " " + value_text.getText().toString();
                connectToPeer(qrContents, zkp_transaction);
            }
        }
        else
        {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }


    // Parse the raw QR string and ...
    void connectToPeer(String rawQrRead, String toAuthenticate)
    {
        // The parameters will be public_key, ip_address, port seperated by ",".
        String[] qrArray = rawQrRead.split(",");

        // If no IP address present, don't attempt connection (assuming other fields will be present)
        if(qrArray[1].equals("null"))
        {
            Toast.makeText(this,"No IP adress present to connect",Toast.LENGTH_LONG).show();
            return;
        }

        Log.e(TAG,"Peer public key to connect" + qrArray[0]);
        Log.e(TAG,"New test after encode" + Key.loadPublicKey(qrArray[0]).getEncoded());

        Peer peer = new Peer(Base64.decode(qrArray[0],Base64.DEFAULT),qrArray[1],Integer.parseInt(qrArray[2]));

        MainActivity.communication.addNewPublicKey(peer);
        Log.e(TAG,"After creating the peer" + Peer.bytesToHex(peer.getPublicKey()));

        MainActivity.communication.createNewBlock(peer, toAuthenticate, TrustChainBlock.AUTHENTICATION_ZKP);

    }


}
