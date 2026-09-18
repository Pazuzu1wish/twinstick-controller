package com.twinstick.controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Touchscreen twin-stick gamepad. Landscape layout:
 *   left stick (bottom-left), D-pad above it (mid-left)
 *   right stick (bottom-right), 4 face buttons in diamond above it (mid-right)
 *   L1/L2 stacked on top-left edge, R1/R2 stacked on top-right edge
 *   Start/Select small buttons top-center, L3/R3 just above the sticks
 * Multi-touch: each pointer is tracked independently per control.
 */
public class ControllerView extends View {

    public interface Listener {
        void onStateChanged();
    }

    private Listener listener;
    public void setListener(Listener l) { listener = l; }

    // ---- HID state (profile-driven; no hardcoded positions) ----
    private HidReport report;
    private int stickLX, stickLY, stickRX, stickRY; // raw -127..127 (profile deadzone applied at pack time)
    private int hat = HidReport.HAT_NEUTRAL;
    private int buttons; // bit i = profile button index i

    // Cached profile positions; -1 if the profile lacks the control.
    private int bitA = -1, bitB = -1, bitX = -1, bitY = -1;
    private int bitL1 = -1, bitR1 = -1, bitL2 = -1, bitR2 = -1;
    private int bitSel = -1, bitStart = -1, bitL3 = -1, bitR3 = -1;
    private int axLX = -1, axLY = -1, axRX = -1, axRY = -1, axL2 = -1, axR2 = -1;

    /** Install the active profile's packer. Resets all input state. */
    public void setHidReport(HidReport r) {
        report = r;
        buttons = 0;
        hat = HidReport.HAT_NEUTRAL;
        stickLX = stickLY = stickRX = stickRY = 0;
        knobLX = knobLY = knobRX = knobRY = 0;
        ptrButton.clear();
        ptrDpad.clear();
        ptrStickL = ptrStickR = -1;
        if (r != null) {
            bitA = r.buttonBit("A"); bitB = r.buttonBit("B");
            bitX = r.buttonBit("X"); bitY = r.buttonBit("Y");
            bitL1 = r.buttonBit("L1"); bitR1 = r.buttonBit("R1");
            bitL2 = r.buttonBit("L2"); bitR2 = r.buttonBit("R2");
            bitSel = r.buttonBit("SELECT"); bitStart = r.buttonBit("START");
            bitL3 = r.buttonBit("L3"); bitR3 = r.buttonBit("R3");
            axLX = r.axisIndex("left_x"); axLY = r.axisIndex("left_y");
            axRX = r.axisIndex("right_x"); axRY = r.axisIndex("right_y");
            axL2 = r.axisIndex("l2"); axR2 = r.axisIndex("r2");
        }
        invalidate();
    }

    public byte[] buildReport() {
        if (report == null) return new byte[0];
        int n = report.getAxisCount();
        int[] raw = new int[n];
        if (axLX >= 0) raw[axLX] = stickLX;
        if (axLY >= 0) raw[axLY] = stickLY;
        if (axRX >= 0) raw[axRX] = stickRX;
        if (axRY >= 0) raw[axRY] = stickRY;
        // L2/R2 are dual-reported: the touch zones drive both the button
        // entries and the analog trigger axes (pressed -> max, released -> min).
        if (axL2 >= 0) raw[axL2] = isPressed(bitL2)
                ? report.axisMax(axL2) : report.axisMin(axL2);
        if (axR2 >= 0) raw[axR2] = isPressed(bitR2)
                ? report.axisMax(axR2) : report.axisMin(axR2);
        return report.build(raw, hat, buttons);
    }

    // ---- geometry ----
    private float W, H;
    private float stickR, knobR, btnR, dpadArm, dpadBtnR, smallR;
    private float lSx, lSy, rSx, rSy;          // stick centers
    private float dCx, dCy;                    // dpad center
    private float fCx, fCy;                    // face diamond center
    private float aX, aY, bX, bY, xX, xY, yX, yY; // face buttons
    private float l1x, l1y, l2x, l2y, r1x, r1y, r2x, r2y;
    private float selX, selY, stX, stY;
    private float l3x, l3y, r3x, r3y;

