package com.example.screenscrubber;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Workflow coordinator - manages the processing pipeline only
 * NO configuration management, NO error UI, NO file validation
 */
public class ScreenScrubberManager {
    private static final String TAG = "ScreenScrubberManager";
    private static final long PROCESSING_TIMEOUT_MS = 30000;

    private final MediaObserver mediaObserver;
    private final TextRecognitionService textService;
    private final ScreenshotProcessor screenshotProcessor;
    private final NotificationHelper notificationHelper;
    private final Context context;

    // Background processing
    private ExecutorService processingExecutor;
    private Handler mainHandler;

    public ScreenScrubberManager(Context context) {
        this.context = context;
        this.mediaObserver = new MediaObserver(context);
        this.textService = new TextRecognitionService();
        this.screenshotProcessor = new ScreenshotProcessor(context);
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
     * Start monitoring with given configuration
     */
    public void startMonitoring(boolean screenshots, boolean cameraPhotos) {
        try {
            mediaObserver.startMonitoring(new MediaObserver.MediaListener() {
                @Override
                public void onNewImage(String filePath, MediaObserver.ImageType type) {
                    Log.i(TAG, "New image detected: " + type + " - " + filePath);

                    // Check if we should process this image type
                    boolean shouldProcess = false;
                    if (type == MediaObserver.ImageType.SCREENSHOT && screenshots) {
                        shouldProcess = true;
                    } else if (type == MediaObserver.ImageType.CAMERA_PHOTO && cameraPhotos) {
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
            // Let NotificationHelper handle all user-facing errors
            notificationHelper.showErrorNotification("Failed to start monitoring: " + e.getMessage(), "System");
        }
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        try {
            mediaObserver.stopMonitoring();
            Log.i(TAG, "Media monitoring stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping monitoring", e);
        }
    }

    /**
     * Update monitoring options (pass-through to MediaObserver)
     */
    public void updateMonitoringOptions(boolean screenshots, boolean cameraPhotos) {
        // MediaObserver doesn't need to restart - it just filters differently
        Log.d(TAG, "Monitoring options updated - Screenshots: " + screenshots + ", Camera: " + cameraPhotos);
        // The filtering happens in the listener above, so no action needed here
    }

    /**
     * Process image asynchronously with timeout - pure workflow coordination
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
                    mainHandler.post(() -> notificationHelper.showErrorNotification("Processing timed out",
                            imageType == MediaObserver.ImageType.SCREENSHOT ? "Screenshot" : "Photo"));
                }
            }
        });
    }

    /**
     * Internal processing pipeline - coordinates the workflow
     */
    private void processImageInternal(String filePath, MediaObserver.ImageType imageType) {
        long startTime = System.currentTimeMillis();

        Log.d(TAG, "Starting processing for " + imageType + ": " + filePath);

        try {
            // Step 1: Extract text with ML Kit
            textService.extractTextFromImage(filePath, new TextRecognitionService.TextExtractionCallback() {
                @Override
                public void onTextExtracted(com.google.mlkit.vision.text.Text visionText, String imagePath) {
                    long extractionTime = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "Text extraction completed in " + extractionTime + "ms");

                    try {
                        // Step 2: Process the image (validate, detect, censor)
                        ScreenshotProcessor.ProcessingResult result =
                                screenshotProcessor.processImage(imagePath, visionText, imageType);

                        long totalTime = System.currentTimeMillis() - startTime;

                        // Step 3: Handle the result on main thread
                        mainHandler.post(() -> handleProcessingResult(result, imageType, totalTime));

                    } catch (Exception e) {
                        Log.e(TAG, "Error in image processing", e);
                        mainHandler.post(() -> notificationHelper.showErrorNotification(
                                "Processing error: " + e.getMessage(),
                                imageType == MediaObserver.ImageType.SCREENSHOT ? "Screenshot" : "Photo"));
                    }
                }

                @Override
                public void onExtractionError(String error, String imagePath) {
                    long failedTime = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "Text extraction failed after " + failedTime + "ms: " + error);
                    mainHandler.post(() -> notificationHelper.showErrorNotification(
                            "Text extraction failed: " + error,
                            imageType == MediaObserver.ImageType.SCREENSHOT ? "Screenshot" : "Photo"));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in processImageInternal", e);
            mainHandler.post(() -> notificationHelper.showErrorNotification(
                    "Unexpected processing error",
                    imageType == MediaObserver.ImageType.SCREENSHOT ? "Screenshot" : "Photo"));
        }
    }

    /**
     * Handle the final result - delegate to NotificationHelper
     */
    private void handleProcessingResult(ScreenshotProcessor.ProcessingResult result,
                                        MediaObserver.ImageType imageType, long processingTime) {
        try {
            String imageTypeStr = imageType == MediaObserver.ImageType.SCREENSHOT ? "Screenshot" : "Photo";

            Log.d(TAG, imageTypeStr + " processing completed in " + processingTime + "ms - Success: " + result.success);

            if (!result.success) {
                Log.e(TAG, "Processing result indicates failure: " + result.errorMessage);
                notificationHelper.showErrorNotification("Processing failed: " + result.errorMessage, imageTypeStr);
                return;
            }

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
            notificationHelper.showErrorNotification("Error displaying result", "System");
        }
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
     * Cleanup resources - coordinate cleanup of all components
     */
    public void cleanup() {
        Log.d(TAG, "Starting cleanup");

        try {
            stopMonitoring();

            if (textService != null) {
                textService.cleanup();
            }

            if (screenshotProcessor != null) {
                screenshotProcessor.cleanup();
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