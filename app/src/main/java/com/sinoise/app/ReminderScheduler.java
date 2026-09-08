package com.sinoise.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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
    private static final String PREF_INTERVAL_NEXT = "interval_next_elapsed";

    public static void restore(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", false)) return;
        scheduleFromPrefs(context);
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    public static void scheduleFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cancelAll(context);
        String mode = prefs.getString("reminder_mode", MODE_INTERVAL);
        if (MODE_TIMES.equals(mode)) {
            String times = prefs.getString("times", "09:00, 13:00, 17:00");
            scheduleTimes(context, times, prefs);
        } else {
            int minutes = Math.max(1, prefs.getInt("interval_minutes", 60));
            scheduleFirstInterval(context, minutes);
        }
    }

    public static void cancelAll(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(intervalIntent(context));
            for (int i = 0; i < MAX_TIMES; i++) {
                alarmManager.cancel(timeIntent(context, i, 0, 0));
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(PREF_INTERVAL_NEXT).apply();
    }

    private static void scheduleFirstInterval(Context context, int minutes) {
        long target = SystemClock.elapsedRealtime() + minutes * 60_000L;
        storeAndScheduleInterval(context, target);
    }

    static void scheduleNextInterval(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", false)) return;
        if (!MODE_INTERVAL.equals(prefs.getString("reminder_mode", MODE_INTERVAL))) return;

        long intervalMs = Math.max(1, prefs.getInt("interval_minutes", 60)) * 60_000L;
        long now = SystemClock.elapsedRealtime();
        long previousTarget = prefs.getLong(PREF_INTERVAL_NEXT, now);
        long next = previousTarget + intervalMs;
        while (next <= now) next += intervalMs;
        storeAndScheduleInterval(context, next);
    }

    private static void storeAndScheduleInterval(Context context, long targetElapsed) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(PREF_INTERVAL_NEXT, targetElapsed).apply();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = intervalIntent(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    targetElapsed,
                    pendingIntent);
        } else {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    targetElapsed,
                    pendingIntent);
        }
    }

    private static void scheduleTimes(Context context, String csv, SharedPreferences prefs) {
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
                if (isQuietMinute(prefs, hour * 60 + minute)) continue;
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

        PendingIntent pendingIntent = timeIntent(context, index, hour, minute);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.getTimeInMillis(),
                    pendingIntent);
        } else {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.getTimeInMillis(),
                    pendingIntent);
        }
    }

    public static boolean isQuietNow(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Calendar now = Calendar.getInstance();
        return isQuietMinute(prefs, now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
    }

    private static boolean isQuietMinute(SharedPreferences prefs, int minuteOfDay) {
        if (!prefs.getBoolean("quiet_enabled", false)) return false;
        int start = parseTimeToMinutes(prefs.getString("quiet_start", "23:00"));
        int end = parseTimeToMinutes(prefs.getString("quiet_end", "07:00"));
        if (start < 0 || end < 0 || start == end) return false;
        if (start < end) return minuteOfDay >= start && minuteOfDay < end;
        return minuteOfDay >= start || minuteOfDay < end;
    }

    public static int parseTimeToMinutes(String value) {
        if (value == null) return -1;
        String[] hm = value.trim().split(":");
        if (hm.length != 2) return -1;
        try {
            int hour = Integer.parseInt(hm[0].trim());
            int minute = Integer.parseInt(hm[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (NumberFormatException ignored) {
            return -1;
        }
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
