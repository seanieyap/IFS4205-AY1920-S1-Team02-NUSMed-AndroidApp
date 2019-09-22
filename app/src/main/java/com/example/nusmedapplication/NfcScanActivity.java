package com.example.nusmedapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.NFC;

public class NfcScanActivity extends AppCompatActivity {

    private static final int MULTIPLE_PERMISSION_REQUEST_CODE = 200;
    private static final String TAG = "DEBUG - NfcScanActivity";

    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPendingIntent;
    private String retrievedDeviceID = null;
    private String retrievedNric = null;
    private String retrievedPass = null;
    private String scanNfcPurpose = null;
    private String uniqueIdString = null;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_nfc_scan);

        ensurePermissions();

        checkNfc();

        retrieveStoredData();

        Button cancelButton = findViewById(R.id.scanNfcCancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, null, null);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        MifareUltralight nfcTag = MifareUltralight.get(tagFromIntent);

        try {
            nfcTag.connect();

            // Read from page 6 of the NFC tag as the tag's unique ID is stored there
            byte[] uniqueIdBytes = nfcTag.readPages(6);
            uniqueIdString = Base64.encodeToString(uniqueIdBytes, Base64.DEFAULT).trim();
            Log.d(TAG, "Scanned Tag ID: " + uniqueIdString);

            AuthenticateTask authenticateTask = new AuthenticateTask();
            authenticateTask.execute();

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
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onBackPressed() {
        // The following line is commented out to disable back press
        // super.onBackPressed();
    }

    private void ensurePermissions() {
        if (!checkPermissions()) {
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), NFC);
        result ^= ContextCompat.checkSelfPermission(getApplicationContext(), CAMERA);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        // Runtime permissions only work API 23 onwards
        ActivityCompat.requestPermissions(this, new String[]{CAMERA, NFC},
                MULTIPLE_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MULTIPLE_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getBaseContext(),
                            "All required permissions granted!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getBaseContext(),
                            "This application requires the requested permissions to be granted!",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void checkNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Check if the smartphone has NFC
        if (nfcAdapter == null) {
            Toast.makeText(this,
                    "This device does not support NFC.\n The application will not work correctly.",
                    Toast.LENGTH_LONG).show();
        }

        // Check if NFC is enabled
        if (nfcAdapter != null && !nfcAdapter.isEnabled()) {
            Toast.makeText(this, "Enable NFC before using the app", Toast.LENGTH_LONG).show();
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_WIRELESS_SETTINGS);
            startActivity(intent);
        }

        Intent nfcIntent = new Intent(this, getClass());
        nfcIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        nfcPendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, 0);
    }

    private void retrieveStoredData() {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    "secret_shared_prefs",
                    masterKeyAlias,
                    getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            retrievedDeviceID = sharedPreferences.getString("deviceID", null);
            Log.d(TAG, "Device ID: " + retrievedDeviceID);

            Intent intent = getIntent();
            scanNfcPurpose = intent.getStringExtra("scanNfcPurpose");
            Log.d(TAG, "Scan NFC Purpose: " + scanNfcPurpose);

            if ("registerDevice".equals(scanNfcPurpose)) {
                retrievedNric = intent.getStringExtra("nric");
                retrievedPass = intent.getStringExtra("password");
            }

            if ("webLogin".equals(scanNfcPurpose)) {
                retrievedNric = sharedPreferences.getString("nric", null);
                retrievedPass = sharedPreferences.getString("password", null);
            }

            Log.d(TAG, "NRIC: " + retrievedNric + " , Password: " + retrievedPass);

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }
    }

    private boolean authenticateData() {
        boolean authenticated = false;
        String deviceID = retrievedDeviceID;
        String nric = retrievedNric;
        String password = retrievedPass;
        String tokenID = uniqueIdString;
        String urlString = null;

        if ("registerDevice".equals(scanNfcPurpose)) {
            //urlString = "https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate/password";
            urlString = "https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate/register";
        } else if ("webLogin".equals(scanNfcPurpose)) {
            //urlString = "https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate/password";
            urlString = "https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate/weblogin";
        }

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'nric': '%s', 'password': '%s', 'deviceID': '%s', 'tokenID': '%s'}",
                    nric, password, deviceID, tokenID);
            Log.d(TAG, jsonCredentialsString);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    authenticated = true;
                    break;
                case 401:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return authenticated;
    }

    private class AuthenticateTask extends AsyncTask<String, Void, Boolean> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(NfcScanActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.authenticating_text));
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            return authenticateData();
        }

        @Override
        protected void onPostExecute(Boolean authenticated) {
            if (authenticated) {
                progressDialog.dismiss();
                Log.d(TAG, "Authentication SUCCESS! Start HOME activity!");

                if ("registerDevice".equals(scanNfcPurpose)) {
                    Toast.makeText(getBaseContext(), R.string.authentication_success, Toast.LENGTH_LONG).show();

                    try {
                        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

                        SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                                "secret_shared_prefs",
                                masterKeyAlias,
                                getApplicationContext(),
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        );


                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("nric", retrievedNric);
                        editor.putString("password", retrievedPass);
                        editor.apply();

                        Log.d(TAG, "Saved (NRIC: " + retrievedNric + " , Password: " + retrievedPass + ")");

                    } catch (Exception e) {
                        Log.e(TAG, "An Exception occurred...", e);
                    }

                } else if ("webLogin".equals(scanNfcPurpose)) {
                    Toast.makeText(getBaseContext(), R.string.weblogin_success, Toast.LENGTH_LONG).show();
                }

                Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                startActivity(intent);
            } else {
                progressDialog.dismiss();
                Log.d(TAG, "Authentication FAILED! Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.authentication_fail, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }
}
