package com.example.nusmedapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

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
    }

    public void getTherapistPage() {
        Intent intent = new Intent(this, TherapistActivity.class);
        startActivity(intent);
    }
}
