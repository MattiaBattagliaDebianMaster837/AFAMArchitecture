package it.pixelquest.desktop;

import it.pixelquest.core.*;

import java.util.HashMap;

/**
 * Headless test harness: drives the real game core with scripted input and writes screenshots.
 * Usage: Sim <outDir> [scenario]
 */
public class Sim {
    static String out;
    static Game g;

    static Save.Storage mem() {
        final HashMap<String, String> m = new HashMap<>();
        return new Save.Storage() {
            public String get(String k) {
                return m.get(k);
            }

            public void put(String k, String v) {
                m.put(k, v);
            }
        };
    }

    static void step(int n) {
        for (int i = 0; i < n; i++) g.tick();
    }

    static void shot(String name) throws Exception {
        g.render();
        Png.save(g.gfx, 2, out + "/" + name + ".png");
    }

    static void press(Runnable r) {
    }

    static void keys(float ax, boolean a, boolean b, boolean c) {
        g.input.keyAx = ax;
        g.input.keyA = a;
        g.input.keyB = b;
        g.input.keyC = c;
    }

    public static void main(String[] args) throws Exception {
        out = args[0];
        String sc = args.length > 1 ? args[1] : "screens";
        g = new Game(498, 224, mem());
        if (sc.equals("screens")) screens();
        else if (sc.equals("levels")) levels(args.length > 2 ? Integer.parseInt(args[2]) : -1);
        else if (sc.equals("play")) play(Integer.parseInt(args[2]), args.length > 3 ? Integer.parseInt(args[3]) : 0);
        else if (sc.equals("moves")) moves();
        else if (sc.equals("audio")) audio();
        else if (sc.equals("bot")) bot(args.length > 2 ? Integer.parseInt(args[2]) : -1);
        else if (sc.equals("boss")) boss(Integer.parseInt(args[2]));
        else if (sc.equals("fuzz")) fuzz(Integer.parseInt(args[2]));
        else if (sc.equals("jumps")) jumps();
        else if (sc.equals("controls")) controls();
        System.out.println("done");
    }

    static void screens() throws Exception {
        step(40);
        shot("01_title");
        keys(0, true, false, false);
        step(2);
        keys(0, false, false, false);
        step(30);
        shot("02_select");
        keys(1, false, false, false);
        step(3);
        keys(0, false, false, false);
        step(20);
        shot("03_select_giuseppe");
        keys(-1, false, false, false);
        step(3);
        keys(0, true, false, false);
        step(2);
        keys(0, false, false, false);
        step(40);
        shot("04_map");
        keys(0, true, false, false);
        step(2);
        keys(0, false, false, false);
        step(40);
        shot("05_level_start");
        // walk right a bit to show hint
        keys(1, false, false, false);
        step(30);
        shot("06_level_running");
        keys(0, false, false, false);
        g.input.keyPause = true;
        step(2);
        g.input.keyPause = false;
        step(5);
        shot("07_pause");
    }

    /** Render each level as one long strip (camera stepped across). */
    static void levels(int only) throws Exception {
        for (int i = 0; i < Levels.count(); i++) {
            if (only >= 0 && i != only) continue;
            LevelDef d;
            try {
                d = Levels.get(i);
            } catch (Exception e) {
                continue;
            }
            Level l = new Level(g, d, false);
            int lw = l.tw * Level.T, lh = l.th * Level.T;
            int[] big = new int[lw * lh];
            for (int cy = 0; cy < lh; cy += g.H) {
                for (int cx = 0; cx < lw; cx += g.W) {
                    l.camX = Math.max(0, Math.min(cx, lw - g.W));
                    l.camY = Math.max(0, Math.min(cy, lh - g.H));
                    l.player.x = -1000;
                    l.render(g.gfx);
                    for (int y = 0; y < g.H; y++)
                        for (int x = 0; x < g.W; x++) {
                            int wx = (int) l.camX + x, wy = (int) l.camY + y;
                            if (wx >= 0 && wy >= 0 && wx < lw && wy < lh) big[wy * lw + wx] = g.gfx.px[y * g.W + x];
                        }
                }
            }
            Png.save(big, lw, lh, 1, out + "/level_" + i + ".png");
        }
    }

