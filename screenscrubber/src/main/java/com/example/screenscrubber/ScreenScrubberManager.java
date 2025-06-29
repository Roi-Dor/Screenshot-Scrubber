package com.example.screenscrubber;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import java.util.List;

public class ScreenScrubberManager {
    private static final String TAG = "ScreenScrubberManager";
    private ScreenshotDetector detector;
    private TextRecognitionService textService;
    private Context context;

    public ScreenScrubberManager(Context context) {
        this.context = context;
        this.detector = new ScreenshotDetector();
        this.textService = new TextRecognitionService();
    }

    public void startMonitoring() {
        detector.startDetection(context, new ScreenshotDetector.ScreenshotListener() {
            @Override
            public void onScreenshotTaken(String filePath) {
                Log.i(TAG, "Processing screenshot: " + filePath);
                processScreenshot(filePath);
            }
        });
    }

    public void stopMonitoring() {
        detector.stopDetection();
    }

    private void processScreenshot(String filePath) {
        Log.d(TAG, "Starting text analysis for: " + filePath);

        textService.analyzeScreenshot(filePath, new TextRecognitionService.TextAnalysisCallback() {
            @Override
            public void onAnalysisComplete(List<SensitiveDataDetector.SensitiveMatch> sensitiveData) {
                handleAnalysisResult(sensitiveData, filePath);
            }

            @Override
            public void onAnalysisError(String error) {
                Log.e(TAG, "Analysis error: " + error);
                showToast("Screenshot analysis failed: " + error);
            }
        });
    }

    private void handleAnalysisResult(List<SensitiveDataDetector.SensitiveMatch> sensitiveData, String filePath) {
        if (sensitiveData.isEmpty()) {
            Log.d(TAG, "No sensitive data found in screenshot");
            showToast("Screenshot is clean - no sensitive data detected");
        } else {
            Log.w(TAG, "Sensitive data detected in screenshot!");

            StringBuilder message = new StringBuilder("⚠️ Sensitive data detected:\n");
            for (SensitiveDataDetector.SensitiveMatch match : sensitiveData) {
                message.append("• ").append(match.type).append("\n");
            }

            showToast(message.toString());

            // TODO: In next phase, we'll add image processing to blur/blacken sensitive areas
            Log.d(TAG, "Image processing will be implemented in next phase");
        }
    }

    private void showToast(String message) {
        // Run on UI thread since callbacks might be on background thread
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
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