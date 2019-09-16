package com.example.nusmedapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PatientActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient);

        Button uploadButton = findViewById(R.id.patientUploadButton);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPatientUploadPage();
            }
        });
    }

    public void getPatientUploadPage() {
        Intent intent = new Intent(this, PatientUploadActivity.class);
        startActivity(intent);
    }
}
