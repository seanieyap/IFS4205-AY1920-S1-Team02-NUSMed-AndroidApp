package com.example.nusmedapplication;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.BufferedReader;
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

public class TherapistActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG - Therapist";

    public static String[] MY_PATIENTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_therapist);

        GetPatientsTask getPatientsTask = new GetPatientsTask();
        getPatientsTask.execute();

        Button uploadButton = findViewById(R.id.therapistUploadButton);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UpdateJwtTask updateJwtTask = new UpdateJwtTask();
                updateJwtTask.execute();
            }
        });
    }

    public void getTherapistUploadPage() {
        Intent intent = new Intent(this, TherapistUploadActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_therapist, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id){
            case R.id.action_therapist_my_profile:
                // TODO: implement my profile page
                Intent profileIntent = new Intent(this, MyProfileActivity.class);
                startActivity(profileIntent);
                break;
            case R.id.action_therapist_web_login:
                callNfcScan();
                break;
            case R.id.action_therapist_switch_role:
                // TODO: actions with the server to switch user role
                Intent roleIntent = new Intent(this, RoleSelectActivity.class);
                startActivity(roleIntent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // The following line is commented out to disable back press
        // super.onBackPressed();
    }

    private void callNfcScan() {
        Intent intent = new Intent(getApplicationContext(), NfcScanActivity.class);
        intent.putExtra("scanNfcPurpose", "webLogin");
        startActivity(intent);
    }

    private int getPatients() {

        int responseCode = 409;

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
            String jwt = sharedPreferences.getString("jwt", null);

            URL url = new URL("https://ifs4205team2-1.comp.nus.edu.sg/api/record/therapist/getPatients");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'deviceID': '%s', 'jwt': '%s'}",
                    deviceID, jwt);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            responseCode = conn.getResponseCode();
            Log.d(TAG, "getPatients() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String currentLine;
                    while ((currentLine = in.readLine()) != null) {
                        response.append(currentLine);
                    }
                    in.close();

                    String results = new String(Base64.decode(response.toString(), Base64.DEFAULT));
                    Log.d(TAG, "getPatients() :: patients: " + results);
                    MY_PATIENTS = results.split("\r");

                    break;
                case 401:
                    break;
                case 409:
                    break;
                case 500:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "An exception occurred...", e);
        }

        return responseCode;
    }

    private class GetPatientsTask extends AsyncTask<String, Void, Integer> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(TherapistActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage("Getting patients info...");
            progressDialog.show();
        }

        @Override
        protected Integer doInBackground(String... strings) {
            return getPatients();
        }

        @Override
        protected void onPostExecute(Integer responseCode) {
            progressDialog.dismiss();

            if (responseCode != 200) {
                Toast.makeText(getApplicationContext(), "Get patients failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int updateJwt() {

        int responseCode = 500;

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
            String jwt = sharedPreferences.getString("jwt", null);

            URL url = new
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/updatejwt");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'deviceID': '%s', 'jwt': '%s'}",
                    deviceID, jwt);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            responseCode = conn.getResponseCode();
            Log.d(TAG, "updateJwt() :: responseCode: " + Integer.toString(responseCode));

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

                    String newJwt = response.toString().replace("\"", "");
                    Log.d(TAG, "updateJwt() :: newJwt: " + newJwt);

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

    private class UpdateJwtTask extends AsyncTask<String, Void, Integer> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(TherapistActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.loading_text));
            progressDialog.show();
        }

        @Override
        protected Integer doInBackground(String... params) {
            return updateJwt();
        }

        @Override
        protected void onPostExecute(Integer responseCode) {
            progressDialog.dismiss();

            if (responseCode == 200) {
                getTherapistUploadPage();
            } else {
                Log.d(TAG, "UpdateJwtTask() :: Authentication FAILED! " +
                        "JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }
}
