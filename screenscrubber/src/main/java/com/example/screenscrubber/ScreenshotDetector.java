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

        // Primary screenshot directory (Samsung, most devices)
        String screenshotPath = Environment.getExternalStorageDirectory() + "/DCIM/Screenshots/";

        File screenshotDir = new File(screenshotPath);

        // If DCIM/Screenshots doesn't exist, try Pictures/Screenshots (some devices)
        if (!screenshotDir.exists()) {
            screenshotPath = Environment.getExternalStorageDirectory() + "/Pictures/Screenshots/";
            screenshotDir = new File(screenshotPath);

            // Create the directory if it doesn't exist
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
        }

        Log.d(TAG, "Monitoring screenshot directory: " + screenshotPath);
        Log.d(TAG, "Directory exists: " + screenshotDir.exists());
        Log.d(TAG, "Directory can read: " + screenshotDir.canRead());

        final String finalScreenshotPath = screenshotPath;

        fileObserver = new FileObserver(screenshotPath, FileObserver.CREATE | FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int event, String fileName) {
                Log.d(TAG, "FileObserver event: " + event + ", fileName: " + fileName);

                if (fileName != null &&
                        fileName.toLowerCase().contains("screenshot") &&
                        !fileName.startsWith(".pending") &&
                        (fileName.endsWith(".jpg") || fileName.endsWith(".png"))) {

                    String fullPath = finalScreenshotPath + fileName;
                    Log.d(TAG, "Screenshot detected: " + fullPath);

                    // Shorter initial delay for better UX
                    handler.postDelayed(() -> {
                        processScreenshotWithRetry(fullPath, 0);
                    }, 500); // Reduced to 500ms
                }
            }
        };

        try {
            fileObserver.startWatching();
            Log.d(TAG, "Screenshot detection started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start file observer", e);
        }
    }

    private void processScreenshotWithRetry(String filePath, int attempt) {
        File file = new File(filePath);

        Log.d(TAG, "Processing attempt " + (attempt + 1) + " for: " + filePath);
        Log.d(TAG, "File exists: " + file.exists() + ", can read: " + file.canRead() + ", size: " + file.length());

        // Check if file exists, is readable, and has content
        if (file.exists() && file.canRead() && file.length() > 0) {
            Log.d(TAG, "Screenshot ready for processing: " + filePath);
            if (listener != null) {
                listener.onScreenshotTaken(filePath);
            }
        } else if (attempt < 3) { // Reduced retry attempts
            // Retry with shorter delays
            int delay = 500 * (attempt + 1); // 500ms, 1s, 1.5s
            Log.d(TAG, "Screenshot not ready, retrying in " + delay + "ms");
            handler.postDelayed(() -> {
                processScreenshotWithRetry(filePath, attempt + 1);
            }, delay);
        } else {
            Log.e(TAG, "Screenshot file not accessible after " + (attempt + 1) + " retries: " + filePath);
        }
    }

    public void stopDetection() {
        if (fileObserver != null) {
            fileObserver.stopWatching();
            Log.d(TAG, "Screenshot detection stopped");
        }
    }
}