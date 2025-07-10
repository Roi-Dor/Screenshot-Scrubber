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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.screenscrubber.ScreenScrubber;
import com.example.screenscrubber.NotificationHelper;
import com.example.screenscrubber.ScreenScrubberManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;

    private ScreenScrubber screenScrubber;
    private Button toggleButton;
    private Button testDataButton;
    private Button statsButton;
    private Button scanRecentButton;
    private Switch screenshotSwitch;
    private Switch photoSwitch;
    private TextView statusText;
    private TextView configText;
    private TextView statsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        initializeViews();

        // Initialize ScreenScrubber
        screenScrubber = new ScreenScrubber(this);

        setupClickListeners();
        updateUI();
        checkPermissions();
    }

    private void initializeViews() {
        toggleButton = findViewById(R.id.toggleButton);
        testDataButton = findViewById(R.id.testDataButton);
        statsButton = findViewById(R.id.statsButton);
        scanRecentButton = findViewById(R.id.scanRecentButton);
        screenshotSwitch = findViewById(R.id.screenshotSwitch);
        photoSwitch = findViewById(R.id.photoSwitch);
        statusText = findViewById(R.id.statusText);
        configText = findViewById(R.id.configText);
        statsText = findViewById(R.id.statsText);

        // Set default switch states
        screenshotSwitch.setChecked(true);
        photoSwitch.setChecked(true);
    }

    private void setupClickListeners() {
        toggleButton.setOnClickListener(v -> toggleProtection());
        testDataButton.setOnClickListener(v -> openTestDataScreen());
        statsButton.setOnClickListener(v -> showStats());
        scanRecentButton.setOnClickListener(v -> scanRecentImages());

        // Switch listeners
        screenshotSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (screenScrubber.isActive()) {
                updateMonitoringOptions();
            }
        });

        photoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (screenScrubber.isActive()) {
                updateMonitoringOptions();
            }
        });
    }

    private void toggleProtection() {
        if (screenScrubber.isActive()) {
            screenScrubber.stop();
            Toast.makeText(this, "🛡️ Protection disabled", Toast.LENGTH_SHORT).show();
        } else {
            boolean screenshots = screenshotSwitch.isChecked();
            boolean photos = photoSwitch.isChecked();

            if (!screenshots && !photos) {
                Toast.makeText(this, "❌ Please enable at least one monitoring type", Toast.LENGTH_SHORT).show();
                return;
            }

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
            } else {
                Toast.makeText(this, "❌ Failed to start protection", Toast.LENGTH_SHORT).show();
            }
        }
        updateUI();
    }

    private void updateMonitoringOptions() {
        // Only update if monitoring is active
        if (!screenScrubber.isActive()) {
            // Just validate the selection but don't update monitoring yet
            boolean screenshots = screenshotSwitch.isChecked();
            boolean photos = photoSwitch.isChecked();

            if (!screenshots && !photos) {
                Toast.makeText(this, "❌ At least one monitoring type must be enabled", Toast.LENGTH_SHORT).show();
                // Revert the switch that was just unchecked
                if (!screenshotSwitch.isChecked()) {
                    screenshotSwitch.setChecked(true);
                } else {
                    photoSwitch.setChecked(true);
                }
            }
            return;
        }

        // Update active monitoring
        boolean screenshots = screenshotSwitch.isChecked();
        boolean photos = photoSwitch.isChecked();

        if (!screenshots && !photos) {
            Toast.makeText(this, "❌ At least one monitoring type must be enabled", Toast.LENGTH_SHORT).show();
            // Revert the switch that was just unchecked
            if (!screenshotSwitch.isChecked()) {
                screenshotSwitch.setChecked(true);
            } else {
                photoSwitch.setChecked(true);
            }
            return;
        }

        screenScrubber.updateMonitoringOptions(screenshots, photos);
        updateUI();

        String message = "📱 Updated monitoring: ";
        if (screenshots && photos) {
            message += "Screenshots & Photos";
        } else if (screenshots) {
            message += "Screenshots Only";
        } else {
            message += "Photos Only";
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        ScreenScrubber.MonitoringConfig config = screenScrubber.getMonitoringConfig();

        // Update toggle button
        if (config.isActive) {
            toggleButton.setText("Stop Protection");
            toggleButton.setBackgroundColor(getColor(android.R.color.holo_red_light));
        } else {
            toggleButton.setText("Start Protection");
            toggleButton.setBackgroundColor(getColor(android.R.color.holo_green_light));
        }

        // Update status text
        if (config.isActive) {
            statusText.setText("🛡️ ACTIVE - Images being monitored for sensitive data");
            statusText.setTextColor(getColor(android.R.color.holo_green_dark));
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

        // Update switches to reflect current state
        if (config.isActive) {
            screenshotSwitch.setChecked(config.monitoringScreenshots);
            photoSwitch.setChecked(config.monitoringPhotos);
        }
        // Note: Don't update switches when inactive to preserve user selection

        // Always keep switches enabled so users can configure before starting
        screenshotSwitch.setEnabled(true);
        photoSwitch.setEnabled(true);

        // Update stats
        updateStatsDisplay();
    }

    private void updateStatsDisplay() {
        try {
            ScreenScrubberManager.ProcessingStats stats = screenScrubber.getStats();

            if (stats.totalProcessed > 0) {
                StringBuilder statsStr = new StringBuilder("📊 Statistics:\n");
                statsStr.append("Total Processed: ").append(stats.totalProcessed).append("\n");
                statsStr.append("Screenshots: ").append(stats.screenshotsProcessed).append("\n");
                statsStr.append("Photos: ").append(stats.cameraPhotosProcessed).append("\n");
                statsStr.append("Success Rate: ").append(String.format("%.1f%%", stats.getSuccessRate() * 100)).append("\n");
                statsStr.append("Avg Time: ").append(stats.averageProcessingTime).append("ms");

                statsText.setText(statsStr.toString());
                statsText.setVisibility(View.VISIBLE);
            } else {
                statsText.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            statsText.setVisibility(View.GONE);
        }
    }

    private void showStats() {
        try {
            ScreenScrubberManager.ProcessingStats stats = screenScrubber.getStats();

            if (stats.totalProcessed == 0) {
                Toast.makeText(this, "📊 No images processed yet", Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder message = new StringBuilder("📊 Detailed Statistics:\n\n");
            message.append("Total Images: ").append(stats.totalProcessed).append("\n");
            message.append("Screenshots: ").append(stats.screenshotsProcessed).append("\n");
            message.append("Camera Photos: ").append(stats.cameraPhotosProcessed).append("\n");
            message.append("Successful: ").append(stats.successfullyProcessed).append("\n");
            message.append("Success Rate: ").append(String.format("%.1f%%", stats.getSuccessRate() * 100)).append("\n");
            message.append("Average Time: ").append(stats.averageProcessingTime).append("ms\n");
            message.append("Total Time: ").append(stats.totalProcessingTime).append("ms");

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("📊 Processing Statistics")
                    .setMessage(message.toString())
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Reset", (dialog, which) -> {
                        screenScrubber.resetStats();
                        updateUI();
                        Toast.makeText(this, "📊 Statistics reset", Toast.LENGTH_SHORT).show();
                    })
                    .show();

        } catch (Exception e) {
            Toast.makeText(this, "❌ Error showing statistics: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void scanRecentImages() {
        if (!screenScrubber.isActive()) {
            Toast.makeText(this, "⚠️ Start protection first to scan recent images", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            screenScrubber.scanRecentImages();
            Toast.makeText(this, "🔍 Scanning recent images (last 2 hours)...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "❌ Error scanning recent images: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

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
    protected void onResume() {
        super.onResume();
        updateUI(); // Update UI when returning to the app
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screenScrubber != null) {
            screenScrubber.cleanup();
        }
    }

    /**
     * Show feature demo dialog
     */
    private void showFeatureDemo() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🎯 ScreenScrubber Features")
                .setMessage("Enhanced Features:\n\n" +
                        "📱 Screenshot Monitoring\n" +
                        "• Automatically detects screenshots\n" +
                        "• Scans for sensitive data\n" +
                        "• Deletes original if sensitive data found\n\n" +
                        "📷 Camera Photo Monitoring\n" +
                        "• Monitors new photos from camera\n" +
                        "• Scans for sensitive data\n" +
                        "• Keeps original, saves censored version\n\n" +
                        "🔍 Sensitive Data Detection\n" +
                        "• US: Credit cards, SSN, phone numbers\n" +
                        "• Israeli: ID numbers, phones, bank accounts\n" +
                        "• Universal: Email addresses\n\n" +
                        "🛡️ Privacy Protection\n" +
                        "• All processing done on device\n" +
                        "• No data sent to servers\n" +
                        "• Instant notifications\n\n" +
                        "📊 Statistics & Monitoring\n" +
                        "• Real-time processing stats\n" +
                        "• Success rate tracking\n" +
                        "• Performance metrics")
                .setPositiveButton("Got it!", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, "Show Features").setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, 2, 0, "Test Library").setIcon(android.R.drawable.ic_menu_manage);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                showFeatureDemo();
                return true;
            case 2:
                testLibraryWithSampleImage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Test the library with a sample image
     */
    private void testLibraryWithSampleImage() {
        // For demo purposes, we'll test with the test data screen
        Toast.makeText(this, "🧪 Use 'Test Data Screen' to test the library", Toast.LENGTH_SHORT).show();
        openTestDataScreen();
    }
}

