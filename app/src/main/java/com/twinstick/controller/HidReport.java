package com.twinstick.controller;

/**
 * HID report descriptor and report builder for the TwinStick gamepad.
 *
 * Report ID 1, 9 data bytes:
 *   byte 0: X  (left stick,  signed -127..127)
 *   byte 1: Y  (left stick,  signed -127..127, up = negative)
 *   byte 2: Z  (right stick, signed -127..127)
 *   byte 3: Rz (right stick, signed -127..127, up = negative)
 *   byte 4: L2 trigger (Simulation page "Brake", 0..255 -> ABS_BRAKE)
 *   byte 5: R2 trigger (Simulation page "Accelerator", 0..255 -> ABS_GAS)
 *   byte 6: low nibble = hat switch (0-7 directions, 8 = neutral), high nibble = pad
 *   byte 7: buttons 1-8  (bit0 = button 1)
 *   byte 8: buttons 9-16 (bit0 = button 9)
 *
 * Trigger design notes:
 * - L2/R2 are reported BOTH as button bits (8/9 -> BTN_TL2/BTN_TR2) and as
 *   analog axes (Brake/Accelerator -> ABS_BRAKE/ABS_GAS). Dual digital+analog
 *   trigger reporting is explicitly allowed by the Linux Gamepad Specification:
 *   "Trigger buttons can be available as digital or analog buttons or both."
 * - Simulation-page usages were chosen because the kernel maps them to
 *   ABS_BRAKE/ABS_GAS, and SDL3's Linux gamepad heuristic maps LEFTTRIGGER /
 *   RIGHTTRIGGER from those codes (Android convention), leaving our right
 *   stick alone on ABS_Z/ABS_RZ.
 *
 * X/Y deliberately use xpad-legacy codes, not spec-positional ones: the kernel
 * gamepad spec wants face buttons by physical position, but games read SDL,
 * not the kernel. SDL's Linux heuristic was built around xpad's legacy
 * mapping (west "X" -> 0x133, north "Y" -> 0x134), so emitting legacy codes
 * is what lands in the right SDL slots. Our X is physical west, Y north.
 *
 * Button map (bit -> button -> Linux evdev code).
 * Bit positions are chosen so the kernel names them like a modern gamepad:
 * HID Button-page usage N (bit N-1) maps to evdev code 303+N.
 *   0=A(bottom) -> BTN_SOUTH, 1=B(right) -> BTN_EAST,
 *   3=X(left) -> BTN_X (0x133), 4=Y(top) -> BTN_Y (0x134),
 *   6=L1 -> BTN_TL, 7=R1 -> BTN_TR, 8=L2 -> BTN_TL2, 9=R2 -> BTN_TR2,
 *   10=Select -> BTN_SELECT, 11=Start -> BTN_START,
 *   13=L3 -> BTN_THUMBL, 14=R3 -> BTN_THUMBR
 * Bits 2, 5, 12, 15 (usages 3, 6, 13, 16) are unused and stay 0.
 * The descriptor still declares 16 one-bit buttons; only the mapping changed.
 */
public final class HidReport {

