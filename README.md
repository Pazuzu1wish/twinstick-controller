# TwinStick Controller

Turn your Android phone into a Bluetooth gamepad. No PC-side software, no drivers —
the phone speaks standard Bluetooth HID, so the host sees a plain old joystick.
Built for game devs who don't own a controller (hi).

## What it is

An Android app (Bluetooth HID **device** role) that presents a twin-stick
controller UI and reports as a USB-style HID gamepad over Bluetooth. Linux,
Windows, and macOS all pick it up as a regular joystick — SDL sees it with
zero extra code.

**Controls**

- Left stick (X/Y) and right stick (Z/Rz)
- D-pad (hat switch)
- Face buttons: A B X Y (bottom/right/left/top diamond)
- L1 / R1 shoulder buttons (digital)
- L2 / R2 triggers: analog axes (0–255) **and** digital buttons
- Start / Select, L3 / R3 stick clicks

## HID profiles (mappable schema)

The button/axis mapping is no longer baked into the app — it lives in JSON
**profiles** that the app loads at runtime, so mapping tweaks never need a
rebuild again. Tap **Profiles** on the controller screen to switch, edit,
duplicate, import, or export them.

**Bundled profiles**

- **SDL xpad-legacy** (default): the mapping every build up to v7 used —
  X (west) → BTN_X/0x133, Y (north) → BTN_Y/0x134, triggers on ABS_BRAKE/ABS_GAS.
- **Linux spec positional**: identical, except X → usage 5 (BTN_Y/0x134) and
  Y → usage 4 (BTN_X/0x133), i.e. buttons strictly by physical position per
  the kernel's gamepad spec. (SDL's heuristic expects the xpad-legacy codes,
  so this one will look X/Y-swapped in SDL consumers — it's here for
  experimenting, not for playing.)

**Profile format**

```json
{
  "profile_name": "SDL xpad-legacy",
  "vendor_id": "0x045E",
  "product_id": "0x02EA",
  "buttons": [
    {"id": "A", "page": "button", "usage": 1},
    {"id": "X", "page": "button", "usage": 4}
  ],
  "axes": [
    {"id": "left_x", "page": "generic_desktop", "usage": 48,
     "min": -127, "max": 127, "invert": false, "deadzone": 0.08},
    {"id": "l2", "page": "simulation", "usage": 197,
     "min": 0, "max": 255, "invert": false, "deadzone": 0.0}
  ],
  "hat": {"page": "generic_desktop", "usage": 57}
}
```

- The physical control set is fixed (12 buttons, 6 axes, hat — it mirrors the
  touchscreen UI); a profile only remaps usages and axis parameters.
- Page names: `generic_desktop` (0x01), `simulation` (0x02), `button` (0x09).
  Usage numbers are decimal (48 = 0x30 X, 197 = 0xC5 Brake, …).
- The HID descriptor is generated from the profile at runtime: one 8-bit
  field per axis in order, then the hat byte, then the button bytes LSB-first
  in profile order. The report layout always mirrors the declaration order.
- Axis conditioning: deadzone (fraction of half-range, snaps to the resting
  value) → invert → clamp to [min, max].
- `vendor_id`/`product_id` are stored and exported but informational only —
  Android's `BluetoothHidDevice` API doesn't let apps set VID/PID.
- Imports are validated (unknown ids, duplicate usages, bad ranges, …) and
  rejected with a plain-English reason.

**Switching profiles:** the Bluetooth SDP record carries the descriptor, so
switching re-registers the HID app and the **host must reconnect** to pick up
the new descriptor (unpair/re-pair if the report layout changed). The app
tells you when this is needed.

## HID layout (default profile)

Report ID 1, 9 data bytes: `[LX, LY, RX, RY, L2, R2, hat+pad, btn_lo, btn_hi]`.

| SDL axis | Linux evdev | Control |
|---|---|---|
| 0 | ABS_X (−127..127) | Left stick X |
| 1 | ABS_Y (−127..127) | Left stick Y |
| 2 | ABS_Z (−127..127) | Right stick X |
| 3 | ABS_RZ (−127..127) | Right stick Y |
| — | ABS_BRAKE (0..255) | L2 trigger (analog) |
| — | ABS_GAS (0..255) | R2 trigger (analog) |

SDL3's Linux gamepad heuristic maps LEFTTRIGGER ← ABS_BRAKE and RIGHTTRIGGER ←
ABS_GAS, which is exactly how our triggers are reported (HID Simulation-page
usages 0xC5 "Brake" and 0xC4 "Accelerator", logical 0..255). The right stick
stays put on ABS_Z/ABS_RZ — the earlier v1.1 experiment moved it to SDL axes
3/4 and that broke in-game right-stick look, so don't do that again.