    /** Simple autoplay: run right, jump at walls/gaps; screenshot periodically. */
    static void play(int idx, int hero) throws Exception {
        g.save.hero = hero;
        g.save.unlocked = 15;
        Level l = new Level(g, Levels.get(idx), false);
        g.level = l;
        java.lang.reflect.Field f = Game.class.getDeclaredField("screen");
        f.setAccessible(true);
        f.setInt(g, Game.PLAY);
        int jumpHold = 0;
        for (int fr = 0; fr < 3000 && l.state == Level.PLAY; fr++) {
            Player p = l.player;
            boolean wallAhead = l.solidAt(p.right() + 6, p.bottom() - 4) || l.solidAt(p.right() + 6, p.y + 2);
            boolean gapAhead = !l.solidAt(p.right() + 10, p.bottom() + 4) && l.tile((int) ((p.right() + 10) / 16), (int) ((p.bottom() + 4) / 16)) != Level.SEMI;
            boolean a = false;
            if (jumpHold > 0) {
                jumpHold--;
                a = true;
            } else if (p.onGround && (wallAhead || gapAhead)) {
                jumpHold = 18;
                a = true;
            }
            if (p.wallSliding && fr % 8 == 0) a = true;
            keys(1f, a, false, fr % 90 == 0);
            g.tick();
            if (fr % 150 == 0) shot(String.format("play%d_%04d", idx, fr));
        }
        shot(String.format("play%d_end", idx));
        System.out.println("state=" + l.state + " x=" + (int) l.player.x + " / " + l.tw * 16 + " coins=" + l.coins);
    }

    /** Exercise the special moves and capture frames. */
    static void moves() throws Exception {
        g.save.hero = 0;
        Level l = new Level(g, Levels.get(0), false);
        g.level = l;
        java.lang.reflect.Field f = Game.class.getDeclaredField("screen");
        f.setAccessible(true);
        f.setInt(g, Game.PLAY);
        Player p = l.player;
        // spin jump
        keys(0, false, true, false);
        step(1);
        keys(0, false, true, false);
        step(7);
        shot("m1_spin");
        keys(0, false, false, false);
        step(40);
        // punch
        keys(0, false, false, true);
        step(6);
        shot("m2_punch");
        keys(0, false, false, false);
        step(30);
        // triple jump: run then jump 3 times on landing
        float startY = p.y;
        keys(1, false, false, false);
        step(40);
        int jumps = 0;
        float minY = 9999;
        for (int i = 0; i < 400 && jumps < 3; i++) {
            boolean a = p.onGround && i > 2;
            keys(1, a || (!p.onGround && p.vy < 0), false, false);
            g.tick();
            if (a) jumps++;
            minY = Math.min(minY, p.y);
            if (jumps == 3 && !p.onGround) {
                step(8);
                shot("m3_triple_flip");
            }
        }
        System.out.println("triple jump: rose " + (startY - minY) + " px, flipping=" + p.flipping);
        // Giuseppe gun
        g.save.hero = 1;
        l = new Level(g, Levels.get(0), false);
        g.level = l;
        keys(0, false, false, true);
        step(3);
        shot("m4_shoot");
        keys(0, false, false, false);
        step(12);
        keys(0, false, false, true);
        step(2);
        keys(0, false, false, false);
        step(2);
        keys(0, false, false, true);
        step(2);
        System.out.println("ammo after 3 presses=" + l.player.ammo + " reload=" + l.player.reloadT);
        keys(0, false, false, false);
        step(600);
        System.out.println("ammo after 10s=" + l.player.ammo);
        shot("m5_after_reload");
    }

    static void bot(int only) {
        for (int i = 0; i < Levels.count(); i++) {
            if (only >= 0 && i != only) continue;
            try {
                Levels.get(i);
            } catch (Exception e) {
                continue;
            }
            for (int hero = 0; hero < 2; hero++) {
                long t0 = System.currentTimeMillis();
                Bot b = new Bot(g, i, hero);
                String r = b.solve(1500);
                System.out.println("level " + i + " hero " + hero + ": " + r + "  [" + (System.currentTimeMillis() - t0) + " ms]");
            }
        }
    }

