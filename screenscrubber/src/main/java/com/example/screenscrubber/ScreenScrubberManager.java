package com.example.screenscrubber;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * Main coordinator - orchestrates the screenshot processing pipeline
 */
public class ScreenScrubberManager {
    private static final String TAG = "ScreenScrubberManager";

    private ScreenshotDetector detector;
    private TextRecognitionService textService;
    private ScreenshotProcessor screenshotProcessor;
    private NotificationHelper notificationHelper;  // ADD THIS LINE
    private Context context;

    public ScreenScrubberManager(Context context) {
        this.context = context;
        this.detector = new ScreenshotDetector();
        this.textService = new TextRecognitionService();
        this.screenshotProcessor = new ScreenshotProcessor();
        this.screenshotProcessor.setContext(context);
        this.notificationHelper = new NotificationHelper(context);  // ADD THIS LINE
    }

    public void startMonitoring() {
        detector.startDetection(context, new ScreenshotDetector.ScreenshotListener() {
            @Override
            public void onScreenshotTaken(String filePath) {
                Log.i(TAG, "Processing screenshot: " + filePath);
                // REMOVE THIS LINE: showToast("🔍 Analyzing screenshot for sensitive data...");
                processScreenshot(filePath);
            }
        });
    }

    public void stopMonitoring() {
        detector.stopDetection();
    }

    /**
     * Simple two-step process: extract text, then process
     */
    private void processScreenshot(String filePath) {
        Log.d(TAG, "Step 1: Extracting text from: " + filePath);

        // Step 1: Extract text with ML Kit
        textService.extractTextFromImage(filePath, new TextRecognitionService.TextExtractionCallback() {
            @Override
            public void onTextExtracted(com.google.mlkit.vision.text.Text visionText, String imagePath) {
                Log.d(TAG, "Step 2: Processing screenshot with extracted text");
                // REMOVE THIS LINE: showToast("📝 Text extracted, checking for sensitive data...");

                // Step 2: Process the screenshot
                ScreenshotProcessor.ProcessingResult result = screenshotProcessor.processScreenshot(imagePath, visionText);

                // Step 3: Show notification instead of toast
                handleProcessingResult(result);
            }

            @Override
            public void onExtractionError(String error, String imagePath) {
                Log.e(TAG, "Text extraction failed: " + error);
                // Keep toast for errors only
                showToast("❌ Screenshot analysis failed: " + error);
            }
        });
    }

    /**
     * Handle the final result and show notification
     */
    private void handleProcessingResult(ScreenshotProcessor.ProcessingResult result) {
        if (!result.hasSensitiveData) {
            Log.d(TAG, "No sensitive data found");
            // REPLACE TOAST WITH NOTIFICATION:
            notificationHelper.showCleanScreenshotNotification();
        } else {
            Log.w(TAG, "Sensitive data detected and processed");
            // REPLACE TOAST WITH NOTIFICATION:
            notificationHelper.showSensitiveDataAlert(result.sensitiveMatches);
        }
    }

    // Keep this method for error messages only
    private void showToast(String message) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    public void cleanup() {
        if (textService != null) {
            textService.cleanup();
        }
        stopMonitoring();
    }
}