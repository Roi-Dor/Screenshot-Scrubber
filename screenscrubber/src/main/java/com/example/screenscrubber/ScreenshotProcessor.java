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

        // Convenience constructors
        public static ProcessingResult success(boolean hasSensitiveData, String censoredImagePath,
                                               List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                               String originalImagePath, MediaObserver.ImageType imageType) {
            return new ProcessingResult(hasSensitiveData, censoredImagePath, sensitiveMatches,
                    originalImagePath, true, null, imageType);
        }

        public static ProcessingResult error(String errorMessage, String originalImagePath,
                                             MediaObserver.ImageType imageType) {
            return new ProcessingResult(false, null, List.of(), originalImagePath, false, errorMessage, imageType);
        }
    }

    public ScreenshotProcessor() {
        this.sensitiveDataDetector = new SensitiveDataDetector();
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * FIXED: Fix image orientation based on EXIF data (for camera photos)
     */
    private Bitmap fixImageOrientation(Bitmap bitmap, String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Log.d(TAG, "Original image orientation: " + orientation);

            // If orientation is normal, return original bitmap
            if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                Log.d(TAG, "No rotation needed");
                return bitmap;
            }

            Matrix matrix = new Matrix();
            boolean needsRotation = false;

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    Log.d(TAG, "Rotating image 90 degrees clockwise");
                    matrix.postRotate(90);
                    needsRotation = true;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    Log.d(TAG, "Rotating image 180 degrees");
                    matrix.postRotate(180);
                    needsRotation = true;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    Log.d(TAG, "Rotating image 270 degrees clockwise");
                    matrix.postRotate(270);
                    needsRotation = true;
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    Log.d(TAG, "Flipping image horizontally");
                    matrix.preScale(-1.0f, 1.0f);
                    needsRotation = true;
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    Log.d(TAG, "Flipping image vertically");
                    matrix.preScale(1.0f, -1.0f);
                    needsRotation = true;
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    Log.d(TAG, "Transposing image");
                    matrix.preScale(-1.0f, 1.0f);
                    matrix.postRotate(90);
                    needsRotation = true;
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    Log.d(TAG, "Transversing image");
                    matrix.preScale(-1.0f, 1.0f);
                    matrix.postRotate(270);
                    needsRotation = true;
                    break;
                default:
                    Log.d(TAG, "Unknown orientation: " + orientation + ", no rotation applied");
                    return bitmap;
            }

            if (!needsRotation) {
                return bitmap;
            }

            // Create rotated bitmap
            Bitmap rotatedBitmap = null;
            try {
                rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
                );

                if (rotatedBitmap != null && rotatedBitmap != bitmap) {
                    Log.d(TAG, "Image orientation corrected successfully");
                    Log.d(TAG, "Original size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    Log.d(TAG, "Rotated size: " + rotatedBitmap.getWidth() + "x" + rotatedBitmap.getHeight());
                    return rotatedBitmap;
                } else {
                    Log.w(TAG, "Failed to create rotated bitmap");
                    return bitmap;
                }
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Out of memory creating rotated bitmap", e);
                return bitmap;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fixing image orientation", e);
            return bitmap; // Return original if rotation fails
        }
    }

    /**
     * FIXED: Copy EXIF data from original to censored image
     */
    private void copyExifData(String originalPath, String newPath) {
        try {
            ExifInterface originalExif = new ExifInterface(originalPath);
            ExifInterface newExif = new ExifInterface(newPath);

            // Copy only commonly available EXIF attributes (API level safe)
            String[] exifTags = {
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_IMAGE_LENGTH,
                    ExifInterface.TAG_IMAGE_WIDTH,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_WHITE_BALANCE,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_DATESTAMP
            };

            for (String tag : exifTags) {
                try {
                    String value = originalExif.getAttribute(tag);
                    if (value != null) {
                        newExif.setAttribute(tag, value);
                    }
                } catch (Exception e) {
                    // Skip this tag if it causes issues
                    Log.d(TAG, "Skipping EXIF tag: " + tag);
                }
            }

            // IMPORTANT: Set orientation to normal since we've already rotated the image
            newExif.setAttribute(ExifInterface.TAG_ORIENTATION,
                    String.valueOf(ExifInterface.ORIENTATION_NORMAL));

            newExif.saveAttributes();
            Log.d(TAG, "EXIF data copied and orientation normalized");

        } catch (Exception e) {
            Log.e(TAG, "Error copying EXIF data", e);
        }
    }

    /**
     * HELPER: Save bitmap temporarily for text extraction
     */
    private String saveTempBitmap(Bitmap bitmap) {
        try {
            File tempDir = new File(context.getCacheDir(), "temp_text_extraction");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            File tempFile = new File(tempDir, "temp_rotated_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error saving temp bitmap", e);
            return null;
        }
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

        if (imageFile.length() == 0) {
            Log.e(TAG, "Image file is empty: " + imagePath);
            return ProcessingResult.error("Image file is empty", imagePath, imageType);
        }

        if (imageFile.length() > MAX_FILE_SIZE) {
            Log.e(TAG, "Image file too large: " + imageFile.length() + " bytes");
            return ProcessingResult.error("Image file too large", imagePath, imageType);
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
                sensitiveMatches = List.of(); // Empty list
            } else {
                sensitiveMatches = sensitiveDataDetector.detectSensitiveData(fullText);
                Log.d(TAG, "Found " + sensitiveMatches.size() + " sensitive data matches");

                // Log findings safely
                for (SensitiveDataDetector.SensitiveMatch match : sensitiveMatches) {
                    Log.d(TAG, "Detected: " + match.type + " - " + maskSensitiveValue(match.value, match.type));
                }
            }

            // Step 2: Process based on findings and image type
            ProcessingResult result;
            if (sensitiveMatches.isEmpty()) {
                // No sensitive data - behavior depends on image type
                if (imageType == MediaObserver.ImageType.SCREENSHOT) {
                    // For screenshots, keep original as usual
                    Log.d(TAG, "No sensitive data found in screenshot - keeping original");
                    result = ProcessingResult.success(false, null, sensitiveMatches, imagePath, imageType);
                } else {
                    // For camera photos, also keep original but note it's a photo
                    Log.d(TAG, "No sensitive data found in camera photo - keeping original");
                    result = ProcessingResult.success(false, null, sensitiveMatches, imagePath, imageType);
                }
            } else {
                // Sensitive data found - handle based on image type
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
     * Backward compatibility method for screenshots
     */
    public ProcessingResult processScreenshot(String imagePath, Text visionText) {
        return processImage(imagePath, visionText, MediaObserver.ImageType.SCREENSHOT);
    }

    /**
     * FIXED: Simple approach - re-extract text from rotated image instead of coordinate transformation
     */
    /**
     * SIMPLE FIX: Censor image BEFORE rotating it
     * Replace the createCensoredVersion method in ScreenshotProcessor.java with this version
     */
    private ProcessingResult createCensoredVersion(String imagePath, Text visionText,
                                                   List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches,
                                                   MediaObserver.ImageType imageType) {
        Bitmap originalBitmap = null;
        Bitmap censoredBitmap = null;
        Bitmap rotatedBitmap = null;

        try {
            // Load original image with size validation
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            // Check image dimensions
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

            if (originalBitmap.isRecycled()) {
                Log.e(TAG, "Original bitmap is recycled");
                return ProcessingResult.error("Image data corrupted", imagePath, imageType);
            }

            // STEP 1: Censor the original image FIRST (before rotation)
            Log.d(TAG, "🎨 DEBUG: Censoring image BEFORE rotation");
            censoredBitmap = createCensoredImageWithDebug(originalBitmap, visionText, sensitiveMatches);
            if (censoredBitmap == null) {
                return ProcessingResult.error("Failed to create censored image", imagePath, imageType);
            }

            // STEP 2: THEN rotate the censored image (for camera photos only)
            rotatedBitmap = censoredBitmap;
            if (imageType == MediaObserver.ImageType.CAMERA_PHOTO) {
                Bitmap tempRotated = fixImageOrientation(censoredBitmap, imagePath);
                if (tempRotated != null && tempRotated != censoredBitmap) {
                    Log.d(TAG, "🔄 DEBUG: Rotated censored image");
                    rotatedBitmap = tempRotated;
                    // Clean up the unrotated censored bitmap
                    censoredBitmap.recycle();
                }
            }

            // STEP 3: Save the final image (censored and rotated)
            String censoredPath = saveCensoredImage(rotatedBitmap, imagePath, imageType);
            if (censoredPath == null) {
                return ProcessingResult.error("Failed to save censored image", imagePath, imageType);
            }

            // Handle original image based on type
            boolean originalHandled = handleOriginalImage(imagePath, imageType);
            if (!originalHandled) {
                Log.w(TAG, "Could not handle original " + imageType + ", but continuing");
            }

            return ProcessingResult.success(true, censoredPath, sensitiveMatches, imagePath, imageType);

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory creating censored version", e);
            return ProcessingResult.error("Out of memory processing image", imagePath, imageType);
        } catch (Exception e) {
            Log.e(TAG, "Error creating censored version", e);
            return ProcessingResult.error("Censoring failed: " + e.getMessage(), imagePath, imageType);
        } finally {
            // Clean up memory
            if (originalBitmap != null && !originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            if (censoredBitmap != null && censoredBitmap != rotatedBitmap && !censoredBitmap.isRecycled()) {
                censoredBitmap.recycle();
            }
            if (rotatedBitmap != null && !rotatedBitmap.isRecycled()) {
                rotatedBitmap.recycle();
            }
        }
    }

    /**
     * SIMPLIFIED: Update createCensoredImageWithDebug to remove coordinate transformation complexity
     * Replace the censoring loop with this simpler version
     */
    private Bitmap createCensoredImageWithDebug(Bitmap originalBitmap, Text visionText,
                                                List<SensitiveDataDetector.SensitiveMatch> sensitiveMatches) {

        if (originalBitmap == null || originalBitmap.isRecycled()) {
            Log.e(TAG, "Original bitmap is null or recycled");
            return null;
        }

        Log.d(TAG, "🎨 DEBUG: Creating censored image (BEFORE rotation)");
        Log.d(TAG, "   📏 Image dimensions: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
        Log.d(TAG, "   🎯 Sensitive matches to censor: " + sensitiveMatches.size());

        Bitmap censoredBitmap = null;
        Canvas canvas = null;

        try {
            censoredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (censoredBitmap == null) {
                Log.e(TAG, "Failed to create bitmap copy");
                return null;
            }

            canvas = new Canvas(censoredBitmap);

            // DEBUG: Draw red rectangles first, then black ones
            Paint redPaint = new Paint();
            redPaint.setColor(0xFFFF0000); // Red for debugging
            redPaint.setStyle(Paint.Style.STROKE);
            redPaint.setStrokeWidth(5);
            redPaint.setAntiAlias(true);

            Paint blackPaint = new Paint();
            blackPaint.setColor(0xFF000000);
            blackPaint.setStyle(Paint.Style.FILL);
            blackPaint.setAntiAlias(true);

            String fullText = visionText.getText();
            int censoredCount = 0;

            for (SensitiveDataDetector.SensitiveMatch match : sensitiveMatches) {
                try {
                    Log.d(TAG, "🎯 DEBUG: Processing match: " + match.type + " = '" + match.value + "'");

                    List<Rect> boundingBoxes = findTextBoundingBoxes(visionText, match, fullText);
                    Log.d(TAG, "   📦 Found " + boundingBoxes.size() + " bounding boxes");

                    for (int i = 0; i < boundingBoxes.size(); i++) {
                        Rect rect = boundingBoxes.get(i);
                        if (rect != null && isValidRect(rect, censoredBitmap.getWidth(), censoredBitmap.getHeight())) {

                            Log.d(TAG, "   📍 Original rect " + i + ": " + rect.toString());

                            // Draw red outline first (for debugging)
                            canvas.drawRect(rect, redPaint);

                            // Enhanced padding for better coverage
                            int padding = 25;
                            if (match.type.contains("PHONE")) {
                                padding = 35;
                            }

                            Rect paddedRect = new Rect(rect);
                            paddedRect.left = Math.max(0, rect.left - padding);
                            paddedRect.top = Math.max(0, rect.top - padding);
                            paddedRect.right = Math.min(censoredBitmap.getWidth(), rect.right + padding);
                            paddedRect.bottom = Math.min(censoredBitmap.getHeight(), rect.bottom + padding);

                            Log.d(TAG, "   📍 Padded rect " + i + ": " + paddedRect.toString() + " (padding: " + padding + ")");

                            // Draw black rectangle
                            canvas.drawRect(paddedRect, blackPaint);
                            censoredCount++;

                            Log.d(TAG, "   ✅ Censored " + match.type + " at coordinates: " + paddedRect.toString());
                        } else {
                            Log.w(TAG, "   ❌ Invalid bounding box for " + match.type + ": " + rect);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ DEBUG: Error censoring match: " + match.type, e);
                    // Continue with other matches
                }
            }

            Log.d(TAG, "🎨 DEBUG: Successfully censored " + censoredCount + " sensitive areas (BEFORE rotation)");
            return censoredBitmap;

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory creating censored image", e);
            if (censoredBitmap != null && !censoredBitmap.isRecycled()) {
                censoredBitmap.recycle();
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error in createCensoredImageWithDebug", e);
            if (censoredBitmap != null && !censoredBitmap.isRecycled()) {
                censoredBitmap.recycle();
            }
            return null;
        }
    }
    private boolean handleOriginalImage(String imagePath, MediaObserver.ImageType imageType) {
        try {
            if (imageType == MediaObserver.ImageType.SCREENSHOT) {
                // Delete original screenshot
                return deleteOriginalImage(imagePath);
            } else if (imageType == MediaObserver.ImageType.CAMERA_PHOTO) {
                // Delete original camera photo when sensitive data is found
                Log.i(TAG, "Deleting original camera photo with sensitive data: " + imagePath);

                // Try multiple deletion methods for better success rate
                boolean deleted = false;

                // Method 1: MediaStore deletion (preferred for Android 10+)
                if (context != null) {
                    deleted = deleteOriginalImageViaMediaStore(imagePath);
                    if (deleted) {
                        Log.i(TAG, "Successfully deleted via MediaStore");
                        return true;
                    }
                }

                // Method 2: Direct file deletion with retry
                deleted = deleteOriginalImageWithRetry(imagePath, 3);
                if (deleted) {
                    Log.i(TAG, "Successfully deleted via file system");
                    return true;
                }

                // Method 3: Mark for deletion and try again later
                Log.w(TAG, "Could not delete immediately, will retry");
                scheduleRetryDeletion(imagePath);
                return false;

            } else {
                // For other types, delete original
                Log.i(TAG, "Deleting original image: " + imagePath);
                return deleteOriginalImage(imagePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling original image", e);
            return false;
        }
    }

    /**
     * ADDED: Delete with retry mechanism for stubborn files
     */
    private boolean deleteOriginalImageWithRetry(String imagePath, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                File originalFile = new File(imagePath);
                if (!originalFile.exists()) {
                    Log.i(TAG, "File already deleted or doesn't exist");
                    return true;
                }

                // Try to make writable first
                if (!originalFile.canWrite()) {
                    originalFile.setWritable(true);
                }

                boolean deleted = originalFile.delete();
                if (deleted) {
                    Log.i(TAG, "File deleted on attempt " + (attempt + 1));
                    return true;
                } else {
                    Log.w(TAG, "Delete failed on attempt " + (attempt + 1));
                    // Wait a bit before retry
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                Log.e(TAG, "Delete attempt " + (attempt + 1) + " failed", e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * ADDED: Schedule retry deletion for later
     */
    private void scheduleRetryDeletion(String imagePath) {
        // Use handler to retry deletion after 2 seconds
        if (context != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                Log.i(TAG, "Retrying deletion of: " + imagePath);
                deleteOriginalImageWithRetry(imagePath, 2);
            }, 2000);
        }
    }

    /**
     * ENHANCED: Create censored image with larger padding and better coverage
     */

    private String saveCensoredImage(Bitmap censoredBitmap, String originalPath, MediaObserver.ImageType imageType) {
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
            String typePrefix = imageType == MediaObserver.ImageType.SCREENSHOT ? "screenshot" : "photo";
            String censoredFileName = "censored_" + typePrefix + "_" + timestamp + "_" + originalFileName;

            File censoredFile = new File(censoredDir, censoredFileName);

            fos = new FileOutputStream(censoredFile);

            // Use higher quality for photos to preserve detail
            int quality = imageType == MediaObserver.ImageType.CAMERA_PHOTO ? 95 : 90;
            Bitmap.CompressFormat format = imageType == MediaObserver.ImageType.CAMERA_PHOTO ?
                    Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;

            boolean compressed = censoredBitmap.compress(format, quality, fos);

            if (!compressed) {
                Log.e(TAG, "Failed to compress bitmap");
                return null;
            }

            fos.flush();
            fos.close();
            fos = null;

            // Copy EXIF data from original (this will set orientation to normal)
            if (imageType == MediaObserver.ImageType.CAMERA_PHOTO) {
                copyExifData(originalPath, censoredFile.getAbsolutePath());
            }

            Log.i(TAG, "Saved censored " + imageType + ": " + censoredFile.getAbsolutePath());

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

        // DEBUG: Log what we're looking for
        Log.d(TAG, "🔍 DEBUG: Looking for sensitive data:");
        Log.d(TAG, "   Type: " + match.type);
        Log.d(TAG, "   Value: " + searchText);
        Log.d(TAG, "   Full text length: " + fullText.length());

        // DEBUG: Log all detected text with coordinates
        debugLogAllDetectedText(visionText);

        // Create variations of the search text for better matching
        String[] searchVariations = createSearchVariations(searchText, match.type);

        Log.d(TAG, "🔍 DEBUG: Search variations: " + java.util.Arrays.toString(searchVariations));

        try {
            // First, try to find exact matches
            for (String variation : searchVariations) {
                Log.d(TAG, "🔍 DEBUG: Trying variation: '" + variation + "'");
                List<Rect> foundBoxes = findTextVariation(visionText, variation, match.type);
                if (!foundBoxes.isEmpty()) {
                    boundingBoxes.addAll(foundBoxes);
                    Log.d(TAG, "✅ DEBUG: Found " + foundBoxes.size() + " boxes for variation: " + variation);
                    for (Rect box : foundBoxes) {
                        Log.d(TAG, "   📍 Box coordinates: " + box.toString());
                    }
                } else {
                    Log.d(TAG, "❌ DEBUG: No matches for variation: " + variation);
                }
            }

            // If no exact matches, try partial matching for phone numbers
            if (boundingBoxes.isEmpty() && (match.type.contains("PHONE") || match.type.contains("ISRAELI_PHONE"))) {
                Log.d(TAG, "🔍 DEBUG: Trying phone number parts matching...");
                List<Rect> phoneBoxes = findPhoneNumberParts(visionText, searchText, match.type);
                boundingBoxes.addAll(phoneBoxes);
                if (!phoneBoxes.isEmpty()) {
                    Log.d(TAG, "✅ DEBUG: Found " + phoneBoxes.size() + " phone number parts");
                    for (Rect box : phoneBoxes) {
                        Log.d(TAG, "   📍 Phone part coordinates: " + box.toString());
                    }
                } else {
                    Log.d(TAG, "❌ DEBUG: No phone number parts found");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ DEBUG: Error in findTextBoundingBoxes", e);
        }

        Log.d(TAG, "🎯 DEBUG: FINAL RESULT - Total bounding boxes found: " + boundingBoxes.size());
        return boundingBoxes;
    }

    /**
     * DEBUG: Log all detected text with coordinates
     */
    private void debugLogAllDetectedText(Text visionText) {
        Log.d(TAG, "📝 DEBUG: === ALL DETECTED TEXT ===");

        int blockCount = 0;
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            if (block == null) continue;
            blockCount++;

            Rect blockBounds = block.getBoundingBox();
            Log.d(TAG, "📦 Block " + blockCount + ": '" + block.getText() + "'");
            if (blockBounds != null) {
                Log.d(TAG, "   📍 Block bounds: " + blockBounds.toString());
            }

            int lineCount = 0;
            for (Text.Line line : block.getLines()) {
                if (line == null) continue;
                lineCount++;

                String lineText = line.getText();
                Rect lineBounds = line.getBoundingBox();
                Log.d(TAG, "   📄 Line " + blockCount + "." + lineCount + ": '" + lineText + "'");
                if (lineBounds != null) {
                    Log.d(TAG, "      📍 Line bounds: " + lineBounds.toString());
                }

                int elementCount = 0;
                for (Text.Element element : line.getElements()) {
                    if (element == null) continue;
                    elementCount++;

                    String elementText = element.getText();
                    Rect elementBounds = element.getBoundingBox();
                    Log.d(TAG, "      🔤 Element " + blockCount + "." + lineCount + "." + elementCount + ": '" + elementText + "'");
                    if (elementBounds != null) {
                        Log.d(TAG, "         📍 Element bounds: " + elementBounds.toString());
                    }
                }
            }
        }
        Log.d(TAG, "📝 DEBUG: === END ALL DETECTED TEXT ===");
    }

    /**
     * DEBUG: Enhanced findTextVariation with detailed logging
     */
    private List<Rect> findTextVariation(Text visionText, String searchText, String type) {
        List<Rect> boxes = new java.util.ArrayList<>();

        Log.d(TAG, "🔍 DEBUG: findTextVariation looking for: '" + searchText + "' (type: " + type + ")");

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            if (block == null) continue;

            for (Text.Line line : block.getLines()) {
                if (line == null) continue;

                String lineText = line.getText();
                if (lineText == null) continue;

                // DEBUG: Log each line we're checking
                Log.d(TAG, "🔍 DEBUG: Checking line: '" + lineText + "'");

                // Check if this line contains our search text
                boolean lineMatches = containsSensitiveTextPrecise(lineText, searchText, type);
                Log.d(TAG, "   📊 Line match result: " + lineMatches);

                if (lineMatches) {
                    Rect lineBounds = line.getBoundingBox();
                    if (lineBounds != null) {
                        boxes.add(new Rect(lineBounds));
                        Log.d(TAG, "✅ DEBUG: FOUND " + type + " '" + searchText + "' in line: '" + lineText + "'");
                        Log.d(TAG, "   📍 Line bounds: " + lineBounds.toString());
                    } else {
                        Log.d(TAG, "❌ DEBUG: Found match but no bounding box for line: '" + lineText + "'");
                    }
                }

                // Also check individual elements for more precise matching
                for (Text.Element element : line.getElements()) {
                    if (element == null) continue;

                    String elementText = element.getText();
                    if (elementText == null) continue;

                    // DEBUG: Log each element we're checking
                    Log.d(TAG, "🔍 DEBUG: Checking element: '" + elementText + "'");

                    boolean elementMatches = containsSensitiveTextPrecise(elementText, searchText, type);
                    Log.d(TAG, "   📊 Element match result: " + elementMatches);

                    if (elementMatches) {
                        Rect elementBounds = element.getBoundingBox();
                        if (elementBounds != null) {
                            boxes.add(new Rect(elementBounds));
                            Log.d(TAG, "✅ DEBUG: FOUND " + type + " '" + searchText + "' in element: '" + elementText + "'");
                            Log.d(TAG, "   📍 Element bounds: " + elementBounds.toString());
                        } else {
                            Log.d(TAG, "❌ DEBUG: Found match but no bounding box for element: '" + elementText + "'");
                        }
                    }
                }
            }
        }

        Log.d(TAG, "🎯 DEBUG: findTextVariation returning " + boxes.size() + " boxes");
        return boxes;
    }

    /**
     * DEBUG: Enhanced containsSensitiveTextPrecise with detailed logging
     */
    private boolean containsSensitiveTextPrecise(String text, String sensitiveText, String type) {
        if (text == null || sensitiveText == null) return false;

        // DEBUG: Log the comparison
        Log.d(TAG, "🔍 DEBUG: Comparing text: '" + text + "' with sensitive: '" + sensitiveText + "'");

        // Clean both texts
        String cleanText = text.replaceAll("[\\s\\-\\(\\)\\.]", "");
        String cleanSensitive = sensitiveText.replaceAll("[\\s\\-\\(\\)\\.]", "");

        Log.d(TAG, "   🧹 Clean text: '" + cleanText + "' vs clean sensitive: '" + cleanSensitive + "'");

        // For phone numbers, be more aggressive
        if (type.contains("PHONE")) {
            // Extract all digits from both
            String textDigits = text.replaceAll("[^0-9]", "");
            String sensitiveDigits = sensitiveText.replaceAll("[^0-9]", "");

            Log.d(TAG, "   🔢 Text digits: '" + textDigits + "' vs sensitive digits: '" + sensitiveDigits + "'");

            // Check if sensitive digits are contained in text digits
            if (sensitiveDigits.length() >= 7 && textDigits.contains(sensitiveDigits)) {
                Log.d(TAG, "   ✅ MATCH: Text digits contain sensitive digits");
                return true;
            }

            // Check if text digits are contained in sensitive digits (for partial matches)
            if (textDigits.length() >= 7 && sensitiveDigits.contains(textDigits)) {
                Log.d(TAG, "   ✅ MATCH: Sensitive digits contain text digits");
                return true;
            }

            Log.d(TAG, "   ❌ No digit match for phone number");
        }

        // Standard matching
        boolean result = cleanText.contains(cleanSensitive) ||
                cleanSensitive.contains(cleanText) ||
                text.toLowerCase().contains(sensitiveText.toLowerCase()) ||
                sensitiveText.toLowerCase().contains(text.toLowerCase());

        Log.d(TAG, "   📊 Standard match result: " + result);
        return result;
    }

    /**
     * DEBUG: Enhanced createCensoredImage with visual debugging
     */

    private String[] createSearchVariations(String originalText, String type) {
        java.util.Set<String> variations = new java.util.HashSet<>();

        // Always include original
        variations.add(originalText);

        if (type.contains("PHONE")) {
            // Phone number variations
            String digitsOnly = originalText.replaceAll("[^0-9]", "");
            variations.add(digitsOnly);

            // Common phone formats
            if (digitsOnly.length() >= 10) {
                variations.add(digitsOnly.substring(0, 3) + "-" + digitsOnly.substring(3, 6) + "-" + digitsOnly.substring(6));
                variations.add(digitsOnly.substring(0, 3) + " " + digitsOnly.substring(3, 6) + " " + digitsOnly.substring(6));
                variations.add("(" + digitsOnly.substring(0, 3) + ") " + digitsOnly.substring(3, 6) + "-" + digitsOnly.substring(6));
            }

            // Israeli phone formats
            if (type.contains("ISRAELI") && digitsOnly.length() >= 9) {
                variations.add(digitsOnly.substring(0, 3) + "-" + digitsOnly.substring(3, 6) + "-" + digitsOnly.substring(6));
                variations.add(digitsOnly.substring(0, 3) + " " + digitsOnly.substring(3, 6) + " " + digitsOnly.substring(6));

                // Add without leading zero if present
                if (digitsOnly.startsWith("0")) {
                    String withoutZero = digitsOnly.substring(1);
                    variations.add(withoutZero);
                    variations.add(withoutZero.substring(0, 2) + "-" + withoutZero.substring(2, 5) + "-" + withoutZero.substring(5));
                }
            }
        }

        return variations.toArray(new String[0]);
    }


    private List<Rect> findPhoneNumberParts(Text visionText, String phoneNumber, String type) {
        List<Rect> boxes = new java.util.ArrayList<>();

        String digitsOnly = phoneNumber.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 7) return boxes;

        Log.d(TAG, "Searching for phone number parts: " + digitsOnly);

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            if (block == null) continue;

            for (Text.Line line : block.getLines()) {
                if (line == null) continue;

                String lineText = line.getText();
                if (lineText == null) continue;

                String lineDigits = lineText.replaceAll("[^0-9]", "");

                // Check if this line contains significant portion of phone number
                if (lineDigits.length() >= 4 && (digitsOnly.contains(lineDigits) || lineDigits.contains(digitsOnly))) {
                    Rect lineBounds = line.getBoundingBox();
                    if (lineBounds != null) {
                        boxes.add(new Rect(lineBounds));
                        Log.d(TAG, "Found phone part in line: '" + lineText + "' (digits: " + lineDigits + ") at: " + lineBounds);
                    }
                }
            }
        }

        return boxes;
    }

    private boolean containsSensitiveText(String text, String sensitiveText, String type) {
        if (text == null || sensitiveText == null) return false;

        String cleanText = text.replaceAll("[\\s\\-\\(\\)]", "");
        String cleanSensitive = sensitiveText.replaceAll("[\\s\\-\\(\\)]", "");

        return cleanText.contains(cleanSensitive) ||
                text.toLowerCase().contains(sensitiveText.toLowerCase());
    }

    /**
     * Add the new censored image to MediaStore
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

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.DATA, imagePath);
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Images.Media.SIZE, imageFile.length());

            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                Log.d(TAG, "Successfully added censored image to MediaStore: " + uri);

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

    /**
     * Delete original image (for screenshots)
     */
    private boolean deleteOriginalImage(String imagePath) {
        // Try MediaStore deletion first (Android 10+)
        if (context != null && deleteOriginalImageViaMediaStore(imagePath)) {
            Log.i(TAG, "Successfully deleted original image via MediaStore: " + imagePath);
            return true;
        }

        // Fallback to direct file deletion
        try {
            File originalFile = new File(imagePath);
            if (originalFile.exists()) {
                boolean deleted = originalFile.delete();
                if (deleted) {
                    Log.i(TAG, "Deleted original image: " + imagePath);
                    return true;
                } else {
                    Log.w(TAG, "Could not delete original image: " + imagePath);
                    return false;
                }
            } else {
                Log.w(TAG, "Original file doesn't exist: " + imagePath);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting original image", e);
            return false;
        }
    }

    private boolean deleteOriginalImageViaMediaStore(String imagePath) {
        try {
            ContentResolver resolver = context.getContentResolver();

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
                return deleted > 0;
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








//          0504442647




