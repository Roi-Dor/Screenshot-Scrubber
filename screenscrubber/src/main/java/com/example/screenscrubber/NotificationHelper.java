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
 * Simplified notification helper for ScreenScrubber
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
        return true;
    }

    /**
     * Show detailed sensitive data alert with icons and types
     */
    public void showDetailedSensitiveDataAlert(List<SensitiveDataDetector.SensitiveMatch> matches, String imageType) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show alert");
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

        detailedMessage.append("\n🗑️ Original ").append(imageType.toLowerCase()).append(" deleted");
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
            Log.e(TAG, "Failed to show notification - permission denied", e);
        }
    }

    /**
     * Show clean image notification
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
            case "ISRAELI_ID": return "🆔";
            case "US_PHONE":
            case "ISRAELI_PHONE": return "📞";
            case "ISRAELI_BANK_ACCOUNT": return "🏦";
            case "EMAIL": return "📧";
            default: return "🔒";
        }
    }
}