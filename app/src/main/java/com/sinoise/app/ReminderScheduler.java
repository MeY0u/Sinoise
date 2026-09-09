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

    private static final int GOAL_INTERVAL_REQUEST = 500;
    private static final int NTD_INTERVAL_REQUEST = 600;
    private static final int GOAL_TIMES_REQUEST_BASE = 1000;
    private static final int NTD_TIMES_REQUEST_BASE = 2000;
    private static final int MAX_TIMES = 32;

    private static final String PREF_GOAL_INTERVAL_NEXT = "interval_next_elapsed";
    private static final String PREF_NTD_INTERVAL_NEXT = "ntd_interval_next_elapsed";

    static final String ACTION_GOAL_INTERVAL = "com.sinoise.app.GOAL_INTERVAL_REMINDER";
    static final String ACTION_NTD_INTERVAL = "com.sinoise.app.NTD_INTERVAL_REMINDER";

    public static void restore(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", false)
                && !prefs.getBoolean("ntd_reminders_enabled", false)) return;
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

        if (prefs.getBoolean("reminders_enabled", false)) {
            String mode = prefs.getString("reminder_mode", MODE_INTERVAL);
            if (MODE_TIMES.equals(mode)) {
                scheduleTimes(context, false, prefs.getString("times", "09:00, 13:00, 17:00"), prefs);
            } else {
                scheduleFirstInterval(context, false, Math.max(1, prefs.getInt("interval_minutes", 60)));
            }
        }

        if (prefs.getBoolean("ntd_reminders_enabled", false)) {
            String mode = prefs.getString("ntd_reminder_mode", MODE_INTERVAL);
            if (MODE_TIMES.equals(mode)) {
                scheduleTimes(context, true, prefs.getString("ntd_times", "10:00, 15:00, 20:00"), prefs);
            } else {
                scheduleFirstInterval(context, true, Math.max(1, prefs.getInt("ntd_interval_minutes", 180)));
            }
        }
    }

    public static void cancelAll(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(intervalIntent(context, false));
            alarmManager.cancel(intervalIntent(context, true));
            for (int i = 0; i < MAX_TIMES; i++) {
                alarmManager.cancel(timeIntent(context, false, i, 0, 0));
                alarmManager.cancel(timeIntent(context, true, i, 0, 0));
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_GOAL_INTERVAL_NEXT)
                .remove(PREF_NTD_INTERVAL_NEXT)
                .apply();
    }

    private static void scheduleFirstInterval(Context context, boolean notToDo, int minutes) {
        long target = SystemClock.elapsedRealtime() + minutes * 60_000L;
        storeAndScheduleInterval(context, notToDo, target);
    }

    static void scheduleNextInterval(Context context, boolean notToDo) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String enabledKey = notToDo ? "ntd_reminders_enabled" : "reminders_enabled";
        String modeKey = notToDo ? "ntd_reminder_mode" : "reminder_mode";
        String minutesKey = notToDo ? "ntd_interval_minutes" : "interval_minutes";
        String nextKey = notToDo ? PREF_NTD_INTERVAL_NEXT : PREF_GOAL_INTERVAL_NEXT;
        int defaultMinutes = notToDo ? 180 : 60;

        if (!prefs.getBoolean(enabledKey, false)) return;
        if (!MODE_INTERVAL.equals(prefs.getString(modeKey, MODE_INTERVAL))) return;

        long intervalMs = Math.max(1, prefs.getInt(minutesKey, defaultMinutes)) * 60_000L;
        long now = SystemClock.elapsedRealtime();
        long previousTarget = prefs.getLong(nextKey, now);
        long next = previousTarget + intervalMs;
        while (next <= now) next += intervalMs;
        storeAndScheduleInterval(context, notToDo, next);
    }

    private static void storeAndScheduleInterval(Context context, boolean notToDo, long targetElapsed) {
        String nextKey = notToDo ? PREF_NTD_INTERVAL_NEXT : PREF_GOAL_INTERVAL_NEXT;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(nextKey, targetElapsed).apply();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = intervalIntent(context, notToDo);
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

    private static void scheduleTimes(Context context, boolean notToDo, String csv, SharedPreferences prefs) {
        String[] parts = csv.split(",");
        int index = 0;
        for (String part : parts) {
            if (index >= MAX_TIMES) break;
            int minuteOfDay = parseTimeToMinutes(part.trim());
            if (minuteOfDay < 0) continue;
            if (isQuietMinute(prefs, minuteOfDay)) continue;
            scheduleOneTime(context, notToDo, index, minuteOfDay / 60, minuteOfDay % 60);
            index++;
        }
    }

    static void scheduleOneTime(Context context, boolean notToDo, int index, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);

        PendingIntent pendingIntent = timeIntent(context, notToDo, index, hour, minute);
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

    private static PendingIntent intervalIntent(Context context, boolean notToDo) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(notToDo ? ACTION_NTD_INTERVAL : ACTION_GOAL_INTERVAL);
        intent.putExtra("not_to_do", notToDo);
        return PendingIntent.getBroadcast(
                context,
                notToDo ? NTD_INTERVAL_REQUEST : GOAL_INTERVAL_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent timeIntent(Context context, boolean notToDo, int index, int hour, int minute) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction((notToDo ? "com.sinoise.app.NTD_TIME_REMINDER_" : "com.sinoise.app.GOAL_TIME_REMINDER_") + index);
        intent.putExtra("not_to_do", notToDo);
        intent.putExtra("specific_time", true);
        intent.putExtra("index", index);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        return PendingIntent.getBroadcast(
                context,
                (notToDo ? NTD_TIMES_REQUEST_BASE : GOAL_TIMES_REQUEST_BASE) + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
