package it.pixelquest.core;

/**
 * Backgrounds of the four prologue worlds, four Sicilian towns. Every level of a world (stage 0..3, 3 = the boss
 * castle) shows a different part of the town:
 * <ul>
 * <li>0 Capo d'Orlando: the seafront with the beach, the harbour, the town centre with the Nebrodi behind, the cape
 * with the sanctuary on top. The Aeolian islands are on the horizon, Stromboli smoking.</li>
 * <li>1 Paceco: the salt pans with their windmills and salt heaps, the town with the Chiesa Madre, the windmills in the
 * fields, the salt pans at sunset. Monte Erice behind.</li>
 * <li>2 Barcellona Pozzo di Gotto under a sudden ice age: the frozen Longano, the basilica of San Sebastiano, the
 * rooftops, the theatre at night. It keeps snowing.</li>
 * <li>3 Naso: the road up the hill at dusk, the old village at night, the castle walls, the castle; Stromboli erupts
 * on the horizon (that is where Lord Magmus came from).</li>
 * </ul>
 * Everything is drawn procedurally (original pixel art, landmarks simplified).
 */
final class TownArt {
    static final int FW = 512, FH = 140, NW = 512, NH = 110;
    /** horizon line inside the far layer for sea views */
    static final int HOR = 44;

    final int w;
    private final Theme th;
    private final Sprite[] far = new Sprite[4], near = new Sprite[4];
    private final int[][] sky = new int[4][];
    private Sprite islands, islandsDusk, capo, erice, ericeDusk, basilica, teatro, nasoTown, nasoNight, castle;

    private static final int[][][] SKY = {
            {{0xFF4AA8F0, 0xFFD2EEFF}, {0xFF52ACEE, 0xFFD8F0FF}, {0xFF5AB4F2, 0xFFE2F4FF}, {0xFF6A7ED8, 0xFFFFC890}},
            {{0xFF62B8EC, 0xFFFFF0C8}, {0xFF6ABCEE, 0xFFFFF4D8}, {0xFF7AC4F0, 0xFFF8E8C0}, {0xFF3A3A8A, 0xFFFF9A50}},
            {{0xFF8AA4C8, 0xFFE8F0FA}, {0xFF6A84B8, 0xFFD4E2F4}, {0xFF4A62A0, 0xFFC0D4EE}, {0xFF141E48, 0xFF5A70B0}},
            {{0xFF2A1A4A, 0xFFFF8A4A}, {0xFF0E0A22, 0xFF4A1E3A}, {0xFF120A20, 0xFF5A2030}, {0xFF14060E, 0xFF9A2A14}}};

    TownArt(Theme th) {
        this.th = th;
        this.w = th.id;
    }

    // =================================================================== primitives (wrapping x for tiled layers)
    static int hash(int x, int y, int s) {
        return Theme.hash(x, y, s);
    }

    static int wrap(int x, int W) {
        int m = x % W;
        return m < 0 ? m + W : m;
    }

    static void ws(Sprite s, int x, int y, int c) {
        s.set(wrap(x, s.w), y, c);
    }

    static void rect(Sprite s, int x, int y, int rw, int rh, int c) {
        for (int yy = y; yy < y + rh; yy++)
            for (int xx = x; xx < x + rw; xx++) ws(s, xx, yy, c);
    }

    static void disc(Sprite s, int cx, int cy, int r, int c) {
        for (int y = -r; y <= r; y++)
            for (int x = -r; x <= r; x++)
                if (x * x + y * y <= r * r + r) ws(s, cx + x, cy + y, c);
    }

    static void ellipse(Sprite s, int cx, int cy, int rx, int ry, int c) {
        for (int y = -ry; y <= ry; y++)
            for (int x = -rx; x <= rx; x++)
                if ((x * x) / (float) (rx * rx + 0.01f) + (y * y) / (float) (ry * ry + 0.01f) <= 1.05f) ws(s, cx + x, cy + y, c);
    }

