package nl.tudelft.cs4160.trustchain_android.main;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.List;

import nl.tudelft.cs4160.trustchain_android.R;
import nl.tudelft.cs4160.trustchain_android.database.BlockDescription;
import nl.tudelft.cs4160.trustchain_android.database.TrustChainDBHelper;
import nl.tudelft.cs4160.trustchain_android.database.Types;

import static nl.tudelft.cs4160.trustchain_android.main.MainActivity.getDbHelper;

public class ValidationActivity extends AppCompatActivity {

    private Spinner val_typeSpinner;
    private Types types;
    private final static String TAG = ValidationActivity.class.toString();
    private TrustChainDBHelper dbHelper;


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

    public void onClickGenValidator(View view) {
        Intent intent = new Intent(this, QRCodePrint.class);
        String toValidate = val_typeSpinner.getSelectedItem().toString();
        intent.putExtra("MESSAGE", toValidate);
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
                startValidation(qrContents);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }


    // Depending on the QR read contents start the validation proceedure
    // - Checks if the 'toValidate' is present in the local blockchain
    // - TODO: Sends the respective full block if present
    private void startValidation(String toValidate) {
        if(toValidate == null)
        {
            Toast.makeText(this, "QR Validation string empty!!", Toast.LENGTH_LONG).show();
            return;
        }

        int toValidateID = types.findTypeIDByDescription(toValidate);
        if(toValidateID == -1)
        {
            Log.e(TAG, "Validation ID not present in the list:" );
        }

        BlockDescription blockDescription = dbHelper.getBlockDescriptionByTypeID(toValidateID);

        if(blockDescription == null)
        {
            Toast.makeText(this, toValidate + " not in Blockchain!!", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, toValidate + " present in Blockchain!!", Toast.LENGTH_LONG).show();

    }

    public void onClickValidate(View view) {
        startScan();
    }
}
