package it.pixelquest.core;

import java.util.HashMap;

/**
 * Pixel art for the playable heroes. Heads are hand drawn (from the reference photos),
 * bodies are composed procedurally from poses so every animation frame stays consistent.
 */
public final class HeroArt {
    // animation frame ids
    public static final int IDLE0 = 0, IDLE1 = 1, WALK0 = 2, RUN0 = 6, JUMP = 10, FALL = 11, SKID = 12, WALL = 13,
            ACT0 = 14, ACT1 = 15, HURT = 16, FRONT = 17, BACK = 18, VICTORY = 19, DEAD = 20, TUCK = 21, CROUCH = 22;
    public static final int FRAMES = 23;
    public static final int FW = 20, FH = 28;

    /** Visual description of one hero. */
    public static final class Look {
        int skin, skinSh, pants, pantsSh, shoe, sole, outline;
        int jacket, jacketSh, jacketLt, yoke, yokeSh, trim;
        int style; // 0 = Mattia fleece, 1 = Giuseppe hoodie
        Sprite headSide, headFront, headBack;
    }

    static HashMap<Character, Integer> pal(Object... kv) {
        HashMap<Character, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Character) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    // ------------------------------------------------------------------ Mattia
    public static Look mattia() {
        Look l = new Look();
        l.skin = 0xFFE8B592;
        l.skinSh = 0xFFC98F6C;
        l.pants = 0xFF3B4A6E;
        l.pantsSh = 0xFF2A3452;
        l.shoe = 0xFFF2F0EA;
        l.sole = 0xFF9A9AA6;
        l.outline = 0xFF1A0F10;
        l.jacket = 0xFFA8481F;   // rust sherpa
        l.jacketSh = 0xFF7C3014;
        l.jacketLt = 0xFFC2602F;
        l.yoke = 0xFFEDE2CB;     // cream panels
        l.yokeSh = 0xFFCDBD9E;
        l.trim = 0xFFF8F8F8;     // zipper
        l.style = 0;
        HashMap<Character, Integer> p = pal(
                'H', 0xFF2B1A12, 'h', 0xFF573829, 'k', 0xFF1C110B,
                'S', l.skin, 's', l.skinSh, 'E', 0xFF2A170E, 'W', 0xFFF6EEE8,
                'B', 0xFF1E110A, 'm', 0xFFB0644F, 'e', 0xFFD69E7C);
        l.headSide = Sprite.parse(new String[]{
                "...hH.hHH.h...",
                "..HHHhHHHhHH..",
                ".hHHHHHHhHHHHk",
                "HHHhHHHHHHHhHH",
                "HHHHHHhHHHHHHH",
                "HhHHHHHHHHhHHH",
                "HHHHHHHSHHHSHH",
                "HHHHHSSSSSSSSS",
                "HHHeeSBBBSSBBS",
                ".HHeeSWESSSWES",
                ".HHHsSSSSSSSsS",
                "..HHsSSSSSmmS.",
                "...ssSSSSSSS..",
        }, p);
        l.headFront = Sprite.parse(new String[]{
                "..hHH.hHH.Hh..",
                ".HHHHhHHHHHHH.",
                "hHHHHHHHhHHHHH",
                "HHhHHHHHHHHhHH",
                "HHHHHhHHHHHHHH",
                "HHHHHHHHHHHHHH",
                "HHHSHHHSHHSHHH",
                "HHSSSSSSSSSSHH",
                "HeSBBBSSSBBBeH",
                "HeSWESSSSSEWeH",
                ".eSSSSSsSSSSe.",
                "..SSSSmmmSSS..",
                "...sSSSSSSs...",
        }, p);
        l.headBack = Sprite.parse(new String[]{
                "..hHH.hHH.Hh..",
                ".HHHHhHHHHHHH.",
                "hHHHHHHHhHHHHH",
                "HHhHHHHHHHHhHH",
                "HHHHHhHHHHHHHH",
                "HHHHHHHHHHhHHH",
                "HHhHHHHHHHHHHH",
                "HHHHHHHhHHHHHH",
                "HeHHhHHHHHHHeH",
                "HeHHHHHHHhHHeH",
                ".HHHHHHHHHHHH.",
                "..HHHHHHHHHH..",
                "...sSSSSSSs...",
        }, p);
        return l;
    }