    // ---- touch tracking ----
    private int ptrStickL = -1, ptrStickR = -1;
    private float knobLX, knobLY, knobRX, knobRY; // knob offsets, pixels
    private final Map<Integer, Integer> ptrButton = new HashMap<>(); // pointerId -> button bit
    private final Map<Integer, Integer> ptrDpad = new HashMap<>();   // pointerId -> dir flag
    private static final int D_UP = 1, D_RIGHT = 2, D_DOWN = 4, D_LEFT = 8;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ControllerView(Context ctx) { super(ctx); init(); }
    public ControllerView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        W = w; H = h;
        stickR = H * 0.195f;   // +15% vs v1.0 for fatter thumbs
        knobR = stickR * 0.45f;
        btnR = H * 0.062f;
        smallR = H * 0.045f;
        dpadArm = H * 0.115f;
        dpadBtnR = H * 0.075f;
        textPaint.setTextSize(H * 0.045f);

        lSx = W * 0.135f; lSy = H * 0.70f;
        rSx = W * 0.865f; rSy = H * 0.70f;
        dCx = W * 0.215f; dCy = H * 0.30f;   // d-pad nudged inward (+8% W)
        fCx = W * 0.785f; fCy = H * 0.30f;   // face diamond nudged inward (-8% W)
        float spread = H * 0.115f;
        aX = fCx;        aY = fCy + spread; // bottom
        bX = fCx + spread; bY = fCy;        // right
        xX = fCx - spread; xY = fCy;        // left
        yX = fCx;        yY = fCy - spread; // top

        l1x = W * 0.048f; l1y = H * 0.09f;    // nudged up/out for clearance
        l2x = W * 0.048f; l2y = H * 0.235f;   // clear of the d-pad's left arm
        r1x = W * 0.952f; r1y = H * 0.09f;
        r2x = W * 0.952f; r2y = H * 0.235f;   // clear of the face diamond

        selX = W * 0.455f; selY = H * 0.09f;
        stX = W * 0.545f;  stY = H * 0.09f;

        // L3/R3 sit just above their sticks: with the bigger sticks there is no
        // room left at the outer edges (they would clip off-screen at 16:9).
        l3x = lSx; l3y = lSy - stickR - smallR - H * 0.025f;
        r3x = rSx; r3y = rSy - stickR - smallR - H * 0.025f;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void setButtonBit(int bit, boolean pressed) {
        int before = buttons;
        if (pressed) buttons |= (1 << bit);
        else buttons &= ~(1 << bit);
        if (before != buttons) notifyChanged();
    }

    private boolean isPressed(int bit) {
        return bit >= 0 && (buttons & (1 << bit)) != 0;
    }

    private void notifyChanged() {
        if (listener != null) listener.onStateChanged();
        invalidate();
    }

    private int axisValue(float offsetPx) {
        // Raw -127..127; the profile's deadzone/invert/clamp are applied when
        // the report is packed.
        int v = Math.round(127f * offsetPx / stickR);
        if (v > 127) v = 127;
        if (v < -127) v = -127;
        return v;
    }

    private void updateHat() {
        boolean up = false, right = false, down = false, left = false;
        for (int dir : ptrDpad.values()) {
            if ((dir & D_UP) != 0) up = true;
            if ((dir & D_RIGHT) != 0) right = true;
            if ((dir & D_DOWN) != 0) down = true;
            if ((dir & D_LEFT) != 0) left = true;
        }
        int v = (right ? 1 : 0) - (left ? 1 : 0);
        int w2 = (down ? 1 : 0) - (up ? 1 : 0);
        int newHat;
        if (v == 0 && w2 == 0) newHat = HidReport.HAT_NEUTRAL;
        else if (v == 0 && w2 == -1) newHat = 0;
        else if (v == 1 && w2 == -1) newHat = 1;
        else if (v == 1 && w2 == 0) newHat = 2;
        else if (v == 1 && w2 == 1) newHat = 3;
        else if (v == 0 && w2 == 1) newHat = 4;
        else if (v == -1 && w2 == 1) newHat = 5;
        else if (v == -1 && w2 == 0) newHat = 6;
        else newHat = 7;
        if (newHat != hat) { hat = newHat; notifyChanged(); }
    }

