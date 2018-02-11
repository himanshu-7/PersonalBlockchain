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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.List;

import nl.tudelft.cs4160.trustchain_android.Peer;
import nl.tudelft.cs4160.trustchain_android.R;
import nl.tudelft.cs4160.trustchain_android.ZeroKnowledge.ZkpHashChain;
import nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock;
import nl.tudelft.cs4160.trustchain_android.block.ValidationResult;
import nl.tudelft.cs4160.trustchain_android.connection.network.NetworkCommunication;
import nl.tudelft.cs4160.trustchain_android.database.BlockDescription;
import nl.tudelft.cs4160.trustchain_android.database.TrustChainDBHelper;
import nl.tudelft.cs4160.trustchain_android.database.Types;
import nl.tudelft.cs4160.trustchain_android.message.MessageProto;

import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.validateFullBlock;
import static nl.tudelft.cs4160.trustchain_android.block.ValidationResult.PARTIAL_NEXT;
import static nl.tudelft.cs4160.trustchain_android.block.ValidationResult.VALID;
import static nl.tudelft.cs4160.trustchain_android.main.MainActivity.communication;
import static nl.tudelft.cs4160.trustchain_android.main.MainActivity.getDbHelper;

public class ValidationActivity extends AppCompatActivity {

    private Spinner val_typeSpinner;
    private Spinner val_typSpinnerZkp;
    private Types types;
    private final static String TAG = ValidationActivity.class.toString();
    static private TrustChainDBHelper dbHelper;
    private static BlockDescription blockDescription;
    private static Peer peer;
    private final static byte[] NullByte = "null".getBytes();
    private static int EditTextZkpMinNumber;



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

