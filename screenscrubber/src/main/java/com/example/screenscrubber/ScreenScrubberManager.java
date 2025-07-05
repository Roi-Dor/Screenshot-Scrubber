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
 * Enhanced main coordinator with background processing and better error handling
 */
public class ScreenScrubberManager {
    private static final String TAG = "ScreenScrubberManager";
    private static final long PROCESSING_TIMEOUT_MS = 30000; // 30 seconds max processing time

    private ScreenshotDetector detector;
    private TextRecognitionService textService;
    private ScreenshotProcessor screenshotProcessor;
    private NotificationHelper notificationHelper;
    private Context context;

    // Background processing
    private ExecutorService processingExecutor;
    private Handler mainHandler;

    // Statistics tracking
    private int totalProcessed = 0;
    private int successfullyProcessed = 0;
    private long totalProcessingTime = 0;

    public ScreenScrubberManager(Context context) {
        this.context = context;
        this.detector = new ScreenshotDetector();
        this.textService = new TextRecognitionService();
        this.screenshotProcessor = new ScreenshotProcessor();
        this.screenshotProcessor.setContext(context);
        this.notificationHelper = new NotificationHelper(context);

        // Initialize background processing
        this.processingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ScreenScrubber-Processing");
            t.setDaemon(true); // Don't prevent app from closing
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "ScreenScrubberManager initialized");
    }

    public void startMonitoring() {
        try {
            detector.startDetection(context, new ScreenshotDetector.ScreenshotListener() {
                @Override
                public void onScreenshotTaken(String filePath) {
                    Log.i(TAG, "Screenshot detected: " + filePath);
                    processScreenshotAsync(filePath);
                }
            });
            Log.i(TAG, "Screenshot monitoring started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start monitoring", e);
            showErrorToast("Failed to start screenshot monitoring: " + e.getMessage());
        }
    }

    public void stopMonitoring() {
        try {
            detector.stopDetection();
            Log.i(TAG, "Screenshot monitoring stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping monitoring", e);
        }
    }

    /**
     * Process screenshot asynchronously with timeout and error handling
     */
    private void processScreenshotAsync(String filePath) {
        if (processingExecutor.isShutdown()) {
            Log.w(TAG, "Processing executor is shutdown, cannot process screenshot");
            return;
        }

        Future<?> processingTask = processingExecutor.submit(() -> {
            processScreenshotInternal(filePath);
        });

        // Optional: Set up timeout handling
        processingExecutor.submit(() -> {
            try {
                processingTask.get(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                if (!processingTask.isDone()) {
                    Log.e(TAG, "Screenshot processing timed out for: " + filePath);
                    processingTask.cancel(true);
                    showErrorToast("Screenshot processing timed out");
                }
            }
        });
    }

    /**
     * Internal processing method that runs on background thread
     */
    private void processScreenshotInternal(String filePath) {
        long startTime = System.currentTimeMillis();
        totalProcessed++;

        Log.d(TAG, "Starting background processing for: " + filePath);

        try {
            // Validate file before processing
            if (!validateScreenshotFile(filePath)) {
                Log.w(TAG, "Screenshot file validation failed: " + filePath);
                return;
            }

            // Step 1: Extract text with ML Kit
            textService.extractTextFromImage(filePath, new TextRecognitionService.TextExtractionCallback() {
                @Override
                public void onTextExtracted(com.google.mlkit.vision.text.Text visionText, String imagePath) {
                    long extractionTime = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "Text extraction completed in " + extractionTime + "ms");

                    try {
                        // Step 2: Process the screenshot
                        ScreenshotProcessor.ProcessingResult result =
                                screenshotProcessor.processScreenshot(imagePath, visionText);

                        long totalTime = System.currentTimeMillis() - startTime;
                        totalProcessingTime += totalTime;

                        if (result.success) {
                            successfullyProcessed++;
                            Log.d(TAG, "Screenshot processing completed successfully in " + totalTime + "ms");

                            // Step 3: Handle result on main thread
                            mainHandler.post(() -> handleProcessingResult(result));
                        } else {
                            Log.e(TAG, "Screenshot processing failed: " + result.errorMessage);
                            mainHandler.post(() -> showErrorToast("Processing failed: " + result.errorMessage));
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error in screenshot processing", e);
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
            Log.e(TAG, "Unexpected error in processScreenshotInternal", e);
            mainHandler.post(() -> showErrorToast("Unexpected processing error"));
        }
    }

    /**
     * Validate screenshot file before processing
     */
    private boolean validateScreenshotFile(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);

            if (!file.exists()) {
                Log.w(TAG, "Screenshot file does not exist: " + filePath);
                return false;
            }

            if (!file.canRead()) {
                Log.w(TAG, "Cannot read screenshot file: " + filePath);
                return false;
            }

            if (file.length() == 0) {
                Log.w(TAG, "Screenshot file is empty: " + filePath);
                return false;
            }

            if (file.length() > 50 * 1024 * 1024) { // 50MB max
                Log.w(TAG, "Screenshot file too large: " + file.length() + " bytes");
                return false;
            }

            // Check file extension
            String fileName = file.getName().toLowerCase();
            if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg") &&
                    !fileName.endsWith(".png") && !fileName.endsWith(".webp")) {
                Log.w(TAG, "Unsupported file format: " + fileName);
                return false;
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error validating screenshot file", e);
            return false;
        }
    }

    /**
     * Handle the final result and show appropriate notification
     */
    private void handleProcessingResult(ScreenshotProcessor.ProcessingResult result) {
        try {
            if (!result.success) {
                Log.e(TAG, "Processing result indicates failure: " + result.errorMessage);
                showErrorToast("Processing failed: " + result.errorMessage);
                return;
            }

            if (!result.hasSensitiveData) {
                Log.d(TAG, "No sensitive data found in screenshot");
                notificationHelper.showCleanScreenshotNotification();
            } else {
                Log.w(TAG, "Sensitive data detected and processed");
                if (result.sensitiveMatches != null && !result.sensitiveMatches.isEmpty()) {
                    notificationHelper.showSensitiveDataAlert(result.sensitiveMatches);

                    // Log detection summary (without sensitive values)
                    StringBuilder summary = new StringBuilder("Detected types: ");
                    for (SensitiveDataDetector.SensitiveMatch match : result.sensitiveMatches) {
                        summary.append(match.type).append(" ");
                    }
                    Log.i(TAG, summary.toString());
                } else {
                    Log.w(TAG, "Sensitive data flag set but no matches provided");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling processing result", e);
            showErrorToast("Error displaying result");
        }
    }

    /**
     * Show error toast (only for errors, not regular processing updates)
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
     * Get processing statistics
     */
    public ProcessingStats getStats() {
        return new ProcessingStats(
                totalProcessed,
                successfullyProcessed,
                totalProcessed > 0 ? totalProcessingTime / totalProcessed : 0,
                totalProcessingTime
        );
    }

    /**
     * Reset statistics
     */
    public void resetStats() {
        totalProcessed = 0;
        successfullyProcessed = 0;
        totalProcessingTime = 0;
        Log.d(TAG, "Statistics reset");
    }

    /**
     * Check if the manager is healthy and ready to process
     */
    public boolean isHealthy() {
        return detector != null &&
                textService != null &&
                screenshotProcessor != null &&
                notificationHelper != null &&
                processingExecutor != null &&
                !processingExecutor.isShutdown();
    }

    /**
     * Cleanup resources with proper shutdown
     */
    public void cleanup() {
        Log.d(TAG, "Starting cleanup");

        try {
            // Stop monitoring first
            stopMonitoring();

            // Shutdown text service
            if (textService != null) {
                textService.cleanup();
            }

            // Shutdown processing executor gracefully
            if (processingExecutor != null && !processingExecutor.isShutdown()) {
                processingExecutor.shutdown();
                try {
                    // Wait up to 5 seconds for tasks to complete
                    if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        Log.w(TAG, "Processing executor did not terminate gracefully, forcing shutdown");
                        processingExecutor.shutdownNow();

                        // Wait a bit more for forced termination
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

            // Final stats log
            ProcessingStats stats = getStats();
            Log.i(TAG, "Cleanup completed. Final stats: " + stats);

        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    /**
     * Force process a specific image (for testing)
     */
    public void processImageForTesting(String imagePath, ProcessingCallback callback) {
        if (!isHealthy()) {
            callback.onError("Manager is not healthy");
            return;
        }

        processingExecutor.submit(() -> {
            try {
                textService.extractTextFromImage(imagePath, new TextRecognitionService.TextExtractionCallback() {
                    @Override
                    public void onTextExtracted(com.google.mlkit.vision.text.Text visionText, String imagePath) {
                        ScreenshotProcessor.ProcessingResult result =
                                screenshotProcessor.processScreenshot(imagePath, visionText);

                        mainHandler.post(() -> {
                            if (result.success) {
                                callback.onSuccess(result);
                            } else {
                                callback.onError(result.errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onExtractionError(String error, String imagePath) {
                        mainHandler.post(() -> callback.onError(error));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // Callback interface for testing
    public interface ProcessingCallback {
        void onSuccess(ScreenshotProcessor.ProcessingResult result);
        void onError(String error);
    }

    // Statistics class
    public static class ProcessingStats {
        public final int totalProcessed;
        public final int successfullyProcessed;
        public final long averageProcessingTime;
        public final long totalProcessingTime;

        public ProcessingStats(int totalProcessed, int successfullyProcessed,
                               long averageProcessingTime, long totalProcessingTime) {
            this.totalProcessed = totalProcessed;
            this.successfullyProcessed = successfullyProcessed;
            this.averageProcessingTime = averageProcessingTime;
            this.totalProcessingTime = totalProcessingTime;
        }

        public double getSuccessRate() {
            return totalProcessed > 0 ? (double) successfullyProcessed / totalProcessed : 0.0;
        }

        @Override
        public String toString() {
            return String.format("ProcessingStats{total=%d, successful=%d, successRate=%.2f%%, avgTime=%dms}",
                    totalProcessed, successfullyProcessed, getSuccessRate() * 100, averageProcessingTime);
        }
    }
}