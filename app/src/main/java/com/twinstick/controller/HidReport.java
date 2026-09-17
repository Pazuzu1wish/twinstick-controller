package com.twinstick.controller;

/**
 * HID report descriptor and report builder for the TwinStick gamepad.
 *
 * Report ID 1, 7 data bytes:
 *   byte 0: X  (left stick,  signed -127..127)
 *   byte 1: Y  (left stick,  signed -127..127, up = negative)
 *   byte 2: Z  (right stick, signed -127..127)
 *   byte 3: Rz (right stick, signed -127..127, up = negative)
 *   byte 4: low nibble = hat switch (0-7 directions, 8 = neutral), high nibble = pad
 *   byte 5: buttons 1-8  (bit0 = button 1)
 *   byte 6: buttons 9-16 (bit0 = button 9)
 *
 * Button map: 1=A(bottom) 2=B(right) 3=X(left) 4=Y(top)
 *             5=L1 6=R1 7=L2 8=R2 9=Select 10=Start 11=L3 12=R3
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
        // --- Hat switch (D-pad), 4 bits + 4 bits padding ---
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
    public static final int REPORT_LEN = 7;

    // Button bit positions (0-based) in the 16-bit button field.
    public static final int BTN_A = 0;
    public static final int BTN_B = 1;
    public static final int BTN_X = 2;
    public static final int BTN_Y = 3;
    public static final int BTN_L1 = 4;
    public static final int BTN_R1 = 5;
    public static final int BTN_L2 = 6;
    public static final int BTN_R2 = 7;
    public static final int BTN_SELECT = 8;
    public static final int BTN_START = 9;
    public static final int BTN_L3 = 10;
    public static final int BTN_R3 = 11;

    public static final int HAT_NEUTRAL = 8;

    private HidReport() {}

    /** Build a 7-byte input report (report ID excluded; passed separately to sendReport). */
    public static byte[] build(int lx, int ly, int rx, int ry, int hat, int buttons) {
        byte[] r = new byte[REPORT_LEN];
        r[0] = (byte) lx;
        r[1] = (byte) ly;
        r[2] = (byte) rx;
        r[3] = (byte) ry;
        r[4] = (byte) (hat & 0x0F);
        r[5] = (byte) (buttons & 0xFF);
        r[6] = (byte) ((buttons >> 8) & 0xFF);
        return r;
    }
}
