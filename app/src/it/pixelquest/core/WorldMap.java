package it.pixelquest.core;

/** The overworld map: 4 worlds, 16 level nodes connected by a path. */
public final class WorldMap {
    static final int MW = 1220, MH = 224;
    static final int[][] NODES = {
            {40, 150}, {100, 104}, {165, 146}, {238, 98},
            {335, 140}, {398, 172}, {462, 124}, {540, 96},
            {636, 150}, {696, 110}, {758, 154}, {838, 100},
            {936, 146}, {998, 104}, {1066, 150}, {1150, 96}};

    private final Game game;
    private Sprite bg;
    int cur, target;
    float iconX, iconY;
    int moveFrom = -1, moveT;
    boolean pendingAdvance;
    float cam;
    int t;

    WorldMap(Game g) {
        this.game = g;
        cur = target = Math.min(g.save.lastNode, g.save.unlocked);
        iconX = NODES[cur][0];
        iconY = NODES[cur][1];
    }

    void enter() {
        if (bg == null) bg = buildBackground();
        cur = target = Math.max(0, Math.min(game.save.lastNode, game.save.unlocked));
        iconX = NODES[cur][0];
        iconY = NODES[cur][1];
        moveFrom = -1;
        if (pendingAdvance && cur + 1 <= game.save.unlocked) target = cur + 1;
        pendingAdvance = false;
        cam = Math.max(0, Math.min(MW - game.W, iconX - game.W / 2f));
    }

    // ------------------------------------------------------------------ update
    void update() {
        t++;
        Input in = game.input;
        TouchControls tc = game.touch;
        if (moveFrom < 0) {
            if (in.backP || in.bP) {
                game.openMapMenu();
                return;
            }
            if (in.rightP && cur < game.save.unlocked && cur < 15) target = cur + 1;
            if (in.leftP && cur > 0) target = cur - 1;
            if (in.aP) {
                game.playLevel(cur);
                return;
            }
            if (in.cP) {
                Sfx.play(Sfx.CONFIRM);
                game.openSelect();
                return;
            }
            for (float[] tp : tc.taps) {
                float mx = tp[0] + cam, my = tp[1];
                for (int i = 0; i <= game.save.unlocked && i < NODES.length; i++) {
                    float dx = mx - NODES[i][0], dy = my - NODES[i][1];
                    if (dx * dx + dy * dy < 16 * 16) {
                        if (i == cur) {
                            game.playLevel(cur);
                            return;
                        }
                        target = i;
                    }
                }
            }
            if (target != cur) {
                moveFrom = cur;
                cur += target > cur ? 1 : -1;
                moveT = 0;
                Sfx.play(Sfx.SELECT);
            }
        } else {
            moveT++;
            float k = Math.min(1, moveT / 18f);
            float sk = k * k * (3 - 2 * k);
            iconX = NODES[moveFrom][0] + (NODES[cur][0] - NODES[moveFrom][0]) * sk;
            iconY = NODES[moveFrom][1] + (NODES[cur][1] - NODES[moveFrom][1]) * sk - (float) Math.sin(k * Math.PI) * 6;
            if (k >= 1) {
                moveFrom = -1;
                iconX = NODES[cur][0];
                iconY = NODES[cur][1];
                game.save.lastNode = cur;
            }
        }
        float tc2 = Math.max(0, Math.min(MW - game.W, iconX - game.W / 2f));
        cam += (tc2 - cam) * 0.1f;
    }

