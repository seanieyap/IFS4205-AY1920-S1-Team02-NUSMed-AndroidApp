package com.example.nusmedapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;

public class TherapistUploadActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final String TAG = "DEBUG - TherapistUpload";

    private static final int RECORD_TITLE_MAX_LENGTH = 45;
    private static final int RECORD_DESCRIPTION_MAX_LENGTH = 120;

    private static final int HEIGHT_MIN_VALUE = 0;
    private static final int HEIGHT_MAX_VALUE = 280;
    private static final int WEIGHT_MIN_VALUE = 0;
    private static final int WEIGHT_MAX_VALUE = 650;
    private static final int TEMPERATURE_MIN_VALUE = 0;
    private static final int TEMPERATURE_MAX_VALUE = 100;
    private static final int BP_MIN_VALUE = 0;
    private static final int BP_MAX_VALUE = 250;

    private static final int FILE_SIZE_512KB = 524288;
    private static final int FILE_SIZE_5MB = 5242880;
    private static final int FILE_SIZE_50MB = 52428800;

    private static final int READ_REQUEST_CODE = 42;

    private int fileSize = 0;
    private String fileContent = "";

    private String patientPermissions = "";

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
        setContentView(R.layout.activity_therapist_upload);

        // Getting the instance of Spinner and applying OnItemSelectedListener on it
        final Spinner recordTypeSpinner = (Spinner) findViewById(R.id.therapistRecordTypeSpinner);
        recordTypeSpinner.setOnItemSelectedListener(this);

        // Creating the ArrayAdapter instance having the bank name list
        ArrayAdapter recordTypeAdapter = new ArrayAdapter(this,android.R.layout.simple_spinner_item, recordTypes);
        recordTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Setting the ArrayAdapter data on the Spinner
        recordTypeSpinner.setAdapter(recordTypeAdapter);

        // Getting the instance of Spinner and applying OnItemSelectedListener on it
        Spinner patientSpinner = (Spinner) findViewById(R.id.therapistPatientSpinner);
        patientSpinner.setOnItemSelectedListener(this);

        // Creating the ArrayAdapter instance having the bank name list
        ArrayAdapter patientAdapter = new ArrayAdapter(this,android.R.layout.simple_spinner_item, TherapistActivity.MY_PATIENTS);
        patientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Setting the ArrayAdapter data on the Spinner
        patientSpinner.setAdapter(patientAdapter);

        Button cancelButton = findViewById(R.id.therapistCancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isPageEdited()) {
                    confirmCancel();
                } else {
                    finish();
                }
            }
        });

        Button uploadButton = findViewById(R.id.therapistSubmitButton);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RecordUploadTask recordUploadTask = new RecordUploadTask();
                recordUploadTask.execute();
            }
        });

        // Check the length of record title
        final EditText titleInput = findViewById(R.id.therapistRecordTitleField);
        titleInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (titleInput.getText().length() == 0) {
                    titleInput.setError("Title cannot be empty");
                } else if (titleInput.getEditableText().length() > RECORD_TITLE_MAX_LENGTH) {
                    titleInput.setError("Title is too long");
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        //Check the length of record description
        final EditText descriptionInput = findViewById(R.id.therapistRecordDescriptionField);
        descriptionInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (descriptionInput.getText().length() == 0) {
                    descriptionInput.setError("Description cannot be empty");
                } else if (descriptionInput.getEditableText().length() > RECORD_DESCRIPTION_MAX_LENGTH) {
                    descriptionInput.setError("Description is too long");
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        Button fileButton = findViewById(R.id.therapistUploadFileButton);
        fileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
                // browser.
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                // Filter to show only images, using the image MIME data type.
                // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
                // To search for all documents available via installed storage providers,
                // it would be "*/*".
                intent.setType("*/*");
                switch (recordTypeSpinner.getSelectedItem().toString()) {
                    case RecordType.ECG:
                        intent.setType("text/*");
                        break;
                    case RecordType.MRI:
                        intent.setType("image/*");
                        break;
                    case RecordType.X_RAY:
                        intent.setType("image/*");
                        break;
                    case RecordType.GAIT:
                        String[] mimetypes = {"text/*", "video/mp4"};
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
                        break;
                }

                startActivityForResult(intent, READ_REQUEST_CODE);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());

                Spinner recordTypeSpinner = (Spinner) findViewById(R.id.therapistRecordTypeSpinner);
                switch (recordTypeSpinner.getSelectedItem().toString()) {
                    case RecordType.ECG:
                        validateECGMetaData(uri);
                        break;
                    case RecordType.MRI:
                        validateMRIMetaData(uri);
                        break;
                    case RecordType.X_RAY:
                        validateXRayMetaData(uri);
                        break;
                    case RecordType.GAIT:
                        validateGaitMetaData(uri);
                        break;
                }
            }
        }
    }

    public void validateECGMetaData(Uri uri) {

        // Get the file's MIME type from the URI, then
        // check the file format
        String mimeType = getContentResolver().getType(uri);
        Log.i(TAG, "Format: " + mimeType);
        if (!(mimeType.equals("text/plain") || mimeType.equals("text/comma-separated-values"))) {
            Toast.makeText(getApplicationContext(), "File format invalid", Toast.LENGTH_SHORT).show();
        } else {
            // The query, since it only applies to a single document, will only return
            // one row. There's no need to filter, sort, or select fields, since we want
            // all fields for one document.
            Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null, null);

            try {
                // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
                // "if there's anything to look at, look at it" conditionals.
                if (cursor != null && cursor.moveToFirst()) {

                    // Note it's called "Display Name".  This is
                    // provider-specific, and might not necessarily be the file name.
                    String displayName = cursor.getString(
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    Log.i(TAG, "Display Name: " + displayName);

                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    // If the size is unknown, the value stored is null.  But since an
                    // int can't be null in Java, the behavior is implementation-specific,
                    // which is just a fancy term for "unpredictable".  So as
                    // a rule, check if it's null before assigning to an int.  This will
                    // happen often:  The storage API allows for remote files, whose
                    // size might not be locally known.
                    String size = null;
                    if (!cursor.isNull(sizeIndex)) {
                        // Technically the column stores an int, but cursor.getString()
                        // will do the conversion automatically.
                        size = cursor.getString(sizeIndex);
                    } else {
                        size = "Unknown";
                    }
                    Log.i(TAG, "Size: " + size);

                    // Check if the size exceeds the limit
                    if (Integer.parseInt(size) > FILE_SIZE_512KB) {
                        Toast.makeText(getApplicationContext(), "File too large", Toast.LENGTH_SHORT).show();
                    } else {
                        // Display file name on the app to show ready to upload
                        TextView fileNameText = findViewById(R.id.therapistFileNameText);
                        fileNameText.setVisibility(View.VISIBLE);
                        fileNameText.setText(displayName);

                        // Retrieve the valid file size
                        fileSize = Integer.parseInt(size);

                        // Retrieve the valid file content
                        byte[] bytes = new byte[fileSize];
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        inputStream.read(bytes);
                        inputStream.close();

                        fileContent = Base64.encodeToString(bytes, Base64.DEFAULT);
                        Log.i(TAG, "Content: " + fileContent);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "An exception occurred...", e);
            } finally {
                assert cursor != null;
                cursor.close();
            }
        }
    }

    public void validateMRIMetaData(Uri uri) {

        // Get the file's MIME type from the URI, then
        // check the file format
        String mimeType = getContentResolver().getType(uri);
        Log.i(TAG, "Format: " + mimeType);
        if (!(mimeType.equals("image/jpeg") || mimeType.equals("image/jpg") || mimeType.equals("image/png"))) {
            Toast.makeText(getApplicationContext(), "File format invalid", Toast.LENGTH_SHORT).show();
        } else {
            // The query, since it only applies to a single document, will only return
            // one row. There's no need to filter, sort, or select fields, since we want
            // all fields for one document.
            Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null, null);

            try {
                // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
                // "if there's anything to look at, look at it" conditionals.
                if (cursor != null && cursor.moveToFirst()) {

                    // Note it's called "Display Name".  This is
                    // provider-specific, and might not necessarily be the file name.
                    String displayName = cursor.getString(
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    Log.i(TAG, "Display Name: " + displayName);

                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    // If the size is unknown, the value stored is null.  But since an
                    // int can't be null in Java, the behavior is implementation-specific,
                    // which is just a fancy term for "unpredictable".  So as
                    // a rule, check if it's null before assigning to an int.  This will
                    // happen often:  The storage API allows for remote files, whose
                    // size might not be locally known.
                    String size = null;
                    if (!cursor.isNull(sizeIndex)) {
                        // Technically the column stores an int, but cursor.getString()
                        // will do the conversion automatically.
                        size = cursor.getString(sizeIndex);
                    } else {
                        size = "Unknown";
                    }
                    Log.i(TAG, "Size: " + size);

                    // Check if the size exceeds the limit
                    if (Integer.parseInt(size) > FILE_SIZE_5MB) {
                        Toast.makeText(getApplicationContext(), "File too large", Toast.LENGTH_SHORT).show();
                    } else {
                        // Display file name on the app to show ready to upload
                        TextView fileNameText = findViewById(R.id.therapistFileNameText);
                        fileNameText.setVisibility(View.VISIBLE);
                        fileNameText.setText(displayName);

                        // Retrieve the valid file size
                        fileSize = Integer.parseInt(size);

                        // Retrieve the valid file content
                        byte[] bytes = new byte[fileSize];
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        inputStream.read(bytes);
                        inputStream.close();

                        fileContent = Base64.encodeToString(bytes, Base64.DEFAULT);
                        Log.i(TAG, "Content: " + fileContent);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "An exception occurred...", e);
            } finally {
                assert cursor != null;
                cursor.close();
            }
        }
    }

    public void validateXRayMetaData(Uri uri) {

        // Get the file's MIME type from the URI, then
        // check the file format
        String mimeType = getContentResolver().getType(uri);
        Log.i(TAG, "Format: " + mimeType);
        if (!(mimeType.equals("image/jpeg") || mimeType.equals("image/jpg") || mimeType.equals("image/png"))) {
            Toast.makeText(getApplicationContext(), "File format invalid", Toast.LENGTH_SHORT).show();
        } else {
            // The query, since it only applies to a single document, will only return
            // one row. There's no need to filter, sort, or select fields, since we want
            // all fields for one document.
            Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null, null);

            try {
                // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
                // "if there's anything to look at, look at it" conditionals.
                if (cursor != null && cursor.moveToFirst()) {

                    // Note it's called "Display Name".  This is
                    // provider-specific, and might not necessarily be the file name.
                    String displayName = cursor.getString(
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    Log.i(TAG, "Display Name: " + displayName);

                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    // If the size is unknown, the value stored is null.  But since an
                    // int can't be null in Java, the behavior is implementation-specific,
                    // which is just a fancy term for "unpredictable".  So as
                    // a rule, check if it's null before assigning to an int.  This will
                    // happen often:  The storage API allows for remote files, whose
                    // size might not be locally known.
                    String size = null;
                    if (!cursor.isNull(sizeIndex)) {
                        // Technically the column stores an int, but cursor.getString()
                        // will do the conversion automatically.
                        size = cursor.getString(sizeIndex);
                    } else {
                        size = "Unknown";
                    }
                    Log.i(TAG, "Size: " + size);

                    // Check if the size exceeds the limit
                    if (Integer.parseInt(size) > FILE_SIZE_5MB) {
                        Toast.makeText(getApplicationContext(), "File too large", Toast.LENGTH_SHORT).show();
                    } else {
                        // Display file name on the app to show ready to upload
                        TextView fileNameText = findViewById(R.id.therapistFileNameText);
                        fileNameText.setVisibility(View.VISIBLE);
                        fileNameText.setText(displayName);

                        // Retrieve the valid file size
                        fileSize = Integer.parseInt(size);

                        // Retrieve the valid file content
                        byte[] bytes = new byte[fileSize];
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        inputStream.read(bytes);
                        inputStream.close();

                        fileContent = Base64.encodeToString(bytes, Base64.DEFAULT);
                        Log.i(TAG, "Content: " + fileContent);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "An exception occurred...", e);
            } finally {
                assert cursor != null;
                cursor.close();
            }
        }
    }

    public void validateGaitMetaData(Uri uri) {

        // Get the file's MIME type from the URI, then
        // check the file format
        String mimeType = getContentResolver().getType(uri);
        Log.i(TAG, "Format: " + mimeType);
        if (!(mimeType.equals("text/plain") || mimeType.equals("text/comma-separated-values") || mimeType.equals("video/mp4"))) {
            Toast.makeText(getApplicationContext(), "File format invalid", Toast.LENGTH_SHORT).show();
        } else {
            // The query, since it only applies to a single document, will only return
            // one row. There's no need to filter, sort, or select fields, since we want
            // all fields for one document.
            Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null, null);

            try {
                // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
                // "if there's anything to look at, look at it" conditionals.
                if (cursor != null && cursor.moveToFirst()) {

                    // Note it's called "Display Name".  This is
                    // provider-specific, and might not necessarily be the file name.
                    String displayName = cursor.getString(
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    Log.i(TAG, "Display Name: " + displayName);

                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    // If the size is unknown, the value stored is null.  But since an
                    // int can't be null in Java, the behavior is implementation-specific,
                    // which is just a fancy term for "unpredictable".  So as
                    // a rule, check if it's null before assigning to an int.  This will
                    // happen often:  The storage API allows for remote files, whose
                    // size might not be locally known.
                    String size = null;
                    if (!cursor.isNull(sizeIndex)) {
                        // Technically the column stores an int, but cursor.getString()
                        // will do the conversion automatically.
                        size = cursor.getString(sizeIndex);
                    } else {
                        size = "Unknown";
                    }
                    Log.i(TAG, "Size: " + size);

                    // Check if the size exceeds the limit
                    if (((mimeType.equals("text/plain") || mimeType.equals("text/comma-separated-values")) && Integer.parseInt(size) > FILE_SIZE_512KB)
                            || (mimeType.equals("video/mp4") && Integer.parseInt(size) > FILE_SIZE_50MB)) {
                        Toast.makeText(getApplicationContext(), "File too large", Toast.LENGTH_SHORT).show();
                    } else {
                        // Display file name on the app to show ready to upload
                        TextView fileNameText = findViewById(R.id.therapistFileNameText);
                        fileNameText.setVisibility(View.VISIBLE);
                        fileNameText.setText(displayName);

                        // Retrieve the valid file size
                        fileSize = Integer.parseInt(size);

                        // Retrieve the valid file content
                        byte[] bytes = new byte[fileSize];
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        inputStream.read(bytes);
                        inputStream.close();

                        fileContent = Base64.encodeToString(bytes, Base64.DEFAULT);
                        Log.i(TAG, "Content: " + fileContent);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "An exception occurred...", e);
            } finally {
                assert cursor != null;
                cursor.close();
            }
        }
    }

    // Performing action onItemSelected and onNothing selected
    @Override
    public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
        // Set record content constraint
        TextView constraintText = findViewById(R.id.therapistRecordContentConstraintText);
        RecordType recordType = RecordType.getRecordType(arg0.getItemAtPosition(position).toString());

        // Check if it is record type spinner value changes, not patient spinner
        if (recordType != null) {
            constraintText.setText(recordType.getConstraint());

            final EditText heightInput = (EditText) findViewById(R.id.therapistUploadHeightField);
            final EditText weightInput = (EditText) findViewById(R.id.therapistUploadWeightField);
            final EditText temperatureInput = (EditText) findViewById(R.id.therapistUploadTemperatureField);
            final EditText sbpInput = (EditText) findViewById(R.id.therapistUploadSBPField);
            final EditText dbpInput = (EditText) findViewById(R.id.therapistUploadDBPField);
            TextView slashText = (TextView) findViewById(R.id.therapistRecordBPSlashText);

            // Upload file button
            Button fileButton = (Button) findViewById(R.id.therapistUploadFileButton);
            TextView fileNameText = (TextView) findViewById(R.id.therapistFileNameText);

            if (recordType.isContent()) {
                fileButton.setVisibility(View.INVISIBLE);
                fileNameText.setVisibility(View.INVISIBLE);

                if (recordType.getName().equals(RecordType.HEIGHT_MEASUREMENT)) {
                    weightInput.setVisibility(View.INVISIBLE);
                    temperatureInput.setVisibility(View.INVISIBLE);
                    sbpInput.setVisibility(View.INVISIBLE);
                    dbpInput.setVisibility(View.INVISIBLE);
                    slashText.setVisibility(View.INVISIBLE);

                    heightInput.setVisibility(View.VISIBLE);
                    heightInput.setText("0");

                    // Check the validity of the input
                    heightInput.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        }

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                            if (heightInput.getText().toString().isEmpty()) {
                                heightInput.setError("Content cannot be empty");
                            } else {
                                float value = Float.parseFloat(heightInput.getText().toString());
                                if (value > HEIGHT_MAX_VALUE || value < HEIGHT_MIN_VALUE) {
                                    heightInput.setError("Enter a valid content");
                                }
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                        }
                    });
                } else if (recordType.getName().equals(RecordType.WEIGHT_MEASUREMENT)) {
                    heightInput.setVisibility(View.INVISIBLE);
                    temperatureInput.setVisibility(View.INVISIBLE);
                    sbpInput.setVisibility(View.INVISIBLE);
                    dbpInput.setVisibility(View.INVISIBLE);
                    slashText.setVisibility(View.INVISIBLE);

                    weightInput.setVisibility(View.VISIBLE);
                    weightInput.setText("0");

                    // Check the validity of the input
                    weightInput.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        }

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                            if (weightInput.getText().toString().isEmpty()) {
                                weightInput.setError("Content cannot be empty");
                            } else {
                                float value = Float.parseFloat(weightInput.getText().toString());
                                if (value > WEIGHT_MAX_VALUE || value < WEIGHT_MIN_VALUE) {
                                    weightInput.setError("Enter a valid content");
                                }
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                        }
                    });
                } else if (recordType.getName().equals(RecordType.TEMPERATURE)) {
                    heightInput.setVisibility(View.INVISIBLE);
                    weightInput.setVisibility(View.INVISIBLE);
                    sbpInput.setVisibility(View.INVISIBLE);
                    dbpInput.setVisibility(View.INVISIBLE);
                    slashText.setVisibility(View.INVISIBLE);

                    temperatureInput.setVisibility(View.VISIBLE);
                    temperatureInput.setText("0");

                    // Check the validity of the input
                    temperatureInput.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        }

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                            if (temperatureInput.getText().toString().isEmpty()) {
                                temperatureInput.setError("Content cannot be empty");
                            } else {
                                float value = Float.parseFloat(temperatureInput.getText().toString());
                                if (value > TEMPERATURE_MAX_VALUE || value < TEMPERATURE_MIN_VALUE) {
                                    temperatureInput.setError("Enter a valid content");
                                }
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                        }
                    });
                }else if (recordType.getName().equals(RecordType.BLOOD_PRESSURE)) {
                    heightInput.setVisibility(View.INVISIBLE);
                    weightInput.setVisibility(View.INVISIBLE);
                    temperatureInput.setVisibility(View.INVISIBLE);

                    sbpInput.setVisibility(View.VISIBLE);
                    dbpInput.setVisibility(View.VISIBLE);
                    slashText.setVisibility(View.VISIBLE);
                    sbpInput.setText("0");
                    dbpInput.setText("0");

                    // Check the validity of the input
                    sbpInput.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        }

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                            if (sbpInput.getText().toString().isEmpty()) {
                                sbpInput.setError("Content cannot be empty");
                            } else {
                                int value = Integer.parseInt(sbpInput.getText().toString());
                                if (value > BP_MAX_VALUE || value < BP_MIN_VALUE) {
                                    sbpInput.setError("Enter a valid content");
                                }
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                        }
                    });

                    // Check the validity of the input
                    dbpInput.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        }

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                            if (dbpInput.getText().toString().isEmpty()) {
                                dbpInput.setError("Content cannot be empty");
                            } else {
                                int value = Integer.parseInt(dbpInput.getText().toString());
                                if (value > BP_MAX_VALUE || value < BP_MIN_VALUE) {
                                    dbpInput.setError("Enter a valid content");
                                }
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                        }
                    });
                }
            } else {
                heightInput.setVisibility(View.INVISIBLE);
                weightInput.setVisibility(View.INVISIBLE);
                temperatureInput.setVisibility(View.INVISIBLE);
                sbpInput.setVisibility(View.INVISIBLE);
                dbpInput.setVisibility(View.INVISIBLE);
                slashText.setVisibility(View.INVISIBLE);

                fileButton.setVisibility(View.VISIBLE);
                fileNameText.setVisibility(View.INVISIBLE);
            }
        } else {
            String patient = arg0.getItemAtPosition(position).toString();
            if (!patient.equals("")) {
                String patientNRIC = patient.split(" ")[0];

                GetPermissionsTask getPermissionsTask = new GetPermissionsTask();
                getPermissionsTask.execute(patientNRIC);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }

    public boolean isPageEdited() {
        EditText titleInput = findViewById(R.id.therapistRecordTitleField);
        if (!titleInput.getText().toString().isEmpty()) {
            return true;
        }

        EditText descriptionInput = findViewById(R.id.therapistRecordDescriptionField);
        if (!descriptionInput.getText().toString().isEmpty()) {
            return true;
        }

        Spinner recordTypeSpinner = findViewById(R.id.therapistRecordTypeSpinner);
        if (recordTypeSpinner.getSelectedItem() == null) {
            return false;
        }
        switch (recordTypeSpinner.getSelectedItem().toString()) {
            case RecordType.HEIGHT_MEASUREMENT:
                EditText heightInput = findViewById(R.id.therapistUploadHeightField);
                if (!heightInput.getText().toString().equals("0")) {
                    return true;
                }
                break;
            case RecordType.WEIGHT_MEASUREMENT:
                EditText weightInput = findViewById(R.id.therapistUploadWeightField);
                if (!weightInput.getText().toString().equals("0")) {
                    return true;
                }
                break;
            case RecordType.TEMPERATURE:
                EditText tempInput = findViewById(R.id.therapistUploadTemperatureField);
                if (!tempInput.getText().toString().equals("0")) {
                    return true;
                }
                break;
            case RecordType.BLOOD_PRESSURE:
                EditText spInput = findViewById(R.id.therapistUploadSBPField);
                EditText dpInput = findViewById(R.id.therapistUploadDBPField);
                if (!spInput.getText().toString().equals("0") || !dpInput.getText().toString().equals("0")) {
                    return true;
                }
                break;
            case RecordType.ECG:
                TextView ecgFileText = findViewById(R.id.therapistFileNameText);
                if ((ecgFileText.getVisibility() == View.VISIBLE) && !ecgFileText.getText().toString().isEmpty()) {
                    return true;
                }
                break;
            case RecordType.MRI:
                TextView mriFileText = findViewById(R.id.therapistFileNameText);
                if ((mriFileText.getVisibility() == View.VISIBLE) && !mriFileText.getText().toString().isEmpty()) {
                    return true;
                }
                break;
            case RecordType.X_RAY:
                TextView xrayFileText = findViewById(R.id.therapistFileNameText);
                if ((xrayFileText.getVisibility() == View.VISIBLE) && !xrayFileText.getText().toString().isEmpty()) {
                    return true;
                }
                break;
            case RecordType.GAIT:
                TextView gaitFileText = findViewById(R.id.therapistFileNameText);
                if ((gaitFileText.getVisibility() == View.VISIBLE) && !gaitFileText.getText().toString().isEmpty()) {
                    return true;
                }
                break;
        }

        return false;
    }

    public void confirmCancel() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle("Confirm Cancel");
        builder.setMessage("Are you sure you want to cancel the record upload? All the information entered in this page will be lost.");
        builder.setPositiveButton("YES",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    @Override
    public void onBackPressed() {
        if (isPageEdited()) {
            confirmCancel();
        } else {
            super.onBackPressed();
        }
    }

    private boolean isValidTitle(String title) {
        if (title.length() == 0 || title.length() > RECORD_TITLE_MAX_LENGTH) {
            return false;
        }
        return true;
    }

    private boolean isValidDescription(String desc) {
        if (desc.length() == 0 || desc.length() > RECORD_DESCRIPTION_MAX_LENGTH) {
            return false;
        }
        return true;
    }

    private int uploadRecord() {

        int responseCode = 409;

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    "secret_shared_prefs",
                    masterKeyAlias,
                    getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String deviceID = sharedPreferences.getString("deviceID", null);
            String jwt = sharedPreferences.getString("jwt", null);

            URL url = new URL("https://ifs4205team2-1.comp.nus.edu.sg/api/record/therapist/upload");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            // Get and check record information
            Spinner patientSpinner = findViewById(R.id.therapistPatientSpinner);
            String patient = patientSpinner.getSelectedItem().toString();
            if (patient.equals("")) {
                return responseCode;
            }
            String patientNRIC = patient.substring(0, patient.indexOf(" "));

            EditText titleInput = findViewById(R.id.therapistRecordTitleField);
            String title = titleInput.getText().toString();
            if (!isValidTitle(title)) {
                return responseCode;
            }

            EditText descInput = findViewById(R.id.therapistRecordDescriptionField);
            String desc = descInput.getText().toString();
            if (!isValidDescription(desc)) {
                return responseCode;
            }

            Spinner typeSpinner = findViewById(R.id.therapistRecordTypeSpinner);
            String type = "";
            Object itemSelected = typeSpinner.getSelectedItem();
            if (itemSelected == null) {
                return responseCode;
            } else {
                type = itemSelected.toString();
            }

            String content = "";
            String fileFullName = "";
            String fileName = "";
            String fileExtension = "";

            switch (type) {
                case RecordType.HEIGHT_MEASUREMENT:
                    EditText heightInput = findViewById(R.id.therapistUploadHeightField);
                    content = heightInput.getText().toString();

                    if (!HeightMeasurement.isContentValid(content)) {
                        return responseCode;
                    }
                    break;
                case RecordType.WEIGHT_MEASUREMENT:
                    EditText weightInput = findViewById(R.id.therapistUploadWeightField);
                    content = weightInput.getText().toString();

                    if (!WeightMeasurement.isContentValid(content)) {
                        return responseCode;
                    }
                    break;
                case RecordType.TEMPERATURE:
                    EditText tempInput = findViewById(R.id.therapistUploadTemperatureField);
                    content = tempInput.getText().toString();

                    if (!Temperature.isContentValid(content)) {
                        return responseCode;
                    }
                    break;
                case RecordType.BLOOD_PRESSURE:
                    EditText spInput = findViewById(R.id.therapistUploadSBPField);
                    content = spInput.getText().toString();
                    content += "/";
                    EditText bpInput = findViewById(R.id.therapistUploadDBPField);
                    content += bpInput.getText().toString();

                    if (!BloodPressure.isContentValid(content)) {
                        return responseCode;
                    }
                    break;
                case RecordType.ECG:
                    TextView ecgNameText = findViewById(R.id.therapistFileNameText);
                    fileFullName = ecgNameText.getText().toString();

                    if (ecgNameText.getVisibility() == View.INVISIBLE || fileFullName.isEmpty()) {
                        return responseCode;
                    }

                    fileName = fileFullName.substring(0, fileFullName.lastIndexOf("."));
                    fileExtension = fileFullName.substring(fileFullName.lastIndexOf("."));

                    if (!ECG.isFileValid(fileExtension, fileSize)) {
                        return responseCode;
                    }
                    break;
                case RecordType.MRI:
                    TextView mriNameText = findViewById(R.id.therapistFileNameText);
                    fileFullName = mriNameText.getText().toString();

                    if (mriNameText.getVisibility() == View.INVISIBLE || fileFullName.isEmpty()) {
                        return responseCode;
                    }

                    fileName = fileFullName.substring(0, fileFullName.lastIndexOf("."));
                    fileExtension = fileFullName.substring(fileFullName.lastIndexOf("."));

                    if (!MRI.isFileValid(fileExtension, fileSize)) {
                        return responseCode;
                    }
                    break;
                case RecordType.X_RAY:
                    TextView xrayNameText = findViewById(R.id.therapistFileNameText);
                    fileFullName = xrayNameText.getText().toString();

                    if (xrayNameText.getVisibility() == View.INVISIBLE || fileFullName.isEmpty()) {
                        return responseCode;
                    }

                    fileName = fileFullName.substring(0, fileFullName.lastIndexOf("."));
                    fileExtension = fileFullName.substring(fileFullName.lastIndexOf("."));

                    if (!Xray.isFileValid(fileExtension, fileSize)) {
                        return responseCode;
                    }
                    break;
                case RecordType.GAIT:
                    TextView gaitNameText = findViewById(R.id.therapistFileNameText);
                    fileFullName = gaitNameText.getText().toString();

                    if (gaitNameText.getVisibility() == View.INVISIBLE || fileFullName.isEmpty()) {
                        return responseCode;
                    }

                    fileName = fileFullName.substring(0, fileFullName.lastIndexOf("."));
                    fileExtension = fileFullName.substring(fileFullName.lastIndexOf("."));

                    if (!Gait.isFileValid(fileExtension, fileSize)) {
                        return responseCode;
                    }
                    break;
            }

            String encodedTitle = Base64.encodeToString(title.getBytes(), Base64.DEFAULT);
            String encodedDesc = Base64.encodeToString(desc.getBytes(), Base64.DEFAULT);
            String encodedFileName = Base64.encodeToString(fileName.getBytes(), Base64.DEFAULT);
            String encodedFileExt = Base64.encodeToString(fileExtension.getBytes(), Base64.DEFAULT);

            String jsonCredentialsString = String.format(
                    "{'deviceID': '%s', 'jwt': '%s', 'patientNRIC': '%s', 'title': '%s', 'description': '%s', 'type': '%s', 'content': '%s', 'fileName': '%s', 'fileExtension': '%s', 'fileSize': %d, 'fileContent': '%s'}",
                    deviceID, jwt, patientNRIC, encodedTitle, encodedDesc, type, content, encodedFileName, encodedFileExt, fileSize, fileContent);
            Log.e(TAG, jsonCredentialsString);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            responseCode = conn.getResponseCode();
            Log.d(TAG, "uploadRecord() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    // Read JWT from response
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String currentLine;
                    while ((currentLine = in.readLine()) != null) {
                        response.append(currentLine);
                    }
                    in.close();

                    String newJwt = response.toString().replace("\"", "");;
                    Log.d(TAG, "uploadRecord() :: newJwt: " + newJwt);

                    // Separate JWT into header, claims and signature
                    String[] newJwtParts = newJwt.split("\\.");
                    String claims = newJwtParts[0];
                    String signature = newJwtParts[1];

                    // Verify signature in JWT
                    byte[] modulusBytes = Base64.decode(getString(R.string.m), Base64.DEFAULT);
                    byte[] exponentBytes = Base64.decode(getString(R.string.e), Base64.DEFAULT);
                    BigInteger modulus = new BigInteger(1, modulusBytes);
                    BigInteger exponent = new BigInteger(1, exponentBytes);

                    RSAPublicKeySpec rsaPubKey = new RSAPublicKeySpec(modulus, exponent);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey pubKey = kf.generatePublic(rsaPubKey);

                    Signature signCheck = Signature.getInstance("SHA256withRSA");
                    signCheck.initVerify(pubKey);
                    signCheck.update(Base64.decode(claims, Base64.DEFAULT));
                    boolean validSig = signCheck.verify(Base64.decode(signature, Base64.DEFAULT));

                    if (validSig) {
                        // Store JWT in EncryptedSharedPreferences
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("jwt", newJwt);
                        editor.apply();
                    }

                    break;
                case 401:
                    break;
                case 403:
                    break;
                case 500:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "An exception occurred...", e);
        }
        return responseCode;
    }

    private class RecordUploadTask extends AsyncTask<String, Void, Integer> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(TherapistUploadActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage("Uploading...");
            progressDialog.show();
        }

        @Override
        protected Integer doInBackground(String... strings) {
            return uploadRecord();
        }

        @Override
        protected void onPostExecute(Integer responseCode) {
            progressDialog.dismiss();

            switch (responseCode) {
                case 200:
                    Toast.makeText(getApplicationContext(), "Record upload successful", Toast.LENGTH_SHORT).show();
                    finish();
                    break;
                case 401:
                    Log.d(TAG, "RecordUploadTask() :: Authentication FAILED! JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                    Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                            Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                    startActivity(intent);
                    break;
                case 409:
                    Toast.makeText(getApplicationContext(), "Invalid inputs", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(getApplicationContext(), "Record upload failed", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    private String getPermissionsBinary(int permissions) {
        String permissionsBinary = "";

        Gait gait = new Gait();
        if (permissions >= gait.getPermissionFlag()) {
            permissions -= gait.getPermissionFlag();
            permissionsBinary += "1";
        } else {
            permissionsBinary += "0";
        }

        Xray xray = new Xray();
        if (permissions >= xray.getPermissionFlag()) {
            permissions -= xray.getPermissionFlag();
            permissionsBinary += "1";
        } else {
            permissionsBinary += "0";
        }

        MRI mri = new MRI();
        if (permissions >= mri.getPermissionFlag()) {
            permissions -= mri.getPermissionFlag();
            permissionsBinary += "1";
        } else {
            permissionsBinary += "0";
        }

        ECG ecg = new ECG();
        if (permissions >= ecg.getPermissionFlag()) {
            permissions -= ecg.getPermissionFlag();
            permissionsBinary += "1";
        } else {
            permissionsBinary += "0";
        }

        BloodPressure bloodPressure = new BloodPressure();
        if (permissions >= bloodPressure.getPermissionFlag()) {
            permissions -= bloodPressure.getPermissionFlag();
            permissionsBinary += "1";
        } else {
            permissionsBinary += "0";
        }

        Temperature temperature = new Temperature();
        if (permissions >= temperature.getPermissionFlag()) {
            permissions -= temperature.getPermissionFlag();
            permissionsBinary += "1";
        } else {
            permissionsBinary += "0";
        }

        WeightMeasurement weightMeasurement = new WeightMeasurement();
        if (permissions >= weightMeasurement.getPermissionFlag()) {
            permissions -= weightMeasurement.getPermissionFlag();
            permissionsBinary += "1";
        } else {
            permissionsBinary += "0";
        }

        HeightMeasurement heightMeasurement = new HeightMeasurement();
        if (permissions >= heightMeasurement.getPermissionFlag()) {
            permissions -= heightMeasurement.getPermissionFlag();
            permissionsBinary += "1";
        } else {
            permissionsBinary += "0";
        }

        return permissionsBinary;
    }

    private void setAuthenticatedRecordTypes(String permissions) {
        int count = 0;
        for (int i = 0; i < permissions.length(); i++) {
            if (permissions.charAt(i) == '1') {
                count++;
            }
        }

        int ptr = 0;
        String[] newRecordTypes = new String[count];

        if (permissions.charAt(7) == '1') {
            newRecordTypes[ptr] = RecordType.HEIGHT_MEASUREMENT;
            ptr++;
        }
        if (permissions.charAt(6) == '1') {
            newRecordTypes[ptr] = RecordType.WEIGHT_MEASUREMENT;
            ptr++;
        }
        if (permissions.charAt(5) == '1') {
            newRecordTypes[ptr] = RecordType.TEMPERATURE;
            ptr++;
        }
        if (permissions.charAt(4) == '1') {
            newRecordTypes[ptr] = RecordType.BLOOD_PRESSURE;
            ptr++;
        }
        if (permissions.charAt(3) == '1') {
            newRecordTypes[ptr] = RecordType.ECG;
            ptr++;
        }
        if (permissions.charAt(2) == '1') {
            newRecordTypes[ptr] = RecordType.MRI;
            ptr++;
        }
        if (permissions.charAt(1) == '1') {
            newRecordTypes[ptr] = RecordType.X_RAY;
            ptr++;
        }
        if (permissions.charAt(0) == '1') {
            newRecordTypes[ptr] = RecordType.GAIT;
        }

        // Getting the instance of Spinner and applying OnItemSelectedListener on it
        final Spinner recordTypeSpinner = (Spinner) findViewById(R.id.therapistRecordTypeSpinner);
        recordTypeSpinner.setOnItemSelectedListener(this);

        // Creating the ArrayAdapter instance having the bank name list
        ArrayAdapter recordTypeAdapter = new ArrayAdapter(this,android.R.layout.simple_spinner_item, newRecordTypes);
        recordTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Setting the ArrayAdapter data on the Spinner
        recordTypeSpinner.setAdapter(recordTypeAdapter);
    }

    private int getPermissions(String patientNRIC) {

        int responseCode = 409;

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    "secret_shared_prefs",
                    masterKeyAlias,
                    getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String deviceID = sharedPreferences.getString("deviceID", null);
            String jwt = sharedPreferences.getString("jwt", null);

            URL url = new URL("https://ifs4205team2-1.comp.nus.edu.sg/api/record/therapist/getPermissions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonCredentialsString = String.format(
                    "{'deviceID': '%s', 'jwt': '%s', 'patientNRIC': '%s'}",
                    deviceID, jwt, patientNRIC);

            OutputStream os = conn.getOutputStream();
            byte[] jsonCredentialsBytes = jsonCredentialsString.getBytes(StandardCharsets.UTF_8);
            os.write(jsonCredentialsBytes, 0, jsonCredentialsBytes.length);

            responseCode = conn.getResponseCode();
            Log.d(TAG, "getPermissions() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    // Read JWT from response
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String currentLine;
                    while ((currentLine = in.readLine()) != null) {
                        response.append(currentLine);
                    }
                    in.close();

                    String results = new String(Base64.decode(response.toString(), Base64.DEFAULT));
                    Log.d(TAG, "getPermissions() :: permissions: " + results);

                    patientPermissions = getPermissionsBinary(Integer.parseInt(results));
                    Log.d(TAG, "getPermissions() :: permissionsBinary: " + patientPermissions);

                    break;
                case 401:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "An exception occurred...", e);
        }
        return responseCode;
    }

    private class GetPermissionsTask extends AsyncTask<String, Void, Integer> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(TherapistUploadActivity.this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.loading_text));
            progressDialog.show();
        }

        @Override
        protected Integer doInBackground(String... strings) {
            return getPermissions(strings[0]);
        }

        @Override
        protected void onPostExecute(Integer responseCode) {
            progressDialog.dismiss();

            switch (responseCode) {
                case 200:
                    setAuthenticatedRecordTypes(patientPermissions);
                    break;
                case 401:
                    Log.d(TAG, "GetPermissionsTask() :: Authentication FAILED! JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
                    Toast.makeText(getBaseContext(), R.string.reauthentication_fail,
                            Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(getApplicationContext(), AuthenticateActivity.class);
                    startActivity(intent);
                    break;
                default:
                    Toast.makeText(getApplicationContext(), "Failed to get patient permissions", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
}