    static void boss(int idx) throws Exception {
        g.save.hero = idx % 2;
        Level l = new Level(g, Levels.get(idx), false);
        g.level = l;
        java.lang.reflect.Field f = Game.class.getDeclaredField("screen");
        f.setAccessible(true);
        f.setInt(g, Game.PLAY);
        Player p = l.player;
        p.x = (l.arenaCol + 3) * Level.T;
        keys(0, false, false, false);
        int hurt = 0;
        for (int i = 0; i < 900; i++) {
            if (p.hp < 3) { hurt++; p.hp = 3; }
            p.invuln = Math.max(p.invuln, 0);
            // dodge a little: walk back and forth
            keys((i / 90) % 2 == 0 ? 0.6f : -0.6f, false, false, false);
            g.tick();
            if (i == 200 || i == 450 || i == 700) shot("boss" + idx + "_" + i);
        }
        System.out.println("boss " + l.bossEnt.name + " hp=" + l.bossEnt.hp + " locked=" + l.arenaLocked + " player hits taken=" + hurt);
        // defeat the boss through its damage API
        int guard = 0;
        while (l.bossEnt.hp > 0 && guard++ < 5000) {
            l.bossEnt.hit(1, p.cx());
            p.hp = 3;
            g.tick();
        }
        System.out.println("boss defeated after " + guard + " frames");
        for (int i = 0; i < 160; i++) { p.hp = 3; g.tick(); }
        shot("boss" + idx + "_defeated");
        Entity trophy = null;
        for (Entity e : l.ents) if (e instanceof Ents.Trophy) trophy = e;
        System.out.println("trophy=" + (trophy != null));
        if (trophy != null) { p.x = trophy.x; p.y = trophy.y; }
        for (int i = 0; i < 120; i++) g.tick();
        shot("boss" + idx + "_win");
        System.out.println("level state=" + l.state + " (3 = BOSSWIN)");
        for (int i = 0; i < 300; i++) g.tick();
        System.out.println("screen after=" + g.screen + " cleared=" + g.save.isCleared(idx) + " unlocked=" + g.save.unlocked);
    }

    /** Random input on every level and through all menus, looking for exceptions. */
    static void fuzz(int frames) throws Exception {
        java.util.Random r = new java.util.Random(1234);
        long total = 0;
        for (int lv = 0; lv < Levels.count(); lv++) {
            for (int hero = 0; hero < 2; hero++) {
                g = new Game(r.nextBoolean() ? 498 : 400, 224, mem());
                g.save.hero = hero;
                g.save.unlocked = 15;
                g.save.lastNode = lv;
                // title -> select -> map -> level using real menu flow
                keys(0, true, false, false); step(3); keys(0, false, false, false); step(30);
                keys(0, true, false, false); step(3); keys(0, false, false, false); step(30);
                keys(0, true, false, false); step(3); keys(0, false, false, false); step(30);
                float ax = 0;
                boolean a = false, b = false, c = false;
                for (int i = 0; i < frames; i++) {
                    if (r.nextInt(12) == 0) ax = r.nextFloat() < 0.75f ? (r.nextBoolean() ? 1f : 0.5f) : (r.nextBoolean() ? -1f : 0);
                    if (r.nextInt(8) == 0) a = r.nextInt(3) > 0;
                    if (r.nextInt(30) == 0) b = r.nextBoolean();
                    if (r.nextInt(25) == 0) c = r.nextBoolean();
                    keys(ax, a, b, c);
                    g.input.keyAy = r.nextInt(50) == 0 ? (r.nextBoolean() ? 1 : -1) : 0;
                    g.input.keyPause = r.nextInt(2000) == 0;
                    if (r.nextInt(3000) == 0) g.onBack();
                    if (r.nextInt(200) == 0) g.touch.down(r.nextInt(5), r.nextInt(g.W), r.nextInt(g.H));
                    if (r.nextInt(150) == 0) g.touch.up(r.nextInt(5));
                    if (r.nextInt(4000) == 0) g.resizePhysical(1600 + r.nextInt(900), 720 + r.nextInt(400));
                    g.tick();
                    g.render();
                    total++;
                    if (g.screen == Game.TITLE && r.nextInt(60) == 0) { keys(0, true, false, false); g.tick(); }
                }
                System.out.println("level " + lv + " hero " + hero + " ok, screen=" + g.screen + " lives=" + g.save.lives + " cleared=" + Integer.toBinaryString(g.save.cleared));
            }
        }
        System.out.println("fuzz frames=" + total);
    }

