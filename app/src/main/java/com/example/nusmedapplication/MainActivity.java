package com.example.nusmedapplication;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.Manifest.permission.NFC;

public class MainActivity extends AppCompatActivity {

    private static final int NFC_PERMISSION_REQUEST_CODE = 200;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ensurePermissions();

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

        try {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);

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

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            // Drop NFC events
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            nfcAdapter.disableForegroundDispatch(this);
        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
        }
    }

    private void ensurePermissions() {
        if (!checkPermissions()) {
            Toast.makeText(getBaseContext(),
                    "No permissions... Requesting permissions...", Toast.LENGTH_LONG).show();
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), NFC);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{NFC}, NFC_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case NFC_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getBaseContext(),
                            "Permissions granted!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getBaseContext(),
                            "Permissions denied...", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }

    public void processLogin() {
        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setEnabled(false);

        if (!validateInput()) {
            onLoginFailed();
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this,
                R.style.Theme_AppCompat_DayNight_Dialog);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setMessage(getString(R.string.authenticating_text));
        progressDialog.show();

        if (authenticateInput()) {
            progressDialog.dismiss();
            onLoginSuccess();
        } else {
            progressDialog.dismiss();
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

        // TODO: Validate password length
        if (password.isEmpty() || password.length() < 5) {
            passwordInput.setError(getString(R.string.invalid_password));
        } else {
            passwordInput.setError(null);
            valid = true;
        }

        return valid;
    }

    public boolean authenticateInput() {
        // TODO: Implement authentication logic
        // hash pass, make login call to server
        // deal with timeout/ no internet connection
        boolean authenticated = false;

        EditText nricInput = findViewById(R.id.nricField);
        EditText passwordInput = findViewById(R.id.passwordField);
        String nric = nricInput.getText().toString();
        String password = passwordInput.getText().toString();

        if ("admin".equals(nric) && "admin".equals(password)) {
            authenticated = true;
        }

        return authenticated;
    }

    public void onLoginSuccess() {
        Toast.makeText(getBaseContext(), "Login successful", Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, NfcScanActivity.class);
        startActivity(intent);
    }

    public void onLoginFailed() {
        Toast.makeText(getBaseContext(), R.string.login_fail, Toast.LENGTH_LONG).show();

        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setEnabled(true);
    }

}
