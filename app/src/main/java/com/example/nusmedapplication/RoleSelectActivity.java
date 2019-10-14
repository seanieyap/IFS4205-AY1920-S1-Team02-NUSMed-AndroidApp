package com.example.nusmedapplication;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.NFC;

public class RoleSelectActivity extends AppCompatActivity {

    private static final int MULTIPLE_PERMISSION_REQUEST_CODE = 200;
    private static final String TAG = "DEBUG - RoleSelect";
    private static final String ROLE_PATIENT = "10";
    private static final String ROLE_THERAPIST = "01";
    private static final String ROLE_PATIENT_AND_THERAPIST = "11";

    private NfcAdapter nfcAdapter;
    private String role;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_select);

        ensurePermissions();

        checkNfc();

        Button patientButton = findViewById(R.id.rolePatientButton);
        patientButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UpdateJwtRoleTask updateJwtRoleTask = new UpdateJwtRoleTask();
                updateJwtRoleTask.execute(ROLE_PATIENT);
            }
        });

        Button therapistButton = findViewById(R.id.roleTherapistButton);
        therapistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UpdateJwtRoleTask updateJwtRoleTask = new UpdateJwtRoleTask();
                updateJwtRoleTask.execute(ROLE_THERAPIST);
            }
        });

        Intent intent = getIntent();
        role = intent.getStringExtra("role");
        Log.d(TAG, "Role: " + role);

        if (role == null) {
            RoleSelectTask roleSelectTask = new RoleSelectTask();
            roleSelectTask.execute();
        } else {
            disableButtons();
        }
    }

    private void disableButtons() {
        Button patientButton = findViewById(R.id.rolePatientButton);
        Button therapistButton = findViewById(R.id.roleTherapistButton);

        if (ROLE_PATIENT.equals(role)) {
            therapistButton.setVisibility(View.INVISIBLE);
        } else if (ROLE_THERAPIST.equals(role)) {
            patientButton.setVisibility(View.INVISIBLE);
        } else if (ROLE_PATIENT_AND_THERAPIST.equals(role)) {
            patientButton.setVisibility(View.VISIBLE);
            therapistButton.setVisibility(View.VISIBLE);
        } else {
            therapistButton.setVisibility(View.INVISIBLE);
            patientButton.setVisibility(View.INVISIBLE);

            TextView noRoleText = findViewById(R.id.noAvailableRoleText);
            noRoleText.setVisibility(View.VISIBLE);
        }
    }

    private void getPatientPage() {
        Intent intent = new Intent(this, PatientActivity.class);
        startActivity(intent);
        Toast.makeText(getBaseContext(), "You have been logged in as patient",
                Toast.LENGTH_SHORT).show();
    }

    private void getTherapistPage() {
        Intent intent = new Intent(this, TherapistActivity.class);
        startActivity(intent);
        Toast.makeText(getBaseContext(), "You have been logged in as therapist",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_role_select, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id){
            case R.id.action_role_my_profile:
                // TODO: implement my profile page
                Intent profileIntent = new Intent(this, MyProfileActivity.class);
                startActivity(profileIntent);
                break;
            case R.id.action_role_web_login:
                callNfcScan();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (nfcAdapter != null) {
            // Disable sound or vibration if tag discovered (only API 19 onwards)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                nfcAdapter.enableReaderMode(this,
                        new NfcAdapter.ReaderCallback() {
                            @Override
                            public void onTagDiscovered(final Tag tag) {
                                // do nothing
                            }
                        },
                        NfcAdapter.FLAG_READER_NFC_A |
                                NfcAdapter.FLAG_READER_NFC_B |
                                NfcAdapter.FLAG_READER_NFC_F |
                                NfcAdapter.FLAG_READER_NFC_V |
                                NfcAdapter.FLAG_READER_NFC_BARCODE |
                                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                        null);
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            // Intentionally left empty to drop NFC events
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
    }

    private void callNfcScan() {
        Intent intent = new Intent(getApplicationContext(), NfcScanActivity.class);
        intent.putExtra("scanNfcPurpose", "webLogin");
        startActivity(intent);
    }

    private String roleSelect() {
        String jwtRole = null;

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
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/getallroles");
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

            int responseCode = conn.getResponseCode();

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
                    Log.d(TAG, "roleSelect() :: newJwt: " + newJwt);

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

                        // Get roles from JWT
                        byte[] claimsBytes = Base64.decode(claims, Base64.DEFAULT);
                        String claimsString = new String(claimsBytes, "UTF-8");
                        JSONObject jwtObj = new JSONObject(claimsString);
                        jwtRole = jwtObj.getString("Roles");
                        Log.d(TAG, "roleSelect() :: Roles: " + jwtRole);
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

        return jwtRole;
    }

    private class RoleSelectTask extends AsyncTask<String, Void, String> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(RoleSelectActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.loading_text));
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            return roleSelect();
        }

        @Override
        protected void onPostExecute(String jwtRole) {
            progressDialog.dismiss();
            if (jwtRole != null) {
                role = jwtRole;
                disableButtons();
            } else {
                Log.d(TAG, "RoleSelectTask() :: Authentication FAILED! " +
                        "JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }

    private String updateJwtRole(String newJwtRole) {
        String jwtRole = null;

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
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/selectrole");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'newJwtRole': '%s', 'deviceID': '%s', 'jwt': '%s'}",
                    newJwtRole, deviceID, jwt);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            int responseCode = conn.getResponseCode();

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
                    Log.d(TAG, "updateJwtRole() :: newJwt: " + newJwt);

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

                        // Get roles from JWT
                        byte[] claimsBytes = Base64.decode(claims, Base64.DEFAULT);
                        String claimsString = new String(claimsBytes, "UTF-8");
                        JSONObject jwtObj = new JSONObject(claimsString);
                        jwtRole = jwtObj.getString("Roles");
                        Log.d(TAG, "updateJwtRole() :: Roles: " + jwtRole);
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

        return jwtRole;
    }

    private class UpdateJwtRoleTask extends AsyncTask<String, Void, String> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(RoleSelectActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.loading_text));
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            return updateJwtRole(params[0]);
        }

        @Override
        protected void onPostExecute(String jwtRole) {
            progressDialog.dismiss();
            if (jwtRole != null) {
                if (ROLE_PATIENT.equals(jwtRole)) {
                    getPatientPage();
                } else if (ROLE_THERAPIST.equals(jwtRole)) {
                    getTherapistPage();
                }
            } else {
                Log.d(TAG, "UpdateJwtRoleTask() :: Authentication FAILED! " +
                        "JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }
}
