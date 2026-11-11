package it.pixelquest.core;

/** Per-world procedural tiles, decorations and parallax backgrounds. */
public final class Theme {
    public static final int T = 16;
    public final int id;
    public final String name;
    public Sprite[] ground = new Sprite[16];
    public Sprite stone, brick, used, spike, ice, gate, semiL, semiM, semiR, lavaBody;
    public Sprite[] qblock = new Sprite[4], lavaTop = new Sprite[2];
    public Sprite far, near, cloud;
    public Sprite[] decor;
    public int skyTop, skyBot, fogColor;
    public int[] skyRows;

    static Theme[] cache = new Theme[4];

    public static Theme get(int id) {
        if (cache[id] == null) cache[id] = new Theme(id);
        return cache[id];
    }

    static int hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    static float rnd(int x, int y, int s) {
        return (hash(x, y, s) & 0xFFFF) / 65535f;
    }

    private Theme(int id) {
        this.id = id;
        int dirt, dirtD, dirtL, cap, capD, capL, brickC, stoneC;
        switch (id) {
            default:
            case 0:
                name = "PRATI PIXEL";
                dirt = 0xFFB4703C; dirtD = 0xFF8A4E28; dirtL = 0xFFD08E52;
                cap = 0xFF5BC24A; capD = 0xFF2E8A34; capL = 0xFF9EE878;
                brickC = 0xFFC0603A; stoneC = 0xFFA89C8C;
                skyTop = 0xFF5AB8F0; skyBot = 0xFFCDEEFF; fogColor = 0xFF9FD8F8;
                break;
            case 1:
                name = "DESERTO DORATO";
                dirt = 0xFFE0A858; dirtD = 0xFFB87A38; dirtL = 0xFFF2C878;
                cap = 0xFFF8DC98; capD = 0xFFD8B070; capL = 0xFFFFF2C8;
                brickC = 0xFFD89A50; stoneC = 0xFFC8A070;
                skyTop = 0xFFF08850; skyBot = 0xFFFFE6A8; fogColor = 0xFFF8C090;
                break;
            case 2:
                name = "PICCHI GHIACCIATI";
                dirt = 0xFF6A88B4; dirtD = 0xFF4A6290; dirtL = 0xFF8EAAD2;
                cap = 0xFFF2F8FF; capD = 0xFFBCD4EE; capL = 0xFFFFFFFF;
                brickC = 0xFF7AA0D0; stoneC = 0xFF9AB4D4;
                skyTop = 0xFF1E2A64; skyBot = 0xFF8AA8E0; fogColor = 0xFF5070B0;
                break;
            case 3:
                name = "FORTEZZA DI LAVA";
                dirt = 0xFF4A3A4C; dirtD = 0xFF30243A; dirtL = 0xFF6A5468;
                cap = 0xFF7A5A6A; capD = 0xFF4A3444; capL = 0xFFB07A6A;
                brickC = 0xFF6A4A5A; stoneC = 0xFF6A6478;
                skyTop = 0xFF14060E; skyBot = 0xFF7A2014; fogColor = 0xFF4A1414;
                break;
        }
        for (int m = 0; m < 16; m++) ground[m] = makeGround(m, dirt, dirtD, dirtL, cap, capD, capL);
        stone = makeStone(stoneC);
        brick = makeBrick(brickC);
        used = makeUsed();
        for (int i = 0; i < 4; i++) qblock[i] = makeQ(i);
        spike = makeSpike();
        ice = makeIce();
        gate = makeGate();
        makeSemi();
        lavaTop[0] = makeLava(0, true);
        lavaTop[1] = makeLava(1, true);
        lavaBody = makeLava(0, false);
        makeBackground();
        makeDecor();
    }

