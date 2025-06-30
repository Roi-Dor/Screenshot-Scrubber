package com.example.screenscrubberdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.screenscrubber.ScreenScrubberManager;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;
    private ScreenScrubberManager scrubberManager;
    private Button toggleButton;
    private Button testDataButton;
    private boolean isMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrubberManager = new ScreenScrubberManager(this);
        toggleButton = findViewById(R.id.toggleButton);
        testDataButton = findViewById(R.id.testDataButton);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMonitoring();
            }
        });

        testDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTestDataScreen();
            }
        });

        checkPermissions();
    }

    private void checkPermissions() {
        // Check if we have MANAGE_EXTERNAL_STORAGE permission (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "App needs storage management permission to delete original screenshots", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                return;
            }
        }

        // Check regular permissions
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10-12
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        } else { // Android 9 and below
            permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Storage management permission granted!", Toast.LENGTH_SHORT).show();
                    checkPermissions(); // Check other permissions
                } else {
                    Toast.makeText(this, "Storage management permission denied. Original screenshots won't be deleted.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Permissions granted! You can now start monitoring.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Permissions denied. App may not work properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void toggleMonitoring() {
        if (isMonitoring) {
            scrubberManager.stopMonitoring();
            toggleButton.setText("Start Monitoring");
            Toast.makeText(this, "Screenshot monitoring stopped", Toast.LENGTH_SHORT).show();
        } else {
            scrubberManager.startMonitoring();
            toggleButton.setText("Stop Monitoring");
            Toast.makeText(this, "Screenshot monitoring started", Toast.LENGTH_SHORT).show();
        }
        isMonitoring = !isMonitoring;
    }

    private void openTestDataScreen() {
        Intent intent = new Intent(this, TestDataActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scrubberManager != null) {
            scrubberManager.cleanup();
        }
    }
}