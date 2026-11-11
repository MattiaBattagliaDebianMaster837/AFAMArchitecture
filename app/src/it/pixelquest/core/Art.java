package it.pixelquest.core;

import java.util.HashMap;

/** Enemies, items, effects and UI sprites. Small sprites are hand drawn, larger ones are procedurally shaded. */
public final class Art {
    public static final int OUT = 0xFF1A1018;

    // enemies [world][frame]
    public static Sprite[][] slime = new Sprite[4][];
    public static Sprite[][] hedgehog = new Sprite[4][];
    public static Sprite[] bat, frog, penguin, fire, cannon, crusher;
    public static Sprite cannonBall, sandBall, snowBall, icicle, bulletP, rock;
    public static Sprite[] fireball;
    // items
    public static Sprite[] coin, gem, star;
    public static Sprite heart, potion, spring0, spring1, flagPole, flagGoal0, flagGoal1, ckOff, ckOn, trophy;
    public static Sprite[] puff, sparkle;
    public static Sprite hudHeart, hudHeartEmpty, hudCoin, hudGem, hudGemEmpty, hudBullet, hudBulletEmpty;
    public static Sprite node, nodeDone, castle, castleDone, lock;

    static HashMap<Character, Integer> pal(Object... kv) {
        HashMap<Character, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Character) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    static Sprite spr(String[] rows, Object... kv) {
        return Sprite.parse(rows, pal(kv)).outlined(OUT);
    }

    static Sprite raw(String[] rows, Object... kv) {
        return Sprite.parse(rows, pal(kv));
    }

    // world palettes for palette-swapped enemies: {main, dark, light}
    static final int[][] SLIME_COL = {
            {0xFF5BC24A, 0xFF2E8A34, 0xFFB8F08A},
            {0xFFE9973A, 0xFFB3611E, 0xFFFFD08A},
            {0xFF6FC6F2, 0xFF3683C2, 0xFFD6F4FF},
            {0xFFB5345A, 0xFF74193A, 0xFFF07A9A}};
    static final int[][] HOG_COL = {
            {0xFF6E5038, 0xFFDDB98C, 0xFF4A3424},
            {0xFF9C6B2E, 0xFFF0D29A, 0xFF6A4518},
            {0xFFD8E6F0, 0xFFA8C8E0, 0xFF7C98B4},
            {0xFF3A2E3E, 0xFFE07A4A, 0xFF241A28}};

