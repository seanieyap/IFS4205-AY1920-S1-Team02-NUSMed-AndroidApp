package com.example.nusmedapplication;

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
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
    private String retrievedJwt = null;
    private String scanNfcPurpose = null;
    private String uniqueIdString = null;
    private String jwtRole = null;

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
            uniqueIdString = Base64.encodeToString(uniqueIdBytes, Base64.NO_WRAP).trim();
            //Log.d(TAG, "onNewIntent() :: Scanned Tag ID: " + uniqueIdString);

            if ("registerDevice".equals(scanNfcPurpose)) {
                RegisterTask registerTask = new RegisterTask();
                registerTask.execute();
            } else if ("webLogin".equals(scanNfcPurpose)) {
                WebLoginTask webLoginTask = new WebLoginTask();
                webLoginTask.execute();
            } else if ("scanPatient".equals(scanNfcPurpose)) {
                ScanPatientTask scanPatientTask = new ScanPatientTask();
                scanPatientTask.execute();
            }

        } catch (Exception e) {
            Toast.makeText(getBaseContext(), "Invalid NFC Tag!", Toast.LENGTH_LONG).show();
            //Log.e(TAG, "Exception while reading MifareUltralight...", e);
            finish();
        } finally {
            try {
                nfcTag.close();
            } catch (Exception e) {
                Toast.makeText(getBaseContext(), "Invalid NFC Tag!", Toast.LENGTH_LONG).show();
                //Log.e(TAG, "Exception while closing MifareUltralight...", e);
                finish();
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

    /**
     * Checks and requests for necessary permissions.
     */
    private void ensurePermissions() {
        if (!checkPermissions()) {
            requestPermissions();
        }
    }

    /**
     * Checks for necessary permissions.
     */
    private boolean checkPermissions() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), NFC);
        result ^= ContextCompat.checkSelfPermission(getApplicationContext(), CAMERA);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests for necessary permissions.
     */
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

    /**
     * Checks if device supports NFC and is enabled.
     */
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

    /**
     * Retrieves the stored data in the encrypted shared preferences
     * or from data passed through the previous activity.
     */
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
            retrievedJwt = sharedPreferences.getString("jwt", null);
            //Log.d(TAG, "retrieveStoredData() :: retrievedDeviceID: " + retrievedDeviceID);
            //Log.d(TAG, "retrieveStoredData() :: retrievedJwt: " + retrievedJwt);

            Intent intent = getIntent();
            scanNfcPurpose = intent.getStringExtra("scanNfcPurpose");
            //Log.d(TAG, "retrieveStoredData() :: scanNfcPurpose: " + scanNfcPurpose);

            if ("registerDevice".equals(scanNfcPurpose)) {
                retrievedNric = intent.getStringExtra("nric");
                retrievedPass = intent.getStringExtra("password");
            }

            //Log.d(TAG, "retrieveStoredData() :: retrievedNric: " + retrievedNric);
            //Log.d(TAG, "retrieveStoredData() :: retrievedPass: " + retrievedPass);

        } catch (Exception e) {
            //Log.e(TAG, "An Exception occurred...", e);
        }
    }

    /**
     * Registers the user's deviceID, tokenID with the web server.
     */
    private boolean register() {
        boolean success = false;
        String deviceID = retrievedDeviceID;
        String nric = retrievedNric;
        String password = retrievedPass;
        String tokenID = uniqueIdString;

        try {
            URL url = new URL(
                    "https://ifs4205team2-1.comp.nus.edu.sg/api/account/register");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            String credentialsString = nric + ":" + password + ":" + deviceID + ":" + tokenID;
            //Log.d(TAG, "register() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Basic " + encodedCredentialsString);
            //Log.d(TAG, "register() :: Authorization: Basic " + encodedCredentialsString);
            conn.connect();

            int responseCode = conn.getResponseCode();
            //Log.d(TAG, "register() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    success = true;

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String currentLine;
                    while ((currentLine = in.readLine()) != null) {
                        response.append(currentLine);
                    }
                    in.close();

                    String responseString = response.toString();
                    //Log.d(TAG, "register() :: responseString: " + responseString);

                    break;
                case 401:
                    break;
                case 500:
                    //Log.e(TAG, "An error has occured!");
                default:
                    break;
            }

        } catch (Exception e) {
            //Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return success;
    }

    /**
     * AsyncTask to register the user's deviceID, tokenID.
     */
    private class RegisterTask extends AsyncTask<String, Void, Boolean> {

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
            return register();
        }

        @Override
        protected void onPostExecute(Boolean authenticated) {
            if (authenticated) {
                progressDialog.dismiss();
                //Log.d(TAG, "RegisterTask() :: Registration SUCCESS! Start AuthenticateTask!");
                AuthenticateTask authenticateTask = new AuthenticateTask();
                authenticateTask.execute();
            } else {
                progressDialog.dismiss();
                //Log.d(TAG, "RegisterTask() :: Registration FAILED! nric/password/deviceId/tokenId may be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.authentication_fail,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * Authenticates the user's nric, password, deviceID with the web server
     * and retrieve the returned JWT.
     */
    private boolean authenticate() {
        boolean authenticated = false;
        String deviceID = retrievedDeviceID;
        String nric = retrievedNric;
        String password = retrievedPass;

        try {
            URL url = new
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            String credentialsString = nric + ":" + password + ":" + deviceID;
            //Log.d(TAG, "authenticate() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Basic " + encodedCredentialsString);
            //Log.d(TAG, "authenticate() :: Authorization: Basic " + encodedCredentialsString);
            conn.connect();

            int responseCode = conn.getResponseCode();
            //Log.d(TAG, "authenticate() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    authenticated = UtilityFunctions.validateResponseAuth(getApplicationContext(),
                            conn.getHeaderField("Authorization"));

                    if (authenticated) {
                        String newJwt = UtilityFunctions.getJwtFromHeader(
                                conn.getHeaderField("Authorization"));
                        UtilityFunctions.storeJwtToPref(getApplicationContext(), newJwt);

                        jwtRole = UtilityFunctions.getRolesFromJwt(newJwt);
                        //Log.d(TAG, "authenticate() :: Roles: " + jwtRole);
                    }

                    break;
                case 401:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            //Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return authenticated;
    }

    /**
     * AsyncTask to authenticate the user's nric, password, deviceID.
     */
    private class AuthenticateTask extends AsyncTask<String, Void, Boolean> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(NfcScanActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.loading_text));
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            return authenticate();
        }

        @Override
        protected void onPostExecute(Boolean authenticated) {
            if (authenticated) {
                progressDialog.dismiss();
                //Log.d(TAG, "AuthenticateTask() :: Authentication SUCCESS! Start RoleSelect activity!");
                Toast.makeText(getBaseContext(), R.string.authentication_success,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), RoleSelectActivity.class);
                intent.putExtra("role", jwtRole);
                startActivity(intent);
            } else {
                progressDialog.dismiss();
                //Log.d(TAG, "AuthenticateTask() :: Authentication FAILED! nric/password/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.authentication_fail,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * Authenticates the user's jwt, deviceID, tokenID with the web server for web login
     * and retrieve the returned JWT.
     */
    private int weblogin() {
        int responseCode = 500;
        String deviceID = retrievedDeviceID;
        String jwt = retrievedJwt;
        String tokenID = uniqueIdString;

        try {
            URL url = new URL(
                    "https://ifs4205team2-1.comp.nus.edu.sg/api/account/weblogin");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            String credentialsString = jwt + ":" + deviceID + ":" + tokenID;
            //Log.d(TAG, "weblogin() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + encodedCredentialsString);
            //Log.d(TAG, "weblogin() :: Authorization: Bearer " + encodedCredentialsString);
            conn.connect();

            responseCode = conn.getResponseCode();
            //Log.d(TAG, "weblogin() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    boolean validSig = UtilityFunctions.validateResponseAuth(getApplicationContext(),
                            conn.getHeaderField("Authorization"));

                    if (validSig) {
                        String newJwt = UtilityFunctions.getJwtFromHeader(
                                conn.getHeaderField("Authorization"));
                        UtilityFunctions.storeJwtToPref(getApplicationContext(), newJwt);

                        jwtRole = UtilityFunctions.getRolesFromJwt(newJwt);
                        //Log.d(TAG, "webLogin() :: Roles: " + jwtRole);
                    }

                    break;
                case 401:
                    break;
                case 400:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            //Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return responseCode;
    }

    /**
     * AsyncTask to authenticate the user's jwt, deviceID, tokenID for web login.
     */
    private class WebLoginTask extends AsyncTask<String, Void, Integer> {

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
        protected Integer doInBackground(String... params) {
            return weblogin();
        }

        @Override
        protected void onPostExecute(Integer responseCode) {
            progressDialog.dismiss();
            Intent intent;

            switch (responseCode) {
                case 200:
                    //Log.d(TAG, "WebLoginTask() :: Web Login SUCCESS! Return to previous activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_success,
                            Toast.LENGTH_LONG).show();
                    finish();
                    break;
                case 401:
                    //Log.d(TAG, "WebLoginTask() :: Web Login FAILED! deviceID/tokenID/JWT might be invalid. Start AUTHENTICATE activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_fail,
                            Toast.LENGTH_LONG).show();
                    intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                    startActivity(intent);
                    break;
                case 400:
                    //Log.d(TAG, "WebLoginTask() :: The web app did not trigger an MFA login!");
                    Toast.makeText(getBaseContext(), R.string.weblogin_fail,
                            Toast.LENGTH_LONG).show();
                    finish();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Handles scanned emergency patient's tokenID with web server
     * and retrieve the returned JWT.
     */
    private int scanPatient() {
        int responseCode = 500;

        String deviceID = retrievedDeviceID;
        String jwt = retrievedJwt;

        String patientTokenID = uniqueIdString;

        try {
            URL url = new URL(
                    "https://ifs4205team2-1.comp.nus.edu.sg/api/record/therapist/scanPatient");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            String credentialsString = jwt + ":" + deviceID + ":" + patientTokenID;
            //Log.d(TAG, "scanPatient() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + encodedCredentialsString);
            //Log.d(TAG, "scanPatient() :: Authorization: Bearer " + encodedCredentialsString);
            conn.connect();

            responseCode = conn.getResponseCode();
            //Log.d(TAG, "scanPatient() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    boolean validSig = UtilityFunctions.validateResponseAuth(getApplicationContext(),
                            conn.getHeaderField("Authorization"));

                    if (validSig) {
                        String newJwt = UtilityFunctions.getJwtFromHeader(
                                conn.getHeaderField("Authorization"));
                        UtilityFunctions.storeJwtToPref(getApplicationContext(), newJwt);

                        jwtRole = UtilityFunctions.getRolesFromJwt(newJwt);
                        //Log.d(TAG, "scanPatient() :: Roles: " + jwtRole);
                    }

                    break;
                case 401:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            //Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return responseCode;
    }

    /**
     * AsyncTask to handle scanned emergency patient's tokenID.
     */
    private class ScanPatientTask extends AsyncTask<String, Void, Integer> {

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
        protected Integer doInBackground(String... params) {
            return scanPatient();
        }

        @Override
        protected void onPostExecute(Integer responseCode) {
            progressDialog.dismiss();
            Intent intent;

            switch (responseCode) {
                case 200:
                    //Log.d(TAG, "ScanPatientTask() :: Scan Patient SUCCESS! Return to previous activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_success,
                            Toast.LENGTH_LONG).show();
                    intent = new Intent(getApplicationContext(), TherapistActivity.class);
                    startActivity(intent);
                    break;
                case 401:
                    //Log.d(TAG, "ScanPatientTask() :: Scan Patient FAILED! deviceID/JWT might be invalid. Start AUTHENTICATE activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_fail,
                            Toast.LENGTH_LONG).show();
                    intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                    startActivity(intent);
                    break;
                default:
                    //Log.d(TAG, "ScanPatientTask() :: Scan Patient FAILED! Patient tokenID might be invalid. Return to previous activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_fail,
                            Toast.LENGTH_LONG).show();
                    finish();
                    break;
            }
        }
    }

}
