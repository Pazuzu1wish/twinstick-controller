package com.twinstick.controller;

import java.io.ByteArrayOutputStream;

/**
 * Generates a HID report descriptor from a HidProfile.
 *
 * Layout (Report ID 1):
 *   - one 8-bit Input field per axis, in profile order, each with its own
 *     Usage Page / Usage / Logical Min / Logical Max (pages differ between
 *     axes, so the page is emitted before every item)
 *   - hat switch: 4-bit Input (Data/Var/Abs/Null) + 4-bit Constant padding
 *   - N one-bit button Inputs, in profile button order
 *
 * Report bytes mirror declaration order: one byte per axis, then the
 * hat+padding byte, then ceil(N/8) button bytes, LSB-first in profile order.
 */
public final class HidDescriptorBuilder {

    private HidDescriptorBuilder() {}

    public static byte[] build(HidProfile p) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        write(out, 0x05, 0x01);          // Usage Page (Generic Desktop)
        write(out, 0x09, 0x05);          // Usage (Game Pad)
        write(out, 0xA1, 0x01);          // Collection (Application)
        write(out, 0x85, 0x01);          // Report ID (1)

        // --- axes, one 8-bit field each, in profile order ---
        for (HidProfile.AxisEntry a : p.axes) {
            writeUsagePage(out, a.page);
            writeUsage(out, a.usage);
            writeLogicalMinMax(out, a.min, a.max);
            write(out, 0x75, 0x08);      // Report Size (8)
            write(out, 0x95, 0x01);      // Report Count (1)
            write(out, 0x81, 0x02);      // Input (Data, Variable, Absolute)
        }

        // --- hat switch: 4 bits + 4 bits padding ---
        writeUsagePage(out, p.hat.page);
        writeUsage(out, p.hat.usage);
        write(out, 0x15, 0x00);          // Logical Minimum (0)
        write(out, 0x25, 0x07);          // Logical Maximum (7)
        write(out, 0x35, 0x00);          // Physical Minimum (0)
        write(out, 0x46, 0x3B, 0x01);    // Physical Maximum (315)
        write(out, 0x65, 0x14);          // Unit (Degrees)
        write(out, 0x75, 0x04);          // Report Size (4)
        write(out, 0x95, 0x01);          // Report Count (1)
        write(out, 0x81, 0x42);          // Input (Data, Var, Abs, Null State)
        write(out, 0x75, 0x04);          // Report Size (4)
        write(out, 0x95, 0x01);          // Report Count (1)
        write(out, 0x81, 0x01);          // Input (Constant) -- padding

        // --- buttons: one 1-bit field each, in profile order ---
        writeUsagePage(out, HidProfile.PAGE_BUTTON);
        for (HidProfile.ButtonEntry b : p.buttons) {
            writeUsage(out, b.usage);
        }
        write(out, 0x15, 0x00);          // Logical Minimum (0)
        write(out, 0x25, 0x01);          // Logical Maximum (1)
        write(out, 0x75, 0x01);          // Report Size (1)
        write(out, 0x95, p.buttons.size()); // Report Count (N)
        write(out, 0x81, 0x02);          // Input (Data, Variable, Absolute)

        write(out, 0xC0);                // End Collection
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, int... bytes) {
        for (int b : bytes) out.write(b);
    }

    private static void writeUsagePage(ByteArrayOutputStream out, int page) {
        write(out, 0x05, page & 0xFF);
    }

    private static void writeUsage(ByteArrayOutputStream out, int usage) {
        if (usage <= 0xFF) {
            write(out, 0x09, usage);
        } else {
            write(out, 0x0A, usage & 0xFF, (usage >> 8) & 0xFF);
        }
    }

    private static void writeLogicalMinMax(ByteArrayOutputStream out, int min, int max) {
        if (min >= -128 && max <= 127) {
            write(out, 0x15, min & 0xFF);   // Logical Minimum, 1 byte
            write(out, 0x25, max & 0xFF);   // Logical Maximum, 1 byte
        } else {
            write(out, 0x16, min & 0xFF, (min >> 8) & 0xFF); // 2-byte min
            write(out, 0x26, max & 0xFF, (max >> 8) & 0xFF); // 2-byte max
        }
    }
}
