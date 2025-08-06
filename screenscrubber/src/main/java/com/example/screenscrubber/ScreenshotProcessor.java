package com.example.screenscrubber;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import com.google.mlkit.vision.text.Text;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.ArrayList;

/**
 * Image processing and file operations ONLY
 * - File validation
 * - Image censoring
 * - File saving/deleting
 * - MediaStore operations
 */
public class ScreenshotProcessor {
    private static final String TAG = "ScreenshotProcessor";
    private static final String CENSORED_FOLDER = "ScreenScrubber_Censored";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB max
    private static final int MAX_IMAGE_DIMENSION = 4096; // Max width/height

    private final SensitiveDataDetector sensitiveDataDetector;
    private final Context context;

    public static class ProcessingResult {
        public final boolean hasSensitiveData;
        public final String censoredImagePath;
        public final List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches;
        public final String originalImagePath;
        public final boolean success;
        public final String errorMessage;
        public final MediaObserver.ImageType imageType;

        public ProcessingResult(boolean hasSensitiveData, String censoredImagePath,
                                List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                String originalImagePath, boolean success, String errorMessage,
                                MediaObserver.ImageType imageType) {
            this.hasSensitiveData = hasSensitiveData;
            this.censoredImagePath = censoredImagePath;
            this.sensitiveMatches = sensitiveMatches;
            this.originalImagePath = originalImagePath;
            this.success = success;
            this.errorMessage = errorMessage;
            this.imageType = imageType;
        }

        public static ProcessingResult success(boolean hasSensitiveData, String censoredImagePath,
                                               List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                               String originalImagePath, MediaObserver.ImageType imageType) {
            return new ProcessingResult(hasSensitiveData, censoredImagePath, sensitiveMatches,
                    originalImagePath, true, null, imageType);
        }

        public static ProcessingResult error(String errorMessage, String originalImagePath,
                                             MediaObserver.ImageType imageType) {
            return new ProcessingResult(false, null, new ArrayList<>(), originalImagePath,
                    false, errorMessage, imageType);
        }
    }

    public ScreenshotProcessor(Context context) {
        this.context = context;
        this.sensitiveDataDetector = new SensitiveDataDetector();
    }

    /**
     * Complete image processing pipeline with ALL validation
     */
    public ProcessingResult processImage(String imagePath, Text visionText, MediaObserver.ImageType imageType) {
        long startTime = System.currentTimeMillis();

        // Step 1: Validate ALL inputs and file conditions
        ProcessingResult validationResult = validateInputs(imagePath, visionText, imageType);
        if (!validationResult.success) {
            return validationResult;
        }

        try {
            // Step 2: Extract and analyze text for sensitive data
            String fullText = visionText.getText();
            Log.d(TAG, "Analyzing text from " + imageType + ": " + imagePath);

            List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches;
            if (fullText.isEmpty()) {
                Log.d(TAG, "No text found in image");
                sensitiveMatches = new ArrayList<>();
            } else {
                sensitiveMatches = sensitiveDataDetector.detectSensitiveData(fullText);
                Log.d(TAG, "Found " + sensitiveMatches.size() + " sensitive data matches");
            }

            // Step 3: Process based on findings
            ProcessingResult result;
            if (sensitiveMatches.isEmpty()) {
                Log.d(TAG, "No sensitive data found in " + imageType + " - keeping original");
                result = ProcessingResult.success(false, null, sensitiveMatches, imagePath, imageType);
            } else {
                Log.d(TAG, "Sensitive data found in " + imageType + " - creating censored version");
                result = createCensoredVersion(imagePath, visionText, sensitiveMatches, imageType);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, imageType + " processing completed in " + processingTime + "ms");
            return result;

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory processing " + imageType, e);
            return ProcessingResult.error("Out of memory", imagePath, imageType);
        } catch (Exception e) {
            Log.e(TAG, "Error processing " + imageType, e);
            return ProcessingResult.error("Processing failed: " + e.getMessage(), imagePath, imageType);
        }
    }

    /**
     * COMPREHENSIVE input and file validation - single responsibility
     */
    private ProcessingResult validateInputs(String imagePath, Text visionText, MediaObserver.ImageType imageType) {
        // Basic parameter validation
        if (imagePath == null || imagePath.isEmpty()) {
            Log.e(TAG, "Invalid image path provided");
            return ProcessingResult.error("Invalid image path", null, imageType);
        }

        if (visionText == null) {
            Log.e(TAG, "Vision text is null");
            return ProcessingResult.error("Text extraction failed", imagePath, imageType);
        }

        // File existence and properties
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Log.e(TAG, "Image file does not exist: " + imagePath);
            return ProcessingResult.error("Image file not found", imagePath, imageType);
        }

