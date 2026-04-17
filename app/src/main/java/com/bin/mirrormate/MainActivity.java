package com.bin.mirrormate;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "RotationTilePrefs";
    public static final String PREF_COLLAPSE = "collapse_on_toggle";
    public static final String PREF_BRIGHTNESS_ENABLED = "brightness_enabled";
    public static final String PREF_BRIGHTNESS_PORTRAIT = "brightness_portrait";
    public static final String PREF_BRIGHTNESS_LANDSCAPE = "brightness_landscape";
    public static final String PREF_VOLUME_TRIGGER_WINDOW_MS = "volume_trigger_window_ms";
    public static final String PREF_ROTATION_SEQUENCE = "rotation_sequence";
    public static final String PREF_VOICE_SEQUENCE    = "voice_sequence";

    private static final int REQUEST_PHONE_STATE = 1001;

    private AlertDialog activeDialog = null;
    private SharedPreferences prefs;

    // Views
    private CheckBox checkCollapse;
    private CheckBox checkBrightness;
    private LinearLayout layoutBrightness;
    private SeekBar seekPortrait;
    private SeekBar seekLandscape;
    private CheckBox checkVolume;
    private LinearLayout layoutVolumeWindow;
    private SeekBar seekVolumeWindow;
    private TextView tvRotationSequencePreview;
    private TextView tvVoiceSequencePreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Auto-uncheck volume trigger if accessibility or phone state permission was revoked
        if (!isAccessibilityEnabled() || !isPhoneStateGranted()) {
            prefs.edit()
                    .putBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, false)
                    .apply();
        }

        // Refresh sequence previews when returning from SequenceActivity
        if (tvRotationSequencePreview != null) {
            tvRotationSequencePreview.setText(sequenceToReadable(
                    prefs.getString(PREF_ROTATION_SEQUENCE, "0,0,0,0,0")));
            tvVoiceSequencePreview.setText(sequenceToReadable(
                    prefs.getString(PREF_VOICE_SEQUENCE, "1,1,1,1,1")));
        }

        if (!Settings.System.canWrite(this)) {
            showDialog(buildPermissionDialog());
            return;
        }

        // If the volume trigger dialog is showing and accessibility just got granted
        if (activeDialog != null && activeDialog.isShowing() && isAccessibilityEnabled()) {
            activeDialog.dismiss();
            prefs.edit()
                    .putBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, true)
                    .apply();
            checkVolume.setChecked(true);
            layoutVolumeWindow.setVisibility(View.VISIBLE);
            return;
        }

        // First time showing the main screen
        if (checkCollapse == null) {
            showMainScreen();
        }
    }

    // ── dialog guard ──────────────────────────────────────────────────────────

    private void showDialog(AlertDialog.Builder builder) {
        if (activeDialog != null && activeDialog.isShowing()) {
            return;
        }
        activeDialog = builder.create();
        activeDialog.setOnDismissListener(d -> activeDialog = null);
        activeDialog.show();
    }

    // ── permission dialog ─────────────────────────────────────────────────────

    private AlertDialog.Builder buildPermissionDialog() {
        return new AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_required_title))
                .setMessage(getString(R.string.permission_required_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.continue_label), (d, w) -> openWriteSettings())
                .setNegativeButton(getString(R.string.cancel_label), (d, w) -> finish());
    }

    // ── main screen ───────────────────────────────────────────────────────────

    private void showMainScreen() {
        setContentView(R.layout.activity_main);
        bindViews();
        loadPreferences();
        attachListeners();
    }

    private void bindViews() {
        checkCollapse             = findViewById(R.id.check_collapse);
        checkBrightness           = findViewById(R.id.check_brightness);
        layoutBrightness          = findViewById(R.id.layout_brightness);
        seekPortrait              = findViewById(R.id.seek_brightness_portrait);
        seekLandscape             = findViewById(R.id.seek_brightness_landscape);
        checkVolume               = findViewById(R.id.check_volume);
        layoutVolumeWindow        = findViewById(R.id.layout_volume_window);
        seekVolumeWindow          = findViewById(R.id.seek_volume_window);
        tvRotationSequencePreview = findViewById(R.id.tv_rotation_sequence_preview);
        tvVoiceSequencePreview    = findViewById(R.id.tv_voice_sequence_preview);
    }

    private void loadPreferences() {
        checkCollapse.setChecked(prefs.getBoolean(PREF_COLLAPSE, false));

        boolean brightnessEnabled = prefs.getBoolean(PREF_BRIGHTNESS_ENABLED, false);
        checkBrightness.setChecked(brightnessEnabled);
        layoutBrightness.setVisibility(brightnessEnabled ? View.VISIBLE : View.GONE);
        seekPortrait.setProgress(prefs.getInt(PREF_BRIGHTNESS_PORTRAIT, 255));
        seekLandscape.setProgress(prefs.getInt(PREF_BRIGHTNESS_LANDSCAPE, 0));

        boolean volumeEnabled = prefs.getBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, false);
        checkVolume.setChecked(volumeEnabled);
        layoutVolumeWindow.setVisibility(volumeEnabled ? View.VISIBLE : View.GONE);
        seekVolumeWindow.setProgress(prefs.getInt(PREF_VOLUME_TRIGGER_WINDOW_MS, 5000) - 500);

        tvRotationSequencePreview.setText(sequenceToReadable(
                prefs.getString(PREF_ROTATION_SEQUENCE, "0,0,0,0,0")));
        tvVoiceSequencePreview.setText(sequenceToReadable(
                prefs.getString(PREF_VOICE_SEQUENCE, "1,1,1,1,1")));
    }

    private void attachListeners() {
        // Collapse
        checkCollapse.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean(PREF_COLLAPSE, isChecked).apply());

        // Brightness checkbox
        checkBrightness.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(PREF_BRIGHTNESS_ENABLED, isChecked).apply();
            layoutBrightness.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Brightness seekbars
        seekPortrait.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) prefs.edit().putInt(PREF_BRIGHTNESS_PORTRAIT, progress).apply();
            }
        });
        seekLandscape.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) prefs.edit().putInt(PREF_BRIGHTNESS_LANDSCAPE, progress).apply();
            }
        });

        // Volume trigger checkbox — request phone state first, then accessibility
        checkVolume.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                btn.setChecked(false); // revert until all permissions confirmed
                requestPhoneStatePermission();
            } else {
                prefs.edit().putBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, false).apply();
                layoutVolumeWindow.setVisibility(View.GONE);
            }
        });

        // Volume window seekbar
        TextView tvVolumeWindowValue = findViewById(R.id.tv_volume_window_value);
        int initialMs = prefs.getInt(PREF_VOLUME_TRIGGER_WINDOW_MS, 5000);
        tvVolumeWindowValue.setText(getString(R.string.current_value_ms, initialMs));

        seekVolumeWindow.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int ms = progress + 500;
                tvVolumeWindowValue.setText(getString(R.string.current_value_ms, ms));
                if (fromUser) prefs.edit()
                        .putInt(PREF_VOLUME_TRIGGER_WINDOW_MS, ms)
                        .apply();
            }
        });

        // Sequence configure buttons
        findViewById(R.id.btn_configure_rotation).setOnClickListener(v -> {
            Intent intent = new Intent(this, SequenceActivity.class);
            intent.putExtra(SequenceActivity.EXTRA_TARGET, SequenceActivity.TARGET_ROTATION);
            startActivity(intent);
        });
        findViewById(R.id.btn_configure_voice).setOnClickListener(v -> {
            Intent intent = new Intent(this, SequenceActivity.class);
            intent.putExtra(SequenceActivity.EXTRA_TARGET, SequenceActivity.TARGET_VOICE);
            startActivity(intent);
        });
    }

    // ── phone state permission ────────────────────────────────────────────────

    private void requestPhoneStatePermission() {
        if (isPhoneStateGranted()) {
            showVolumeTriggerPermissionDialog();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.READ_PHONE_STATE)) {
            // First denial — can ask again via system dialog
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.READ_PHONE_STATE},
                    REQUEST_PHONE_STATE);
        } else {
            // Permanently denied — send to app settings
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.permission_required_title))
                    .setMessage(getString(R.string.phone_state_denied_message))
                    .setPositiveButton("Open Settings", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton(getString(R.string.cancel_label),
                            (d, w) -> checkVolume.setChecked(false))
                    .show();
        }
    }

    private boolean isPhoneStateGranted() {
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PHONE_STATE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Granted — proceed to accessibility check
                showVolumeTriggerPermissionDialog();
            } else {
                // Denied — warn user and keep checkbox unchecked
                checkVolume.setChecked(false);
            }
        }
    }

    // ── volume trigger permission dialog ──────────────────────────────────────

    private void showVolumeTriggerPermissionDialog() {
        // If already granted, enable directly without showing dialog
        if (isAccessibilityEnabled()) {
            prefs.edit()
                    .putBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, true)
                    .apply();
            checkVolume.setChecked(true);
            layoutVolumeWindow.setVisibility(View.VISIBLE);
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dpToPx(20);
        layout.setPadding(p, p, p, p);

        boolean fromPlayStore = isInstalledFromPlayStore();

        // ── Step 1 (sideloaded only): Allow restricted settings ───────────────
        if (!fromPlayStore) {
            addSectionTitle(layout, getString(R.string.allow_restricted_settings_title));
            addBody(layout, getString(R.string.allow_restricted_settings_message));
            addDivider(layout);
        }

        // ── Step 2 (always): Enable accessibility service ─────────────────────
        addSectionTitle(layout, getString(R.string.enable_accessibility_service_title));
        addBody(layout, getString(R.string.enable_accessibility_service_message));
        addGoToButton(layout, getString(R.string.enable_accessibility_button_label),
                this::openAccessibilitySettings);

        // ── Warning: banking/sensitive apps ──────────────────────────────────  ← ADD HERE
        addDivider(layout);
        addSectionTitle(layout, getString(R.string.accessibility_warning_title));
        addBody(layout, getString(R.string.accessibility_warning_message));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.volume_trigger_setup))
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.close_label),
                        (d, w) -> checkVolume.setChecked(false))
                .setNegativeButton(null, null);

        showDialog(builder);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void openWriteSettings() {
        startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, dpToPx(8), 0, dpToPx(6));
        parent.addView(tv);
    }

    private void addBody(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(0, 0, 0, dpToPx(10));
        parent.addView(tv);
    }

    private void addGoToButton(LinearLayout parent, String label, Runnable action) {
        android.widget.Button btn = new android.widget.Button(this);
        btn.setText(label);
        btn.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(12);
        btn.setLayoutParams(params);
        parent.addView(btn);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        params.topMargin = dpToPx(8);
        params.bottomMargin = dpToPx(16);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(0x22888888);
        parent.addView(divider);
    }

    private boolean isInstalledFromPlayStore() {
        if (Build.VERSION.SDK_INT < 30) return false;
        try {
            String installer = getPackageManager()
                    .getInstallSourceInfo(getPackageName())
                    .getInitiatingPackageName();
            return "com.android.vending".equals(installer);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAccessibilityEnabled() {
        String service = getPackageName() + "/" + VolumeButtonService.class.getName();
        try {
            int enabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled == 0) return false;
            String enabledServices = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(service);
        } catch (Exception e) {
            return false;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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

    // ── SimpleSeekBarListener ─────────────────────────────────────────────────

    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}