    public static final byte[] DESCRIPTOR = new byte[] {
        0x05, 0x01,                 // Usage Page (Generic Desktop)
        0x09, 0x05,                 // Usage (Game Pad)
        (byte) 0xA1, 0x01,          // Collection (Application)
        (byte) 0x85, 0x01,          //   Report ID (1)
        // --- 4 axes: X, Y (left stick), Z, Rz (right stick) ---
        0x09, 0x01,                 //   Usage (Pointer)
        (byte) 0xA1, 0x00,          //   Collection (Physical)
        0x09, 0x30,                 //     Usage (X)
        0x09, 0x31,                 //     Usage (Y)
        0x09, 0x32,                 //     Usage (Z)
        0x09, 0x35,                 //     Usage (Rz)
        0x15, (byte) 0x81,          //     Logical Minimum (-127)
        0x25, 0x7F,                 //     Logical Maximum (127)
        0x75, 0x08,                 //     Report Size (8)
        (byte) 0x95, 0x04,          //     Report Count (4)
        (byte) 0x81, 0x02,          //     Input (Data, Variable, Absolute)
        (byte) 0xC0,                //   End Collection
        // --- 2 analog trigger axes: L2 = Brake, R2 = Accelerator ---
        0x05, 0x02,                 //   Usage Page (Simulation)
        0x09, (byte) 0xC5,          //   Usage (Brake) -> ABS_BRAKE (L2)
        0x09, (byte) 0xC4,          //   Usage (Accelerator) -> ABS_GAS (R2)
        0x15, 0x00,                 //   Logical Minimum (0)
        0x26, (byte) 0xFF, 0x00,    //   Logical Maximum (255)
        0x75, 0x08,                 //   Report Size (8)
        (byte) 0x95, 0x02,          //   Report Count (2)
        (byte) 0x81, 0x02,          //   Input (Data, Variable, Absolute)
        // --- Hat switch (D-pad), 4 bits + 4 bits padding ---
        0x05, 0x01,                 //   Usage Page (Generic Desktop) -- reset
        0x09, 0x39,                 //   Usage (Hat Switch)
        0x15, 0x00,                 //   Logical Minimum (0)
        0x25, 0x07,                 //   Logical Maximum (7)
        0x35, 0x00,                 //   Physical Minimum (0)
        0x46, 0x3B, 0x01,           //   Physical Maximum (315)
        0x65, 0x14,                 //   Unit (Degrees)
        0x75, 0x04,                 //   Report Size (4)
        (byte) 0x95, 0x01,          //   Report Count (1)
        (byte) 0x81, 0x42,          //   Input (Data, Variable, Absolute, Null State)
        0x75, 0x04,                 //   Report Size (4)
        (byte) 0x95, 0x01,          //   Report Count (1)
        (byte) 0x81, 0x01,          //   Input (Constant) -- padding
        // --- 16 buttons, 1 bit each ---
        0x05, 0x09,                 //   Usage Page (Button)
        0x19, 0x01,                 //   Usage Minimum (1)
        0x29, 0x10,                 //   Usage Maximum (16)
        0x15, 0x00,                 //   Logical Minimum (0)
        0x25, 0x01,                 //   Logical Maximum (1)
        0x75, 0x01,                 //   Report Size (1)
        (byte) 0x95, 0x10,          //   Report Count (16)
        (byte) 0x81, 0x02,          //   Input (Data, Variable, Absolute)
        (byte) 0xC0                 // End Collection
    };

    public static final int REPORT_ID = 1;
    public static final int REPORT_LEN = 9;

    // Button bit positions (0-based) in the 16-bit button field.
    // Chosen so the Linux kernel maps them to modern gamepad BTN_ codes:
    // HID Button-page usage N (bit N-1) -> evdev code 303+N.
    public static final int BTN_A = 0;       // usage 1  -> BTN_SOUTH
    public static final int BTN_B = 1;       // usage 2  -> BTN_EAST
    public static final int BTN_X = 3;       // usage 4  -> BTN_X (0x133); physical west button, legacy xpad code for SDL
    public static final int BTN_Y = 4;       // usage 5  -> BTN_Y (0x134); physical north button, legacy xpad code for SDL
    public static final int BTN_L1 = 6;      // usage 7  -> BTN_TL
    public static final int BTN_R1 = 7;      // usage 8  -> BTN_TR
    public static final int BTN_L2 = 8;      // usage 9  -> BTN_TL2
    public static final int BTN_R2 = 9;      // usage 10 -> BTN_TR2
    public static final int BTN_SELECT = 10; // usage 11 -> BTN_SELECT
    public static final int BTN_START = 11;  // usage 12 -> BTN_START
    public static final int BTN_L3 = 13;     // usage 14 -> BTN_THUMBL
    public static final int BTN_R3 = 14;     // usage 15 -> BTN_THUMBR
    // bits 2, 5, 12, 15 unused -> usages 3, 6, 13, 16 are never set

    public static final int HAT_NEUTRAL = 8;

    private HidReport() {}

    /** Build a 9-byte input report (report ID excluded; passed separately to sendReport). */
    public static byte[] build(int lx, int ly, int rx, int ry,
                               int trigL2, int trigR2, int hat, int buttons) {
        byte[] r = new byte[REPORT_LEN];
        r[0] = (byte) lx;
        r[1] = (byte) ly;
        r[2] = (byte) rx;
        r[3] = (byte) ry;
        r[4] = (byte) (trigL2 & 0xFF);
        r[5] = (byte) (trigR2 & 0xFF);
        r[6] = (byte) (hat & 0x0F);
        r[7] = (byte) (buttons & 0xFF);
        r[8] = (byte) ((buttons >> 8) & 0xFF);
        return r;
    }
}
