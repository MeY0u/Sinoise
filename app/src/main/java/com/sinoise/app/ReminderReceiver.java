package com.sinoise.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL_ID = "sinoise_silent_reminders_v1";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", false)) return;

        createChannel(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            String goal = prefs.getString("goal_" + i, "").trim();
            if (goal.isEmpty()) continue;
            boolean done = prefs.getBoolean("goal_done_" + i, false);
            if (body.length() > 0) body.append("\n");
            body.append(done ? "✓ " : "○ ").append(goal);
        }
        if (body.length() == 0) body.append("Choose your 3 signals for today.");

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                77,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.sinoise.app.R.drawable.ic_sinoise)
                .setContentTitle("TODAY")
                .setContentText(body.toString())
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body.toString()))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(101, notification);

        if (intent.getBooleanExtra("specific_time", false)
                && ReminderScheduler.MODE_TIMES.equals(prefs.getString("reminder_mode", ReminderScheduler.MODE_INTERVAL))) {
            ReminderScheduler.scheduleOneTime(
                    context,
                    intent.getIntExtra("index", 0),
                    intent.getIntExtra("hour", 9),
                    intent.getIntExtra("minute", 0));
        }
    }

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Sinoise reminders",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Silent reminders for today's three goals");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setVibrationPattern(null);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
