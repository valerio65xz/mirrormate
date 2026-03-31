package com.example.rotationtile;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeButtonService extends AccessibilityService {

    public static final String PREF_VOLUME_TRIGGER = "volume_trigger_enabled";

    private static final int REQUIRED_PRESSES = 5;

    private int pressCount = 0;
    private long firstPressTime = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // not needed
    }

    @Override
    public void onInterrupt() {
        // not needed
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);

        if (!prefs.getBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, false)) {
            return false;
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
            long resetIntervalMs = prefs.getInt(MainActivity.PREF_VOLUME_TRIGGER_WINDOW_MS, 2000);
            long now = System.currentTimeMillis();

            // If the user doesn't press 5 times inside the defined time interval, reset the counter
            if (now - firstPressTime > resetIntervalMs) {
                pressCount = 0;
                firstPressTime = now;
            }

            pressCount++;

            if (pressCount >= REQUIRED_PRESSES) {
                pressCount = 0;
                firstPressTime = 0;
                onFivePressesDetected();
                return true;
            }
        }

        return false;
    }

    private void onFivePressesDetected() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        RotationHelper.execute(this, prefs, null);
    }
}