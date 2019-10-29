package com.example.nusmedapplication;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG - MainActivity";

    private String retrievedDeviceID = null;
    private String retrievedJwt = null;
    private String jwtRole = null;

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
            Log.d(TAG, "onCreate() :: No stored JWT! Application data might have been wiped or application was just installed. Start AUTHENTICATE activity!");
            Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
            startActivity(intent);
        }
    }

    /**
     * Retrieves the stored data in the encrypted shared preferences.
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

    /**
     * Validates the retrieved JWT with the web server.
     */
    private boolean authenticateJwt() {
        boolean authenticated = false;
        String deviceID = retrievedDeviceID;
        String jwt = retrievedJwt;

        try {
            URL url = new
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            String credentialsString = jwt + ":" + deviceID;
            Log.d(TAG, "authenticateJwt() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + encodedCredentialsString);
            Log.d(TAG, "authenticateJwt() :: Authorization: Bearer " + encodedCredentialsString);
            conn.connect();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "authenticateJwt() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    authenticated = UtilityFunctions.validateResponseAuth(getApplicationContext(),
                            conn.getHeaderField("Authorization"));

                    if (authenticated) {
                        String newJwt = UtilityFunctions.getJwtFromHeader(
                                conn.getHeaderField("Authorization"));
                        UtilityFunctions.storeJwtToPref(getApplicationContext(), newJwt);

                        jwtRole = UtilityFunctions.getRolesFromJwt(newJwt);
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

    /**
     * AsyncTask to validate the retrieved JWT.
     */
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
                intent.putExtra("role", jwtRole);
                startActivity(intent);
            } else {
                progressDialog.dismiss();
                Log.d(TAG, "AuthenticateJwtTask() :: Authentication FAILED! JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }

}
