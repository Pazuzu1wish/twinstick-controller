package com.twinstick.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Edit one (user) profile: remap button usages, tune axis usages/ranges,
 * and the hat usage. Everything is validated on save; bad values are
 * rejected with the reason shown.
 */
public class ProfileEditActivity extends Activity {

    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_SAVED_ACTIVE = "saved_active";

    private static final String[] BTN_HINTS = {
            "1 → 303 BTN_SOUTH", "2 → 305 BTN_EAST", "3 → 306 BTN_C",
            "4 → 307 BTN_X (north slot)", "5 → 308 BTN_Y (west slot)",
            "6 → 309 BTN_Z", "7 → 310 BTN_TL", "8 → 311 BTN_TR",
            "9 → 312 BTN_TL2", "10 → 313 BTN_TR2",
            "11 → 314 BTN_SELECT", "12 → 315 BTN_START",
            "13 → 316 BTN_MODE", "14 → 317 BTN_THUMBL",
            "15 → 318 BTN_THUMBR", "16 → 319 (no standard name)",
    };

    private static final String[] AXIS_COMMON_LABELS = {
            "X — 0x30 (48)", "Y — 0x31 (49)", "Z — 0x32 (50)",
            "Rx — 0x33 (51)", "Ry — 0x34 (52)", "Rz — 0x35 (53)",
            "Slider — 0x36 (54)", "Dial — 0x37 (55)",
            "Accelerator — 0xC4 (196) [simulation]",
            "Brake — 0xC5 (197) [simulation]",
            "Custom…",
    };
    // page, usage pairs matching the labels above (except Custom)
    private static final int[][] AXIS_COMMON = {
            {0x01, 0x30}, {0x01, 0x31}, {0x01, 0x32}, {0x01, 0x33},
            {0x01, 0x34}, {0x01, 0x35}, {0x01, 0x36}, {0x01, 0x37},
            {0x02, 0xC4}, {0x02, 0xC5},
    };

