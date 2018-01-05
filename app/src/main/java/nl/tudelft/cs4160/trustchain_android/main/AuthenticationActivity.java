package nl.tudelft.cs4160.trustchain_android.main;

import android.content.Intent;
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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.tudelft.cs4160.trustchain_android.Peer;
import nl.tudelft.cs4160.trustchain_android.R;
import nl.tudelft.cs4160.trustchain_android.Util.Key;
import nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock;
import nl.tudelft.cs4160.trustchain_android.connection.network.NetworkCommunication;
import nl.tudelft.cs4160.trustchain_android.database.BlockDescription;
import nl.tudelft.cs4160.trustchain_android.database.Types;

// TODO: Create a common class for scanning qr codes and halfblock creation
public class AuthenticationActivity extends AppCompatActivity {

    private final static String TAG = AuthenticationActivity.class.toString();
    private static String transaction;
    private static int typeOfTransaction;
    private int typeOfBlock;
    private static EditText validatorText;
    private Spinner normal_typeSpinner;
    private Spinner zkp_typeSpinner;


    private Types types;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        validatorText = findViewById(R.id.normal_toValidate);

        ArrayList<BlockDescription> alreadyStoredBlock = MainActivity.communication.getTypeStored();

        //Type selection spinner creation and population
        types = new Types();


        List<String> spinnerArray = types.getTypesNormal(alreadyStoredBlock);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        normal_typeSpinner = (Spinner) findViewById(R.id.normal_typeSpinner);
        normal_typeSpinner.setAdapter(adapter);

        spinnerArray = types.getTypesZKP(alreadyStoredBlock);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        zkp_typeSpinner = (Spinner) findViewById(R.id.zkp_typeSpinner);
        zkp_typeSpinner.setAdapter(adapter);
    }

    public void onClickScanZkpAuth(View view) {
            EditText attribute_text = findViewById(R.id.zkp_attribute);
            EditText value_text = findViewById(R.id.zkp_value);
            this.transaction = attribute_text.getText().toString().replaceAll("\\s+", "") + " " + value_text.getText().toString();
            this.typeOfTransaction = types.findTypeIDByDescription(zkp_typeSpinner.getSelectedItem().toString());
            typeOfBlock = TrustChainBlock.AUTHENTICATION_ZKP;
            startScan();

    }

    public void onClickScanNormalAuth(View view) {
            EditText transaction_text = findViewById(R.id.normal_toAuthenticate);
            this.transaction = transaction_text.getText().toString();
            this.typeOfTransaction = types.findTypeIDByDescription(normal_typeSpinner.getSelectedItem().toString());
            typeOfBlock = TrustChainBlock.AUTHENTICATION;
            Log.e(TAG, "The transaction value inside onClick is :" + this.transaction);
            startScan();
    }


    public void onClickLoadQRCode(View view) {

        String localIp = getLocalIPAddress();

        if (localIp != null) {

            String publicKeyToSend = Base64.encodeToString(MainActivity.kp.getPublic().getEncoded(), Base64.DEFAULT);

            String ipToSend = "";
            String portToSend = NetworkCommunication.DEFAULT_PORT + "";
            ipToSend = localIp;

            String message = publicKeyToSend + "," + ipToSend + "," + portToSend;


            Intent intent = new Intent(this, QRCodePrint.class);
            intent.putExtra("MESSAGE", message);
            startActivity(intent);
        } else {
            Log.e(TAG, "I can't detect your ip address");
        }
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
                Toast.makeText(this, "The selected type is: "+this.typeOfTransaction, Toast.LENGTH_LONG).show();

                if (this.transaction != null)
                    connectToPeer(qrContents, this.typeOfTransaction, this.transaction);
                else
                    Toast.makeText(this, "Enter data to authenticate", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }


    // Parse the raw QR string and ...
    void connectToPeer(String rawQrRead, int typeOfValue, String toAuthenticate) {
        // The parameters will be public_key, ip_address, port seperated by ",".
        String[] qrArray = rawQrRead.split(",");

        // If no IP address present, don't attempt connection (assuming other fields will be present)
        if (qrArray[1].equals("null")) {
            Toast.makeText(this, "No IP adress present to connect", Toast.LENGTH_LONG).show();
            return;
        }

        Log.e(TAG, "Peer public key to connect" + qrArray[0]);
        Log.e(TAG, "New test after encode" + Key.loadPublicKey(qrArray[0]).getEncoded());

        Peer peer = new Peer(Base64.decode(qrArray[0], Base64.DEFAULT), qrArray[1], Integer.parseInt(qrArray[2]));

        MainActivity.communication.addNewPublicKey(peer);
        Log.e(TAG, "After creating the peer" + Peer.bytesToHex(peer.getPublicKey()));

        MainActivity.communication.createNewBlock(peer, typeOfValue, toAuthenticate, typeOfBlock);
    }



    /**
     * Finds the local IP address of this device, loops trough network interfaces in order to find it.
     * The address that is not a loopback address is the IP of the device.
     *
     * @return a string representation of the device's IP address
     */
    public String getLocalIPAddress() {
        try {
            List<NetworkInterface> netInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface netInt : netInterfaces) {
                List<InetAddress> addresses = Collections.list(netInt.getInetAddresses());
                for (InetAddress addr : addresses) {
                    if (addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static String getValidatorText() {
        return validatorText.getText().toString();
    }

}