        spinnerArray = types.getTypesZKP(null);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        val_typSpinnerZkp = (Spinner) findViewById(R.id.validation_typeSpinner_zkp);
        val_typSpinnerZkp.setAdapter(adapter);

    }

    // Generate QR code with
    // -Attribute to be validated
    // -My local IP address
    // -My port
    public void onClickGenValidator(View view) {
        String toValidate = val_typeSpinner.getSelectedItem().toString();
        genQrCode(toValidate);
    }

    private  void genQrCode(String toValidate)
    {
        Intent intent = new Intent(this, QRCodePrint.class);

        String ipToSend = "";
        String portToSend = NetworkCommunication.DEFAULT_PORT + "";
        ipToSend = MainActivity.getLocalIPAddress();

        String message = toValidate + "," + ipToSend + "," + portToSend;

        intent.putExtra("MESSAGE", message);
        startActivity(intent);

    }

    public void onClickGenValidatorZkp(View view){

        String toValidate = val_typSpinnerZkp.getSelectedItem().toString();
        EditText editText = findViewById(R.id.val_editText_zkp);
        String text = editText.getText().toString();

        try
        {
            EditTextZkpMinNumber = Integer.parseInt(text);
        }
        catch (NumberFormatException nfe)
        {
            Toast.makeText(this, "Not an integer", Toast.LENGTH_LONG).show();
            return;
        }

        toValidate = toValidate + ":" + text;
        genQrCode(toValidate);

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
                    String []toValidateStrArray = qrArray[0].split(":");

                    if(toValidateStrArray.length == 2)
                        checkBlockPresence(toValidateStrArray[0], qrArray[1], qrArray[2], toValidateStrArray[1]);
                    else
                        checkBlockPresence(toValidateStrArray[0], qrArray[1], qrArray[2], null);
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
    private void checkBlockPresence(String toValidate, String ipToConnect, String portToConnect, final String toValidateZkpMin)
    {
        final int toValidateID = types.findTypeIDByDescription(toValidate);
        if(toValidateID == -1)
        {
            Log.e(TAG, "Error Validation ID not present in the list:" );
            return;
        }

        blockDescription = dbHelper.getBlockDescriptionByTypeID(toValidateID);

        if(blockDescription == null)
        {
            showAlertDialog(toValidate + " not in Blockchain!!");
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
                if(types.isZkpCompatible(toValidateID)) {
                    int zkpMinVal;
                    try
                    {
                        zkpMinVal = Integer.parseInt(toValidateZkpMin);
                    }
                    catch (NumberFormatException nfe)
                    {
                        Log.e(TAG,"ERROR: ZKP not an integer");
                        return;
                    }
                    sendValidationBlock(true, zkpMinVal);
                }
                else
                {
                    sendValidationBlock(false, 0);
                }
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

    private void sendValidationBlock(boolean isZkpBlock, int zkpMinVal)
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

        //Toast.makeText(this, "Block found "+ sequence_number, Toast.LENGTH_LONG).show();

        if(isZkpBlock == false) {
            // We have the block and also peer information, can send the block now
            // First send a Utilcomm block so that the validator knows what block he will be receiving next
            byte []toValidate = new byte[0];;
            try {
                 toValidate = blockDescription.value.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            MessageProto.UtilComm utilValBlock = communication.createUtilCommBlock(toValidate, NullByte, NullByte, TrustChainBlock.VALIDATION_NORMAL);
            communication.sendBlock(peer, utilValBlock);

            //Log.e(TAG,"Hash value before sending for validation "+ Arrays.toString(block.getTransaction().toByteArray()));
            // Send the full block
            communication.sendBlock(peer, block);
        }
        else
        {
            ZkpHashChain zkpObj = new ZkpHashChain();
            // Read the random number and the actual value from the blockDescription
            String[] parser = blockDescription.value.split(":");
            String random = parser[0];
            int authenicatedValue = Integer.parseInt(parser[1]);
            byte []zkpProofHash;

            if(authenicatedValue >= zkpMinVal)
            {
                zkpProofHash = zkpObj.genVerificationProof(random,authenicatedValue,zkpMinVal);
                MessageProto.UtilComm utilValBlock = communication.createUtilCommBlock(NullByte, NullByte, zkpProofHash, TrustChainBlock.VALIDATION_ZKP);
                communication.sendBlock(peer, utilValBlock);

                // Now send the full block
                communication.sendBlock(peer, block);
            }
            else
            {
                showAlertDialog("Cannot Prove!!");
            }


        }
    }

    public static int ValidateBlock(MessageProto.UtilComm utilComm, MessageProto.TrustChainBlock block, Peer to_peer)
    {
        int validationResult = TrustChainBlock.VALIDATION_FAILURE;

        String toValidate = "";
        if(utilComm.getBlockType() == TrustChainBlock.VALIDATION_NORMAL)
        {
            try {
                toValidate = utilComm.getTransactionValue().toString("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            byte[] hashInBlock = block.getTransaction().toByteArray();

            if(Arrays.equals(hashInBlock, TrustChainBlock.hash(toValidate)))
            {
                // TODO: Check for signature of authority here, before setting the result to success.
                validationResult = TrustChainBlock.VALIDATION_SUCCESS;
                Log.e(TAG, "Normal validation success");
            }
            else
            {
                validationResult = TrustChainBlock.VALIDATION_FAILURE;
                Log.e(TAG, "Normal validation failure");
            }
        }
        else if(utilComm.getBlockType() == TrustChainBlock.VALIDATION_ZKP)
        {
            ZkpHashChain zkpObj = new ZkpHashChain();


            byte[]zkpProofHash = utilComm.getZkpProofHash().toByteArray();
            byte []hashInBlock = block.getTransaction().toByteArray();
            Log.e(TAG,"ZkpProof Hash in utilComm" + Arrays.toString(zkpProofHash));
            if(zkpObj.verifyZkpProof(zkpProofHash,hashInBlock,EditTextZkpMinNumber))
                validationResult = TrustChainBlock.VALIDATION_SUCCESS;
        }

         //Second phase to the validation procedure, here im checking if the block is trustable
        if(validationResult == TrustChainBlock.VALIDATION_SUCCESS) {
            validationResult = TrustChainBlock.VALIDATION_FAILURE;

            Log.e(TAG, " \n Transaction verification succesfull start checking the block hash integrity");

            ValidationResult validation = null;
            try {
                validation = validateFullBlock(block, dbHelper);
                if (validation != null && (validation.getStatus() == PARTIAL_NEXT || validation.getStatus() == VALID)) {
                    validationResult = TrustChainBlock.VALIDATION_SUCCESS;
                } else {
                    Log.e(TAG, " \n Validation failed during block verification.");
                }
            } catch (Exception e) {
                Log.e(TAG, " \n Validation failed during block verification.");
            }
        }






        MessageProto.UtilComm ValResult = communication.createUtilCommBlock(NullByte,NullByte,NullByte,validationResult);
        communication.sendBlock(to_peer, ValResult);

        return validationResult;
    }


    public void showAlertDialog(String message)
    {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage(message);
        builder1.setCancelable(true);

        builder1.setNeutralButton(
                "Ok",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

    public void onClickValidate(View view)
    {
        startScan();
    }
}