    // ---------------------------------------------------------------- Giuseppe
    public static Look giuseppe() {
        Look l = new Look();
        l.skin = 0xFFDDA27E;
        l.skinSh = 0xFFB97E5C;
        l.pants = 0xFF2C2C34;
        l.pantsSh = 0xFF1E1E24;
        l.shoe = 0xFFF2F0EA;
        l.sole = 0xFF8A8A96;
        l.outline = 0xFF1A0F10;
        l.jacket = 0xFFB9533B;   // terracotta hoodie
        l.jacketSh = 0xFF8C3A28;
        l.jacketLt = 0xFFD06C51;
        l.yoke = 0xFFB9533B;
        l.yokeSh = 0xFF8C3A28;
        l.trim = 0xFFEADBC9;     // drawstrings
        l.style = 1;
        HashMap<Character, Integer> p = pal(
                'H', 0xFF4A2E1D, 'h', 0xFF7A5234, 'k', 0xFF2E1B10,
                'S', l.skin, 's', l.skinSh, 'E', 0xFF2A170E, 'W', 0xFFF6EEE8,
                'G', 0xFF1C4A33, 'g', 0xFF8FD3B0, 'D', 0xFF5E3D28, 'd', 0xFF8A6046,
                'm', 0xFFB0644F, 'e', 0xFFCC9370);
        l.headSide = Sprite.parse(new String[]{
                "....hH.hHh....",
                "..hHHHhHHHHh..",
                ".HHHhHHHHhHHH.",
                "HHHHHHHHHHHHHH",
                "HHhHHHHhHHHhHH",
                "HHHHHHSHHHSHHH",
                "HHHHSSSSSSSSSS",
                "HHHeGGGGSGGGGS",
                "HHHeGgESGGgEGS",
                ".HHsGGGGSGGGGS",
                ".HHdDSSSSSSSsS",
                "..dDDDDSSmmDS.",
                "...dDDDDDDDD..",
        }, p);
        l.headFront = Sprite.parse(new String[]{
                "...hHH.hHHh...",
                ".hHHHHhHHHHHh.",
                "HHHhHHHHHhHHHH",
                "HHHHHHHHHHHHHH",
                "HHHHhHHHHHHhHH",
                "HHHSHHHSHHSHHH",
                "HHSSSSSSSSSSHH",
                "HeGGGGSSGGGGeH",
                "HeGgEGGGGEgGeH",
                ".eGGGGSSGGGGe.",
                ".dDSSSSsSSSDd.",
                "..dDDDmmDDDd..",
                "...dDDDDDDd...",
        }, p);
        l.headBack = Sprite.parse(new String[]{
                "...hHH.hHHh...",
                ".hHHHHhHHHHHh.",
                "HHHhHHHHHhHHHH",
                "HHHHHHHHHHHHHH",
                "HHHHhHHHHHHhHH",
                "HHHHHHHHHHHHHH",
                "HHhHHHHHHhHHHH",
                "HHHHHHHHHHHHHH",
                "HGHHHHhHHHHHGH",
                "HeHHHHHHHHHHeH",
                ".HHHHHHHHHHHH.",
                "..HHHHHHHHHH..",
                "...sSSSSSSs...",
        }, p);
        return l;
    }

    static Look tee(int skin, int skinSh, int shirt, int shirtSh, int shirtLt, int pants, int pantsSh, int shoe, int sole, int style) {
        Look l = new Look();
        l.skin = skin;
        l.skinSh = skinSh;
        l.jacket = shirt;
        l.jacketSh = shirtSh;
        l.jacketLt = shirtLt;
        l.yoke = shirt;
        l.yokeSh = shirtSh;
        l.trim = shirtLt;
        l.pants = pants;
        l.pantsSh = pantsSh;
        l.shoe = shoe;
        l.sole = sole;
        l.outline = 0xFF1A0F10;
        l.style = style;
        return l;
    }

