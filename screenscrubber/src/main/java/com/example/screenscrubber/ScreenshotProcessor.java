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
import android.media.ExifInterface;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import com.google.mlkit.vision.text.Text;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

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
            return new ProcessingResult(false, null, new ArrayList<>(), originalImagePath, false, errorMessage, imageType);
        }
    }

    public ScreenshotProcessor() {
        this.sensitiveDataDetector = new SensitiveDataDetector();
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Enhanced processing pipeline for different image types
     */
    public ProcessingResult processImage(String imagePath, Text visionText, MediaObserver.ImageType imageType) {
        long startTime = System.currentTimeMillis();

        // Input validation
        if (imagePath == null || imagePath.isEmpty()) {
            Log.e(TAG, "Invalid image path provided");
            return ProcessingResult.error("Invalid image path", null, imageType);
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Log.e(TAG, "Image file does not exist: " + imagePath);
            return ProcessingResult.error("Image file not found", imagePath, imageType);
        }

        if (visionText == null) {
            Log.e(TAG, "Vision text is null");
            return ProcessingResult.error("Text extraction failed", imagePath, imageType);
        }

        try {
            // Step 1: Extract and analyze text for sensitive data
            String fullText = visionText.getText();
            Log.d(TAG, "Analyzing text from " + imageType + ": " + imagePath);

            List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches;
            if (fullText.isEmpty()) {
                Log.d(TAG, "No text found in image");
                sensitiveMatches = new ArrayList<>();
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
     * PRECISE CENSORING: Create censored version with character-level precision
     */
    private ProcessingResult createCensoredVersion(String imagePath, Text visionText,
                                                   List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                                   MediaObserver.ImageType imageType) {
        Bitmap originalBitmap = null;
        Bitmap censoredBitmap = null;

        try {
            // Load original image
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            if (options.outWidth > MAX_IMAGE_DIMENSION || options.outHeight > MAX_IMAGE_DIMENSION) {
                Log.w(TAG, "Large image detected: " + options.outWidth + "x" + options.outHeight);
                options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION);
            }

            options.inJustDecodeBounds = false;
            originalBitmap = BitmapFactory.decodeFile(imagePath, options);

            if (originalBitmap == null) {
                Log.e(TAG, "Could not load original image");
                return ProcessingResult.error("Could not load image", imagePath, imageType);
            }

            // Create censored bitmap with PRECISE character-level redaction
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

            // Save the final censored image
            String censoredPath = saveCensoredImage(finalBitmap, imagePath, imageType);
            if (censoredPath == null) {
                return ProcessingResult.error("Failed to save censored image", imagePath, imageType);
            }

            // Handle original image
            boolean originalHandled = handleOriginalImage(imagePath, imageType);
            if (!originalHandled) {
                Log.w(TAG, "Could not handle original " + imageType + ", but continuing");
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
     * PRECISE CHARACTER-LEVEL CENSORING
     * This method creates precise bounding boxes for only the sensitive characters
     */
    private Bitmap createPreciseCensoredImage(Bitmap originalBitmap, Text visionText,
                                              List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches) {

        if (originalBitmap == null || originalBitmap.isRecycled()) {
            Log.e(TAG, "Original bitmap is null or recycled");
            return null;
        }

        Log.d(TAG, "🎯 Creating PRECISE censored image");
        Log.d(TAG, "   📏 Image dimensions: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
        Log.d(TAG, "   🔍 Sensitive matches: " + sensitiveMatches.size());

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

            // For each sensitive match, find PRECISE character boundaries
            for (SensitiveDataDetector.SensitiveMatch match : sensitiveMatches) {
                try {
                    Log.d(TAG, "🎯 Processing match: " + match.type + " = '" + match.value + "'");

                    List<Rect> preciseBoxes = findPreciseCharacterBoxes(visionText, match);
                    Log.d(TAG, "   📦 Found " + preciseBoxes.size() + " precise character boxes");

                    for (Rect rect : preciseBoxes) {
                        if (rect != null && isValidRect(rect, censoredBitmap.getWidth(), censoredBitmap.getHeight())) {

                            // Add minimal padding (just 2-4 pixels for better coverage)
                            int padding = 3;
                            Rect paddedRect = new Rect(
                                    Math.max(0, rect.left - padding),
                                    Math.max(0, rect.top - padding),
                                    Math.min(censoredBitmap.getWidth(), rect.right + padding),
                                    Math.min(censoredBitmap.getHeight(), rect.bottom + padding)
                            );

                            canvas.drawRect(paddedRect, blackPaint);
                            censoredCount++;

                            Log.d(TAG, "   ✅ Censored precise area: " + paddedRect.toString());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error censoring match: " + match.type, e);
                }
            }

            Log.d(TAG, "🎨 Successfully censored " + censoredCount + " precise areas");
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
     * PRECISE CHARACTER FINDER: Find exact character-level bounding boxes
     */
    private List<Rect> findPreciseCharacterBoxes(Text visionText, SensitiveDataDetector.SensitiveMatch match) {
        List<Rect> preciseBoxes = new ArrayList<>();

        if (visionText == null || match == null || match.value == null) {
            return preciseBoxes;
        }

        String searchValue = match.value.trim();
        Log.d(TAG, "🔍 Looking for precise characters: '" + searchValue + "'");

        // Search through all text elements for character-level matches
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            if (block == null) continue;

            for (Text.Line line : block.getLines()) {
                if (line == null) continue;

                for (Text.Element element : line.getElements()) {
                    if (element == null) continue;

                    String elementText = element.getText();
                    if (elementText == null) continue;

                    // Check if this element contains our sensitive data
                    if (containsSensitiveDataPrecise(elementText, searchValue, match.type)) {
                        Rect elementBounds = element.getBoundingBox();
                        if (elementBounds != null) {

                            // For elements that exactly match or contain the sensitive data,
                            // calculate precise character boundaries
                            List<Rect> charBoxes = calculateCharacterBounds(elementText, searchValue, elementBounds, match.type);
                            preciseBoxes.addAll(charBoxes);

                            Log.d(TAG, "   ✅ Found in element: '" + elementText + "' -> " + charBoxes.size() + " char boxes");
                        }
                    }
                }
            }
        }

        Log.d(TAG, "🎯 Total precise boxes found: " + preciseBoxes.size());
        return preciseBoxes;
    }

    /**
     * CALCULATE COMPLETE SENSITIVE DATA BOUNDARIES within an element
     * Covers the ENTIRE sensitive value (complete email, complete phone, etc.)
     */
    private List<Rect> calculateCharacterBounds(String elementText, String sensitiveValue, Rect elementBounds, String type) {
        List<Rect> charBoxes = new ArrayList<>();

        Log.d(TAG, "   📊 Element: '" + elementText + "'");
        Log.d(TAG, "   📊 Sensitive: '" + sensitiveValue + "' (type: " + type + ")");

        int startIndex = findSensitiveDataStart(elementText, sensitiveValue, type);

        if (startIndex >= 0) {
            int endIndex;

            // Special handling for different data types to ensure complete coverage
            if (type.contains("PHONE")) {
                // For phones, cover from first digit to last digit (including formatting)
                endIndex = findLastPhoneCharacter(elementText, startIndex);
            } else if (type.contains("CREDIT_CARD")) {
                // For credit cards, cover all digits and spaces
                endIndex = findLastCreditCardCharacter(elementText, startIndex);
            } else if (type.contains("EMAIL")) {
                // For emails, cover the complete email address
                endIndex = findEmailEnd(elementText, startIndex);
            } else {
                // For other types (SSN, ID), use the sensitive value length
                endIndex = startIndex + sensitiveValue.length();
            }

            // Make sure we don't go beyond the element text length
            endIndex = Math.min(endIndex, elementText.length());

            // Calculate the portion of the element bounds to censor
            float elementWidth = elementBounds.width();
            float elementTextLength = elementText.length();

            if (elementTextLength > 0) {
                float charWidth = elementWidth / elementTextLength;

                int leftOffset = (int) (startIndex * charWidth);
                int rightOffset = (int) (endIndex * charWidth);

                // Add padding to ensure complete coverage
                int padding = Math.max(2, (int) (charWidth * 0.3));

                Rect charRect = new Rect(
                        Math.max(elementBounds.left, elementBounds.left + leftOffset - padding),
                        elementBounds.top,
                        Math.min(elementBounds.right, elementBounds.left + rightOffset + padding),
                        elementBounds.bottom
                );

                charBoxes.add(charRect);
                Log.d(TAG, "   🎯 Complete " + type + " bounds: " + charRect.toString());
                Log.d(TAG, "   📏 Covering characters " + startIndex + " to " + endIndex + " ('" +
                        elementText.substring(startIndex, endIndex) + "')");
            }
        } else {
            // If we can't find the exact position, check if this element contains the sensitive data
            if (containsSensitiveDataPrecise(elementText, sensitiveValue, type)) {
                charBoxes.add(new Rect(elementBounds));
                Log.d(TAG, "   ⚠️ Fallback - censoring entire element: " + elementText);
            }
        }

        return charBoxes;
    }

    private int findLastPhoneCharacter(String text, int startIndex) {
        int lastDigitIndex = startIndex;
        int digitCount = 0;

        // Look for phone number pattern: find last digit, but include formatting
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                lastDigitIndex = i;
                digitCount++;
            } else if (c == ' ' || c == '-' || c == '(' || c == ')' || c == '.') {
                // Include formatting characters if they're between digits
                if (i < text.length() - 1 && hasMoreDigitsAhead(text, i + 1)) {
                    continue;
                } else if (digitCount >= 7) {
                    // We have enough digits, stop here
                    break;
                }
            } else {
                // Non-phone character, stop if we have enough digits
                if (digitCount >= 7) break;
            }
        }

        return lastDigitIndex + 1;
    }

    /**
     * Find the end of a credit card number (including spaces and dashes)
     */
    private int findLastCreditCardCharacter(String text, int startIndex) {
        int lastIndex = startIndex;
        int digitCount = 0;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                lastIndex = i;
                digitCount++;
            } else if (c == ' ' || c == '-') {
                // Include formatting characters
                if (hasMoreDigitsAhead(text, i + 1)) {
                    lastIndex = i;
                } else if (digitCount >= 13) {
                    break;
                }
            } else {
                // Non-card character
                if (digitCount >= 13) break;
            }
        }

        return lastIndex + 1;
    }

    /**
     * Find the end of an email address
     */
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
                // Stop at whitespace after @ and domain
                break;
            } else if (!foundAt && (c == ' ' || c == '\t' || c == '\n')) {
                // Stop at whitespace before @
                break;
            }
        }

        return endIndex + 1;
    }

    /**
     * Check if there are more digits ahead in the text
     */
    private boolean hasMoreDigitsAhead(String text, int fromIndex) {
        for (int i = fromIndex; i < Math.min(fromIndex + 5, text.length()); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the starting position of sensitive data in the element text
     */
    private int findSensitiveDataStart(String elementText, String sensitiveValue, String type) {
        if (elementText == null || sensitiveValue == null) return -1;

        // Try exact match first
        int exactIndex = elementText.indexOf(sensitiveValue);
        if (exactIndex >= 0) {
            Log.d(TAG, "   ✅ Found exact match at index " + exactIndex);
            return exactIndex;
        }

        // Try case-insensitive match
        int caseIndex = elementText.toLowerCase().indexOf(sensitiveValue.toLowerCase());
        if (caseIndex >= 0) {
            Log.d(TAG, "   ✅ Found case-insensitive match at index " + caseIndex);
            return caseIndex;
        }

        // For phone numbers, try matching by digits pattern
        if (type.contains("PHONE")) {
            String elementDigits = elementText.replaceAll("[^0-9]", "");
            String sensitiveDigits = sensitiveValue.replaceAll("[^0-9]", "");

            if (elementDigits.equals(sensitiveDigits) ||
                    (elementDigits.length() >= 7 && sensitiveDigits.length() >= 7 &&
                            (elementDigits.contains(sensitiveDigits) || sensitiveDigits.contains(elementDigits)))) {

                // For phone numbers, if digits match, cover from first digit
                for (int i = 0; i < elementText.length(); i++) {
                    if (Character.isDigit(elementText.charAt(i))) {
                        Log.d(TAG, "   📞 Phone digits match - starting from " + i);
                        return i;
                    }
                }
            }
        }

        // For credit cards, match by digits
        if (type.contains("CREDIT_CARD")) {
            String elementDigits = elementText.replaceAll("[^0-9]", "");
            String sensitiveDigits = sensitiveValue.replaceAll("[^0-9]", "");

            if (elementDigits.equals(sensitiveDigits)) {
                // Cover from first digit
                for (int i = 0; i < elementText.length(); i++) {
                    if (Character.isDigit(elementText.charAt(i))) {
                        Log.d(TAG, "   💳 Credit card digits match - starting from " + i);
                        return i;
                    }
                }
            }
        }

        // For emails, find the @ symbol and work backwards/forwards
        if (type.contains("EMAIL")) {
            String lowerElement = elementText.toLowerCase();
            String lowerSensitive = sensitiveValue.toLowerCase();

            int atIndex = lowerElement.indexOf("@");
            if (atIndex >= 0 && lowerSensitive.contains("@")) {
                // Find the start of the email by looking backwards from @
                int emailStart = atIndex;
                while (emailStart > 0) {
                    char c = elementText.charAt(emailStart - 1);
                    if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-' || c == '+') {
                        emailStart--;
                    } else {
                        break;
                    }
                }
                Log.d(TAG, "   📧 Email found starting from " + emailStart);
                return emailStart;
            }
        }

        Log.d(TAG, "   ❌ No match found for '" + sensitiveValue + "' in '" + elementText + "'");
        return -1;
    }

    /**
     * PRECISE matching for sensitive data in text elements
     */
    private boolean containsSensitiveDataPrecise(String text, String sensitiveText, String type) {
        if (text == null || sensitiveText == null) return false;

        // Clean both for comparison
        String cleanText = text.replaceAll("[\\s\\-\\(\\)\\.]", "");
        String cleanSensitive = sensitiveText.replaceAll("[\\s\\-\\(\\)\\.]", "");

        if (type.contains("PHONE")) {
            // For phones, compare digits only
            String textDigits = text.replaceAll("[^0-9]", "");
            String sensitiveDigits = sensitiveText.replaceAll("[^0-9]", "");

            return textDigits.contains(sensitiveDigits) ||
                    sensitiveDigits.contains(textDigits) ||
                    textDigits.equals(sensitiveDigits);
        } else {
            // For other types, check if the text contains the sensitive data
            return cleanText.contains(cleanSensitive) ||
                    cleanSensitive.contains(cleanText) ||
                    text.toLowerCase().contains(sensitiveText.toLowerCase()) ||
                    sensitiveText.toLowerCase().contains(text.toLowerCase());
        }
    }

    // ... [Keep all the other helper methods unchanged: fixImageOrientation, saveCensoredImage, etc.] ...

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

    private boolean handleOriginalImage(String imagePath, MediaObserver.ImageType imageType) {
        try {
            return deleteOriginalImage(imagePath);
        } catch (Exception e) {
            Log.e(TAG, "Error handling original image", e);
            return false;
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

    private String maskSensitiveValue(String value, String type) {
        if (value == null || value.length() < 4) return "***";

        switch (type) {
            case "CREDIT_CARD":
                return value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
            case "US_SSN":
                return "***-**-****";
            case "ISRAELI_ID":
                return "***-***-***";
            default:
                return "***";
        }
    }
}