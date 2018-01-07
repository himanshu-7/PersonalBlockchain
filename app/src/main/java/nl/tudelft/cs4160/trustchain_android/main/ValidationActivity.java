package nl.tudelft.cs4160.trustchain_android.main;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.security.KeyPair;
import java.util.List;

import nl.tudelft.cs4160.trustchain_android.Peer;
import nl.tudelft.cs4160.trustchain_android.R;
import nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock;
import nl.tudelft.cs4160.trustchain_android.connection.network.NetworkCommunication;
import nl.tudelft.cs4160.trustchain_android.database.BlockDescription;
import nl.tudelft.cs4160.trustchain_android.database.TrustChainDBHelper;
import nl.tudelft.cs4160.trustchain_android.database.Types;
import nl.tudelft.cs4160.trustchain_android.message.MessageProto;

import static nl.tudelft.cs4160.trustchain_android.main.MainActivity.communication;
import static nl.tudelft.cs4160.trustchain_android.main.MainActivity.getDbHelper;

public class ValidationActivity extends AppCompatActivity {

    private Spinner val_typeSpinner;
    private Types types;
    private final static String TAG = ValidationActivity.class.toString();
    private TrustChainDBHelper dbHelper;
    private static BlockDescription blockDescription;
    private static Peer peer;
    private final static byte[] NullByte = "null".getBytes();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_validation);

        types = new Types();
        dbHelper = getDbHelper();
        List<String> spinnerArray = types.getTypesNormal(null);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        val_typeSpinner = (Spinner) findViewById(R.id.validation_typeSpinner);
        val_typeSpinner.setAdapter(adapter);

    }

    // Generate QR code with
    // -String to be validated
    // -My local IP address
    // -My port
    public void onClickGenValidator(View view) {
        Intent intent = new Intent(this, QRCodePrint.class);
        String toValidate = val_typeSpinner.getSelectedItem().toString();

        String ipToSend = "";
        String portToSend = NetworkCommunication.DEFAULT_PORT + "";
        ipToSend = MainActivity.getLocalIPAddress();

        String message = toValidate + "," + ipToSend + "," + portToSend;

        intent.putExtra("MESSAGE", message);
        startActivity(intent);
    }

    private void startScan() {
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

        IntentResult res = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (res != null) {
            if (res.getContents() == null) {
                Toast.makeText(this, "Cancelled the scan", Toast.LENGTH_LONG).show();
            } else {
                // Got text, extract data and connect to the peer now
                String qrContents;
                qrContents = res.getContents();
                String[] qrArray = qrContents.split(",");
                if(qrArray[0] != null && qrArray[1]!=null && qrArray[2]!=null)
                {
                    checkBlockPresence(qrArray[0], qrArray[1], qrArray[2]);
                }
                else
                {
                    Toast.makeText(this, "QR not generated properly", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }


    // Depending on the QR read contents start the validation proceedure
    // - Checks if 'toValidate' is present in the local blockchain
    private void checkBlockPresence(String toValidate, String ipToConnect, String portToConnect)
    {
        int toValidateID = types.findTypeIDByDescription(toValidate);
        if(toValidateID == -1)
        {
            Log.e(TAG, "Error Validation ID not present in the list:" );
            return;
        }

        blockDescription = dbHelper.getBlockDescriptionByTypeID(toValidateID);

        if(blockDescription == null)
        {
            Toast.makeText(this, toValidate + " not in Blockchain!!", Toast.LENGTH_LONG).show();
            return;
        }

        // Create the peer to communicate with
        peer = new Peer(null, ipToConnect, Integer.parseInt(portToConnect));

        // Check if User would like to share his details
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission");
        builder.setMessage("Share my "+ toValidate + "?");

        builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
                sendValidationBlock();
            }
        });

        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
                Toast.makeText(getApplicationContext(), "Not sharing details", Toast.LENGTH_LONG).show();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void sendValidationBlock()
    {
        int sequence_number = blockDescription.sequence_number;
        //Toast.makeText(this, "Sending Validation Block "+ sequence_number, Toast.LENGTH_LONG).show();

        byte[] publicKey = MainActivity.kp.getPublic().getEncoded();
        MessageProto.TrustChainBlock block = dbHelper.getBlock(publicKey,sequence_number);

        if(block == null)
        {
            Log.e(TAG, "Error: Block not found in Database, sequence_number " + sequence_number );
            return;
        }

        Toast.makeText(this, "Block found "+ sequence_number, Toast.LENGTH_LONG).show();

        // We have the block and also peer information, can send the block now
        // TODO: Send Utilcomm message for differentiating between zkp and normal block and send the full block next
        // First send a Utilcomm block so that the validator knows what block he will be receiving next
        MessageProto.UtilComm utilValBlock = communication.createUtilCommBlock(NullByte,NullByte,NullByte,TrustChainBlock.VALIDATION_NORMAL);
        communication.sendBlock(peer, utilValBlock);

        // Send the full block
        communication.sendBlock(peer,block);
    }

    public static void ValidateBlock(MessageProto.UtilComm utilComm, MessageProto.TrustChainBlock block, Peer to_peer)
    {
        int validationResult = TrustChainBlock.VALIDATION_FAILURE;

        if(utilComm.getBlockType() == TrustChainBlock.VALIDATION_NORMAL)
        {
            // TODO: validate the block.
            validationResult = TrustChainBlock.VALIDATION_SUCCESS;
        }
        else if(utilComm.getBlockType() == TrustChainBlock.VALIDATION_ZKP)
        {
            // TODO: validate the block.
            validationResult = TrustChainBlock.VALIDATION_SUCCESS;
        }

        MessageProto.UtilComm ValResult = communication.createUtilCommBlock(NullByte,NullByte,NullByte,validationResult);
        communication.sendBlock(to_peer, ValResult);

    }


    public void onClickValidate(View view)
    {
        startScan();
    }
}
