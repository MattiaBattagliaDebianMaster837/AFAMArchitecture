package it.pixelquest.core;

/** Sprites for the University of Palermo worlds: new enemies, projectiles and the professor bosses. */
public final class UniArt {
    static final int OUT = Art.OUT;
    public static Sprite[] turtle, shell, paper, book, vending, drone, lily;
    public static Sprite can, bomb, bookShot, chalk, qmark, apple, disc, sineOrb, laserHead, wave;
    public static Sprite[] arrow = new Sprite[8], bits, noise;
    public static Sprite[] martino, valenti, principato, cruciata, garbo;
    static boolean ready;

    static Sprite spr(String[] rows, Object... kv) {
        return Art.spr(rows, kv);
    }

    static Sprite raw(String[] rows, Object... kv) {
        return Art.raw(rows, kv);
    }

    public static synchronized void init() {
        if (ready) return;
        ready = true;
        // ---------------------------------------------------------------- turtle (four-legged, hex shell)
        Object[] tp = {'S', 0xFF2A9A8A, 's', 0xFF1A6A5E, 'h', 0xFF8AE0D0, 'K', 0xFFE8D0A0, 'k', 0xFFB89868, 'E', 0xFF1A1018, 'W', 0xFFFFFFFF};
        turtle = new Sprite[]{spr(new String[]{
                "....ssssss......",
                "...sShShShs.....",
                "..sSShSShSSs....",
                ".sShSSSSSShSs.KK",
                ".sSSShSShSSSsKWE",
                "sssssssssssssKKK",
                ".kKK.....KKk.KK.",
                ".KK.......KK....",
        }, tp), spr(new String[]{
                "....ssssss......",
                "...sShShShs.....",
                "..sSShSShSSs....",
                ".sShSSSSSShSs.KK",
                ".sSSShSShSSSsKWE",
                "sssssssssssssKKK",
                "..KKk....kKK..K.",
                "...KK....KK.....",
        }, tp)};
        shell = new Sprite[4];
        Sprite sh = spr(new String[]{
                "....ssssss....",
                "...sShShShs...",
                "..sSShSShSSs..",
                ".sShSSSSSShSs.",
                ".sSSShSShSSSs.",
                "ssssssssssssss",
                ".kkkkkkkkkkkk.",
        }, tp);
        shell[0] = sh;
        shell[1] = sh.flipped();
        shell[2] = raw(new String[]{
                ".....ssss.....",
                "...ssShhSss...",
                "..sShSSSShSs..",
                ".sShSSSSSShSs.",
                ".sSShSSSShSSs.",
                "ssssssssssssss",
                ".kkkkkkkkkkkk.",
        }, tp).outlined(OUT);
        shell[3] = shell[2].flipped();
        // ---------------------------------------------------------------- exam sheet (flying)
        Object[] pp = {'W', 0xFFF8F8F0, 'w', 0xFFD8D8D0, 'l', 0xFF8AA0C8, 'R', 0xFFE03A3A, 'E', 0xFF1A1018};
        paper = new Sprite[]{spr(new String[]{
                "WWWWWWWWWW..",
                "WllllllWWWW.",
                "WWWWWWWWWWWW",
                "WlllllWERWEW",
                "WWWWWWWWRRWW",
                "WllllWWWWWWW",
                "WWWWWWWWWWWw",
                ".wwwwwwwwww.",
        }, pp), spr(new String[]{
                "............",
                ".WWWWWWWWWW.",
                "WllllllWWWWW",
                "WWWWWWWWWWWW",
                "WlllllWERWEW",
                "WWWWWWWWRRWW",
                "WllllWWWWWWw",
                "wwwwwwwwwww.",
        }, pp)};
        // ---------------------------------------------------------------- biting book
        Object[] bp = {'C', 0xFFC03A3A, 'c', 0xFF8A1A2A, 'P', 0xFFF8F0E0, 'T', 0xFFFFFFFF, 'E', 0xFFFFE040, 'e', 0xFF1A1018, 'G', 0xFFE8C040};
        book = new Sprite[]{spr(new String[]{
                "CCCCCCCCCCC.",
                "CGCCCCCCCGCC",
                "CCCEeCCEeCCC",
                "CCCCCCCCCCCC",
                "PPPPPPPPPPPC",
                "TPTPTPTPTPPC",
                "cccccccccccc",
        }, bp), spr(new String[]{
                "CCCCCCCCCCC.",
                "CGCCCCCCCGCC",
                "CCCEeCCEeCCC",
                "PTPTPTPTPTPC",
                "............",
                "TPTPTPTPTPPC",
                "cccccccccccc",
        }, bp)};
        // ---------------------------------------------------------------- vending machine
        Object[] vp = {'R', 0xFFD02A3A, 'r', 0xFF8A1A26, 'G', 0xFF9AD8F8, 'g', 0xFF4A8AB0, 'K', 0xFF2A2A34, 'Y', 0xFFFFD040, 'W', 0xFFFFFFFF, 'O', 0xFF3AC05A};
        Sprite vm = spr(new String[]{
                "rRRRRRRRRRRRRr",
                "rRWWWWWWWWWRRr",
                "rRRRRRRRRRRRRr",
                "rGGGGGGGGgRKKr",
                "rGYGOGYGOgRYYr",
                "rGGGGGGGGgRKKr",
                "rGOGYGOGYgRKKr",
                "rGGGGGGGGgRRRr",
                "rGYGOGYGOgRKKr",
                "rGGGGGGGGgRKKr",
                "rRRRRRRRRRRRRr",
                "rRKKKKKKKKRRRr",
                "rRKKKKKKKKRRRr",
                "rRRRRRRRRRRRRr",
                "rrrrrrrrrrrrrr",
                ".K..........K.",
        }, vp);
        vending = new Sprite[]{vm, vm.copy()};
        for (int y = 4; y < 9; y++) vending[1].set(12, y, 0xFFFF6A6A);
        // ---------------------------------------------------------------- security drone
        Object[] dp = {'K', 0xFF2A2A34, 'k', 0xFF4A4A5A, 'R', 0xFFFF3A3A, 'W', 0xFFE8E8F0, 'B', 0xFF6AD8FF};
        drone = new Sprite[]{spr(new String[]{
                "WWWW......WWWW",
                "..k........k..",
                "..kkkkkkkkkk..",
                ".kKKKKKKKKKKk.",
                ".kKKBBBBBBKKk.",
                ".kKKKKRRKKKKk.",
                "..kkkkkkkkkk..",
                "....k....k....",
        }, dp), spr(new String[]{
                ".WWWW....WWWW.",
                "..k........k..",
                "..kkkkkkkkkk..",
                ".kKKKKKKKKKKk.",
                ".kKKBBBBBBKKk.",
                ".kKKKKKKKKKKk.",
                "..kkkkkkkkkk..",
                "....k....k....",
        }, dp)};
        laserHead = spr(new String[]{"KKKKKKKK", "kkRRRRkk", ".kRRRRk.", "..kRRk.."}, dp);
        // ---------------------------------------------------------------- projectiles
        can = spr(new String[]{".ww.", "RRRR", "RWWR", "RRRR", "RWRR", "RRRR", ".ww."}, 'R', 0xFFE03A3A, 'W', 0xFFFFFFFF, 'w', 0xFFB0B0C0);
        bomb = spr(new String[]{"...Y.", "..kO.", ".KKK.", "KKkKK", "KKKKK", ".KKK."}, 'K', 0xFF1A1A22, 'k', 0xFF6A6A7A, 'O', 0xFF8A6A3A, 'Y', 0xFFFFD040);
        bookShot = spr(new String[]{"BBBBBBB", "BYBBBBB", "BBBBBBB", "WWWWWWB", "bbbbbbb"}, 'B', 0xFF3A6AC0, 'b', 0xFF1A3A80, 'W', 0xFFF8F0E0, 'Y', 0xFFFFD040);
        chalk = spr(new String[]{"WWWWWw", "WWWWWw"}, 'W', 0xFFFFFFFF, 'w', 0xFFD0D0D8);
        qmark = spr(new String[]{".RRR.", "R...R", "...R.", "..R..", ".....", "..R.."}, 'R', 0xFFE83A3A);
        apple = spr(new String[]{"..g..", ".RgR.", "RRRRR", "RWRRR", "RRRRR", ".RRR."}, 'R', 0xFFE03A3A, 'W', 0xFFFFC0C0, 'g', 0xFF3A9A3A);
        disc = spr(new String[]{".OOOOOO.", "OYYYYYYO", ".OOOOOO."}, 'O', 0xFFFF7A2A, 'Y', 0xFFFFD040);
        sineOrb = Art.orb(0xFFFFFFFF, 0xFF8AF0FF, 0xFF2A9AE0, 4).outlined(0xFF0A3A6A);
        wave = spr(new String[]{".W..W.", "W.WW.W", ".W..W."}, 'W', 0xFFB0F0FF);
        for (int d = 0; d < 8; d++) arrow[d] = makeArrow(d);
        bits = new Sprite[]{bitSprite('0'), bitSprite('1')};
        int[] nc = {0xFFFF4A4A, 0xFF4AFF6A, 0xFF4A8AFF, 0xFFFFE04A, 0xFFFF4AE0, 0xFF4AFFF0};
        noise = new Sprite[nc.length];
        for (int i = 0; i < nc.length; i++) {
            Sprite n = new Sprite(5, 5);
            for (int y = 0; y < 5; y++)
                for (int x = 0; x < 5; x++) if (((x * 3 + y * 7 + i) % 4) != 0) n.set(x, y, nc[i]);
            noise[i] = n.outlined(0xFF101018);
        }
        // ---------------------------------------------------------------- bosses
        martino = person(0xFFE2B08A, 0xFF3A2416, HAIR_SHORT, false, 0, 0xFF3A7A4A, 0xFFF0F0F0, 0xFF2A4A2A, 0xFF34405E, false, false, ACC_BOOKS);
        valenti = person(0xFFEAC0A0, 0xFF2A1A12, HAIR_LONG, true, 0xFFB03040, 0xFF7A3A8A, 0xFFF0E8F0, 0xFF7A3A8A, 0xFF2A2A34, false, true, ACC_CHALK);
        principato = person(0xFFE0B494, 0xFFE8E8EC, HAIR_BALD, true, 0xFF3A3A44, 0xFF6A4A2A, 0xFFF0F0E8, 0xFF8A2A2A, 0xFF4A3A2A, true, false, ACC_NONE);
        cruciata = person(0xFFDCAA86, 0xFF14100E, HAIR_SPIKY, false, 0, 0xFF2A5AB0, 0xFF2A5AB0, 0xFF1A3A80, 0xFF2A2A34, false, false, ACC_DISC);
        garbo = person(0xFFE0B090, 0xFF8A8A92, HAIR_SHORT, true, 0xFF1A1A22, 0xFF2A2A3C, 0xFFF4F4F8, 0xFFC02A2A, 0xFF22222E, false, false, ACC_SCOPE);
        lily = new Sprite[]{blob(0), blob(1), blob(2)};
    }

