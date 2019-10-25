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
            return UtilityFunctions.updateJwt(getApplicationContext());
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
