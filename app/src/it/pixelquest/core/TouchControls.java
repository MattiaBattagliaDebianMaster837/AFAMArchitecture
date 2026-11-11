package it.pixelquest.core;

import java.util.ArrayList;

/**
 * On-screen virtual controller: floating analog stick on the left (push to the edge to run),
 * A (jump) / B (spin jump) / C (ability) buttons on the right and a pause button.
 * Pointer events arrive in virtual-screen coordinates from the platform thread.
 */
public final class TouchControls {
    private static final int MAXP = 10;
    private final int[] pid = new int[MAXP];
    private final float[] px = new float[MAXP], py = new float[MAXP];
    private final int[] role = new int[MAXP]; // 0 none, 1 stick, 2 buttons, 3 pause
    private final boolean[] used = new boolean[MAXP];
    private final ArrayList<float[]> pendingTaps = new ArrayList<>();

    public int W = 400, H = 224;
    public float ax, ay;
    public boolean a, b, c, pause;
    public boolean visible = true;
    public boolean stickActive;
    public float baseX, baseY, knobX, knobY;
    public final ArrayList<float[]> taps = new ArrayList<>();
    public boolean anyTouch;

    public static final int STICK_R = 30;

    public float defBaseX() {
        return 54;
    }

    public float defBaseY() {
        return H - 52;
    }

    public float[] btnA() {
        return new float[]{W - 38, H - 46, 21};
    }

    public float[] btnB() {
        return new float[]{W - 90, H - 30, 18};
    }

    public float[] btnC() {
        return new float[]{W - 80, H - 86, 18};
    }

    public float[] btnPause() {
        return new float[]{W - 16, 14, 11};
    }

    public synchronized void down(int id, float x, float y) {
        for (int i = 0; i < MAXP; i++)
            if (!used[i]) {
                used[i] = true;
                pid[i] = id;
                px[i] = x;
                py[i] = y;
                float[] p = btnPause();
                if (dist(x, y, p[0], p[1]) < p[2] + 8) role[i] = 3;
                else if (x < W * 0.42f && !hasRole(1)) {
                    role[i] = 1;
                    baseX = Math.max(STICK_R + 4, Math.min(W * 0.42f, x));
                    baseY = Math.max(STICK_R + 4, Math.min(H - STICK_R - 4, y));
                } else role[i] = 2;
                pendingTaps.add(new float[]{x, y});
                return;
            }
    }

    public synchronized void move(int id, float x, float y) {
        for (int i = 0; i < MAXP; i++)
            if (used[i] && pid[i] == id) {
                px[i] = x;
                py[i] = y;
            }
    }

    public synchronized void up(int id) {
        for (int i = 0; i < MAXP; i++)
            if (used[i] && pid[i] == id) used[i] = false;
    }

    public synchronized void cancelAll() {
        for (int i = 0; i < MAXP; i++) used[i] = false;
    }

    private boolean hasRole(int r) {
        for (int i = 0; i < MAXP; i++) if (used[i] && role[i] == r) return true;
        return false;
    }

    private static float dist(float x, float y, float x2, float y2) {
        float dx = x - x2, dy = y - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Called once per game update on the game thread. */
    public synchronized void poll() {
        taps.clear();
        taps.addAll(pendingTaps);
        pendingTaps.clear();
        ax = ay = 0;
        a = b = c = pause = false;
        stickActive = false;
        anyTouch = false;
        float[] A = btnA(), B = btnB(), C = btnC();
        for (int i = 0; i < MAXP; i++) {
            if (!used[i]) continue;
            anyTouch = true;
            if (role[i] == 1) {
                stickActive = true;
                float dx = px[i] - baseX, dy = py[i] - baseY;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float k = d > STICK_R ? STICK_R / d : 1;
                knobX = baseX + dx * k;
                knobY = baseY + dy * k;
                float nx = dx / STICK_R, ny = dy / STICK_R;
                // horizontal axis: dead zone, then walk, then run at the edge
                ax = Math.max(-1, Math.min(1, nx));
                ay = Math.max(-1, Math.min(1, ny));
                if (Math.abs(ax) < 0.18f) ax = 0;
                if (Math.abs(ay) < 0.35f) ay = 0;
            } else if (role[i] == 2) {
                // nearest button within a generous radius (sliding between buttons works)
                float dA = dist(px[i], py[i], A[0], A[1]) / A[2];
                float dB = dist(px[i], py[i], B[0], B[1]) / B[2];
                float dC = dist(px[i], py[i], C[0], C[1]) / C[2];
                float best = Math.min(dA, Math.min(dB, dC));
                if (best < 1.9f) {
                    if (best == dA) a = true;
                    else if (best == dB) b = true;
                    else c = true;
                }
            } else if (role[i] == 3) pause = true;
        }
        if (!stickActive) {
            baseX = knobX = defBaseX();
            baseY = knobY = defBaseY();
        }
    }

    public void draw(Gfx g, String cLabel, boolean showC, boolean showPause) {
        if (!visible) return;
        int ring = 0xFFFFFFFF;
        // stick
        g.circle((int) baseX, (int) baseY, STICK_R + 4, 0xFF101020, 70);
        g.ring((int) baseX, (int) baseY, STICK_R + 4, 2, ring, 110);
        // run threshold marks
        g.ring((int) baseX, (int) baseY, (int) (STICK_R * 0.8f), 1, ring, 40);
        int kx = (int) knobX, ky = (int) knobY;
        g.circle(kx, ky, 13, 0xFFE8E8F0, stickActive ? 190 : 120);
        g.circle(kx - 3, ky - 3, 5, 0xFFFFFFFF, stickActive ? 160 : 90);
        g.ring(kx, ky, 13, 1, 0xFF404060, 140);
        // arrows
        g.text("\u0004", (int) baseX + STICK_R - 5, (int) baseY - 3, 0xFFFFFFFF, 1, 90);
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

    private void drawBtn(Gfx g, float[] p, String label, int col, boolean pressed) {
        int x = (int) p[0], y = (int) p[1], r = (int) p[2];
        g.circle(x, y + 2, r, 0xFF000000, 70);
        g.circle(x, y + (pressed ? 1 : 0), r, col, pressed ? 220 : 140);
        g.ring(x, y + (pressed ? 1 : 0), r, 2, 0xFFFFFFFF, pressed ? 220 : 130);
        g.text(label, x - 4, y - 6 + (pressed ? 1 : 0), 0xFFFFFFFF, 2, pressed ? 255 : 200);
    }
}
