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
    private Context context;

    public ScreenScrubberManager(Context context) {
        this.context = context;
        this.detector = new ScreenshotDetector();
        this.textService = new TextRecognitionService();
        this.screenshotProcessor = new ScreenshotProcessor();
        this.screenshotProcessor.setContext(context); // Pass context for MediaStore deletion
    }

    public void startMonitoring() {
        detector.startDetection(context, new ScreenshotDetector.ScreenshotListener() {
            @Override
            public void onScreenshotTaken(String filePath) {
                Log.i(TAG, "Processing screenshot: " + filePath);
                showToast("🔍 Analyzing screenshot for sensitive data...");
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
                showToast("📝 Text extracted, checking for sensitive data...");

                // Step 2: Process the screenshot (detect sensitive data, censor, save/delete)
                ScreenshotProcessor.ProcessingResult result = screenshotProcessor.processScreenshot(imagePath, visionText);

                // Step 3: Inform user
                handleProcessingResult(result);
            }

            @Override
            public void onExtractionError(String error, String imagePath) {
                Log.e(TAG, "Text extraction failed: " + error);
                showToast("❌ Screenshot analysis failed: " + error);
            }
        });
    }

    /**
     * Handle the final result and inform user
     */
    private void handleProcessingResult(ScreenshotProcessor.ProcessingResult result) {
        if (!result.hasSensitiveData) {
            Log.d(TAG, "No sensitive data found");
            showToast("✅ Screenshot is clean - no sensitive data detected");
        } else {
            Log.w(TAG, "Sensitive data detected and processed");

            StringBuilder message = new StringBuilder("⚠️ Sensitive data found and censored:\n");
            for (SensitiveDataDetector.SensitiveMatch match : result.sensitiveMatches) {
                message.append("• ").append(match.type).append("\n");
            }

            if (result.censoredImagePath != null) {
                message.append("\n✅ Safe version saved to Pictures/ScreenScrubber_Censored");
                message.append("\n🗑️ Original screenshot deleted");
                Log.i(TAG, "Censored image saved: " + result.censoredImagePath);
            } else {
                message.append("\n❌ Could not save censored version");
            }

            showToast(message.toString());
        }
    }

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