    // ------------------------------------------------------------------ tiles
    private Sprite makeGround(int mask, int dirt, int dirtD, int dirtL, int cap, int capD, int capL) {
        boolean up = (mask & 1) != 0, right = (mask & 2) != 0, down = (mask & 4) != 0, left = (mask & 8) != 0;
        Sprite s = new Sprite(T, T);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                float r = rnd(x, y, 11 + mask);
                int c = dirt;
                if (r < 0.10f) c = dirtD;
                else if (r > 0.93f) c = dirtL;
                // pebbles
                if (((x / 4 + y / 5) * 7 + mask) % 9 == 0 && (x % 4 == 1) && (y % 5 == 2)) c = dirtD;
                s.set(x, y, c);
            }
        if (id == 3) { // lava world: glowing cracks
            for (int i = 0; i < 6; i++) {
                int cx = (hash(i, mask, 3) & 15), cy = (hash(i, mask, 5) & 15);
                s.set(cx, cy, 0xFFE0502A);
                s.set(Math.min(15, cx + 1), cy, 0xFF9A2A1A);
            }
        }
        if (!left) for (int y = 0; y < T; y++) s.set(0, y, dirtD);
        if (!right) for (int y = 0; y < T; y++) s.set(15, y, dirtD);
        if (!down) for (int x = 0; x < T; x++) s.set(x, 15, dirtD);
        if (!up) {
            for (int x = 0; x < T; x++) {
                int depth = 4 + (int) (Math.sin(x * 1.3 + mask) * 1.2 + 0.5f);
                if (id == 1) depth = 3 + (x % 5 == 0 ? 1 : 0);
                for (int y = 0; y < depth; y++) s.set(x, y, y == 0 ? capL : y == depth - 1 ? capD : cap);
                if (id == 0 && x % 3 == 1) s.set(x, depth, capD); // grass blades hang
                if (id == 2 && x % 4 == 2) { // icicle drip
                    s.set(x, depth, capD);
                    s.set(x, depth + 1, capD);
                }
            }
            if (!left) {
                s.set(0, 0, 0);
                s.set(0, 1, capD);
            }
            if (!right) {
                s.set(15, 0, 0);
                s.set(15, 1, capD);
            }
        }
        return s;
    }

    private Sprite makeStone(int c) {
        Sprite s = new Sprite(T, T);
        int hi = Gfx.shade(c, 1.25f), lo = Gfx.shade(c, 0.65f), mid = Gfx.shade(c, 0.9f);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int col = rnd(x, y, 77) < 0.12f ? mid : c;
                if (x == 0 || y == 0) col = hi;
                if (x == 15 || y == 15) col = lo;
                if ((x == 1 && y < 15) || (y == 1 && x < 15)) col = Gfx.mix(c, hi, 0.5f);
                s.set(x, y, col);
            }
        return s;
    }

    private Sprite makeBrick(int c) {
        Sprite s = new Sprite(T, T);
        int mortar = Gfx.shade(c, 0.55f), hi = Gfx.shade(c, 1.2f);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int row = y / 4;
                int off = (row % 2) * 4;
                boolean m = y % 4 == 3 || (x + off) % 8 == 7;
                int col = m ? mortar : (y % 4 == 0 ? hi : c);
                if (!m && rnd(x, y, 5) < 0.08f) col = Gfx.shade(c, 0.85f);
                s.set(x, y, col);
            }
        return s;
    }

    private Sprite makeQ(int f) {
        Sprite s = new Sprite(T, T);
        int base = 0xFFF2B630, hi = 0xFFFFE07A, lo = 0xFFB0700E;
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int col = base;
                if (x == 0 || y == 0) col = hi;
                if (x == 15 || y == 15) col = lo;
                if ((x == 2 || x == 13) && (y == 2 || y == 13)) col = lo; // rivets
                s.set(x, y, col);
            }
        // star emblem
        int[] star = {
                0b0000010000, 0b0000111000, 0b1111111111, 0b0111111110, 0b0011111100,
                0b0011101100, 0b0110000110, 0b0100000010};
        int shine = f == 1 ? 0xFFFFFFFF : f == 2 ? 0xFFFFF6D0 : 0xFFFFF0B0;
        for (int y = 0; y < star.length; y++)
            for (int x = 0; x < 10; x++)
                if ((star[y] >> (9 - x) & 1) != 0) {
                    s.set(3 + x, 4 + y, shine);
                    if (y + 5 < 16) {
                        int below = s.get(4 + x, 5 + y);
                        if (below == base) s.set(4 + x, 5 + y, lo);
                    }
                }
        return s;
    }

    private Sprite makeUsed() {
        Sprite s = new Sprite(T, T);
        int c = 0xFF9A6A4A, hi = 0xFFB88A66, lo = 0xFF6A4430;
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int col = c;
                if (x == 0 || y == 0) col = hi;
                if (x == 15 || y == 15) col = lo;
                if ((x == 2 || x == 13) && (y == 2 || y == 13)) col = lo;
                s.set(x, y, col);
            }
        return s;
    }

    private Sprite makeSpike() {
        Sprite s = new Sprite(T, T);
        for (int k = 0; k < 3; k++) {
            int cx = 2 + k * 5;
            for (int y = 4; y < 16; y++) {
                int hw = (y - 4) / 4;
                for (int x = cx - hw; x <= cx + hw + 1; x++) s.set(x, y, x <= cx ? 0xFFE8E8F4 : 0xFF9A9AB0);
            }
        }
        for (int x = 0; x < T; x++) {
            s.set(x, 14, 0xFF6A6A80);
            s.set(x, 15, 0xFF4A4A60);
        }
        return s;
    }

    private Sprite makeIce() {
        Sprite s = new Sprite(T, T);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int col = 0xFFA8DCF8;
                if ((x + y) % 7 == 0 && x > 2 && y > 2) col = 0xFFE8F8FF;
                if (x == 0 || y == 0) col = 0xFFF0FCFF;
                if (x == 15 || y == 15) col = 0xFF5A9ACC;
                s.set(x, y, col);
            }
        return s;
    }

    private Sprite makeGate() {
        Sprite s = new Sprite(T, T);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                if (x % 5 == 1 || x % 5 == 2) s.set(x, y, x % 5 == 1 ? 0xFF9A9AAE : 0xFF5A5A6E);
                if (y == 0 || y == 15) s.set(x, y, 0xFF3A3A4A);
            }
        return s;
    }

    private void makeSemi() {
        int top, topD, sup;
        switch (id) {
            case 1: top = 0xFFE8C080; topD = 0xFFB08040; sup = 0xFF9A6A30; break;
            case 2: top = 0xFFD8F0FF; topD = 0xFF8AB8E0; sup = 0xFF6A8AB0; break;
            case 3: top = 0xFF8A8A9E; topD = 0xFF4A4A5E; sup = 0xFF3A3A4A; break;
            default: top = 0xFFC08A50; topD = 0xFF8A5A30; sup = 0xFF6A4424; break;
        }
        Sprite[] r = new Sprite[3];
        for (int k = 0; k < 3; k++) {
            Sprite s = new Sprite(T, T);
            for (int x = 0; x < T; x++) {
                for (int y = 0; y < 5; y++) s.set(x, y, y == 0 ? Gfx.shade(top, 1.15f) : y == 4 ? topD : top);
                if (x % 8 == 7) for (int y = 1; y < 4; y++) s.set(x, y, topD);
            }
            if (k == 0) for (int y = 0; y < 5; y++) s.set(0, y, topD);
            if (k == 2) for (int y = 0; y < 5; y++) s.set(15, y, topD);
            // little supports
            for (int y = 5; y < 8; y++) {
                s.set(3, y, sup);
                s.set(12, y, sup);
            }
            r[k] = s;
        }
        semiL = r[0];
        semiM = r[1];
        semiR = r[2];
    }

    private Sprite makeLava(int f, boolean top) {
        Sprite s = new Sprite(T, T);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int col = 0xFFE0401A;
                if (rnd(x, y + f, 9) < 0.15f) col = 0xFFFF7A2A;
                if (rnd(x, y, 13) < 0.05f) col = 0xFFB02010;
                if (top) {
                    int wave = (int) (Math.sin((x + f * 4) * 0.8) * 1.5 + 2);
                    if (y < wave) col = 0;
                    else if (y < wave + 2) col = 0xFFFFD050;
                    else if (y < wave + 4) col = 0xFFFF9A30;
                }
                s.set(x, y, col);
            }
        return s;
    }

    // ------------------------------------------------------------- background
    private void makeBackground() {
        skyRows = new int[256];
        for (int y = 0; y < 256; y++) skyRows[y] = Gfx.mix(skyTop, skyBot, Math.min(1f, y / 200f));
        int W = 512;
        far = new Sprite(W, 140);
        near = new Sprite(W, 110);
        int farC, farD, nearC, nearL;
        switch (id) {
            case 1: farC = 0xFFE8A070; farD = 0xFFD08858; nearC = 0xFFE8B068; nearL = 0xFFF8D090; break;
            case 2: farC = 0xFF4A62A8; farD = 0xFFDDEBFF; nearC = 0xFF2E4A80; nearL = 0xFF6A8AC0; break;
            case 3: farC = 0xFF3A1420; farD = 0xFF5A1E22; nearC = 0xFF240C16; nearL = 0xFF4A1A22; break;
            default: farC = 0xFF7AB8D8; farD = 0xFF9ACCE4; nearC = 0xFF4AA858; nearL = 0xFF72C878; break;
        }
        for (int x = 0; x < W; x++) {
            double t = x * 2 * Math.PI / W;
            double hf;
            if (id == 1) { // pyramids
                int px = x % 170;
                double p1 = Math.max(0, 70 - Math.abs(px - 85) * 0.9);
                hf = 20 + p1 + Math.sin(t * 3) * 6;
            } else if (id == 2 || id == 3) { // sharp peaks
                double a = Math.abs(((x * 3) % 128) - 64) / 64.0;
                double b = Math.abs(((x * 5 + 40) % 128) - 64) / 64.0;
                hf = 30 + (1 - a) * 60 + (1 - b) * 30 + Math.sin(t * 7) * 4;
            } else hf = 50 + Math.sin(t * 2) * 22 + Math.sin(t * 5 + 1) * 12 + Math.sin(t * 11) * 4;
            int top = 140 - (int) hf;
            for (int y = Math.max(0, top); y < 140; y++) {
                int c = farC;
                if (id == 2 && y < top + 10 + (x % 7)) c = farD; // snow caps
                else if (id == 0 && ((x + y) & 3) == 0 && y > top + 6) c = farD;
                else if (id == 1 && (x % 170) > 85 && y > top) c = farD;
                else if (id == 3 && y < top + 3) c = farD;
                far.set(x, y, c);
            }
            if (id == 3 && (x % 128) > 60 && (x % 128) < 64 + 20) { // castle towers silhouette
                int tx = x % 128;
                int th = (tx < 68 || tx > 78) ? 64 : 84;
                for (int y = 140 - th; y < 140; y++) far.set(x, y, 0xFF2A0E18);
                if (tx % 4 == 0) far.set(x, 140 - th - 1, 0xFF2A0E18);
                if ((tx == 66 || tx == 80) && true) for (int y = 140 - th + 10; y < 140 - th + 14; y++) far.set(x, y, 0xFFFF9A3A);
            }
            double hn = 34 + Math.sin(t * 3 + 2) * 14 + Math.sin(t * 8) * 6;
            if (id == 1) hn = 26 + Math.sin(t * 4) * 12 + Math.sin(t * 9 + 1) * 4;
            int nt = 110 - (int) hn;
            for (int y = Math.max(0, nt); y < 110; y++) near.set(x, y, y < nt + 3 ? nearL : nearC);
        }
        // decorative elements on near layer
        for (int k = 0; k < 12; k++) {
            int x0 = k * 43 + (hash(k, 1, 3) & 15);
            int base = 110 - (int) (34 + Math.sin(x0 * 2 * Math.PI / W * 3 + 2) * 14 + Math.sin(x0 * 2 * Math.PI / W * 8) * 6) + 2;
            if (id == 1) base = 110 - (int) (26 + Math.sin(x0 * 2 * Math.PI / W * 4) * 12 + Math.sin(x0 * 2 * Math.PI / W * 9 + 1) * 4) + 2;
            drawBgProp(near, x0, base, k);
        }
        cloud = new Sprite(48, 18);
        int cc = id == 3 ? 0xFF5A2A30 : id == 2 ? 0xFF8AA0D8 : 0xFFFFFFFF;
        int cs = id == 3 ? 0xFF3A1A22 : id == 2 ? 0xFF6A80C0 : 0xFFDCEAF8;
        float[][] blobs = {{12, 11, 8}, {24, 8, 10}, {36, 11, 8}, {20, 13, 7}, {30, 13, 7}};
        for (float[] b : blobs)
            for (int y = 0; y < 18; y++)
                for (int x = 0; x < 48; x++) {
                    float dx = x - b[0], dy = y - b[1];
                    if (dx * dx + dy * dy < b[2] * b[2]) cloud.set(x, y, y > 13 ? cs : cc);
                }
    }

    private void drawBgProp(Sprite s, int x0, int base, int k) {
        switch (id) {
            case 0: { // round tree
                int trunk = 0xFF5A3A24, leaf = 0xFF3A9048, leafL = 0xFF5AB060;
                for (int y = base - 8; y < base; y++) { s.set(x0, y, trunk); s.set(x0 + 1, y, trunk); }
                for (int y = -10; y <= 0; y++)
                    for (int x = -7; x <= 8; x++)
                        if (x * x + (y + 5) * (y + 5) * 2 < 50) s.set(x0 + x, base - 8 + y, (x + y) < -6 ? leafL : leaf);
                break;
            }
            case 1: { // cactus
                int c = 0xFF4A9A5A;
                for (int y = base - 18; y < base; y++) { s.set(x0, y, c); s.set(x0 + 1, y, c); s.set(x0 + 2, y, 0xFF3A7A4A); }
                for (int y = base - 12; y < base - 7; y++) s.set(x0 - 3, y, c);
                for (int x = x0 - 3; x < x0; x++) s.set(x, base - 7, c);
                for (int y = base - 14; y < base - 9; y++) s.set(x0 + 5, y, c);
                for (int x = x0 + 3; x < x0 + 6; x++) s.set(x, base - 9, c);
                break;
            }
            case 2: { // snowy pine
                for (int y = 0; y < 22; y++) {
                    int hw = y / 2 + 1;
                    for (int x = -hw; x <= hw; x++) s.set(x0 + x, base - 24 + y, (y % 6 < 2) ? 0xFFE8F2FF : 0xFF2A5A6A);
                }
                s.set(x0, base - 2, 0xFF4A3A2A);
                s.set(x0, base - 1, 0xFF4A3A2A);
                break;
            }
            default: { // dark spire with ember window
                for (int y = base - 26; y < base; y++)
                    for (int x = -2; x <= 2; x++) if (Math.abs(x) <= (y - (base - 26)) / 5 + 1) s.set(x0 + x, y, 0xFF1A0810);
                s.set(x0, base - 14, 0xFFFF8A2A);
                break;
            }
        }
    }

    private void makeDecor() {
        decor = new Sprite[3];
        switch (id) {
            case 0:
                decor[0] = Art.raw(new String[]{
                        "....gggg....", "..ggGGGGgg..", ".gGGGGLGGGg.", "gGGLGGGGGGGg", "gGGGGGGGLGGg", "GGGGGGGGGGGG"},
                        'g', 0xFF2E8A34, 'G', 0xFF4AB04A, 'L', 0xFF8AE070);
                decor[1] = Art.raw(new String[]{".R.", "RYR", ".R.", ".g.", "gg."}, 'R', 0xFFF06A8A, 'Y', 0xFFFFE04A, 'g', 0xFF2E8A34);
                decor[2] = Art.raw(new String[]{".W.", "WYW", ".W.", ".g.", ".gg"}, 'W', 0xFFFFFFFF, 'Y', 0xFFFFC04A, 'g', 0xFF2E8A34);
                break;
            case 1:
                decor[0] = Art.raw(new String[]{"..c..", ".cc.c", "c.ccc", "ccc..", "..c..", "..c.."}, 'c', 0xFF3E8A4A);
                decor[1] = Art.raw(new String[]{".rrr.", "rRRRr", "RRRRR"}, 'r', 0xFFB08A60, 'R', 0xFF8A6A48);
                decor[2] = Art.raw(new String[]{"W.W.W", ".WWW.", "..W.."}, 'W', 0xFFEFE6D0);
                break;
            case 2:
                decor[0] = Art.raw(new String[]{"..w..", ".wCw.", "wCCCw", ".wCw.", "..w.."}, 'w', 0xFFFFFFFF, 'C', 0xFF9AD8F8);
                decor[1] = Art.raw(new String[]{"..WWWW..", ".WWWWWWW", "WWWWWWWW"}, 'W', 0xFFF4FAFF);
                decor[2] = Art.raw(new String[]{"C.C", ".C.", "C.C"}, 'C', 0xFFBCE8FF);
                break;
            default:
                decor[0] = Art.raw(new String[]{"..R..", ".ROR.", "ROYOR", ".ROR."}, 'R', 0xFFC02A1A, 'O', 0xFFFF8A2A, 'Y', 0xFFFFE04A);
                decor[1] = Art.raw(new String[]{"W..W", ".WW.", ".WW.", "W..W"}, 'W', 0xFFDCD0C0);
                decor[2] = Art.raw(new String[]{".kk.", "kkkk", "kkkk"}, 'k', 0xFF2A1A24);
                break;
        }
    }

    /** Draw sky + parallax layers. */
    public void drawBackground(Gfx g, float camX, float camY, int levelH, int frame) {
        int H = g.h;
        // vertical offset so backgrounds sit relative to the bottom of the level
        float bottom = levelH - camY - H; // how far the camera is above the level bottom
        for (int y = 0; y < H; y++) {
            int sy = (int) (y - bottom * 0.1f);
            int c = skyRows[Math.max(0, Math.min(255, sy))];
            int o = y * g.w;
            for (int x = 0; x < g.w; x++) g.px[o + x] = c;
        }
        // clouds
        for (int k = 0; k < 6; k++) {
            float cx = (k * 137 + frame * 0.05f * (1 + k % 3) - camX * 0.08f) % (g.w + 96);
            if (cx < 0) cx += g.w + 96;
            int cy = 10 + (k * 37) % 60 + (int) (bottom * 0.05f);
            g.blit(cloud, (int) cx - 48, cy);
        }
        int farY = H - far.h + (int) (bottom * 0.25f) - 20;
        tileLayer(g, far, camX * 0.2f, farY);
        int nearY = H - near.h + (int) (bottom * 0.45f) + 4;
        tileLayer(g, near, camX * 0.45f, nearY);
    }

    private void tileLayer(Gfx g, Sprite s, float off, int y0) {
        int o = ((int) off) % s.w;
        if (o < 0) o += s.w;
        for (int y = 0; y < s.h; y++) {
            int dy = y0 + y;
            if (dy < 0 || dy >= g.h) continue;
            int row = y * s.w, drow = dy * g.w;
            for (int x = 0; x < g.w; x++) {
                int c = s.px[row + (x + o) % s.w];
                if ((c >>> 24) != 0) g.px[drow + x] = c;
            }
            // fill below the layer with its bottom colour
        }
        int lastRow = y0 + s.h;
        if (lastRow < g.h) {
            for (int dy = Math.max(0, lastRow); dy < g.h; dy++)
                for (int x = 0; x < g.w; x++) {
                    int c = s.px[(s.h - 1) * s.w + (x + o) % s.w];
                    if ((c >>> 24) != 0) g.px[dy * g.w + x] = c;
                }
        }
    }
}
