package com.sinoise.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private final EditText[] goals = new EditText[3];
    private final CheckBox[] done = new CheckBox[3];
    private EditText notToDo;
    private RadioGroup modeGroup;
    private RadioButton intervalRadio;
    private RadioButton timesRadio;
    private EditText intervalMinutes;
    private EditText times;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        ReminderReceiver.createChannel(this);
        requestNotificationsIfNeeded();
        setContentView(buildUi());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null
                && prefs.getBoolean("reminders_enabled", false)
                && ReminderScheduler.MODE_TIMES.equals(prefs.getString("reminder_mode", ReminderScheduler.MODE_INTERVAL))
                && ReminderScheduler.canScheduleExact(this)) {
            ReminderScheduler.scheduleFromPrefs(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        save(false);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(250, 250, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = text("SINOISE", 14, Typeface.BOLD);
        brand.setLetterSpacing(0.16f);
        root.addView(brand);

        TextView subtitle = text("Signal > noise", 30, Typeface.BOLD);
        subtitle.setPadding(0, dp(6), 0, dp(30));
        root.addView(subtitle);

        root.addView(sectionTitle("3 GOALS TODAY"));
        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            done[i] = new CheckBox(this);
            done[i].setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20, 20, 20)));
            row.addView(done[i], new LinearLayout.LayoutParams(dp(44), dp(52)));

            goals[i] = field("Goal " + (i + 1), false);
            row.addView(goals[i], new LinearLayout.LayoutParams(0, dp(52), 1f));
            root.addView(row);
        }

        Button newDay = button("NEW DAY · RESET CHECKS");
        newDay.setOnClickListener(v -> {
            for (CheckBox box : done) box.setChecked(false);
            save(false);
            Toast.makeText(this, "Checks reset", Toast.LENGTH_SHORT).show();
        });
        root.addView(newDay, marginTop(dp(10)));

        root.addView(spacer(28));
        root.addView(sectionTitle("NOT TO DO"));
        notToDo = field("One item per line\nNo Instagram before 18:00\nDon't start new projects", true);
        notToDo.setGravity(Gravity.TOP | Gravity.START);
        root.addView(notToDo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        root.addView(spacer(28));
        root.addView(sectionTitle("SILENT REMINDERS"));

        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        intervalRadio = radio("Every interval");
        timesRadio = radio("At chosen times");
        modeGroup.addView(intervalRadio);
        modeGroup.addView(timesRadio);
        root.addView(modeGroup);

        TextView intervalLabel = smallLabel("INTERVAL · MINUTES (1+) ");
        root.addView(intervalLabel, marginTop(dp(10)));
        intervalMinutes = field("60", false);
        intervalMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(intervalMinutes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView timesLabel = smallLabel("TIMES · 24H, COMMA-SEPARATED");
        root.addView(timesLabel, marginTop(dp(14)));
        times = field("09:00, 13:00, 17:00", false);
        root.addView(times, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView note = text("Chosen times use exact alarms when Android allows it. Notifications stay silent: no sound or vibration.", 13, Typeface.NORMAL);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(12), 0, dp(8));
        root.addView(note);

        Button test = button("TEST NOTIFICATION NOW");
        test.setOnClickListener(v -> {
            save(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationsIfNeeded();
                Toast.makeText(this, "Allow notifications, then tap TEST again", Toast.LENGTH_LONG).show();
                return;
            }
            boolean shown = ReminderReceiver.showNow(this);
            Toast.makeText(this, shown ? "Test notification sent" : "Notification permission is blocked", Toast.LENGTH_SHORT).show();
        });
        root.addView(test, marginTop(dp(8)));

        Button save = button("SAVE & SCHEDULE");
        save.setOnClickListener(v -> save(true));
        root.addView(save, marginTop(dp(10)));

        Button stop = button("STOP REMINDERS");
        stop.setOnClickListener(v -> {
            prefs.edit().putBoolean("reminders_enabled", false).apply();
            ReminderScheduler.cancelAll(this);
            Toast.makeText(this, "Reminders stopped", Toast.LENGTH_SHORT).show();
        });
        root.addView(stop, marginTop(dp(10)));

        return scroll;
    }

    private void load() {
        for (int i = 0; i < 3; i++) {
            goals[i].setText(prefs.getString("goal_" + (i + 1), ""));
            done[i].setChecked(prefs.getBoolean("goal_done_" + (i + 1), false));
        }
        notToDo.setText(prefs.getString("not_to_do", ""));
        intervalMinutes.setText(String.valueOf(prefs.getInt("interval_minutes", 60)));
        times.setText(prefs.getString("times", "09:00, 13:00, 17:00"));
        String mode = prefs.getString("reminder_mode", ReminderScheduler.MODE_INTERVAL);
        if (ReminderScheduler.MODE_TIMES.equals(mode)) timesRadio.setChecked(true);
        else intervalRadio.setChecked(true);
    }

    private void save(boolean schedule) {
        int minutes = 60;
        try {
            minutes = Integer.parseInt(intervalMinutes.getText().toString().trim());
        } catch (NumberFormatException ignored) {
        }
        if (minutes < 1) minutes = 1;
        intervalMinutes.setText(String.valueOf(minutes));

        String mode = timesRadio.isChecked() ? ReminderScheduler.MODE_TIMES : ReminderScheduler.MODE_INTERVAL;
        if (schedule && ReminderScheduler.MODE_TIMES.equals(mode) && !containsValidTime(times.getText().toString())) {
            Toast.makeText(this, "Add at least one valid time, e.g. 09:00", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < 3; i++) {
            editor.putString("goal_" + (i + 1), goals[i].getText().toString().trim());
            editor.putBoolean("goal_done_" + (i + 1), done[i].isChecked());
        }
        editor.putString("not_to_do", notToDo.getText().toString());
        editor.putString("reminder_mode", mode);
        editor.putInt("interval_minutes", minutes);
        editor.putString("times", times.getText().toString().trim());
        if (schedule) editor.putBoolean("reminders_enabled", true);
        editor.apply();

        if (schedule) {
            ReminderScheduler.scheduleFromPrefs(this);
            if (ReminderScheduler.MODE_TIMES.equals(mode) && !ReminderScheduler.canScheduleExact(this)) {
                requestExactAlarmAccess();
                Toast.makeText(this, "Allow Alarms & reminders for exact chosen times", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Silent reminders scheduled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private boolean containsValidTime(String csv) {
        String[] parts = csv.split(",");
        for (String part : parts) {
            String[] hm = part.trim().split(":");
            if (hm.length != 2) continue;
            try {
                int h = Integer.parseInt(hm[0].trim());
                int m = Integer.parseInt(hm[1].trim());
                if (h >= 0 && h <= 23 && m >= 0 && m <= 59) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 13, Typeface.BOLD);
        view.setLetterSpacing(0.12f);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView smallLabel(String value) {
        TextView view = text(value, 11, Typeface.BOLD);
        view.setTextColor(Color.DKGRAY);
        view.setLetterSpacing(0.08f);
        view.setPadding(0, 0, 0, dp(5));
        return view;
    }

    private TextView text(String value, int sp, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(20, 20, 20));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private EditText field(String hint, boolean multiLine) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(16);
        edit.setTextColor(Color.rgb(15, 15, 15));
        edit.setHintTextColor(Color.rgb(145, 145, 145));
        edit.setPadding(dp(14), dp(10), dp(14), dp(10));
        edit.setBackground(makeFieldBackground());
        if (multiLine) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            edit.setSingleLine(true);
        }
        return edit;
    }

    private android.graphics.drawable.GradientDrawable makeFieldBackground() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.rgb(225, 225, 225));
        return bg;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackground(makeButtonBackground());
        return button;
    }

    private android.graphics.drawable.GradientDrawable makeButtonBackground() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(20, 20, 20));
        bg.setCornerRadius(dp(10));
        return bg;
    }

    private RadioButton radio(String value) {
        RadioButton radio = new RadioButton(this);
        radio.setText(value);
        radio.setTextSize(15);
        radio.setTextColor(Color.rgb(20, 20, 20));
        radio.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20, 20, 20)));
        return radio;
    }

    private View spacer(int dp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return view;
    }

    private LinearLayout.LayoutParams marginTop(int px) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = px;
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