        if (!imageFile.canRead()) {
            Log.w(TAG, "Cannot read image file: " + imagePath);
            return ProcessingResult.error("Cannot read image file", imagePath, imageType);
        }

        long fileSize = imageFile.length();
        if (fileSize == 0) {
            Log.w(TAG, "Image file is empty: " + imagePath);
            return ProcessingResult.error("Image file is empty", imagePath, imageType);
        }

        if (fileSize > MAX_FILE_SIZE) {
            Log.w(TAG, "Image file too large: " + fileSize + " bytes (max: " + MAX_FILE_SIZE + ")");
            return ProcessingResult.error("Image file too large", imagePath, imageType);
        }

        // Try to decode the image to verify it's valid
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: " + options.outWidth + "x" + options.outHeight);
                return ProcessingResult.error("Invalid image format", imagePath, imageType);
            }

            if (options.outWidth > MAX_IMAGE_DIMENSION || options.outHeight > MAX_IMAGE_DIMENSION) {
                Log.w(TAG, "Large image detected: " + options.outWidth + "x" + options.outHeight);
                // This is just a warning, not an error - we can handle large images
            }

        } catch (Exception e) {
            Log.e(TAG, "Error validating image format", e);
            return ProcessingResult.error("Cannot decode image", imagePath, imageType);
        }

        // All validation passed
        return ProcessingResult.success(false, null, new ArrayList<>(), imagePath, imageType);
    }

    /**
     * Create censored version - focus on image processing operations
     */
    private ProcessingResult createCensoredVersion(String imagePath, Text visionText,
                                                   List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                                   MediaObserver.ImageType imageType) {
        Bitmap originalBitmap = null;
        Bitmap censoredBitmap = null;

        try {
            // Load and potentially resize image
            originalBitmap = loadOptimizedBitmap(imagePath);
            if (originalBitmap == null) {
                Log.e(TAG, "Could not load original image");
                return ProcessingResult.error("Could not load image", imagePath, imageType);
            }

            // Create censored bitmap with precise character-level redaction
            censoredBitmap = createPreciseCensoredImage(originalBitmap, visionText, sensitiveMatches);
            if (censoredBitmap == null) {
                return ProcessingResult.error("Failed to create censored image", imagePath, imageType);
            }

            // Fix orientation if needed (for camera photos)
            Bitmap finalBitmap = censoredBitmap;
            if (imageType == MediaObserver.ImageType.CAMERA_PHOTO) {
                Bitmap rotated = fixImageOrientation(censoredBitmap, imagePath);
                if (rotated != null && rotated != censoredBitmap) {
                    finalBitmap = rotated;
                    censoredBitmap.recycle();
                }
            }

            // Save the censored image
            String censoredPath = saveCensoredImage(finalBitmap, imagePath, imageType);
            if (censoredPath == null) {
                return ProcessingResult.error("Failed to save censored image", imagePath, imageType);
            }

            // Delete original image
            boolean originalDeleted = deleteOriginalImage(imagePath);
            if (!originalDeleted) {
                Log.w(TAG, "Could not delete original " + imageType + ", but continuing");
            }

            return ProcessingResult.success(true, censoredPath, sensitiveMatches, imagePath, imageType);

        } catch (Exception e) {
            Log.e(TAG, "Error creating censored version", e);
            return ProcessingResult.error("Censoring failed: " + e.getMessage(), imagePath, imageType);
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
     * Load bitmap with optimization for large images
     */
    private Bitmap loadOptimizedBitmap(String imagePath) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            if (options.outWidth > MAX_IMAGE_DIMENSION || options.outHeight > MAX_IMAGE_DIMENSION) {
                Log.w(TAG, "Large image detected: " + options.outWidth + "x" + options.outHeight);
                options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION);
            }

            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(imagePath, options);

        } catch (Exception e) {
            Log.e(TAG, "Error loading optimized bitmap", e);
            return null;
        }
    }

    /**
     * Create precise censored image with character-level accuracy
     */
    private Bitmap createPreciseCensoredImage(Bitmap originalBitmap, Text visionText,
                                              List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches) {

        if (originalBitmap == null || originalBitmap.isRecycled()) {
            Log.e(TAG, "Original bitmap is null or recycled");
            return null;
        }

        Log.d(TAG, "Creating precise censored image - " + sensitiveMatches.size() + " matches to process");

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

            int censoredCount = 0;

            // For each sensitive match, find precise character boundaries
            for (SensitiveDataDetector.SensitiveMatch match : sensitiveMatches) {
                try {
                    Log.d(TAG, "Processing match: " + match.type);

                    List<Rect> preciseBoxes = findPreciseCharacterBoxes(visionText, match);
                    Log.d(TAG, "Found " + preciseBoxes.size() + " precise character boxes");

                    // Fallback: If no precise boxes found, use word-level boxes
                    if (preciseBoxes.isEmpty()) {
                        preciseBoxes = findWordBoxes(visionText, match);
                        Log.d(TAG, "Fallback: Found " + preciseBoxes.size() + " word boxes");
                    }

                    for (Rect rect : preciseBoxes) {
                        if (rect != null && isValidRect(rect, censoredBitmap.getWidth(), censoredBitmap.getHeight())) {
                            // Add minimal padding for better coverage
                            int padding = 3;
                            Rect paddedRect = new Rect(
                                    Math.max(0, rect.left - padding),
                                    Math.max(0, rect.top - padding),
                                    Math.min(censoredBitmap.getWidth(), rect.right + padding),
                                    Math.min(censoredBitmap.getHeight(), rect.bottom + padding)
                            );

                            canvas.drawRect(paddedRect, blackPaint);
                            censoredCount++;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error censoring match: " + match.type, e);
                }
            }

            Log.d(TAG, "Successfully censored " + censoredCount + " precise areas");
            return censoredBitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error in createPreciseCensoredImage", e);
            if (censoredBitmap != null && !censoredBitmap.isRecycled()) {
                censoredBitmap.recycle();
            }
            return null;
        }
    }

    /**
     * Find precise character-level bounding boxes
     */
    private List<Rect> findPreciseCharacterBoxes(Text visionText, SensitiveDataDetector.SensitiveMatch match) {
        List<Rect> preciseBoxes = new ArrayList<>();

        if (visionText == null || match == null || match.value == null) {
            return preciseBoxes;
        }

        String searchValue = match.value.trim();

        // Search through all text elements for character-level matches
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            if (block == null) continue;

            for (Text.Line line : block.getLines()) {
                if (line == null) continue;

                for (Text.Element element : line.getElements()) {
                    if (element == null) continue;

                    String elementText = element.getText();
                    if (elementText == null) continue;

                    // Only process if element actually contains the sensitive data
                    if (actuallyContainsSensitiveData(elementText, searchValue, match.type)) {
                        Rect elementBounds = element.getBoundingBox();
                        if (elementBounds != null) {
                            List<Rect> charBoxes = calculateCharacterBounds(elementText, searchValue, elementBounds, match.type);
                            preciseBoxes.addAll(charBoxes);
                        }
                    }
                }
            }
        }

        return preciseBoxes;
    }

    /**
     * Fallback: Find word-level boxes when character-level fails
     */
    private List<Rect> findWordBoxes(Text visionText, SensitiveDataDetector.SensitiveMatch match) {
        List<Rect> wordBoxes = new ArrayList<>();
        String searchValue = match.value.trim();
        String normalizedMatch = normalizeText(searchValue);

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            if (block == null) continue;

            for (Text.Line line : block.getLines()) {
                if (line == null) continue;

                String lineText = line.getText();
                if (lineText != null && normalizeText(lineText).contains(normalizedMatch)) {
                    // Union all element boxes in this line
                    Rect unionRect = null;
                    for (Text.Element element : line.getElements()) {
                        if (element.getBoundingBox() != null) {
                            if (unionRect == null) {
                                unionRect = new Rect(element.getBoundingBox());
                            } else {
                                unionRect.union(element.getBoundingBox());
                            }
                        }
                    }
                    if (unionRect != null) {
                        wordBoxes.add(unionRect);
                    }
                }
            }
        }

        return wordBoxes;
    }

    /**
     * Precise matching logic to avoid false positives
     */
    private boolean actuallyContainsSensitiveData(String elementText, String sensitiveValue, String type) {
        if (elementText == null || sensitiveValue == null) return false;

        String normalizedElement = normalizeText(elementText);
        String normalizedSensitive = normalizeText(sensitiveValue);

        // For exact matches first
        if (normalizedElement.equals(normalizedSensitive)) {
            return true;
        }

        // Type-specific matching
        if (type.contains("PHONE")) {
            String elementDigits = elementText.replaceAll("[^0-9]", "");
            String sensitiveDigits = sensitiveValue.replaceAll("[^0-9]", "");
            return elementDigits.length() >= 7 && sensitiveDigits.length() >= 7 &&
                    elementDigits.equals(sensitiveDigits);
        }

        if (type.contains("CREDIT_CARD")) {
            String elementDigits = elementText.replaceAll("[^0-9]", "");
            String sensitiveDigits = sensitiveValue.replaceAll("[^0-9]", "");
            return elementDigits.length() >= 13 && sensitiveDigits.length() >= 13 &&
                    elementDigits.equals(sensitiveDigits);
        }

        if (type.contains("EMAIL")) {
            return elementText.contains("@") && sensitiveValue.contains("@") &&
                    normalizedElement.equals(normalizedSensitive);
        }

        if (type.contains("SSN") || type.contains("ID")) {
            String elementClean = elementText.replaceAll("[^0-9]", "");
            String sensitiveClean = sensitiveValue.replaceAll("[^0-9]", "");
            return elementClean.equals(sensitiveClean) && elementClean.length() >= 9;
        }

        if (type.contains("BANK")) {
            String elementClean = elementText.replaceAll("[^0-9\\-]", "");
            String sensitiveClean = sensitiveValue.replaceAll("[^0-9\\-]", "");
            return elementClean.equals(sensitiveClean) && elementClean.length() >= 8;
        }

        return false;
    }

    /**
     * Calculate character bounds within an element
     */
    private List<Rect> calculateCharacterBounds(String elementText, String sensitiveValue, Rect elementBounds, String type) {
        List<Rect> charBoxes = new ArrayList<>();

        int startIndex = findSensitiveDataStart(elementText, sensitiveValue, type);
        if (startIndex >= 0) {
            int endIndex = findSensitiveDataEnd(elementText, sensitiveValue, startIndex, type);
            endIndex = Math.min(endIndex, elementText.length());

            if (startIndex < endIndex) {
                float elementWidth = elementBounds.width();
                float elementTextLength = elementText.length();

                if (elementTextLength > 0) {
                    float charWidth = elementWidth / elementTextLength;

                    int leftOffset = (int) (startIndex * charWidth);
                    int rightOffset = (int) (endIndex * charWidth);

                    int padding = Math.max(2, (int) (charWidth * 0.3));

                    Rect charRect = new Rect(
                            Math.max(elementBounds.left, elementBounds.left + leftOffset - padding),
                            elementBounds.top,
                            Math.min(elementBounds.right, elementBounds.left + rightOffset + padding),
                            elementBounds.bottom
                    );

                    charBoxes.add(charRect);
                }
            }
        }

        return charBoxes;
    }

    /**
     * Find starting position of sensitive data
     */
    private int findSensitiveDataStart(String elementText, String sensitiveValue, String type) {
        if (elementText == null || sensitiveValue == null) return -1;

        // Try exact match first
        int exactIndex = elementText.indexOf(sensitiveValue);
        if (exactIndex >= 0) {
            return exactIndex;
        }

        // Try case-insensitive match
        int caseIndex = elementText.toLowerCase().indexOf(sensitiveValue.toLowerCase());
        if (caseIndex >= 0) {
            return caseIndex;
        }

        // Type-specific matching for digits-only comparison
        if (type.contains("PHONE") || type.contains("CREDIT_CARD")) {
            String elementDigits = elementText.replaceAll("[^0-9]", "");
            String sensitiveDigits = sensitiveValue.replaceAll("[^0-9]", "");

            if (elementDigits.equals(sensitiveDigits)) {
                // Find first digit
                for (int i = 0; i < elementText.length(); i++) {
                    if (Character.isDigit(elementText.charAt(i))) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Find ending position of sensitive data
     */
    private int findSensitiveDataEnd(String elementText, String sensitiveValue, int startIndex, String type) {
        if (type.contains("PHONE")) {
            return findLastPhoneCharacter(elementText, startIndex);
        } else if (type.contains("CREDIT_CARD")) {
            return findLastCreditCardCharacter(elementText, startIndex);
        } else if (type.contains("EMAIL")) {
            return findEmailEnd(elementText, startIndex);
        } else {
            return startIndex + sensitiveValue.length();
        }
    }

    /**
     * Helper methods for finding data end positions
     */
    private int findLastPhoneCharacter(String text, int startIndex) {
        int lastDigitIndex = startIndex;
        int digitCount = 0;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                lastDigitIndex = i;
                digitCount++;
            } else if (c == ' ' || c == '-' || c == '(' || c == ')' || c == '.') {
                if (i < text.length() - 1 && hasMoreDigitsAhead(text, i + 1)) {
                    continue;
                } else if (digitCount >= 7) {
                    break;
                }
            } else {
                if (digitCount >= 7) break;
            }
        }

        return lastDigitIndex + 1;
    }

    private int findLastCreditCardCharacter(String text, int startIndex) {
        int lastIndex = startIndex;
        int digitCount = 0;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                lastIndex = i;
                digitCount++;
            } else if (c == ' ' || c == '-') {
                if (hasMoreDigitsAhead(text, i + 1)) {
                    lastIndex = i;
                } else if (digitCount >= 13) {
                    break;
                }
            } else {
                if (digitCount >= 13) break;
            }
        }

        return lastIndex + 1;
    }

    private int findEmailEnd(String text, int startIndex) {
        int endIndex = startIndex;
        boolean foundAt = false;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '@') {
                foundAt = true;
                endIndex = i;
            } else if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '+') {
                endIndex = i;
            } else if (foundAt && (c == ' ' || c == '\t' || c == '\n')) {
                break;
            } else if (!foundAt && (c == ' ' || c == '\t' || c == '\n')) {
                break;
            }
        }

        return endIndex + 1;
    }

    private boolean hasMoreDigitsAhead(String text, int fromIndex) {
        for (int i = fromIndex; i < Math.min(fromIndex + 5, text.length()); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * File operations - saving, deleting, MediaStore management
     */
    private String saveCensoredImage(Bitmap censoredBitmap, String originalPath, MediaObserver.ImageType imageType) {
        if (censoredBitmap == null || censoredBitmap.isRecycled()) {
            return null;
        }

        try {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File censoredDir = new File(picturesDir, CENSORED_FOLDER);

            if (!censoredDir.exists()) {
                censoredDir.mkdirs();
            }

            String originalFileName = new File(originalPath).getName();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String typePrefix = imageType == MediaObserver.ImageType.SCREENSHOT ? "screenshot" : "photo";
            String censoredFileName = "censored_" + typePrefix + "_" + timestamp + "_" + originalFileName;

            File censoredFile = new File(censoredDir, censoredFileName);
            FileOutputStream fos = new FileOutputStream(censoredFile);

            int quality = imageType == MediaObserver.ImageType.CAMERA_PHOTO ? 95 : 90;
            Bitmap.CompressFormat format = imageType == MediaObserver.ImageType.CAMERA_PHOTO ?
                    Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;

            censoredBitmap.compress(format, quality, fos);
            fos.close();

            addImageToMediaStore(censoredFile.getAbsolutePath());
            return censoredFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Error saving censored image", e);
            return null;
        }
    }

    private boolean deleteOriginalImage(String imagePath) {
        try {
            File originalFile = new File(imagePath);
            if (originalFile.exists()) {
                return originalFile.delete();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting original image", e);
            return false;
        }
    }

    private void addImageToMediaStore(String imagePath) {
        try {
            if (context == null) return;

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DATA, imagePath);
            context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Exception e) {
            Log.e(TAG, "Error adding to MediaStore", e);
        }
    }

    private Bitmap fixImageOrientation(Bitmap bitmap, String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                return bitmap;
            }

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            return rotatedBitmap != null ? rotatedBitmap : bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error fixing image orientation", e);
            return bitmap;
        }
    }

    /**
     * Utility methods
     */
    private String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", "").toLowerCase();
    }

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

    private boolean isValidRect(Rect rect, int imageWidth, int imageHeight) {
        return rect != null &&
                rect.left >= 0 && rect.top >= 0 &&
                rect.right <= imageWidth && rect.bottom <= imageHeight &&
                rect.width() > 0 && rect.height() > 0;
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        // ScreenshotProcessor doesn't hold long-term resources, but good practice
        Log.d(TAG, "ScreenshotProcessor cleanup completed");
    }
}