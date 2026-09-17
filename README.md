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

- Left stick (X/Y) and right stick (Rx/Ry)
- D-pad (hat switch)
- Face buttons: A B X Y (bottom/right/left/top diamond)
- L1 / R1 bumpers, **analog** L2 / R2 triggers
- Start / Select, L3 / R3 stick clicks

**HID layout** (v1.1+, Xbox 360 style, Report ID 1)

| SDL axis | Linux evdev | Control |
|---|---|---|
| 0 | ABS_X (−127..127) | Left stick X |
| 1 | ABS_Y (−127..127) | Left stick Y |
| 2 | ABS_Z (0..255) | L2 trigger |
| 3 | ABS_RX (−127..127) | Right stick X |
| 4 | ABS_RY (−127..127) | Right stick Y |
| 5 | ABS_RZ (0..255) | R2 trigger |

Buttons 0–9: A, B, X, Y, LB, RB, Select, Start, L3, R3. Plus a hat switch
for the D-pad. Touchscreen triggers are full-pull only (0 or 255 — it's glass,
not potentiometers).

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
