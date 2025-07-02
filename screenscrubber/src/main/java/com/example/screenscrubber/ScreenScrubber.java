package com.example.screenscrubber;

import android.content.Context;
import android.util.Log;

/**
 * Simple public API for ScreenScrubber library
 * Usage:
 *   ScreenScrubber scrubber = new ScreenScrubber(context);
 *   scrubber.start();  // Enable protection
 *   scrubber.stop();   // Disable protection
 */
public class ScreenScrubber {
    private static final String TAG = "ScreenScrubber";

    private ScreenScrubberManager manager;
    private boolean isActive = false;

    public ScreenScrubber(Context context) {
        this.manager = new ScreenScrubberManager(context);
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

        try {
            manager.startMonitoring();
            isActive = true;
            Log.i(TAG, "ScreenScrubber protection started");
            return true;
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
            return;
        }

        manager.stopMonitoring();
        isActive = false;
        Log.i(TAG, "ScreenScrubber protection stopped");
    }

    /**
     * Check if protection is currently active
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Cleanup resources - call in onDestroy()
     */
    public void cleanup() {
        stop();
        if (manager != null) {
            manager.cleanup();
        }
    }
}