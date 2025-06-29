package com.example.screenscrubberdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.screenscrubber.ScreenScrubberManager;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private ScreenScrubberManager scrubberManager;
    private Button toggleButton;
    private boolean isMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrubberManager = new ScreenScrubberManager(this);
        toggleButton = findViewById(R.id.toggleButton);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMonitoring();
            }
        });

        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scrubberManager != null) {
            scrubberManager.stopMonitoring();
        }
    }
}