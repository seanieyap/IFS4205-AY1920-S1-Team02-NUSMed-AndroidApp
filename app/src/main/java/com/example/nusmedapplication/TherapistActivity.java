package com.example.nusmedapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

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
    public void onBackPressed() {
        // The following line is commented out to disable back press
        // super.onBackPressed();
    }
}
