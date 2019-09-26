package com.example.nusmedapplication;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG - MainActivity";

    private String retrievedDeviceID = null;
    private String retrievedJwt = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        retrieveStoredData();

        if (retrievedJwt != null) {
            Log.d(TAG, "onCreate() :: retrievedJwt: " + retrievedJwt);
            AuthenticateJwtTask authenticateJwtTask = new AuthenticateJwtTask();
            authenticateJwtTask.execute();
        } else {
            Log.d(TAG, "onCreate() :: No stored JWT! Application data might have been wiped or " +
                    "application was just installed. Start AUTHENTICATE activity!");
            Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
            startActivity(intent);
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
            retrievedJwt = sharedPreferences.getString("jwt", null);

            if (retrievedDeviceID == null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                String deviceID = UUID.randomUUID().toString();
                editor.putString("deviceID", deviceID);
                editor.apply();
                retrievedDeviceID = deviceID;
            }

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }
    }

    private boolean authenticateJwt() {
        boolean authenticated = false;
        String deviceID = retrievedDeviceID;
        String jwt = retrievedJwt;

        try {
            URL url = new
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'nric': %s, 'password': %s, 'deviceID': '%s', 'guid': '%s'}",
                    null, null, deviceID, jwt);
            Log.d(TAG, "authenticateJwt() :: jsonCredentialsString: " + jsonCredentialsString);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "authenticateJwt() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    authenticated = true;

                    /*BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String currentLine;
                    while ((currentLine = in.readLine()) != null) {
                        response.append(currentLine);
                    }
                    in.close();

                    String newJwt = response.toString().replace("\"", "");;
                    Log.d(TAG, "authenticateJwt() :: newJwt: " + newJwt);

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
                    editor.apply();*/

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

    private class AuthenticateJwtTask extends AsyncTask<String, Void, Boolean> {

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
            return authenticateJwt();
        }

        @Override
        protected void onPostExecute(Boolean authenticated) {
            if (authenticated) {
                progressDialog.dismiss();
                Log.d(TAG, "AuthenticateJwtTask() :: Authentication SUCCESS! Start RoleSelect activity!");
                Intent intent = new Intent(getApplicationContext(), RoleSelectActivity.class);
                startActivity(intent);
            } else {
                progressDialog.dismiss();
                Log.d(TAG, "AuthenticateJwtTask() :: Authentication FAILED! " +
                        "JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }

}