    public static void init() {
        for (int w = 0; w < 4; w++) {
            int[] c = SLIME_COL[w];
            Object[] p = {'G', c[0], 'g', c[1], 'L', c[2], 'W', 0xFFFFFFFF, 'P', 0xFF1A1018};
            slime[w] = new Sprite[]{spr(new String[]{
                    "................",
                    "......LLLL......",
                    "....LLLGGGGG....",
                    "...LLGGGGGGGG...",
                    "..LLGGGGGGGGGG..",
                    "..LGGWWGGGWWGG..",
                    "..GGGWPGGGWPGG..",
                    ".GGGGWPGGGWPGGG.",
                    ".GGGGGGGGGGGGGG.",
                    ".GgGGGGGGGGGGgG.",
                    "GgGGgGGGGGGgGGgG",
                    "gggggggggggggggg",
            }, p), spr(new String[]{
                    "................",
                    "................",
                    "................",
                    ".....LLLLLL.....",
                    "...LLLGGGGGGG...",
                    "..LLGGGGGGGGGG..",
                    ".LGGGWWGGGWWGGG.",
                    ".GGGGWPGGGWPGGG.",
                    "GGGGGWPGGGWPGGGG",
                    "GGgGGGGGGGGGGgGG",
                    "gGGGgGGGGGGgGGGg",
                    "gggggggggggggggg",
            }, p), spr(new String[]{
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "....LLLLLLLL....",
                    "..LLGGGGGGGGGG..",
                    ".GGGPPGGGGPPGGG.",
                    "gGGGGGGGGGGGGGGg",
                    "gggggggggggggggg",
            }, p)};
            int[] h = HOG_COL[w];
            Object[] q = {'S', h[0], 's', 0xFFF4F0E8, 'B', h[1], 'b', Gfx.shade(h[1], 0.8f), 'W', 0xFFFFFFFF, 'P', 0xFF1A1018, 'n', 0xFF1A1018, 'f', h[2]};
            hedgehog[w] = new Sprite[]{spr(new String[]{
                    "..s.s.s.........",
                    ".sSsSsSs........",
                    "sSSSSSSSs.......",
                    "SSSSSSSSSSBB....",
                    "SSSSSSSSSBBBBB..",
                    "SSSSSSSSBBBWPB..",
                    "SSSSSSSSBBBBBBBn",
                    "SSSSSSSSBBBBBBBn",
                    ".SSSSSSbBBBBBB..",
                    "..bBBBBBBBBBBb..",
                    "...ff.....ff....",
            }, q), spr(new String[]{
                    "...s.s.s........",
                    "..sSsSsSs.......",
                    ".sSSSSSSSs......",
                    "sSSSSSSSSSBB....",
                    "SSSSSSSSSBBBBB..",
                    "SSSSSSSSBBBWPB..",
                    "SSSSSSSSBBBBBBBn",
                    "SSSSSSSSBBBBBBBn",
                    ".SSSSSSbBBBBBB..",
                    "..bBBBBBBBBBBb..",
                    ".....ff..ff.....",
            }, q)};
        }
        Object[] bp = {'W', 0xFF5E3C8C, 'w', 0xFF3E2466, 'B', 0xFF7A52B0, 'E', 0xFFFFE04A, 't', 0xFFFFFFFF};
        bat = new Sprite[]{spr(new String[]{
                "W..............W",
                "WW............WW",
                "WWW...B..B...WWW",
                "wWWW.BBBBBB.WWWw",
                ".wWWBEBBBBEBWWw.",
                "..wWBBBBBBBBWw..",
                "...wBBtBBtBBw...",
                ".....BBBBBB.....",
        }, bp), spr(new String[]{
                "................",
                "......B..B......",
                ".....BBBBBB.....",
                "....BEBBBBEB....",
                "..wWBBBBBBBBWw..",
                ".wWWWBtBBtBWWWw.",
                "wWW..BBBBBB..WWw",
                "WW............WW",
        }, bp)};
        Object[] fp = {'G', 0xFF3FA66A, 'g', 0xFF226B44, 'Y', 0xFFE8E08A, 'W', 0xFFFFFFFF, 'P', 0xFF1A1018, 'R', 0xFF8A2A3A};
        frog = new Sprite[]{spr(new String[]{
                "................",
                "..WWW....WWW....",
                ".WWPPW..WWPPW...",
                ".WWPPWGGWWPPW...",
                ".GWWWGGGGWWWGG..",
                "GGGGGGGGGGGGGGG.",
                "GRRRRRRRRRRRRGG.",
                "GGYYYYYYYYYYGGG.",
                "gGYYYYYYYYYYGgg.",
                "ggGGGGGGGGGGGgg.",
                "ggg.gg....gg.ggg",
        }, fp), spr(new String[]{
                "..WWW....WWW....",
                ".WWPPW..WWPPW...",
                ".WWPPWGGWWPPW...",
                ".GWWWGGGGWWWGG..",
                "GGGGGGGGGGGGGGG.",
                "GRRRRRRRRRRRRGG.",
                "GGYYYYYYYYYYGGG.",
                ".gYYYYYYYYYYGg..",
                ".gGGGGGGGGGGGg..",
                "gg..........gg..",
                "g............g..",
        }, fp)};
        Object[] pp = {'b', 0xFF223454, 'W', 0xFFF4F8FF, 'P', 0xFF1A1018, 'O', 0xFFF29A2A, 'c', 0xFF3E5A86};
        penguin = new Sprite[]{spr(new String[]{
                "....bbbbb.....",
                "...bbbbbbb....",
                "..bbbWWbbWb...",
                "..bbWPWbWPb...",
                "..bbWWWWWWOO..",
                "..bbWWWWWWb...",
                ".cbbWWWWWWbb..",
                "cbbWWWWWWWWbb.",
                "cb.WWWWWWWW.bb",
                "c..WWWWWWWW..b",
                "...WWWWWWWW...",
                "...bWWWWWWb...",
                "....bbbbbb....",
                "...OOO..OOO...",
        }, pp), spr(new String[]{
                "..............",
                "....bbbbb.....",
                "...bbbbbbb....",
                "..bbbWWbbWb...",
                "..bbWPWbWPb...",
                "..bbWWWWWWOO..",
                ".cbbWWWWWWbb..",
                "cbbWWWWWWWWbbb",
                "cb.WWWWWWWW..b",
                "...WWWWWWWW...",
                "...WWWWWWWW...",
                "...bWWWWWWb...",
                "....bbbbbb....",
                "..OOO....OOO..",
        }, pp)};
        Object[] xp = {'Y', 0xFFFFE15A, 'O', 0xFFFF9A2A, 'R', 0xFFE83A1A, 'W', 0xFFFFFFFF, 'P', 0xFF3A0A0A};
        fire = new Sprite[]{spr(new String[]{
                ".....Y......",
                "....YY..Y...",
                "...YOOY.YY..",
                "..YOOOOYOY..",
                ".YORRROOOY..",
                ".YORRRRROOY.",
                "YORRWRRWROY.",
                "YORRPRRPROOY",
                "YORRRRRRRROY",
                ".YORRRRRROY.",
                "..YOOOOOOY..",
                "...YYYYYY...",
        }, xp), spr(new String[]{
                "......Y.....",
                "..Y..YY.....",
                ".YY.YOOY....",
                "..YOYOOOY...",
                "..YOOORROY..",
                ".YOORRRRROY.",
                ".YORWRRWRROY",
                "YOORPRRPRROY",
                "YORRRRRRRROY",
                ".YORRRRRROY.",
                "..YOOOOOOY..",
                "...YYYYYY...",
        }, xp)};
        fireball = new Sprite[]{fire[0], fire[1]};

        cannon = new Sprite[]{makeCannon()};
        crusher = new Sprite[]{makeCrusher(false), makeCrusher(true)};
        cannonBall = ball(10, 0xFF2A2A34, 0xFF6A6A7A);
        sandBall = ball(10, 0xFFC99A4E, 0xFFF2D49A);
        snowBall = ball(12, 0xFFE8F2FF, 0xFFFFFFFF);
        rock = ball(11, 0xFF8A7A6A, 0xFFB8A898);
        icicle = raw(new String[]{
                "IIIIIIII",
                ".IiiIIi.",
                ".IiIIIi.",
                "..IiIi..",
                "..IiIi..",
                "..IIIi..",
                "...Ii...",
                "...Ii...",
                "...II...",
                "....I...",
        }, 'I', 0xFF9AD8F8, 'i', 0xFFE8FAFF).outlined(0xFF2A5A8A);
        bulletP = raw(new String[]{
                "..YYYW",
                "OOYYYWW",
                "..YYYW",
        }, 'Y', 0xFFFFE040, 'W', 0xFFFFFFFF, 'O', 0xFFFF8A2A);

        // ---- items
        coin = new Sprite[4];
        int[] cw = {10, 7, 3, 7};
        for (int i = 0; i < 4; i++) coin[i] = coinFrame(cw[i], i == 2);
        gem = new Sprite[4];
        for (int i = 0; i < 4; i++) gem[i] = gemFrame(i);
        heart = raw(new String[]{
                ".RR...RR.",
                "RWRR.RRRR",
                "RWRRRRRRR",
                "RRRRRRRRR",
                ".RRRRRRr.",
                "..RRRRr..",
                "...RRr...",
                "....r....",
        }, 'R', 0xFFF03A4A, 'r', 0xFFB01A30, 'W', 0xFFFFFFFF).outlined(OUT);
        star = new Sprite[]{starFrame(0xFFFFE040, 0xFFFFA020), starFrame(0xFFFFF4A0, 0xFFFFC040)};
        potion = raw(new String[]{
                "...cc...",
                "...ww...",
                "..wGGw..",
                ".wGGGGw.",
                "wGGLGGGw",
                "wGLGGGGw",
                "wGGGGGGw",
                "wgGGGGgw",
                ".wggggw.",
        }, 'c', 0xFF8A5A2A, 'w', 0xFFDFF4FF, 'G', 0xFF4AD06A, 'g', 0xFF2A8A4A, 'L', 0xFFFFFFFF).outlined(OUT);
        spring0 = makeSpring(false);
        spring1 = makeSpring(true);
        flagGoal0 = makeGoalFlag(0);
        flagGoal1 = makeGoalFlag(1);
        ckOff = makeCheckpoint(0xFFC03A3A);
        ckOn = makeCheckpoint(0xFF3AC05A);
        trophy = raw(new String[]{
                "YYYYYYYYYYYY",
                "YyWYYYYYYYYy",
                "YyWYYYYYYYyY",
                ".YyWYYYYYYy.",
                "..YYYYYYYY..",
                "...YYYYYy...",
                "....YYYy....",
                ".....Yy.....",
                ".....Yy.....",
                "....yYYy....",
                "...YYYYYy...",
                "..yyyyyyyy..",
        }, 'Y', 0xFFFFD040, 'y', 0xFFC08A1A, 'W', 0xFFFFFFFF).outlined(OUT);

        puff = new Sprite[3];
        for (int i = 0; i < 3; i++) puff[i] = makePuff(i);
        sparkle = new Sprite[]{
                raw(new String[]{"..W..", "..W..", "WWWWW", "..W..", "..W.."}, 'W', 0xFFFFFFFF),
                raw(new String[]{"W...W", ".W.W.", "..W..", ".W.W.", "W...W"}, 'W', 0xFFFFF4A0)};

        hudHeart = heart;
        hudHeartEmpty = heart.silhouette(0xFF4A3A4A);
        hudCoin = coin[0];
        hudGem = gem[0];
        hudGemEmpty = gem[0].silhouette(0xFF3A3A5A);
        hudBullet = raw(new String[]{".YY..", "YYYYc", "YYYYc", ".YY.."}, 'Y', 0xFFFFD040, 'c', 0xFFC08A1A).outlined(OUT);
        hudBulletEmpty = hudBullet.silhouette(0xFF4A4A5A);

        node = makeNode(0xFFE04040, 0xFF902020);
        nodeDone = makeNode(0xFF40A0F0, 0xFF2060B0);
        castle = makeCastle(0xFF8A8A9A);
        castleDone = makeCastle(0xFFE0C050);
        lock = raw(new String[]{".kkk.", "k...k", "k...k", "YYYYY", "YYkYY", "YYkYY", "YYYYY"}, 'k', 0xFF6A6A7A, 'Y', 0xFFE0B030).outlined(OUT);
    }

