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
            channel.setDescription("Notifications about sensitive data in screenshots");

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

    public void showSensitiveDataAlert(List<SensitiveDataDetector.SensitiveMatch> matches) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show alert");
            return;
        }

        StringBuilder message = new StringBuilder("Detected: ");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) message.append(", ");
            message.append(getDisplayName(matches.get(i).type));
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⚠️ Sensitive Data Protected")
                .setContentText(message.toString())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Sensitive data was detected and censored in your screenshot. " +
                                "Original deleted, safe version saved to Pictures/ScreenScrubber_Censored."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Sensitive data notification sent");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification - permission denied", e);
        }
    }

    public void showCleanScreenshotNotification() {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - cannot show clean screenshot notification");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("✅ Screenshot Protected")
                .setContentText("No sensitive data detected")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Clean screenshot notification sent");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification - permission denied", e);
        }
    }

    private String getDisplayName(String type) {
        switch (type) {
            case "CREDIT_CARD": return "Credit Card";
            case "SSN": return "SSN";
            case "PHONE": return "Phone Number";
            case "EMAIL": return "Email";
            default: return type;
        }
    }
}