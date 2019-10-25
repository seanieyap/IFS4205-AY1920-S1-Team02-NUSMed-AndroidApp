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

public class PatientActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG - Patient";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient);

        Button uploadButton = findViewById(R.id.patientUploadButton);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UpdateJwtTask updateJwtTask = new UpdateJwtTask();
                updateJwtTask.execute();
            }
        });
    }

    public void getPatientUploadPage() {
        Intent intent = new Intent(this, PatientUploadActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_patient, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id){
            case R.id.action_patient_my_profile:
                Intent profileIntent = new Intent(this, MyProfileActivity.class);
                profileIntent.putExtra("role", "Patient");
                startActivity(profileIntent);
                break;
            case R.id.action_patient_web_login:
                callNfcScan();
                break;
            case R.id.action_patient_switch_role:
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

    private class UpdateJwtTask extends AsyncTask<String, Void, Integer> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PatientActivity.this);
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

            if (responseCode == 200) {
                getPatientUploadPage();
            } else {
                Log.d(TAG, "UpdateJwtTask() :: Authentication FAILED! JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }
}