    /** Returns the profile button bit for a tap, or -1. Checks face, shoulders, start/select, L3/R3. */
    private int buttonAt(float x, float y) {
        if (bitA >= 0 && dist(x, y, aX, aY) <= btnR * 1.25f) return bitA;
        if (bitB >= 0 && dist(x, y, bX, bY) <= btnR * 1.25f) return bitB;
        if (bitX >= 0 && dist(x, y, xX, xY) <= btnR * 1.25f) return bitX;
        if (bitY >= 0 && dist(x, y, yX, yY) <= btnR * 1.25f) return bitY;
        if (bitL1 >= 0 && dist(x, y, l1x, l1y) <= btnR * 1.25f) return bitL1;
        if (bitL2 >= 0 && dist(x, y, l2x, l2y) <= btnR * 1.25f) return bitL2;
        if (bitR1 >= 0 && dist(x, y, r1x, r1y) <= btnR * 1.25f) return bitR1;
        if (bitR2 >= 0 && dist(x, y, r2x, r2y) <= btnR * 1.25f) return bitR2;
        if (bitSel >= 0 && dist(x, y, selX, selY) <= smallR * 1.4f) return bitSel;
        if (bitStart >= 0 && dist(x, y, stX, stY) <= smallR * 1.4f) return bitStart;
        if (bitL3 >= 0 && dist(x, y, l3x, l3y) <= smallR * 1.4f) return bitL3;
        if (bitR3 >= 0 && dist(x, y, r3x, r3y) <= smallR * 1.4f) return bitR3;
        return -1;
    }

    /** Returns dpad direction flag for a tap, or 0. */
    private int dpadAt(float x, float y) {
        if (dist(x, y, dCx, dCy - dpadArm) <= dpadBtnR) return D_UP;
        if (dist(x, y, dCx + dpadArm, dCy) <= dpadBtnR) return D_RIGHT;
        if (dist(x, y, dCx, dCy + dpadArm) <= dpadBtnR) return D_DOWN;
        if (dist(x, y, dCx - dpadArm, dCy) <= dpadBtnR) return D_LEFT;
        return 0;
    }

    private void releasePointer(int pid) {
        if (pid == ptrStickL) {
            ptrStickL = -1; knobLX = 0; knobLY = 0;
            stickLX = 0; stickLY = 0; notifyChanged();
        }
        if (pid == ptrStickR) {
            ptrStickR = -1; knobRX = 0; knobRY = 0;
            stickRX = 0; stickRY = 0; notifyChanged();
        }
        Integer bit = ptrButton.remove(pid);
        if (bit != null) setButtonBit(bit, false);
        if (ptrDpad.remove(pid) != null) updateHat();
    }

