package com.example.screenscrubber;


import android.content.Context;
import android.util.Log;

public class ScreenScrubberManager {
    private static final String TAG = "ScreenScrubberManager";
    private ScreenshotDetector detector;
    private Context context;

    public ScreenScrubberManager(Context context) {
        this.context = context;
        this.detector = new ScreenshotDetector();
    }

    public void startMonitoring() {
        detector.startDetection(context, new ScreenshotDetector.ScreenshotListener() {
            @Override
            public void onScreenshotTaken(String filePath) {
                Log.i(TAG, "Processing screenshot: " + filePath);
                // TODO: Add image processing logic in next phase
                processScreenshot(filePath);
            }
        });
    }

    public void stopMonitoring() {
        detector.stopDetection();
    }

    private void processScreenshot(String filePath) {
        // Placeholder for image processing
        Log.d(TAG, "Screenshot processing will be implemented next");
    }
}