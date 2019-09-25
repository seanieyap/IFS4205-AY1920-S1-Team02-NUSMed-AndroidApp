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

public class RoleSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_select);

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
            case R.id.action_role_logout:
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