    // ------------------------------------------------------------------ render
    void render(Gfx g) {
        int c = Math.round(cam);
        g.blit(bg, -c, 0);
        // animated water sparkles / lava glow
        for (int k = 0; k < 14; k++) {
            int sx = (k * 89 + t / 2) % MW, sy = 30 + (k * 61) % 170;
            int region = sx / 300;
            if (region == 3 && (t / 8 + k) % 3 == 0) g.pset(sx - c, sy, 0xFFFF9A3A);
        }
        // path
        Save s = game.save;
        for (int i = 0; i < NODES.length - 1; i++) {
            boolean open = i + 1 <= s.unlocked;
            int x0 = NODES[i][0], y0 = NODES[i][1], x1 = NODES[i + 1][0], y1 = NODES[i + 1][1];
            float len = (float) Math.hypot(x1 - x0, y1 - y0);
            for (float d = 6; d < len - 5; d += 6) {
                int px = (int) (x0 + (x1 - x0) * d / len) - c, py = (int) (y0 + (y1 - y0) * d / len);
                g.fill(px - 1, py - 1, 3, 3, Art.OUT);
                g.pset(px, py, open ? 0xFFFFF0C8 : 0xFF6A6A7A);
            }
        }
        // nodes
        for (int i = 0; i < NODES.length; i++) {
            int x = NODES[i][0] - c, y = NODES[i][1];
            boolean boss = i % 4 == 3, done = s.isCleared(i), open = i <= s.unlocked;
            Sprite sp = boss ? (done ? Art.castleDone : Art.castle) : (done ? Art.nodeDone : Art.node);
            g.blit(sp, x - sp.w / 2, y - sp.h / 2 + (boss ? -4 : 0), false, open ? 255 : 120, 0);
            if (!open) g.blit(Art.lock, x - 3, y - 4);
            int gm = s.gems(i);
            if (open && !boss)
                for (int k = 0; k < 3; k++) g.fill(x - 5 + k * 4, y + 6, 3, 3, (gm & (1 << k)) != 0 ? 0xFF6AC8FF : 0xFF2A2A3A);
        }
        // hero icon
        Sprite icon = game.hero().icon;
        int bob = moveFrom < 0 ? (int) Math.round(Math.sin(t * 0.12) * 2) : 0;
        g.fillA((int) iconX - c - 6, (int) iconY + 1, 12, 3, 0xFF000000, 90);
        g.blit(icon, (int) iconX - c - icon.w / 2, (int) iconY - icon.h - 2 + bob);

        // top info bar
        LevelDef d = Levels.get(cur);
        g.fillA(0, 0, game.W, 36, 0xFF000000, 150);
        g.textO("MONDO " + (d.world + 1) + ": " + Theme.get(d.world).name, 6, 5, 0xFFFFE070, 0xFF000000, 1);
        g.textO(d.code + "  " + d.name, 6, 19, 0xFFFFFFFF, 0xFF000000, 1);
        int nx = 6 + Gfx.textW(d.code + "  " + d.name, 1) + 8;
        if (!d.boss) for (int k = 0; k < 3; k++) g.blit((s.gems(cur) & (1 << k)) != 0 ? Art.hudGem : Art.hudGemEmpty, nx + k * 14, 15);
        else if (s.isCleared(cur)) g.textO("\u0006 SCONFITTO", nx, 19, 0xFF7CFF7C, 0xFF000000, 1);
        // right: lives / coins / gems
        int rx = game.W - 120;
        g.blit(game.hero().icon, rx, 3);
        g.textO("x" + s.lives, rx + 17, 8, 0xFFFFFFFF, 0xFF000000, 1);
        g.blit(Art.hudCoin, rx + 40, 3);
        g.textO("" + s.totalCoins, rx + 53, 7, 0xFFFFE070, 0xFF000000, 1);
        g.blit(Art.hudGem, rx + 78, 2);
        g.textO(s.totalGems() + "/48", rx + 92, 7, 0xFF8AD0FF, 0xFF000000, 1);
        g.textO(game.hero().name, rx - 30 + 10, 21, 0xFFC0C0E0, 0xFF000000, 1);

        // controls
        TouchControls tc = game.touch;
        tc.visible = true;
        tc.draw(g, "EROE", true, false);
        float[] A = tc.btnA(), B = tc.btnB();
        g.textC("GIOCA", (int) A[0], (int) (A[1] - A[2] - 10), 0xFFFFFFFF, 0xFF000000, 1);
        g.textC("MENU", (int) B[0], (int) (B[1] - B[2] - 10), 0xFFFFFFFF, 0xFF000000, 1);
    }

