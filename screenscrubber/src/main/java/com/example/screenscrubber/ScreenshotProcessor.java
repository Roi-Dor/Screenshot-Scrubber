package com.example.screenscrubber;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import com.google.mlkit.vision.text.Text;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Handles the complete screenshot processing pipeline:
 * 1. Detect sensitive data in extracted text
 * 2. Create censored image if needed
 * 3. Save/delete files appropriately
 */
public class ScreenshotProcessor {
    private static final String TAG = "ScreenshotProcessor";
    private static final String CENSORED_FOLDER = "ScreenScrubber_Censored";

    private SensitiveDataDetector sensitiveDataDetector;
    private Context context;

    public static class ProcessingResult {
        public final boolean hasSensitiveData;
        public final String censoredImagePath;
        public final List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches;
        public final String originalImagePath;

        public ProcessingResult(boolean hasSensitiveData, String censoredImagePath,
                                List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                String originalImagePath) {
            this.hasSensitiveData = hasSensitiveData;
            this.censoredImagePath = censoredImagePath;
            this.sensitiveMatches = sensitiveMatches;
            this.originalImagePath = originalImagePath;
        }
    }

    public ScreenshotProcessor() {
        this.sensitiveDataDetector = new SensitiveDataDetector();
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Complete processing pipeline: analyze text, censor if needed, clean up files
     */
    /**
     * Complete processing pipeline: analyze text, censor if needed, clean up files
     */
    public ProcessingResult processScreenshot(String imagePath, Text visionText) {

        // Step 1: Extract and analyze text for sensitive data
        String fullText = visionText.getText();
        Log.d(TAG, "Analyzing text from: " + imagePath);

        List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches;
        if (fullText.isEmpty()) {
            Log.d(TAG, "No text found in image");
            sensitiveMatches = List.of(); // Empty list
        } else {
            sensitiveMatches = sensitiveDataDetector.detectSensitiveData(fullText);
            Log.d(TAG, "Found " + sensitiveMatches.size() + " sensitive data matches");

            // Log findings safely
            for (SensitiveDataDetector.SensitiveMatch match : sensitiveMatches) {
                Log.d(TAG, "Detected: " + match.type + " - " + maskSensitiveValue(match.value, match.type));
            }
        }

        // Step 2: Process based on findings
        if (sensitiveMatches.isEmpty()) {
            // No sensitive data - KEEP the original, don't delete it
            Log.d(TAG, "No sensitive data found - keeping original screenshot");
            return new ProcessingResult(false, null, sensitiveMatches, imagePath);
        } else {
            // Sensitive data found - create censored version AND delete original
            Log.d(TAG, "Sensitive data found - creating censored version and deleting original");
            return createCensoredVersion(imagePath, visionText, sensitiveMatches);
        }
    }

    /**
     * Create censored version of the image
     */
    private ProcessingResult createCensoredVersion(String imagePath, Text visionText,
                                                   List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches) {
        try {
            // Load original image
            Bitmap originalBitmap = BitmapFactory.decodeFile(imagePath);
            if (originalBitmap == null) {
                Log.e(TAG, "Could not load original image");
                return new ProcessingResult(true, null, sensitiveMatches, imagePath);
            }

            // Create censored version
            Bitmap censoredBitmap = createCensoredImage(originalBitmap, visionText, sensitiveMatches);

            // Save censored image
            String censoredPath = saveCensoredImage(censoredBitmap, imagePath);

            // Clean up memory
            originalBitmap.recycle();
            censoredBitmap.recycle();

            // Delete original screenshot using MediaStore
            deleteOriginalScreenshot(imagePath);

            return new ProcessingResult(true, censoredPath, sensitiveMatches, imagePath);

        } catch (Exception e) {
            Log.e(TAG, "Error creating censored version", e);
            return new ProcessingResult(true, null, sensitiveMatches, imagePath);
        }
    }

    /**
     * Create image with black rectangles over sensitive areas
     */
    private Bitmap createCensoredImage(Bitmap originalBitmap, Text visionText,
                                       List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches) {

        Bitmap censoredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(censoredBitmap);

        Paint blackPaint = new Paint();
        blackPaint.setColor(0xFF000000);
        blackPaint.setStyle(Paint.Style.FILL);

        String fullText = visionText.getText();

        for (SensitiveDataDetector.SensitiveMatch match : sensitiveMatches) {
            List<Rect> boundingBoxes = findTextBoundingBoxes(visionText, match, fullText);

            for (Rect rect : boundingBoxes) {
                // Add padding and ensure within bounds
                int padding = 10;
                rect.left = Math.max(0, rect.left - padding);
                rect.top = Math.max(0, rect.top - padding);
                rect.right = Math.min(censoredBitmap.getWidth(), rect.right + padding);
                rect.bottom = Math.min(censoredBitmap.getHeight(), rect.bottom + padding);

                canvas.drawRect(rect, blackPaint);
                Log.d(TAG, "Censored " + match.type + " at: " + rect.toString());
            }
        }

        return censoredBitmap;
    }

    /**
     * Find where sensitive text appears in the image
     */
    private List<Rect> findTextBoundingBoxes(Text visionText, SensitiveDataDetector.SensitiveMatch match, String fullText) {
        List<Rect> boundingBoxes = new java.util.ArrayList<>();
        String searchText = match.value.trim();

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText();

                if (containsSensitiveText(lineText, searchText, match.type)) {
                    Rect lineBounds = line.getBoundingBox();
                    if (lineBounds != null) {
                        boundingBoxes.add(new Rect(lineBounds));
                        Log.d(TAG, "Found " + match.type + " in line at: " + lineBounds.toString());
                    }
                } else {
                    // Check individual elements
                    for (Text.Element element : line.getElements()) {
                        String elementText = element.getText();
                        if (containsSensitiveText(elementText, searchText, match.type)) {
                            Rect elementBounds = element.getBoundingBox();
                            if (elementBounds != null) {
                                boundingBoxes.add(new Rect(elementBounds));
                                Log.d(TAG, "Found " + match.type + " in element at: " + elementBounds.toString());
                            }
                        }
                    }
                }
            }
        }

