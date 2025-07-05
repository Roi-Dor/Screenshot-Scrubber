package com.example.screenscrubber;

import android.content.Context;
import android.util.Log;

/**
 * Enhanced public API for ScreenScrubber library with better error handling and monitoring
 * Usage:
 *   ScreenScrubber scrubber = new ScreenScrubber(context);
 *   scrubber.start();  // Enable protection
 *   scrubber.stop();   // Disable protection
 *   scrubber.cleanup(); // Call in onDestroy()
 */
public class ScreenScrubber {
    private static final String TAG = "ScreenScrubber";

    private ScreenScrubberManager manager;
    private boolean isActive = false;
    private Context context;

    public ScreenScrubber(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        this.context = context.getApplicationContext(); // Use app context to avoid leaks
        this.manager = new ScreenScrubberManager(this.context);

        Log.d(TAG, "ScreenScrubber initialized");
    }

    /**
     * Start screenshot protection
     * @return true if started successfully
     */
    public boolean start() {
        if (isActive) {
            Log.w(TAG, "ScreenScrubber already active");
            return true;
        }

        if (!isHealthy()) {
            Log.e(TAG, "ScreenScrubber is not in a healthy state, cannot start");
            return false;
        }

        try {
            manager.startMonitoring();
            isActive = true;
            Log.i(TAG, "ScreenScrubber protection started successfully");
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting ScreenScrubber - check permissions", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ScreenScrubber", e);
            return false;
        }
    }

    /**
     * Stop screenshot protection
     */
    public void stop() {
        if (!isActive) {
            Log.d(TAG, "ScreenScrubber already inactive");
            return;
        }

        try {
            manager.stopMonitoring();
            isActive = false;
            Log.i(TAG, "ScreenScrubber protection stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping ScreenScrubber", e);
            // Still mark as inactive even if there was an error
            isActive = false;
        }
    }

    /**
     * Check if protection is currently active
     */
    public boolean isActive() {
        return isActive && isHealthy();
    }

    /**
     * Check if the ScreenScrubber is in a healthy state
     */
    public boolean isHealthy() {
        return manager != null && manager.isHealthy();
    }

    /**
     * Get processing statistics
     */
    public ScreenScrubberManager.ProcessingStats getStats() {
        if (manager == null) {
            return new ScreenScrubberManager.ProcessingStats(0, 0, 0, 0);
        }
        return manager.getStats();
    }

    /**
     * Reset processing statistics
     */
    public void resetStats() {
        if (manager != null) {
            manager.resetStats();
        }
    }

    /**
     * Test the library with a specific image (for development/testing)
     * @param imagePath Path to test image
     * @param callback Callback for results
     */
    public void testWithImage(String imagePath, TestCallback callback) {
        if (manager == null) {
            callback.onError("Manager not initialized");
            return;
        }

        if (imagePath == null || imagePath.isEmpty()) {
            callback.onError("Invalid image path");
            return;
        }

        manager.processImageForTesting(imagePath, new ScreenScrubberManager.ProcessingCallback() {
            @Override
            public void onSuccess(ScreenshotProcessor.ProcessingResult result) {
                callback.onSuccess(new TestResult(
                        result.hasSensitiveData,
                        result.sensitiveMatches != null ? result.sensitiveMatches.size() : 0,
                        result.censoredImagePath,
                        result.success
                ));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Cleanup resources - MUST call in onDestroy()
     */
    public void cleanup() {
        Log.d(TAG, "Starting ScreenScrubber cleanup");

        try {
            // Stop monitoring first
            stop();

            // Cleanup manager
            if (manager != null) {
                manager.cleanup();
            }

            // Log final stats
            if (manager != null) {
                ScreenScrubberManager.ProcessingStats stats = manager.getStats();
                Log.i(TAG, "ScreenScrubber cleanup completed. Final stats: " + stats);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during ScreenScrubber cleanup", e);
        } finally {
            isActive = false;
            Log.d(TAG, "ScreenScrubber cleanup finished");
        }
    }

    // Callback interface for testing
    public interface TestCallback {
        void onSuccess(TestResult result);
        void onError(String error);
    }

    // Test result class
    public static class TestResult {
        public final boolean hasSensitiveData;
        public final int sensitiveMatchCount;
        public final String censoredImagePath;
        public final boolean processingSuccess;

        public TestResult(boolean hasSensitiveData, int sensitiveMatchCount,
                          String censoredImagePath, boolean processingSuccess) {
            this.hasSensitiveData = hasSensitiveData;
            this.sensitiveMatchCount = sensitiveMatchCount;
            this.censoredImagePath = censoredImagePath;
            this.processingSuccess = processingSuccess;
        }

        @Override
        public String toString() {
            return String.format("TestResult{sensitive=%s, matches=%d, success=%s}",
                    hasSensitiveData, sensitiveMatchCount, processingSuccess);
        }
    }

    /**
     * Get library version and build info
     */
    public static LibraryInfo getLibraryInfo() {
        return new LibraryInfo("1.0.0", "Enhanced", System.currentTimeMillis());
    }

    public static class LibraryInfo {
        public final String version;
        public final String buildType;
        public final long buildTime;

        public LibraryInfo(String version, String buildType, long buildTime) {
            this.version = version;
            this.buildType = buildType;
            this.buildTime = buildTime;
        }

        @Override
        public String toString() {
            return String.format("ScreenScrubber %s (%s) built at %d", version, buildType, buildTime);
        }
    }

    /**
     * Builder pattern for advanced configuration (future extensibility)
     */
    public static class Builder {
        private Context context;
        private boolean enableNotifications = true;
        private boolean enableStatistics = true;
        private long processingTimeoutMs = 30000;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder enableNotifications(boolean enable) {
            this.enableNotifications = enable;
            return this;
        }

        public Builder enableStatistics(boolean enable) {
            this.enableStatistics = enable;
            return this;
        }

        public Builder setProcessingTimeout(long timeoutMs) {
            this.processingTimeoutMs = timeoutMs;
            return this;
        }

        public ScreenScrubber build() {
            // For now, return standard ScreenScrubber
            // In future, could configure based on these settings
            return new ScreenScrubber(context);
        }
    }
}