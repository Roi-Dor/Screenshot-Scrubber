package com.example.screenscrubber;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Simplified manager - removed statistics and scan recent functionality
 */
public class ScreenScrubberManager {
    private static final String TAG = "ScreenScrubberManager";
    private static final long PROCESSING_TIMEOUT_MS = 30000;

    private MediaObserver mediaObserver;
    private TextRecognitionService textService;
    private ScreenshotProcessor screenshotProcessor;
    private NotificationHelper notificationHelper;
    private Context context;

    // Background processing
    private ExecutorService processingExecutor;
    private Handler mainHandler;

    // Configuration
    private boolean monitorScreenshots = true;
    private boolean monitorCameraPhotos = true;

    public ScreenScrubberManager(Context context) {
        this.context = context;
        this.mediaObserver = new MediaObserver(context);
        this.textService = new TextRecognitionService();
        this.screenshotProcessor = new ScreenshotProcessor();
        this.screenshotProcessor.setContext(context);
        this.notificationHelper = new NotificationHelper(context);

        // Initialize background processing
        this.processingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ScreenScrubber-Processing");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "ScreenScrubberManager initialized");
    }

    /**
     * Start monitoring with configuration options
     */
    public void startMonitoring() {
        startMonitoring(true, true);
    }

    public void startMonitoring(boolean screenshots, boolean cameraPhotos) {
        this.monitorScreenshots = screenshots;
        this.monitorCameraPhotos = cameraPhotos;

        try {
            mediaObserver.startMonitoring(new MediaObserver.MediaListener() {
                @Override
                public void onNewImage(String filePath, MediaObserver.ImageType type) {
                    Log.i(TAG, "New image detected: " + type + " - " + filePath);

                    boolean shouldProcess = false;

                    if (type == MediaObserver.ImageType.SCREENSHOT && monitorScreenshots) {
                        shouldProcess = true;
                    } else if (type == MediaObserver.ImageType.CAMERA_PHOTO && monitorCameraPhotos) {
                        shouldProcess = true;
                    }

                    if (shouldProcess) {
                        processImageAsync(filePath, type);
                    }
                }
            });

            Log.i(TAG, "Media monitoring started - Screenshots: " + screenshots + ", Camera: " + cameraPhotos);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start monitoring", e);
            showErrorToast("Failed to start monitoring: " + e.getMessage());
        }
    }

    public void stopMonitoring() {
        try {
            mediaObserver.stopMonitoring();
            Log.i(TAG, "Media monitoring stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping monitoring", e);
        }
    }

