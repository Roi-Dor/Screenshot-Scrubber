package com.example.screenscrubberdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.screenscrubber.ScreenScrubber;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;

    private ScreenScrubber screenScrubber;
    private Button toggleButton;
    private Button testDataButton;
    private Switch screenshotSwitch;
    private Switch photoSwitch;
    private TextView statusText;
    private TextView configText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity created");

        // Initialize views
        initializeViews();

        // Initialize ScreenScrubber
        try {
            screenScrubber = new ScreenScrubber(this);
            Log.d(TAG, "ScreenScrubber initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ScreenScrubber", e);
            Toast.makeText(this, "❌ Failed to initialize ScreenScrubber", Toast.LENGTH_LONG).show();
            return;
        }

        setupClickListeners();
        updateUI();
        checkPermissions();
    }

    private void initializeViews() {
        toggleButton = findViewById(R.id.toggleButton);
        testDataButton = findViewById(R.id.testDataButton);
        screenshotSwitch = findViewById(R.id.screenshotSwitch);
        photoSwitch = findViewById(R.id.photoSwitch);
        statusText = findViewById(R.id.statusText);
        configText = findViewById(R.id.configText);

        // Set default switch states
        screenshotSwitch.setChecked(true);
        photoSwitch.setChecked(true);

        Log.d(TAG, "Views initialized");
    }

    private void setupClickListeners() {
        toggleButton.setOnClickListener(v -> toggleProtection());
        testDataButton.setOnClickListener(v -> openTestDataScreen());

        // Switch listeners with proper validation
        screenshotSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateMonitoringOptions();
        });

        photoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateMonitoringOptions();
        });

        Log.d(TAG, "Click listeners set up");
    }

    private void toggleProtection() {
        Log.d(TAG, "Toggle protection called - current state: " + screenScrubber.isActive());

        if (screenScrubber.isActive()) {
            screenScrubber.stop();
            Toast.makeText(this, "🛡️ Protection disabled", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Protection stopped");
        } else {
            boolean screenshots = screenshotSwitch.isChecked();
            boolean photos = photoSwitch.isChecked();

            if (!screenshots && !photos) {
                Toast.makeText(this, "❌ Please enable at least one monitoring type", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Cannot start - no monitoring types selected");
                return;
            }

            Log.d(TAG, "Starting protection - Screenshots: " + screenshots + ", Photos: " + photos);
            boolean started = screenScrubber.start(screenshots, photos);

            if (started) {
                String message = "🛡️ Protection enabled - monitoring ";
                if (screenshots && photos) {
                    message += "screenshots & photos";
                } else if (screenshots) {
                    message += "screenshots only";
                } else {
                    message += "photos only";
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                Log.i(TAG, "Protection started successfully");
            } else {
                Toast.makeText(this, "❌ Failed to start protection", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Failed to start protection");
            }
        }
        updateUI();
    }

    private void updateMonitoringOptions() {
        boolean screenshots = screenshotSwitch.isChecked();
        boolean photos = photoSwitch.isChecked();

        Log.d(TAG, "Updating monitoring options - Screenshots: " + screenshots + ", Photos: " + photos + ", Active: " + screenScrubber.isActive());

        // Validate at least one option is selected
        if (!screenshots && !photos) {
            Toast.makeText(this, "❌ At least one monitoring type must be enabled", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Reverting switch - at least one must be enabled");

            // Revert the switch that was just unchecked
            if (!screenshotSwitch.isChecked()) {
                screenshotSwitch.setChecked(true);
            } else {
                photoSwitch.setChecked(true);
            }
            return;
        }

        // Only update if protection is active
        if (screenScrubber.isActive()) {
            try {
                screenScrubber.updateMonitoringOptions(screenshots, photos);

                String message = "📱 Updated monitoring: ";
                if (screenshots && photos) {
                    message += "Screenshots & Photos";
                } else if (screenshots) {
                    message += "Screenshots Only";
                } else {
                    message += "Photos Only";
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Monitoring options updated successfully");

            } catch (Exception e) {
                Log.e(TAG, "Error updating monitoring options", e);
                Toast.makeText(this, "❌ Error updating monitoring options", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(TAG, "Protection not active - monitoring options will be applied when started");
        }

        updateUI();
    }

    private void updateUI() {
        try {
            ScreenScrubber.MonitoringConfig config = screenScrubber.getMonitoringConfig();

            Log.d(TAG, "Updating UI - " + config.toString());

            // Update toggle button
            if (config.isActive) {
                toggleButton.setText("Stop Protection");
                toggleButton.setBackgroundColor(getColor(android.R.color.holo_red_light));
            } else {
                toggleButton.setText("Start Protection");
                toggleButton.setBackgroundColor(getColor(android.R.color.holo_green_light));
            }

            // Update status text with health indicator
            if (config.isActive) {
                if (config.isHealthy) {
                    statusText.setText("🛡️ ACTIVE - Images being monitored for sensitive data");
                    statusText.setTextColor(getColor(android.R.color.holo_green_dark));
                } else {
                    statusText.setText("⚠️ ACTIVE but UNHEALTHY - Check system status");
                    statusText.setTextColor(getColor(android.R.color.holo_orange_light));
                }
            } else {
                statusText.setText("⚪ INACTIVE - Images not protected");
                statusText.setTextColor(getColor(android.R.color.darker_gray));
            }

            // Update configuration text
            if (config.isActive) {
                StringBuilder configStr = new StringBuilder("📋 Configuration:\n");
                configStr.append("Screenshots: ").append(config.monitoringScreenshots ? "✅ ON" : "❌ OFF").append("\n");
                configStr.append("Photos: ").append(config.monitoringPhotos ? "✅ ON" : "❌ OFF").append("\n");
                configStr.append("Health: ").append(config.isHealthy ? "✅ OK" : "❌ ERROR");
                configText.setText(configStr.toString());
                configText.setVisibility(View.VISIBLE);
            } else {
                configText.setVisibility(View.GONE);
            }

            // Update switches to reflect current state when active
            if (config.isActive) {
                screenshotSwitch.setChecked(config.monitoringScreenshots);
                photoSwitch.setChecked(config.monitoringPhotos);
            }

            // Always keep switches enabled so users can configure
            screenshotSwitch.setEnabled(true);
            photoSwitch.setEnabled(true);

        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
            statusText.setText("❌ ERROR - Cannot get library status");
            statusText.setTextColor(getColor(android.R.color.holo_red_dark));
        }
    }

    private void openTestDataScreen() {
        Log.d(TAG, "Opening test data screen");
        try {
            Intent intent = new Intent(this, TestDataActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening test data screen", e);
            Toast.makeText(this, "❌ Error opening test screen", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkPermissions() {
        Log.d(TAG, "Checking permissions");

        // Check MANAGE_EXTERNAL_STORAGE for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.w(TAG, "MANAGE_EXTERNAL_STORAGE not granted");
                showStoragePermissionDialog();
                return;
            }
        }

        // Check regular permissions
        String[] permissions = getRequiredPermissions();
        List<String> missingPermissions = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            Log.w(TAG, "Missing permissions: " + missingPermissions);
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return;
        }

        Log.i(TAG, "All permissions granted");
        Toast.makeText(this, "✅ All permissions granted", Toast.LENGTH_SHORT).show();
    }

    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // Storage permissions based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        Log.d(TAG, "Required permissions: " + permissions);
        return permissions.toArray(new String[0]);
    }

    private void showStoragePermissionDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage("ScreenScrubber needs storage permission to:\n\n" +
                        "• Monitor new screenshots and photos\n" +
                        "• Save censored versions\n" +
                        "• Delete original screenshots with sensitive data\n\n" +
                        "This permission is required for the app to function properly.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "⚠️ Storage permission is required for full functionality", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Log.i(TAG, "Storage permission granted");
                Toast.makeText(this, "✅ Storage permission granted", Toast.LENGTH_SHORT).show();
                checkPermissions();
            } else {
                Log.w(TAG, "Storage permission denied");
                Toast.makeText(this, "⚠️ Storage permission needed for full functionality", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            List<String> denied = new ArrayList<>();

            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    denied.add(permissions[i]);
                }
            }

            if (denied.isEmpty()) {
                Log.i(TAG, "All requested permissions granted");
                Toast.makeText(this, "✅ Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Some permissions denied: " + denied);
                Toast.makeText(this, "⚠️ Some permissions denied - app may not work properly", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Activity resumed");
        if (screenScrubber != null) {
            updateUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Activity paused");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Activity destroyed - cleaning up");
        super.onDestroy();

        if (screenScrubber != null) {
            try {
                screenScrubber.cleanup();
                Log.i(TAG, "ScreenScrubber cleanup completed");
            } catch (Exception e) {
                Log.e(TAG, "Error during ScreenScrubber cleanup", e);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, "About").setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 1) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        ScreenScrubber.LibraryInfo info = ScreenScrubber.getLibraryInfo();

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🛡️ ScreenScrubber")
                .setMessage("Privacy Protection Library\n\n" +
                        "Features:\n" +
                        "• Automatic screenshot monitoring\n" +
                        "• Camera photo monitoring\n" +
                        "• Sensitive data detection (US & Israeli)\n" +
                        "• Automatic censoring\n" +
                        "• On-device processing\n\n" +
                        "Version: " + info.version + " (" + info.buildType + ")")
                .setPositiveButton("OK", null)
                .show();
    }
}