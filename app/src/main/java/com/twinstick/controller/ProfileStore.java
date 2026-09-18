package com.twinstick.controller;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Loads, saves and tracks HID profiles.
 *
 * Bundled profiles ship in assets/profiles/ (read-only, factory defaults).
 * User profiles live in the app's files/profiles/ as JSON. The active
 * profile id is kept in SharedPreferences; a missing or corrupt active
 * profile falls back to the bundled default.
 *
 * Profile ids: "bundled:<name>" or "user:<name>".
 */
public final class ProfileStore {

    private static final String PREFS = "twinstick_prefs";
    private static final String KEY_ACTIVE = "active_profile";
    public static final String DEFAULT_ID = "bundled:sdl-xpad-legacy";
    private static final String ASSET_DIR = "profiles";
    private static final String USER_DIR = "profiles";

    public static final class Entry {
        public final String id;
        public final String name;
        public final boolean bundled;
        Entry(String id, String name, boolean bundled) {
            this.id = id; this.name = name; this.bundled = bundled;
        }
    }

    private ProfileStore() {}

    public static List<Entry> list(Context ctx) {
        List<Entry> out = new ArrayList<>();
        try {
            String[] files = ctx.getAssets().list(ASSET_DIR);
            if (files != null) {
                Arrays.sort(files);
                for (String f : files) {
                    if (!f.endsWith(".json")) continue;
                    String id = "bundled:" + f.substring(0, f.length() - 5);
                    String name = id;
                    try { name = loadBundled(ctx, f).name; } catch (Exception ignored) {}
                    out.add(new Entry(id, name, true));
                }
            }
        } catch (IOException ignored) {}
        File dir = userDir(ctx);
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                if (!f.getName().endsWith(".json")) continue;
                String id = "user:" + f.getName().substring(0, f.getName().length() - 5);
                String name = id;
                try { name = loadFile(f).name; } catch (Exception ignored) {}
                out.add(new Entry(id, name, false));
            }
        }
        return out;
    }

    public static HidProfile load(Context ctx, String id) throws ProfileException {
        if (id.startsWith("bundled:")) {
            return loadBundled(ctx, id.substring("bundled:".length()) + ".json");
        }
        if (id.startsWith("user:")) {
            return loadFile(new File(userDir(ctx), id.substring("user:".length()) + ".json"));
        }
        throw new ProfileException("Unknown profile id: " + id);
    }

    public static String getActiveId(Context ctx) {
        return prefs(ctx).getString(KEY_ACTIVE, DEFAULT_ID);
    }

    /** Active profile, falling back to the bundled default if missing/corrupt. */
    public static HidProfile getActive(Context ctx) {
        String id = getActiveId(ctx);
        try {
            return load(ctx, id);
        } catch (Exception e) {
            try {
                return load(ctx, DEFAULT_ID);
            } catch (Exception e2) {
                throw new RuntimeException("Bundled default profile missing or corrupt", e2);
            }
        }
    }

    public static void setActive(Context ctx, String id) {
        prefs(ctx).edit().putString(KEY_ACTIVE, id).apply();
    }

    /**
     * Save a user profile. If existingUserId names a user profile, that file
     * is overwritten; otherwise a new file is created (name uniquified).
     * Returns the profile id.
     */
    public static String saveUserProfile(Context ctx, HidProfile p, String existingUserId)
            throws ProfileException {
        File dir = userDir(ctx);
        if (!dir.mkdirs() && !dir.isDirectory())
            throw new ProfileException("Could not create profiles directory");
        File f;
        if (existingUserId != null && existingUserId.startsWith("user:")) {
            f = new File(dir, existingUserId.substring("user:".length()) + ".json");
        } else {
            String base = sanitize(p.name);
            String name = base;
            int n = 2;
            f = new File(dir, name + ".json");
            while (f.exists()) {
                name = base + "-" + (n++);
                f = new File(dir, name + ".json");
            }
        }
        try {
            writeFile(f, p.toJson());
        } catch (IOException e) {
            throw new ProfileException("Could not save profile: " + e.getMessage());
        }
        String base = f.getName();
        return "user:" + base.substring(0, base.length() - 5);
    }

    public static void deleteUserProfile(Context ctx, String id) throws ProfileException {
        if (!id.startsWith("user:"))
            throw new ProfileException("Bundled profiles are factory defaults and can't be deleted");
        File f = new File(userDir(ctx), id.substring("user:".length()) + ".json");
        if (!f.exists()) throw new ProfileException("Profile not found");
        if (!f.delete()) throw new ProfileException("Could not delete profile file");
        if (getActiveId(ctx).equals(id)) setActive(ctx, DEFAULT_ID);
    }

    /** Validate + store an imported JSON document. Returns the new profile id. */
    public static String importJson(Context ctx, String json) throws ProfileException {
        HidProfile p = HidProfile.fromJson(json); // validates
        return saveUserProfile(ctx, p, null);
    }

    // ---- internals ----

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File userDir(Context ctx) {
        return new File(ctx.getFilesDir(), USER_DIR);
    }

    private static HidProfile loadBundled(Context ctx, String file) throws ProfileException {
        try (InputStream in = ctx.getAssets().open(ASSET_DIR + "/" + file)) {
            return HidProfile.fromJson(readAll(in));
        } catch (IOException e) {
            throw new ProfileException("Could not read bundled profile " + file);
        }
    }

    private static HidProfile loadFile(File f) throws ProfileException {
        try (InputStream in = new FileInputStream(f)) {
            return HidProfile.fromJson(readAll(in));
        } catch (IOException e) {
            throw new ProfileException("Could not read profile file " + f.getName());
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeFile(File f, String s) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sanitize(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.isEmpty()) s = "profile";
        if (s.length() > 40) s = s.substring(0, 40);
        return s;
    }
}
