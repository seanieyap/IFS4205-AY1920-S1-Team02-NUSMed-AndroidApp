package com.example.nusmedapplication;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONObject;

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

public class MyProfileActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG - MyProfile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_profile);

        UpdateJwtTask updateJwtTask = new UpdateJwtTask();
        updateJwtTask.execute();

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    "secret_shared_prefs",
                    masterKeyAlias,
                    getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String jwt = sharedPreferences.getString("jwt", null);
            String claims = jwt.split("\\.")[0];
            byte[] claimsBytes = Base64.decode(claims, Base64.DEFAULT);
            String claimsString = new String(claimsBytes, "UTF-8");
            JSONObject jwtObj = new JSONObject(claimsString);

            String jwtNRIC = jwtObj.getString("nric");

            TextView nricText = findViewById(R.id.userNRICText);
            TextView roleText = findViewById(R.id.userRoleText);

            String nricStr = "NRIC: " + jwtNRIC;
            nricText.setText(nricStr);

            String roleStr = "Role: " + getIntent().getStringExtra("role");
            roleText.setText(roleStr);

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }

        Button returnButton = findViewById(R.id.myProfileReturnButton);
        returnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
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
            progressDialog = new ProgressDialog(MyProfileActivity.this);
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

            if (responseCode != 200) {
                Log.d(TAG, "UpdateJwtTask() :: Authentication FAILED! JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }
}