        return boundingBoxes;
    }

    private boolean containsSensitiveText(String text, String sensitiveText, String type) {
        if (text == null || sensitiveText == null) return false;

        String cleanText = text.replaceAll("[\\s\\-\\(\\)]", "");
        String cleanSensitive = sensitiveText.replaceAll("[\\s\\-\\(\\)]", "");

        return cleanText.contains(cleanSensitive) ||
                text.toLowerCase().contains(sensitiveText.toLowerCase());
    }

    private String saveCensoredImage(Bitmap censoredBitmap, String originalPath) {
        try {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File censoredDir = new File(picturesDir, CENSORED_FOLDER);

            if (!censoredDir.exists()) {
                censoredDir.mkdirs();
            }

            String originalFileName = new File(originalPath).getName();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String censoredFileName = "censored_" + timestamp + "_" + originalFileName;

            File censoredFile = new File(censoredDir, censoredFileName);

            FileOutputStream fos = new FileOutputStream(censoredFile);
            censoredBitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.flush();
            fos.close();

            Log.i(TAG, "Saved censored image: " + censoredFile.getAbsolutePath());

            // NEW: Tell Android about the new file so it appears in gallery
            addImageToMediaStore(censoredFile.getAbsolutePath());

            return censoredFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Error saving censored image", e);
            return null;
        }
    }

    /**
     * Add the new censored image to MediaStore so it appears in gallery apps
     */
    private void addImageToMediaStore(String imagePath) {
        try {
            if (context == null) return;

            ContentResolver resolver = context.getContentResolver();
            File imageFile = new File(imagePath);

            if (!imageFile.exists()) {
                Log.w(TAG, "Image file doesn't exist for MediaStore insertion");
                return;
            }

            // Create content values for the new image
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.DATA, imagePath);
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Images.Media.SIZE, imageFile.length());

            // Insert into MediaStore
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                Log.d(TAG, "Successfully added censored image to MediaStore: " + uri);

                // Alternative method for older devices - scan the file
                Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scanIntent.setData(Uri.fromFile(imageFile));
                context.sendBroadcast(scanIntent);

            } else {
                Log.w(TAG, "Failed to add image to MediaStore");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error adding image to MediaStore", e);
        }
    }

    private void deleteOriginalScreenshot(String imagePath) {
        // Try MediaStore deletion first (Android 10+)
        if (context != null && deleteOriginalScreenshotViaMediaStore(imagePath)) {
            Log.i(TAG, "Successfully deleted original screenshot via MediaStore: " + imagePath);
            return;
        }

        // Fallback to direct file deletion
        try {
            File originalFile = new File(imagePath);
            if (originalFile.exists()) {
                boolean deleted = originalFile.delete();
                if (deleted) {
                    Log.i(TAG, "Deleted original screenshot: " + imagePath);
                } else {
                    Log.w(TAG, "Could not delete original screenshot: " + imagePath);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting original screenshot", e);
        }
    }

    private boolean deleteOriginalScreenshotViaMediaStore(String imagePath) {
        try {
            ContentResolver resolver = context.getContentResolver();

            // Query MediaStore for the file
            String[] projection = {MediaStore.Images.Media._ID};
            String selection = MediaStore.Images.Media.DATA + "=?";
            String[] selectionArgs = {imagePath};

            Cursor cursor = resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                cursor.close();

                Uri contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                );

                int deleted = resolver.delete(contentUri, null, null);

                if (deleted > 0) {
                    Log.i(TAG, "Successfully deleted original via MediaStore");
                    return true;
                }
            }

            if (cursor != null) cursor.close();

        } catch (Exception e) {
            Log.e(TAG, "MediaStore deletion failed", e);
        }

        return false;
    }

    private String maskSensitiveValue(String value, String type) {
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
}