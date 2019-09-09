package com.example.nusmedapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

public class NfcScanActivity extends AppCompatActivity {

    private static final String TAG = "NfcScanActivity";

    private TextView mText;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPendingIntent;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_nfc_scan);

        mText = findViewById(R.id.text);
        mText.setText(R.string.scan_user_tag_helptext);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Check if the smartphone has NFC
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported", Toast.LENGTH_LONG).show();
            finish();
        }

        // Check if NFC is enabled
        if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this, "Enable NFC before using the app", Toast.LENGTH_LONG).show();
            finish();
        }

        Toast.makeText(this, "NFC enabled", Toast.LENGTH_LONG).show();

        Intent nfcIntent = new Intent(this, getClass());
        nfcIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        nfcPendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, 0);

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    "secret_shared_prefs",
                    masterKeyAlias,
                    getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String deviceID = sharedPreferences.getString("deviceID", null);

            if (deviceID == null) {
                Toast.makeText(this, "No device ID found!", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, deviceID, Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (nfcAdapter != null)
            nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, null, null);
    }

    @Override
    public void onNewIntent(Intent intent) {
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        MifareUltralight nfcTag = MifareUltralight.get(tagFromIntent);

        try {
            nfcTag.connect();

            // Read from page 6 of the NFC tag as the tag's unique ID is stored there
            byte[] uniqueIdBytes = nfcTag.readPages(6);

            mText.setText("Tag ID: " + encodeHexString(uniqueIdBytes));
            Toast.makeText(getBaseContext(), "Scan successful!", Toast.LENGTH_LONG).show();

            //TODO: Retrieve device ID, send device ID + tag ID to server to authenticate user, go to next activity

        } catch (IOException e) {
            Log.e(TAG, "IOException while writing MifareUltralight...", e);
        } finally {
            try {
                nfcTag.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException while closing MifareUltralight...", e);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
    }

    public String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }

    public String encodeHexString(byte[] byteArray) {
        StringBuilder hexStringBuilder = new StringBuilder();
        for (byte byteToConvert : byteArray) {
            hexStringBuilder.append(byteToHex(byteToConvert));
        }
        return hexStringBuilder.toString();
    }

    // TODO: disable back navigation
}
