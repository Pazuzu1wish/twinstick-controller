package com.twinstick.controller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;

/**
 * Hosts the ControllerView and acts as a Bluetooth HID gamepad.
 * Pairing is initiated from the PC side: tap "Enable controller", make sure
 * the phone is discoverable, then pair + connect from the PC's Bluetooth settings.
 */
public class MainActivity extends Activity {

    private static final int REQ_BT_PERMS = 1;
    private static final int REQ_ENABLE_BT = 2;
    private static final int REQ_DISCOVERABLE = 3;
    private static final int REQ_PROFILES = 4;
    private static final long PROXY_TIMEOUT_MS = 12000;

    private TextView statusText;
    private Button enableButton;
    private ControllerView controllerView;

    private BluetoothAdapter btAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice hostDevice;
    private byte[] lastReport = new byte[9];
    private boolean dirty;
    private boolean appRegistered;
    private boolean pendingProfileSwitch;

    private HidProfile activeProfile;
    private HidReport hidReport;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sendLoop = new Runnable() {
        @Override public void run() {
            if (dirty && hidDevice != null && hostDevice != null) {
                byte[] r = controllerView.buildReport();
                if (hidDevice.sendReport(hostDevice, HidReport.REPORT_ID, r)) {
                    lastReport = Arrays.copyOf(r, r.length);
                    dirty = false;
                }
            }
            handler.postDelayed(this, 16); // ~60 Hz max
        }
    };

    private final Runnable proxyTimeout = new Runnable() {
        @Override public void run() {
            if (hidDevice == null) {
                setStatus("HID device role not supported on this phone.");
                enableButton.setEnabled(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(16, 16, 16, 16);

        statusText = new TextView(this);
        statusText.setText("Tap \"Enable controller\" to start.");
        statusText.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(statusText, sp);

        enableButton = new Button(this);
        enableButton.setText("Enable controller");
        enableButton.setOnClickListener(v -> onEnableClicked());
        topBar.addView(enableButton);

        Button settingsButton = new Button(this);
        settingsButton.setText("Profiles");
        settingsButton.setOnClickListener(v -> startActivityForResult(
                new Intent(this, ProfilesActivity.class), REQ_PROFILES));
        topBar.addView(settingsButton);

        controllerView = new ControllerView(this);
        controllerView.setListener(() -> dirty = true);
        reloadProfile(); // active profile -> packer -> ControllerView

        root.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(controllerView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = bm != null ? bm.getAdapter() : null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(sendLoop);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(sendLoop);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(proxyTimeout);
        if (hidDevice != null) {
            try { hidDevice.unregisterApp(); } catch (Exception ignored) {}
            if (btAdapter != null) {
                try { btAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice); }
                catch (Exception ignored) {}
            }
            hidDevice = null;
        }
        super.onDestroy();
    }

    private void setStatus(final String s) {
        runOnUiThread(() -> statusText.setText(s));
    }

    /** Load the active profile, build its packer, and hand it to the view. */
    private void reloadProfile() {
        activeProfile = ProfileStore.getActive(this);
        hidReport = new HidReport(activeProfile);
        controllerView.setHidReport(hidReport);
        lastReport = new byte[hidReport.getReportLength()];
        dirty = true;
    }

    /** Called when returning from ProfilesActivity with a changed profile. */
    private void onProfileChanged() {
        reloadProfile();
        if (appRegistered && hidDevice != null) {
            // The SDP record carries the descriptor: unregister, then
            // re-register with the new one. The host must reconnect.
            pendingProfileSwitch = true;
            try {
                hidDevice.unregisterApp();
            } catch (Exception e) {
                pendingProfileSwitch = false;
                setStatus("Profile switch failed: " + e.getMessage());
            }
        } else {
            setStatus("Profile switched to \"" + activeProfile.name + "\".");
        }
    }

    private void onEnableClicked() {
        if (btAdapter == null) {
            setStatus("No Bluetooth on this device.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 &&
                (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE}, REQ_BT_PERMS);
            return;
        }
        startHid();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_BT_PERMS) {
            boolean ok = true;
            for (int r : results) ok &= (r == PackageManager.PERMISSION_GRANTED);
            if (ok) startHid();
            else setStatus("Bluetooth permissions denied.");
        }
    }

    private void startHid() {
        if (!btAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT);
            return;
        }
        // Make discoverable so the PC can find + pair with us.
        Intent disc = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        disc.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivityForResult(disc, REQ_DISCOVERABLE);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_ENABLE_BT) {
            if (btAdapter.isEnabled()) startHid();
            else setStatus("Bluetooth not enabled.");
        } else if (code == REQ_DISCOVERABLE) {
            registerHidApp();
        } else if (code == REQ_PROFILES) {
            if (result == RESULT_OK && data != null
                    && data.getBooleanExtra(ProfilesActivity.EXTRA_PROFILE_CHANGED, false)) {
                onProfileChanged();
            }
        }
    }

    private void registerHidApp() {
        if (hidDevice != null) return;
        setStatus("Registering HID gamepad...");
        enableButton.setEnabled(false);
        handler.postDelayed(proxyTimeout, PROXY_TIMEOUT_MS);
        btAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                handler.removeCallbacks(proxyTimeout);
                hidDevice = (BluetoothHidDevice) proxy;
                boolean ok = hidDevice.registerApp(buildSdpSettings(), null, null,
                        getMainExecutor(), hidCallback);
                if (!ok) {
                    setStatus("registerApp failed: HID device role unavailable.");
                    enableButton.setEnabled(true);
                }
            }
            @Override public void onServiceDisconnected(int profile) { hidDevice = null; }
        }, BluetoothProfile.HID_DEVICE);
    }

    /** SDP settings for the active profile (descriptor bytes come from the profile). */
    private BluetoothHidDeviceAppSdpSettings buildSdpSettings() {
        return new BluetoothHidDeviceAppSdpSettings(
                "TwinStick Controller",
                "TwinStick Bluetooth gamepad (" + activeProfile.name + ")",
                "TwinStick",
                BluetoothHidDevice.SUBCLASS2_GAMEPAD,
                hidReport.getDescriptor());
    }

    private final BluetoothHidDevice.Callback hidCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice device, boolean registered) {
            if (registered) {
                appRegistered = true;
                if (pendingProfileSwitch) {
                    pendingProfileSwitch = false;
                    setStatus("Profile switched to \"" + activeProfile.name +
                            "\" — reconnect from the host to pick up the new descriptor.");
                } else {
                    setStatus("Waiting for host - pair from the PC's Bluetooth settings.");
                }
            } else {
                appRegistered = false;
                if (pendingProfileSwitch) {
                    // Our own unregister for a profile switch: re-register now
                    // with the new descriptor.
                    if (hidDevice != null) {
                        hidDevice.registerApp(buildSdpSettings(), null, null,
                                getMainExecutor(), hidCallback);
                    } else {
                        pendingProfileSwitch = false;
                    }
                } else {
                    setStatus("HID app unregistered.");
                    runOnUiThread(() -> enableButton.setEnabled(true));
                }
            }
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device;
                String name = device.getName() != null ? device.getName() : device.getAddress();
                setStatus("Connected to " + name);
                dirty = true; // push current state immediately
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (device.equals(hostDevice)) hostDevice = null;
                setStatus("Disconnected - waiting for host...");
            }
        }

        @Override
        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            if (hidDevice != null) hidDevice.replyReport(device, type, id, lastReport);
        }

        @Override
        public void onVirtualCableUnplug(BluetoothDevice device) {
            if (device.equals(hostDevice)) hostDevice = null;
            setStatus("Unplugged - waiting for host...");
        }
    };
}