    private String profileId;
    private JSONObject working;
    private EditText nameField;
    private LinearLayout btnSection;
    private LinearLayout axisSection;
    private LinearLayout hatSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        try {
            HidProfile p = ProfileStore.load(this, profileId);
            working = new JSONObject(p.toJson());
        } catch (Exception e) {
            toast("Could not load profile: " + e.getMessage());
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("Edit profile");
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        body.addView(sectionHeader("Profile name"));
        nameField = new EditText(this);
        try { nameField.setText(working.getString("profile_name")); }
        catch (JSONException ignored) {}
        nameField.setTextColor(0xFFFFFFFF);
        body.addView(nameField);

        body.addView(sectionHeader("Buttons — tap to remap usage"));
        btnSection = new LinearLayout(this);
        btnSection.setOrientation(LinearLayout.VERTICAL);
        body.addView(btnSection);

        body.addView(sectionHeader("Axes — tap to remap / tune"));
        axisSection = new LinearLayout(this);
        axisSection.setOrientation(LinearLayout.VERTICAL);
        body.addView(axisSection);

        body.addView(sectionHeader("Hat switch (D-pad)"));
        hatSection = new LinearLayout(this);
        hatSection.setOrientation(LinearLayout.VERTICAL);
        body.addView(hatSection);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, 16, 0, 0);
        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> onSave());
        buttons.addView(save);
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> finish());
        buttons.addView(cancel);
        root.addView(buttons);

        setContentView(root);
        refreshRows();
    }

    private TextView sectionHeader(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setTextColor(0xFF9AC8FF);
        t.setPadding(0, 20, 0, 8);
        return t;
    }

    private void refreshRows() {
        try {
            btnSection.removeAllViews();
            JSONArray buttons = working.getJSONArray("buttons");
            for (int i = 0; i < buttons.length(); i++) {
                JSONObject b = buttons.getJSONObject(i);
                btnSection.addView(rowView(
                        b.getString("id") + "  →  usage " + b.getInt("usage") +
                                "  (" + BTN_HINTS[b.getInt("usage") - 1] + ")",
                        () -> editButton(b)));
            }
            axisSection.removeAllViews();
            JSONArray axes = working.getJSONArray("axes");
            for (int i = 0; i < axes.length(); i++) {
                JSONObject a = axes.getJSONObject(i);
                axisSection.addView(rowView(
                        a.getString("id") + "  →  " + usageLabel(a) +
                                "  [" + a.getInt("min") + ".." + a.getInt("max") + "]" +
                                (a.optBoolean("invert", false) ? " inv" : "") +
                                " dz=" + a.optDouble("deadzone", 0.0),
                        () -> editAxis(a)));
            }
            hatSection.removeAllViews();
            JSONObject h = working.getJSONObject("hat");
            hatSection.addView(rowView(
                    "hat  →  " + usageLabel(h), this::editHat));
        } catch (Exception e) {
            toast("Profile data broken: " + e.getMessage());
        }
    }

    private TextView rowView(String text, Runnable onTap) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(16, 14, 16, 14);
        t.setBackgroundColor(0xFF1E1E28);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> onTap.run());
        return t;
    }

    private static String usageLabel(JSONObject e) throws Exception {
        int page = HidProfile.pageFromName(e.getString("page"));
        int usage = e.getInt("usage");
        String name = null;
        for (int i = 0; i < AXIS_COMMON.length; i++) {
            if (AXIS_COMMON[i][0] == page && AXIS_COMMON[i][1] == usage) {
                name = AXIS_COMMON_LABELS[i].split(" — ")[0];
                break;
            }
        }
        if (usage == 0x39 && page == 0x01) name = "Hat switch";
        return "0x" + Integer.toHexString(usage).toUpperCase() +
                (name != null ? " (" + name + ")" : "") +
                " [" + HidProfile.pageToName(page) + "]";
    }

    // ---- button editing ----

    private void editButton(JSONObject b) {
        int current;
        String id;
        try {
            current = b.getInt("usage");
            id = b.getString("id");
        } catch (JSONException e) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Button " + id + " → usage")
                .setSingleChoiceItems(BTN_HINTS, current - 1, (d, which) -> {
                    try {
                        b.put("usage", which + 1);
                    } catch (JSONException ignored) {}
                    refreshRows();
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- axis editing ----

    private void editAxis(JSONObject a) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(24, 16, 24, 0);

        TextView usageView = new TextView(this);
        usageView.setTextColor(0xFFFFFFFF);
        Button changeUsage = new Button(this);
        changeUsage.setText("Change usage…");
        Runnable refreshUsage = () -> {
            try { usageView.setText("Usage: " + usageLabel(a)); }
            catch (Exception ignored) {}
        };
        refreshUsage.run();
        changeUsage.setOnClickListener(v -> pickAxisUsage(a, refreshUsage));
        form.addView(usageView);
        form.addView(changeUsage);

        EditText minF = numberField("min", intStr(a, "min"));
        EditText maxF = numberField("max", intStr(a, "max"));
        EditText dzF = decimalField("deadzone 0..0.9", dblStr(a, "deadzone"));
        CheckBox inv = new CheckBox(this);
        inv.setText("Invert");
        inv.setTextColor(0xFFFFFFFF);
        inv.setChecked(a.optBoolean("invert", false));
        form.addView(labeled("Min", minF));
        form.addView(labeled("Max", maxF));
        form.addView(labeled("Deadzone", dzF));
        form.addView(inv);

        String id;
        try { id = a.getString("id"); } catch (JSONException e) { id = "?"; }
        new AlertDialog.Builder(this)
                .setTitle("Axis " + id)
                .setView(form)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        a.put("min", Integer.parseInt(minF.getText().toString().trim()));
                        a.put("max", Integer.parseInt(maxF.getText().toString().trim()));
                        a.put("deadzone", Double.parseDouble(dzF.getText().toString().trim()));
                        a.put("invert", inv.isChecked());
                    } catch (Exception ex) {
                        toast("Bad number: " + ex.getMessage());
                        return;
                    }
                    refreshRows();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickAxisUsage(JSONObject a, Runnable onPicked) {
        new AlertDialog.Builder(this)
                .setTitle("Pick usage")
                .setItems(AXIS_COMMON_LABELS, (d, which) -> {
                    if (which < AXIS_COMMON.length) {
                        try {
                            a.put("page", HidProfile.pageToName(AXIS_COMMON[which][0]));
                            a.put("usage", AXIS_COMMON[which][1]);
                        } catch (JSONException ignored) {}
                        onPicked.run();
                    } else {
                        customAxisUsage(a, onPicked);
                    }
                })
                .show();
    }

    private void customAxisUsage(JSONObject a, Runnable onPicked) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(24, 16, 24, 0);
        final String[] pages = {"generic_desktop", "simulation"};
        final int[] sel = {0};
        try {
            int p = HidProfile.pageFromName(a.getString("page"));
            sel[0] = (p == 0x02) ? 1 : 0;
        } catch (Exception ignored) {}
        EditText usageF = numberField("usage (decimal)", intStr(a, "usage"));
        form.addView(labeled("Usage number (decimal)", usageF));
        new AlertDialog.Builder(this)
                .setTitle("Custom usage")
                .setSingleChoiceItems(
                        new String[]{"Generic Desktop (0x01)", "Simulation (0x02)"},
                        sel[0], (d, which) -> sel[0] = which)
                .setView(form)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        int u = Integer.parseInt(usageF.getText().toString().trim());
                        a.put("page", pages[sel[0]]);
                        a.put("usage", u);
                    } catch (Exception ex) {
                        toast("Bad usage number");
                        return;
                    }
                    onPicked.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- hat editing ----

    private void editHat() {
        try {
            JSONObject h = working.getJSONObject("hat");
            EditText usageF = numberField("usage (decimal)", intStr(h, "usage"));
            LinearLayout form = new LinearLayout(this);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(24, 16, 24, 0);
            form.addView(labeled("Usage number (decimal, usually 57 = 0x39)", usageF));
            new AlertDialog.Builder(this)
                    .setTitle("Hat switch")
                    .setView(form)
                    .setPositiveButton("OK", (d, w) -> {
                        try {
                            h.put("usage", Integer.parseInt(
                                    usageF.getText().toString().trim()));
                        } catch (Exception ex) {
                            toast("Bad usage number");
                            return;
                        }
                        refreshRows();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (JSONException e) {
            toast("Hat entry broken");
        }
    }

    // ---- save ----

    private void onSave() {
        try {
            String name = nameField.getText().toString().trim();
            if (name.isEmpty()) {
                toast("Profile name can't be empty");
                return;
            }
            working.put("profile_name", name);
            HidProfile p = HidProfile.fromJson(working.toString()); // validates
            String savedId = ProfileStore.saveUserProfile(this, p, profileId);
            boolean savedActive = savedId.equals(ProfileStore.getActiveId(this));
            Intent result = new Intent();
            result.putExtra(EXTRA_SAVED_ACTIVE, savedActive);
            setResult(RESULT_OK, result);
            toast("Saved \"" + p.name + "\"");
            finish();
        } catch (ProfileException pe) {
            new AlertDialog.Builder(this)
                    .setTitle("Can't save")
                    .setMessage(pe.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    // ---- small helpers ----

    private EditText numberField(String hint, String value) {
        EditText f = new EditText(this);
        f.setHint(hint);
        f.setText(value);
        f.setTextColor(0xFFFFFFFF);
        f.setHintTextColor(0xFF888888);
        f.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return f;
    }

    private EditText decimalField(String hint, String value) {
        EditText f = numberField(hint, value);
        f.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return f;
    }

    private LinearLayout labeled(String label, EditText field) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFAAAAAA);
        l.addView(t);
        l.addView(field);
        return l;
    }

    private static String intStr(JSONObject o, String key) {
        try { return String.valueOf(o.getInt(key)); }
        catch (JSONException e) { return ""; }
    }

    private static String dblStr(JSONObject o, String key) {
        try { return String.valueOf(o.getDouble(key)); }
        catch (JSONException e) { return ""; }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
