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

public class PatientUploadActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

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
        setContentView(R.layout.activity_patient_upload);

        // Getting the instance of Spinner and applying OnItemSelectedListener on it
        Spinner recordTypeSpinner = (Spinner) findViewById(R.id.patientRecordTypeSpinner);
        recordTypeSpinner.setOnItemSelectedListener(this);

        // Creating the ArrayAdapter instance having the bank name list
        ArrayAdapter recordTypeAdapter = new ArrayAdapter(this,android.R.layout.simple_spinner_item, recordTypes);
        recordTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Setting the ArrayAdapter data on the Spinner
        recordTypeSpinner.setAdapter(recordTypeAdapter);

        Button cancelButton = findViewById(R.id.patientCancelButton);
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
        TextView constraintText = findViewById(R.id.patientRecordContentConstraintText);
        RecordType recordType = RecordType.getRecordType(arg0.getItemAtPosition(position).toString());
        constraintText.setText(recordType.getConstraint());

        EditText contentInput = (EditText) findViewById(R.id.patientUploadContentField);
        Button fileButton = (Button) findViewById(R.id.patientUploadFileButton);

        if (recordType.isContent()) {
            fileButton.setVisibility(View.INVISIBLE);
            contentInput.setVisibility(View.VISIBLE);
        } else {
            contentInput.setVisibility(View.INVISIBLE);
            fileButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }
}

abstract class RecordType {
    public static final String HEIGHT_MEASUREMENT = "Height Measurement";
    public static final String WEIGHT_MEASUREMENT = "Weight Measurement";
    public static final String TEMPERATURE = "Temperature Reading";
    public static final String BLOOD_PRESSURE = "Blood Pressure Reading";
    public static final String ECG = "ECG Reading";
    public static final String MRI = "MRI";
    public static final String X_RAY = "X-ray";
    public static final String GAIT = "Gait";

    public abstract String getName();

    public abstract String getConstraint();

    public abstract int getPermissionFlag();

    public abstract boolean isContent();

    public static RecordType getRecordType(String type) {
        switch (type) {
            case HEIGHT_MEASUREMENT:
                return new HeightMeasurement();
            case WEIGHT_MEASUREMENT:
                return new WeightMeasurement();
            case TEMPERATURE:
                return new Temperature();
            case BLOOD_PRESSURE:
                return new BloodPressure();
            case ECG:
                return new ECG();
            case MRI:
                return new MRI();
            case X_RAY:
                return new Xray();
            case GAIT:
                return new Gait();
            default:
                return null;
        }
    }
}

class HeightMeasurement extends RecordType {

    @Override
    public String getName() {
        return HEIGHT_MEASUREMENT;
    }

    @Override
    public String getConstraint() {
        return "(Format: Centimetre, cm. Values: 0 - 280)";
    }

    @Override
    public int getPermissionFlag() {
        return 1;
    }

    @Override
    public boolean isContent() {
        return true;
    }

    public static boolean isContentValid(String content) {
        if (content.isEmpty()) {
            return false;
        }

        int contentDecimal = Integer.parseInt(content);
        if (contentDecimal >= 0 && contentDecimal <= 280) {
            return true;
        }
        return false;
    }
}

class WeightMeasurement extends RecordType {

    @Override
    public String getName() {
        return WEIGHT_MEASUREMENT;
    }

    @Override
    public String getConstraint() {
        return "(Format: Kilogram, kg. Values: 0 - 650)";
    }

    @Override
    public int getPermissionFlag() {
        return 2;
    }

    @Override
    public boolean isContent() {
        return true;
    }

    public static boolean isContentValid(String content) {
        if (content.isEmpty()) {
            return false;
        }

        int contentDecimal = Integer.parseInt(content);
        if (contentDecimal >= 0 && contentDecimal <= 650) {
            return true;
        }
        return false;
    }
}

class Temperature extends RecordType {

    @Override
    public String getName() {
        return TEMPERATURE;
    }

    @Override
    public String getConstraint() {
        return "(Format: Degree Celsius, °C. Values: 0 - 100)";
    }

    @Override
    public int getPermissionFlag() {
        return 4;
    }

    @Override
    public boolean isContent() {
        return true;
    }

    public static boolean isContentValid(String content) {
        if (content.isEmpty()) {
            return false;
        }

        int contentDecimal = Integer.parseInt(content);
        if (contentDecimal >= 0 && contentDecimal <= 100) {
            return true;
        }
        return false;
    }
}

class BloodPressure extends RecordType {

    @Override
    public String getName() {
        return BLOOD_PRESSURE;
    }

    @Override
    public String getConstraint() {
        return "(Format: Systolic Pressure (mmHG) over Diastolic Pressure (mmHG). Values: 0 - 250 / 0 - 250)";
    }

    @Override
    public int getPermissionFlag() {
        return 8;
    }

    @Override
    public boolean isContent() {
        return true;
    }

    public static boolean isContentValid(String content) {
        if (content.isEmpty()) {
            return false;
        }

        String[] contents = content.split("/");
        if (contents.length != 2) {
            return false;
        }

        int systolicPressure = Integer.parseInt(contents[0]);
        int distolicPressure = Integer.parseInt(contents[1]);
        if (systolicPressure >= 0 && systolicPressure <= 250 && distolicPressure >= 0 && distolicPressure <= 250) {
            return true;
        }
        return false;
    }
}

class ECG extends RecordType {

    @Override
    public String getName() {
        return ECG;
    }

    @Override
    public String getConstraint() {
        return "(Format: .txt. Max Size: 0.5MB)";
    }

    @Override
    public int getPermissionFlag() {
        return 16;
    }

    @Override
    public boolean isContent() {
        return false;
    }

    public static boolean isFileValid(String file) {
        return false;
    }
}

class MRI extends RecordType {

    @Override
    public String getName() {
        return MRI;
    }

    @Override
    public String getConstraint() {
        return "(Formats: .jpg, .jpeg, .png. Max Size: 5MB)";
    }

    @Override
    public int getPermissionFlag() {
        return 32;
    }

    @Override
    public boolean isContent() {
        return false;
    }

    public static boolean isFileValid(String file) {
        return false;
    }
}

class Xray extends RecordType {

    @Override
    public String getName() {
        return X_RAY;
    }

    @Override
    public String getConstraint() {
        return "(Formats: .jpg, .jpeg, .png. Max Size: 5MB)";
    }

    @Override
    public int getPermissionFlag() {
        return 64;
    }

    @Override
    public boolean isContent() {
        return false;
    }

    public static boolean isFileValid(String file) {
        return false;
    }
}

class Gait extends RecordType {

    @Override
    public String getName() {
        return GAIT;
    }

    @Override
    public String getConstraint() {
        return "(Formats: .txt, .mp4. Max Size: 0.5MB (for txt), 50MB (for mp4))";
    }

    @Override
    public int getPermissionFlag() {
        return 128;
    }

    @Override
    public boolean isContent() {
        return false;
    }

    public static boolean isFileValid(String file) {
        return false;
    }
}
