package com.example.screenscrubber;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.List;

/**
 * ALL user communication - notifications, toasts, error messages
 * Single responsibility: User interface and feedback
 */
public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "screen_scrubber_alerts";
    private static final String CHANNEL_NAME = "Screen Protection";

    // Different notification IDs for different types
    private static final int SENSITIVE_DATA_NOTIFICATION_ID = 1001;
    private static final int CLEAN_IMAGE_NOTIFICATION_ID = 1002;
    private static final int ERROR_NOTIFICATION_ID = 1003;
    private static final int SYSTEM_ERROR_NOTIFICATION_ID = 1004;

    private final Context context;
    private final NotificationManagerCompat notificationManager;

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
            Log.w(TAG, "No notification permission - showing toast instead");
            showSensitiveDataToast(matches.size(), imageType);
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
            notificationManager.notify(SENSITIVE_DATA_NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Detailed sensitive data notification sent for " + imageType);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification - permission denied, showing toast", e);
            showSensitiveDataToast(matches.size(), imageType);
        }
    }

    /**
     * Show clean image notification
     */
    public void showCleanImageNotification(String imageType) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - showing toast instead");
            showCleanImageToast(imageType);
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
            notificationManager.notify(CLEAN_IMAGE_NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Clean " + imageType + " notification sent");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification - permission denied, showing toast", e);
            showCleanImageToast(imageType);
        }
    }

    /**
     * Show error notification - CENTRALIZED error handling
     */
    public void showErrorNotification(String errorMessage, String imageType) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - showing toast instead");
            showErrorToast(errorMessage, imageType);
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
            notificationManager.notify(ERROR_NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Error notification sent: " + errorMessage);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show error notification - permission denied, showing toast", e);
            showErrorToast(errorMessage, imageType);
        }
    }

    /**
     * Show system error notification (for startup/configuration issues)
     */
    public void showSystemErrorNotification(String errorMessage) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - showing toast instead");
            showSystemErrorToast(errorMessage);
            return;
        }

        String title = "⚠️ ScreenScrubber Error";
        String contentText = "System error occurred";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("ScreenScrubber system error: " + errorMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            notificationManager.notify(SYSTEM_ERROR_NOTIFICATION_ID, builder.build());
            Log.d(TAG, "System error notification sent: " + errorMessage);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show system error notification - permission denied, showing toast", e);
            showSystemErrorToast(errorMessage);
        }
    }

    /**
     * FALLBACK: Toast messages when notifications aren't available
     */
    private void showSensitiveDataToast(int matchCount, String imageType) {
        try {
            runOnUiThread(() -> {
                String message = "🛡️ " + matchCount + " sensitive items found in " + imageType.toLowerCase() + " - Protected!";
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error showing sensitive data toast", e);
        }
    }

    private void showCleanImageToast(String imageType) {
        try {
            runOnUiThread(() -> {
                String message = "✅ " + imageType + " is clean - No sensitive data found";
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error showing clean image toast", e);
        }
    }

    private void showErrorToast(String errorMessage, String imageType) {
        try {
            runOnUiThread(() -> {
                String message = "❌ Error processing " + imageType.toLowerCase() + ": " + errorMessage;
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error showing error toast", e);
        }
    }

    private void showSystemErrorToast(String errorMessage) {
        try {
            runOnUiThread(() -> {
                String message = "⚠️ ScreenScrubber Error: " + errorMessage;
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error showing system error toast", e);
        }
    }

    /**
     * Generic toast method for other components to use
     */
    public void showToast(String message) {
        try {
            runOnUiThread(() -> {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error showing generic toast", e);
        }
    }

    public void showToast(String message, boolean isLong) {
        try {
            runOnUiThread(() -> {
                int duration = isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
                Toast.makeText(context, message, duration).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error showing generic toast", e);
        }
    }

    /**
     * Ensure UI operations run on main thread
     */
    private void runOnUiThread(Runnable action) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(action);
        } else {
            // Fallback: post to main looper
            new android.os.Handler(android.os.Looper.getMainLooper()).post(action);
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
     * Clear specific notification types
     */
    public void clearSensitiveDataNotifications() {
        try {
            notificationManager.cancel(SENSITIVE_DATA_NOTIFICATION_ID);
            Log.d(TAG, "Sensitive data notifications cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing sensitive data notifications", e);
        }
    }

    public void clearErrorNotifications() {
        try {
            notificationManager.cancel(ERROR_NOTIFICATION_ID);
            notificationManager.cancel(SYSTEM_ERROR_NOTIFICATION_ID);
            Log.d(TAG, "Error notifications cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing error notifications", e);
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