    // ------------------------------------------------------------------ helpers
    static Sprite ball(int d, int c, int hi) {
        Sprite s = new Sprite(d, d);
        float r = d / 2f;
        for (int y = 0; y < d; y++)
            for (int x = 0; x < d; x++) {
                float dx = x + .5f - r, dy = y + .5f - r;
                float dd = (float) Math.sqrt(dx * dx + dy * dy);
                if (dd <= r - 0.3f) {
                    float l = -(dx + dy) / (r * 2.2f);
                    int col = l > 0.25f ? hi : l < -0.25f ? Gfx.shade(c, 0.7f) : c;
                    s.set(x, y, col);
                }
            }
        return s.outlined(OUT);
    }

    static Sprite coinFrame(int w, boolean edge) {
        Sprite s = new Sprite(10, 14);
        int x0 = (10 - w) / 2;
        for (int y = 0; y < 14; y++)
            for (int x = 0; x < w; x++) {
                float nx = (x + .5f) / w * 2 - 1, ny = (y + .5f) / 14f * 2 - 1;
                if (nx * nx + ny * ny > 1.05f) continue;
                int c = 0xFFFFC830;
                if (edge) c = 0xFFD08A10;
                else {
                    if (nx * nx + ny * ny > 0.55f) c = 0xFFE09A18;
                    if (w >= 7 && Math.abs(nx) < 0.25f && Math.abs(ny) < 0.5f) c = 0xFFFFF0A0;
                    if (nx < -0.3f && ny < -0.2f && nx * nx + ny * ny < 0.55f) c = 0xFFFFF4C0;
                }
                s.set(x0 + x, y, c);
            }
        return s.outlined(0xFF6A3A08);
    }