L2/R2 are dual-reported: analog axes *and* digital button bits (8/9 →
BTN_TL2/BTN_TR2). The Linux Gamepad Specification allows both:
"Trigger buttons can be available as digital or analog buttons or both."

A note on X/Y: the Linux Gamepad Specification wants action buttons reported by
physical position, but games don't read the kernel — they read SDL. SDL's
Linux gamepad heuristic and its mapping database were built around the xpad
driver's legacy codes (Xbox 360's west "X" → BTN_X/0x133, north "Y" →
BTN_Y/0x134), so SDL maps those codes into the west/north slots. We emit the
xpad-legacy codes so X and Y land in the right SDL slots. X is the physical
west button → BTN_X (0x133), Y the physical north button → BTN_Y (0x134).

| Button | HID bit | Linux evdev |
|---|---|---|
| A (bottom) | 0 | BTN_SOUTH |
| B (right) | 1 | BTN_EAST |
| X (left) | 3 | BTN_X (0x133) |
| Y (top) | 4 | BTN_Y (0x134) |
| L1 | 6 | BTN_TL |
| R1 | 7 | BTN_TR |
| L2 | 8 | BTN_TL2 |
| R2 | 9 | BTN_TR2 |
| Select | 10 | BTN_SELECT |
| Start | 11 | BTN_START |
| L3 | 13 | BTN_THUMBL |
| R3 | 14 | BTN_THUMBR |

Bits 2, 5, 12, 15 are unused (usages 3, 6, 13, 16 never set). The descriptor
declares 16 one-bit buttons; the bit positions are chosen so the kernel maps
them to modern gamepad names (HID Button usage N → evdev code 303+N). Plus a hat
switch for the D-pad.

Full 9-byte report layout: `[LX, LY, RX, RY, L2, R2, hat+pad, btn_lo, btn_hi]`.

**Re-pairing note:** the v6 report grew from 7 to 9 data bytes, so installing v6
required an unpair/re-pair so the host re-reads the descriptor. The v7 update
changed only which button bit X/Y set (descriptor and layout untouched) — no
re-pair needed, just reinstall the APK. **v8 adds profile switching: changing
profiles re-registers the HID app, so the host must reconnect** (and if the
new profile changes the report layout, unpair/re-pair). The default v8
profile is byte-for-byte the v7 mapping, so installing v8 over v7 needs no
re-pair until you actually switch profiles.

(Historical note: an earlier v1.1 experiment made L2/R2 analog trigger axes
in an Xbox 360-style layout, but it moved the right stick to SDL axes 3/4 and
broke in-game right-stick look, so it was reverted. This build keeps the
v1.0 stick/axis layout and adds triggers via ABS_BRAKE/ABS_GAS instead.)

## Use it

1. Install the APK from the [releases](../../releases) (or build below).
2. Open the app, tap **Enable controller**.
3. On the PC: open Bluetooth settings, pair with the phone, and connect.
   Linux may need `trust` + `connect` in `bluetoothctl`.
4. The phone shows "Connected". Keep the app in the foreground — Android drops
   the HID channel if it backgrounds or the screen sleeps.

Verify on Linux: `sudo evtest` on the new `js*` device, or `jstest /dev/input/js0`.

## Build it

```sh
./gradlew assembleDebug
```

Needs the Android SDK (minSdk 28, targetSdk 34) and a JDK 17. The APK is
debug-signed — fine for sideloading.

If the Gradle daemon won't cooperate in your environment, there's a manual
build path (`javac` → `d8` → `aapt2 link` → `zipalign` → `apksigner`) —
see `build-manual/` notes from when we did exactly that.

## Troubleshooting

- **Paired but no joystick device**: the HID channel isn't up. Tap "Enable
  controller" *before* pairing/connecting from the PC, and on Linux run
  `connect` in `bluetoothctl`, not just `pair`.
- **Inputs act like a keyboard / type letters**: the host built the device from
  a stale descriptor. Unpair completely (`bluetoothctl remove <MAC>`) and
  re-pair after enabling the controller.
- **It worked, then stopped**: app got backgrounded or the screen slept.
  Reopen it; the HID channel re-establishes.
- **Phone lacks HID device role**: some phones/ROMs don't implement
  `BluetoothHidDevice`. Nothing to do about that one — it's a hardware/firmware
  thing. Most Android 9+ devices have it.

## License

Do whatever you want with it. If it helps you ship a game, that's payment enough.
