package com.bin.mirrormate;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class SequenceActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET = "target";
    public static final String TARGET_ROTATION = "rotation";
    public static final String TARGET_VOICE = "voice";
    public static boolean isRecording = false;

    private static final int MIN_PRESSES = 3;
    private static final int MAX_PRESSES = 8;

    // 0 = volume down, 1 = volume up
    private static final String DEFAULT_ROTATION = "0,0,0,0,0";
    private static final String DEFAULT_VOICE    = "1,1,1,1,1";

    private String target;
    private final List<Integer> currentPresses = new ArrayList<>();

    private LinearLayout layoutIndicators;
    private TextView tvPressCount;
    private TextView tvError;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sequence);

        target = getIntent().getStringExtra(EXTRA_TARGET);

        layoutIndicators = findViewById(R.id.layout_press_indicators);
        tvPressCount      = findViewById(R.id.tv_press_count);
        tvError           = findViewById(R.id.tv_error);
        TextView tvCurrentSequence = findViewById(R.id.tv_current_sequence);
        TextView tvEditingLabel = findViewById(R.id.tv_editing_label);
        btnSave           = findViewById(R.id.btn_save);
        Button btnClear = findViewById(R.id.btn_clear);

        // Show which sequence we're editing
        boolean isRotation = TARGET_ROTATION.equals(target);
        tvEditingLabel.setText(getString(isRotation
                ? R.string.sequence_editing_rotation
                : R.string.sequence_editing_voice));

        // Show current saved sequence
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(
                isRotation ? MainActivity.PREF_ROTATION_SEQUENCE : MainActivity.PREF_VOICE_SEQUENCE,
                isRotation ? DEFAULT_ROTATION : DEFAULT_VOICE);
        tvCurrentSequence.setText(getString(R.string.sequence_current, sequenceToReadable(saved)));

        updatePressCount();

        btnClear.setOnClickListener(v -> clearPresses());
        btnSave.setOnClickListener(v -> saveSequence());
    }

    @Override
    protected void onResume() {
        super.onResume();
        isRecording = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRecording = false;
    }

    // ── key capture ───────────────────────────────────────────────────────────

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            addPress(0);
            return true; // consume — don't change volume
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            addPress(1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── press handling ────────────────────────────────────────────────────────

    private void addPress(int press) {
        if (currentPresses.size() >= MAX_PRESSES) return;

        currentPresses.add(press);
        addIndicator(press);
        updatePressCount();
        clearError();
        validateAndToggleSave();
    }

    private void clearPresses() {
        currentPresses.clear();
        layoutIndicators.removeAllViews();
        updatePressCount();
        clearError();
        btnSave.setEnabled(false);
    }

    private void addIndicator(int press) {
        TextView pill = new TextView(this);
        pill.setText(press == 0 ? "▼" : "▲");
        pill.setTextSize(18);
        pill.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        pill.setTextColor(press == 0
                ? ContextCompat.getColor(this, android.R.color.holo_blue_light)
                : ContextCompat.getColor(this, android.R.color.holo_orange_light));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dpToPx(6));
        pill.setLayoutParams(params);

        layoutIndicators.addView(pill);
    }

    private void updatePressCount() {
        int count = currentPresses.size();
        tvPressCount.setText(getString(R.string.sequence_press_count, count, MAX_PRESSES));
    }

    // ── validation ────────────────────────────────────────────────────────────

    private void validateAndToggleSave() {
        if (currentPresses.size() < MIN_PRESSES) {
            btnSave.setEnabled(false);
            return;
        }

        String newSeq = listToString(currentPresses);
        String otherSeq = getOtherSequence();

        // Check equal
        if (newSeq.equals(otherSeq)) {
            showError(getString(R.string.sequence_error_equal));
            btnSave.setEnabled(false);
            return;
        }

        // Check if new is prefix of other
        if (otherSeq.startsWith(newSeq + ",")) {
            showError(getString(R.string.sequence_error_prefix_of_other));
            btnSave.setEnabled(false);
            return;
        }

        // Check if other is prefix of new
        if (newSeq.startsWith(otherSeq + ",")) {
            showError(getString(R.string.sequence_error_other_prefix));
            btnSave.setEnabled(false);
            return;
        }

        clearError();
        btnSave.setEnabled(true);
    }

    private String getOtherSequence() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        boolean isRotation = TARGET_ROTATION.equals(target);
        return prefs.getString(
                isRotation ? MainActivity.PREF_VOICE_SEQUENCE : MainActivity.PREF_ROTATION_SEQUENCE,
                isRotation ? DEFAULT_VOICE : DEFAULT_ROTATION);
    }

    // ── save ──────────────────────────────────────────────────────────────────

    private void saveSequence() {
        String newSeq = listToString(currentPresses);
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String key = TARGET_ROTATION.equals(target)
                ? MainActivity.PREF_ROTATION_SEQUENCE
                : MainActivity.PREF_VOICE_SEQUENCE;
        prefs.edit().putString(key, newSeq).apply();
        finish();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String listToString(List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private String sequenceToReadable(String seq) {
        if (seq == null || seq.isEmpty()) return "";
        String[] parts = seq.split(",");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append("0".equals(part.trim()) ? "▼" : "▲");
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
    }

    private void clearError() {
        tvError.setVisibility(View.GONE);
        tvError.setText("");
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}