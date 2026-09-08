package com.sinoise.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.Calendar;

public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static final String PREFS = "sinoise_prefs";
    public static final String MODE_INTERVAL = "interval";
    public static final String MODE_TIMES = "times";
    private static final int INTERVAL_REQUEST = 500;
    private static final int TIMES_REQUEST_BASE = 1000;
    private static final int MAX_TIMES = 32;

    public static void restore(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", false)) return;
        scheduleFromPrefs(context);
    }

    public static void scheduleFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cancelAll(context);
        String mode = prefs.getString("reminder_mode", MODE_INTERVAL);
        if (MODE_TIMES.equals(mode)) {
            String times = prefs.getString("times", "09:00, 13:00, 17:00");
            scheduleTimes(context, times);
        } else {
            int minutes = Math.max(1, prefs.getInt("interval_minutes", 60));
            scheduleInterval(context, minutes);
        }
    }

    public static void cancelAll(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(intervalIntent(context));
        for (int i = 0; i < MAX_TIMES; i++) {
            alarmManager.cancel(timeIntent(context, i, 0, 0));
        }
    }

    private static void scheduleInterval(Context context, int minutes) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        long intervalMs = minutes * 60_000L;
        long first = SystemClock.elapsedRealtime() + intervalMs;
        alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                first,
                intervalMs,
                intervalIntent(context));
    }

    private static void scheduleTimes(Context context, String csv) {
        String[] parts = csv.split(",");
        int index = 0;
        for (String part : parts) {
            if (index >= MAX_TIMES) break;
            String text = part.trim();
            String[] hm = text.split(":");
            if (hm.length != 2) continue;
            try {
                int hour = Integer.parseInt(hm[0].trim());
                int minute = Integer.parseInt(hm[1].trim());
                if (hour < 0 || hour > 23 || minute < 0 || minute > 59) continue;
                scheduleOneTime(context, index, hour, minute);
                index++;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    static void scheduleOneTime(Context context, int index, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);

        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next.getTimeInMillis(),
                timeIntent(context, index, hour, minute));
    }

    private static PendingIntent intervalIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.sinoise.app.INTERVAL_REMINDER");
        return PendingIntent.getBroadcast(
                context,
                INTERVAL_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent timeIntent(Context context, int index, int hour, int minute) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.sinoise.app.TIME_REMINDER_" + index);
        intent.putExtra("specific_time", true);
        intent.putExtra("index", index);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        return PendingIntent.getBroadcast(
                context,
                TIMES_REQUEST_BASE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
