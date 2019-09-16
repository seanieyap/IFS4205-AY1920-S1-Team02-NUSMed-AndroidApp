package com.example.nusmedapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class PatientUploadActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    String[] medicalTypes = {"Blood Pressure", "Heart Rate", "Temperature", "X-Ray", "MRI"};
    String[] diagnoses = {"Diagnosis 1", "Diagnosis 2", "Diagnosis 3", "Diagnosis 4", "Diagnosis 5"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_upload);

        // Getting the instance of Spinner and applying OnItemSelectedListener on it
        Spinner medicalTypeSpinner = (Spinner) findViewById(R.id.patientMedicalTypeSpinner);
        medicalTypeSpinner.setOnItemSelectedListener(this);

        // Creating the ArrayAdapter instance having the bank name list
        ArrayAdapter medicalTypeAdapter = new ArrayAdapter(this,android.R.layout.simple_spinner_item, medicalTypes);
        medicalTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Setting the ArrayAdapter data on the Spinner
        medicalTypeSpinner.setAdapter(medicalTypeAdapter);

        // Getting the instance of Spinner and applying OnItemSelectedListener on it
        Spinner diagnosisSpinner = (Spinner) findViewById(R.id.patientDiagnosisSpinner);
        diagnosisSpinner.setOnItemSelectedListener(this);

        // Creating the ArrayAdapter instance having the bank name list
        ArrayAdapter diagnosisAdapter = new ArrayAdapter(this,android.R.layout.simple_spinner_item, diagnoses);
        diagnosisAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Setting the ArrayAdapter data on the Spinner
        diagnosisSpinner.setAdapter(diagnosisAdapter);

        RadioButton dataRadioButton = (RadioButton) findViewById(R.id.patientUploadDataRadioButton);
        dataRadioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Set upload file button to be invisible
                Button fileButton = (Button) findViewById(R.id.patientUploadFileButton);
                fileButton.setVisibility(View.INVISIBLE);

                // Set upload data button to be visible
                EditText dataInput = (EditText) findViewById(R.id.patientUploadDataField);
                dataInput.setVisibility(View.VISIBLE);
            }
        });

        RadioButton fileRadioButton = (RadioButton) findViewById(R.id.patientUploadFileRadioButton);
        fileRadioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Set upload data button to be invisible
                EditText dataInput = (EditText) findViewById(R.id.patientUploadDataField);
                dataInput.setVisibility(View.INVISIBLE);

                // Set upload file button to be visible
                Button fileButton = (Button) findViewById(R.id.patientUploadFileButton);
                fileButton.setVisibility(View.VISIBLE);
            }
        });
    }

    // Performing action onItemSelected and onNothing selected
    @Override
    public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }
}
