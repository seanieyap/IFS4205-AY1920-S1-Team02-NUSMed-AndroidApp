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

public class PatientUploadActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final String TAG = "DEBUG - PatientUpload";

    public static final int RECORD_TITLE_MAX_LENGTH = 45;
    public static final int RECORD_DESCRIPTION_MAX_LENGTH = 120;

    public static final int HEIGHT_MIN_VALUE = 0;
    public static final int HEIGHT_MAX_VALUE = 280;
    public static final int WEIGHT_MIN_VALUE = 0;
    public static final int WEIGHT_MAX_VALUE = 650;
    public static final int TEMPERATURE_MIN_VALUE = 0;
    public static final int TEMPERATURE_MAX_VALUE = 100;
    public static final int BP_MIN_VALUE = 0;
    public static final int BP_MAX_VALUE = 250;

    public static final int FILE_SIZE_512KB = 524288;
    public static final int FILE_SIZE_5MB = 5242880;
    public static final int FILE_SIZE_50MB = 52428800;

    public static final int READ_REQUEST_CODE = 42;

    private int fileSize = 0;
    private String fileContent = "";

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
        final Spinner recordTypeSpinner = (Spinner) findViewById(R.id.patientRecordTypeSpinner);
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
                if (isPageEdited()) {
                    confirmCancel();
                } else {
                    finish();
                }
            }
        });

        Button uploadButton = findViewById(R.id.patientSubmitButton);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RecordUploadTask recordUploadTask = new RecordUploadTask();
                recordUploadTask.execute();
            }
        });

        // Check the length of record title
        final EditText titleInput = findViewById(R.id.patientRecordTitleField);
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
        final EditText descriptionInput = findViewById(R.id.patientRecordDescriptionField);
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

        Button fileButton = findViewById(R.id.patientUploadFileButton);
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
                //Log.i(TAG, "Uri: " + uri.toString());

                Spinner recordTypeSpinner = (Spinner) findViewById(R.id.patientRecordTypeSpinner);
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
        //Log.i(TAG, "Format: " + mimeType);
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
                    //Log.i(TAG, "Display Name: " + displayName);

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
                    //Log.i(TAG, "Size: " + size);

                    // Check if the size exceeds the limit
                    if (Integer.parseInt(size) > FILE_SIZE_512KB) {
                        Toast.makeText(getApplicationContext(), "File too large", Toast.LENGTH_SHORT).show();
                    } else {
                        // Display file name on the app to show ready to upload
                        TextView fileNameText = findViewById(R.id.patientFileNameText);
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
                        //Log.i(TAG, "Content: " + fileContent);
                    }
                }
            } catch (Exception e) {
                //Log.e(TAG, "An exception occurred...", e);
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
        //Log.i(TAG, "Format: " + mimeType);
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
                    //Log.i(TAG, "Display Name: " + displayName);

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
                    //Log.i(TAG, "Size: " + size);

                    // Check if the size exceeds the limit
                    if (Integer.parseInt(size) > FILE_SIZE_5MB) {
                        Toast.makeText(getApplicationContext(), "File too large", Toast.LENGTH_SHORT).show();
                    } else {
                        // Display file name on the app to show ready to upload
                        TextView fileNameText = findViewById(R.id.patientFileNameText);
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
                        //Log.i(TAG, "Content: " + fileContent);
                    }
                }
            } catch (Exception e) {
                //Log.e(TAG, "An exception occurred...", e);
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
        //Log.i(TAG, "Format: " + mimeType);
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
                    //Log.i(TAG, "Display Name: " + displayName);

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
                    //Log.i(TAG, "Size: " + size);

                    // Check if the size exceeds the limit
                    if (Integer.parseInt(size) > FILE_SIZE_5MB) {
                        Toast.makeText(getApplicationContext(), "File too large", Toast.LENGTH_SHORT).show();
                    } else {
                        // Display file name on the app to show ready to upload
                        TextView fileNameText = findViewById(R.id.patientFileNameText);
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
                        //Log.i(TAG, "Content: " + fileContent);
                    }
                }
            } catch (Exception e) {
                //Log.e(TAG, "An exception occurred...", e);
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
        //Log.i(TAG, "Format: " + mimeType);
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
                    //Log.i(TAG, "Display Name: " + displayName);

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
                    //Log.i(TAG, "Size: " + size);

                    // Check if the size exceeds the limit
                    if (((mimeType.equals("text/plain") || mimeType.equals("text/comma-separated-values")) && Integer.parseInt(size) > FILE_SIZE_512KB)
                            || (mimeType.equals("video/mp4") && Integer.parseInt(size) > FILE_SIZE_50MB)) {
                        Toast.makeText(getApplicationContext(), "File too large", Toast.LENGTH_SHORT).show();
                    } else {
                        // Display file name on the app to show ready to upload
                        TextView fileNameText = findViewById(R.id.patientFileNameText);
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
                        //Log.i(TAG, "Content: " + fileContent);
                    }
                }
            } catch (Exception e) {
                //Log.e(TAG, "An exception occurred...", e);
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
        TextView constraintText = findViewById(R.id.patientRecordContentConstraintText);
        RecordType recordType = RecordType.getRecordType(arg0.getItemAtPosition(position).toString());
        constraintText.setText(recordType.getConstraint());

        final EditText heightInput = (EditText) findViewById(R.id.patientUploadHeightField);
        final EditText weightInput = (EditText) findViewById(R.id.patientUploadWeightField);
        final EditText temperatureInput = (EditText) findViewById(R.id.patientUploadTemperatureField);
        final EditText sbpInput = (EditText) findViewById(R.id.patientUploadSBPField);
        final EditText dbpInput = (EditText) findViewById(R.id.patientUploadDBPField);
        TextView slashText = (TextView) findViewById(R.id.patientRecordBPSlashText);

        // Upload file button
        Button fileButton = (Button) findViewById(R.id.patientUploadFileButton);
        TextView fileNameText = (TextView) findViewById(R.id.patientFileNameText);

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
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }

    public boolean isPageEdited() {
        EditText titleInput = findViewById(R.id.patientRecordTitleField);
        if (!titleInput.getText().toString().isEmpty()) {
            return true;
        }

        EditText descriptionInput = findViewById(R.id.patientRecordDescriptionField);
        if (!descriptionInput.getText().toString().isEmpty()) {
            return true;
        }

        Spinner recordTypeSpinner = findViewById(R.id.patientRecordTypeSpinner);
        switch (recordTypeSpinner.getSelectedItem().toString()) {
            case RecordType.HEIGHT_MEASUREMENT:
                EditText heightInput = findViewById(R.id.patientUploadHeightField);
                if (!heightInput.getText().toString().equals("0")) {
                    return true;
                }
                break;
            case RecordType.WEIGHT_MEASUREMENT:
                EditText weightInput = findViewById(R.id.patientUploadWeightField);
                if (!weightInput.getText().toString().equals("0")) {
                    return true;
                }
                break;
            case RecordType.TEMPERATURE:
                EditText tempInput = findViewById(R.id.patientUploadTemperatureField);
                if (!tempInput.getText().toString().equals("0")) {
                    return true;
                }
                break;
            case RecordType.BLOOD_PRESSURE:
                EditText spInput = findViewById(R.id.patientUploadSBPField);
                EditText dpInput = findViewById(R.id.patientUploadDBPField);
                if (!spInput.getText().toString().equals("0") || !dpInput.getText().toString().equals("0")) {
                    return true;
                }
                break;
            case RecordType.ECG:
                TextView ecgFileText = findViewById(R.id.patientFileNameText);
                if ((ecgFileText.getVisibility() == View.VISIBLE) && !ecgFileText.getText().toString().isEmpty()) {
                    return true;
                }
                break;
            case RecordType.MRI:
                TextView mriFileText = findViewById(R.id.patientFileNameText);
                if ((mriFileText.getVisibility() == View.VISIBLE) && !mriFileText.getText().toString().isEmpty()) {
                    return true;
                }
                break;
            case RecordType.X_RAY:
                TextView xrayFileText = findViewById(R.id.patientFileNameText);
                if ((xrayFileText.getVisibility() == View.VISIBLE) && !xrayFileText.getText().toString().isEmpty()) {
                    return true;
                }
                break;
            case RecordType.GAIT:
                TextView gaitFileText = findViewById(R.id.patientFileNameText);
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

            URL url = new URL("https://ifs4205team2-1.comp.nus.edu.sg/api/record/patient/upload");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            // Get and check record information
            EditText titleInput = findViewById(R.id.patientRecordTitleField);
            String title = titleInput.getText().toString();
            if (!isValidTitle(title)) {
                return responseCode;
            }

            EditText descInput = findViewById(R.id.patientRecordDescriptionField);
            String desc = descInput.getText().toString();
            if (!isValidDescription(desc)) {
                return responseCode;
            }

            Spinner typeSpinner = findViewById(R.id.patientRecordTypeSpinner);
            String type = typeSpinner.getSelectedItem().toString();
            String content = "";
            String fileFullName = "";
            String fileName = "";
            String fileExtension = "";
            switch (type) {
                case RecordType.HEIGHT_MEASUREMENT:
                    EditText heightInput = findViewById(R.id.patientUploadHeightField);
                    content = heightInput.getText().toString();

                    if (!HeightMeasurement.isContentValid(content)) {
                        return responseCode;
                    }
                    break;
                case RecordType.WEIGHT_MEASUREMENT:
                    EditText weightInput = findViewById(R.id.patientUploadWeightField);
                    content = weightInput.getText().toString();

                    if (!WeightMeasurement.isContentValid(content)) {
                        return responseCode;
                    }
                    break;
                case RecordType.TEMPERATURE:
                    EditText tempInput = findViewById(R.id.patientUploadTemperatureField);
                    content = tempInput.getText().toString();

                    if (!Temperature.isContentValid(content)) {
                        return responseCode;
                    }
                    break;
                case RecordType.BLOOD_PRESSURE:
                    EditText spInput = findViewById(R.id.patientUploadSBPField);
                    content = spInput.getText().toString();
                    content += "/";
                    EditText bpInput = findViewById(R.id.patientUploadDBPField);
                    content += bpInput.getText().toString();

                    if (!BloodPressure.isContentValid(content)) {
                        return responseCode;
                    }
                    break;
                case RecordType.ECG:
                    TextView ecgNameText = findViewById(R.id.patientFileNameText);
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
                    TextView mriNameText = findViewById(R.id.patientFileNameText);
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
                    TextView xrayNameText = findViewById(R.id.patientFileNameText);
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
                    TextView gaitNameText = findViewById(R.id.patientFileNameText);
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

            String credentialsString = jwt + ":" + deviceID;
            //Log.d(TAG, "uploadRecord() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", "Bearer " + encodedCredentialsString);
            //Log.d(TAG, "uploadRecord() :: Authorization: Bearer " + encodedCredentialsString);

            String contentsString = String.format(
                    "{'title': '%s', 'description': '%s', 'type': '%s', 'content': '%s', 'fileName': '%s', 'fileExtension': '%s', 'fileSize': %d, 'fileContent': '%s'}",
                    encodedTitle, encodedDesc, type, content, encodedFileName, encodedFileExt, fileSize, fileContent);
            //Log.e(TAG, "uploadRecord() :: contentsString: " + contentsString);

            OutputStream os = conn.getOutputStream();
            byte[] contentsBytes = contentsString.getBytes(StandardCharsets.UTF_8);
            os.write(contentsBytes, 0, contentsBytes.length);

            responseCode = conn.getResponseCode();
            //Log.d(TAG, "uploadRecord() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    boolean validSig = UtilityFunctions.validateResponseAuth(getApplicationContext(),
                            conn.getHeaderField("Authorization"));

                    if (validSig) {
                        String newJwt = UtilityFunctions.getJwtFromHeader(
                                conn.getHeaderField("Authorization"));
                        UtilityFunctions.storeJwtToPref(getApplicationContext(), newJwt);
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
            //Log.e(TAG, "An exception occurred...", e);
        }
        return responseCode;
    }

    private class RecordUploadTask extends AsyncTask<String, Void, Integer> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PatientUploadActivity.this);
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
                    //Log.d(TAG, "RecordUploadTask() :: Authentication FAILED! JWT/deviceID might be invalid. Start AUTHENTICATE activity!");
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

        double contentDecimal = Double.parseDouble(content);
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

        double contentDecimal = Double.parseDouble(content);
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

        double contentDecimal = Double.parseDouble(content);
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

        if (contents[0].isEmpty() || contents[1].isEmpty()) {
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
        return "(Format: .txt, .csv. Max Size: 0.5MB)";
    }

    @Override
    public int getPermissionFlag() {
        return 16;
    }

    @Override
    public boolean isContent() {
        return false;
    }

    public static boolean isFileValid(String extension, int size) {
        if ((extension.equals(".txt") || extension.equals(".csv"))
                && size <= PatientUploadActivity.FILE_SIZE_512KB) {
            return true;
        }
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

    public static boolean isFileValid(String extension, int size) {
        if ((extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".png"))
                && size <= PatientUploadActivity.FILE_SIZE_5MB) {
            return true;
        }
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

    public static boolean isFileValid(String extension, int size) {
        if ((extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".png"))
                && size <= PatientUploadActivity.FILE_SIZE_5MB) {
            return true;
        }
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
        return "(Formats: .txt, .csv, .mp4. Max Size: 0.5MB (for txt & csv), 50MB (for mp4))";
    }

    @Override
    public int getPermissionFlag() {
        return 128;
    }

    @Override
    public boolean isContent() {
        return false;
    }

    public static boolean isFileValid(String extension, int size) {
        if (((extension.equals(".txt") || extension.equals(".csv")) && size <= PatientUploadActivity.FILE_SIZE_512KB)
                || (extension.equals(".mp4") && size <= PatientUploadActivity.FILE_SIZE_50MB)) {
            return true;
        }
        return false;
    }
}
