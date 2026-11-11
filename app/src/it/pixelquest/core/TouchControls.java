package it.pixelquest.core;

import java.util.ArrayList;

/**
 * On-screen virtual controller, tuned for responsiveness.
 * <ul>
 * <li>Movement, two selectable modes:
 *   <ul><li>STICK: analog stick (touching the visible pad moves immediately, touching elsewhere on the left
 *   places it under the finger; the base follows the finger so reversing is instant; push to the edge to run)</li>
 *   <li>ARROWS: two arrow buttons; holding one walks and after a short moment runs automatically</li></ul></li>
 * <li>A (jump) / B (spin jump) / C (ability): the whole right side of the screen is split between the three
 * buttons (nearest wins), so a press never misses because it landed slightly outside a circle.</li>
 * <li>Presses are latched: a tap shorter than one game frame is still delivered.</li>
 * </ul>
 * Pointer events arrive in virtual-screen coordinates from the platform thread.
 */
public final class TouchControls {
    public static final int STICK = 0, ARROWS = 1;
    public static final int NONE = 0, BTN_A = 1, BTN_B = 2, BTN_C = 3, BTN_PAUSE = 4, MOVE = 5;
    private static final int MAXP = 10;
    private final int[] pid = new int[MAXP];
    private final float[] px = new float[MAXP], py = new float[MAXP];
    private final int[] role = new int[MAXP]; // 1 move, 2 buttons, 3 pause
    private final boolean[] used = new boolean[MAXP];
    private final ArrayList<float[]> pendingTaps = new ArrayList<>();
    // presses that happened since the last poll (kept even if the finger was already lifted)
    private boolean pendA, pendB, pendC, pendPause;
    private int pendMoveDir;

    public int W = 400, H = 224;
    public volatile int mode = STICK;
    /** When false (menus) touches are only taps: no stick / buttons. */
    public volatile boolean enabled = true;
    public float ax, ay;
    public boolean a, b, c, pause;
    public boolean visible = true;
    public boolean stickActive;
    public float baseX, baseY, knobX, knobY;
    public final ArrayList<float[]> taps = new ArrayList<>();
    public boolean anyTouch;
    // arrows
    public int arrowDir;
    private int arrowHeld, arrowLastDir;

    public static final int STICK_R = 28;
    /** Stick deflection (0..1) above which the hero runs. */
    public static final float RUN_AT = 0.72f;
    /** Frames an arrow must be held before the hero starts running. */
    public static final int ARROW_RUN_FRAMES = 14;

    public float defBaseX() {
        return 52;
    }

    public float defBaseY() {
        return H - 50;
    }

    public float[] btnA() {
        return new float[]{W - 40, H - 48, 24};
    }

    public float[] btnB() {
        return new float[]{W - 96, H - 34, 20};
    }

    public float[] btnC() {
        return new float[]{W - 84, H - 98, 19};
    }

    public float[] btnPause() {
        return new float[]{W - 16, 14, 11};
    }

    /** Left / right arrow buttons: {cx, cy, halfW, halfH}. */
    public float[] arrowL() {
        return new float[]{34, H - 46, 27, 25};
    }

    public float[] arrowR() {
        return new float[]{98, H - 46, 27, 25};
    }

    private float moveZone() {
        return W * 0.42f;
    }

