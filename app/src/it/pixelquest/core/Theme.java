package it.pixelquest.core;

/** Per-world procedural tiles, decorations and parallax backgrounds. */
public final class Theme {
    public static final int T = 16;
    public final int id;
    public final String name;
    /** short form for banners ("BARCELLONA LIBERATA!") */
    public final String shortName;
    /** prologue worlds: backgrounds of the four towns */
    private TownArt town;
    public Sprite[] ground = new Sprite[16];
    public Sprite stone, brick, used, spike, ice, gate, semiL, semiM, semiR, lavaBody;
    public Sprite[] qblock = new Sprite[4], lavaTop = new Sprite[2];
    public Sprite far, near, cloud;
    public Sprite[] decor;
    public int skyTop, skyBot, fogColor;
    public int[] skyRows;
    // Unipa worlds
    public Sprite[] conv = new Sprite[2];
    public Sprite timedA, timedB, ublock;
    /** level position inside the world (0..5): moves the Unipa building closer / farther */
    public int stage;
    Sprite building, buildingNight;
    final Sprite[] indoorFar = new Sprite[6], indoorNear = new Sprite[6];

    static Theme[] cache = new Theme[LevelDef.WORLDS];

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
                name = "CAPO D'ORLANDO";
                dirt = 0xFFB4703C; dirtD = 0xFF8A4E28; dirtL = 0xFFD08E52;
                cap = 0xFF5BC24A; capD = 0xFF2E8A34; capL = 0xFF9EE878;
                brickC = 0xFFC0603A; stoneC = 0xFFA89C8C;
                skyTop = 0xFF5AB8F0; skyBot = 0xFFCDEEFF; fogColor = 0xFF9FD8F8;
                break;
            case 1:
                name = "PACECO";
                dirt = 0xFFE0A858; dirtD = 0xFFB87A38; dirtL = 0xFFF2C878;
                cap = 0xFFF8DC98; capD = 0xFFD8B070; capL = 0xFFFFF2C8;
                brickC = 0xFFD89A50; stoneC = 0xFFC8A070;
                skyTop = 0xFFF08850; skyBot = 0xFFFFE6A8; fogColor = 0xFFF8C090;
                break;
            case 2:
                name = "BARCELLONA POZZO DI GOTTO";
                dirt = 0xFF6A88B4; dirtD = 0xFF4A6290; dirtL = 0xFF8EAAD2;
                cap = 0xFFF2F8FF; capD = 0xFFBCD4EE; capL = 0xFFFFFFFF;
                brickC = 0xFF7AA0D0; stoneC = 0xFF9AB4D4;
                skyTop = 0xFF1E2A64; skyBot = 0xFF8AA8E0; fogColor = 0xFF5070B0;
                break;
            case 3:
                name = "NASO";
                dirt = 0xFF4A3A4C; dirtD = 0xFF30243A; dirtL = 0xFF6A5468;
                cap = 0xFF7A5A6A; capD = 0xFF4A3444; capL = 0xFFB07A6A;
                brickC = 0xFF6A4A5A; stoneC = 0xFF6A6478;
                skyTop = 0xFF14060E; skyBot = 0xFF7A2014; fogColor = 0xFF4A1414;
                break;            case 4:
                name = "PRIMO ANNO";
                dirt = 0xFFB8AE9C; dirtD = 0xFF8C8272; dirtL = 0xFFD2C8B6;
                cap = 0xFFCACACE; capD = 0xFF8E8E96; capL = 0xFFEAEAEE;
                brickC = 0xFFD8B47A; stoneC = 0xFFC8B48E;
                skyTop = 0xFF3A9AE8; skyBot = 0xFFC4E6FF; fogColor = 0xFF9FD0F0;
                break;
            case 5:
                name = "SECONDO ANNO";
                dirt = 0xFF8A6446; dirtD = 0xFF684A34; dirtL = 0xFFA27A58;
                cap = 0xFFD6CCB8; capD = 0xFFAA9E88; capL = 0xFFF2ECE0;
                brickC = 0xFFBCA88A; stoneC = 0xFFA8A6B4;
                skyTop = 0xFFE6D8BA; skyBot = 0xFFCDB894; fogColor = 0xFFD8C8A8;
                break;
            case 6:
                name = "ULTIMO ANNO";
                dirt = 0xFF54545E; dirtD = 0xFF3A3A44; dirtL = 0xFF6E6E7A;
                cap = 0xFFE0B020; capD = 0xFF2A2A30; capL = 0xFFFFD84A;
                brickC = 0xFF6C5A6E; stoneC = 0xFF787888;
                skyTop = 0xFF060818; skyBot = 0xFF2A1A48; fogColor = 0xFF1A1030;
                break;
        }
        shortName = id == 2 ? "BARCELLONA" : name;
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
        makeUnipaTiles();
        if (id >= 4) {
            building = makeBuilding(false);
            buildingNight = makeBuilding(true);
        }
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
        if (id == 4) { // sidewalk slabs
            for (int x = 0; x < T; x++) s.set(x, 8, dirtD);
            for (int y = 0; y < T; y++) s.set(y < 8 ? 7 : 15, y, dirtD);
        } else if (id == 5) { // parquet planks
            for (int y = 0; y < T; y++)
                for (int x = 0; x < T; x++) {
                    int c = ((x / 4 + y / 8) % 2 == 0) ? dirt : dirtL;
                    if (x % 4 == 3) c = dirtD;
                    if (y % 8 == 0 && x % 4 != 3) c = Gfx.shade(c, 0.9f);
                    s.set(x, y, c);
                }
        } else if (id == 6) { // concrete with bolts
            s.set(3, 11, 0xFF9A9AA6);
            s.set(12, 11, 0xFF9A9AA6);
        }
        if (!left) for (int y = 0; y < T; y++) s.set(0, y, dirtD);
        if (!right) for (int y = 0; y < T; y++) s.set(15, y, dirtD);
        if (!down) for (int x = 0; x < T; x++) s.set(x, 15, dirtD);
        if (!up) {
            for (int x = 0; x < T; x++) {
                int depth = 4 + (int) (StrictMath.sin(x * 1.3 + mask) * 1.2 + 0.5f);
                if (id == 1) depth = 3 + (x % 5 == 0 ? 1 : 0);
                if (id >= 4) depth = 4;
                for (int y = 0; y < depth; y++) s.set(x, y, y == 0 ? capL : y == depth - 1 ? capD : cap);
                if (id == 6 && ((x + mask) / 3) % 2 == 0) for (int y = 1; y < 3; y++) s.set(x, y, 0xFF2A2A30); // hazard stripes
                if (id == 5 && x % 8 == 0) for (int y = 0; y < 3; y++) s.set(x, y, capD); // floor tiles
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
        if (id >= 4) return makeUQ(f);
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
            case 5: top = 0xFFD8A868; topD = 0xFF9A6A38; sup = 0xFF5A5A66; break; // desk
            case 6: top = 0xFF9A9AAE; topD = 0xFF54546A; sup = 0xFF3A3A4A; break; // metal catwalk
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
                    int wave = (int) (StrictMath.sin((x + f * 4) * 0.8) * 1.5 + 2);
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
                hf = 20 + p1 + StrictMath.sin(t * 3) * 6;
            } else if (id == 2 || id == 3) { // sharp peaks
                double a = Math.abs(((x * 3) % 128) - 64) / 64.0;
                double b = Math.abs(((x * 5 + 40) % 128) - 64) / 64.0;
                hf = 30 + (1 - a) * 60 + (1 - b) * 30 + StrictMath.sin(t * 7) * 4;
            } else hf = 50 + StrictMath.sin(t * 2) * 22 + StrictMath.sin(t * 5 + 1) * 12 + StrictMath.sin(t * 11) * 4;
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
            double hn = 34 + StrictMath.sin(t * 3 + 2) * 14 + StrictMath.sin(t * 8) * 6;
            if (id == 1) hn = 26 + StrictMath.sin(t * 4) * 12 + StrictMath.sin(t * 9 + 1) * 4;
            int nt = 110 - (int) hn;
            for (int y = Math.max(0, nt); y < 110; y++) near.set(x, y, y < nt + 3 ? nearL : nearC);
        }
        // decorative elements on near layer
        for (int k = 0; k < 12; k++) {
            int x0 = k * 43 + (hash(k, 1, 3) & 15);
            int base = 110 - (int) (34 + StrictMath.sin(x0 * 2 * Math.PI / W * 3 + 2) * 14 + StrictMath.sin(x0 * 2 * Math.PI / W * 8) * 6) + 2;
            if (id == 1) base = 110 - (int) (26 + StrictMath.sin(x0 * 2 * Math.PI / W * 4) * 12 + StrictMath.sin(x0 * 2 * Math.PI / W * 9 + 1) * 4) + 2;
            drawBgProp(near, x0, base, k);
        }
        if (id == 4 || id == 6) makeCityNear();
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
            case 4:
                decor[0] = Art.raw(new String[]{"..O..", ".OWO.", ".OOO.", "OWWWO", "OOOOO"}, 'O', 0xFFFF7A2A, 'W', 0xFFFFFFFF); // traffic cone
                decor[1] = Art.raw(new String[]{".ggg.", "ggGgg", ".ggg.", "..b..", ".bbb."}, 'g', 0xFF3E9A4A, 'G', 0xFF7AD07A, 'b', 0xFF9A6A38); // potted plant
                decor[2] = Art.raw(new String[]{"kkkkk", "kGGGk", "kGGGk", "kGGGk", "kkkkk"}, 'k', 0xFF3A4A3A, 'G', 0xFF5A8A5A); // bin
                break;
            case 5:
                decor[0] = Art.raw(new String[]{"RRRRR.", "BBBBBB", ".GGGGG"}, 'R', 0xFFC03A3A, 'B', 0xFF3A6AC0, 'G', 0xFF3AA05A); // books
                decor[1] = Art.raw(new String[]{"k....", "k....", "kkkkk", "k...k", "k...k"}, 'k', 0xFF4A4A56); // chair
                decor[2] = Art.raw(new String[]{".g.g.", "ggGgg", ".ggg.", ".bbb.", ".bbb."}, 'g', 0xFF3E9A4A, 'G', 0xFF7AD07A, 'b', 0xFFB0603A); // plant
                break;
            case 6:
                decor[0] = Art.raw(new String[]{"..O..", ".OWO.", ".OOO.", "OWWWO", "OOOOO"}, 'O', 0xFFFF7A2A, 'W', 0xFFFFFFFF);
                decor[1] = Art.raw(new String[]{"wwwww", "wWwWw", "wwwww", "wWwWw", "wwwww"}, 'w', 0xFF8A6A3A, 'W', 0xFF6A4A2A); // crate
                decor[2] = Art.raw(new String[]{".R.", "RRR", "kkk"}, 'R', 0xFFFF3A3A, 'k', 0xFF3A3A44); // alarm light
                break;
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
    /** Backgrounds of the prologue towns (worlds 0-3). */
    TownArt town() {
        if (town == null) town = new TownArt(this);
        return town;
    }

    public void drawBackground(Gfx g, float camX, float camY, int levelH, int frame) {
        if (id <= 3) {
            town().draw(g, camX, camY, levelH, frame, stage);
            return;
        }
        if (id == 5 || (id == 6 && stage <= 1)) {
            drawIndoor(g, camX, camY, levelH, frame);
            return;
        }
        if (id == 6) {
            drawNight(g, camX, camY, levelH, frame);
            return;
        }
        if (id == 4) {
            drawCampus(g, camX, camY, levelH, frame);
            return;
        }
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

    // ================================================================ Unipa worlds
    private void makeUnipaTiles() {
        for (int f = 0; f < 2; f++) { // conveyor belt
            Sprite c = new Sprite(T, T);
            for (int y = 0; y < T; y++)
                for (int x = 0; x < T; x++) {
                    int col = y < 2 ? 0xFF3A3A44 : y < 6 ? (((x + f * 4) / 4) % 2 == 0 ? 0xFF6A6A7A : 0xFF4A4A56) : 0xFF8A8A9A;
                    if (y >= 6 && (x == 0 || x == 15)) col = 0xFF4A4A56;
                    if (y >= 8 && y <= 12 && (x == 4 || x == 11)) col = 0xFF3A3A44;
                    if (y == 15) col = 0xFF3A3A44;
                    c.set(x, y, col);
                }
            conv[f] = c;
        }
        timedA = timedBlock(0xFFFF8A2A, 'A');
        timedB = timedBlock(0xFF3AC0F0, 'B');
        ublock = makeUBlock(0xFFC8B48E);
    }

    private Sprite timedBlock(int c, char letter) {
        Sprite s = new Sprite(T, T);
        int hi = Gfx.shade(c, 1.25f), lo = Gfx.shade(c, 0.6f);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int col = c;
                if (x == 0 || y == 0) col = hi;
                if (x == 15 || y == 15) col = lo;
                s.set(x, y, col);
            }
        for (int gy = 0; gy < 7; gy++)
            for (int gx = 0; gx < 5; gx++)
                if (Font.pixel(letter, gx, gy)) s.set(5 + gx, 4 + gy, 0xFFFFFFFF);
        return s;
    }

    /** Stone block with the "U" emblem (Unipa). */
    private Sprite makeUBlock(int c) {
        Sprite s = makeStone(c);
        int red = 0xFF8A1A2A;
        for (int y = 3; y < 13; y++)
            for (int x = 4; x < 12; x++) {
                boolean u = (x <= 5 || x >= 10) && y < 11 || (y >= 10 && x >= 5 && x <= 10);
                if (u) s.set(x, y, red);
            }
        return s;
    }

    private Sprite makeUQ(int f) {
        Sprite s = new Sprite(T, T);
        int base = 0xFF2A5AB0, hi = 0xFF6A9AE8, lo = 0xFF1A3A78;
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int col = base;
                if (x == 0 || y == 0) col = hi;
                if (x == 15 || y == 15) col = lo;
                if ((x == 2 || x == 13) && (y == 2 || y == 13)) col = lo;
                s.set(x, y, col);
            }
        int em = f == 1 ? 0xFFFFFFFF : 0xFFFFD040;
        for (int y = 4; y < 12; y++)
            for (int x = 5; x < 11; x++) {
                boolean u = (x == 5 || x == 10) && y < 11 || (y == 11 && x > 5 && x < 10);
                if (u) {
                    s.set(x, y, em);
                    s.set(x + 1, y + 1 > 15 ? 15 : y + 1, s.get(x + 1, y + 1) == base ? lo : s.get(x + 1, y + 1));
                }
            }
        return s;
    }

    static void spriteText(Sprite s, String t, int x, int y, int col) {
        for (int i = 0; i < t.length(); i++)
            for (int gy = 0; gy < 7; gy++)
                for (int gx = 0; gx < 5; gx++)
                    if (Font.pixel(t.charAt(i), gx, gy)) s.set(x + i * 6 + gx, y + gy, col);
    }

    /** Original pixel-art university building with the UNIPA sign. */
    private Sprite makeBuilding(boolean night) {
        int W = 150, H = 86;
        Sprite s = new Sprite(W, H);
        int body = night ? 0xFF2C2C46 : 0xFFE6D8B8, bodySh = night ? 0xFF22223A : 0xFFC8B896, line = night ? 0xFF1A1A2E : 0xFFB8A684;
        int win = night ? 0xFF14142A : 0xFF3A5A8A, winLit = 0xFFFFD86A, winHi = 0xFF8AB0E0;
        // wings
        for (int y = 30; y < H; y++)
            for (int x = 0; x < W; x++) {
                boolean wing = x < 36 || x >= W - 36;
                boolean main = x >= 30 && x < W - 30;
                if (wing && y >= 40 || main && y >= 18) s.set(x, y, (x % 37 == 0) ? line : body);
            }
        for (int y = 18; y < H; y++) for (int x = 30; x < 34; x++) s.set(x, y, bodySh);
        // windows grid
        for (int y = 24; y < H - 14; y += 9)
            for (int x = 4; x < W - 4; x += 8) {
                boolean inMain = x >= 34 && x < W - 36, inWing = (x < 32 || x >= W - 34) && y >= 44;
                if (!inMain && !inWing) continue;
                if (x > W / 2 - 14 && x < W / 2 + 10 && y > H - 34) continue; // entrance
                boolean lit = night && hash(x, y, 9) % 3 != 0;
                for (int yy = 0; yy < 6; yy++)
                    for (int xx = 0; xx < 5; xx++)
                        s.set(x + xx, y + yy, lit ? winLit : (xx == 0 && yy < 3 && !night ? winHi : win));
            }
        // entrance with columns and steps
        int ex = W / 2 - 16;
        for (int y = H - 30; y < H - 4; y++)
            for (int x = ex; x < ex + 32; x++) s.set(x, y, night ? 0xFF101020 : 0xFF6A5A4A);
        for (int c = 0; c < 5; c++)
            for (int y = H - 32; y < H - 4; y++) {
                s.set(ex + 2 + c * 7, y, night ? 0xFF4A4A66 : 0xFFF4EEE0);
                s.set(ex + 3 + c * 7, y, night ? 0xFF3A3A54 : 0xFFDCD2C0);
            }
        for (int st = 0; st < 4; st++)
            for (int x = ex - 4 - st * 2; x < ex + 36 + st * 2; x++) s.set(x, H - 4 + st, night ? 0xFF3A3A50 : 0xFFD8D0C0);
        // sign
        for (int y = 6; y < 17; y++)
            for (int x = W / 2 - 22; x < W / 2 + 22; x++) s.set(x, y, night ? 0xFF5A0A14 : 0xFF8A1A2A);
        spriteText(s, "UNIPA", W / 2 - 14, 8, night ? 0xFFFF6A6A : 0xFFFFE6A0);
        if (night) { // alarm beacons
            s.set(32, 16, 0xFFFF2A2A);
            s.set(W - 32, 16, 0xFFFF2A2A);
        }
        return s.outlined(night ? 0xFF060612 : 0xFF6A5A48);
    }

    private void drawSky(Gfx g, float bottom) {
        for (int y = 0; y < g.h; y++) {
            int sy = (int) (y - bottom * 0.1f);
            int c = skyRows[Math.max(0, Math.min(255, sy))];
            int o = y * g.w;
            for (int x = 0; x < g.w; x++) g.px[o + x] = c;
        }
    }

    /** World 5: outside the university, the building gets closer level after level. */
    private void drawCampus(Gfx g, float camX, float camY, int levelH, int frame) {
        int H = g.h;
        float bottom = levelH - camY - H;
        drawSky(g, bottom);
        for (int k = 0; k < 5; k++) {
            float cx = (k * 151 + frame * 0.06f * (1 + k % 2) - camX * 0.06f) % (g.w + 96);
            if (cx < 0) cx += g.w + 96;
            g.blit(cloud, (int) cx - 48, 12 + (k * 29) % 40 + (int) (bottom * 0.05f));
        }
        int farY = H - far.h + (int) (bottom * 0.25f) - 20;
        tileLayer(g, far, camX * 0.12f, farY);
        float sc = 0.45f + stage * 0.32f;
        float bx = g.w * 0.68f - (camX * 0.05f) % (g.w * 0.4f);
        float by = H - 40 + bottom * 0.35f - building.h * sc / 2f;
        g.blitEx(building, bx, by, sc, sc, 0, false, 255, 0);
        int nearY = H - near.h + (int) (bottom * 0.45f) + 4;
        tileLayer(g, near, camX * 0.45f, nearY);
    }

    /** World 7 outdoor levels: escaping at night, the building behind gets smaller. */
    private void drawNight(Gfx g, float camX, float camY, int levelH, int frame) {
        int H = g.h;
        float bottom = levelH - camY - H;
        drawSky(g, bottom);
        for (int k = 0; k < 60; k++) {
            int sx = (int) ((k * 97 - camX * 0.02f) % g.w + g.w) % g.w, sy = (k * 41) % (H - 40);
            g.pset(sx, sy, (k + frame / 20) % 7 == 0 ? 0xFF8A8AB0 : 0xFFFFFFFF);
        }
        g.circle(g.w - 50, 30, 12, 0xFFF4F0D8, 255);
        g.circle(g.w - 46, 27, 10, 0xFF1A1236, 255);
        // searchlights
        for (int k = 0; k < 2; k++) {
            double a = StrictMath.sin(frame * 0.012 + k * 2) * 0.6 - Math.PI / 2;
            int ox = (int) (g.w * (0.3f + 0.4f * k)), oy = H - 30;
            for (int d = 0; d < 200; d += 2) {
                int x = ox + (int) (StrictMath.cos(a) * d), y = oy + (int) (StrictMath.sin(a) * d);
                int wdt = 2 + d / 12;
                g.fillA(x - wdt / 2, y, wdt, 2, 0xFFFFF4C0, 22);
            }
        }
        float sc = Math.max(0.4f, 2.0f - stage * 0.32f);
        float bx = g.w * 0.35f - (camX * 0.04f) % (g.w * 0.3f);
        float by = H - 40 + bottom * 0.35f - buildingNight.h * sc / 2f;
        g.blitEx(buildingNight, bx, by, sc, sc, 0, false, 255, 0);
        if ((frame / 20) % 2 == 0) g.circle((int) bx, (int) (by - buildingNight.h * sc / 2f), 3, 0xFFFF3A3A, 200);
        int nearY = H - near.h + (int) (bottom * 0.45f) + 4;
        tileLayer(g, near, camX * 0.45f, nearY);
    }

    /** Worlds 6 and 7 indoor levels: classrooms, labs, library, server room. */
    private void drawIndoor(Gfx g, float camX, float camY, int levelH, int frame) {
        int st = Math.max(0, Math.min(5, stage));
        int key = id == 6 ? 4 + Math.min(1, st) : st; // world 7 indoor reuses the darker rooms
        if (indoorFar[key] == null) {
            indoorFar[key] = makeIndoorFar(key);
            indoorNear[key] = makeIndoorNear(key);
        }
        int H = g.h;
        int[] walls = {0xFFE6D8BA, 0xFFD4E0E4, 0xFFE0D4C0, 0xFF8A6A4A, 0xFF1E2230, 0xFF2A3040};
        int wall = walls[key];
        if (id == 6) wall = Gfx.shade(wall, 0.45f);
        for (int y = 0; y < H; y++) {
            int c = Gfx.shade(wall, 0.9f + 0.15f * y / H);
            int o = y * g.w;
            for (int x = 0; x < g.w; x++) g.px[o + x] = c;
        }
        float bottom = levelH - camY - H;
        tileLayer(g, indoorFar[key], camX * 0.35f, (int) (bottom * 0.3f) - 10);
        int nearY = H - indoorNear[key].h + (int) (bottom * 0.5f) + 6;
        tileLayer(g, indoorNear[key], camX * 0.6f, nearY);
        if (id == 6 && (frame / 30) % 2 == 0) g.dim(0xFFFF2020, 18); // alarm
        if (key == 4) // blinking server LEDs
            for (int k = 0; k < 30; k++) {
                int sx = (int) (((k * 37 - camX * 0.35f) % g.w + g.w) % g.w), sy = 40 + (k * 23) % 120;
                if ((frame / 8 + k) % 3 == 0) g.pset(sx, sy, k % 2 == 0 ? 0xFF3AFF6A : 0xFFFF3A3A);
            }
    }

    private Sprite makeIndoorFar(int key) {
        int W = 512, H = 190;
        Sprite s = new Sprite(W, H);
        for (int x = 0; x < W; x++) {
            int seg = x % 128;
            switch (key) {
                case 0: // aula magna: blackboards and windows
                case 2:
                    if (seg >= 8 && seg < 72) for (int y = 50; y < 96; y++) s.set(x, y, (y == 50 || y == 95 || seg == 8 || seg == 71) ? 0xFF6A4A2A : 0xFF2A4A3A);
                    if (seg >= 88 && seg < 120) for (int y = 40; y < 100; y++) s.set(x, y, (seg - 88) % 15 == 0 || (y - 40) % 20 == 0 ? 0xFFF0F0F0 : 0xFF8AC8F0);
                    if (key == 2 && seg >= 14 && seg < 66 && x % 3 == 0) for (int y = 58; y < 90; y += 9) s.set(x, y, 0xFFE8E8E8); // chalk formulas
                    break;
                case 1: // physics lab: tiled wall, shelves with flasks
                    for (int y = 30; y < 130; y++) if ((x % 16 == 0) || (y % 16 == 0)) s.set(x, y, 0xFFB8C8CC);
                    if (seg >= 20 && seg < 100) { for (int y = 70; y < 73; y++) s.set(x, y, 0xFF6A6A7A); if (seg % 12 < 5) for (int y = 60; y < 70; y++) s.set(x, y, seg % 24 < 12 ? 0xFF6AE0A0 : 0xFFE06A9A); }
                    break;
                case 3: // library: bookshelves
                    for (int y = 20; y < 180; y++) {
                        int shelf = (y - 20) % 32;
                        int col = shelf < 3 ? 0xFF5A3A22 : (x % 64 < 3 ? 0xFF5A3A22 : (new int[]{0xFFB03A3A, 0xFF3A6AB0, 0xFF3AA05A, 0xFFD0A030, 0xFF7A4AA0})[(x / 4 + y / 32) % 5]);
                        if (shelf >= 3 && x % 4 == 3) col = Gfx.shade(col, 0.7f);
                        s.set(x, y, col);
                    }
                    break;
                default: // server room / computer lab: racks and monitors
                    if (seg >= 10 && seg < 50) for (int y = 30; y < 170; y++) s.set(x, y, (y % 10 == 0) ? 0xFF0A0A12 : 0xFF2A2E3A);
                    if (seg >= 70 && seg < 118) for (int y = 60; y < 92; y++) s.set(x, y, (y == 60 || y == 91 || seg == 70 || seg == 117) ? 0xFF0A0A12 : 0xFF2A6AB0);
                    if (seg >= 74 && seg < 114 && (x % 2 == 0)) for (int y = 66; y < 86; y += 4) if ((x * 7 + y) % 5 != 0) s.set(x, y, 0xFF8AE0FF);
                    break;
            }
        }
        return s;
    }

    private Sprite makeIndoorNear(int key) {
        int W = 512, H = 60;
        Sprite s = new Sprite(W, H);
        int c = key >= 4 ? 0xFF14161E : key == 3 ? 0xFF3A2414 : 0xFF6A4A30;
        int c2 = Gfx.shade(c, 1.3f);
        for (int x = 0; x < W; x++) {
            int seg = x % 64;
            if (key == 3) { // reading tables
                if (seg < 50) for (int y = 30; y < 34; y++) s.set(x, y, c2);
                if (seg == 4 || seg == 45) for (int y = 34; y < 60; y++) s.set(x, y, c);
            } else if (key >= 4) { // desks with monitors
                if (seg < 54) for (int y = 34; y < 37; y++) s.set(x, y, c2);
                if (seg >= 14 && seg < 34) for (int y = 18; y < 32; y++) s.set(x, y, (y == 18 || seg == 14 || seg == 33) ? c : 0xFF1A3A6A);
                if (seg == 3 || seg == 50) for (int y = 37; y < 60; y++) s.set(x, y, c);
            } else { // rows of lecture seats
                for (int y = 26; y < 60; y++) {
                    int row = (y - 26) / 12;
                    if ((y - 26) % 12 < 3 && (seg + row * 9) % 64 < 56) s.set(x, y, c2);
                    else if ((y - 26) % 12 >= 3 && (seg + row * 9) % 16 == 0) s.set(x, y, c);
                }
            }
        }
        return s;
    }

    /** Palermo street (day) or campus fence and skyline (night) for the near parallax layer. */
    private void makeCityNear() {
        int W = near.w, H = near.h;
        near = new Sprite(W, H);
        boolean night = id == 6;
        int[] houses = night ? new int[]{0xFF1E1A30, 0xFF26203A, 0xFF1A1828} : new int[]{0xFFE8C890, 0xFFE0A878, 0xFFF0DCB0, 0xFFD89A80};
        int x = 0, k = 0;
        while (x < W) {
            int hw = 30 + (hash(k, 3, 7) & 15), hh = 34 + (hash(k, 5, 7) & 31);
            int col = houses[k % houses.length];
            for (int xx = x; xx < Math.min(W, x + hw); xx++)
                for (int y = H - hh; y < H; y++) {
                    int c = col;
                    int wx = (xx - x) % 10, wy = (y - (H - hh)) % 12;
                    if (wx >= 3 && wx <= 6 && wy >= 4 && wy <= 8 && y < H - 6)
                        c = night ? ((hash(xx / 10, y / 12, 4) & 3) == 0 ? 0xFFFFD86A : 0xFF0E0C18) : 0xFF5A7A9A;
                    if (y == H - hh) c = Gfx.shade(col, 0.8f);
                    near.set(xx, y, c);
                }
            x += hw + 2;
            if (!night && k % 2 == 0 && x < W - 10) { // palm tree
                int px = x + 3;
                for (int y = H - 50; y < H; y++) { near.set(px, y, 0xFF8A6A3A); near.set(px + 1, y, 0xFF6A4A2A); }
                for (int a = 0; a < 6; a++)
                    for (int d = 0; d < 12; d++) {
                        double ang = a * Math.PI / 3 + 0.3;
                        int lx = px + (int) (StrictMath.cos(ang) * d), ly = H - 52 + (int) (StrictMath.sin(ang) * d * 0.5 + d * d * 0.04);
                        if (lx >= 0 && lx < W && ly >= 0 && ly < H) { near.set(lx, ly, 0xFF2E8A3A); if (ly + 1 < H) near.set(lx, ly + 1, 0xFF1E6A2A); }
                    }
                x += 6;
            }
            k++;
        }
        if (night) // fence
            for (int xx = 0; xx < W; xx++)
                for (int y = H - 22; y < H; y++)
                    if (xx % 6 == 0 || y == H - 22 || y == H - 12) near.set(xx, y, 0xFF0A0A14);
    }
}