    static void jumps() throws Exception {
        for (int hero = 0; hero < 2; hero++) {
            g.save.hero = hero;
            Level l = new Level(g, Levels.get(0), false);
            g.level = l;
            Player p = l.player;
            p.x = 144 * 16;
            p.y = 192 - 22;
            keys(1, false, false, false);
            for (int i = 0; i < 60; i++) l.update(in(1, false, false));
            float groundY = p.y;
            StringBuilder sb = new StringBuilder();
            int jumps = 0;
            float apex = 9999;
            boolean wasGround = true;
            for (int i = 0; i < 400 && jumps <= 3; i++) {
                boolean a = (p.onGround && i > 0) || (!p.onGround && p.vy < 0);
                l.update(in(1, a, false));
                if (!p.onGround) apex = Math.min(apex, p.y);
                if (p.onGround && !wasGround) {
                    sb.append(String.format(" jump%d rise=%.0fpx", jumps, groundY - apex));
                    apex = 9999;
                }
                if (!p.onGround && wasGround) jumps++;
                wasGround = p.onGround;
                if (jumps == 3 && p.flipping && i % 4 == 0 && hero == 0) shot("tj_" + i);
            }
            System.out.println(Hero.ALL[hero].name + ":" + sb);
            // single jumps: walk jump and run jump from standstill on flat ground
            l = new Level(g, Levels.get(0), false);
            p = l.player;
            p.x = 150 * 16;
            for (int i = 0; i < 10; i++) l.update(in(0, false, false));
            float y0 = p.y, x0 = p.x;
            apex = 9999;
            l.update(in(0, true, false));
            for (int i = 0; i < 120 && !(p.onGround && i > 3); i++) { l.update(in(0, true, false)); apex = Math.min(apex, p.y); }
            System.out.println("  standing jump (hold) rise=" + (y0 - apex));
        }
    }

    static void startLevel(int idx) throws Exception {
        g.save.unlocked = 15;
        Level l = new Level(g, Levels.get(idx), false);
        g.level = l;
        java.lang.reflect.Field f = Game.class.getDeclaredField("screen");
        f.setAccessible(true);
        f.setInt(g, Game.PLAY);
        keys(0, false, false, false);
        step(5);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
    }

