package com.example.nusmedapplication;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.NFC;

public class RoleSelectActivity extends AppCompatActivity {

    private static final int MULTIPLE_PERMISSION_REQUEST_CODE = 200;
    private static final String TAG = "DEBUG - RoleSelectActivity";

    private NfcAdapter nfcAdapter;

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
                getPatientPage();
            }
        });

        Button therapistButton = findViewById(R.id.roleTherapistButton);
        therapistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getTherapistPage();
            }
        });
    }

    public void getPatientPage() {
        Intent intent = new Intent(this, PatientActivity.class);
        startActivity(intent);
        Toast.makeText(getBaseContext(), "You have been logged in as patient", Toast.LENGTH_SHORT).show();
    }

    public void getTherapistPage() {
        Intent intent = new Intent(this, TherapistActivity.class);
        startActivity(intent);
        Toast.makeText(getBaseContext(), "You have been logged in as therapist", Toast.LENGTH_SHORT).show();
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
}
