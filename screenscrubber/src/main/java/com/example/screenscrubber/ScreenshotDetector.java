package com.example.screenscrubber;

import android.content.Context;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;

public class ScreenshotDetector {
    private static final String TAG = "ScreenshotDetector";
    private FileObserver fileObserver;
    private ScreenshotListener listener;
    private Handler handler = new Handler(Looper.getMainLooper());

    public interface ScreenshotListener {
        void onScreenshotTaken(String filePath);
    }

    public void startDetection(Context context, ScreenshotListener listener) {
        this.listener = listener;

        // Get screenshot directory
        String screenshotPath = Environment.getExternalStorageDirectory() +
                "/DCIM/Screenshots/";

        File screenshotDir = new File(screenshotPath);
        if (!screenshotDir.exists()) {
            // Try alternative path
            screenshotPath = Environment.getExternalStorageDirectory() +
                    "/Pictures/Screenshots/";
        }

        Log.d(TAG, "Monitoring screenshot directory: " + screenshotPath);

        final String finalScreenshotPath = screenshotPath;

        fileObserver = new FileObserver(screenshotPath, FileObserver.CREATE | FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int event, String fileName) {
                if (fileName != null &&
                        fileName.toLowerCase().contains("screenshot") &&
                        !fileName.startsWith(".pending") &&
                        (fileName.endsWith(".jpg") || fileName.endsWith(".png"))) {

                    String fullPath = finalScreenshotPath + fileName;
                    Log.d(TAG, "Screenshot detected: " + fullPath);

                    // Wait a bit for file to be fully written and permissions to be set
                    handler.postDelayed(() -> {
                        processScreenshotWithRetry(fullPath, 0);
                    }, 500); // 500ms delay
                }
            }
        };

        fileObserver.startWatching();
        Log.d(TAG, "Screenshot detection started");
    }

    private void processScreenshotWithRetry(String filePath, int attempt) {
        File file = new File(filePath);

        if (file.exists() && file.canRead()) {
            Log.d(TAG, "Screenshot ready for processing: " + filePath);
            if (listener != null) {
                listener.onScreenshotTaken(filePath);
            }
        } else if (attempt < 3) {
            // Retry up to 3 times with increasing delay
            Log.d(TAG, "Screenshot not ready, retrying in " + (1000 * (attempt + 1)) + "ms");
            handler.postDelayed(() -> {
                processScreenshotWithRetry(filePath, attempt + 1);
            }, 1000 * (attempt + 1));
        } else {
            Log.e(TAG, "Screenshot file not accessible after retries: " + filePath);
        }
    }

    public void stopDetection() {
        if (fileObserver != null) {
            fileObserver.stopWatching();
            Log.d(TAG, "Screenshot detection stopped");
        }
    }
}