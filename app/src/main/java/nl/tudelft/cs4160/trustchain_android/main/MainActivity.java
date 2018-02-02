package nl.tudelft.cs4160.trustchain_android.main;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.KeyPair;
import java.util.Collections;
import java.util.List;

import nl.tudelft.cs4160.trustchain_android.Peer;
import nl.tudelft.cs4160.trustchain_android.R;
import nl.tudelft.cs4160.trustchain_android.Util.Key;
import nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock;
import nl.tudelft.cs4160.trustchain_android.chainExplorer.ChainExplorerActivity;
import nl.tudelft.cs4160.trustchain_android.connection.Communication;
import nl.tudelft.cs4160.trustchain_android.connection.CommunicationListener;
import nl.tudelft.cs4160.trustchain_android.connection.network.NetworkCommunication;
import nl.tudelft.cs4160.trustchain_android.database.TrustChainDBHelper;
import nl.tudelft.cs4160.trustchain_android.message.MessageProto;

import static nl.tudelft.cs4160.trustchain_android.block.TrustChainBlock.GENESIS_SEQ;

public class MainActivity extends AppCompatActivity implements CommunicationListener, NavigationView.OnNavigationItemSelectedListener {


    public final static String TRANSACTION = "Hello world!";
    private final static String TAG = MainActivity.class.toString();

    public static TrustChainDBHelper getDbHelper() {
        return dbHelper;
    }

    static TrustChainDBHelper dbHelper;

    TextView externalIPText;
    TextView localIPText;
    TextView statusText;
    Button connectionButton;
    Button chainExplorerButton;
    Button resetDatabaseButton;
    Button bluetoothButton;
    EditText editTextDestinationIP;
    EditText toValidateText;
    static EditText validatorText;
    EditText editTextDestinationPort;

    MainActivity thisActivity;
    // Making it public, so other classes can access it
    // TODO: A better design would be to make NetworkCommunication a singleton class
    public static Communication communication;
    /**
     * Key pair of user
     */
    static KeyPair kp;

