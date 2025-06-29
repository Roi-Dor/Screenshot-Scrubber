package com.example.screenscrubber;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import java.util.List;

public class TextRecognitionService {
    private static final String TAG = "TextRecognitionService";
    private TextRecognizer recognizer;
    private SensitiveDataDetector sensitiveDataDetector;

    public interface TextAnalysisCallback {
        void onAnalysisComplete(List<SensitiveDataDetector.SensitiveMatch> sensitiveData);
        void onAnalysisError(String error);
    }

    public TextRecognitionService() {
        // Initialize ML Kit text recognizer
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        sensitiveDataDetector = new SensitiveDataDetector();
    }

    public void analyzeScreenshot(String imagePath, TextAnalysisCallback callback) {
        if (imagePath == null || callback == null) {
            if (callback != null) {
                callback.onAnalysisError("Invalid parameters");
            }
            return;
        }

        try {
            // Load image from file
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                callback.onAnalysisError("Image file not found: " + imagePath);
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                callback.onAnalysisError("Could not decode image");
                return;
            }

            // Create InputImage for ML Kit
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            // Process with ML Kit
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        processTextResult(visionText, callback);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Text recognition failed", e);
                        callback.onAnalysisError("Text recognition failed: " + e.getMessage());
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing screenshot", e);
            callback.onAnalysisError("Analysis error: " + e.getMessage());
        }
    }

    private void processTextResult(Text visionText, TextAnalysisCallback callback) {
        String fullText = visionText.getText();
        Log.d(TAG, "Extracted text: " + fullText);

        if (fullText.isEmpty()) {
            Log.d(TAG, "No text found in image");
            callback.onAnalysisComplete(List.of()); // Empty list
            return;
        }

        // Detect sensitive data
        List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches =
                sensitiveDataDetector.detectSensitiveData(fullText);

        Log.d(TAG, "Found " + sensitiveMatches.size() + " sensitive data matches");

        for (SensitiveDataDetector.SensitiveMatch match : sensitiveMatches) {
            Log.d(TAG, "Sensitive data detected - Type: " + match.type +
                    ", Value: " + maskSensitiveValue(match.value, match.type));
        }

        callback.onAnalysisComplete(sensitiveMatches);
    }

    private String maskSensitiveValue(String value, String type) {
        // Mask sensitive values for logging
        switch (type) {
            case "CREDIT_CARD":
                return value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
            case "SSN":
                return "***-**-" + value.substring(value.length() - 4);
            case "PHONE":
                return "***-***-" + value.substring(value.length() - 4);
            case "EMAIL":
                int atIndex = value.indexOf('@');
                if (atIndex > 2) {
                    return value.substring(0, 2) + "***" + value.substring(atIndex);
                }
                return "***" + value.substring(atIndex);
            default:
                return "***";
        }
    }

    public void cleanup() {
        if (recognizer != null) {
            recognizer.close();
        }
    }
}