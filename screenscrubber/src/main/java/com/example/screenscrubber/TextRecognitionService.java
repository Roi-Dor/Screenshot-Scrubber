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

/**
 * Responsible ONLY for extracting text from images using ML Kit
 * Does NOT handle sensitive data detection or image processing
 */
public class TextRecognitionService {
    private static final String TAG = "TextRecognitionService";
    private TextRecognizer recognizer;

    public interface TextExtractionCallback {
        void onTextExtracted(Text visionText, String imagePath);
        void onExtractionError(String error, String imagePath);
    }

    public TextRecognitionService() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    /**
     * Extract text from image - that's all this class does
     */
    public void extractTextFromImage(String imagePath, TextExtractionCallback callback) {
        if (imagePath == null || callback == null) {
            if (callback != null) {
                callback.onExtractionError("Invalid parameters", imagePath);
            }
            return;
        }

        try {
            // Load image from file
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                callback.onExtractionError("Image file not found", imagePath);
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                callback.onExtractionError("Could not decode image", imagePath);
                return;
            }

            // Create InputImage for ML Kit
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            // Process with ML Kit
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        Log.d(TAG, "Text extraction complete for: " + imagePath);
                        callback.onTextExtracted(visionText, imagePath);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Text recognition failed", e);
                        callback.onExtractionError("Text recognition failed: " + e.getMessage(), imagePath);
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error extracting text", e);
            callback.onExtractionError("Extraction error: " + e.getMessage(), imagePath);
        }
    }

    public void cleanup() {
        if (recognizer != null) {
            recognizer.close();
        }
    }
}