    private static float dist(float x, float y, float x2, float y2) {
        float dx = x - x2, dy = y - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Which action button a point on the right side belongs to (nearest button, generous reach). */
    private int buttonAt(float x, float y) {
        float[] A = btnA(), B = btnB(), C = btnC();
        float dA = dist(x, y, A[0], A[1]) / A[2];
        float dB = dist(x, y, B[0], B[1]) / B[2];
        float dC = dist(x, y, C[0], C[1]) / C[2];
        float best = Math.min(dA, Math.min(dB, dC));
        // anything in the lower right area counts, even right at the screen edge / corner
        boolean lowerRight = y > H * 0.45f && x > W * 0.55f;
        if (best > 2.8f && !lowerRight) return NONE;
        if (best == dA) return BTN_A;
        if (best == dB) return BTN_B;
        return BTN_C;
    }

    private int arrowAt(float x) {
        float[] L = arrowL(), R = arrowR();
        return x < (L[0] + R[0]) / 2f ? -1 : 1;
    }

    /** Finger down. Returns what was pressed (for haptic feedback on the platform side). */
    public synchronized int down(int id, float x, float y) {
        for (int i = 0; i < MAXP; i++)
            if (!used[i]) {
                // reset the slot first: a stale role from the previous finger must not leak in
                role[i] = 0;
                used[i] = true;
                pid[i] = id;
                px[i] = x;
                py[i] = y;
                pendingTaps.add(new float[]{x, y});
                if (!enabled) {
                    role[i] = 0;
                    return NONE;
                }
                float[] p = btnPause();
                if (dist(x, y, p[0], p[1]) < p[2] + 9) {
                    role[i] = 3;
                    pendPause = true;
                    return BTN_PAUSE;
                }
                if (x < moveZone() && !hasRole(1)) {
                    role[i] = 1;
                    if (mode == ARROWS) {
                        pendMoveDir = arrowAt(x);
                    } else {
                        // touching the visible pad keeps it in place (instant direction);
                        // elsewhere the pad jumps under the finger
                        if (dist(x, y, defBaseX(), defBaseY()) > STICK_R * 1.7f) {
                            baseX = Math.max(STICK_R + 4, Math.min(moveZone() - 8, x));
                            baseY = Math.max(STICK_R + 4, Math.min(H - STICK_R - 4, y));
                        } else {
                            baseX = defBaseX();
                            baseY = defBaseY();
                        }
                    }
                    return MOVE;
                }
                role[i] = 2;
                int bt = buttonAt(x, y);
                if (bt == BTN_A) pendA = true;
                else if (bt == BTN_B) pendB = true;
                else if (bt == BTN_C) pendC = true;
                return bt;
            }
        return NONE;
    }

    public synchronized void move(int id, float x, float y) {
        for (int i = 0; i < MAXP; i++)
            if (used[i] && pid[i] == id) {
                px[i] = x;
                py[i] = y;
                if (role[i] == 1 && mode == STICK) {
                    // the base follows the finger beyond the rim: reversing direction needs only a short move
                    float dx = x - baseX, dy = y - baseY;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d > STICK_R) {
                        float k = (d - STICK_R) / d;
                        baseX += dx * k;
                        baseY += dy * k;
                    }
                }
            }
    }

    public synchronized void up(int id) {
        for (int i = 0; i < MAXP; i++)
            if (used[i] && pid[i] == id) used[i] = false;
    }

    public synchronized void cancelAll() {
        for (int i = 0; i < MAXP; i++) used[i] = false;
        pendA = pendB = pendC = pendPause = false;
        pendMoveDir = 0;
    }

    private boolean hasRole(int r) {
        for (int i = 0; i < MAXP; i++) if (used[i] && role[i] == r) return true;
        return false;
    }

    /** Number of fingers currently down (for tests). */
    public synchronized int fingers() {
        int n = 0;
        for (int i = 0; i < MAXP; i++) if (used[i]) n++;
        return n;
    }

    /** Called once per game update on the game thread. */
    public synchronized void poll() {
        taps.clear();
        taps.addAll(pendingTaps);
        pendingTaps.clear();
        ax = ay = 0;
        a = pendA;
        b = pendB;
        c = pendC;
        pause = pendPause;
        pendA = pendB = pendC = pendPause = false;
        stickActive = false;
        anyTouch = false;
        int dirNow = 0;
        for (int i = 0; i < MAXP; i++) {
            if (!used[i]) continue;
            anyTouch = true;
            if (role[i] == 1) {
                stickActive = true;
                if (mode == ARROWS) {
                    dirNow = arrowAt(px[i]);
                } else {
                    float dx = px[i] - baseX, dy = py[i] - baseY;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float k = d > STICK_R ? STICK_R / d : 1;
                    knobX = baseX + dx * k;
                    knobY = baseY + dy * k;
                    float nx = dx / STICK_R, ny = dy / STICK_R;
                    ax = Math.max(-1, Math.min(1, nx));
                    ay = Math.max(-1, Math.min(1, ny));
                    // small dead zone, then a firm minimum so the hero reacts at once
                    if (Math.abs(ax) < 0.12f) ax = 0;
                    else if (Math.abs(ax) < 0.3f) ax = Math.signum(ax) * 0.3f;
                    if (Math.abs(ay) < 0.45f) ay = 0;
                }
            } else if (role[i] == 2) {
                int bt = buttonAt(px[i], py[i]);
                if (bt == BTN_A) a = true;
                else if (bt == BTN_B) b = true;
                else if (bt == BTN_C) c = true;
            } else if (role[i] == 3) pause = true;
        }
        if (mode == ARROWS) {
            if (dirNow == 0 && pendMoveDir != 0) dirNow = pendMoveDir; // tap shorter than a frame
            pendMoveDir = 0;
            if (dirNow != 0 && dirNow == arrowLastDir) arrowHeld++;
            else arrowHeld = dirNow != 0 ? 1 : 0;
            arrowLastDir = dirNow;
            arrowDir = dirNow;
            ax = dirNow == 0 ? 0 : dirNow * (arrowHeld >= ARROW_RUN_FRAMES ? 1f : 0.55f);
        }
        if (!stickActive) {
            baseX = knobX = defBaseX();
            baseY = knobY = defBaseY();
        }
    }