    static Sprite gemFrame(int f) {
        Sprite s = new Sprite(12, 13);
        int[] cols = {0xFF4AA8FF, 0xFF8AD0FF, 0xFF2A6ADA, 0xFFFFFFFF};
        for (int y = 0; y < 13; y++) {
            int hw = y < 4 ? 3 + y : Math.max(0, 6 - (y - 4) * 6 / 8);
            for (int x = 6 - hw; x < 6 + hw; x++) {
                int band = ((x - (6 - hw)) * 4 / Math.max(1, 2 * hw) + f) % 4;
                int c = y < 4 ? (band % 2 == 0 ? cols[1] : cols[0]) : (band < 2 ? cols[0] : cols[2]);
                s.set(x, y, c);
            }
        }
        s.set(4, 1, 0xFFFFFFFF);
        s.set(3, 2, 0xFFFFFFFF);
        return s.outlined(0xFF101A40);
    }

    static Sprite starFrame(int c, int c2) {
        Sprite s = new Sprite(15, 15);
        for (int y = 0; y < 15; y++)
            for (int x = 0; x < 15; x++) {
                double dx = x - 7, dy = y - 7.5;
                double a = Math.atan2(dy, dx) + Math.PI / 2;
                double r = Math.sqrt(dx * dx + dy * dy);
                double k = Math.cos(5 * a) * 0.5 + 0.5;
                double rmax = 3.2 + 4.3 * Math.pow(k, 2.2);
                if (r <= rmax) s.set(x, y, r < rmax - 2.5 ? c : c2);
            }
        s.set(5, 6, OUT);
        s.set(5, 7, OUT);
        s.set(9, 6, OUT);
        s.set(9, 7, OUT);
        return s.outlined(0xFF7A4A08);
    }

