package com.example.screenscrubber;

import android.content.Context;
import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;
import java.io.File;

public class ScreenshotDetector {
    private static final String TAG = "ScreenshotDetector";
    private FileObserver fileObserver;
    private ScreenshotListener listener;


    public interface ScreenshotListener {
        void onScreenshotTaken(String filePath);
    }

    public void startDetection(Context context, ScreenshotListener listener) {
        this.listener = listener;

        // Get screenshot directory
        String screenshotPath = Environment.getExternalStorageDirectory() +
                "/Pictures/Screenshots/";

        File screenshotDir = new File(screenshotPath);
        if (!screenshotDir.exists()) {
            // Try alternative path
            screenshotPath = Environment.getExternalStorageDirectory() +
                    "/DCIM/Screenshots/";
        }

        Log.d(TAG, "Monitoring screenshot directory: " + screenshotPath);

        final String finalScreenshotPath = screenshotPath;

        fileObserver = new FileObserver(screenshotPath, FileObserver.CREATE) {
            @Override
            public void onEvent(int event, String fileName) {
                if (fileName != null && fileName.toLowerCase().contains("screenshot")) {
                    String fullPath = finalScreenshotPath + fileName;
                    Log.d(TAG, "Screenshot detected: " + fullPath);

                    if (ScreenshotDetector.this.listener != null) {
                        ScreenshotDetector.this.listener.onScreenshotTaken(fullPath);
                    }
                }
            }
        };

        fileObserver.startWatching();
        Log.d(TAG, "Screenshot detection started");
    }

    public void stopDetection() {
        if (fileObserver != null) {
            fileObserver.stopWatching();
            Log.d(TAG, "Screenshot detection stopped");
        }
    }
}


