package com.twinstick.controller;

/**
 * Packs HID input reports for one HidProfile.
 *
 * Report layout mirrors the generated descriptor: one byte per axis (in
 * profile axis order), then the hat+padding byte, then ceil(N/8) button
 * bytes with button bit i = profile button index i (LSB-first).
 *
 * Axis conditioning per profile entry, in order: deadzone -> invert -> clamp.
 * The deadzone is a fraction of the half-range; values inside it snap to the
 * resting value (0 for symmetric ranges like -127..127, min for 0..max
 * ranges like triggers).
 *
 * There are no hardcoded control positions here: callers map their physical
 * controls to profile ids via buttonBit()/axisIndex().
 */
public final class HidReport {

    public static final int REPORT_ID = 1;
    public static final int HAT_NEUTRAL = 8;

    private final HidProfile profile;
    private final byte[] descriptor;
    private final int reportLen;

    public HidReport(HidProfile profile) {
        this.profile = profile;
        this.descriptor = HidDescriptorBuilder.build(profile);
        this.reportLen = profile.axes.size() + 1
                + (profile.buttons.size() + 7) / 8;
    }

    public HidProfile getProfile() { return profile; }

    /** A fresh copy of the generated descriptor bytes (for the SDP record). */
    public byte[] getDescriptor() { return descriptor.clone(); }

    public int getReportLength() { return reportLen; }
    public int getAxisCount() { return profile.axes.size(); }
    public int getButtonCount() { return profile.buttons.size(); }

    /** Bit position of a button id in the button mask, or -1 if absent. */
    public int buttonBit(String id) {
        for (int i = 0; i < profile.buttons.size(); i++) {
            if (profile.buttons.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    /** Byte offset of an axis id within the report, or -1 if absent. */
    public int axisIndex(String id) {
        for (int i = 0; i < profile.axes.size(); i++) {
            if (profile.axes.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    public int axisMin(int idx) { return profile.axes.get(idx).min; }
    public int axisMax(int idx) { return profile.axes.get(idx).max; }

    /**
     * Build one input report. rawAxes are unconditioned values in profile
     * axis order (each in its axis's [min,max] units); the profile's
     * deadzone/invert/clamp are applied here. buttonMask bit i corresponds
     * to profile button index i.
     */
    public byte[] build(int[] rawAxes, int hat, int buttonMask) {
        byte[] r = new byte[reportLen];
        int n = profile.axes.size();
        for (int i = 0; i < n; i++) {
            int v = (rawAxes != null && i < rawAxes.length) ? rawAxes[i] : 0;
            r[i] = (byte) condition(profile.axes.get(i), v);
        }
        r[n] = (byte) (hat & 0x0F);
        int btnBytes = (profile.buttons.size() + 7) / 8;
        for (int k = 0; k < btnBytes; k++) {
            r[n + 1 + k] = (byte) ((buttonMask >> (8 * k)) & 0xFF);
        }
        return r;
    }

    /** Deadzone -> invert -> clamp. Package-visible for tests. */
    static int condition(HidProfile.AxisEntry a, int v) {
        int rest = a.min < 0 ? 0 : a.min;
        double half = (a.max - a.min) / 2.0;
        if (Math.abs(v - rest) <= a.deadzone * half) v = rest;
        if (a.invert) v = a.min + a.max - v;
        if (v < a.min) v = a.min;
        if (v > a.max) v = a.max;
        return v;
    }
}
