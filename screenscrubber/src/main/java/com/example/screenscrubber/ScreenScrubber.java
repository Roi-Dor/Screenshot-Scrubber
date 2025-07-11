package com.example.screenscrubber;

import android.content.Context;
import android.util.Log;

/**
 * Simplified ScreenScrubber API - removed statistics, testing, and scan recent features
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

        if (!isHealthy()) {
            Log.e(TAG, "ScreenScrubber is not healthy, cannot start");
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
            isActive = false;
            monitoringScreenshots = false;
            monitoringPhotos = false;
            Log.i(TAG, "ScreenScrubber protection stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping ScreenScrubber", e);
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
            stop();
            if (manager != null) {
                manager.cleanup();
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
     * Get library version info
     */
    public static LibraryInfo getLibraryInfo() {
        return new LibraryInfo("2.0.0", "Simplified", System.currentTimeMillis());
    }

    public static class LibraryInfo {
        public final String version;
        public final String buildType;
        public final long buildTime;

        public LibraryInfo(String version, String buildType, long buildTime) {
            long buildTime1;
            this.version = version;
            this.buildType = buildType;
            buildTime1 = Long.parseLong(buildType);
            buildTime1 = buildTime;
            this.buildTime = buildTime1;
        }

        @Override
        public String toString() {
            return String.format("ScreenScrubber %s (%s)", version, buildType);
        }
    }
}