    // -------------------------------------------------------------- Francesco
    public static Look francesco() {
        Look l = tee(0xFFE4B48E, 0xFFC48E6A, 0xFFEEE6D6, 0xFFCDC3AE, 0xFFFFFFFF, 0xFF34405E, 0xFF252E46, 0xFFF2F0EA, 0xFF9A9AA6, 2);
        HashMap<Character, Integer> p = pal(
                'H', 0xFF1C1210, 'h', 0xFF3E2C22, 'S', l.skin, 's', l.skinSh, 'E', 0xFF2A160E, 'W', 0xFFFFFFFF,
                'B', 0xFF160E0A, 'd', 0xFFB08A6E, 'm', 0xFF5A1A1E, 'r', 0xFFB8C0CC, 'T', 0xFFF4F0EA, 't', 0xFFE8909A,
                'e', 0xFFD29C7A);
        l.headSide = Sprite.parse(new String[]{
                "..hHH.HHh.Hh..",
                ".HHHhHHHHHHHH.",
                "HHhHHHHHhHHHHH",
                "HHHHHHhHHHHHHH",
                "HhHHHHHHHHHhHH",
                "HHHHHHHHHHHHHH",
                "HHHHHSHHHSSHHS",
                "HHHHSBBSSSBBSS",
                "HHHeSWESSSWESS",
                ".HHeSWESSSWESs",
                ".HHdSSSSSSSssS",
                "..ddmTrTrTmSS.",
                "...dmmttmmdd..",
        }, p);
        l.headFront = Sprite.parse(new String[]{
                "..hHH.hHHh.H..",
                ".HHHHhHHHHHHH.",
                "hHHHHHHHhHHHHH",
                "HHhHHHHHHHHhHH",
                "HHHHHhHHHHHHHH",
                "HHHHHHHHHHHHHH",
                "HHHSHHSSHHSHHH",
                "HHSBBSSSSBBSHH",
                "HeSWESSSSSWESe",
                "HeSWESSsSSWESe",
                ".dSSSSSSSSSSd.",
                "..dmTrTrTrTmd.",
                "...dmmttmmd...",
        }, p);
        l.headBack = Sprite.parse(new String[]{
                "..hHH.hHHh.H..",
                ".HHHHhHHHHHHH.",
                "hHHHHHHHhHHHHH",
                "HHhHHHHHHHHhHH",
                "HHHHHhHHHHHHHH",
                "HHHHHHHHHHhHHH",
                "HHhHHHHHHHHHHH",
                "HHHHHHHhHHHHHH",
                "HeHHhHHHHHHHeH",
                "HeHHHHHHHhHHeH",
                ".HHHHHHHHHHHH.",
                "..HHHHHHHHHH..",
                "...sSSSSSSs...",
        }, p);
        return l;
    }

    // ----------------------------------------------------------------- Andrea
    public static Look andrea() {
        Look l = tee(0xFFE8B898, 0xFFC8967A, 0xFF2C2C34, 0xFF18181E, 0xFF4A4A54, 0xFF6A6A78, 0xFF4E4E5A, 0xFF22222A, 0xFFE8E8E8, 3);
        HashMap<Character, Integer> p = pal(
                'H', 0xFF3A2218, 'h', 0xFF5C3A28, 'S', l.skin, 's', l.skinSh, 'E', 0xFF2A170E, 'W', 0xFFF6EEE8,
                'B', 0xFF2A1810, 'm', 0xFFB07060, 'e', 0xFFD8A688);
        l.headSide = Sprite.parse(new String[]{
                "...hHHHHh.....",
                "..HHHHHHHHH...",
                ".HHHHHHHHHHH..",
                "HHHHHHHHHHHHH.",
                "HHHHHHHHHHHHHH",
                "HHHHhHHHHHHHHH",
                "HHHHHHHHHHHHSS",
                "HHHeSBBBSSBBBS",
                "HHHeSWESSSSWES",
                ".HHsSSSSSSSSsS",
                ".HHSSSSSSSSSSS",
                "..sSSSSSmmmSS.",
                "...sSSSSSSSS..",
        }, p);
        l.headFront = Sprite.parse(new String[]{
                "...hHHHHHh....",
                ".hHHHHHHHHHHh.",
                "HHHHHHHHHHHHHH",
                "HHHHHHHHHHHHHH",
                "HHHHhHHHHHHHHH",
                "HHHHHHHHHHHSHH",
                "HHHHHHHHSSSSSH",
                "HeSBBBSSSBBBSe",
                "HeSSWESSSWESSe",
                ".eSSSSSsSSSSe.",
                ".SSSSSSSSSSSS.",
                "..SSSSmmmSSS..",
                "...sSSSSSSs...",
        }, p);
        l.headBack = Sprite.parse(new String[]{
                "...hHHHHHh....",
                ".hHHHHHHHHHHh.",
                "HHHHHHHHHHHHHH",
                "HHHHHHhHHHHHHH",
                "HHHHHHHHHHHHHH",
                "HHHHHHHHHHHHHH",
                "HHHHhHHHHHHHHH",
                "HHHHHHHHHHHHHH",
                "HeHHHHHHHHHHeH",
                "HeHHHHHHHHHHeH",
                ".HHHHHHHHHHHH.",
                "..sHHHHHHHHs..",
                "...sSSSSSSs...",
        }, p);
        return l;
    }