    /**
     * Process image asynchronously with timeout
     */
    private void processImageAsync(String filePath, MediaObserver.ImageType imageType) {
        if (processingExecutor.isShutdown()) {
            Log.w(TAG, "Processing executor is shutdown, cannot process image");
            return;
        }

        Future<?> processingTask = processingExecutor.submit(() -> {
            processImageInternal(filePath, imageType);
        });

        // Set up timeout handling
        processingExecutor.submit(() -> {
            try {
                processingTask.get(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                if (!processingTask.isDone()) {
                    Log.e(TAG, "Image processing timed out for: " + filePath);
                    processingTask.cancel(true);
                    showErrorToast("Image processing timed out");
                }
            }
        });
    }

    /**
     * Internal processing method that runs on background thread
     */
    private void processImageInternal(String filePath, MediaObserver.ImageType imageType) {
        long startTime = System.currentTimeMillis();

        Log.d(TAG, "Starting processing for " + imageType + ": " + filePath);

        try {
            // Validate file before processing
            if (!validateImageFile(filePath)) {
                Log.w(TAG, "Image file validation failed: " + filePath);
                return;
            }

            // Extract text with ML Kit
            textService.extractTextFromImage(filePath, new TextRecognitionService.TextExtractionCallback() {
                @Override
                public void onTextExtracted(com.google.mlkit.vision.text.Text visionText, String imagePath) {
                    long extractionTime = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "Text extraction completed in " + extractionTime + "ms");

                    try {
                        // Process the image
                        ScreenshotProcessor.ProcessingResult result =
                                screenshotProcessor.processImage(imagePath, visionText, imageType);

                        long totalTime = System.currentTimeMillis() - startTime;

                        if (result.success) {
                            Log.d(TAG, "Image processing completed successfully in " + totalTime + "ms");
                            mainHandler.post(() -> handleProcessingResult(result, imageType));
                        } else {
                            Log.e(TAG, "Image processing failed: " + result.errorMessage);
                            mainHandler.post(() -> showErrorToast("Processing failed: " + result.errorMessage));
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error in image processing", e);
                        mainHandler.post(() -> showErrorToast("Processing error: " + e.getMessage()));
                    }
                }

                @Override
                public void onExtractionError(String error, String imagePath) {
                    long failedTime = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "Text extraction failed after " + failedTime + "ms: " + error);
                    mainHandler.post(() -> showErrorToast("Text extraction failed: " + error));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in processImageInternal", e);
            mainHandler.post(() -> showErrorToast("Unexpected processing error"));
        }
    }

    /**
     * Validate image file before processing
     */
    private boolean validateImageFile(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);

            if (!file.exists()) {
                Log.w(TAG, "Image file does not exist: " + filePath);
                return false;
            }

            if (!file.canRead()) {
                Log.w(TAG, "Cannot read image file: " + filePath);
                return false;
            }

            if (file.length() == 0) {
                Log.w(TAG, "Image file is empty: " + filePath);
                return false;
            }

            if (file.length() > 50 * 1024 * 1024) { // 50MB max
                Log.w(TAG, "Image file too large: " + file.length() + " bytes");
                return false;
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error validating image file", e);
            return false;
        }
    }

    /**
     * Handle the final result and show appropriate notification
     */
    private void handleProcessingResult(ScreenshotProcessor.ProcessingResult result, MediaObserver.ImageType imageType) {
        try {
            if (!result.success) {
                Log.e(TAG, "Processing result indicates failure: " + result.errorMessage);
                showErrorToast("Processing failed: " + result.errorMessage);
                return;
            }

            String imageTypeStr = imageType == MediaObserver.ImageType.SCREENSHOT ? "Screenshot" : "Photo";

            if (!result.hasSensitiveData) {
                Log.d(TAG, "No sensitive data found in " + imageTypeStr.toLowerCase());
                notificationHelper.showCleanImageNotification(imageTypeStr);
            } else {
                Log.w(TAG, "Sensitive data detected in " + imageTypeStr.toLowerCase());
                if (result.sensitiveMatches != null && !result.sensitiveMatches.isEmpty()) {
                    notificationHelper.showDetailedSensitiveDataAlert(result.sensitiveMatches, imageTypeStr);

                    // Log detection summary (without sensitive values)
                    StringBuilder summary = new StringBuilder("Detected types in " + imageTypeStr + ": ");
                    for (SensitiveDataDetector.SensitiveMatch match : result.sensitiveMatches) {
                        summary.append(match.type).append(" ");
                    }
                    Log.i(TAG, summary.toString());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling processing result", e);
            showErrorToast("Error displaying result");
        }
    }

    /**
     * Show error toast
     */
    private void showErrorToast(String message) {
        try {
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "❌ " + message, Toast.LENGTH_LONG).show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing toast", e);
        }
    }

    /**
     * Configuration methods
     */
    public void setMonitoringOptions(boolean screenshots, boolean cameraPhotos) {
        this.monitorScreenshots = screenshots;
        this.monitorCameraPhotos = cameraPhotos;
        Log.d(TAG, "Monitoring options updated - Screenshots: " + screenshots + ", Camera: " + cameraPhotos);
    }

    public boolean isMonitoringScreenshots() {
        return monitorScreenshots;
    }

    public boolean isMonitoringCameraPhotos() {
        return monitorCameraPhotos;
    }

    /**
     * Check if the manager is healthy
     */
    public boolean isHealthy() {
        return mediaObserver != null &&
                textService != null &&
                screenshotProcessor != null &&
                notificationHelper != null &&
                processingExecutor != null &&
                !processingExecutor.isShutdown();
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        Log.d(TAG, "Starting cleanup");

        try {
            stopMonitoring();

            if (textService != null) {
                textService.cleanup();
            }

            if (processingExecutor != null && !processingExecutor.isShutdown()) {
                processingExecutor.shutdown();
                try {
                    if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        Log.w(TAG, "Processing executor did not terminate gracefully, forcing shutdown");
                        processingExecutor.shutdownNow();

                        if (!processingExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                            Log.e(TAG, "Processing executor could not be terminated");
                        }
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for executor termination");
                    processingExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            Log.i(TAG, "Cleanup completed");

        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }
}