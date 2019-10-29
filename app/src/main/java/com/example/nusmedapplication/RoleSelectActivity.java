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

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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

        Intent intent = getIntent();
        role = intent.getStringExtra("role");
        //Log.d(TAG, "onCreate() :: Roles: " + role);

        RoleSelectTask roleSelectTask = new RoleSelectTask();
        roleSelectTask.execute();
    }

    /**
     * Sets patient or therapist buttons depending on the user's available roles.
     */
    private void setButtons(String role) {
        Button patientButton = findViewById(R.id.rolePatientButton);
        Button therapistButton = findViewById(R.id.roleTherapistButton);

        patientButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UpdateJwtRoleTask updateJwtRoleTask = new UpdateJwtRoleTask();
                updateJwtRoleTask.execute(ROLE_PATIENT);
            }
        });

        therapistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UpdateJwtRoleTask updateJwtRoleTask = new UpdateJwtRoleTask();
                updateJwtRoleTask.execute(ROLE_THERAPIST);
            }
        });

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
                Intent profileIntent = new Intent(this, MyProfileActivity.class);
                profileIntent.putExtra("role", "Not Selected");
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

    /**
     * Checks and requests for necessary permissions.
     */
    private void ensurePermissions() {
        if (!checkPermissions()) {
            requestPermissions();
        }
    }

    /**
     * Checks for necessary permissions.
     */
    private boolean checkPermissions() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), NFC);
        result ^= ContextCompat.checkSelfPermission(getApplicationContext(), CAMERA);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests for necessary permissions.
     */
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

    /**
     * Checks if device supports NFC and is enabled.
     */
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

    /**
     * Calls NfcScanActivity for the purpose of web login.
     */
    private void callNfcScan() {
        Intent intent = new Intent(getApplicationContext(), NfcScanActivity.class);
        intent.putExtra("scanNfcPurpose", "webLogin");
        startActivity(intent);
    }

    /**
     * Retrieves all available roles of the user from the web server.
     */
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

            String credentialsString = jwt + ":" + deviceID;
            //Log.d(TAG, "roleSelect() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + encodedCredentialsString);
            //Log.d(TAG, "roleSelect() :: Authorization: Bearer " + encodedCredentialsString);
            conn.connect();

            int responseCode = conn.getResponseCode();
            //Log.d(TAG, "roleSelect() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    boolean validSig = UtilityFunctions.validateResponseAuth(getApplicationContext(),
                            conn.getHeaderField("Authorization"));

                    if (validSig) {
                        String newJwt = UtilityFunctions.getJwtFromHeader(
                                conn.getHeaderField("Authorization"));
                        UtilityFunctions.storeJwtToPref(getApplicationContext(), newJwt);

                        jwtRole = UtilityFunctions.getRolesFromJwt(newJwt);
                        //Log.d(TAG, "roleSelect() :: Roles: " + jwtRole);
                    }

                    break;
                case 401:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            //Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return jwtRole;
    }

    /**
     * AsyncTask to retrieve all available roles of the user.
     */
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
                setButtons(role);
            } else {
                //Log.d(TAG, "RoleSelectTask() :: Authentication FAILED! JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }

    /**
     * Retrieves updated JWT with the updated user roles from the web server.
     */
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

            String credentialsString = jwt + ":" + deviceID + ":" + newJwtRole;
            //Log.d(TAG, "updateJwtRole() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + encodedCredentialsString);
            //Log.d(TAG, "updateJwtRole() :: Authorization: Bearer " + encodedCredentialsString);
            conn.connect();

            int responseCode = conn.getResponseCode();
            //Log.d(TAG, "updateJwtRole() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    boolean validSig = UtilityFunctions.validateResponseAuth(getApplicationContext(),
                            conn.getHeaderField("Authorization"));

                    if (validSig) {
                        String newJwt = UtilityFunctions.getJwtFromHeader(
                                conn.getHeaderField("Authorization"));
                        UtilityFunctions.storeJwtToPref(getApplicationContext(), newJwt);

                        jwtRole = UtilityFunctions.getRolesFromJwt(newJwt);
                        //Log.d(TAG, "updateJwtRole() :: Roles: " + jwtRole);
                    }

                    break;
                case 401:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            //Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return jwtRole;
    }

    /**
     * AsyncTask to retrieve updated JWT with the updated user roles.
     */
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
                //Log.d(TAG, "UpdateJwtRoleTask() :: Authentication FAILED! JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                startActivity(intent);
            }
        }
    }
}