    // ------------------------------------------------------------------ Paolo
    public static Look paolo() {
        Look l = tee(0xFFE6B694, 0xFFC4906E, 0xFFC9C9CF, 0xFFA2A2AA, 0xFFE4E4EA, 0xFF3E5A8E, 0xFF2C4268, 0xFFF2F0EA, 0xFF8A8A96, 4);
        HashMap<Character, Integer> p = pal(
                'H', 0xFF4A2E1E, 'h', 0xFF7A5638, 'S', l.skin, 's', l.skinSh, 'E', 0xFF2A170E, 'W', 0xFFFFFFFF,
                'B', 0xFF2A1A10, 'D', 0xFF2E1C12, 'd', 0xFF4E3424, 'T', 0xFFF6F2EA, 'e', 0xFFD4A282);
        l.headSide = Sprite.parse(new String[]{
                "...hHHHh......",
                "..HHHHHHHH....",
                ".HHHHhHHHHH...",
                "HHHHHHHHHHHH..",
                "HHHHHHHHHHHHH.",
                "HHHHHHHhHHHHHH",
                "HHHHSSHHHHHHSS",
                "HHHeSBBSSSBBSS",
                "HHHeSWESSSWESS",
                ".HHdSSSSSSSSsS",
                ".HDDDSSSSSDDDS",
                "..DDDTTTTTTDD.",
                "...DDDDDDDDD..",
        }, p);
        l.headFront = Sprite.parse(new String[]{
                "...hHHHHh.....",
                "..HHHHHHHHHh..",
                ".HHHHHHHHHHHH.",
                "HHHHHHHHHHHHHH",
                "HHHHHHhHHHHHHH",
                "HHHHHHHHHHHHHH",
                "HHSSSHHHHHSSSH",
                "HeSBBBSSSBBBSe",
                "HeSSWESSSWESSe",
                ".dSSSSSsSSSSd.",
                ".DDDDSSSSDDDD.",
                ".DDDTTTTTTDDD.",
                "..DDDDDDDDDD..",
        }, p);
        l.headBack = Sprite.parse(new String[]{
                "...hHHHHh.....",
                "..HHHHHHHHHh..",
                ".HHHHHHHHHHHH.",
                "HHHHHHHHHHHHHH",
                "HHHHHhHHHHHHHH",
                "HHHHHHHHHHHHHH",
                "HHHHHHHHHhHHHH",
                "HHHHHHHHHHHHHH",
                "HeHHHHHHHHHHeH",
                "HeHHHHHHHHHHeH",
                ".DHHHHHHHHHHD.",
                "..DHHHHHHHHD..",
                "...DSSSSSSD...",
        }, p);
        return l;
    }

    // ------------------------------------------------------------- composition
    private static final int CW = 18, CH = 26;

