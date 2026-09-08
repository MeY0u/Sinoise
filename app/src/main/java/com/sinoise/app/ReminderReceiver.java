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
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String SILENT_CHANNEL_ID = "sinoise_silent_v2";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", false)) return;

        if (!ReminderScheduler.isQuietNow(context)) {
            showNow(context);
        }

        if (intent.getBooleanExtra("specific_time", false)
                && ReminderScheduler.MODE_TIMES.equals(prefs.getString("reminder_mode", ReminderScheduler.MODE_INTERVAL))) {
            ReminderScheduler.scheduleOneTime(
                    context,
                    intent.getIntExtra("index", 0),
                    intent.getIntExtra("hour", 9),
                    intent.getIntExtra("minute", 0));
        }
    }

    static boolean showNow(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        String channelId = ensureChannel(context, prefs);

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

        android.app.Notification notification = new android.app.Notification.Builder(context, channelId)
                .setSmallIcon(com.sinoise.app.R.drawable.ic_sinoise)
                .setContentTitle("TODAY")
                .setContentText(body.toString())
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body.toString()))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        manager.notify(101, notification);
        return true;
    }

    static void createChannel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        ensureChannel(context, prefs);
    }

    private static String ensureChannel(Context context, SharedPreferences prefs) {
        boolean soundEnabled = prefs.getBoolean("sound_enabled", false);
        String soundUri = prefs.getString("sound_uri", "");
        String channelId = SILENT_CHANNEL_ID;
        Uri uri = null;

        if (soundEnabled && soundUri != null && !soundUri.isEmpty()) {
            uri = Uri.parse(soundUri);
            channelId = "sinoise_sound_" + Integer.toHexString(soundUri.hashCode());
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return channelId;

        NotificationChannel existing = manager.getNotificationChannel(channelId);
        if (existing != null) return channelId;

        int importance = soundEnabled && uri != null
                ? NotificationManager.IMPORTANCE_DEFAULT
                : NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(
                channelId,
                soundEnabled && uri != null ? "Sinoise reminders with sound" : "Sinoise silent reminders",
                importance);
        channel.setDescription("Reminders for today's three goals");
        channel.enableVibration(false);
        channel.setVibrationPattern(null);
        channel.setShowBadge(false);

        if (soundEnabled && uri != null) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(uri, attributes);
        } else {
            channel.setSound(null, null);
        }

        manager.createNotificationChannel(channel);
        return channelId;
    }
}