    // --------------------------------------------------------------- background
    private Sprite buildBackground() {
        Sprite s = new Sprite(MW, MH);
        int[][] cols = {
                {0xFF58B048, 0xFF4A9A3E, 0xFF6CC45A},
                {0xFFE8C078, 0xFFD8AA62, 0xFFF4D494},
                {0xFFE4EEF8, 0xFFC4D6EA, 0xFFFFFFFF},
                {0xFF4A3442, 0xFF3A2834, 0xFF5A4252}};
        for (int y = 0; y < MH; y++)
            for (int x = 0; x < MW; x++) {
                // wavy region borders
                float bx = x + (float) Math.sin(y * 0.07) * 10;
                int r = Math.max(0, Math.min(3, (int) (bx / 300)));
                int[] c = cols[r];
                float n = Theme.rnd(x / 3, y / 3, 99 + r);
                int col = n < 0.18f ? c[1] : n > 0.9f ? c[2] : c[0];
                s.set(x, y, col);
            }
        // sea at top & bottom edges (frames the map)
        for (int x = 0; x < MW; x++) {
            int top = 22 + (int) (Math.sin(x * 0.05) * 4 + Math.sin(x * 0.013) * 5);
            int bot = MH - 14 - (int) (Math.sin(x * 0.04 + 1) * 4);
            for (int y = 0; y < top; y++) s.set(x, y, y > top - 3 ? 0xFF8AD0F0 : ((x + y * 3) % 23 == 0 ? 0xFF6ABEEA : 0xFF3A8AD0));
            for (int y = bot; y < MH; y++) s.set(x, y, y < bot + 2 ? 0xFF8AD0F0 : ((x * 2 + y) % 19 == 0 ? 0xFF6ABEEA : 0xFF3A8AD0));
        }
        // lava rivers in world 4
        for (int y = 30; y < MH - 16; y++) {
            int rx = 1010 + (int) (Math.sin(y * 0.06) * 30);
            for (int x = rx; x < rx + 5; x++) s.set(x, y, (x + y) % 5 == 0 ? 0xFFFFD050 : 0xFFE8501A);
        }
        // decorations
        java.util.Random rnd = new java.util.Random(42);
        for (int k = 0; k < 220; k++) {
            int x = rnd.nextInt(MW), y = 34 + rnd.nextInt(MH - 56);
            boolean nearPath = false;
            for (int[] nd : NODES) if (Math.abs(nd[0] - x) < 22 && Math.abs(nd[1] - y) < 22) nearPath = true;
            for (int i = 0; i < NODES.length - 1 && !nearPath; i++) {
                float d = distSeg(x, y, NODES[i][0], NODES[i][1], NODES[i + 1][0], NODES[i + 1][1]);
                if (d < 12) nearPath = true;
            }
            if (nearPath) continue;
            int r = x / 300;
            drawDeco(s, x, y, Math.min(3, r), rnd.nextInt(3));
        }
        return s;
    }

    private static float distSeg(float px, float py, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float k = Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / (dx * dx + dy * dy)));
        float qx = x0 + dx * k - px, qy = y0 + dy * k - py;
        return (float) Math.sqrt(qx * qx + qy * qy);
    }

    private void drawDeco(Sprite s, int x, int y, int region, int v) {
        switch (region) {
            case 0:
                if (v < 2) { // tree
                    for (int yy = -9; yy <= 0; yy++)
                        for (int xx = -5; xx <= 5; xx++)
                            if (xx * xx + (yy + 5) * (yy + 5) < 22) s.set(x + xx, y + yy, xx + yy < -4 ? 0xFF7ACF6A : 0xFF2E7A34);
                    s.set(x, y + 1, 0xFF5A3A24);
                    s.set(x, y + 2, 0xFF5A3A24);
                } else { // flowers
                    s.set(x, y, 0xFFF06A8A);
                    s.set(x + 3, y + 1, 0xFFFFE04A);
                    s.set(x - 2, y + 2, 0xFFFFFFFF);
                }
                break;
            case 1:
                if (v == 0) { // pyramid
                    for (int yy = 0; yy < 10; yy++)
                        for (int xx = -yy; xx <= yy; xx++) s.set(x + xx, y - 10 + yy, xx < 0 ? 0xFFE0A858 : 0xFFB8803A);
                } else { // cactus
                    for (int yy = -6; yy <= 0; yy++) s.set(x, y + yy, 0xFF3E8A4A);
                    s.set(x - 1, y - 3, 0xFF3E8A4A);
                    s.set(x + 1, y - 4, 0xFF3E8A4A);
                }
                break;
            case 2:
                for (int yy = 0; yy < 10; yy++) { // snowy pine
                    int hw = yy / 2;
                    for (int xx = -hw; xx <= hw; xx++) s.set(x + xx, y - 10 + yy, yy % 4 < 1 ? 0xFFFFFFFF : 0xFF2A5A6A);
                }
                s.set(x, y, 0xFF4A3A2A);
                break;
            default:
                if (v == 0) { // volcano
                    for (int yy = 0; yy < 12; yy++)
                        for (int xx = -yy - 2; xx <= yy + 2; xx++) s.set(x + xx, y - 12 + yy, yy < 2 ? 0xFFFF7A2A : xx < 0 ? 0xFF2A1A22 : 0xFF1A1018);
                } else { // spikes of rock
                    for (int yy = 0; yy < 6; yy++) s.set(x, y - yy, 0xFF2A1A22);
                    s.set(x + 1, y, 0xFF2A1A22);
                    s.set(x + 1, y - 1, 0xFF2A1A22);
                }
                break;
        }
    }
}