    public void draw(Gfx g, String cLabel, boolean showC, boolean showPause) {
        if (!visible) return;
        if (mode == ARROWS) drawArrows(g);
        else drawStick(g);
        drawBtn(g, btnA(), "A", 0xFF3AB04A, a);
        drawBtn(g, btnB(), "B", 0xFF3A7AE0, b);
        if (showC) drawBtn(g, btnC(), "C", 0xFFE0503A, c);
        if (showC && cLabel != null) {
            float[] C = btnC();
            g.textC(cLabel, (int) C[0], (int) (C[1] - C[2] - 10), 0xFFFFFFFF, 0xFF000000, 1);
        }
        if (showPause) {
            float[] P = btnPause();
            g.circle((int) P[0], (int) P[1], (int) P[2], 0xFF101020, pause ? 200 : 110);
            g.ring((int) P[0], (int) P[1], (int) P[2], 1, 0xFFFFFFFF, 140);
            g.fill((int) P[0] - 4, (int) P[1] - 4, 3, 9, 0xFFFFFFFF);
            g.fill((int) P[0] + 1, (int) P[1] - 4, 3, 9, 0xFFFFFFFF);
        }
    }

    private void drawStick(Gfx g) {
        int ring = 0xFFFFFFFF;
        g.circle((int) baseX, (int) baseY, STICK_R + 4, 0xFF101020, 70);
        g.ring((int) baseX, (int) baseY, STICK_R + 4, 2, ring, 110);
        g.ring((int) baseX, (int) baseY, (int) (STICK_R * RUN_AT), 1, ring, 40);
        int kx = (int) knobX, ky = (int) knobY;
        boolean run = Math.abs(ax) >= RUN_AT;
        g.circle(kx, ky, 13, run ? 0xFFFFE070 : 0xFFE8E8F0, stickActive ? 200 : 120);
        g.circle(kx - 3, ky - 3, 5, 0xFFFFFFFF, stickActive ? 160 : 90);
        g.ring(kx, ky, 13, 1, 0xFF404060, 140);
        g.text("\u0004", (int) baseX + STICK_R - 5, (int) baseY - 3, 0xFFFFFFFF, 1, 90);
    }

    private void drawArrows(Gfx g) {
        float[][] arr = {arrowL(), arrowR()};
        for (int k = 0; k < 2; k++) {
            float[] r = arr[k];
            int dir = k == 0 ? -1 : 1;
            boolean on = arrowDir == dir;
            boolean running = on && arrowHeld >= ARROW_RUN_FRAMES;
            int x0 = (int) (r[0] - r[2]), y0 = (int) (r[1] - r[3]), w = (int) (r[2] * 2), h = (int) (r[3] * 2);
            g.fillA(x0 + 2, y0 + 2, w, h, 0xFF000000, 60);
            g.fillA(x0, y0 + (on ? 1 : 0), w, h, on ? (running ? 0xFF8A7A30 : 0xFF4A4A70) : 0xFF101020, on ? 200 : 110);
            g.rect(x0, y0 + (on ? 1 : 0), w, h, 0xFFFFFFFF);
            // triangle
            int cx = (int) r[0], cy = (int) r[1] + (on ? 1 : 0);
            for (int i = 0; i < 12; i++) {
                int hh = 12 - i;
                int x = cx + dir * (i - 6);
                g.fillA(x, cy - hh, 1, hh * 2, running ? 0xFFFFE070 : 0xFFFFFFFF, on ? 255 : 190);
            }
        }
    }

    private void drawBtn(Gfx g, float[] p, String label, int col, boolean pressed) {
        int x = (int) p[0], y = (int) p[1], r = (int) p[2];
        g.circle(x, y + 2, r, 0xFF000000, 70);
        g.circle(x, y + (pressed ? 1 : 0), r, col, pressed ? 230 : 140);
        g.ring(x, y + (pressed ? 1 : 0), r, 2, 0xFFFFFFFF, pressed ? 230 : 130);
        g.text(label, x - 4, y - 6 + (pressed ? 1 : 0), 0xFFFFFFFF, 2, pressed ? 255 : 200);
    }
}