    /**
     * Listener for the connection button.
     * On click a block is created and send to a peer.
     * When we encounter an unknown peer, send a crawl request to that peer in order to get its
     * public key.
     * Also, when we want to send a block always send our last 5 blocks to the peer so the block
     * request won't be rejected due to NO_INFO error.
     * <p>
     * This is code to simulate dispersy, note that this does not work properly with a busy network,
     * because the time delay between sending information to the peer and sending the actual
     * to-be-signed block could cause gaps.
     * <p>
     * Also note that whatever goes wrong we will never get a valid full block, so the integrity of
     * the network is not compromised due to not using dispersy.
     */
    View.OnClickListener connectionButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Peer peer = new Peer(null, editTextDestinationIP.getText().toString(), Integer.parseInt(editTextDestinationPort.getText().toString()));
            //Simulation : by addding me to the list i don't have to send a claw request first in case i'm sending message to myself
            communication.simAddPublicKey(getLocalIPAddress(), communication.getMyPublicKey());
            //send either a crawl request or a half block
            //communication.connectToPeer(peer);
            communication.createNewBlock(peer, -1, toValidateText.getText().toString(), TrustChainBlock.AUTHENTICATION);
        }
    };

    View.OnClickListener chainExplorerButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(thisActivity, ChainExplorerActivity.class);
            startActivity(intent);
        }
    };


    View.OnClickListener keyOptionsListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String localIp = getLocalIPAddress();

            if (localIp != null || externalIPText.getText() != "") {

                String publicKeyToSend = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.DEFAULT);

                String ipToSend = "";
                String portToSend = NetworkCommunication.DEFAULT_PORT + "";
                if (localIp == null) {
                    ipToSend = externalIPText.getText() + "";
                } else {
                    ipToSend = localIp;
                }
                String message = publicKeyToSend + "," + ipToSend + "," + portToSend;


                Intent intent = new Intent(thisActivity, QRCodePrint.class);
                intent.putExtra("MESSAGE", message);
                startActivity(intent);
            } else {
                Log.e(TAG, "I can't detect your ip address");
            }

            //Log.e(TAG, "This is my public key: " + kp.getPublic().toString());

            /*
            //Correct
            Peer peerTest = new Peer(kp.getPublic().getEncoded(), "test", 1234);
            Log.e(TAG, "The simulated reading gave me this:" + pubKeyToString(peerTest.getPublicKey(), 700));


            //Correct
            String toSend = toString(kp.getPublic().getEncoded());
            Log.e(TAG, "Text to send base 64" + toSend);
            peerTest = new Peer(fromString(toSend), "test", 1234);
            Log.e(TAG, "The simulated reading gave me this:" + pubKeyToString(peerTest.getPublicKey(), 700));

*/
            //Goal
            //String PK = "308201313081EA06072A8648CE3D02013081DE020101302B06072A8648CE3D010102207FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFED304404202AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA984914A14404207B425ED097B425ED097B425ED097B425ED097B425ED097B4260B5E9C7710C8640441042AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD245A20AE19A1B8A086B4E01EDD2C7748D14C923D4D7E6D7C61B229E9C5A27ECED3D902201000000000000000000000000000000014DEF9DEA2F79CD65812631A5CF5D3ED020108034200044993E3375723F039D38B258DE2F59E01CFADCB559B6DA9E8D878B5D55AB763DF4E8274BBF23469122C9DB555551069ABC6C8FA536268876475E62F52EB0230DB";
            //Peer peerTest2 = new Peer( null,"test",1234);
            //peerTest2.setPublicKey(PK.getBytes(Charset.forName("utf-8")));
            // Log.e(TAG, "The simulated 2 reading gave me this:" + pubKeyToString(peerTest2.getPublicKey(), 700));


        }
    };


   /*
    private byte[] fromString(String s) {
        byte[] data = Base64.decode(s, Base64.DEFAULT);
        return data;
    }

    */

    /**
     * Write the object to a Base64 string.
     */
   /* private  String toString(byte[] data) throws IOException {
        String key = Base64.encodeToString(data, Base64.DEFAULT);
        return key;
    } */


    View.OnClickListener resetDatabaseListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
                ((ActivityManager) getApplicationContext().getSystemService(ACTIVITY_SERVICE))
                        .clearApplicationUserData();
            } else {
                Toast.makeText(getApplicationContext(), "Requires at least API 19 (KitKat)", Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        initVariables();
        init();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initVariables() {
        thisActivity = this;
        localIPText = findViewById(R.id.my_local_ip);
        externalIPText = findViewById(R.id.my_external_ip);
        statusText = findViewById(R.id.status);
        statusText.setMovementMethod(new ScrollingMovementMethod());
        editTextDestinationIP = findViewById(R.id.destination_IP);
        toValidateText = findViewById(R.id.toValidateText);
        validatorText = findViewById(R.id.validatorText);
        editTextDestinationPort = findViewById(R.id.destination_port);
        connectionButton = findViewById(R.id.connection_button);
        chainExplorerButton = findViewById(R.id.chain_explorer_button);
        resetDatabaseButton = findViewById(R.id.reset_database_button);
        bluetoothButton = findViewById(R.id.bluetooth_connection_button);
    }

    private void init() {
        dbHelper = new TrustChainDBHelper(thisActivity);


        //create or load keys
        initKeys();

        if (isStartedFirstTime()) {
            MessageProto.TrustChainBlock block = TrustChainBlock.createGenesisBlock(kp);
            dbHelper.insertInDB(block, -1, null);
        }

        communication = new NetworkCommunication(dbHelper, kp, this);

        updateIP();
        updateLocalIPField(getLocalIPAddress());

        connectionButton.setOnClickListener(connectionButtonListener);
        chainExplorerButton.setOnClickListener(chainExplorerButtonListener);
        bluetoothButton.setOnClickListener(keyOptionsListener);
        resetDatabaseButton.setOnClickListener(resetDatabaseListener);

        //start listening for messages
        communication.start();

    }

    private void initKeys() {
        kp = Key.loadKeys(getApplicationContext());
        if (kp == null) {
            kp = Key.createAndSaveKeys(getApplicationContext());
            Log.i(TAG, "New keys created");
        }
    }

    /**
     * Checks if this is the first time the app is started and returns a boolean value indicating
     * this state.
     *
     * @return state - false if the app has been initialized before, true if first time app started
     */
    public boolean isStartedFirstTime() {
        // check if a genesis block is present in database
        MessageProto.TrustChainBlock genesisBlock = dbHelper.getBlock(kp.getPublic().getEncoded(), GENESIS_SEQ);

        return genesisBlock == null;
    }

    /**
     * Updates the external IP address textfield to the given IP address.
     */
    public void updateExternalIPField(String ipAddress) {
        externalIPText.setText(ipAddress);
        Log.i(TAG, "Updated external IP Address: " + ipAddress);
    }

    /**
     * Updates the internal IP address textfield to the given IP address.
     */
    public void updateLocalIPField(String ipAddress) {
        localIPText.setText(ipAddress);
        Log.i(TAG, "Updated local IP Address:" + ipAddress);
    }

    /**
     * Finds the external IP address of this device by making an API call to https://www.ipify.org/.
     * The networking runs on a separate thread.
     */
    public void updateIP() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (java.util.Scanner s = new java.util.Scanner(new java.net.URL("https://api.ipify.org").openStream(), "UTF-8").useDelimiter("\\A")) {
                    final String ip = s.next();
                    // new thread to handle UI updates
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateExternalIPField(ip);
                        }
                    });
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * Finds the local IP address of this device, loops trough network interfaces in order to find it.
     * The address that is not a loopback address is the IP of the device.
     *
     * @return a string representation of the device's IP address
     */
    public static String getLocalIPAddress() {
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


    @Override
    public void updateLog(final String msg) {
        //just to be sure run it on the ui thread
        //this is not necessary when this function is called from a AsyncTask
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView statusText = findViewById(R.id.status);
                statusText.append(msg);
            }
        });
    }

    public static String getValidatorText() {

        return validatorText.getText().toString();
    }

    // When the QR scan code button is pressed
    public void onClickScanButton(View view) {
        IntentIntegrator intIntegrator = new IntentIntegrator(thisActivity);
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
                connectToPeer(qrContents);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }

    // Parse the raw QR string and ...
    void connectToPeer(String rawQrRead) {
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

        communication.addNewPublicKey(peer);
        Log.e(TAG, "After creating the peer" + Peer.bytesToHex(peer.getPublicKey()));

        communication.createNewBlock(peer, -1, toValidateText.getText().toString(), TrustChainBlock.AUTHENTICATION);


    }

    // Start a new activity when the button for Authentication is pressed
    public void onClickZKPAuthenticate(View view) {
        Intent myIntent = new Intent(thisActivity, AuthenticationActivity.class);
        thisActivity.startActivity(myIntent);
    }

    // Start a new activity when the validation buttton is pressed.
    public void onClickValidateButton(View view) {
        Intent intent = new Intent(this, ValidationActivity.class);
        thisActivity.startActivity(intent);
    }

    //Start a new activity when My Authentications button is pressed.
    public void onClickMyAuthentications(View view) {
        Intent intent = new Intent(this, MyAuthenticationsActivity.class);
        thisActivity.startActivity(intent);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected (MenuItem item){
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.chain_explorer) {
            Intent intent = new Intent(thisActivity, ChainExplorerActivity.class);
            startActivity(intent);
        } else if (id == R.id.authentication) {
            Intent myIntent = new Intent(thisActivity, AuthenticationActivity.class);
            thisActivity.startActivity(myIntent);
        } else if (id == R.id.verification) {
            Intent intent = new Intent(this, ValidationActivity.class);
            thisActivity.startActivity(intent);
        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.view_all) {
            Intent intent = new Intent(this, MyAuthenticationsActivity.class);
            thisActivity.startActivity(intent);
        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

}