    static void line(Sprite s, int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            ws(s, x0, y0, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** Isosceles triangle with the apex at (cx, top) and the base on row {@code base}. */
    static void tri(Sprite s, int cx, int top, int base, int halfW, int c) {
        for (int y = top; y <= base; y++) {
            int hw = (int) ((y - top + 1) * halfW / (float) (base - top + 1));
            for (int x = -hw; x <= hw; x++) ws(s, cx + x, y, c);
        }
    }

    static void gradientRows(Sprite s, int y0, int y1, int c0, int c1) {
        for (int y = y0; y < y1; y++) {
            int c = Gfx.mix(c0, c1, (y - y0) / (float) Math.max(1, y1 - y0 - 1));
            for (int x = 0; x < s.w; x++) s.set(x, y, c);
        }
    }

    // =================================================================== drawing
    void draw(Gfx g, float camX, float camY, int levelH, int frame, int stage) {
        int st = Math.max(0, Math.min(3, stage));
        if (far[st] == null) {
            far[st] = makeFar(st);
            near[st] = makeNear(st);
            sky[st] = new int[256];
            for (int y = 0; y < 256; y++) sky[st][y] = Gfx.mix(SKY[w][st][0], SKY[w][st][1], Math.min(1f, y / 190f));
        }
        int H = g.h, W = g.w;
        float bottom = levelH - camY - H; // how far the camera is above the bottom of the level
        int[] rows = sky[st];
        for (int y = 0; y < H; y++) {
            int c = rows[Math.max(0, Math.min(255, (int) (y - bottom * 0.1f)))];
            int o = y * W;
            for (int x = 0; x < W; x++) g.px[o + x] = c;
        }
        int farY = H - FH + (int) (bottom * 0.25f) - 20;
        int horY = farY + HOR;
        celestial(g, st, frame, horY, bottom);
        clouds(g, st, camX, frame, bottom);
        // distant landmarks behind the far layer
        boolean seaView = w == 0 && st != 2 || w == 3;
        if (seaView) {
            Sprite isl = w == 3 ? islandsDusk() : islands();
            // keep Stromboli clear of the cape
            float base = w == 0 ? (st == 0 ? -110 : st == 1 ? 110 : -200) : W * 0.06f;
            int ix = (int) (base - camX * 0.03f);
            g.blit(isl, ix, horY - isl.h + 1);
            volcano(g, ix + 380, horY - isl.h + 1 + 17, frame, w == 3 ? (st == 0 || st == 3 ? 2 : 1) : 0);
        }
        if (w == 1) {
            Sprite e = st == 3 ? ericeDusk() : erice();
            float[] sc = {0.85f, 0.6f, 0.72f, 0.95f}, px = {0.32f, 0.72f, 0.5f, 0.3f};
            g.blitEx(e, W * px[st] - camX * 0.025f, farY + 58 - e.h * sc[st] / 2f, sc[st], sc[st], 0, false, 255, 0);
        }
        layer(g, far[st], camX * 0.2f, farY);
        if (w == 2 && st == 0) landmark(g, basilica(), W * 0.74f - camX * 0.03f, farY + 106, 0.45f); // far away across the river
        // landmarks in front of the far layer
        switch (w) {
            case 0: // the cape: anchored at the sanctuary, its waterline on the horizon
                if (st == 0) place(g, capo(), W * 0.72f - camX * 0.04f, horY, CAPO_AX, CAPO_HY, 0.75f, false);
                else if (st == 1) place(g, capo(), W * 0.2f - camX * 0.03f, horY, CAPO_AX, CAPO_HY, 0.5f, true);
                else if (st == 3) place(g, capo(), W * 0.55f - camX * 0.05f, horY, CAPO_AX, CAPO_HY, 1.1f, false);
                break;
            case 2:
                if (st == 1) landmark(g, basilica(), W * 0.6f - camX * 0.05f, farY + 100, 1.3f);
                else if (st == 2) landmark(g, basilica(), W * 0.5f - camX * 0.03f, farY + 104, 0.8f);
                else if (st == 3) landmark(g, teatro(), W * 0.56f - camX * 0.05f, farY + 104, 1.25f);
                break;
            case 3:
                if (st == 0) landmark(g, nasoTown(), W * 0.76f - camX * 0.03f, farY + 120, 0.62f);
                else if (st == 1) landmark(g, nasoNight(), W * 0.55f - camX * 0.05f, farY + 128, 1.05f);
                else if (st == 2) landmark(g, nasoNight(), W * 0.6f - camX * 0.03f, farY + 112, 0.6f);
                else landmark(g, castle(), W * 0.58f - camX * 0.05f, farY + 126, 1.15f);
                break;
            default:
                break;
        }
        int nearY = H - NH + (int) (bottom * 0.45f) + 4;
        float nearOff = camX * 0.45f;
        layer(g, near[st], nearOff, nearY);
        if (w == 1) windmillSails(g, st, nearOff, nearY, frame);
        overlay(g, st, camX, frame);
    }

    /** Blit {@code s} scaled so that its pixel (ax, ay) lands on (x, y); mirrored when {@code flip}. */
    private static void place(Gfx g, Sprite s, float x, float y, int ax, int ay, float sc, boolean flip) {
        float dx = (s.w / 2f - ax) * sc;
        g.blitEx(s, x + (flip ? -dx : dx), y + (s.h / 2f - ay) * sc, sc, sc, 0, flip, 255, 0);
    }

    /** Blit a landmark sprite with its base centre at (cx, baseY). */
    private static void landmark(Gfx g, Sprite s, float cx, float baseY, float sc) {
        g.blitEx(s, cx, baseY - s.h * sc / 2f, sc, sc, 0, false, 255, 0);
    }

    private static void layer(Gfx g, Sprite s, float off, int y0) {
        int o = wrap((int) off, s.w);
        for (int y = 0; y < s.h; y++) {
            int dy = y0 + y;
            if (dy < 0 || dy >= g.h) continue;
            int row = y * s.w, drow = dy * g.w;
            for (int x = 0; x < g.w; x++) {
                int c = s.px[row + (x + o) % s.w];
                if ((c >>> 24) != 0) g.px[drow + x] = c;
            }
        }
        for (int dy = Math.max(0, y0 + s.h); dy < g.h; dy++)
            for (int x = 0; x < g.w; x++) {
                int c = s.px[(s.h - 1) * s.w + (x + o) % s.w];
                if ((c >>> 24) != 0) g.px[dy * g.w + x] = c;
            }
    }

    private void celestial(Gfx g, int st, int frame, int horY, float bottom) {
        int W = g.w;
        int lift = (int) (bottom * 0.05f);
        boolean night = w == 2 && st == 3 || w == 3 && (st == 1 || st == 2);
        if (night) {
            for (int k = 0; k < 70; k++) {
                int sx = (k * 97 + 13) % W, sy = (k * 41) % (g.h - 60) + lift;
                g.pset(sx, sy, (k + frame / 24) % 9 == 0 ? 0xFF8A8AB0 : 0xFFFFFFFF);
            }
            g.circle(W - 64, 30 + lift, 11, 0xFFF4F0D8, 255);
            g.circle(W - 60, 27 + lift, 10, SKY[w][st][0], 255);
            return;
        }
        if (w == 0 && st == 3) sun(g, (int) (W * 0.2f), horY - 34, 13, 0xFFFFE8A0, 0xFFFFC070);
        else if (w == 1 && st == 3) sun(g, (int) (W * 0.72f), horY + 4, 22, 0xFFFFB050, 0xFFFF7A3A);
        else if (w == 3 && st == 0) sun(g, (int) (W * 0.16f), horY - 6, 15, 0xFFFF9A50, 0xFFFF5A3A);
        else if (w == 1 || w == 0) sun(g, W - 58, 24 + lift, 9, 0xFFFFFAE0, 0xFFFFF0B0);
        else if (w == 2) g.circle(W - 70, 30 + lift, 12, 0xFFF0F4FA, 120); // pale sun behind the snow clouds
    }

    private static void sun(Gfx g, int x, int y, int r, int c, int glow) {
        g.circle(x, y, r + 8, glow, 50);
        g.circle(x, y, r + 4, glow, 80);
        g.circle(x, y, r, c, 255);
    }

    private void clouds(Gfx g, int st, float camX, int frame, float bottom) {
        if (w == 3 && st != 3 || w == 2 && st == 3) return;
        int n = w == 1 ? 3 : w == 2 ? 7 : 5;
        for (int k = 0; k < n; k++) {
            float cx = (k * 137 + frame * 0.05f * (1 + k % 3) - camX * 0.08f) % (g.w + 96);
            if (cx < 0) cx += g.w + 96;
            int cy = 8 + (k * 37) % 46 + (int) (bottom * 0.05f);
            g.blit(th.cloud, (int) cx - 48, cy, false, w == 3 ? 150 : 230, w == 2 ? 0xFFC8D4E6 : 0);
        }
    }

    /** Stromboli: a thin plume of smoke by day; from Naso a real eruption (power 1 small, 2 big). */
    private static void volcano(Gfx g, int sx, int sy, int frame, int power) {
        // smoke drifting to the right
        for (int k = 0; k < 7; k++) {
            int ph = (frame / 3 + k * 18) % 126;
            int px = sx + ph / 3 + (int) (Math.sin((ph + k) * 0.1) * 2), py = sy - 2 - ph / 2;
            int r = 2 + ph / 30;
            g.circle(px, py, r, power > 0 ? 0xFF4A3A44 : 0xFFE8E8EC, Math.max(0, (power > 0 ? 150 : 90) - ph));
        }
        if (power == 0) return;
        g.circle(sx, sy, 5 + (frame / 6) % 2, 0xFFFF7A2A, 110);
        g.circle(sx, sy, 3, 0xFFFFD050, 200);
        int n = power == 2 ? 26 : 12;
        for (int k = 0; k < n; k++) {
            int life = power == 2 ? 70 : 50;
            int t = (frame + k * 11) % life;
            float vx = ((hash(k, 7, 3) & 15) - 7.5f) * (power == 2 ? 0.06f : 0.04f);
            float vy = -(power == 2 ? 1.5f : 1.0f) - (hash(k, 9, 3) & 7) * 0.08f;
            int x = sx + (int) (vx * t), y = sy + (int) (vy * t + 0.035f * t * t);
            if (y > sy + 22) continue;
            g.pset(x, y, t < life / 3 ? 0xFFFFE070 : t < life * 2 / 3 ? 0xFFFF9A3A : 0xFFC0401A);
            if (power == 2 && k % 3 == 0) g.pset(x + 1, y, 0xFFFF7A2A);
        }
    }

    private void windmillSails(Gfx g, int st, float off, int nearY, int frame) {
        int[] mills = millX(st);
        int hubY = millHubY(st), len = st == 3 ? 26 : st == 2 ? 19 : 15;
        int o = wrap((int) off, NW);
        int col = st == 3 ? 0xFF2A1A2A : 0xFFF4EEE0, rib = st == 3 ? 0xFF1A0E1A : 0xFF8A7A60;
        float speed = st == 2 ? 0.05f : 0.03f;
        for (int m = 0; m < mills.length; m++)
            for (int rep = -1; rep <= 1; rep++) {
                int cx = mills[m] - o + rep * NW, cy = nearY + hubY;
                if (cx < -40 || cx > g.w + 40) continue;
                double a0 = frame * speed + m * 0.7;
                for (int s = 0; s < 6; s++) {
                    double a = a0 + s * Math.PI / 3;
                    float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    for (int d = 2; d <= len; d++) {
                        int x = cx + Math.round(dx * d), y = cy + Math.round(dy * d);
                        g.pset(x, y, rib);
                        // triangular canvas on one side of each spar
                        int wdt = d > 4 ? Math.min(4, (d - 2) / 3 + 1) : 0;
                        for (int q = 1; q <= wdt; q++) g.pset(x + Math.round(-dy * q), y + Math.round(dx * q), col);
                    }
                }
                g.fill(cx - 1, cy - 1, 3, 3, 0xFF3A2A20);
            }
    }

    private void overlay(Gfx g, int st, float camX, int frame) {
        if (w == 2) { // snowfall
            int n = st == 3 ? 70 : 110;
            for (int k = 0; k < n; k++) {
                float fx = k * 73.3f + frame * (0.25f + (k % 3) * 0.12f) - camX * (0.3f + (k % 4) * 0.12f);
                float fy = k * 41.7f + frame * (0.45f + (k % 4) * 0.14f);
                int x = wrap((int) (fx + Math.sin((frame + k * 13) * 0.03) * 6), g.w), y = wrap((int) fy, g.h);
                if (k % 5 == 0) g.fillA(x, y, 2, 2, 0xFFFFFFFF, 220);
                else g.psetA(x, y, 0xFFFFFFFF, 200);
            }
        } else if (w == 3 && st >= 2) { // embers and ash rising from the lava
            for (int k = 0; k < 40; k++) {
                float fy = g.h - ((k * 53 + frame * (0.4f + (k % 3) * 0.2f)) % (g.h + 20));
                int x = wrap((int) (k * 61 - camX * 0.6f + Math.sin((frame + k * 7) * 0.05) * 5), g.w);
                g.psetA(x, (int) fy, k % 4 == 0 ? 0xFF9A9090 : 0xFFFF8A3A, 180);
            }
        }
    }

    // =================================================================== far layers
    private Sprite makeFar(int st) {
        Sprite s = new Sprite(FW, FH);
        switch (w) {
            case 0:
                if (st == 2) nebrodi(s, 0xFF7AA8B0, 0xFF4E9A58, 0xFF3E8448);
                else {
                    sea(s, st == 3 ? 0xFFF0C0A0 : 0xFF8AC0E4, st == 3 ? 0xFF3A5A9A : 0xFF2A6EB8);
                    if (st == 0) beach(s);
                    if (st == 1) breakwater(s);
                }
                break;
            case 1:
                if (st == 0 || st == 3) saltPlain(s, st == 3);
                else fields(s, st);
                break;
            case 2:
                peloritani(s, st);
                break;
            default:
                if (st == 0 || st == 3) {
                    sea(s, st == 3 ? 0xFFB04A3A : 0xFFE0906A, st == 3 ? 0xFF2A0E1A : 0xFF2A2A5A);
                    coastHills(s, st == 3 ? 0xFF1A0A14 : 0xFF2A3A2A, 92);
                } else {
                    sea(s, 0xFF3A2A4A, 0xFF120A1E);
                    coastHills(s, 0xFF140E1E, 70);
                    for (int k = 0; k < 40; k++) // lights of the villages along the coast
                        s.set((k * 53 + 7) % FW, 74 + (hash(k, 2, 5) & 15), (k % 3 == 0) ? 0xFFFFD070 : 0xFFFFB050);
                }
                break;
        }
        return s;
    }

    private static void sea(Sprite s, int light, int deep) {
        for (int y = HOR; y < FH; y++) {
            int c = Gfx.mix(light, deep, Math.min(1f, (y - HOR) / 60f));
            for (int x = 0; x < FW; x++) s.set(x, y, c);
        }
        int hi = Gfx.mix(light, 0xFFFFFFFF, 0.5f);
        for (int y = HOR + 3; y < FH; y += 3)
            for (int x = 0; x < FW; x++)
                if ((hash(x / 7, y, 5) & 15) == 0) for (int k = 0; k < 3 + (y - HOR) / 20; k++) s.set(wrap(x + k, FW), y, hi);
    }

    /** Sandy beach with umbrellas along the bottom of the sea view (Capo d'Orlando seafront). */
    private static void beach(Sprite s) {
        int top = 112;
        for (int y = top; y < FH; y++)
            for (int x = 0; x < FW; x++) {
                int c = y == top ? 0xFFFFFFFF : y < top + 3 ? 0xFFD8C08A : ((hash(x, y, 8) & 7) == 0 ? 0xFFE6CC94 : 0xFFF2DCA8);
                s.set(x, y, c);
            }
        int[][] stripes = {{0xFFE8483A, 0xFFFFFFFF}, {0xFF2A6AD0, 0xFFFFFFFF}, {0xFFFFC830, 0xFF2A8AD0}, {0xFF3AA05A, 0xFFFFF0C0}};
        for (int k = 0; k < 11; k++) {
            int x = k * 47 + (hash(k, 4, 2) & 15), y = top + 9 + (k % 2) * 4;
            for (int yy = y - 10; yy < y; yy++) ws(s, x, yy, 0xFF6A5A4A);
            int[] st = stripes[k % stripes.length];
            for (int yy = 0; yy < 5; yy++) {
                int hw = 2 + yy * 2;
                for (int xx = -hw; xx <= hw; xx++) ws(s, x + xx, y - 12 + yy, ((xx + 20) / 3) % 2 == 0 ? st[0] : st[1]);
            }
            rect(s, x + 4, y - 1, 7, 2, 0xFFFFFFFF); // sunbed
        }
    }

    private static void breakwater(Sprite s) {
        for (int x = 60; x < 330; x++)
            for (int y = 64; y < 70; y++) s.set(x, y, y == 64 ? 0xFFC8C4BC : (hash(x / 3, y, 4) & 3) == 0 ? 0xFF7A7670 : 0xFF9A968E);
        rect(s, 326, 50, 5, 14, 0xFFE03A2A); // harbour light
        rect(s, 326, 54, 5, 3, 0xFFFFFFFF);
        rect(s, 325, 48, 7, 2, 0xFF3A3A3A);
    }

    /** Green rolling hills of the Nebrodi with a few white villages. */
    private static void nebrodi(Sprite s, int back, int front, int dark) {
        for (int x = 0; x < FW; x++) {
            double t = x * 2 * Math.PI / FW;
            int hb = 52 + (int) (Math.sin(t * 2 + 1) * 14 + Math.sin(t * 5) * 6);
            int hf = 84 + (int) (Math.sin(t * 3 + 2) * 12 + Math.sin(t * 7 + 1) * 5);
            for (int y = hb; y < FH; y++) s.set(x, y, y < hb + 2 ? Gfx.shade(back, 1.15f) : back);
            for (int y = hf; y < FH; y++) s.set(x, y, (hash(x / 5, y / 4, 6) & 7) == 0 ? dark : front);
        }
        for (int k = 0; k < 9; k++) { // villages
            int vx = k * 57 + 20, vy = 70 + (k % 3) * 8;
            for (int h = 0; h < 6; h++) {
                int hx = vx + (hash(k, h, 9) & 15) - 7, hy = vy + (hash(h, k, 9) & 7) - 3;
                rect(s, hx, hy, 3, 2, 0xFFF4EEE0);
                s.set(hx, hy - 1, 0xFFC0603A);
                s.set(hx + 1, hy - 1, 0xFFC0603A);
            }
        }
    }

    /** Paceco: flat coast with the salt pans reflecting the sky, the sea a thin line far away. */
    private static void saltPlain(Sprite s, boolean dusk) {
        int seaC = dusk ? 0xFFE08A5A : 0xFF5A9AD0;
        for (int y = 50; y < 55; y++) for (int x = 0; x < FW; x++) s.set(x, y, seaC);
        int bank = dusk ? 0xFF5A3A3A : 0xFFC8A878, water = dusk ? 0xFFFFA860 : 0xFFE8D8E8, water2 = dusk ? 0xFFFF8A50 : 0xFFF8C8D8;
        int y = 55, band = 2, k = 0;
        while (y < FH) {
            for (int yy = y; yy < Math.min(FH, y + band); yy++)
                for (int x = 0; x < FW; x++) {
                    boolean pan = k % 2 == 1 && (x + k * 37) % (60 + band * 8) > 3;
                    s.set(x, yy, pan ? ((k / 2) % 2 == 0 ? water : water2) : bank);
                }
            y += band;
            k++;
            if (k % 2 == 0) band++;
        }
    }

    /** Paceco countryside: golden fields, vineyards and farmhouses. */
    private static void fields(Sprite s, int st) {
        int[] cols = {0xFFE8C870, 0xFFC8B060, 0xFF9AB060, 0xFFE0B858};
        for (int y = 56; y < FH; y++)
            for (int x = 0; x < FW; x++) {
                int band = (y - 56) / 6 + (x / (90 + (y - 56))) * 3;
                int c = cols[(band + st) % cols.length];
                if (c == 0xFF9AB060 && x % 4 == 0 && y % 3 == 0) c = 0xFF5A7A3A; // vine rows
                s.set(x, y, c);
            }
        for (int y = 54; y < 57; y++) for (int x = 0; x < FW; x++) s.set(x, y, 0xFFB8C4A8);
        for (int k = 0; k < 7; k++) { // white farmhouses
            int hx = k * 73 + 30, hy = 62 + (k % 3) * 9;
            rect(s, hx, hy, 7, 4, 0xFFF4EEE0);
            rect(s, hx, hy - 1, 7, 1, 0xFFC07048);
            s.set(hx + 2, hy + 2, 0xFF5A4A3A);
        }
    }

    /** Barcellona Pozzo di Gotto in the ice age: snowy Peloritani and the frozen town. */
    private void peloritani(Sprite s, int st) {
        int rock = st == 3 ? 0xFF1E2A50 : 0xFF5A72A8, snow = st == 3 ? 0xFF8A9AC8 : 0xFFF2F6FF;
        for (int x = 0; x < FW; x++) {
            double a = Math.abs(((x * 2) % 170) - 85) / 85.0, b = Math.abs(((x * 3 + 40) % 128) - 64) / 64.0;
            double big = 0.65 + 0.35 * Math.sin(x * 2 * Math.PI / FW * 2 + 1);
            int hf = 22 + (int) ((1 - a) * 50 * big + (1 - b) * 22 + Math.sin(x * 0.11) * 3 + Math.sin(x * 0.37) * 1.5);
            int top = FH - 30 - hf;
            for (int y = Math.max(0, top); y < FH; y++) s.set(x, y, y < top + 9 + (x % 5) ? snow : rock);
        }
        // the frozen plain / town at the foot of the mountains
        int plain = st == 3 ? 0xFF2A3664 : 0xFFDCE6F4;
        for (int y = 104; y < FH; y++) for (int x = 0; x < FW; x++) s.set(x, y, plain);
        if (st >= 1) {
            for (int k = 0; k < 26; k++) {
                int hx = k * 20 + (hash(k, 1, 4) & 7), hh = 6 + (hash(k, 2, 4) & 7);
                int body = st == 3 ? 0xFF283058 : 0xFFE8D8C4;
                rect(s, hx, 104 - hh, 14, hh + 2, body);
                rect(s, hx - 1, 104 - hh - 2, 16, 2, st == 3 ? 0xFF8A9AC8 : 0xFFFFFFFF);
                for (int wx = 2; wx < 12; wx += 4)
                    s.set(hx + wx, 104 - hh + 3, st == 3 ? ((hash(k, wx, 5) & 1) == 0 ? 0xFFFFD070 : 0xFF141A30) : 0xFF6A7A9A);
            }
        }
    }

    private static void coastHills(Sprite s, int c, int top0) {
        for (int x = 0; x < FW; x++) {
            int top = top0 + (int) (Math.sin(x * 0.02) * 10 + Math.sin(x * 0.07 + 1) * 4);
            for (int y = top; y < FH; y++) s.set(x, y, c);
        }
    }

    // =================================================================== near layers
    private Sprite makeNear(int st) {
        Sprite s = new Sprite(NW, NH);
        switch (w) {
            case 0:
                if (st == 0) seafront(s);
                else if (st == 1) harbour(s);
                else if (st == 2) townHouses(s, new int[]{0xFFF2D8B0, 0xFFE8B8A0, 0xFFF0E6CC, 0xFFD8C090, 0xFFE0A890}, false, true);
                else capeRocks(s);
                break;
            case 1:
                if (st == 0) saltPans(s, false);
                else if (st == 1) pacecoTown(s);
                else if (st == 2) windmillFields(s);
                else saltPans(s, true);
                break;
            case 2:
                if (st == 0) frozenRiver(s);
                else if (st == 2) snowyRoofs(s);
                else snowyStreet(s, st == 3);
                break;
            default:
                if (st == 0) olives(s);
                else if (st == 1) borgo(s);
                else if (st == 2) castleWall(s);
                else ruins(s);
                break;
        }
        return s;
    }

    private static void fillBottom(Sprite s, int from, int c) {
        for (int y = from; y < NH; y++) for (int x = 0; x < NW; x++) s.set(x, y, c);
    }

    private static void palm(Sprite s, int x, int base, int top) {
        for (int y = top; y < base; y++) {
            int bend = (int) (Math.sin((base - y) * 0.04) * 3);
            ws(s, x + bend, y, (y % 4 == 0) ? 0xFF5A3A22 : 0xFF8A6A3A);
            ws(s, x + bend + 1, y, 0xFF6A4A2A);
        }
        int tx = x + (int) (Math.sin((base - top) * 0.04) * 3);
        for (int a = 0; a < 7; a++)
            for (int d = 0; d < 14; d++) {
                double ang = a * Math.PI / 3.5 + 0.2;
                int lx = tx + (int) (Math.cos(ang) * d), ly = top + (int) (Math.sin(ang) * d * 0.45 + d * d * 0.035);
                ws(s, lx, ly, 0xFF2E8A3A);
                ws(s, lx, ly + 1, 0xFF1E6A2A);
            }
    }

    private static void lamp(Sprite s, int x, int base, int top, int light) {
        for (int y = top; y < base; y++) ws(s, x, y, 0xFF2A2A30);
        rect(s, x - 3, base - 2, 7, 2, 0xFF2A2A30);
        rect(s, x, top, 4, 1, 0xFF2A2A30);
        rect(s, x + 3, top + 1, 3, 3, light);
    }

    /** Capo d'Orlando seafront: railing, palms and street lamps. */
    private static void seafront(Sprite s) {
        fillBottom(s, 74, 0xFFD8CCB8);
        for (int x = 0; x < NW; x++) {
            ws(s, x, 61, 0xFFF4F2EA);
            ws(s, x, 62, 0xFFB8B4AA);
            ws(s, x, 67, 0xFFF4F2EA);
            if (x % 12 == 0) for (int y = 60; y < 74; y++) { ws(s, x, y, 0xFFF4F2EA); ws(s, x + 1, y, 0xFFB8B4AA); }
        }
        for (int k = 0; k < 8; k++) {
            palm(s, k * 64 + 10, 74, 14 + (k % 3) * 6);
            lamp(s, k * 64 + 42, 74, 30, 0xFFFFF0B0);
        }
    }

    /** The harbour: moored boats and the quay. */
    private static void harbour(Sprite s) {
        fillBottom(s, 72, 0xFF9A9AA0);
        for (int x = 0; x < NW; x++) ws(s, x, 71, 0xFFC8C8CC);
        for (int y = 64; y < 71; y++) for (int x = 0; x < NW; x++) s.set(x, y, (hash(x / 6, y, 3) & 7) == 0 ? 0xFF4A8AD0 : 0xFF2A6AB8);
        int[] stripe = {0xFF2A5AB0, 0xFFC03A3A, 0xFF2A8A5A, 0xFFE0A030};
        for (int k = 0; k < 7; k++) {
            int x = k * 73 + (hash(k, 3, 1) & 15), y = 62 + (k % 2);
            boolean sail = k % 3 != 2;
            for (int xx = 0; xx < 30; xx++) { // hull, tapered
                int depth = xx < 4 ? xx + 2 : xx > 25 ? 31 - xx : 6;
                for (int yy = 0; yy < depth; yy++) ws(s, x + xx, y + yy, yy == 2 ? stripe[k % 4] : 0xFFF8F8F4);
            }
            rect(s, x + 9, y - 4, 10, 4, 0xFFE8E8E0);
            rect(s, x + 11, y - 3, 2, 2, 0xFF3A5A7A);
            if (sail) {
                for (int yy = y - 44; yy < y; yy++) ws(s, x + 15, yy, 0xFF6A6A6A);
                for (int yy = 0; yy < 36; yy++) for (int xx = 1; xx <= yy / 3; xx++) ws(s, x + 15 - xx, y - 42 + yy, 0xFFF4F4EE);
                ws(s, x + 16, y - 44, 0xFFE03A3A);
                ws(s, x + 17, y - 44, 0xFFE03A3A);
            }
        }
        for (int k = 0; k < 16; k++) rect(s, k * 32 + 20, 68, 3, 3, 0xFF3A3A40); // bollards
    }

    /** Rows of Sicilian town houses with shutters and balconies (optionally a church with a bell tower). */
    private static void townHouses(Sprite s, int[] cols, boolean flatRoofs, boolean church) {
        fillBottom(s, 74, 0xFFB8AC98);
        int x = 0, k = 0;
        while (x < NW - 30) {
            int hw = 34 + (hash(k, 3, 2) & 15), floors = 2 + (hash(k, 5, 2) & 1) + (k % 4 == 0 ? 1 : 0);
            int hh = floors * 15 + 6, top = 74 - hh;
            int col = cols[k % cols.length];
            if (church && k == 3) { // church with bell tower
                rect(s, x, 22, 40, 52, 0xFFF0E8D8);
                tri(s, x + 20, 12, 22, 20, 0xFFE8DCC8);
                rect(s, x + 15, 52, 10, 22, 0xFF6A4A2A);
                disc(s, x + 20, 36, 5, 0xFFB8D0E8);
                rect(s, x + 42, 4, 12, 70, 0xFFE8DCC8);
                rect(s, x + 45, 10, 6, 9, 0xFF3A3A44);
                tri(s, x + 48, -2, 4, 7, 0xFFB86A3A);
                x += 58;
                k++;
                continue;
            }
            rect(s, x, top, hw, hh, col);
            if (!flatRoofs) {
                rect(s, x - 1, top - 3, hw + 2, 3, 0xFFB0603A);
                for (int xx = x - 1; xx < x + hw + 1; xx += 3) ws(s, xx, top - 3, 0xFF8A4A2A);
            } else rect(s, x, top - 2, hw, 2, Gfx.shade(col, 0.85f));
            for (int f = 0; f < floors; f++) {
                int fy = top + 4 + f * 15;
                for (int wx = x + 4; wx + 6 < x + hw; wx += 11) {
                    boolean door = f == floors - 1 && wx == x + 4 + 11 * ((hw / 11) / 2);
                    if (door) {
                        rect(s, wx, fy, 7, 15, 0xFF5A3A24);
                        continue;
                    }
                    rect(s, wx, fy, 7, 9, 0xFF3A7A4A); // green shutters
                    ws(s, wx + 3, fy, 0xFF2A5A3A);
                    for (int yy = fy; yy < fy + 9; yy += 2) for (int xx = wx; xx < wx + 7; xx++) if (xx != wx + 3) ws(s, xx, yy, 0xFF2E6A3E);
                    if (f < floors - 1) { // balcony
                        rect(s, wx - 2, fy + 10, 11, 1, 0xFF8A8A8A);
                        for (int bx = wx - 2; bx < wx + 9; bx += 2) for (int yy = fy + 7; yy < fy + 10; yy++) ws(s, bx, yy, 0xFF2A2A2A);
                        rect(s, wx - 2, fy + 7, 11, 1, 0xFF2A2A2A);
                    }
                }
            }
            x += hw + 3;
            if (k % 3 == 1) { // a tree between houses
                for (int y = 58; y < 74; y++) ws(s, x + 4, y, 0xFF5A3A24);
                disc(s, x + 4, 52, 7, 0xFF3A8A44);
                disc(s, x + 2, 49, 4, 0xFF5AAA58);
                x += 12;
            }
            k++;
        }
    }

    /** The cape: rocks, agaves and prickly pears. */
    private static void capeRocks(Sprite s) {
        fillBottom(s, 74, 0xFF8A7A62);
        for (int k = 0; k < 22; k++) {
            int x = k * 24 + (hash(k, 1, 7) & 15), r = 5 + (hash(k, 2, 7) & 7);
            ellipse(s, x, 74 - r / 2, r + 3, r, (k % 2 == 0) ? 0xFF7A6A58 : 0xFF9A8A70);
            ellipse(s, x - 2, 72 - r, r / 2 + 1, 2, 0xFFB8A888);
        }
        for (int k = 0; k < 8; k++) pricklyPear(s, k * 64 + 20, 70);
        for (int k = 0; k < 8; k++) agave(s, k * 64 + 50, 72);
    }

    static void pricklyPear(Sprite s, int x, int base) {
        int pad = 0xFF5A9A4A, padD = 0xFF3A7A3A;
        int[][] pads = {{0, -6}, {-6, -15}, {6, -14}, {-2, -24}, {9, -23}, {-9, -25}};
        for (int[] p : pads) {
            ellipse(s, x + p[0], base + p[1], 4, 5, pad);
            ws(s, x + p[0] - 2, base + p[1] - 1, padD);
            ws(s, x + p[0] + 1, base + p[1] + 2, padD);
            ws(s, x + p[0], base + p[1] - 6, 0xFFE8603A); // fruit
            ws(s, x + p[0] + 2, base + p[1] - 5, 0xFFF0A030);
        }
    }

    static void agave(Sprite s, int x, int base) {
        for (int a = 0; a < 7; a++) {
            double ang = Math.PI + a * Math.PI / 6;
            for (int d = 0; d < 11; d++) ws(s, x + (int) (Math.cos(ang) * d), base + (int) (Math.sin(ang) * d), (d % 3 == 0) ? 0xFF5A8A9A : 0xFF7AAAB0);
        }
    }

    // ---------------------------------------------------------------- Paceco
    static int[] millX(int st) {
        return st == 0 ? new int[]{150, 400} : st == 2 ? new int[]{80, 250, 420} : st == 3 ? new int[]{300} : new int[0];
    }

    static int millHubY(int st) {
        return st == 3 ? 14 : st == 2 ? 24 : 34;
    }

    private static void millTower(Sprite s, int x, int st) {
        int hub = millHubY(st), base = 74;
        int top = hub + 4;
        int bw = st == 3 ? 22 : st == 2 ? 18 : 14;
        int body = st == 3 ? 0xFF3A2434 : 0xFFF0E6D0, sh = st == 3 ? 0xFF2A1A28 : 0xFFD8CCB4;
        for (int y = top; y < base; y++) {
            int hw = (int) (bw / 2f - (base - y) * 0.06f);
            for (int xx = -hw; xx <= hw; xx++) ws(s, x + xx, y, xx > hw / 2 ? sh : body);
        }
        tri(s, x, hub - 4, top, bw / 2 - 1, st == 3 ? 0xFF1E1018 : 0xFF6A4A32);
        rect(s, x - 2, base - 8, 4, 8, st == 3 ? 0xFF140A12 : 0xFF5A3A24);
        rect(s, x - 1, top + 8, 2, 3, st == 3 ? 0xFFFFB050 : 0xFF3A3A44);
    }

    private static void saltHeap(Sprite s, int x, int base, int hw, int h, boolean tiles, boolean dusk) {
        int c = dusk ? 0xFF6A4A5A : 0xFFF8F8F4, sh = dusk ? 0xFF4A3040 : 0xFFD8DCE4;
        for (int y = 0; y < h; y++) {
            int ww = (int) (hw * Math.sqrt((y + 1) / (float) h));
            for (int xx = -ww; xx <= ww; xx++) {
                int col = xx > ww / 3 ? sh : c;
                if (tiles && y > h / 3 && (y % 3 == 0)) col = dusk ? 0xFF7A3A2A : 0xFFB0603A; // terracotta tiles on the heap
                if (dusk && xx < -ww + 2) col = 0xFFFF9A50; // rim lit by the sunset
                ws(s, x + xx, base - h + y, col);
            }
        }
    }

    private static void saltPans(Sprite s, boolean dusk) {
        int bank = dusk ? 0xFF3A2430 : 0xFFB89A6A;
        fillBottom(s, 74, bank);
        int w1 = dusk ? 0xFFFF9A50 : 0xFFF0B8C8, w2 = dusk ? 0xFFFFC070 : 0xFFFFE0E8;
        for (int y = 64; y < 74; y++)
            for (int x = 0; x < NW; x++) {
                boolean b = y == 64 || y == 69 || x % 70 < 2;
                s.set(x, y, b ? bank : ((hash(x / 9, y, 6) & 7) == 0 ? w2 : w1));
            }
        for (int k = 0; k < 9; k++) {
            int x = k * 57 + (hash(k, 4, 6) & 15);
            if (dusk && Math.abs(x - 300) < 30) continue;
            boolean nearMill = false;
            for (int m : millX(dusk ? 3 : 0)) if (Math.abs(x - m) < 22) nearMill = true;
            if (nearMill) continue;
            saltHeap(s, x, 64, 12 + (hash(k, 1, 6) & 7), 13 + (hash(k, 2, 6) & 7), k % 2 == 0, dusk);
        }
        for (int m : millX(dusk ? 3 : 0)) millTower(s, m, dusk ? 3 : 0);
    }

    private static void pacecoTown(Sprite s) {
        fillBottom(s, 74, 0xFFC8B088);
        int[] tufo = {0xFFE8C890, 0xFFDDB878, 0xFFF0D8A8, 0xFFE4C080};
        int x = 0, k = 0;
        while (x < NW - 20) {
            if (k == 4) { // the Chiesa Madre: facade, dome and bell tower
                rect(s, x, 26, 46, 48, 0xFFE8D0A0);
                rect(s, x, 26, 46, 2, 0xFFC8A870);
                tri(s, x + 23, 16, 26, 23, 0xFFE0C490);
                rect(s, x + 18, 52, 10, 22, 0xFF6A4A2A);
                disc(s, x + 23, 38, 4, 0xFFB8D0E8);
                for (int yy = -12; yy <= 0; yy++) for (int xx = -12; xx <= 12; xx++) if (xx * xx + yy * yy * 2 < 150) ws(s, x + 23 + xx, 14 + yy, xx < 0 ? 0xFFD07048 : 0xFFB05A38);
                rect(s, x + 22, -2, 2, 4, 0xFF5A4A3A);
                rect(s, x + 48, 8, 12, 66, 0xFFE0C490);
                rect(s, x + 51, 14, 6, 9, 0xFF3A3A44);
                disc(s, x + 54, 6, 5, 0xFFD07048);
                x += 64;
                k++;
                continue;
            }
            int hw = 26 + (hash(k, 3, 8) & 15), hh = 18 + (hash(k, 5, 8) & 15);
            int col = tufo[k % tufo.length];
            rect(s, x, 74 - hh, hw, hh, col);
            rect(s, x - 1, 74 - hh - 2, hw + 2, 2, Gfx.shade(col, 0.85f)); // flat roof with parapet
            for (int wx = x + 4; wx + 5 < x + hw; wx += 10) {
                rect(s, wx, 74 - hh + 5, 5, 6, 0xFF3A6A4A);
                if (hh > 26) rect(s, wx, 74 - hh + 16, 5, 6, 0xFF3A6A4A);
            }
            for (int yy = 0; yy < 10; yy++) for (int xx = 0; xx < 6; xx++) if (yy > 2 || (xx > 0 && xx < 5)) ws(s, x + hw / 2 - 3 + xx, 64 + yy, 0xFF5A3A24);
            x += hw + 2;
            if (k % 2 == 0) {
                palm(s, x + 4, 74, 28 + (k % 3) * 6);
                x += 12;
            } else { // oleander bush with pink flowers
                disc(s, x + 4, 68, 6, 0xFF3A7A3A);
                for (int f = 0; f < 6; f++) ws(s, x + (hash(k, f, 2) & 7), 63 + (hash(f, k, 2) & 7), 0xFFF07AA8);
                x += 12;
            }
            k++;
        }
    }

    private static void windmillFields(Sprite s) {
        fillBottom(s, 74, 0xFFB8A070);
        for (int y = 58; y < 74; y++)
            for (int x = 0; x < NW; x++) {
                int c = (x + y / 2) % 3 == 0 ? 0xFFC8A040 : 0xFFE8C860;
                if (y == 58) c = (x % 2 == 0) ? 0xFFF0D878 : 0;
                if (c != 0) s.set(x, y, c);
            }
        for (int x = 0; x < NW; x++) // dry stone wall
            for (int y = 68; y < 74; y++) if (x % 170 < 60) ws(s, x, y, ((x / 4 + y / 2) % 2 == 0) ? 0xFF9A9080 : 0xFF7A7268);
        for (int k = 0; k < 6; k++) agave(s, k * 85 + 40, 66);
        for (int m : millX(2)) millTower(s, m, 2);
    }

    // ---------------------------------------------------------------- Barcellona Pozzo di Gotto (frozen)
    private static void snowTop(Sprite s, int x, int y, int wdt) {
        for (int xx = x - 1; xx < x + wdt + 1; xx++) {
            ws(s, xx, y - 1, 0xFFFFFFFF);
            ws(s, xx, y, 0xFFF0F6FF);
            if ((hash(xx, y, 3) & 7) == 0) { ws(s, xx, y + 1, 0xFFBFE6FF); ws(s, xx, y + 2, 0xFFBFE6FF); } // icicles
        }
    }

    private static void bareTree(Sprite s, int x, int base) {
        int c = 0xFF3A2E2A;
        for (int y = base - 26; y < base; y++) ws(s, x, y, c);
        for (int b = 0; b < 5; b++) {
            int by = base - 12 - b * 3, dir = b % 2 == 0 ? 1 : -1;
            for (int d = 0; d < 8 - b; d++) {
                ws(s, x + dir * d, by - d / 2, c);
                if (d % 2 == 0) ws(s, x + dir * d, by - d / 2 - 1, 0xFFFFFFFF);
            }
        }
    }

    private static void frozenRiver(Sprite s) {
        fillBottom(s, 74, 0xFFCFE4F4);
        for (int y = 64; y < 74; y++) // frozen Longano
            for (int x = 0; x < NW; x++) s.set(x, y, ((hash(x / 5, y / 2, 4) & 15) == 0) ? 0xFFFFFFFF : (x + y * 3) % 37 == 0 ? 0xFF8AB8D8 : 0xFFBCDCF4);
        for (int x = 0; x < NW; x++) // embankment wall on the far bank
            for (int y = 50; y < 64; y++) ws(s, x, y, ((x / 6 + (y / 4) % 2) % 2 == 0) ? 0xFF8A8A94 : 0xFF9A9AA6);
        snowTop(s, 0, 50, NW);
        for (int k = 0; k < 2; k++) { // stone bridge with arches
            int bx = k * 256 + 60;
            rect(s, bx, 30, 130, 8, 0xFFA8A2A0);
            snowTop(s, bx, 30, 130);
            for (int a = 0; a < 3; a++) {
                int ax = bx + 8 + a * 42;
                for (int y = 38; y < 64; y++)
                    for (int xx = 0; xx < 36; xx++) {
                        float dx = (xx - 18) / 18f, dy = (y - 38) / 18f;
                        boolean hole = dx * dx + (dy - 1.4f) * (dy - 1.4f) < 1.4f && y > 44;
                        if (!hole) ws(s, ax + xx, y, ((xx / 5 + y / 4) % 2 == 0) ? 0xFFA8A2A0 : 0xFF98928E);
                    }
            }
            for (int x = bx; x < bx + 130; x += 6) for (int y = 24; y < 30; y++) ws(s, x, y, 0xFF3A3A44); // railing
            rect(s, bx, 24, 130, 1, 0xFF3A3A44);
        }
        for (int k = 0; k < 8; k++) bareTree(s, k * 64 + 30, 50);
        for (int k = 0; k < 4; k++) lamp(s, k * 128 + 210, 50, 22, 0xFFFFE8A0);
    }

    private static void snowyStreet(Sprite s, boolean night) {
        int[] cols = night ? new int[]{0xFF3A3A5A, 0xFF44405E, 0xFF34344E} : new int[]{0xFFE8D8C4, 0xFFF0E0D0, 0xFFDCCCB8, 0xFFE8CCB0};
        fillBottom(s, 74, night ? 0xFF8A9AC8 : 0xFFF4F8FF);
        int x = 0, k = 0;
        while (x < NW - 20) {
            int hw = 30 + (hash(k, 3, 5) & 15), hh = 30 + (hash(k, 5, 5) & 15), top = 74 - hh;
            int col = cols[k % cols.length];
            rect(s, x, top, hw, hh, col);
            snowTop(s, x, top, hw);
            for (int wy = top + 6; wy < 66; wy += 12)
                for (int wx = x + 4; wx + 5 < x + hw; wx += 10) {
                    boolean lit = night && (hash(wx, wy, 7) & 3) != 0;
                    rect(s, wx, wy, 5, 7, lit ? 0xFFFFD070 : (night ? 0xFF1A1A2E : 0xFF6A7A9A));
                    snowTop(s, wx, wy + 7, 5);
                }
            x += hw + 3;
            if (k % 2 == 0) {
                lamp(s, x + 3, 74, 34, night ? 0xFFFFE070 : 0xFFFFF0C0);
                x += 10;
            }
            k++;
        }
        for (int x2 = 0; x2 < NW; x2++) for (int y = 70; y < 74; y++) s.set(x2, y, night ? 0xFFA8B8E0 : 0xFFFFFFFF); // snow drifts
    }

    private static void snowyRoofs(Sprite s) {
        fillBottom(s, 74, 0xFFB0603A);
        int x = 0, k = 0;
        while (x < NW) {
            int hw = 40 + (hash(k, 3, 4) & 31), top = 30 + (hash(k, 4, 4) & 31);
            for (int y = top; y < NH; y++)
                for (int xx = 0; xx < hw; xx++) {
                    int slope = Math.abs(xx - hw / 2) / 2;
                    if (y < top + slope) continue;
                    int c = (y - top - slope) < 3 ? 0xFFFFFFFF : ((y + xx / 3) % 3 == 0 ? 0xFF8A4A2A : 0xFFB0603A);
                    ws(s, x + xx, y, c);
                }
            rect(s, x + hw / 3, top - 6, 5, 10, 0xFF8A7A70); // chimney
            snowTop(s, x + hw / 3, top - 6, 5);
            x += hw;
            k++;
        }
    }

    // ---------------------------------------------------------------- Naso
    private static void olives(Sprite s) {
        fillBottom(s, 74, 0xFF4A3A2A);
        for (int x = 0; x < NW; x++) // dry stone wall
            for (int y = 64; y < 74; y++) ws(s, x, y, ((x / 5 + (y / 3) % 2) % 2 == 0) ? 0xFF6A5A50 : 0xFF54483E);
        for (int k = 0; k < 9; k++) {
            int x = k * 57 + (hash(k, 2, 3) & 15);
            for (int y = 46; y < 64; y++) { ws(s, x + (y % 5 == 0 ? 1 : 0), y, 0xFF4A3A30); ws(s, x + 1, y, 0xFF5A4A3A); }
            ellipse(s, x, 40, 13, 8, 0xFF4A5A3A);
            ellipse(s, x - 4, 37, 7, 4, 0xFF6A7A4A);
            ellipse(s, x + 6, 42, 6, 4, 0xFF3A4A2E);
        }
        for (int k = 0; k < 5; k++) pricklyPear(s, k * 100 + 80, 64);
    }

    private static void borgo(Sprite s) {
        fillBottom(s, 74, 0xFF3A2E2A);
        int x = 0, k = 0;
        while (x < NW - 20) {
            int hw = 30 + (hash(k, 3, 6) & 15), hh = 34 + (hash(k, 5, 6) & 15), top = 74 - hh;
            int col = (k % 2 == 0) ? 0xFF5A4A44 : 0xFF6A5648;
            for (int y = top; y < 74; y++)
                for (int xx = 0; xx < hw; xx++) ws(s, x + xx, y, ((xx / 6 + (y / 4) % 2) % 2 == 0) ? col : Gfx.shade(col, 0.88f));
            rect(s, x - 1, top - 2, hw + 2, 2, 0xFF4A2A22);
            for (int wy = top + 6; wy < 62; wy += 13)
                for (int wx = x + 5; wx + 5 < x + hw; wx += 11) {
                    boolean lit = (hash(wx, wy, 2) & 3) != 0;
                    rect(s, wx, wy, 5, 7, lit ? 0xFFFFC860 : 0xFF140E12);
                    if (lit) rect(s, wx, wy + 7, 5, 1, 0xFF8A6A3A);
                }
            for (int y = 60; y < 74; y++) for (int xx = 0; xx < 9; xx++) { // arched door
                float dx = (xx - 4) / 4.5f, dy = (y - 64) / 4.5f;
                if (y >= 64 || dx * dx + dy * dy < 1) ws(s, x + hw / 2 - 4 + xx, y, 0xFF1A1014);
            }
            rect(s, x + hw - 4, top + 14, 3, 3, 0xFFFFD050); // lantern
            x += hw + 2;
            k++;
        }
    }

    private static void castleWall(Sprite s) {
        fillBottom(s, 74, 0xFF2A2026);
        for (int y = 24; y < 74; y++)
            for (int x = 0; x < NW; x++) {
                boolean merlon = y < 32 && (x % 16) >= 10;
                if (merlon) continue;
                int c = (((x + (y / 6) % 2 * 6) / 12 + y / 6) % 2 == 0) ? 0xFF4A3C40 : 0xFF3E3236;
                if ((x % 12 == 0) || (y % 6 == 0)) c = 0xFF2A2026;
                s.set(x, y, c);
            }
        for (int k = 0; k < 8; k++) {
            rect(s, k * 64 + 30, 44, 2, 10, 0xFF0E080C); // arrow slit
            rect(s, k * 64 + 50, 48, 2, 6, 0xFF5A3A2A); // torch
            disc(s, k * 64 + 51, 46, 2, 0xFFFF9A3A);
            ws(s, k * 64 + 51, 44, 0xFFFFE070);
        }
    }

    private static void ruins(Sprite s) {
        fillBottom(s, 74, 0xFF1E1016);
        for (int k = 0; k < 10; k++) {
            int x = k * 52 + (hash(k, 1, 9) & 15), h = 14 + (hash(k, 2, 9) & 31), wdt = 10 + (hash(k, 3, 9) & 15);
            for (int y = 74 - h; y < 74; y++)
                for (int xx = 0; xx < wdt; xx++) {
                    int broken = (hash(k, xx, 4) & 7);
                    if (y < 74 - h + broken) continue;
                    int c = (xx < 2) ? 0xFFC0603A : (((xx / 4) + (y / 4)) % 2 == 0 ? 0xFF2E1E26 : 0xFF261820); // rims lit by the lava glow
                    ws(s, x + xx, y, c);
                }
        }
        for (int x = 0; x < NW; x++) if ((hash(x / 3, 1, 5) & 7) == 0) for (int y = 70; y < 74; y++) ws(s, x, y, (y % 2 == 0) ? 0xFFFF7A2A : 0xFFFFC040);
    }

    // =================================================================== landmarks
    /** Aeolian islands as seen from the north coast: Alicudi, Filicudi, Salina, Lipari, Vulcano, Panarea, Stromboli. */
    private Sprite islands() {
        if (islands == null) islands = makeIslands(0xFF8AA6C4, 0xFF9AB4CE);
        return islands;
    }

    private Sprite islandsDusk() {
        if (islandsDusk == null) {
            islandsDusk = makeIslands(0xFF3A2440, 0xFF4A2E4A);
            // lava running down the Sciara del Fuoco on Stromboli
            for (int d = 0; d < 16; d++) {
                islandsDusk.set(380 + 2 + d / 2, 18 + d, d % 3 == 0 ? 0xFFFFD050 : 0xFFFF6A2A);
                islandsDusk.set(381 + 2 + d / 2, 18 + d, 0xFFC0401A);
            }
        }
        return islandsDusk;
    }

    private static Sprite makeIslands(int c, int hi) {
        Sprite s = new Sprite(420, 40);
        int[][] cones = {{20, 14, 15}, {60, 20, 11}, {72, 10, 8}, {112, 22, 21}, {142, 20, 19}, {200, 30, 13}, {214, 16, 10},
                {258, 22, 12}, {322, 10, 5}, {380, 26, 22}};
        for (int[] k : cones)
            for (int y = 0; y < k[2]; y++) {
                int hw = (int) (k[1] * Math.pow((y + 1) / (float) k[2], 0.8));
                for (int x = -hw; x <= hw; x++) s.set(k[0] + x, 39 - k[2] + 1 + y, x < -hw / 3 && y < k[2] / 2 ? hi : c);
            }
        // Vulcano's crater notch
        s.set(258, 39 - 12 + 1, 0);
        s.set(259, 39 - 12 + 1, 0);
        return s;
    }

    /** sanctuary x and waterline row inside the cape sprite */
    static final int CAPO_AX = 60, CAPO_HY = 70;

    /**
     * The cape of Capo d'Orlando: the rocky promontory with the sanctuary on top and the ruins of the old castle.
     * Below the waterline the land comes towards the viewer (the town along the bay), so it reads as a promontory
     * attached to the coast and not as an island.
     */
    Sprite capo() {
        if (capo != null) return capo;
        int W = 320, H = 210, HY = CAPO_HY; // tall: the coast recedes to nothing before the bottom edge
        Sprite s = new Sprite(W, H);
        for (int x = 0; x < W; x++) {
            int h;
            if (x < 22) h = x * 2 + 2;
            else if (x < 96) h = 44 + (int) (Math.sin((x - 22) / 74.0 * Math.PI) * 12);
            else if (x < 140) h = 44 - (int) ((x - 96) * 0.78f);
            else h = 10 + (int) (Math.sin(x * 0.07) * 2);
            for (int y = HY - h; y < HY; y++) {
                int c = 0xFF8A7864;
                if ((y + x / 3) % 7 == 0) c = 0xFF6E5E4E; // rock strata
                if (y < HY - h + 4 + (hash(x / 4, 1, 3) & 3) || x >= 140) c = (hash(x / 3, y, 5) & 1) == 0 ? 0xFF5A7A3A : 0xFF6E8A44; // scrub
                if (x < 22) c = Gfx.shade(c, 0.78f); // cliff in shadow
                s.set(x, y, c);
            }
        }
        // below the waterline: the coast curving towards the viewer, with the town along it
        for (int y = HY; y < H; y++) {
            int d = y - HY, xl = 4 + (int) (d * 2.4f);
            for (int x = xl; x < W; x++) {
                int c = (hash(x / 3, y / 2, 8) & 3) == 0 ? 0xFF4E6E34 : 0xFF6A8A44;
                if (x < xl + 2) c = 0xFFE8E0C8; // little beach along the water
                s.set(x, y, c);
            }
        }
        for (int k = 0; k < 70; k++) { // houses of the town along the bay and on the low coast
            int hx = 150 + (hash(k, 1, 4) & 127) + (k % 2) * 40, hy = HY - 12 + (hash(k, 2, 4) & 31);
            int xl = 4 + (int) (Math.max(0, hy - HY) * 2.4f);
            if (hx < xl + 4) continue;
            int[] cols = {0xFFF4EEE0, 0xFFF0D8B0, 0xFFE8C8A8, 0xFFF8F4EC};
            for (int y = hy; y < hy + 4; y++) for (int x = hx; x < hx + 5; x++) s.set(x, y, cols[k % 4]);
            for (int x = hx; x < hx + 5; x++) s.set(x, hy - 1, 0xFFC0603A);
            s.set(hx + 1, hy + 2, 0xFF5A6A7A);
        }
        // sanctuary: white church, red roof, bell tower
        int bx = CAPO_AX - 11, by = HY - 54;
        for (int y = by; y < by + 13; y++) for (int x = bx; x < bx + 22; x++) s.set(x, y, x > bx + 17 ? 0xFFDCD6C8 : 0xFFF6F2E8);
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 22; x++) if (x >= -1 + (5 - y) / 2 && x <= 22 - (5 - y) / 2) s.set(bx + x, by - 6 + y, 0xFFB8503A);
        for (int y = by - 14; y < by + 13; y++) for (int x = bx - 6; x < bx; x++) s.set(x, y, 0xFFEEE8DC);
        for (int y = by - 19; y < by - 14; y++) for (int x = bx - 6 + (by - 14 - y) / 2; x < bx - (by - 14 - y) / 2; x++) s.set(x, y, 0xFFB8503A);
        s.set(bx - 3, by - 11, 0xFF3A3A44);
        s.set(bx - 4, by - 11, 0xFF3A3A44);
        for (int y = by + 6; y < by + 13; y++) for (int x = bx + 9; x < bx + 13; x++) s.set(x, y, 0xFF6A4A2A);
        // ruins of the castle
        int[][] walls = {{bx + 30, 10, 8}, {bx + 40, 6, 13}, {bx + 24, 4, 5}};
        for (int[] wl : walls)
            for (int y = by + 13 - wl[2]; y < by + 14; y++)
                for (int x = wl[0]; x < wl[0] + wl[1]; x++) s.set(x, y, ((x + y) % 4 == 0) ? 0xFF8A8070 : 0xFFA89E8A);
        capo = s.outlined(0xFF3A3428);
        return capo;
    }

    /** Monte Erice with the medieval town on the summit. */
    Sprite erice() {
        if (erice == null) erice = makeErice(0xFF7A9AB8, 0xFF8EAEC8, 0xFFE8E4DA);
        return erice;
    }

    private Sprite ericeDusk() {
        if (ericeDusk == null) ericeDusk = makeErice(0xFF4A3A6A, 0xFF5A4478, 0xFFFFC070);
        return ericeDusk;
    }

    private static Sprite makeErice(int c, int hi, int town) {
        int W = 240, H = 90;
        Sprite s = new Sprite(W, H);
        for (int x = 0; x < W; x++) {
            double u = (x - 120) / 120.0;
            int h = (int) (78 * Math.max(0, 1 - Math.pow(Math.abs(u), 1.6)) + Math.sin(x * 0.2) * 1.5);
            if (Math.abs(u) < 0.22) h = Math.max(h, 74); // flat summit
            for (int y = H - h; y < H; y++) s.set(x, y, u < -0.1 && y < H - h + 12 ? hi : c);
        }
        for (int k = 0; k < 12; k++) { // the town on top
            int hx = 98 + k * 4, hy = H - 76 - (hash(k, 1, 2) & 3);
            for (int y = hy; y < H - 72; y++) for (int x = hx; x < hx + 3; x++) s.set(x, y, town);
        }
        for (int y = H - 84; y < H - 72; y++) for (int x = 96; x < 100; x++) s.set(x, y, town); // castle towers
        for (int y = H - 82; y < H - 72; y++) for (int x = 142; x < 145; x++) s.set(x, y, town);
        return s;
    }

    /** Basilica of San Sebastiano under the snow: dome, two bell towers, portal, icicles. */
    Sprite basilica() {
        if (basilica != null) return basilica;
        int W = 110, H = 100;
        Sprite s = new Sprite(W, H);
        int stone = 0xFFE4D8C4, stoneD = 0xFFC8BCA6, dark = 0xFF5A4A44;
        for (int y = 46; y < H; y++) for (int x = 20; x < 90; x++) s.set(x, y, x > 80 ? stoneD : stone); // body
        for (int y = 40; y < 46; y++) for (int x = 18 + (46 - y); x < 92 - (46 - y); x++) s.set(x, y, stoneD); // pediment
        for (int y = 30; y < 46; y++) for (int x = 41; x < 69; x++) s.set(x, y, stone); // drum
        for (int y = 32; y < 44; y += 4) for (int x = 44; x < 66; x += 5) { s.set(x, y, dark); s.set(x, y + 1, dark); }
        for (int y = 8; y < 31; y++) { // dome
            int hw = (int) (15 * Math.sqrt(1 - Math.pow((30 - y) / 23.0, 2)));
            for (int x = -hw; x <= hw; x++) s.set(55 + x, y, x < -hw / 3 ? 0xFFF4F8FF : x % 5 == 0 ? 0xFF9AA4B4 : 0xFFB8C2D0);
        }
        for (int y = 2; y < 9; y++) s.set(55, y, 0xFF5A5A66);
        for (int x = 53; x < 58; x++) s.set(x, 4, 0xFF5A5A66);
        for (int t = 0; t < 2; t++) { // bell towers
            int tx = t == 0 ? 6 : 88;
            for (int y = 26; y < H; y++) for (int x = tx; x < tx + 16; x++) s.set(x, y, x > tx + 12 ? stoneD : stone);
            for (int y = 32; y < 42; y++) for (int x = tx + 5; x < tx + 11; x++) s.set(x, y, dark);
            for (int y = 16; y < 26; y++) { int hw = (26 - y) * 8 / 10; for (int x = tx + 8 - hw; x <= tx + 8 + hw; x++) s.set(x, y, 0xFFB8C2D0); }
            for (int x = tx - 1; x < tx + 17; x++) { s.set(x, 25, 0xFFFFFFFF); s.set(x, 26, 0xFFFFFFFF); }
        }
        for (int y = 74; y < H; y++) for (int x = 46; x < 64; x++) { // portal
            float dx = (x - 55) / 9f, dy = (y - 80) / 8f;
            if (y >= 80 || dx * dx + dy * dy < 1) s.set(x, y, 0xFF3A2A24);
        }
        for (int y = 52; y < 64; y++) for (int x = 50; x < 60; x++) { float dx = (x - 54.5f) / 5f, dy = (y - 57.5f) / 5.5f; if (dx * dx + dy * dy < 1) s.set(x, y, 0xFF6A8AB0); }
        for (int x = 18; x < 92; x++) { s.set(x, 46, 0xFFFFFFFF); s.set(x, 47, 0xFFF0F6FF); if (x % 4 == 1) { s.set(x, 48, 0xFFBFE6FF); s.set(x, 49, 0xFFBFE6FF); } }
        for (int y = 8; y < 14; y++) for (int x = 48; x < 63; x++) if (s.get(x, y) != 0) s.set(x, y, 0xFFFFFFFF); // snow on the dome
        basilica = s.outlined(0xFF3A3440);
        return basilica;
    }

    /** The town theatre, neoclassical facade, at night under the snow. */
    private Sprite teatro() {
        if (teatro != null) return teatro;
        int W = 120, H = 76;
        Sprite s = new Sprite(W, H);
        int wall = 0xFF8A6A6A, col = 0xFFD8D0C8, colD = 0xFFA8A0A0;
        for (int y = 22; y < H; y++) for (int x = 4; x < 116; x++) s.set(x, y, wall);
        for (int y = 8; y < 22; y++) for (int x = 10 + (22 - y) * 3; x < 110 - (22 - y) * 3; x++) s.set(x, y, 0xFFB8A8A0); // pediment
        for (int x = 4; x < 116; x++) { s.set(x, 22, 0xFFFFFFFF); s.set(x, 23, 0xFFE8EEF8); }
        for (int c = 0; c < 6; c++) // columns
            for (int y = 26; y < H - 6; y++) for (int x = 14 + c * 18; x < 20 + c * 18; x++) s.set(x, y, x == 19 + c * 18 ? colD : col);
        Theme.spriteText(s, "MANDANICI", 33, 12, 0xFF3A2A2A);
        for (int c = 0; c < 5; c++) { // lit doors between the columns
            int dx = 22 + c * 18;
            for (int y = 50; y < H - 6; y++) for (int x = dx; x < dx + 8; x++) s.set(x, y, 0xFFFFC860);
            for (int y = 32; y < 42; y++) for (int x = dx + 1; x < dx + 7; x++) s.set(x, y, 0xFFFFD880);
        }
        for (int st = 0; st < 3; st++) for (int x = 2 - st * 2; x < 118 + st * 2; x++) s.set(x, H - 6 + st * 2, 0xFFE8EEF8); // snowy steps
        teatro = s.outlined(0xFF1A1220);
        return teatro;
    }

    Sprite nasoTown() {
        if (nasoTown == null) nasoTown = makeNaso(false);
        return nasoTown;
    }

    private Sprite nasoNight() {
        if (nasoNight == null) nasoNight = makeNaso(true);
        return nasoNight;
    }

    /** Naso on its hill: houses stacked along the slope, two bell towers, the castle on the top. */
    private static Sprite makeNaso(boolean night) {
        int W = 170, H = 100;
        Sprite s = new Sprite(W, H);
        int hill = night ? 0xFF1E1A26 : 0xFF3A4A2E, hillHi = night ? 0xFF2A2232 : 0xFF4E5E38;
        for (int x = 0; x < W; x++) {
            double u = (x - 85) / 85.0;
            int h = (int) (70 * Math.max(0, 1 - u * u * 0.9) + 8);
            for (int y = H - h; y < H; y++) s.set(x, y, y < H - h + 3 ? hillHi : hill);
        }
        int[] houseC = night ? new int[]{0xFF4A3E3A, 0xFF54463E, 0xFF3E3434} : new int[]{0xFFE8D4B0, 0xFFD8B890, 0xFFF0E0C4, 0xFFC8A880};
        for (int row = 0; row < 7; row++) { // houses stacked in rows along the slope
            int ry = H - 70 + 4 + row * 7;
            int k = row * 31;
            for (int hx = 24 + (row % 2) * 4; hx < W - 24; k++) {
                int hw = 6 + (hash(k, 3, 3) & 3);
                double u = (hx + hw / 2 - 85) / 85.0;
                int ground = H - (int) (70 * Math.max(0, 1 - u * u * 0.9) + 8);
                if (ground <= ry - 2 && (hash(k, 6, 3) & 7) != 0) {
                    int hh = 6;
                    for (int y = ry - hh; y < ry; y++) for (int x = hx; x < hx + hw; x++) s.set(x, y, houseC[k % houseC.length]);
                    for (int x = hx; x < hx + hw; x++) s.set(x, ry - hh, night ? 0xFF3A2424 : 0xFFB0603A);
                    s.set(hx + 2, ry - 3, night ? ((hash(k, 5, 3) & 1) == 0 ? 0xFFFFC860 : 0xFF120C10) : 0xFF4A3A2A);
                }
                hx += hw + 1;
            }
        }
        int bell = night ? 0xFF5A4A44 : 0xFFE8DCC8;
        for (int t = 0; t < 2; t++) {
            int tx = t == 0 ? 58 : 108, ty = H - 74 + (t == 1 ? 6 : 0);
            for (int y = ty; y < ty + 16; y++) for (int x = tx; x < tx + 5; x++) s.set(x, y, bell);
            s.set(tx + 2, ty + 3, night ? 0xFFFFC860 : 0xFF3A3A44);
            for (int y = ty - 4; y < ty; y++) for (int x = tx + (ty - y) / 2; x < tx + 5 - (ty - y) / 2; x++) s.set(x, y, night ? 0xFF3A2424 : 0xFFB0603A);
        }
        int cs = night ? 0xFF3A3036 : 0xFF9A9080; // castle ruins on the summit
        for (int y = H - 84; y < H - 74; y++) for (int x = 76; x < 96; x++) if (!(y < H - 81 && (x % 4) >= 2)) s.set(x, y, cs);
        for (int y = H - 90; y < H - 74; y++) for (int x = 82; x < 89; x++) if (!(y < H - 87 && x == 85)) s.set(x, y, cs);
        return s.outlined(night ? 0xFF0A0610 : 0xFF2A2A1E);
    }

    /** Naso castle, ruined keep and walls, against the red sky. */
    private Sprite castle() {
        if (castle != null) return castle;
        int W = 140, H = 96;
        Sprite s = new Sprite(W, H);
        int st = 0xFF3A2C30, stD = 0xFF2A1E24, rim = 0xFFC0603A;
        for (int y = 30; y < H; y++) for (int x = 40; x < 80; x++) { // keep
            int broken = (int) (Math.abs(Math.sin(x * 0.7)) * 6);
            if (y < 30 + broken) continue;
            s.set(x, y, ((x / 5 + y / 5) % 2 == 0) ? st : stD);
        }
        for (int y = 40; y < H; y++) for (int x = 92; x < 120; x++) { // round tower
            float dx = (x - 106) / 14f;
            if (y < 40 + (int) (dx * dx * 4)) continue;
            s.set(x, y, x < 96 ? rim : ((x / 4 + y / 4) % 2 == 0 ? st : stD));
        }
        for (int x = 92; x < 120; x += 6) for (int y = 34; y < 40; y++) for (int xx = 0; xx < 3; xx++) s.set(x + xx, y, st); // merlons
        for (int y = 58; y < H; y++) for (int x = 0; x < W; x++) { // curtain walls
            if (x >= 40 && x < 80 || x >= 92 && x < 120) continue;
            if (y < 62 && (x % 8) >= 4) continue;
            s.set(x, y, x % 31 == 0 ? rim : ((x / 6 + y / 4) % 2 == 0 ? st : stD));
        }
        for (int y = 74; y < H; y++) for (int x = 52; x < 68; x++) { float dx = (x - 60) / 8f, dy = (y - 80) / 7f; if (y >= 80 || dx * dx + dy * dy < 1) s.set(x, y, 0xFF120A0E); }
        for (int y = 42; y < 50; y++) { s.set(50, y, 0xFFFF9A3A); s.set(70, y, 0xFFFF9A3A); } // glowing windows
        for (int y = 30; y < H; y++) s.set(40, y, rim);
        castle = s.outlined(0xFF0E0608);
        return castle;
    }
}
