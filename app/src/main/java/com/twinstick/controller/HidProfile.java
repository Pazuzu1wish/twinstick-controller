package com.twinstick.controller;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A mappable HID profile: which HID usages each physical control reports.
 *
 * The physical control set is fixed (it mirrors the touchscreen UI):
 *   buttons: A B X Y L1 R1 L2 R2 SELECT START L3 R3
 *   axes:    left_x left_y right_x right_y l2 r2
 *   hat:     the D-pad
 * A profile only remaps usages and axis parameters — it never adds or
 * removes controls, so ControllerView's wiring always holds.
 *
 * JSON format (arrays preserve report order):
 * {
 *   "profile_name": "SDL xpad-legacy",
 *   "vendor_id": "0x045E", "product_id": "0x02EA",   // informational only:
 *        Android's BluetoothHidDevice API does not let apps set VID/PID,
 *        so these are stored/exported but not sent to the host.
 *   "buttons": [ {"id":"A", "page":"button", "usage":1}, ... ],
 *   "axes":    [ {"id":"left_x", "page":"generic_desktop", "usage":48,
 *                 "min":-127, "max":127, "invert":false, "deadzone":0.08}, ... ],
 *   "hat": {"page":"generic_desktop", "usage":57}
 * }
 * Page names: generic_desktop (0x01), simulation (0x02), button (0x09).
 * Usage numbers are decimal.
 */
public final class HidProfile {

    public static final String[] BUTTON_IDS =
            {"A", "B", "X", "Y", "L1", "R1", "L2", "R2", "SELECT", "START", "L3", "R3"};
    public static final String[] AXIS_IDS =
            {"left_x", "left_y", "right_x", "right_y", "l2", "r2"};

    public static final int PAGE_GENERIC_DESKTOP = 0x01;
    public static final int PAGE_SIMULATION = 0x02;
    public static final int PAGE_BUTTON = 0x09;

    public static final class ButtonEntry {
        public final String id;
        public final int usage; // 1..32, Button page
        ButtonEntry(String id, int usage) { this.id = id; this.usage = usage; }
    }

    public static final class AxisEntry {
        public final String id;
        public final int page;   // 0x01 / 0x02
        public final int usage;  // 0..255
        public final int min;
        public final int max;
        public final boolean invert;
        public final double deadzone; // fraction of half-range, 0..0.9
        AxisEntry(String id, int page, int usage, int min, int max,
                  boolean invert, double deadzone) {
            this.id = id; this.page = page; this.usage = usage;
            this.min = min; this.max = max;
            this.invert = invert; this.deadzone = deadzone;
        }
    }

    public static final class HatEntry {
        public final int page;
        public final int usage;
        HatEntry(int page, int usage) { this.page = page; this.usage = usage; }
    }

    public final String name;
    public final String vendorId;
    public final String productId;
    public final List<ButtonEntry> buttons;
    public final List<AxisEntry> axes;
    public final HatEntry hat;

    private HidProfile(String name, String vendorId, String productId,
                       List<ButtonEntry> buttons, List<AxisEntry> axes, HatEntry hat) {
        this.name = name; this.vendorId = vendorId; this.productId = productId;
        this.buttons = buttons; this.axes = axes; this.hat = hat;
    }

    public static int pageFromName(String n) throws ProfileException {
        if ("generic_desktop".equals(n)) return PAGE_GENERIC_DESKTOP;
        if ("simulation".equals(n)) return PAGE_SIMULATION;
        if ("button".equals(n)) return PAGE_BUTTON;
        throw new ProfileException(
                "Unknown usage page \"" + n + "\" — want generic_desktop, simulation or button");
    }

    public static String pageToName(int page) {
        switch (page) {
            case PAGE_GENERIC_DESKTOP: return "generic_desktop";
            case PAGE_SIMULATION: return "simulation";
            case PAGE_BUTTON: return "button";
            default: return "0x" + Integer.toHexString(page);
        }
    }

    private static void checkHexId(String field, String v) throws ProfileException {
        if (!v.matches("0[xX][0-9a-fA-F]{1,4}"))
            throw new ProfileException(field + " \"" + v + "\" is not hex like 0x045E");
    }

    private static void checkIdSet(String what, Set<String> got, String[] want)
            throws ProfileException {
        Set<String> wantSet = new HashSet<>();
        for (String s : want) wantSet.add(s);
        for (String id : got) {
            if (!wantSet.contains(id))
                throw new ProfileException(
                        "Unknown " + what + " id \"" + id + "\" — the physical controls are fixed");
        }
        for (String id : want) {
            if (!got.contains(id))
                throw new ProfileException("Missing " + what + " entry for \"" + id + "\"");
        }
    }

