package com.example.screenscrubber;

import android.content.Context;
import android.util.Log;

/**
 * Enhanced public API for ScreenScrubber library supporting both screenshots and camera photos
 * Usage:
 *   ScreenScrubber scrubber = new ScreenScrubber(context);
 *   scrubber.start();  // Enable protection for both screenshots and photos
 *   scrubber.startScreenshotProtection();  // Enable only screenshot protection
 *   scrubber.startPhotoProtection();       // Enable only photo protection
 *   scrubber.stop();   // Disable protection
 *   scrubber.cleanup(); // Call in onDestroy()
 */
public class ScreenScrubber {
    private static final String TAG = "ScreenScrubber";

    private ScreenScrubberManager manager;
    private boolean isActive = false;
    private boolean monitoringScreenshots = false;
    private boolean monitoringPhotos = false;
    private Context context;

    public ScreenScrubber(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        this.context = context.getApplicationContext(); // Use app context to avoid leaks
        this.manager = new ScreenScrubberManager(this.context);

        Log.d(TAG, "Enhanced ScreenScrubber initialized");
    }

    /**
     * Start protection for both screenshots and camera photos
     * @return true if started successfully
     */
    public boolean start() {
        return start(true, true);
    }

    /**
     * Start protection with specific options
     * @param screenshots Enable screenshot monitoring
     * @param photos Enable camera photo monitoring
     * @return true if started successfully
     */
    public boolean start(boolean screenshots, boolean photos) {
        if (isActive) {
            Log.w(TAG, "ScreenScrubber already active");
            return true;
        }

        if (!isHealthy()) {
            Log.e(TAG, "ScreenScrubber is not in a healthy state, cannot start");
            return false;
        }

        if (!screenshots && !photos) {
            Log.e(TAG, "At least one monitoring type must be enabled");
            return false;
        }

        try {
            manager.startMonitoring(screenshots, photos);
            isActive = true;
            monitoringScreenshots = screenshots;
            monitoringPhotos = photos;

            Log.i(TAG, "ScreenScrubber protection started - Screenshots: " + screenshots + ", Photos: " + photos);
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
     * Start screenshot protection only
     * @return true if started successfully
     */
    public boolean startScreenshotProtection() {
        return start(true, false);
    }

    /**
     * Start camera photo protection only
     * @return true if started successfully
     */
    public boolean startPhotoProtection() {
        return start(false, true);
    }

    /**
     * Stop all protection
     */
    public void stop() {
        if (!isActive) {
            Log.d(TAG, "ScreenScrubber already inactive");
            return;
        }

        try {
            manager.stopMonitoring();
            isActive = false;
            monitoringScreenshots = false;
            monitoringPhotos = false;
            Log.i(TAG, "ScreenScrubber protection stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping ScreenScrubber", e);
            // Still mark as inactive even if there was an error
            isActive = false;
            monitoringScreenshots = false;
            monitoringPhotos = false;
        }
    }

    /**
     * Update monitoring options without stopping/starting
     * @param screenshots Enable screenshot monitoring
     * @param photos Enable camera photo monitoring
     */
    public void updateMonitoringOptions(boolean screenshots, boolean photos) {
        if (!isActive) {
            Log.w(TAG, "ScreenScrubber not active, cannot update monitoring options");
            return;
        }

        if (!screenshots && !photos) {
            Log.e(TAG, "At least one monitoring type must be enabled");
            return;
        }

        try {
            manager.setMonitoringOptions(screenshots, photos);
            monitoringScreenshots = screenshots;
            monitoringPhotos = photos;
            Log.i(TAG, "Monitoring options updated - Screenshots: " + screenshots + ", Photos: " + photos);
        } catch (Exception e) {
            Log.e(TAG, "Error updating monitoring options", e);
        }
    }

    /**
     * Check if protection is currently active
     */
    public boolean isActive() {
        return isActive && isHealthy();
    }

    /**
     * Check if screenshot monitoring is enabled
     */
    public boolean isMonitoringScreenshots() {
        return isActive && monitoringScreenshots;
    }

    /**
     * Check if photo monitoring is enabled
     */
    public boolean isMonitoringPhotos() {
        return isActive && monitoringPhotos;
    }

    /**
     * Check if the ScreenScrubber is in a healthy state
     */
    public boolean isHealthy() {
        return manager != null && manager.isHealthy();
    }

    /**
     * Get enhanced processing statistics
     */
    public ScreenScrubberManager.ProcessingStats getStats() {
        if (manager == null) {
            return new ScreenScrubberManager.ProcessingStats(0, 0, 0, 0, 0, 0);
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
     * Scan recent images for immediate processing
     */
    public void scanRecentImages() {
        if (manager != null) {
            manager.scanRecentImages();
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
                        result.success,
                        result.imageType
                ));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Get monitoring configuration
     */
    public MonitoringConfig getMonitoringConfig() {
        return new MonitoringConfig(
                isActive,
                monitoringScreenshots,
                monitoringPhotos,
                isHealthy()
        );
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
            monitoringScreenshots = false;
            monitoringPhotos = false;
            Log.d(TAG, "ScreenScrubber cleanup finished");
        }
    }

    // Callback interface for testing
    public interface TestCallback {
        void onSuccess(TestResult result);
        void onError(String error);
    }

    // Enhanced test result class
    public static class TestResult {
        public final boolean hasSensitiveData;
        public final int sensitiveMatchCount;
        public final String censoredImagePath;
        public final boolean processingSuccess;
        public final MediaObserver.ImageType imageType;

        public TestResult(boolean hasSensitiveData, int sensitiveMatchCount,
                          String censoredImagePath, boolean processingSuccess,
                          MediaObserver.ImageType imageType) {
            this.hasSensitiveData = hasSensitiveData;
            this.sensitiveMatchCount = sensitiveMatchCount;
            this.censoredImagePath = censoredImagePath;
            this.processingSuccess = processingSuccess;
            this.imageType = imageType;
        }

        @Override
        public String toString() {
            return String.format("TestResult{sensitive=%s, matches=%d, success=%s, type=%s}",
                    hasSensitiveData, sensitiveMatchCount, processingSuccess, imageType);
        }
    }

    // Monitoring configuration class
    public static class MonitoringConfig {
        public final boolean isActive;
        public final boolean monitoringScreenshots;
        public final boolean monitoringPhotos;
        public final boolean isHealthy;

        public MonitoringConfig(boolean isActive, boolean monitoringScreenshots,
                                boolean monitoringPhotos, boolean isHealthy) {
            this.isActive = isActive;
            this.monitoringScreenshots = monitoringScreenshots;
            this.monitoringPhotos = monitoringPhotos;
            this.isHealthy = isHealthy;
        }

        @Override
        public String toString() {
            return String.format("MonitoringConfig{active=%s, screenshots=%s, photos=%s, healthy=%s}",
                    isActive, monitoringScreenshots, monitoringPhotos, isHealthy);
        }
    }

    /**
     * Get library version and build info
     */
    public static LibraryInfo getLibraryInfo() {
        return new LibraryInfo("2.0.0", "Enhanced with Photo Support", System.currentTimeMillis());
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
     * Enhanced builder pattern for advanced configuration
     */
    public static class Builder {
        private Context context;
        private boolean enableNotifications = true;
        private boolean enableStatistics = true;
        private boolean enableScreenshots = true;
        private boolean enablePhotos = true;
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

        public Builder enableScreenshots(boolean enable) {
            this.enableScreenshots = enable;
            return this;
        }

        public Builder enablePhotos(boolean enable) {
            this.enablePhotos = enable;
            return this;
        }

        public Builder setProcessingTimeout(long timeoutMs) {
            this.processingTimeoutMs = timeoutMs;
            return this;
        }

        public ScreenScrubber build() {
            ScreenScrubber scrubber = new ScreenScrubber(context);
            // Future: Apply configuration settings
            return scrubber;
        }
    }

    /**
     * Quick configuration presets
     */
    public static class Presets {
        public static ScreenScrubber createScreenshotOnly(Context context) {
            return new Builder(context)
                    .enableScreenshots(true)
                    .enablePhotos(false)
                    .build();
        }

        public static ScreenScrubber createPhotoOnly(Context context) {
            return new Builder(context)
                    .enableScreenshots(false)
                    .enablePhotos(true)
                    .build();
        }

        public static ScreenScrubber createSilent(Context context) {
            return new Builder(context)
                    .enableNotifications(false)
                    .build();
        }
    }
}