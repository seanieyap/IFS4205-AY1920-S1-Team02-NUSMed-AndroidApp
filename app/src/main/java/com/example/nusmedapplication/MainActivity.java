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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.NFC;

public class MainActivity extends AppCompatActivity {

    private static final int MULTIPLE_PERMISSION_REQUEST_CODE = 200;
    private static final String TAG = "MainActivity";

    private NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ensurePermissions();

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

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    "secret_shared_prefs",
                    masterKeyAlias,
                    getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String retrievedDeviceID = sharedPreferences.getString("deviceID", null);

            if (retrievedDeviceID == null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                String deviceID = UUID.randomUUID().toString();
                editor.putString("deviceID", deviceID);
                editor.apply();
            }

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }

        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                processLogin();
            }
        });
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

    public void processLogin() {
        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setEnabled(false);

        if (validateInput()) {
            // TODO: Fix error causing next activity to not be shown
            //onLoginSuccess();
            LoginTask loginTask = new LoginTask();
            loginTask.execute();
        } else {
            onLoginFailed();
        }
    }

    public boolean validateInput() {
        boolean valid = false;

        EditText nricInput = findViewById(R.id.nricField);
        EditText passwordInput = findViewById(R.id.passwordField);
        String nric = nricInput.getText().toString();
        String password = passwordInput.getText().toString();

        // Regex to check if string is alphanumeric
        String regex = "^[a-zA-Z0-9]+$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(nric);

        if (nric.isEmpty() || !matcher.matches()) {
            nricInput.setError(getString(R.string.invalid_nric));
        } else {
            nricInput.setError(null);
            valid = true;
        }

        if (password.isEmpty() || password.length() < 12) {
            passwordInput.setError(getString(R.string.invalid_password));
        } else {
            passwordInput.setError(null);
            valid = true;
        }

        return valid;
    }

    public boolean authenticateInput() {
        // TODO: Remove unnecessary logs
        boolean authenticated = false;

        EditText nricInput = findViewById(R.id.nricField);
        EditText passwordInput = findViewById(R.id.passwordField);
        String nric = nricInput.getText().toString();
        String password = passwordInput.getText().toString();
        try {
            // TODO: Remove unused URL link
            //URL url = new URL("http://192.168.1.8:44320/api/account/authenticate/password");
            URL url = new
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/authenticate/password");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format("{'nric': '%s', 'password': '%s'}",
                    nric, password);
            Log.i(TAG, jsonCredentialsString);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            int responseCode = conn.getResponseCode();
            Log.i(TAG, Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    authenticated = true;
                    break;
                case 401:
                    break;
                default:
                    break;
            }

            /*BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }*/

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return authenticated;
    }

    public void onLoginSuccess() {
        Toast.makeText(getBaseContext(), "Login successful", Toast.LENGTH_LONG).show();

        Intent intent = new Intent(getApplicationContext(), NfcScanActivity.class);
        startActivity(intent);
    }

    public void onLoginFailed() {
        Toast.makeText(getBaseContext(), R.string.login_fail, Toast.LENGTH_LONG).show();

        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setEnabled(true);
    }

    @Override
    public void onBackPressed() {
        // The following line is commented out to disable back press
        // super.onBackPressed();
    }

    private class LoginTask extends AsyncTask<String, Void, Boolean> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.authenticating_text));
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            return authenticateInput();
        }

        @Override
        protected void onPostExecute(Boolean authenticated) {
            if (authenticated) {
                progressDialog.dismiss();
                onLoginSuccess();
            } else {
                progressDialog.dismiss();
                onLoginFailed();
            }
        }
    }

}
