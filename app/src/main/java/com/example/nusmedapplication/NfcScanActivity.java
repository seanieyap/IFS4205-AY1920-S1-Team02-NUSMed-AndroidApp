package com.example.nusmedapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class NfcScanActivity extends AppCompatActivity {

    private TextView mText;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPendingIntent;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_nfc_scan);

        mText = (TextView) findViewById(R.id.text);
        mText.setText("Scan a tag");

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // TODO: handle if smartphone has nfc and ensure app has nfc permissions
        // Check if the smartphone has NFC
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported", Toast.LENGTH_LONG).show();
            finish();
        }

        // Check if NFC is enabled
        if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this, "Enable NFC before using the app",
                    Toast.LENGTH_LONG).show();
        }

        Toast.makeText(this, "NFC enabled",
                Toast.LENGTH_LONG).show();

        Intent nfcIntent = new Intent(this, getClass());
        nfcIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        nfcPendingIntent =
                PendingIntent.getActivity(this, 0, nfcIntent, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (nfcAdapter != null)
            nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, null, null);
    }

    @Override
    public void onNewIntent(Intent intent) {
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Toast.makeText(getBaseContext(), tagFromIntent.toString(), Toast.LENGTH_LONG).show();
        mText.setText("Tag ID: " + tagFromIntent.getId());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
    }

    // TODO: disable back navigation
}