    /** Parse + validate. Throws ProfileException with a human-readable reason. */
    public static HidProfile fromJson(String json) throws ProfileException {
        final JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            throw new ProfileException("Not valid JSON: " + e.getMessage());
        }
        try {
            String name = o.getString("profile_name").trim();
            if (name.isEmpty()) throw new ProfileException("profile_name is empty");
            String vid = o.optString("vendor_id", "").trim();
            String pid = o.optString("product_id", "").trim();
            if (!vid.isEmpty()) checkHexId("vendor_id", vid);
            if (!pid.isEmpty()) checkHexId("product_id", pid);

            JSONArray jb = o.getJSONArray("buttons");
            if (jb.length() == 0) throw new ProfileException("buttons list is empty");
            List<ButtonEntry> buttons = new ArrayList<>();
            Set<String> btnIds = new HashSet<>();
            Set<Integer> btnUsages = new HashSet<>();
            for (int i = 0; i < jb.length(); i++) {
                JSONObject b = jb.getJSONObject(i);
                String id = b.getString("id");
                int page = pageFromName(b.getString("page"));
                if (page != PAGE_BUTTON)
                    throw new ProfileException(
                            "Button \"" + id + "\": page must be \"button\"");
                int usage = b.getInt("usage");
                if (usage < 1 || usage > 32)
                    throw new ProfileException(
                            "Button \"" + id + "\": usage " + usage + " out of range 1..32");
                if (!btnIds.add(id))
                    throw new ProfileException("Duplicate button id \"" + id + "\"");
                if (!btnUsages.add(usage))
                    throw new ProfileException(
                            "Two buttons share usage " + usage + " — each button needs its own usage");
                buttons.add(new ButtonEntry(id, usage));
            }
            checkIdSet("button", btnIds, BUTTON_IDS);

            JSONArray ja = o.getJSONArray("axes");
            if (ja.length() == 0) throw new ProfileException("axes list is empty");
            List<AxisEntry> axes = new ArrayList<>();
            Set<String> axisIds = new HashSet<>();
            Set<String> axisUsages = new HashSet<>();
            for (int i = 0; i < ja.length(); i++) {
                JSONObject a = ja.getJSONObject(i);
                String id = a.getString("id");
                int page = pageFromName(a.getString("page"));
                if (page == PAGE_BUTTON)
                    throw new ProfileException(
                            "Axis \"" + id + "\": page must be generic_desktop or simulation");
                int usage = a.getInt("usage");
                if (usage < 0 || usage > 255)
                    throw new ProfileException(
                            "Axis \"" + id + "\": usage " + usage + " out of range 0..255");
                int min = a.getInt("min");
                int max = a.getInt("max");
                if (min >= max)
                    throw new ProfileException("Axis \"" + id + "\": min (" + min +
                            ") must be smaller than max (" + max + ")");
                if (min < -32768 || max > 32767)
                    throw new ProfileException(
                            "Axis \"" + id + "\": range must fit in 16 bits");
                boolean invert = a.optBoolean("invert", false);
                double dz = a.optDouble("deadzone", 0.0);
                if (dz < 0 || dz > 0.9)
                    throw new ProfileException(
                            "Axis \"" + id + "\": deadzone must be between 0 and 0.9");
                if (!axisIds.add(id))
                    throw new ProfileException("Duplicate axis id \"" + id + "\"");
                String key = page + ":" + usage;
                if (!axisUsages.add(key))
                    throw new ProfileException("Two axes share page/usage " + key +
                            " — each axis needs its own usage");
                axes.add(new AxisEntry(id, page, usage, min, max, invert, dz));
            }
            checkIdSet("axis", axisIds, AXIS_IDS);

            JSONObject h = o.getJSONObject("hat");
            int hpage = pageFromName(h.getString("page"));
            int husage = h.getInt("usage");
            if (husage < 1 || husage > 255)
                throw new ProfileException("hat: usage " + husage + " out of range 1..255");

            return new HidProfile(name, vid, pid, buttons, axes, new HatEntry(hpage, husage));
        } catch (JSONException e) {
            throw new ProfileException("Missing or wrong-typed field: " + e.getMessage());
        }
    }

    /** Serialize back to the same format fromJson accepts. */
    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("profile_name", name);
            o.put("vendor_id", vendorId);
            o.put("product_id", productId);
            JSONArray jb = new JSONArray();
            for (ButtonEntry b : buttons) {
                JSONObject e = new JSONObject();
                e.put("id", b.id);
                e.put("page", pageToName(PAGE_BUTTON));
                e.put("usage", b.usage);
                jb.put(e);
            }
            o.put("buttons", jb);
            JSONArray ja = new JSONArray();
            for (AxisEntry a : axes) {
                JSONObject e = new JSONObject();
                e.put("id", a.id);
                e.put("page", pageToName(a.page));
                e.put("usage", a.usage);
                e.put("min", a.min);
                e.put("max", a.max);
                e.put("invert", a.invert);
                e.put("deadzone", a.deadzone);
                ja.put(e);
            }
            o.put("axes", ja);
            JSONObject h = new JSONObject();
            h.put("page", pageToName(hat.page));
            h.put("usage", hat.usage);
            o.put("hat", h);
            return o.toString(2);
        } catch (JSONException e) {
            throw new RuntimeException(e); // cannot happen: we built it
        }
    }

    /** Copy with a different name (used by Duplicate). */
    public HidProfile withName(String newName) {
        return new HidProfile(newName, vendorId, productId, buttons, axes, hat);
    }
}
