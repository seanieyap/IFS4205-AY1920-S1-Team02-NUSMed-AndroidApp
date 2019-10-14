package com.example.nusmedapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class TherapistActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_therapist);

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
}
