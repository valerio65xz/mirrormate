package com.bin.mirrormate;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayList;
import java.util.List;

public class VolumeButtonService extends AccessibilityService {

    public static final String PREF_VOLUME_TRIGGER = "volume_trigger_enabled";

    private static final String DEFAULT_ROTATION = "0,0,0,0,0";
    private static final String DEFAULT_VOICE    = "1,1,1,1,1";

    private final List<Integer> buffer = new ArrayList<>();
    private long lastPressTime = 0;

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

        if (shouldIgnoreEvents()) {
            return false;
        }

        if (!prefs.getBoolean(PREF_VOLUME_TRIGGER, false)) {
            return false;
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return false;
        }

        long now = System.currentTimeMillis();
        long windowMs = prefs.getInt(MainActivity.PREF_VOLUME_TRIGGER_WINDOW_MS, 2000);

        // Reset buffer if detection window expired
        if (lastPressTime > 0 && (now - lastPressTime) > windowMs) {
            buffer.clear();
        }
        lastPressTime = now;

        // Append press: 0 = down, 1 = up
        buffer.add(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ? 0 : 1);

        // Load sequences
        List<Integer> rotationSeq = parseSequence(prefs.getString(
                MainActivity.PREF_ROTATION_SEQUENCE, DEFAULT_ROTATION));
        List<Integer> voiceSeq = parseSequence(prefs.getString(
                MainActivity.PREF_VOICE_SEQUENCE, DEFAULT_VOICE));

        // Check rotation match
        if (bufferEndsWith(rotationSeq)) {
            buffer.clear();
            lastPressTime = 0;
            onRotationTriggered(prefs);
            return true;
        }

        // Check voice match
        if (bufferEndsWith(voiceSeq)) {
            buffer.clear();
            lastPressTime = 0;
            onVoiceTriggered();
            return true;
        }

        // Reset if buffer exceeds max sequence length
        int maxLen = Math.max(rotationSeq.size(), voiceSeq.size());
        if (buffer.size() > maxLen) {
            buffer.clear();
        }

        return false;
    }

    // ── actions ───────────────────────────────────────────────────────────────

    private void onRotationTriggered(SharedPreferences prefs) {
        RotationHelper.execute(this, prefs, null);
    }

    private void onVoiceTriggered() {
        Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean bufferEndsWith(List<Integer> sequence) {
        int seqLen = sequence.size();
        int bufLen = buffer.size();
        if (bufLen < seqLen) return false;
        List<Integer> tail = buffer.subList(bufLen - seqLen, bufLen);
        return tail.equals(sequence);
    }

    private List<Integer> parseSequence(String raw) {
        List<Integer> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private boolean shouldIgnoreEvents() {
        // Pause if SequenceActivity is recording
        if (SequenceActivity.isRecording) return true;

        // Pause if a phone call is in progress
        android.telephony.TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        return tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

}