    private static void plot(Sprite s, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < s.w && y < s.h) s.px[y * s.w + x] = c;
    }

    private static void block(Sprite s, int x, int y, int w, int h, int c) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) plot(s, xx, yy, c);
    }

    private static void thick(Sprite s, float x0, float y0, float x1, float y1, int t, int c) {
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2 + 1;
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * f), y = Math.round(y0 + (y1 - y0) * f);
            block(s, x - t / 2, y - t / 2, t, t, c);
        }
    }

    private static void stamp(Sprite dst, Sprite src, int ox, int oy) {
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++) {
                int c = src.px[y * src.w + x];
                if ((c >>> 24) != 0) plot(dst, ox + x, oy + y, c);
            }
    }

    /** Leg from hip to foot, with shoe pointing right. */
    private static void leg(Sprite s, Look l, int hipX, int dx, int lift, boolean back) {
        int pc = back ? l.pantsSh : l.pants;
        int fx = hipX + dx, fy = 23 - lift;
        thick(s, hipX, 19, fx, fy, 3, pc);
        block(s, fx - 1, fy + 1, 4, 1, back ? Gfx.shade(l.shoe, 0.8f) : l.shoe);
        block(s, fx - 1, fy + 2, 4, 1, l.sole);
    }

    /** Arm from shoulder at an angle (degrees, 0 = down, 90 = forward, 180 = up). */
    private static void arm(Sprite s, Look l, int sx, int sy, float deg, int len, boolean back, boolean fist) {
        double a = Math.toRadians(deg);
        float dx = (float) Math.sin(a), dy = (float) Math.cos(a);
        float ex = sx + dx * len, ey = sy + dy * len;
        float mx = sx + dx * (len - 2), my = sy + dy * (len - 2);
        int sleeve = l.style == 0 ? (back ? l.yokeSh : l.yoke) : (back ? l.jacketSh : l.jacket);
        if (l.style >= 2) { // t-shirt: short sleeve, bare forearm
            float qx = sx + dx * 2, qy = sy + dy * 2;
            thick(s, sx, sy, ex, ey, 2, back ? l.skinSh : l.skin);
            thick(s, sx, sy, qx, qy, 2, sleeve);
            int hs = fist ? 3 : 2;
            block(s, Math.round(ex) - hs / 2, Math.round(ey) - hs / 2, hs, hs, back ? l.skinSh : l.skin);
            return;
        }
        thick(s, sx, sy, mx, my, 2, sleeve);
        int cuff = l.style == 0 ? (back ? l.jacketSh : l.jacket) : (back ? Gfx.shade(l.jacketSh, 0.9f) : l.jacketSh);
        block(s, Math.round(mx) - 1, Math.round(my) - 1, 2, 2, cuff);
        int hs = fist ? 3 : 2;
        block(s, Math.round(ex) - hs / 2, Math.round(ey) - hs / 2, hs, hs, back ? l.skinSh : l.skin);
    }

    private static void torsoSide(Sprite s, Look l, int bob) {
        int y0 = 12 + bob;
        block(s, 5, y0, 8, 7 - bob, l.jacket);
        block(s, 5, y0, 1, 7 - bob, l.jacketSh);
        if (l.style >= 2) { // t-shirt with round neckline
            block(s, 9, y0, 3, 1, l.skin);
            plot(s, 10, y0 + 1, l.skinSh);
            block(s, 5, 18, 8, 1, l.jacketSh);
            plot(s, 7, y0 + 3, l.jacketLt);
            if (l.style == 3) { // Andrea: small grey print
                plot(s, 10, y0 + 3, 0xFF8A8A96);
                plot(s, 11, y0 + 3, 0xFF8A8A96);
                plot(s, 10, y0 + 4, 0xFF8A8A96);
            }
            return;
        }
        if (l.style == 0) {
            block(s, 5, y0, 8, 3, l.yoke);
            block(s, 5, y0, 1, 3, l.yokeSh);
            block(s, 9, y0, 3, 1, l.jacket); // collar
            block(s, 8, y0, 1, 1, l.jacketSh);
            for (int y = y0; y < 19; y++) plot(s, 11, y, l.trim);
            block(s, 5, 18, 8, 1, l.jacketSh);
            plot(s, 7, y0 + 4, l.jacketLt);
            plot(s, 9, y0 + 5, l.jacketLt);
        } else {
            block(s, 5, y0, 6, 1, l.jacketSh); // hood folds
            plot(s, 6, y0 + 1, l.jacketSh);
            plot(s, 11, y0 + 1, l.trim);
            plot(s, 11, y0 + 2, l.trim);
            plot(s, 12, y0 + 1, l.trim);
            plot(s, 12, y0 + 3, l.trim);
            block(s, 8, 16, 5, 2, l.jacketSh); // pocket
            block(s, 9, 16, 3, 1, l.jacket);
            block(s, 5, 18, 8, 1, l.jacketSh);
            plot(s, 7, y0 + 2, l.jacketLt);
        }
    }

    private static void torsoFront(Sprite s, Look l, int bob, boolean back) {
        int y0 = 12 + bob;
        block(s, 5, y0, 8, 7 - bob, l.jacket);
        block(s, 5, y0, 1, 7 - bob, l.jacketSh);
        block(s, 12, y0, 1, 7 - bob, l.jacketSh);
        if (l.style >= 2) {
            if (!back) {
                block(s, 7, y0, 4, 1, l.skin);
                block(s, 8, y0 + 1, 2, 1, l.skinSh);
                if (l.style == 3) block(s, 8, y0 + 3, 2, 2, 0xFF8A8A96);
            }
            block(s, 5, 18, 8, 1, l.jacketSh);
            return;
        }
        if (l.style == 0) {
            block(s, 5, y0, 8, 3, l.yoke);
            if (!back) {
                block(s, 7, y0, 4, 1, l.jacket);
                for (int y = y0; y < 19; y++) plot(s, 9, y, l.trim);
            } else block(s, 6, y0, 6, 1, l.jacket);
            block(s, 5, 18, 8, 1, l.jacketSh);
        } else {
            block(s, 6, y0, 6, 1, l.jacketSh);
            if (!back) {
                plot(s, 7, y0 + 1, l.trim);
                plot(s, 7, y0 + 2, l.trim);
                plot(s, 10, y0 + 1, l.trim);
                plot(s, 10, y0 + 2, l.trim);
                plot(s, 10, y0 + 3, l.trim);
                block(s, 6, 16, 6, 2, l.jacketSh);
                block(s, 7, 16, 4, 1, l.jacket);
            } else block(s, 6, y0 + 1, 6, 2, l.jacketSh);
            block(s, 5, 18, 8, 1, l.jacketSh);
        }
    }

    private static Sprite side(Look l, int bob, int headDx, int flDx, int flLift, int blDx, int blLift,
                               float fa, float ba, int extra) {
        Sprite s = new Sprite(CW, CH);
        arm(s, l, 7, 13 + bob, ba, 6, true, false);
        leg(s, l, 7, blDx, blLift, true);
        torsoSide(s, l, bob);
        leg(s, l, 10, flDx, flLift, false);
        stamp(s, l.headSide, 3 + headDx, bob);
        boolean fist = extra == 1;
        int len = extra == 1 ? 8 : 6;
        arm(s, l, 10, 13 + bob, fa, len, false, fist);
        if (extra == 3) { // gold coin in the hand
            double a = Math.toRadians(fa);
            int hx = Math.round(10 + (float) Math.sin(a) * 6), hy = Math.round(13 + bob + (float) Math.cos(a) * 6);
            block(s, hx - 1, hy - 3, 3, 3, 0xFFFFC830);
            plot(s, hx - 1, hy - 3, 0xFFFFF0A0);
        }
        if (extra == 2) { // pistol held forward
            double a = Math.toRadians(fa);
            int hx = Math.round(10 + (float) Math.sin(a) * 6), hy = Math.round(13 + bob + (float) Math.cos(a) * 6);
            block(s, hx - 1, hy - 2, 5, 2, 0xFF3A3A46);
            block(s, hx, hy - 2, 3, 1, 0xFF7C7C8C);
            block(s, hx - 1, hy, 2, 2, 0xFF2A2A33);
        }
        return s;
    }

    private static Sprite front(Look l, boolean back, float armL, float armR, int lift, boolean surprised) {
        Sprite s = new Sprite(CW, CH);
        if (!back) {
            leg(s, l, 7, -1, lift, false);
            leg(s, l, 11, -1, lift, false);
        } else {
            leg(s, l, 7, -1, lift, true);
            leg(s, l, 11, -1, lift, true);
        }
        torsoFront(s, l, 0, back);
        arm(s, l, 5, 13, -armL, 6, back, false);
        arm(s, l, 12, 13, armR, 6, back, false);
        Sprite head = back ? l.headBack : l.headFront;
        if (surprised) {
            head = head.copy();
            // open mouth
            for (int x = 5; x <= 8; x++) head.set(x, 11, 0xFF5A1E1E);
        }
        stamp(s, head, 2, 0);
        return s;
    }

    /** Builds all animation frames (facing right) for a look. */
    public static Sprite[] frames(Look l) {
        Sprite[] f = new Sprite[FRAMES];
        f[IDLE0] = side(l, 0, 0, 1, 0, -1, 0, 8, -8, 0);
        f[IDLE1] = side(l, 1, 0, 1, 0, -1, 0, 8, -8, 0);
        f[WALK0] = side(l, 0, 0, 3, 0, -3, 0, -35, 35, 0);
        f[WALK0 + 1] = side(l, 1, 0, 0, 0, 0, 2, 0, 0, 0);
        f[WALK0 + 2] = side(l, 0, 0, -3, 0, 3, 0, 35, -35, 0);
        f[WALK0 + 3] = side(l, 1, 0, 0, 2, 0, 0, 0, 0, 0);
        f[RUN0] = side(l, 0, 1, 4, 1, -4, 0, -60, 75, 0);
        f[RUN0 + 1] = side(l, 1, 1, 1, 0, -1, 3, 10, -10, 0);
        f[RUN0 + 2] = side(l, 0, 1, -4, 0, 4, 1, 75, -60, 0);
        f[RUN0 + 3] = side(l, 1, 1, -1, 3, 1, 0, -10, 10, 0);
        f[JUMP] = side(l, 0, 0, 2, 3, -2, 1, 150, -130, 0);
        f[FALL] = side(l, 0, 0, 1, 1, -2, 0, 115, -100, 0);
        f[SKID] = side(l, 0, -1, 4, 0, 1, 0, -40, -70, 0);
        f[WALL] = side(l, 0, 0, 2, 3, -1, 0, 95, 60, 0);
        switch (l.style) {
            case 0: // punch
                f[ACT0] = side(l, 0, 0, 2, 0, -3, 0, -70, 30, 1);
                f[ACT1] = side(l, 0, 1, 4, 0, -4, 0, 90, -40, 1);
                break;
            case 1: // pistol
                f[ACT0] = side(l, 0, 0, 2, 0, -2, 0, 90, 40, 2);
                f[ACT1] = side(l, 0, -1, 2, 0, -2, 0, 100, 40, 2);
                break;
            case 2: // coin throw: wind up behind the head, then release forward
                f[ACT0] = side(l, 0, -1, 2, 0, -3, 0, -150, 40, 3);
                f[ACT1] = side(l, 0, 1, 4, 0, -3, 0, 110, -40, 0);
                break;
            case 3: // bat: both hands up behind, then swung forward (bat drawn by the player)
                f[ACT0] = side(l, 0, -1, 2, 0, -3, 0, -140, -120, 0);
                f[ACT1] = side(l, 0, 1, 4, 0, -4, 0, 80, 70, 0);
                break;
            default: // kick: leg back, then high kick forward
                f[ACT0] = side(l, 0, 0, -4, 3, 1, 0, 50, -50, 0);
                f[ACT1] = side(l, 0, -1, 7, 6, -1, 0, -60, 80, 0);
                break;
        }
        f[HURT] = side(l, 0, -1, 2, 2, -2, 1, 160, -160, 0);
        f[FRONT] = front(l, false, 15, 15, 0, false);
        f[BACK] = front(l, true, 15, 15, 0, false);
        f[VICTORY] = front(l, false, 165, 165, 0, false);
        f[DEAD] = front(l, false, 140, 140, 1, true);
        f[TUCK] = side(l, 1, 0, 3, 4, 0, 4, 110, 70, 0);
        f[CROUCH] = side(l, 3, 0, 3, 0, -2, 0, 40, -20, 0);
        for (int i = 0; i < FRAMES; i++) f[i] = f[i].outlined(l.outline);
        return f;
    }

    /** Small head icon for HUD / map (front head with outline). */
    public static Sprite icon(Look l) {
        return l.headFront.outlined(l.outline);
    }
}
