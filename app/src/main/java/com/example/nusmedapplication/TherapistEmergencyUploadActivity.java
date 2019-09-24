package com.example.nusmedapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TherapistEmergencyUploadActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    // TODO: get emergency patients list of the therapist from the server
    String[] patients = {
            "F0095679N",
            "F0194653X",
            "F0359848T",
            "F0398231L",
            "F0412392P"
    };

    String[] recordTypes = {
            RecordType.HEIGHT_MEASUREMENT,
            RecordType.WEIGHT_MEASUREMENT,
            RecordType.TEMPERATURE,
            RecordType.BLOOD_PRESSURE,
            RecordType.ECG,
            RecordType.MRI,
            RecordType.X_RAY,
            RecordType.GAIT
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_therapist_emergency_upload);

        // Getting the instance of Spinner and applying OnItemSelectedListener on it
        Spinner recordTypeSpinner = (Spinner) findViewById(R.id.therapistERecordTypeSpinner);
        recordTypeSpinner.setOnItemSelectedListener(this);

        // Creating the ArrayAdapter instance having the bank name list
        ArrayAdapter recordTypeAdapter = new ArrayAdapter(this,android.R.layout.simple_spinner_item, recordTypes);
        recordTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Setting the ArrayAdapter data on the Spinner
        recordTypeSpinner.setAdapter(recordTypeAdapter);

        // Getting the instance of Spinner and applying OnItemSelectedListener on it
        Spinner patientSpinner = (Spinner) findViewById(R.id.therapistEPatientSpinner);
        patientSpinner.setOnItemSelectedListener(this);

        // Creating the ArrayAdapter instance having the bank name list
        ArrayAdapter patientAdapter = new ArrayAdapter(this,android.R.layout.simple_spinner_item, patients);
        patientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Setting the ArrayAdapter data on the Spinner
        patientSpinner.setAdapter(patientAdapter);

        Button cancelButton = findViewById(R.id.therapistECancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    // Performing action onItemSelected and onNothing selected
    @Override
    public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
        // Set record content constraint
        TextView constraintText = findViewById(R.id.therapistERecordContentConstraintText);
        RecordType recordType = RecordType.getRecordType(arg0.getItemAtPosition(position).toString());
        if (recordType != null) {
            constraintText.setText(recordType.getConstraint());

            EditText contentInput = (EditText) findViewById(R.id.therapistEUploadContentField);
            Button fileButton = (Button) findViewById(R.id.therapistEUploadFileButton);

            if (recordType.isContent()) {
                fileButton.setVisibility(View.INVISIBLE);
                contentInput.setVisibility(View.VISIBLE);
            } else {
                contentInput.setVisibility(View.INVISIBLE);
                fileButton.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }
}
