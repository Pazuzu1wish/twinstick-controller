package com.twinstick.controller;

/**
 * HID report descriptor and report builder for the TwinStick gamepad.
 *
 * Xbox-360-style layout, Report ID 1, 9 data bytes:
 *   byte 0: X  (left stick,  signed -127..127)
 *   byte 1: Y  (left stick,  signed -127..127, up = negative)
 *   byte 2: Z  (left trigger L2, unsigned 0..255, rest 0)
 *   byte 3: Rx (right stick, signed -127..127)
 *   byte 4: Ry (right stick, signed -127..127, up = negative)
 *   byte 5: Rz (right trigger R2, unsigned 0..255, rest 0)
 *   byte 6: low nibble = hat switch (0-7 directions, 8 = neutral), high nibble = pad
 *   byte 7: buttons 1-8  (bit0 = button 1)
 *   byte 8: buttons 9-10 (bit0 = button 9), upper 6 bits constant pad
 *
 * Button map: 1=A(bottom) 2=B(right) 3=X(left) 4=Y(top)
 *             5=LB(L1) 6=RB(R1) 7=Select 8=Start 9=L3 10=R3
 * L2/R2 are analog trigger axes (touchscreen: full 0 or 255), not buttons.
 */
public final class HidReport {

    public static final byte[] DESCRIPTOR = new byte[] {
        0x05, 0x01,                 // Usage Page (Generic Desktop)
        0x09, 0x05,                 // Usage (Game Pad)
        (byte) 0xA1, 0x01,          // Collection (Application)
        (byte) 0x85, 0x01,          //   Report ID (1)
        // --- Left stick X, Y: signed 8-bit ---
        0x09, 0x01,                 //   Usage (Pointer)
        (byte) 0xA1, 0x00,          //   Collection (Physical)
        0x09, 0x30,                 //     Usage (X)
        0x09, 0x31,                 //     Usage (Y)
        0x15, (byte) 0x81,          //     Logical Minimum (-127)
        0x25, 0x7F,                 //     Logical Maximum (127)
        0x75, 0x08,                 //     Report Size (8)
        (byte) 0x95, 0x02,          //     Report Count (2)
        (byte) 0x81, 0x02,          //     Input (Data, Variable, Absolute)
        (byte) 0xC0,                //   End Collection
        // --- Left trigger L2 on Z: unsigned 8-bit ---
        0x09, 0x32,                 //   Usage (Z)
        0x15, 0x00,                 //   Logical Minimum (0)
        0x26, (byte) 0xFF, 0x00,    //   Logical Maximum (255)
        0x75, 0x08,                 //   Report Size (8)
        (byte) 0x95, 0x01,          //   Report Count (1)
        (byte) 0x81, 0x02,          //   Input (Data, Variable, Absolute)
        // --- Right stick Rx, Ry: signed 8-bit ---
        0x09, 0x01,                 //   Usage (Pointer)
        (byte) 0xA1, 0x00,          //   Collection (Physical)
        0x09, 0x33,                 //     Usage (Rx)
        0x09, 0x34,                 //     Usage (Ry)
        0x15, (byte) 0x81,          //     Logical Minimum (-127)
        0x25, 0x7F,                 //     Logical Maximum (127)
        0x75, 0x08,                 //     Report Size (8)
        (byte) 0x95, 0x02,          //     Report Count (2)
        (byte) 0x81, 0x02,          //     Input (Data, Variable, Absolute)
        (byte) 0xC0,                //   End Collection
        // --- Right trigger R2 on Rz: unsigned 8-bit ---
        0x09, 0x35,                 //   Usage (Rz)
        0x15, 0x00,                 //   Logical Minimum (0)
        0x26, (byte) 0xFF, 0x00,    //   Logical Maximum (255)
        0x75, 0x08,                 //     Report Size (8)
        (byte) 0x95, 0x01,          //     Report Count (1)
        (byte) 0x81, 0x02,          //     Input (Data, Variable, Absolute)
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
        // --- 10 buttons, 1 bit each + 6 bits padding ---
        0x05, 0x09,                 //   Usage Page (Button)
        0x19, 0x01,                 //   Usage Minimum (1)
        0x29, 0x0A,                 //   Usage Maximum (10)
        0x15, 0x00,                 //   Logical Minimum (0)
        0x25, 0x01,                 //   Logical Maximum (1)
        0x75, 0x01,                 //   Report Size (1)
        (byte) 0x95, 0x0A,          //   Report Count (10)
        (byte) 0x81, 0x02,          //   Input (Data, Variable, Absolute)
        0x75, 0x06,                 //   Report Size (6)
        (byte) 0x95, 0x01,          //   Report Count (1)
        (byte) 0x81, 0x01,          //   Input (Constant) -- padding
        (byte) 0xC0                 // End Collection
    };

    public static final int REPORT_ID = 1;
    public static final int REPORT_LEN = 9;

    // Button bit positions (0-based) in the 10-bit button field.
    public static final int BTN_A = 0;
    public static final int BTN_B = 1;
    public static final int BTN_X = 2;
    public static final int BTN_Y = 3;
    public static final int BTN_L1 = 4;
    public static final int BTN_R1 = 5;
    public static final int BTN_SELECT = 6;
    public static final int BTN_START = 7;
    public static final int BTN_L3 = 8;
    public static final int BTN_R3 = 9;

    public static final int HAT_NEUTRAL = 8;

    private HidReport() {}

    /**
     * Build a 9-byte input report (report ID excluded; passed separately to sendReport).
     * lx/ly/rx/ry: -127..127 signed stick axes; lt/rt: 0..255 unsigned trigger axes.
     */
    public static byte[] build(int lx, int ly, int lt, int rx, int ry, int rt, int hat, int buttons) {
        byte[] r = new byte[REPORT_LEN];
        r[0] = (byte) lx;
        r[1] = (byte) ly;
        r[2] = (byte) (lt & 0xFF);
        r[3] = (byte) rx;
        r[4] = (byte) ry;
        r[5] = (byte) (rt & 0xFF);
        r[6] = (byte) (hat & 0x0F);
        r[7] = (byte) (buttons & 0xFF);
        r[8] = (byte) ((buttons >> 8) & 0xFF);
        return r;
    }
}
