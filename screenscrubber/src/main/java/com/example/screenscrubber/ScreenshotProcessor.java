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
 * Handles the complete screenshot processing pipeline with enhanced error handling:
 * 1. Detect sensitive data in extracted text
 * 2. Create censored image if needed
 * 3. Save/delete files appropriately
 */
public class ScreenshotProcessor {
    private static final String TAG = "ScreenshotProcessor";
    private static final String CENSORED_FOLDER = "ScreenScrubber_Censored";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB max
    private static final int MAX_IMAGE_DIMENSION = 4096; // Max width/height

    private SensitiveDataDetector sensitiveDataDetector;
    private Context context;

    public static class ProcessingResult {
        public final boolean hasSensitiveData;
        public final String censoredImagePath;
        public final List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches;
        public final String originalImagePath;
        public final boolean success;
        public final String errorMessage;

        public ProcessingResult(boolean hasSensitiveData, String censoredImagePath,
                                List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                String originalImagePath, boolean success, String errorMessage) {
            this.hasSensitiveData = hasSensitiveData;
            this.censoredImagePath = censoredImagePath;
            this.sensitiveMatches = sensitiveMatches;
            this.originalImagePath = originalImagePath;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        // Convenience constructors
        public static ProcessingResult success(boolean hasSensitiveData, String censoredImagePath,
                                               List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                               String originalImagePath) {
            return new ProcessingResult(hasSensitiveData, censoredImagePath, sensitiveMatches,
                    originalImagePath, true, null);
        }

        public static ProcessingResult error(String errorMessage, String originalImagePath) {
            return new ProcessingResult(false, null, List.of(), originalImagePath, false, errorMessage);
        }
    }

