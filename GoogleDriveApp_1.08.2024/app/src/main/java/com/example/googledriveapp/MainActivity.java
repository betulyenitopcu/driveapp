package com.example.googledriveapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_AUTHORIZATION = 1001;
    private GoogleSignInClient googleSignInClient;
    private Drive googleDriveService;
    private ProgressBar loadingProgressBar;
    private ListView fileListView;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadingProgressBar = findViewById(R.id.loading_progress_bar);
        fileListView = findViewById(R.id.file_list_view);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            initializeDriveService(account);
            listFiles();
        } else {
            // Giriş yapılmamışsa, LoginActivity'ye yönlendir
            // Intent intent = new Intent(this, LoginActivity.class);
            // startActivity(intent);
            // finish();
            Log.e(TAG, "No account signed in");
        }
    }

    private void initializeDriveService(GoogleSignInAccount account) {
        try {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(account.getAccount());

            googleDriveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName("Your Application Name")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error initializing Drive service", e);
        }
    }

    private void listFiles() {
        new Thread(() -> {
            List<String> fileNames = new ArrayList<>();
            try {
                FileList result = googleDriveService.files().list()
                        .setPageSize(10)
                        .setFields("nextPageToken, files(id, name)")
                        .execute();
                List<File> files = result.getFiles();
                if (files == null || files.isEmpty()) {
                    fileNames.add("No files found.");
                } else {
                    for (File file : files) {
                        fileNames.add(file.getName() + " (" + file.getId() + ")");
                    }
                }
            } catch (UserRecoverableAuthIOException e) {
                Log.e(TAG, "User needs to authorize access", e);
                startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
            } catch (IOException e) {
                Log.e(TAG, "Unable to list files", e);
                fileNames.add("Error: " + e.getMessage());
            }

            runOnUiThread(() -> {
                loadingProgressBar.setVisibility(View.GONE);
                fileListView.setVisibility(View.VISIBLE);
                adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fileNames);
                fileListView.setAdapter(adapter);
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_AUTHORIZATION) {
            if (resultCode == RESULT_OK) {
                // Retry listing files after authorization
                listFiles();
            } else {
                Log.e(TAG, "Authorization failed");
            }
        }
    }
}