    static Sprite makeCannon() {
        Sprite s = new Sprite(16, 16);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int c = 0xFF3A3A48;
                if (x == 0 || y == 0) c = 0xFF5A5A6E;
                if (x == 15 || y == 15) c = 0xFF22222C;
                s.set(x, y, c);
            }
        for (int y = 3; y < 11; y++)
            for (int x = 3; x < 13; x++) {
                float dx = x - 7.5f, dy = y - 6.5f;
                if (dx * dx + dy * dy < 16) s.set(x, y, 0xFF14141A);
                if (dx * dx + dy * dy < 16 && dx * dx + dy * dy > 9) s.set(x, y, 0xFF8A8AA0);
            }
        // skull-ish marking
        s.set(6, 12, 0xFFE0E0E0);
        s.set(9, 12, 0xFFE0E0E0);
        s.set(7, 13, 0xFFE0E0E0);
        s.set(8, 13, 0xFFE0E0E0);
        return s;
    }

    static Sprite makeCrusher(boolean angry) {
        Sprite s = new Sprite(28, 30);
        for (int y = 0; y < 26; y++)
            for (int x = 0; x < 28; x++) {
                int c = 0xFF8A8C9E;
                if (((x * 7 + y * 13) % 11) == 0) c = 0xFF7A7C8E;
                if (x < 2 || y < 2) c = 0xFFB0B2C4;
                if (x > 25 || y > 23) c = 0xFF5A5C6E;
                s.set(x, y, c);
            }
        for (int x = 0; x < 28; x++) { // bottom spikes
            int k = x % 4;
            int hgt = k == 0 || k == 3 ? 1 : 4;
            for (int y = 26; y < 26 + hgt; y++) s.set(x, y, y - 26 < 2 ? 0xFFD0D2E0 : 0xFFA0A2B4);
        }
        int eye = angry ? 0xFFFF3A2A : 0xFFFFFFFF;
        for (int y = 8; y < 13; y++)
            for (int x = 0; x < 4; x++) {
                s.set(6 + x, y, eye);
                s.set(18 + x, y, eye);
            }
        for (int y = 10; y < 13; y++) {
            s.set(8, y, 0xFF101018);
            s.set(9, y, 0xFF101018);
            s.set(18, y, 0xFF101018);
            s.set(19, y, 0xFF101018);
        }
        for (int i = 0; i < 6; i++) { // brows
            s.set(5 + i, 5 + i / 2, 0xFF2A2A34);
            s.set(22 - i, 5 + i / 2, 0xFF2A2A34);
            s.set(5 + i, 6 + i / 2, 0xFF2A2A34);
            s.set(22 - i, 6 + i / 2, 0xFF2A2A34);
        }
        for (int x = 8; x < 20; x++) {
            s.set(x, 18, 0xFF2A2A34);
            if (x % 3 == 0) s.set(x, 19, 0xFFE8E8F0);
        }
        return s.outlined(OUT);
    }

    static Sprite makeSpring(boolean compressed) {
        int h = compressed ? 9 : 16;
        Sprite s = new Sprite(16, 16);
        int top = 16 - h;
        for (int x = 1; x < 15; x++) {
            s.set(x, top, 0xFFF04A4A);
            s.set(x, top + 1, 0xFFC02A2A);
            s.set(x, 14, 0xFF6A6A7A);
            s.set(x, 15, 0xFF4A4A5A);
        }
        for (int y = top + 2; y < 14; y++) {
            int k = (y - top) % 3;
            for (int x = 4; x < 12; x++) if (k == 0) s.set(x, y, 0xFFC8C8D8);
            s.set(k == 1 ? 4 : 11, y, 0xFFA0A0B0);
        }
        return s.outlined(OUT);
    }

    static Sprite makeGoalFlag(int f) {
        Sprite s = new Sprite(18, 12);
        for (int y = 0; y < 12; y++)
            for (int x = 0; x < 18; x++) {
                int wy = y + (int) Math.round(Math.sin((x + f * 3) * 0.6) * 1.2);
                if (wy < 0 || wy > 11) continue;
                boolean chk = ((x / 3) + (wy / 3)) % 2 == 0;
                s.set(x, y, chk ? 0xFFFFFFFF : 0xFF1A1A22);
            }
        return s.outlined(OUT);
    }

    static Sprite makeCheckpoint(int flag) {
        Sprite s = new Sprite(12, 32);
        for (int y = 2; y < 32; y++) {
            s.set(1, y, 0xFFD0D0E0);
            s.set(2, y, 0xFF8A8A9A);
        }
        s.set(1, 0, 0xFFFFD040);
        s.set(2, 0, 0xFFFFD040);
        s.set(1, 1, 0xFFFFD040);
        s.set(2, 1, 0xFFC08A1A);
        for (int y = 3; y < 11; y++)
            for (int x = 3; x < 3 + (11 - y) + 1 + (y < 7 ? y - 3 : 0); x++)
                if (x < 12) s.set(x, y, flag);
        for (int x = 0; x < 4; x++) {
            s.set(x, 31, 0xFF5A5A6A);
            s.set(x, 30, 0xFF7A7A8A);
        }
        return s.outlined(OUT);
    }

    static Sprite makePuff(int f) {
        int d = 10 + f * 3;
        Sprite s = new Sprite(d, d);
        float r = d / 2f;
        java.util.Random rnd = new java.util.Random(7 + f);
        for (int k = 0; k < 5; k++) {
            float cx = r + (rnd.nextFloat() - .5f) * r * 0.8f, cy = r + (rnd.nextFloat() - .5f) * r * 0.8f;
            float rr = r * (0.35f + rnd.nextFloat() * 0.25f) * (1 - f * 0.12f);
            for (int y = 0; y < d; y++)
                for (int x = 0; x < d; x++) {
                    float dx = x + .5f - cx, dy = y + .5f - cy;
                    if (dx * dx + dy * dy < rr * rr) s.set(x, y, dx + dy < -rr * 0.5f ? 0xFFFFFFFF : 0xFFE0E4EC);
                }
        }
        return s;
    }

    static Sprite makeNode(int c, int dark) {
        return raw(new String[]{
                "..cccc..",
                ".cWccccc",
                "cWccccccd",
                "cccccccd",
                "ccccccdd",
                ".cccddd.",
                "..dddd..",
        }, 'c', c, 'W', 0xFFFFFFFF, 'd', dark).outlined(OUT);
    }

    static Sprite makeCastle(int c) {
        int d = Gfx.shade(c, 0.7f);
        return raw(new String[]{
                "c.c.c....c.c.c",
                "ccccc....ccccc",
                "cdcdc.cc.cdcdc",
                "ccccc.cc.ccccc",
                "cccccccccccccc",
                "ccdccccccccdcc",
                "cccccccccccccc",
                "cccccKKKKccccc",
                "ccccKKKKKKcccc",
                "ccccKKKKKKcccc",
                "ddddKKKKKKdddd",
        }, 'c', c, 'd', d, 'K', 0xFF2A1A2A).outlined(OUT);
    }
}