    private void moveStick(int pid, float x, float y) {
        boolean left = pid == ptrStickL;
        float cx = left ? lSx : rSx, cy = left ? lSy : rSy;
        float dx = x - cx, dy = y - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > stickR) { dx = dx / len * stickR; dy = dy / len * stickR; }
        if (left) {
            knobLX = dx; knobLY = dy;
            stickLX = axisValue(dx); stickLY = axisValue(dy);
        } else {
            knobRX = dx; knobRY = dy;
            stickRX = axisValue(dx); stickRY = axisValue(dy);
        }
        notifyChanged();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        int pid = e.getPointerId(idx);
        float x = e.getX(idx), y = e.getY(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (ptrStickL == -1 && dist(x, y, lSx, lSy) <= stickR * 1.3f) {
                    ptrStickL = pid; moveStick(pid, x, y);
                } else if (ptrStickR == -1 && dist(x, y, rSx, rSy) <= stickR * 1.3f) {
                    ptrStickR = pid; moveStick(pid, x, y);
                } else {
                    int dir = dpadAt(x, y);
                    if (dir != 0) { ptrDpad.put(pid, dir); updateHat(); }
                    else {
                        int bit = buttonAt(x, y);
                        if (bit >= 0) { ptrButton.put(pid, bit); setButtonBit(bit, true); }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int p = e.getPointerId(i);
                    if (p == ptrStickL || p == ptrStickR) moveStick(p, e.getX(i), e.getY(i));
                    else if (ptrDpad.containsKey(p)) {
                        int dir = dpadAt(e.getX(i), e.getY(i));
                        if (dir == 0) { ptrDpad.remove(p); updateHat(); }
                        else if (dir != ptrDpad.get(p)) { ptrDpad.put(p, dir); updateHat(); }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                releasePointer(pid);
                break;
        }
        return true;
    }

    // ---- drawing ----
    private void drawStick(Canvas c, float cx, float cy, float kx, float ky) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF2A2A38);
        c.drawCircle(cx, cy, stickR, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(0xFF6A6A8A);
        c.drawCircle(cx, cy, stickR, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF4A4A66);
        c.drawCircle(cx + kx, cy + ky, knobR, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF9A9AC0);
        c.drawCircle(cx + kx, cy + ky, knobR, paint);
    }

    private void drawButton(Canvas c, float cx, float cy, float r, String label, boolean pressed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? 0xFF7A5AC8 : 0xFF2A2A38);
        c.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(pressed ? 0xFFFFFFFF : 0xFF6A6A8A);
        c.drawCircle(cx, cy, r, paint);
        c.drawText(label, cx, cy + textPaint.getTextSize() * 0.35f, textPaint);
    }

    private void drawDpad(Canvas c) {
        // arms: up/right/down/left
        float[][] arms = {
            {dCx, dCy - dpadArm, D_UP}, {dCx + dpadArm, dCy, D_RIGHT},
            {dCx, dCy + dpadArm, D_DOWN}, {dCx - dpadArm, dCy, D_LEFT}
        };
        boolean[] on = new boolean[4];
        for (int dir : ptrDpad.values()) {
            if ((dir & D_UP) != 0) on[0] = true;
            if ((dir & D_RIGHT) != 0) on[1] = true;
            if ((dir & D_DOWN) != 0) on[2] = true;
            if ((dir & D_LEFT) != 0) on[3] = true;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF23232F);
        // plus shape: vertical + horizontal bars
        float w2 = dpadBtnR * 0.9f;
        c.drawRect(dCx - w2, dCy - dpadArm - dpadBtnR, dCx + w2, dCy + dpadArm + dpadBtnR, paint);
        c.drawRect(dCx - dpadArm - dpadBtnR, dCy - w2, dCx + dpadArm + dpadBtnR, dCy + w2, paint);
        String[] labels = {"^", ">", "v", "<"};
        for (int i = 0; i < 4; i++) {
            drawButton(c, arms[i][0], arms[i][1], dpadBtnR, labels[i], on[i]);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF14141C);
        drawDpad(c);
        drawStick(c, lSx, lSy, knobLX, knobLY);
        drawStick(c, rSx, rSy, knobRX, knobRY);

        drawButton(c, aX, aY, btnR, "A", isPressed(bitA));
        drawButton(c, bX, bY, btnR, "B", isPressed(bitB));
        drawButton(c, xX, xY, btnR, "X", isPressed(bitX));
        drawButton(c, yX, yY, btnR, "Y", isPressed(bitY));

        drawButton(c, l1x, l1y, btnR, "L1", isPressed(bitL1));
        drawButton(c, l2x, l2y, btnR, "L2", isPressed(bitL2));
        drawButton(c, r1x, r1y, btnR, "R1", isPressed(bitR1));
        drawButton(c, r2x, r2y, btnR, "R2", isPressed(bitR2));

        drawButton(c, selX, selY, smallR, "SEL", isPressed(bitSel));
        drawButton(c, stX, stY, smallR, "STA", isPressed(bitStart));
        drawButton(c, l3x, l3y, smallR, "L3", isPressed(bitL3));
        drawButton(c, r3x, r3y, smallR, "R3", isPressed(bitR3));
    }
}
