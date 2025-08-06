package com.example.screenscrubber;

import android.content.Context;
import android.util.Log;

/**
 * Clean ScreenScrubber API - Configuration and lifecycle management only
 */
public class ScreenScrubber {
    private static final String TAG = "ScreenScrubber";

    private final ScreenScrubberManager manager;
    private final Context context;

    // Configuration state - only place these are managed
    private boolean isActive = false;
    private boolean monitoringScreenshots = false;
    private boolean monitoringPhotos = false;

    public ScreenScrubber(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        this.context = context.getApplicationContext();
        this.manager = new ScreenScrubberManager(this.context);

        Log.d(TAG, "ScreenScrubber initialized");
    }

    /**
     * Start protection for both screenshots and camera photos
     */
    public boolean start() {
        return start(true, true);
    }

    /**
     * Start protection with specific options
     */
    public boolean start(boolean screenshots, boolean photos) {
        if (isActive) {
            Log.w(TAG, "ScreenScrubber already active");
            return true;
        }

        if (!screenshots && !photos) {
            Log.e(TAG, "At least one monitoring type must be enabled");
            return false;
        }

        if (!manager.isHealthy()) {
            Log.e(TAG, "ScreenScrubber is not healthy, cannot start");
            return false;
        }

        try {
            // Pass configuration to manager
            manager.startMonitoring(screenshots, photos);

            // Update our state
            isActive = true;
            monitoringScreenshots = screenshots;
            monitoringPhotos = photos;

            Log.i(TAG, "ScreenScrubber started - Screenshots: " + screenshots + ", Photos: " + photos);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ScreenScrubber", e);
            return false;
        }
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

            // Reset state
            isActive = false;
            monitoringScreenshots = false;
            monitoringPhotos = false;

            Log.i(TAG, "ScreenScrubber protection stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping ScreenScrubber", e);
            // Reset state even on error
            isActive = false;
            monitoringScreenshots = false;
            monitoringPhotos = false;
        }
    }

    /**
     * Update monitoring options without stopping/starting
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
            manager.updateMonitoringOptions(screenshots, photos);

            // Update our configuration
            monitoringScreenshots = screenshots;
            monitoringPhotos = photos;

            Log.i(TAG, "Monitoring options updated - Screenshots: " + screenshots + ", Photos: " + photos);
        } catch (Exception e) {
            Log.e(TAG, "Error updating monitoring options", e);
        }
    }

    // Configuration getters - single source of truth
    public boolean isActive() {
        return isActive && manager.isHealthy();
    }

    public boolean isMonitoringScreenshots() {
        return isActive && monitoringScreenshots;
    }

    public boolean isMonitoringPhotos() {
        return isActive && monitoringPhotos;
    }

    public MonitoringConfig getMonitoringConfig() {
        return new MonitoringConfig(
                isActive,
                monitoringScreenshots,
                monitoringPhotos,
                manager.isHealthy()
        );
    }

    /**
     * Cleanup resources - MUST call in onDestroy()
     */
    public void cleanup() {
        Log.d(TAG, "Starting ScreenScrubber cleanup");

        try {
            stop();
            manager.cleanup();
        } catch (Exception e) {
            Log.e(TAG, "Error during ScreenScrubber cleanup", e);
        } finally {
            isActive = false;
            monitoringScreenshots = false;
            monitoringPhotos = false;
            Log.d(TAG, "ScreenScrubber cleanup finished");
        }
    }

    // Configuration data class
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
     * Get library version info
     */
    public static LibraryInfo getLibraryInfo() {
        return new LibraryInfo("2.1.0", "Refactored", System.currentTimeMillis());
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
            return String.format("ScreenScrubber %s (%s)", version, buildType);
        }
    }
}