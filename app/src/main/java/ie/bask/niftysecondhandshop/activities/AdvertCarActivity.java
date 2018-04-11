package ie.bask.niftysecondhandshop.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.michaelmuenzer.android.scrollablennumberpicker.ScrollableNumberPicker;

import java.util.UUID;

import ie.bask.niftysecondhandshop.R;
import ie.bask.niftysecondhandshop.models.AdvertCar;

public class AdvertCarActivity extends Base {

    private AutoCompleteTextView carMake;
    private EditText carModel;
    private ScrollableNumberPicker carYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advert_car);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialising widgets
        advertImage = findViewById(R.id.advertImage);
        carMake = findViewById(R.id.carMake);
        carModel = findViewById(R.id.carModel);
        carYear = findViewById(R.id.snp_carYear);
        snp_horizontal = findViewById(R.id.snp_horizontal);
        priceManual = findViewById(R.id.priceManual);
        autoCompleteCounty = findViewById(R.id.autoCompleteCounty);
        productDetails = findViewById(R.id.productDetails);
        submitButton = findViewById(R.id.submitButton);
        progressDialog = new ProgressDialog(this);

        // Load string-array from resources to give suggestions
        // to the user when they start typing
        ArrayAdapter<String> arrayAdapterCarMakes = new ArrayAdapter<>(AdvertCarActivity.this, android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.carMakes));
        carMake.setAdapter(arrayAdapterCarMakes);
        // Show suggestions after 1 symbol is typed
        carMake.setThreshold(1);

        // Set max input length of carMake to 20 chars
        InputFilter[] filter = new InputFilter[1];
        filter[0] = new InputFilter.LengthFilter(20);
        carMake.setFilters(filter);

        filter[0] = new InputFilter.LengthFilter(20);
        carModel.setFilters(filter);

        filter[0] = new InputFilter.LengthFilter(6);
        priceManual.setFilters(filter);

        filter[0] = new InputFilter.LengthFilter(9);
        autoCompleteCounty.setFilters(filter);

        filter[0] = new InputFilter.LengthFilter(50);
        productDetails.setFilters(filter);

        // Load string-array from resources to give suggestions
        // to the user when they start typing
        ArrayAdapter<String> arrayAdapterCounties = new ArrayAdapter<>(AdvertCarActivity.this, android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.counties));
        autoCompleteCounty.setAdapter(arrayAdapterCounties);
        // Show suggestions after 1 symbol is typed
        autoCompleteCounty.setThreshold(1);

        // Getting the reference of Users node
        databaseUsers = FirebaseDatabase.getInstance().getReference("users");

        // Store current user id
        String currentUserId = firebaseAuth.getCurrentUser().getUid();

        // Get value of county for current user from databaseUsers
        databaseUsers.child(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                try {
                    String county = (String) dataSnapshot.child("county").getValue();
                    autoCompleteCounty.setText(county);
                }
                // Catch NullPointerException if the user is logged in with Google
                // therefore meaning there will be no value for county
                catch (NullPointerException nullPointer) {
                    autoCompleteCounty.setText(null);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.v("MyLogs", "Error reading data");
            }
        });

        permissionCheck();
        takePhoto();

        // Initialise Firebase Storage
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        // Initialising Google API client
        mGoogleSignInClient = createGoogleClient();
    }


    public void submitButtonPressed(View view) {

        // Get input from widgets
        final String make = carMake.getText().toString().trim();
        final String model = carModel.getText().toString().trim();
        final int year = carYear.getValue();
        final double price;
        final String location = autoCompleteCounty.getText().toString().trim();
        final String details = productDetails.getText().toString().trim();

        // Use the number picker if manual price is empty, default value of np is 0
        if (priceManual.getText().toString().isEmpty()) {
            price = (double) snp_horizontal.getValue();
        } else {
            price = Double.parseDouble(priceManual.getText().toString());
        }

        // Get the URI of captured image
        final String imageUri = getImageUri(bitmap);

        // Check if the user entered an existing county
        autoCompleteCounty.setValidator(new AutoCompleteTextView.Validator() {
            @Override
            public boolean isValid(CharSequence text) {
                for (int j = 0; j < getResources().getStringArray(R.array.counties).length; j++) {
                    String currentElement = getResources().getStringArray(R.array.counties)[j];
                    if (location.equals(currentElement)) {
                        Log.v("MyLogs", "FOUND COUNTY IN ARRAY!");
                        return true;
                    }
                }
                return false;
            }

            @Override
            public CharSequence fixText(CharSequence invalidText) {
                return null;
            }
        });
        autoCompleteCounty.performValidation();

        // Check if there are empty fields and set errors to alert the user
        if (TextUtils.isEmpty(make)) {
            carMake.setError("Car make is required!");
            carMake.requestFocus();
        } else if (TextUtils.isEmpty(model)) {
            carModel.setError("Car model is required");
            carModel.requestFocus();
        } else if (TextUtils.isEmpty(location) || !autoCompleteCounty.getValidator().isValid(location)) {
            autoCompleteCounty.setError("Empty or invalid county!");
            autoCompleteCounty.requestFocus();
        } else if (TextUtils.isEmpty(details)) {
            productDetails.setError("Details field is required!");
            productDetails.requestFocus();
        }
        // If none of the field are empty
        else {
            // Start progress dialog
            progressDialog.setMessage("Uploading advert...");
            progressDialog.show();

            // Get reference to Firebase Storage and put photo into images folder
            final StorageReference ref = storageReference.child("images/" + UUID.randomUUID().toString());
            ref.putFile(Uri.parse(imageUri)).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // Close progress dialog
                    progressDialog.dismiss();

                    String downloadURL = String.valueOf(taskSnapshot.getDownloadUrl());
                    // Create a new advert with the data
                    newAdvertCar(new AdvertCar(downloadURL, make, model, year, price, location, details));
                    Log.v("MyLogs", "Submit pressed! Data: 1) Make: " + model + " (2) Model: " + model + " (3) Year: " + year + " (4) Price: " + price +
                            " (5) Location: " + location + " (6) Details: " + details);
                }
            });
        }
    }


    /**
     * Receive captured image from camera and store it as Bitmap
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_PIC_REQUEST && resultCode == RESULT_OK) {
            bitmap = (Bitmap) data.getExtras().get("data");
            advertImage.setImageBitmap(bitmap);
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
    }

}