    static Sprite bitSprite(char c) {
        Sprite s = new Sprite(5, 7);
        for (int gy = 0; gy < 7; gy++)
            for (int gx = 0; gx < 5; gx++) if (Font.pixel(c, gx, gy)) s.set(gx, gy, 0xFF3AFF6A);
        return s.outlined(0xFF0A2A12);
    }

    static Sprite makeArrow(int dir) {
        Sprite base = raw(new String[]{"......Y...", "......YY..", "YYYYYYYYY.", "YYYYYYYYYY", "YYYYYYYYY.", "......YY..", "......Y..."}, 'Y', 0xFFFFE040);
        Sprite s = new Sprite(14, 14);
        double a = dir * Math.PI / 4;
        for (int y = 0; y < 14; y++)
            for (int x = 0; x < 14; x++) {
                double dx = x - 6.5, dy = y - 6.5;
                double u = dx * Math.cos(a) + dy * Math.sin(a) + 5, v = -dx * Math.sin(a) + dy * Math.cos(a) + 3.5;
                int iu = (int) Math.floor(u), iv = (int) Math.floor(v);
                if (iu >= 0 && iv >= 0 && iu < base.w && iv < base.h && (base.get(iu, iv) >>> 24) != 0) s.set(x, y, 0xFFFFE040);
            }
        return s.outlined(0xFF6A4A0A);
    }

