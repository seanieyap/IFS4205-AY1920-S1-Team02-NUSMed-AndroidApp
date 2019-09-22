package com.example.nusmedapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG - MainActivity";

    private String retrievedDeviceID = null;
    private String retrievedNric = null;
    private String retrievedPass = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        retrieveStoredData();

        if (retrievedNric != null && retrievedPass != null) {
            AuthenticateTask authenticateTask = new AuthenticateTask();
            authenticateTask.execute();
        }
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
            retrievedNric = sharedPreferences.getString("nric", null);
            retrievedPass = sharedPreferences.getString("password", null);

            if (retrievedDeviceID == null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                String deviceID = UUID.randomUUID().toString();
                editor.putString("deviceID", deviceID);
                editor.apply();
                retrievedDeviceID = deviceID;
            }

            if (retrievedNric == null || retrievedPass == null) {
                Log.d(TAG, "No stored user/pass! Start AUTHENTICATE activity!");
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }
    }

    private boolean authenticateData() {
        boolean authenticated = false;
        String deviceID = retrievedDeviceID;
        String nric = retrievedNric;
        String password = retrievedPass;

        try {
            URL url = new
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate/password");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format("{'nric': '%s', 'password': '%s'}",
                    nric, password);
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
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.loading_text));
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
                Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                startActivity(intent);
            } else {
                progressDialog.dismiss();
                Log.d(TAG, "Authentication FAILED! Start AUTHENTICATE activity!");
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }
}
