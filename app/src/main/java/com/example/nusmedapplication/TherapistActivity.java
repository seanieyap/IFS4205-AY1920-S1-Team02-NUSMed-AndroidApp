package com.example.nusmedapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TherapistActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_therapist);

        Button uploadNormalButton = findViewById(R.id.therapistUploadNormalButton);
        uploadNormalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getTherapistNormalUploadPage();
            }
        });

        Button uploadEmergencyButton = findViewById(R.id.therapistUploadEmergencyButton);
        uploadEmergencyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getTherapistEmergencyUploadPage();
            }
        });
    }

    public void getTherapistNormalUploadPage() {
        Intent intent = new Intent(this, TherapistNormalUploadActivity.class);
        startActivity(intent);
    }

    public void getTherapistEmergencyUploadPage() {
        Intent intent = new Intent(this, TherapistEmergencyUploadActivity.class);
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
            case R.id.action_therapist_switch_role:
                // TODO: actions with the server to switch user role
                Intent roleIntent = new Intent(this, RoleSelectActivity.class);
                startActivity(roleIntent);
                break;
            case R.id.action_therapist_logout:
                // TODO: end the current session
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addCategory( Intent.CATEGORY_HOME );
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(mainIntent);
                Toast.makeText(getBaseContext(), "You have been logged out", Toast.LENGTH_SHORT).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // The following line is commented out to disable back press
        // super.onBackPressed();
    }
}
