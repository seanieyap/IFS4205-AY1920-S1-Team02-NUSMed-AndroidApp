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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;

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
            uniqueIdString = Base64.encodeToString(uniqueIdBytes, Base64.DEFAULT).trim();
            Log.d(TAG, "onNewIntent() :: Scanned Tag ID: " + uniqueIdString);

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
            retrievedJwt = sharedPreferences.getString("jwt", null);
            Log.d(TAG, "retrieveStoredData() :: retrievedDeviceID: " + retrievedDeviceID);
            Log.d(TAG, "retrieveStoredData() :: retrievedJwt: " + retrievedJwt);

            Intent intent = getIntent();
            scanNfcPurpose = intent.getStringExtra("scanNfcPurpose");
            Log.d(TAG, "retrieveStoredData() :: scanNfcPurpose: " + scanNfcPurpose);

            if ("registerDevice".equals(scanNfcPurpose)) {
                retrievedNric = intent.getStringExtra("nric");
                retrievedPass = intent.getStringExtra("password");
            }

            Log.d(TAG, "retrieveStoredData() :: retrievedNric: " + retrievedNric);
            Log.d(TAG, "retrieveStoredData() :: retrievedPass: " + retrievedPass);

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }
    }

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
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'nric': '%s', 'password': '%s', 'deviceID': '%s', 'tokenID': '%s'}",
                    nric, password, deviceID, tokenID);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "register() :: responseCode: " + Integer.toString(responseCode));

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
                    Log.d(TAG, "register() :: responseString: " + responseString);

                    break;
                case 401:
                    break;
                case 500:
                    Log.e(TAG, "An error has occured!");
                default:
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return success;
    }

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
                Log.d(TAG, "RegisterTask() :: Registration SUCCESS! Start AuthenticateTask!");
                AuthenticateTask authenticateTask = new AuthenticateTask();
                authenticateTask.execute();
            } else {
                progressDialog.dismiss();
                Log.d(TAG, "RegisterTask() :: Registration FAILED! nric/password/deviceId/tokenId may be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.authentication_fail,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private boolean authenticate() {
        boolean authenticated = false;
        String deviceID = retrievedDeviceID;
        String nric = retrievedNric;
        String password = retrievedPass;

        try {
            URL url = new
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'nric': '%s', 'password': '%s', 'deviceID': '%s', 'jwt': %s}",
                    nric, password, deviceID, null);
            Log.d(TAG, "authenticate() :: jsonCredentialsString: " + jsonCredentialsString);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "authenticate() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    // Read JWT from response
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String currentLine;
                    while ((currentLine = in.readLine()) != null) {
                        response.append(currentLine);
                    }
                    in.close();

                    String newJwt = response.toString().replace("\"", "");;
                    Log.d(TAG, "authenticate() :: newJwt: " + newJwt);

                    // Separate JWT into header, claims and signature
                    String[] newJwtParts = newJwt.split("\\.");
                    String claims = newJwtParts[0];
                    String signature = newJwtParts[1];

                    // Verify signature in JWT
                    byte[] modulusBytes = Base64.decode(getString(R.string.m), Base64.DEFAULT);
                    byte[] exponentBytes = Base64.decode(getString(R.string.e), Base64.DEFAULT);
                    BigInteger modulus = new BigInteger(1, modulusBytes);
                    BigInteger exponent = new BigInteger(1, exponentBytes);

                    RSAPublicKeySpec rsaPubKey = new RSAPublicKeySpec(modulus, exponent);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey pubKey = kf.generatePublic(rsaPubKey);

                    Signature signCheck = Signature.getInstance("SHA256withRSA");
                    signCheck.initVerify(pubKey);
                    signCheck.update(Base64.decode(claims, Base64.DEFAULT));
                    authenticated = signCheck.verify(Base64.decode(signature, Base64.DEFAULT));

                    if (authenticated) {
                        // Store JWT in EncryptedSharedPreferences
                        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

                        SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                                "secret_shared_prefs",
                                masterKeyAlias,
                                getApplicationContext(),
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        );

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("jwt", newJwt);
                        editor.apply();

                        // Get roles from JWT
                        byte[] claimsBytes = Base64.decode(claims, Base64.DEFAULT);
                        String claimsString = new String(claimsBytes, "UTF-8");
                        JSONObject jwtObj = new JSONObject(claimsString);
                        jwtRole = jwtObj.getString("Roles");
                        Log.d(TAG, "authenticate() :: Roles: " + jwtRole);
                    }

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
                Log.d(TAG, "AuthenticateTask() :: Authentication SUCCESS! Start RoleSelect activity!");
                Toast.makeText(getBaseContext(), R.string.authentication_success,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), RoleSelectActivity.class);
                intent.putExtra("role", jwtRole);
                startActivity(intent);
            } else {
                progressDialog.dismiss();
                Log.d(TAG, "AuthenticateTask() :: Authentication FAILED! nric/password/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.authentication_fail,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private int weblogin() {
        int responseCode = 500;
        String deviceID = retrievedDeviceID;
        String jwt = retrievedJwt;
        String tokenID = uniqueIdString;

        try {
            URL url = new URL(
                    "https://ifs4205team2-1.comp.nus.edu.sg/api/account/weblogin");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'deviceID': '%s', 'tokenID': '%s', 'jwt': '%s'}",
                    deviceID, tokenID, jwt);
            Log.d(TAG, "weblogin() :: jsonCredentialsString: " + jsonCredentialsString);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            responseCode = conn.getResponseCode();
            Log.d(TAG, "weblogin() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    // Read JWT from response
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String currentLine;
                    while ((currentLine = in.readLine()) != null) {
                        response.append(currentLine);
                    }
                    in.close();

                    String newJwt = response.toString().replace("\"", "");;
                    Log.d(TAG, "weblogin() :: newJwt: " + newJwt);

                    // Separate JWT into header, claims and signature
                    String[] newJwtParts = newJwt.split("\\.");
                    String claims = newJwtParts[0];
                    String signature = newJwtParts[1];

                    // Verify signature in JWT
                    byte[] modulusBytes = Base64.decode(getString(R.string.m), Base64.DEFAULT);
                    byte[] exponentBytes = Base64.decode(getString(R.string.e), Base64.DEFAULT);
                    BigInteger modulus = new BigInteger(1, modulusBytes);
                    BigInteger exponent = new BigInteger(1, exponentBytes);

                    RSAPublicKeySpec rsaPubKey = new RSAPublicKeySpec(modulus, exponent);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey pubKey = kf.generatePublic(rsaPubKey);

                    Signature signCheck = Signature.getInstance("SHA256withRSA");
                    signCheck.initVerify(pubKey);
                    signCheck.update(Base64.decode(claims, Base64.DEFAULT));
                    boolean validSig = signCheck.verify(Base64.decode(signature, Base64.DEFAULT));

                    if (validSig) {
                        // Store JWT in EncryptedSharedPreferences
                        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

                        SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                                "secret_shared_prefs",
                                masterKeyAlias,
                                getApplicationContext(),
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        );

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("jwt", newJwt);
                        editor.apply();

                        // Get roles from JWT
                        byte[] claimsBytes = Base64.decode(claims, Base64.DEFAULT);
                        String claimsString = new String(claimsBytes, "UTF-8");
                        JSONObject jwtObj = new JSONObject(claimsString);
                        jwtRole = jwtObj.getString("Roles");
                        Log.d(TAG, "webLogin() :: Roles: " + jwtRole);
                    }

                    break;
                case 401:
                    break;
                case 404:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return responseCode;
    }

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
                    Log.d(TAG, "WebLoginTask() :: Web Login SUCCESS! Return to previous activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_success,
                            Toast.LENGTH_LONG).show();
                    finish();
                    break;
                case 401:
                    Log.d(TAG, "WebLoginTask() :: Web Login FAILED! deviceID/tokenID/JWT might be invalid. Start AUTHENTICATE activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_fail,
                            Toast.LENGTH_LONG).show();
                    intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                    startActivity(intent);
                    break;
                case 404:
                    Log.d(TAG, "WebLoginTask() :: The web app did not trigger an MFA login!");
                    Toast.makeText(getBaseContext(), R.string.weblogin_fail,
                            Toast.LENGTH_LONG).show();
                    finish();
                    break;
                default:
                    break;
            }
        }
    }

    private int scanPatient() {
        int responseCode = 500;

        String deviceID = retrievedDeviceID;
        String jwt = retrievedJwt;

        String patientTokenID = uniqueIdString;

        try {
            URL url = new URL(
                    "https://ifs4205team2-1.comp.nus.edu.sg/api/record/therapist/scanPatient");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'deviceID': '%s', 'jwt': '%s', 'tokenID': '%s'}",
                    deviceID, jwt, patientTokenID);
            Log.d(TAG, "scanPatient() :: jsonCredentialsString: " + jsonCredentialsString);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            responseCode = conn.getResponseCode();
            Log.d(TAG, "scanPatient() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    // Read JWT from response
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String currentLine;
                    while ((currentLine = in.readLine()) != null) {
                        response.append(currentLine);
                    }
                    in.close();

                    String newJwt = response.toString().replace("\"", "");;
                    Log.d(TAG, "scanPatient() :: newJwt: " + newJwt);

                    // Separate JWT into header, claims and signature
                    String[] newJwtParts = newJwt.split("\\.");
                    String claims = newJwtParts[0];
                    String signature = newJwtParts[1];

                    // Verify signature in JWT
                    byte[] modulusBytes = Base64.decode(getString(R.string.m), Base64.DEFAULT);
                    byte[] exponentBytes = Base64.decode(getString(R.string.e), Base64.DEFAULT);
                    BigInteger modulus = new BigInteger(1, modulusBytes);
                    BigInteger exponent = new BigInteger(1, exponentBytes);

                    RSAPublicKeySpec rsaPubKey = new RSAPublicKeySpec(modulus, exponent);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey pubKey = kf.generatePublic(rsaPubKey);

                    Signature signCheck = Signature.getInstance("SHA256withRSA");
                    signCheck.initVerify(pubKey);
                    signCheck.update(Base64.decode(claims, Base64.DEFAULT));
                    boolean validSig = signCheck.verify(Base64.decode(signature, Base64.DEFAULT));

                    if (validSig) {
                        // Store JWT in EncryptedSharedPreferences
                        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

                        SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                                "secret_shared_prefs",
                                masterKeyAlias,
                                getApplicationContext(),
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        );

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("jwt", newJwt);
                        editor.apply();
                    }

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

        return responseCode;
    }

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
                    Log.d(TAG, "ScanPatientTask() :: Scan Patient SUCCESS! Return to previous activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_success,
                            Toast.LENGTH_LONG).show();
                    intent = new Intent(getApplicationContext(), TherapistActivity.class);
                    startActivity(intent);
                    break;
                case 401:
                    Log.d(TAG, "ScanPatientTask() :: Scan Patient FAILED! deviceID/JWT might be invalid. Start AUTHENTICATE activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_fail,
                            Toast.LENGTH_LONG).show();
                    intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                    startActivity(intent);
                    break;
                default:
                    Log.d(TAG, "ScanPatientTask() :: Scan Patient FAILED! Patient tokenID might be invalid. Return to previous activity!");
                    Toast.makeText(getBaseContext(), R.string.authentication_fail,
                            Toast.LENGTH_LONG).show();
                    finish();
                    break;
            }
        }
    }

}
