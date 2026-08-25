package ru.mark99.gk_heats;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class Utils {
    public static Notification getForegroundNotification(Context ctx) {
        NotificationChannel channel = new NotificationChannel(
                "foreground_service",
                "Foreground",
                NotificationManager.IMPORTANCE_MIN);

        ctx.getSystemService(NotificationManager.class).createNotificationChannel(channel);

        return new Notification.Builder(ctx, "foreground_service")
                .setContentTitle("This notification allow to work app")
                .setAutoCancel(false)
                .build();
    }
}
