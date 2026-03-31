package com.example.rotationtile;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "RotationTilePrefs";
    public static final String PREF_COLLAPSE = "collapse_on_toggle";
    public static final String PREF_BRIGHTNESS_ENABLED = "brightness_enabled";
    public static final String PREF_BRIGHTNESS_PORTRAIT = "brightness_portrait";
    public static final String PREF_BRIGHTNESS_LANDSCAPE = "brightness_landscape";
    public static final String PREF_VOLUME_TRIGGER_WINDOW_MS = "volume_trigger_window_ms";

    private AlertDialog activeDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Auto-uncheck volume trigger if accessibility was revoked
        if (!isAccessibilityEnabled()) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, false)
                    .apply();
        }

        if (!Settings.System.canWrite(this)) {
            showDialog(buildPermissionDialog());
        } else {
            showDialog(buildSettingsDialog());
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
                .setTitle("⚠️ Permission required")
                .setMessage("""
                        This app needs the "Modify system settings" permission
                        to control screen rotation and brightness.
                        
                        Tap Continue to open the permission screen,
                        then enable the toggle for this app.""")
                .setCancelable(false)
                .setPositiveButton("Continue", (d, w) -> openWriteSettings())
                .setNegativeButton("Cancel", (d, w) -> finish());
    }

    private void openWriteSettings() {
        startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    // ── volume trigger permission dialog ──────────────────────────────────────

    private void showVolumeTriggerPermissionDialog(CheckBox volumeCheckbox) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dpToPx(20);
        layout.setPadding(p, p, p, p);

        boolean fromPlayStore = isInstalledFromPlayStore();
        boolean accessibilityGranted = isAccessibilityEnabled();
        int stepNumber = 1;

        // ── Step 1 (sideloaded only): Allow restricted settings ───────────────
        if (!fromPlayStore) {
            addSectionTitle(layout, "Step " + stepNumber++ + " — Allow restricted settings");
            addBody(layout,
                    """
                            Since this app was installed outside the Play Store, Android restricts
                            access to the Accessibility service.
                            
                            Follow these steps to unlock it:
                            
                            ① Go to Settings → Apps → Rotation Tile
                            
                            ② Tap the ⋮ three-dot menu in the top-right corner
                               → Select "Allow restricted settings"
                            
                            ⚠️ If the ⋮ menu doesn't appear:
                               Click on "Open Accessibility" below and enable any toggle
                            (e.g. "Press power button to end calls").
                            Then go back to App Info — the ⋮ menu will appear.
                            You can disable that toggle again afterwards.
                            
                            ③ Once allowed, continue to the next step.""");
            addGoToButton(layout, "Open App Info →", () -> startActivity(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))));
            addDivider(layout);
        }

        // ── Step 2 (always): Enable accessibility service ─────────────────────
        addSectionTitle(layout, "Step " + stepNumber + " — Enable Accessibility service");
        addStatusRow(layout, "Volume Trigger service enabled", accessibilityGranted);

        if (!accessibilityGranted) {
            addBody(layout,
                    "In the Accessibility settings, find \"Downloaded apps\" " +
                            "→ tap \"Volume Trigger\" and enable it.");
            addGoToButton(layout, "Open Accessibility →", this::openAccessibilitySettings);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("🔧 Volume trigger setup")
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton(accessibilityGranted ? "Enable & Save" : "Close", (d, w) -> {
                    if (accessibilityGranted) {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, true)
                                .apply();
                        volumeCheckbox.setChecked(true);
                    } else {
                        volumeCheckbox.setChecked(false);
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> volumeCheckbox.setChecked(false));

        showDialog(builder);
    }

    // ── main settings dialog ──────────────────────────────────────────────────

    private AlertDialog.Builder buildSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dpToPx(20);
        layout.setPadding(p, p, p, 0);

        TextView description = new TextView(this);
        description.setText(getString(R.string.settings_description));
        description.setTextSize(14);
        description.setPadding(0, 0, 0, dpToPx(16));
        layout.addView(description);

        // ── Collapse panel ────────────────────────────────────────────────────
        CheckBox checkCollapse = new CheckBox(this);
        checkCollapse.setText(getString(R.string.pref_collapse));
        checkCollapse.setChecked(prefs.getBoolean(PREF_COLLAPSE, false));
        layout.addView(checkCollapse);
        addHint(layout, "Automatically dismisses Quick Settings when the tile is tapped.");

        // ── Brightness per orientation ────────────────────────────────────────
        CheckBox checkBrightness = new CheckBox(this);
        checkBrightness.setText(getString(R.string.pref_brightness));
        checkBrightness.setChecked(prefs.getBoolean(PREF_BRIGHTNESS_ENABLED, false));
        layout.addView(checkBrightness);
        addHint(layout, "Applies a different brightness level when switching orientation.");

        LinearLayout portraitRow = buildNumberInputRow(
                "Portrait brightness (0–255):",
                prefs.getInt(PREF_BRIGHTNESS_PORTRAIT, 128));
        EditText portraitInput = (EditText) portraitRow.getChildAt(1);
        layout.addView(portraitRow);

        LinearLayout landscapeRow = buildNumberInputRow(
                "Landscape brightness (0–255):",
                prefs.getInt(PREF_BRIGHTNESS_LANDSCAPE, 50));
        EditText landscapeInput = (EditText) landscapeRow.getChildAt(1);
        layout.addView(landscapeRow);

        int brightnessVisibility = checkBrightness.isChecked()
                ? android.view.View.VISIBLE : android.view.View.GONE;
        portraitRow.setVisibility(brightnessVisibility);
        landscapeRow.setVisibility(brightnessVisibility);

        checkBrightness.setOnCheckedChangeListener((btn, isChecked) -> {
            portraitRow.setVisibility(isChecked
                    ? android.view.View.VISIBLE : android.view.View.GONE);
            landscapeRow.setVisibility(isChecked
                    ? android.view.View.VISIBLE : android.view.View.GONE);
        });

        // ── Volume trigger ────────────────────────────────────────────────────
        CheckBox checkVolume = new CheckBox(this);
        checkVolume.setText(getString(R.string.check_volume));
        checkVolume.setChecked(prefs.getBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, false));
        layout.addView(checkVolume);
        addHint(layout, getString(R.string.volume_hint));

        LinearLayout volumeWindowRow = buildNumberInputRow(
                "Detection window (ms):",
                prefs.getInt(PREF_VOLUME_TRIGGER_WINDOW_MS, 2000));
        volumeWindowRow.getChildAt(1).getLayoutParams().width = dpToPx(80);
        EditText volumeWindowInput = (EditText) volumeWindowRow.getChildAt(1);
        layout.addView(volumeWindowRow);

        volumeWindowRow.setVisibility(checkVolume.isChecked()
                ? android.view.View.VISIBLE : android.view.View.GONE);

        checkVolume.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                btn.setChecked(false);
                showVolumeTriggerPermissionDialog(checkVolume);
            } else {
                volumeWindowRow.setVisibility(android.view.View.GONE);
                prefs.edit()
                        .putBoolean(VolumeButtonService.PREF_VOLUME_TRIGGER, false)
                        .apply();
            }
        });

        checkVolume.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) ->
                volumeWindowRow.setVisibility(checkVolume.isChecked()
                        ? android.view.View.VISIBLE : android.view.View.GONE));

        // ── Save ──────────────────────────────────────────────────────────────
        return new AlertDialog.Builder(this)
                .setTitle("⚙️ Rotation Tile Settings")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Save & Close", (d, w) -> {
                    prefs.edit()
                            .putBoolean(PREF_COLLAPSE, checkCollapse.isChecked())
                            .putBoolean(PREF_BRIGHTNESS_ENABLED, checkBrightness.isChecked())
                            .putInt(PREF_BRIGHTNESS_PORTRAIT,
                                    clampValue(portraitInput.getText().toString(), 128, 0, 255))
                            .putInt(PREF_BRIGHTNESS_LANDSCAPE,
                                    clampValue(landscapeInput.getText().toString(), 64, 0, 255))
                            .putInt(PREF_VOLUME_TRIGGER_WINDOW_MS,
                                    clampValue(volumeWindowInput.getText().toString(), 2000, 500, 10000))
                            .apply();
                    finish();
                });
    }

    // ── layout helpers ────────────────────────────────────────────────────────

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

    private void addStatusRow(LinearLayout parent, String label, boolean granted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dpToPx(4), 0, dpToPx(8));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        icon.setText(granted ? "✅ " : "❌ ");
        icon.setTextSize(16);
        row.addView(icon);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        row.addView(tv);

        parent.addView(row);
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
        android.view.View divider = new android.view.View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        params.topMargin = dpToPx(8);
        params.bottomMargin = dpToPx(16);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(0x22888888);
        parent.addView(divider);
    }

    private void addHint(LinearLayout parent, String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextSize(12);
        hint.setAlpha(0.6f);
        hint.setPadding(dpToPx(32), 0, 0, dpToPx(16));
        parent.addView(hint);
    }

    private LinearLayout buildNumberInputRow(String label, int currentValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(32), dpToPx(4), 0, dpToPx(8));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tvParams);
        row.addView(tv);

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(currentValue));
        et.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                dpToPx(60), LinearLayout.LayoutParams.WRAP_CONTENT);
        et.setLayoutParams(etParams);
        row.addView(et);

        return row;
    }

    // ── utils ─────────────────────────────────────────────────────────────────

    private int clampValue(String value, int defaultValue, int min, int max) {
        try {
            return Math.min(max, Math.max(min, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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

}