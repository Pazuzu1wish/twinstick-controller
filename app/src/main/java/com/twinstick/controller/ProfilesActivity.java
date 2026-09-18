package com.twinstick.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Manage HID profiles: switch the active one, duplicate, edit, import,
 * export, delete. Switching profiles changes the Bluetooth descriptor, so
 * the host must reconnect afterwards.
 */
public class ProfilesActivity extends Activity {

    public static final String EXTRA_PROFILE_CHANGED = "profile_changed";
    private static final int REQ_IMPORT = 10;
    private static final int REQ_EDIT = 11;

    private LinearLayout listLayout;
    private boolean changed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("HID Profiles");
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Switching profiles changes the Bluetooth descriptor — " +
                "the host must reconnect to pick it up. Tap a profile to switch; " +
                "long-press for edit / duplicate / export / delete.");
        hint.setTextColor(0xFFAAAAAA);
        hint.setPadding(0, 8, 0, 16);
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, 16, 0, 0);

        Button importBtn = new Button(this);
        importBtn.setText("Import");
        importBtn.setOnClickListener(v -> onImportClicked());
        buttons.addView(importBtn);

        Button dupBtn = new Button(this);
        dupBtn.setText("Duplicate active");
        dupBtn.setOnClickListener(v -> onDuplicateActive());
        buttons.addView(dupBtn);

        Button resetBtn = new Button(this);
        resetBtn.setText("Reset default");
        resetBtn.setOnClickListener(v -> onResetDefault());
        buttons.addView(resetBtn);

        root.addView(buttons);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void markChanged() {
        changed = true;
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_PROFILE_CHANGED, true));
    }

    @Override
    public void onBackPressed() {
        if (!changed) setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    private void refresh() {
        listLayout.removeAllViews();
        String activeId = ProfileStore.getActiveId(this);
        List<ProfileStore.Entry> entries = ProfileStore.list(this);
        for (ProfileStore.Entry e : entries) {
            listLayout.addView(rowView(e, e.id.equals(activeId)));
        }
        if (entries.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No profiles found.");
            t.setTextColor(0xFFAAAAAA);
            listLayout.addView(t);
        }
    }

    private View rowView(ProfileStore.Entry e, boolean isActive) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 16, 16, 16);
        row.setBackgroundColor(isActive ? 0xFF2A3A5A : 0xFF1E1E28);

        TextView name = new TextView(this);
        name.setText(e.name + (isActive ? "  [ACTIVE]" : ""));
        name.setTextSize(17);
        name.setTextColor(isActive ? 0xFF9AC8FF : 0xFFFFFFFF);
        row.addView(name);

        TextView meta = new TextView(this);
        String detail = "";
        try {
            HidProfile p = ProfileStore.load(this, e.id);
            detail = p.buttons.size() + " buttons, " + p.axes.size() + " axes, " +
                    new HidReport(p).getReportLength() + "-byte report";
        } catch (Exception ex) {
            detail = "unreadable: " + ex.getMessage();
        }
        meta.setText((e.bundled ? "bundled" : "custom") + " - " + detail);
        meta.setTextColor(0xFF999999);
        row.addView(meta);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 12);
        row.setLayoutParams(lp);

        row.setOnClickListener(v -> confirmSwitch(e));
        row.setOnLongClickListener(v -> { showOptions(e); return true; });
        return row;
    }

    private void confirmSwitch(ProfileStore.Entry e) {
        if (e.id.equals(ProfileStore.getActiveId(this))) {
            toast("Already active");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Switch profile?")
                .setMessage("Switch to \"" + e.name + "\"?\n\n" +
                        "The Bluetooth descriptor changes, so the host must " +
                        "reconnect (unpair/re-pair may be needed if the report " +
                        "layout changed).")
                .setPositiveButton("Switch", (d, w) -> {
                    ProfileStore.setActive(this, e.id);
                    markChanged();
                    refresh();
                    toast("Switched to \"" + e.name + "\"");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOptions(ProfileStore.Entry e) {
        String[] items = e.bundled
                ? new String[]{"Edit (makes a copy)", "Duplicate", "Export"}
                : new String[]{"Edit", "Duplicate", "Export", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(e.name)
                .setItems(items, (d, which) -> {
                    String sel = items[which];
                    if (sel.startsWith("Edit")) onEdit(e);
                    else if (sel.equals("Duplicate")) onDuplicate(e);
                    else if (sel.equals("Export")) onExport(e);
                    else if (sel.equals("Delete")) confirmDelete(e);
                })
                .show();
    }

    private void onEdit(ProfileStore.Entry e) {
        try {
            String id = e.id;
            if (e.bundled) {
                // Bundled profiles are factory defaults: edit a copy.
                id = duplicate(e);
                toast("Editing a copy — bundled profiles stay pristine");
            }
            Intent i = new Intent(this, ProfileEditActivity.class);
            i.putExtra(ProfileEditActivity.EXTRA_PROFILE_ID, id);
            startActivityForResult(i, REQ_EDIT);
        } catch (Exception ex) {
            showError("Edit failed", ex.getMessage());
        }
    }

    private String duplicate(ProfileStore.Entry e) throws ProfileException {
        HidProfile p = ProfileStore.load(this, e.id);
        try {
            JSONObject j = new JSONObject(p.toJson());
            j.put("profile_name", p.name + " copy");
            return ProfileStore.importJson(this, j.toString());
        } catch (Exception ex) {
            throw new ProfileException("Duplicate failed: " + ex.getMessage());
        }
    }

    private void onDuplicate(ProfileStore.Entry e) {
        try {
            duplicate(e);
            toast("Duplicated \"" + e.name + "\"");
            refresh();
        } catch (Exception ex) {
            showError("Duplicate failed", ex.getMessage());
        }
    }

    private void onDuplicateActive() {
        String activeId = ProfileStore.getActiveId(this);
        for (ProfileStore.Entry e : ProfileStore.list(this)) {
            if (e.id.equals(activeId)) { onDuplicate(e); return; }
        }
    }

    private void confirmDelete(ProfileStore.Entry e) {
        new AlertDialog.Builder(this)
                .setTitle("Delete profile?")
                .setMessage("Delete \"" + e.name + "\"? This can't be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    try {
                        boolean wasActive = e.id.equals(ProfileStore.getActiveId(this));
                        ProfileStore.deleteUserProfile(this, e.id);
                        if (wasActive) markChanged();
                        refresh();
                        toast("Deleted");
                    } catch (Exception ex) {
                        showError("Delete failed", ex.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void onExport(ProfileStore.Entry e) {
        try {
            HidProfile p = ProfileStore.load(this, e.id);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_SUBJECT, p.name + ".json");
            send.putExtra(Intent.EXTRA_TEXT, p.toJson());
            startActivity(Intent.createChooser(send, "Export profile"));
        } catch (Exception ex) {
            showError("Export failed", ex.getMessage());
        }
    }

    private void onResetDefault() {
        ProfileStore.setActive(this, ProfileStore.DEFAULT_ID);
        markChanged();
        refresh();
        toast("Active profile reset to factory default");
    }

    private void onImportClicked() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                String json = readAll(in);
                String id = ProfileStore.importJson(this, json);
                HidProfile p = ProfileStore.load(this, id);
                refresh();
                toast("Imported \"" + p.name + "\"");
            } catch (ProfileException pe) {
                showError("Import rejected", pe.getMessage());
            } catch (Exception ex) {
                showError("Import failed", ex.getMessage());
            }
        } else if (requestCode == REQ_EDIT && resultCode == RESULT_OK && data != null) {
            if (data.getBooleanExtra(ProfileEditActivity.EXTRA_SAVED_ACTIVE, false)) {
                markChanged();
            }
            refresh();
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void showError(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg != null ? msg : "unknown error")
                .setPositiveButton("OK", null)
                .show();
    }
}
