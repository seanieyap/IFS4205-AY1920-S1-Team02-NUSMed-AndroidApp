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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TherapistActivity extends AppCompatActivity {

    public static String[] MY_PATIENTS;

    private static final String TAG = "DEBUG - Therapist";

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
                getTherapistUploadPage();
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
}