    public ScreenshotProcessor() {
        this.sensitiveDataDetector = new SensitiveDataDetector();
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Complete processing pipeline with robust error handling
     */
    public ProcessingResult processScreenshot(String imagePath, Text visionText) {
        long startTime = System.currentTimeMillis();

        // Input validation
        if (imagePath == null || imagePath.isEmpty()) {
            Log.e(TAG, "Invalid image path provided");
            return ProcessingResult.error("Invalid image path", null);
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Log.e(TAG, "Image file does not exist: " + imagePath);
            return ProcessingResult.error("Image file not found", imagePath);
        }

        if (imageFile.length() == 0) {
            Log.e(TAG, "Image file is empty: " + imagePath);
            return ProcessingResult.error("Image file is empty", imagePath);
        }

        if (imageFile.length() > MAX_FILE_SIZE) {
            Log.e(TAG, "Image file too large: " + imageFile.length() + " bytes");
            return ProcessingResult.error("Image file too large", imagePath);
        }

        if (visionText == null) {
            Log.e(TAG, "Vision text is null");
            return ProcessingResult.error("Text extraction failed", imagePath);
        }

        try {
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
            ProcessingResult result;
            if (sensitiveMatches.isEmpty()) {
                // No sensitive data - KEEP the original, don't delete it
                Log.d(TAG, "No sensitive data found - keeping original screenshot");
                result = ProcessingResult.success(false, null, sensitiveMatches, imagePath);
            } else {
                // Sensitive data found - create censored version AND delete original
                Log.d(TAG, "Sensitive data found - creating censored version and deleting original");
                result = createCensoredVersion(imagePath, visionText, sensitiveMatches);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Screenshot processing completed in " + processingTime + "ms");
            return result;

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory processing screenshot", e);
            return ProcessingResult.error("Out of memory", imagePath);
        } catch (Exception e) {
            Log.e(TAG, "Error processing screenshot", e);
            return ProcessingResult.error("Processing failed: " + e.getMessage(), imagePath);
        }
    }

    /**
     * Create censored version of the image with enhanced error handling
     */
    private ProcessingResult createCensoredVersion(String imagePath, Text visionText,
                                                   List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches) {
        Bitmap originalBitmap = null;
        Bitmap censoredBitmap = null;

        try {
            // Load original image with size validation
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            // Check image dimensions
            if (options.outWidth > MAX_IMAGE_DIMENSION || options.outHeight > MAX_IMAGE_DIMENSION) {
                Log.w(TAG, "Large image detected: " + options.outWidth + "x" + options.outHeight);
                // Calculate sample size to reduce memory usage
                options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION);
            }

            options.inJustDecodeBounds = false;
            originalBitmap = BitmapFactory.decodeFile(imagePath, options);

            if (originalBitmap == null) {
                Log.e(TAG, "Could not load original image");
                return ProcessingResult.error("Could not load image", imagePath);
            }

            if (originalBitmap.isRecycled()) {
                Log.e(TAG, "Original bitmap is recycled");
                return ProcessingResult.error("Image data corrupted", imagePath);
            }

            // Create censored version
            censoredBitmap = createCensoredImage(originalBitmap, visionText, sensitiveMatches);
            if (censoredBitmap == null) {
                return ProcessingResult.error("Failed to create censored image", imagePath);
            }

            // Save censored image
            String censoredPath = saveCensoredImage(censoredBitmap, imagePath);
            if (censoredPath == null) {
                return ProcessingResult.error("Failed to save censored image", imagePath);
            }

            // Delete original screenshot using MediaStore
            boolean deleted = deleteOriginalScreenshot(imagePath);
            if (!deleted) {
                Log.w(TAG, "Could not delete original screenshot, but continuing");
            }

            return ProcessingResult.success(true, censoredPath, sensitiveMatches, imagePath);

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory creating censored version", e);
            return ProcessingResult.error("Out of memory processing image", imagePath);
        } catch (Exception e) {
            Log.e(TAG, "Error creating censored version", e);
            return ProcessingResult.error("Censoring failed: " + e.getMessage(), imagePath);
        } finally {
            // Clean up memory
            if (originalBitmap != null && !originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            if (censoredBitmap != null && !censoredBitmap.isRecycled()) {
                censoredBitmap.recycle();
            }
        }
    }

    /**
     * Create image with black rectangles over sensitive areas with better error handling
     */
    private Bitmap createCensoredImage(Bitmap originalBitmap, Text visionText,
                                       List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches) {

        if (originalBitmap == null || originalBitmap.isRecycled()) {
            Log.e(TAG, "Original bitmap is null or recycled");
            return null;
        }

        Bitmap censoredBitmap = null;
        Canvas canvas = null;

        try {
            censoredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (censoredBitmap == null) {
                Log.e(TAG, "Failed to create bitmap copy");
                return null;
            }

            canvas = new Canvas(censoredBitmap);

            Paint blackPaint = new Paint();
            blackPaint.setColor(0xFF000000);
            blackPaint.setStyle(Paint.Style.FILL);
            blackPaint.setAntiAlias(true);

            String fullText = visionText.getText();
            int censoredCount = 0;

            for (SensitiveDataDetector.SensitiveMatch match : sensitiveMatches) {
                try {
                    List<Rect> boundingBoxes = findTextBoundingBoxes(visionText, match, fullText);

                    for (Rect rect : boundingBoxes) {
                        if (rect != null && isValidRect(rect, censoredBitmap.getWidth(), censoredBitmap.getHeight())) {
                            // Add padding and ensure within bounds
                            int padding = 10;
                            rect.left = Math.max(0, rect.left - padding);
                            rect.top = Math.max(0, rect.top - padding);
                            rect.right = Math.min(censoredBitmap.getWidth(), rect.right + padding);
                            rect.bottom = Math.min(censoredBitmap.getHeight(), rect.bottom + padding);

                            canvas.drawRect(rect, blackPaint);
                            censoredCount++;
                            Log.d(TAG, "Censored " + match.type + " at: " + rect.toString());
                        } else {
                            Log.w(TAG, "Invalid bounding box for " + match.type + ": " + rect);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error censoring match: " + match.type, e);
                    // Continue with other matches
                }
            }

            Log.d(TAG, "Successfully censored " + censoredCount + " sensitive areas");
            return censoredBitmap;

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory creating censored image", e);
            if (censoredBitmap != null && !censoredBitmap.isRecycled()) {
                censoredBitmap.recycle();
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error in createCensoredImage", e);
            if (censoredBitmap != null && !censoredBitmap.isRecycled()) {
                censoredBitmap.recycle();
            }
            return null;
        }
    }

    /**
     * Calculate sample size for large images to prevent OOM
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Validate rectangle bounds
     */
    private boolean isValidRect(Rect rect, int imageWidth, int imageHeight) {
        return rect != null &&
                rect.left >= 0 && rect.top >= 0 &&
                rect.right <= imageWidth && rect.bottom <= imageHeight &&
                rect.width() > 0 && rect.height() > 0;
    }

    /**
     * Find where sensitive text appears in the image with better error handling
     */
    private List<Rect> findTextBoundingBoxes(Text visionText, SensitiveDataDetector.SensitiveMatch match, String fullText) {
        List<Rect> boundingBoxes = new java.util.ArrayList<>();

        if (visionText == null || match == null || match.value == null) {
            Log.w(TAG, "Invalid parameters for findTextBoundingBoxes");
            return boundingBoxes;
        }

        String searchText = match.value.trim();
        if (searchText.isEmpty()) {
            Log.w(TAG, "Empty search text for match: " + match.type);
            return boundingBoxes;
        }

        try {
            for (Text.TextBlock block : visionText.getTextBlocks()) {
                if (block == null) continue;

                for (Text.Line line : block.getLines()) {
                    if (line == null) continue;

                    String lineText = line.getText();
                    if (lineText == null) continue;

                    if (containsSensitiveText(lineText, searchText, match.type)) {
                        Rect lineBounds = line.getBoundingBox();
                        if (lineBounds != null) {
                            boundingBoxes.add(new Rect(lineBounds));
                            Log.d(TAG, "Found " + match.type + " in line at: " + lineBounds.toString());
                        }
                    } else {
                        // Check individual elements
                        for (Text.Element element : line.getElements()) {
                            if (element == null) continue;

                            String elementText = element.getText();
                            if (elementText != null && containsSensitiveText(elementText, searchText, match.type)) {
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
        } catch (Exception e) {
            Log.e(TAG, "Error finding text bounding boxes", e);
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
        if (censoredBitmap == null || censoredBitmap.isRecycled()) {
            Log.e(TAG, "Cannot save null or recycled bitmap");
            return null;
        }

        FileOutputStream fos = null;
        try {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File censoredDir = new File(picturesDir, CENSORED_FOLDER);

            if (!censoredDir.exists()) {
                boolean created = censoredDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Could not create censored directory");
                    return null;
                }
            }

            String originalFileName = new File(originalPath).getName();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String censoredFileName = "censored_" + timestamp + "_" + originalFileName;

            File censoredFile = new File(censoredDir, censoredFileName);

            fos = new FileOutputStream(censoredFile);
            boolean compressed = censoredBitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);

            if (!compressed) {
                Log.e(TAG, "Failed to compress bitmap");
                return null;
            }

            fos.flush();
            Log.i(TAG, "Saved censored image: " + censoredFile.getAbsolutePath());

            // Tell Android about the new file so it appears in gallery
            addImageToMediaStore(censoredFile.getAbsolutePath());

            return censoredFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Error saving censored image", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error saving censored image", e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file output stream", e);
                }
            }
        }
    }

    /**
     * Add the new censored image to MediaStore so it appears in gallery apps
     */
    private void addImageToMediaStore(String imagePath) {
        try {
            if (context == null) {
                Log.w(TAG, "Context is null, cannot add to MediaStore");
                return;
            }

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

    private boolean deleteOriginalScreenshot(String imagePath) {
        // Try MediaStore deletion first (Android 10+)
        if (context != null && deleteOriginalScreenshotViaMediaStore(imagePath)) {
            Log.i(TAG, "Successfully deleted original screenshot via MediaStore: " + imagePath);
            return true;
        }

        // Fallback to direct file deletion
        try {
            File originalFile = new File(imagePath);
            if (originalFile.exists()) {
                boolean deleted = originalFile.delete();
                if (deleted) {
                    Log.i(TAG, "Deleted original screenshot: " + imagePath);
                    return true;
                } else {
                    Log.w(TAG, "Could not delete original screenshot: " + imagePath);
                    return false;
                }
            } else {
                Log.w(TAG, "Original file doesn't exist: " + imagePath);
                return true; // Consider it "deleted" if it doesn't exist
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting original screenshot", e);
            return false;
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
        if (value == null || value.length() < 4) return "***";

        switch (type) {
            case "CREDIT_CARD":
                if (value.length() >= 8) {
                    return value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
                }
                return "****";
            case "SSN":
                if (value.length() >= 4) {
                    return "***-**-" + value.substring(value.length() - 4);
                }
                return "***-**-****";
            case "PHONE":
                if (value.length() >= 4) {
                    return "***-***-" + value.substring(value.length() - 4);
                }
                return "***-***-****";
            case "EMAIL":
                int atIndex = value.indexOf('@');
                if (atIndex > 2) {
                    return value.substring(0, 2) + "***" + value.substring(atIndex);
                }
                return "***@***";
            default:
                return "***";
        }
    }
}