    // ------------------------------------------------------------ professor generator
    static final int HAIR_SHORT = 0, HAIR_LONG = 1, HAIR_BALD = 2, HAIR_SPIKY = 3;
    static final int ACC_NONE = 0, ACC_BOOKS = 1, ACC_CHALK = 2, ACC_DISC = 3, ACC_SCOPE = 4;

    static void box(Sprite s, int x, int y, int w, int h, int c) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) s.set(xx, yy, c);
    }

    /** Two frames (idle, attack) of a cartoon professor / student, facing left, 36x52 before outline. */
    static Sprite[] person(int skin, int hair, int hairStyle, boolean glasses, int glassC, int jacket, int shirt, int tie,
                           int pants, boolean mustache, boolean female, int acc) {
        Sprite[] out = new Sprite[2];
        for (int f = 0; f < 2; f++) {
            Sprite s = new Sprite(36, 52);
            int skinSh = Gfx.shade(skin, 0.85f), jSh = Gfx.shade(jacket, 0.75f), jHi = Gfx.shade(jacket, 1.2f);
            // legs + shoes
            box(s, 12, 38, 5, 10, pants);
            box(s, 19, 38, 5, 10, Gfx.shade(pants, 0.85f));
            box(s, 10, 48, 7, 3, 0xFF1A1A20);
            box(s, 19, 48, 7, 3, 0xFF1A1A20);
            // torso (jacket) with shirt and tie
            for (int y = 22; y < 40; y++) {
                int hw = 9 + (y - 22) / 6;
                for (int x = 18 - hw; x < 18 + hw; x++) s.set(x, y, x < 18 - hw + 2 ? jSh : jacket);
            }
            for (int y = 22; y < 31; y++) {
                int hw = 3 - (y - 22) / 3;
                for (int x = 18 - hw; x <= 18 + hw; x++) s.set(x, y, shirt);
            }
            if (tie != jacket) box(s, 17, 23, 2, 10, tie);
            s.set(11, 30, jHi);
            s.set(24, 32, jHi);
            // back arm
            box(s, 25, 24, 4, 12, jSh);
            box(s, 25, 36, 4, 3, skinSh);
            // front arm: down (idle) or raised forward (attack), facing left
            if (f == 0) {
                box(s, 7, 24, 4, 12, jacket);
                box(s, 7, 36, 4, 3, skin);
            } else {
                box(s, 1, 22, 10, 4, jacket);
                box(s, 0, 21, 3, 5, skin);
            }
            // head
            for (int y = 2; y < 22; y++)
                for (int x = 8; x < 28; x++) {
                    float dx = (x + 0.5f - 18) / 9.5f, dy = (y + 0.5f - 12) / 10f;
                    if (dx * dx + dy * dy <= 1) s.set(x, y, dx > 0.55f ? skinSh : skin);
                }
            box(s, 26, 10, 2, 5, skinSh); // ear
            // hair
            switch (hairStyle) {
                case HAIR_SHORT:
                    for (int x = 8; x < 28; x++) for (int y = 1; y < 6 + (x > 22 ? 4 : 0); y++) {
                        float dx = (x + 0.5f - 18) / 10f, dy = (y + 0.5f - 11) / 10.5f;
                        if (dx * dx + dy * dy <= 1) s.set(x, y, hair);
                    }
                    break;
                case HAIR_LONG:
                    for (int x = 7; x < 29; x++) for (int y = 1; y < 30; y++) {
                        float dx = (x + 0.5f - 18) / 10.5f, dy = (y + 0.5f - 11) / 11f;
                        boolean top = y < 6 && dx * dx + dy * dy <= 1;
                        boolean sides = (x < 10 || x > 25) && y >= 5 && y < 30 && Math.abs(dx) <= 1.05f;
                        if (top || sides) s.set(x, y, hair);
                    }
                    break;
                case HAIR_BALD:
                    for (int y = 7; y < 15; y++) {
                        box(s, 7, y, 3, 1, hair);
                        box(s, 26, y, 3, 1, hair);
                    }
                    s.set(15, 3, 0xFFFFFFFF);
                    s.set(16, 3, 0xFFFFFFFF);
                    break;
                default: // spiky
                    for (int k = 0; k < 7; k++) {
                        int bx = 8 + k * 3;
                        for (int y = 0; y < 6; y++) for (int x = bx; x < bx + 3; x++) if (x - bx <= y / 2 + 1) s.set(x, y + (k % 2), hair);
                    }
                    box(s, 8, 4, 20, 3, hair);
                    break;
            }
            // face (looking left)
            int eyeY = 11;
            box(s, 11, eyeY, 2, 2, 0xFF1A1018);
            box(s, 18, eyeY, 2, 2, 0xFF1A1018);
            if (glasses) {
                for (int x = 9; x < 15; x++) { s.set(x, eyeY - 2, glassC); s.set(x, eyeY + 3, glassC); }
                for (int x = 16; x < 22; x++) { s.set(x, eyeY - 2, glassC); s.set(x, eyeY + 3, glassC); }
                for (int y = eyeY - 2; y <= eyeY + 3; y++) { s.set(9, y, glassC); s.set(14, y, glassC); s.set(16, y, glassC); s.set(21, y, glassC); }
                s.set(15, eyeY, glassC);
            }
            // brows: grumpy professors frown
            boolean frown = hairStyle == HAIR_BALD || acc == ACC_SCOPE;
            box(s, 10, eyeY - (frown ? 3 : 4), 4, 1, hair == 0xFFE8E8EC ? 0xFFB0B0B8 : Gfx.shade(hair, 0.8f));
            box(s, 17, eyeY - (frown ? 3 : 4), 4, 1, hair == 0xFFE8E8EC ? 0xFFB0B0B8 : Gfx.shade(hair, 0.8f));
            if (frown) { s.set(13, eyeY - 4, Gfx.shade(hair, 0.8f)); s.set(17, eyeY - 4, Gfx.shade(hair, 0.8f)); }
            s.set(9, 15, skinSh); // nose
            s.set(10, 15, skinSh);
            if (mustache) box(s, 10, 17, 9, 2, 0xFFE8E8EC);
            if (frown) box(s, 12, 19, 6, 1, 0xFF6A2A2A);
            else {
                box(s, 12, 18, 6, 1, 0xFF6A2A2A);
                s.set(11, 17, 0xFF6A2A2A);
                s.set(18, 17, 0xFF6A2A2A);
            }
            if (female) { s.set(26, 14, 0xFFFFD040); } // earring
            // accessory
            switch (acc) {
                case ACC_BOOKS:
                    if (f == 0) { box(s, 4, 30, 9, 3, 0xFFC03A3A); box(s, 4, 33, 9, 3, 0xFF3A6AC0); box(s, 4, 36, 9, 3, 0xFF3AA05A); }
                    else box(s, 0, 14, 7, 6, 0xFFC03A3A);
                    break;
                case ACC_CHALK:
                    if (f == 1) box(s, 0, 19, 2, 2, 0xFFFFFFFF);
                    else box(s, 6, 38, 2, 3, 0xFFFFFFFF);
                    break;
                case ACC_DISC:
                    box(s, 26, 2, 2, 12, 0xFF1A1A22); // headphones band
                    box(s, 25, 9, 4, 5, 0xFF1A1A22);
                    if (f == 1) box(s, 0, 18, 6, 2, 0xFFFF7A2A);
                    break;
                case ACC_SCOPE: { // handheld oscilloscope with a sine on screen
                    int ox = f == 0 ? 3 : 0, oy = f == 0 ? 30 : 13;
                    box(s, ox, oy, 10, 8, 0xFF3A3A44);
                    box(s, ox + 1, oy + 1, 8, 5, 0xFF0A2A12);
                    for (int x = 0; x < 8; x++) s.set(ox + 1 + x, oy + 3 + (int) Math.round(Math.sin(x * 0.9) * 1.6), 0xFF3AFF6A);
                    break;
                }
                default:
                    break;
            }
            out[f] = s.outlined(OUT);
        }
        return out;
    }

    /** Lily: a purple blob with glasses and floating binary digits. */
    static Sprite blob(int f) {
        int w = 44, h = 36;
        Sprite s = new Sprite(w, h);
        float sq = f == 1 ? 1.12f : f == 2 ? 0.9f : 1f;
        float rx = 19 * sq, ry = 15 / sq;
        float cx = w / 2f, cy = h - ry - 1;
        int c = 0xFF9A5AE0, cd = 0xFF6A30B0, cl = 0xFFD0A8FF;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float dx = (x + .5f - cx) / rx, dy = (y + .5f - cy) / ry;
                float d = dx * dx + dy * dy;
                if (d <= 1) s.set(x, y, (dx + dy < -0.9f) ? cl : d > 0.75f && dy > 0 ? cd : c);
            }
        int ey = (int) (cy - ry * 0.25f);
        // glasses + eyes
        for (int k = 0; k < 2; k++) {
            int ex = (int) cx - 11 + k * 12;
            box(s, ex, ey - 4, 9, 8, 0xFFFFFFFF);
            box(s, ex + 3, ey - 2, 3, 4, 0xFF1A1018);
            for (int x = ex - 1; x < ex + 10; x++) { s.set(x, ey - 5, 0xFF1A1A22); s.set(x, ey + 4, 0xFF1A1A22); }
            for (int y = ey - 5; y <= ey + 4; y++) { s.set(ex - 1, y, 0xFF1A1A22); s.set(ex + 9, y, 0xFF1A1A22); }
        }
        box(s, (int) cx - 3, ey - 1, 2, 1, 0xFF1A1A22);
        box(s, (int) cx - 6, ey + 8, 10, 2, 0xFF3A1A5A); // smile
        s.set((int) cx - 7, ey + 7, 0xFF3A1A5A);
        s.set((int) cx + 4, ey + 7, 0xFF3A1A5A);
        // a little bow on top
        box(s, (int) cx + 5, (int) (cy - ry) - 1, 3, 3, 0xFFFF6AB0);
        box(s, (int) cx + 9, (int) (cy - ry) - 1, 3, 3, 0xFFFF6AB0);
        box(s, (int) cx + 8, (int) (cy - ry), 1, 1, 0xFFC03A80);
        return s.outlined(OUT);
    }
}