    /** Touch-control responsiveness checks driven through the real TouchControls. */
    static void controls() throws Exception {
        TouchControls tc = g.touch;
        int W = g.W, H = g.H;
        // 1) ultra-short tap on A in the screen corner (down+up before the next frame) must jump
        startLevel(0);
        Player p = g.level.player;
        float y0 = p.y;
        tc.down(1, W - 3, H - 3);
        tc.up(1);
        step(1);
        check("corner tap on A starts the jump on the next frame (vy=" + p.vy + ")", p.vy < -3);
        float minY = p.y;
        for (int i = 0; i < 60; i++) { step(1); minY = Math.min(minY, p.y); }
        check("quick tap jump height = " + (int) (y0 - minY) + "px (>= 35)", y0 - minY >= 35);
        // 2) taps between A, B, C all register
        float[][] pts = {{W - 40, H - 48}, {W - 96, H - 34}, {W - 84, H - 98}, {W - 60, H - 20}, {W - 10, H - 70}};
        String[] exp = {"A", "B", "C", "A", "A"};
        for (int k = 0; k < pts.length; k++) {
            tc.down(2, pts[k][0], pts[k][1]);
            tc.poll();
            String got = tc.a ? "A" : tc.b ? "B" : tc.c ? "C" : "-";
            tc.up(2);
            tc.poll();
            check("touch at " + (int) pts[k][0] + "," + (int) pts[k][1] + " -> " + got, got.equals(exp[k]));
        }
        // 3) stick: touching the pad slightly right moves at once, frames to walk/run speed
        startLevel(0);
        p = g.level.player;
        tc.down(3, tc.defBaseX() + 6, tc.defBaseY());
        step(1);
        check("light touch on the pad moves immediately (vx=" + p.vx + ")", p.vx > 0.5f);
        int f = 1;
        while (p.vx < p.hero.walk - 0.01f && f < 60) { step(1); f++; }
        check("frames to reach walking speed: " + f, f <= 6);
        tc.move(3, tc.defBaseX() + 40, tc.defBaseY());
        int fr = 0;
        while (p.vx < p.hero.run - 0.01f && fr < 90) { step(1); fr++; }
        check("frames from walk to full run: " + fr, fr <= 14);
        // 4) base follows the finger: a short move back reverses direction
        tc.move(3, tc.defBaseX() + 80, tc.defBaseY());
        step(1);
        tc.move(3, tc.defBaseX() + 80 - 36, tc.defBaseY());
        step(1);
        check("reversing needs a short swipe (ax=" + tc.ax + ")", tc.ax < -0.2f);
        int stop = 0;
        while (p.vx > 0 && stop < 60) { step(1); stop++; }
        check("frames to turn around from full run: " + stop, stop <= 9);
        tc.up(3);
        step(2);
        // 4b) every new touch on the pad works (slot reuse regression)
        int okTouches = 0;
        for (int k = 0; k < 6; k++) {
            startLevel(0);
            p = g.level.player;
            tc.down(10 + k, tc.defBaseX() + 15, tc.defBaseY());
            step(2);
            if (p.vx > 0.5f) okTouches++;
            tc.up(10 + k);
            step(1);
        }
        check("6 consecutive pad touches all move the hero: " + okTouches + "/6", okTouches == 6);
        // 5) arrows mode
        tc.mode = TouchControls.ARROWS;
        startLevel(0);
        p = g.level.player;
        float[] R = tc.arrowR();
        int rr = tc.down(4, R[0], R[1]);
        step(1);
        check("arrow press moves immediately (vx=" + p.vx + ")", p.vx > 0.5f);
        step(TouchControls.ARROW_RUN_FRAMES + 12);
        check("holding the arrow switches to running (vx=" + p.vx + ")", p.vx > p.hero.walk + 0.5f);
        shot("c1_arrows");
        float[] L = tc.arrowL();
        tc.move(4, L[0], L[1]);
        step(1);
        check("sliding to the left arrow reverses at once (ax=" + tc.ax + ")", tc.ax < 0);
        tc.up(4);
        step(4);
        // 6) options menu on the map toggles the control type
        g.save.controls = TouchControls.STICK;
        tc.mode = TouchControls.STICK;
        java.lang.reflect.Field fs = Game.class.getDeclaredField("screen");
        fs.setAccessible(true);
        fs.setInt(g, Game.MAP);
        keys(0, false, true, false);
        step(2);
        keys(0, false, false, false);
        step(5);
        check("B on the map opens the menu (screen=" + g.screen + ")", g.screen == Game.MENU);
        shot("c2_menu");
        tc.down(5, W / 2f, 62 + 19 + 3); // tap "COMANDI"
        tc.up(5);
        step(2);
        check("tapping COMANDI switches to arrows (mode=" + tc.mode + ")", tc.mode == TouchControls.ARROWS && g.save.controls == TouchControls.ARROWS);
        shot("c3_menu_arrows");
        // 7) select screen: tapping the other card only selects it
        fs.setInt(g, Game.SELECT);
        g.save.hero = 0;
        java.lang.reflect.Field fc = Game.class.getDeclaredField("selCursor");
        fc.setAccessible(true);
        fc.setInt(g, 0);
        step(20);
        tc.down(6, W / 2f + 60, 140);
        tc.up(6);
        step(3);
        check("tap on Giuseppe's card selects without confirming (screen=" + g.screen + ", cursor=" + fc.getInt(g) + ")",
                g.screen == Game.SELECT && fc.getInt(g) == 1);
    }

    static Input inp = new Input();

    static Input in(float ax, boolean a, boolean b) {
        inp.keyAx = ax;
        inp.keyA = a;
        inp.keyB = b;
        inp.latch(null);
        return inp;
    }

    static void audio() throws Exception {
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < Music.N; i++) {
            short[] s = Music.get(i);
            int peak = 0;
            for (short v : s) peak = Math.max(peak, Math.abs(v));
            System.out.println("track " + i + " len=" + s.length / (float) Audio.RATE + "s peak=" + peak);
        }
        System.out.println("render ms=" + (System.currentTimeMillis() - t0));
        // write the W1 track to wav for inspection
        short[] s = Music.get(Music.W1);
        java.io.DataOutputStream o = new java.io.DataOutputStream(new java.io.FileOutputStream(out + "/w1.wav"));
        int n = s.length * 2;
        o.writeBytes("RIFF");
        o.writeInt(Integer.reverseBytes(36 + n));
        o.writeBytes("WAVEfmt ");
        o.writeInt(Integer.reverseBytes(16));
        o.writeShort(Short.reverseBytes((short) 1));
        o.writeShort(Short.reverseBytes((short) 1));
        o.writeInt(Integer.reverseBytes(Audio.RATE));
        o.writeInt(Integer.reverseBytes(Audio.RATE * 2));
        o.writeShort(Short.reverseBytes((short) 2));
        o.writeShort(Short.reverseBytes((short) 16));
        o.writeBytes("data");
        o.writeInt(Integer.reverseBytes(n));
        for (short v : s) o.writeShort(Short.reverseBytes(v));
        o.close();
    }
}
