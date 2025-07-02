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
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.screenscrubber.ScreenScrubber;
import com.example.screenscrubber.NotificationHelper;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;

    private ScreenScrubber screenScrubber;
    private Button toggleButton;
    private Button testDataButton;
    private TextView statusText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        toggleButton = findViewById(R.id.toggleButton);
        testDataButton = findViewById(R.id.testDataButton);
        statusText = findViewById(R.id.statusText);

        // Initialize ScreenScrubber with cleaner API
        screenScrubber = new ScreenScrubber(this);

        setupClickListeners();
        updateUI();
        checkPermissions();

    }

    private void setupClickListeners() {
        toggleButton.setOnClickListener(v -> toggleProtection());
        testDataButton.setOnClickListener(v -> openTestDataScreen());
    }

    private void toggleProtection() {
        if (screenScrubber.isActive()) {
            screenScrubber.stop();
            Toast.makeText(this, "🛡️ Protection disabled", Toast.LENGTH_SHORT).show();
        } else {
            boolean started = screenScrubber.start();
            if (started) {
                Toast.makeText(this, "🛡️ Protection enabled - your screenshots are now monitored", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Failed to start protection", Toast.LENGTH_SHORT).show();
            }
        }
        updateUI();
    }

    private void updateUI() {
        if (screenScrubber.isActive()) {
            toggleButton.setText("Stop Protection");
            toggleButton.setBackgroundColor(getColor(android.R.color.holo_red_light));
            statusText.setText("🛡️ ACTIVE - Screenshots being monitored for sensitive data");
            statusText.setTextColor(getColor(android.R.color.holo_green_dark));
        } else {
            toggleButton.setText("Start Protection");
            toggleButton.setBackgroundColor(getColor(android.R.color.holo_green_light));
            statusText.setText("⚪ INACTIVE - Screenshots not protected");
            statusText.setTextColor(getColor(android.R.color.darker_gray));
        }
    }

    private void openTestDataScreen() {
        Intent intent = new Intent(this, TestDataActivity.class);
        startActivity(intent);
    }

    private void checkPermissions() {
        // Check MANAGE_EXTERNAL_STORAGE for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionDialog();
                return;
            }
        }

        // Check regular permissions
        String[] permissions = getRequiredPermissions();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
                return;
            }
        }

        Toast.makeText(this, "✅ All permissions granted", Toast.LENGTH_SHORT).show();
    }

    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // Storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS); // ADD THIS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        return permissions.toArray(new String[0]);
    }


    private void showStoragePermissionDialog() {
        Toast.makeText(this, "Storage permission needed to manage screenshots", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Toast.makeText(this, "✅ Storage permission granted", Toast.LENGTH_SHORT).show();
                checkPermissions();
            } else {
                Toast.makeText(this, "⚠️ Storage permission needed for full functionality", Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "✅ Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ Some permissions denied - app may not work properly", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screenScrubber != null) {
            screenScrubber.cleanup();
        }
    }
}