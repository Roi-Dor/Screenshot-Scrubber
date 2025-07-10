package com.example.screenscrubber;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.List;

/**
 * Enhanced notification helper that supports different image types (screenshots, camera photos)
 */
public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "screen_scrubber_alerts";
    private static final String CHANNEL_NAME = "Screen Protection";
    private static final int NOTIFICATION_ID = 1001;

    private Context context;
    private NotificationManagerCompat notificationManager;

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications about sensitive data in screenshots and photos");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Check if we have permission to show notifications
     */
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Older Android versions don't need this permission
    }

    /**
     * Show sensitive data alert with image type awareness
     */
    public void showSensitiveDataAlert(List<SensitiveDataDetector.SensitiveMatch> matches, String imageType) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show alert");
            return;
        }

        StringBuilder message = new StringBuilder("Detected: ");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) message.append(", ");
            message.append(getDisplayName(matches.get(i).type));
        }

        String title = "⚠️ Sensitive Data Protected";
        String contentText = message.toString();

        // Create different messages for different image types
        String bigTextMessage;
        if (imageType.toLowerCase().contains("screenshot")) {
            bigTextMessage = "Sensitive data was detected and censored in your screenshot. " +
                    "Original deleted, safe version saved to Pictures/ScreenScrubber_Censored.";
        } else if (imageType.toLowerCase().contains("photo")) {
            bigTextMessage = "Sensitive data was detected and censored in your photo. " +
                    "Original deleted, safe censored version saved to Pictures/ScreenScrubber_Censored.";
        } else {
            bigTextMessage = "Sensitive data was detected and censored in your image. " +
                    "Original deleted, safe version saved to Pictures/ScreenScrubber_Censored.";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(bigTextMessage))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Sensitive data notification sent for " + imageType);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification - permission denied", e);
        }
    }

    /**
     * Backward compatibility method for screenshots
     */
    public void showSensitiveDataAlert(List<SensitiveDataDetector.SensitiveMatch> matches) {
        showSensitiveDataAlert(matches, "Screenshot");
    }

    /**
     * Show clean image notification with image type awareness
     */
    public void showCleanImageNotification(String imageType) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show clean image notification");
            return;
        }

        String title = "✅ " + imageType + " Protected";
        String contentText = "No sensitive data detected";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Clean " + imageType + " notification sent");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification - permission denied", e);
        }
    }

    /**
     * Backward compatibility method for screenshots
     */
    public void showCleanScreenshotNotification() {
        showCleanImageNotification("Screenshot");
    }

    /**
     * Show monitoring status notification
     */
    public void showMonitoringStatusNotification(boolean screenshots, boolean photos) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show monitoring status");
            return;
        }

        String title = "🛡️ ScreenScrubber Active";
        StringBuilder message = new StringBuilder("Monitoring: ");

        if (screenshots && photos) {
            message.append("Screenshots & Photos");
        } else if (screenshots) {
            message.append("Screenshots Only");
        } else if (photos) {
            message.append("Photos Only");
        } else {
            message.append("None");
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(title)
                .setContentText(message.toString())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // Make it persistent
                .setAutoCancel(false);

        try {
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
            Log.d(TAG, "Monitoring status notification sent");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show monitoring notification - permission denied", e);
        }
    }

    /**
     * Hide monitoring status notification
     */
    public void hideMonitoringStatusNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID + 1);
            Log.d(TAG, "Monitoring status notification hidden");
        } catch (Exception e) {
            Log.e(TAG, "Error hiding monitoring notification", e);
        }
    }

    /**
     * Show batch processing complete notification
     */
    public void showBatchProcessingCompleteNotification(int totalImages, int sensitiveImages) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show batch processing notification");
            return;
        }

        String title = "📊 Batch Processing Complete";
        String contentText = String.format("Processed %d images, %d contained sensitive data",
                totalImages, sensitiveImages);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        try {
            notificationManager.notify(NOTIFICATION_ID + 2, builder.build());
            Log.d(TAG, "Batch processing notification sent");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show batch processing notification - permission denied", e);
        }
    }

    /**
     * Show error notification
     */
    public void showErrorNotification(String errorMessage, String imageType) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show error notification");
            return;
        }

        String title = "❌ Processing Error";
        String contentText = "Failed to process " + imageType.toLowerCase();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Error processing " + imageType.toLowerCase() + ": " + errorMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            notificationManager.notify(NOTIFICATION_ID + 3, builder.build());
            Log.d(TAG, "Error notification sent");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show error notification - permission denied", e);
        }
    }

    /**
     * Show statistics notification
     */
    public void showStatisticsNotification(ScreenScrubberManager.ProcessingStats stats) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show statistics notification");
            return;
        }

        String title = "📈 ScreenScrubber Statistics";
        String contentText = String.format("Processed %d images (%.1f%% success rate)",
                stats.totalProcessed, stats.getSuccessRate() * 100);

        String bigText = String.format(
                "Total Images: %d\n" +
                        "Screenshots: %d\n" +
                        "Photos: %d\n" +
                        "Success Rate: %.1f%%\n" +
                        "Average Processing Time: %dms",
                stats.totalProcessed,
                stats.screenshotsProcessed,
                stats.cameraPhotosProcessed,
                stats.getSuccessRate() * 100,
                stats.averageProcessingTime
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);

        try {
            notificationManager.notify(NOTIFICATION_ID + 4, builder.build());
            Log.d(TAG, "Statistics notification sent");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show statistics notification - permission denied", e);
        }
    }

    /**
     * Clear all notifications
     */
    public void clearAllNotifications() {
        try {
            notificationManager.cancelAll();
            Log.d(TAG, "All notifications cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing notifications", e);
        }
    }

    /**
     * Get user-friendly display name for sensitive data types
     */
    private String getDisplayName(String type) {
        switch (type) {
            case "CREDIT_CARD": return "Credit Card";
            case "US_SSN": return "US SSN";
            case "US_PHONE": return "US Phone";
            case "ISRAELI_ID": return "Israeli ID";
            case "ISRAELI_PHONE": return "Israeli Phone";
            case "ISRAELI_BANK_ACCOUNT": return "Israeli Bank Account";
            case "EMAIL": return "Email";
            case "SSN": return "SSN"; // Backward compatibility
            case "PHONE": return "Phone"; // Backward compatibility
            default: return type.replace("_", " ");
        }
    }

    /**
     * Get emoji icon for sensitive data types
     */
    private String getEmojiIcon(String type) {
        switch (type) {
            case "CREDIT_CARD": return "💳";
            case "US_SSN":
            case "ISRAELI_ID":
            case "SSN": return "🆔";
            case "US_PHONE":
            case "ISRAELI_PHONE":
            case "PHONE": return "📞";
            case "ISRAELI_BANK_ACCOUNT": return "🏦";
            case "EMAIL": return "📧";
            default: return "🔒";
        }
    }

    /**
     * Show detailed sensitive data notification with icons
     */
    public void showDetailedSensitiveDataAlert(List<SensitiveDataDetector.SensitiveMatch> matches, String imageType) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show detailed alert");
            return;
        }

        StringBuilder message = new StringBuilder();
        StringBuilder detailedMessage = new StringBuilder();

        detailedMessage.append("Sensitive data detected in your ").append(imageType.toLowerCase()).append(":\n\n");

        for (int i = 0; i < matches.size(); i++) {
            SensitiveDataDetector.SensitiveMatch match = matches.get(i);
            String emoji = getEmojiIcon(match.type);
            String displayName = getDisplayName(match.type);

            if (i > 0) message.append(", ");
            message.append(emoji).append(" ").append(displayName);

            detailedMessage.append(emoji).append(" ").append(displayName);
            if (match.confidence < 1.0) {
                detailedMessage.append(" (").append(String.format("%.0f%% confidence", match.confidence * 100)).append(")");
            }
            detailedMessage.append("\n");
        }

        if (imageType.toLowerCase().contains("screenshot")) {
            detailedMessage.append("\n🗑️ Original screenshot deleted");
        } else if (imageType.toLowerCase().contains("photo")) {
            detailedMessage.append("\n🗑️ Original photo deleted");
        }

        detailedMessage.append("\n💾 Safe version saved to Pictures/ScreenScrubber_Censored");

        String title = "🛡️ Sensitive Data Protected";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message.toString())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(detailedMessage.toString()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Detailed sensitive data notification sent for " + imageType);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show detailed notification - permission denied", e);
        }
    }
}