package com.example.screenscrubber;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

/**
 * Enhanced observer that monitors both screenshots and camera photos
 * Uses MediaStore to be compatible with Android 10+ scoped storage
 */
public class MediaObserver {
    private static final String TAG = "MediaObserver";
    private static final long NEW_IMAGE_THRESHOLD_MS = 10000; // 10 seconds

    private Context context;
    private MediaListener listener;
    private Handler handler;
    private ContentObserver mediaObserver;
    private boolean isMonitoring = false;

    public interface MediaListener {
        void onNewImage(String filePath, ImageType type);
    }

    public enum ImageType {
        SCREENSHOT,
        CAMERA_PHOTO,
        OTHER
    }

    public MediaObserver(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void startMonitoring(MediaListener listener) {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring media changes");
            return;
        }

        this.listener = listener;

        mediaObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                Log.d(TAG, "Media change detected: " + uri);

                if (uri != null) {
                    checkNewImage(uri);
                }
            }
        };

        try {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    mediaObserver
            );

            isMonitoring = true;
            Log.i(TAG, "Started monitoring media changes");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start media monitoring", e);
        }
    }

    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }

        try {
            if (mediaObserver != null) {
                context.getContentResolver().unregisterContentObserver(mediaObserver);
                mediaObserver = null;
            }

            isMonitoring = false;
            Log.i(TAG, "Stopped monitoring media changes");

        } catch (Exception e) {
            Log.e(TAG, "Error stopping media monitoring", e);
        }
    }

    /**
     * Check if a new image was added and determine its type
     */
    private void checkNewImage(Uri uri) {
        try {
            String[] projection = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
            };

            Cursor cursor = context.getContentResolver().query(
                    uri,
                    projection,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED));
                String bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));
                String relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH));

                cursor.close();

                // Check if this is a recent image
                long currentTime = System.currentTimeMillis() / 1000; // MediaStore uses seconds
                if (currentTime - dateAdded <= NEW_IMAGE_THRESHOLD_MS / 1000) {

                    ImageType imageType = determineImageType(displayName, filePath, bucketName, relativePath);

                    if (imageType == ImageType.SCREENSHOT || imageType == ImageType.CAMERA_PHOTO) {
                        Log.i(TAG, "New " + imageType + " detected: " + displayName);

                        if (listener != null && filePath != null) {
                            // Add slight delay to ensure file is fully written
                            handler.postDelayed(() -> {
                                listener.onNewImage(filePath, imageType);
                            }, 1000);
                        }
                    }
                }
            } else {
                Log.w(TAG, "Could not query image details from URI: " + uri);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking new image", e);
        }
    }

    /**
     * Determine if an image is a screenshot, camera photo, or other
     */
    private ImageType determineImageType(String displayName, String filePath, String bucketName, String relativePath) {
        if (displayName == null) displayName = "";
        if (filePath == null) filePath = "";
        if (bucketName == null) bucketName = "";
        if (relativePath == null) relativePath = "";

        // Convert to lowercase for case-insensitive matching
        String lowerDisplayName = displayName.toLowerCase();
        String lowerFilePath = filePath.toLowerCase();
        String lowerBucketName = bucketName.toLowerCase();
        String lowerRelativePath = relativePath.toLowerCase();

        Log.d(TAG, "Analyzing image:");
        Log.d(TAG, "  Display name: " + displayName);
        Log.d(TAG, "  File path: " + filePath);
        Log.d(TAG, "  Bucket name: " + bucketName);
        Log.d(TAG, "  Relative path: " + relativePath);

        // Check for screenshot indicators
        if (isScreenshot(lowerDisplayName, lowerFilePath, lowerBucketName, lowerRelativePath)) {
            return ImageType.SCREENSHOT;
        }

        // Check for camera photo indicators
        if (isCameraPhoto(lowerDisplayName, lowerFilePath, lowerBucketName, lowerRelativePath)) {
            return ImageType.CAMERA_PHOTO;
        }

        return ImageType.OTHER;
    }

    /**
     * Check if image is a screenshot
     */
    private boolean isScreenshot(String displayName, String filePath, String bucketName, String relativePath) {
        // Screenshot name patterns
        if (displayName.contains("screenshot") ||
                displayName.contains("screen_shot") ||
                displayName.contains("screencap") ||
                displayName.contains("screen capture")) {
            return true;
        }

        // Screenshot path patterns
        if (filePath.contains("/screenshots/") ||
                filePath.contains("/screenshot/") ||
                filePath.contains("/screen_shots/") ||
                filePath.contains("/screencaps/")) {
            return true;
        }

        // Screenshot bucket patterns
        if (bucketName.contains("screenshot") ||
                bucketName.contains("screen shot") ||
                bucketName.contains("screencap")) {
            return true;
        }

        // Screenshot relative path patterns (Android 10+)
        if (relativePath.contains("screenshots/") ||
                relativePath.contains("screenshot/") ||
                relativePath.contains("screencaps/")) {
            return true;
        }

        return false;
    }

    /**
     * Check if image is a camera photo
     */
    private boolean isCameraPhoto(String displayName, String filePath, String bucketName, String relativePath) {
        // Camera name patterns
        if (displayName.matches("img_\\d+.*") ||
                displayName.matches("\\d{8}_\\d{6}.*") ||
                displayName.matches("photo_\\d+.*") ||
                displayName.matches("pxl_\\d+.*") ||  // Google Pixel
                displayName.matches("\\d{4}-\\d{2}-\\d{2}.*") ||
                displayName.startsWith("cam_") ||
                displayName.startsWith("dsc_") ||
                displayName.startsWith("dscn")) {
            return true;
        }

        // Camera path patterns
        if (filePath.contains("/camera/") ||
                filePath.contains("/dcim/") ||
                filePath.contains("/dcim/camera/") ||
                filePath.contains("/pictures/camera/")) {
            return true;
        }

        // Camera bucket patterns
        if (bucketName.equals("camera") ||
                bucketName.equals("dcim") ||
                bucketName.contains("camera") ||
                bucketName.contains("photo")) {
            return true;
        }

        // Camera relative path patterns (Android 10+)
        if (relativePath.contains("dcim/") ||
                relativePath.contains("camera/") ||
                relativePath.contains("pictures/camera/")) {
            return true;
        }

        return false;
    }

    /**
     * Get recent images for initial scan
     */
    public void scanRecentImages(int limitHours) {
        try {
            long cutoffTime = (System.currentTimeMillis() / 1000) - (limitHours * 3600);

            String[] projection = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
            };

            String selection = MediaStore.Images.Media.DATE_ADDED + " > ?";
            String[] selectionArgs = {String.valueOf(cutoffTime)};
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

            Cursor cursor = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                    String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                    String bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));
                    String relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH));

                    ImageType imageType = determineImageType(displayName, filePath, bucketName, relativePath);

                    if (imageType == ImageType.SCREENSHOT || imageType == ImageType.CAMERA_PHOTO) {
                        Log.i(TAG, "Found recent " + imageType + ": " + displayName);

                        if (listener != null && filePath != null) {
                            listener.onNewImage(filePath, imageType);
                        }
                    }
                }
                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error scanning recent images", e);
        }
    }

    public boolean isMonitoring() {
        return isMonitoring;
    }
}