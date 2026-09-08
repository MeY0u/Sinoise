package com.sinoise.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SinoiseApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (!(activity instanceof MainActivity)) return;

        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;

        View original = content.getChildAt(0);
        content.removeView(original);

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(Color.rgb(250, 250, 250));

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        bar.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(activity);
        title.setText("SINOISE");
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setLetterSpacing(0.14f);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        title.setGravity(Gravity.CENTER_VERTICAL);

        TextView about = new TextView(activity);
        about.setText("ABOUT");
        about.setTextSize(13);
        about.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        about.setTextColor(Color.rgb(20, 20, 20));
        about.setGravity(Gravity.CENTER);
        about.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        about.setClickable(true);
        about.setFocusable(true);
        about.setOnClickListener(v -> showAbout(activity));
        bar.addView(about, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(activity, 44)));

        View divider = new View(activity);
        divider.setBackgroundColor(Color.rgb(232, 232, 232));

        wrapper.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 1)));
        wrapper.addView(original, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        content.addView(wrapper, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showAbout(Activity activity) {
        String message =
                "Signal > noise\n\n" +
                "Version 0.5.0\n\n" +
                "Made by Gennady109";

        new AlertDialog.Builder(activity)
                .setTitle("About Sinoise")
                .setMessage(message)
                .setPositiveButton("CLOSE", null)
                .show();
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
