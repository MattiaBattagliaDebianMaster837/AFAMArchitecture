package it.pixelquest.core;

/**
 * Desktop check of battle mode: complete CPU-vs-CPU battles on every arena (the "human" fighter is driven by a
 * CPU brain too). Reports how each battle ended, KOs, damage dealt by every hero, and renders frames so that
 * drawing code runs as well.
 */
public final class BattleCheck {
    public interface Shot {
        void save(String name);
    }

    public static void run(Game g, int arenas, int perArena, Shot shot) {
        int n = Hero.ALL.length;
        float[] dealtBy = new float[n];
        int[] wins = new int[n], kosBy = new int[n];
        int battles = 0, finished = 0, totalFrames = 0;
        long seed = 7;
        for (int a = 0; a < Math.min(arenas, Battle.ARENAS.length); a++) {
            for (int k = 0; k < perArena; k++) {
                Battle.Config cfg = new Battle.Config();
                cfg.hero = (a + k) % n;
                int rivals = 1 + (a + k) % 4;
                for (int r = 1; r <= rivals; r++) cfg.rivals[(cfg.hero + r) % n] = true;
                cfg.level = 2 - (k % 3);
                cfg.stocks = 2;
                cfg.arena = a;
                Battle b = new Battle(g, cfg);
                g.level = b.level;
                g.screen = Game.PLAY;
                Cpu human = new Cpu(b, b.fighters.get(0), 2, seed++);
                int f = 0;
                for (; f < 60 * 60 * 4 && !(b.over && b.overT > 150); f++) {
                    Input in = human.think();
                    b.level.update(in);
                    if (f % 37 == 0) {
                        b.level.render(g.gfx);
                        b.level.renderHud(g.gfx);
                    }
                    if (shot != null && k == 0 && f == 900) {
                        b.level.render(g.gfx);
                        b.level.renderHud(g.gfx);
                        shot.save("battle_" + a);
                    }
                }
                battles++;
                totalFrames += f;
                if (b.over) finished++;
                StringBuilder sb = new StringBuilder();
                for (Player p : b.fighters) {
                    dealtBy[p.hero.id] += p.dealt;
                    kosBy[p.hero.id] += p.kos;
                    sb.append(String.format(" %s[pl%d ko%d fall%d dealt%.0f]", p.hero.name.substring(0, 3), p.place, p.kos, p.falls, p.dealt));
                }
                if (b.over) wins[b.winner().hero.id]++;
                System.out.println(String.format("arena %d %-22s %d fighters lvl%d: %s in %ds%s", a, b.arena.name, b.fighters.size(), cfg.level,
                        b.over ? "WIN " + b.winner().hero.name.substring(0, 3) : "NOT OVER", f / 60, sb));
            }
        }
        System.out.println("battles " + battles + ", finished " + finished + ", avg " + (totalFrames / Math.max(1, battles) / 60) + "s");
        for (int i = 0; i < n; i++)
            System.out.println(String.format("  %-20s wins %d  KOs %d  damage dealt %.0f%%", Hero.ALL[i].name, wins[i], kosBy[i], dealtBy[i]));
    }

    /** Each hero's C ability must hurt a rival standing in front of it. */
    public static void abilities(Game g) {
        for (int h = 0; h < Hero.ALL.length; h++) {
            Battle.Config cfg = new Battle.Config();
            cfg.hero = h;
            cfg.rivals[(h + 1) % Hero.ALL.length] = true;
            cfg.arena = 6;
            Battle b = new Battle(g, cfg);
            g.level = b.level;
            Player me = b.fighters.get(0), foe = b.fighters.get(1);
            Input none = new Input();
            for (int i = 0; i < Battle.COUNTDOWN + 5; i++) b.level.update(none);
            // put the rival right in front, at ability range
            float dist = h == 1 || h == 4 ? 70 : h == 2 ? 30 : 8;
            foe.x = me.right() + dist;
            foe.y = me.y;
            foe.vx = foe.vy = 0;
            me.facing = 1;
            foe.frozenT = 999; // the rival stands still (a CPU would dodge)
            Input c = new Input();
            float before = foe.dmg;
            for (int i = 0; i < 90; i++) {
                c.keyC = i == 0;
                c.latch(null);
                foe.x = Math.max(foe.x, me.right() + 4);
                b.level.update(c);
            }
            System.out.println(String.format("%-20s C on a rival: %.0f%% -> %.0f%%  (launched vx %.1f)", Hero.ALL[h].name, before, foe.dmg, foe.vx));
        }
    }

    /** Screenshots of the title (mode choice), the three setup steps, a battle and the results. */
    public static void menus(Game g, Shot shot) {
        g.go(Game.TITLE, 0);
        for (int i = 0; i < 80; i++) g.tick();
        shot.save("bm_0_title");
        // tap BATTAGLIA
        g.touch.down(1, g.W / 2f + 60, 108);
        g.touch.up(1);
        for (int i = 0; i < 60; i++) g.tick();
        System.out.println("after tapping BATTAGLIA: screen " + g.screen + " (10 = battle setup), step " + g.bmenu.step + " (-1 = mode)");
        shot.save("bm_0b_mode");
        g.input.keyA = true; g.tick(); g.input.keyA = false; // CONTRO IL TELEFONO
        for (int i = 0; i < 20; i++) g.tick();
        shot.save("bm_1_hero");
        g.input.keyA = true; g.tick(); g.input.keyA = false;
        for (int i = 0; i < 30; i++) g.tick();
        // add two more rivals by tapping their cards
        g.bmenu.activate(3);
        g.bmenu.activate(4);
        for (int i = 0; i < 20; i++) g.tick();
        shot.save("bm_2_rivals");
        g.input.keyC = true; g.tick(); g.input.keyC = false;
        for (int i = 0; i < 30; i++) g.tick();
        g.bmenu.cfg.arena = 3;
        for (int i = 0; i < 10; i++) g.tick();
        shot.save("bm_3_arena");
        g.input.keyA = true; g.tick(); g.input.keyA = false;
        for (int i = 0; i < 60; i++) g.tick();
        System.out.println("after COMBATTI: screen " + g.screen + " (3 = play), battle=" + (g.level != null && g.level.battle != null));
        for (int i = 0; i < 70; i++) g.tick();
        shot.save("bm_4_countdown");
        // let the CPUs fight; the human just stands (will lose eventually) - speed up: KO the human's rivals
        Battle b = g.level.battle;
        for (int i = 0; i < 600; i++) g.tick();
        shot.save("bm_5_fight");
        for (Player p : b.fighters) if (p.cpu) { p.stocks = 1; b.ko(p); }
        for (int i = 0; i < 400 && g.screen == Game.PLAY; i++) g.tick();
        for (int i = 0; i < 60; i++) g.tick();
        System.out.println("results: screen " + g.screen + " (11 = results), winner " + b.winner().hero.name + ", human lost=" + b.humanLost);
        shot.save("bm_6_results");
        // rematch
        g.input.keyA = true; g.tick(); g.input.keyA = false;
        for (int i = 0; i < 40; i++) g.tick();
        System.out.println("rematch: screen " + g.screen + ", new battle=" + (g.level.battle != b));
    }

    /** Pause during a battle and leave it: back to the battle setup; the story progress is untouched. */
    public static void pause(Game g) {
        int unlocked = g.save.unlocked, hero = g.save.hero;
        g.bmenu.cfg.hero = 2;
        g.go(Game.PLAY, -1);
        for (int i = 0; i < 300; i++) g.tick();
        g.input.keyPause = true; g.tick(); g.input.keyPause = false; g.tick();
        System.out.println("pause: screen " + g.screen + " (4 = pause)");
        // the last item is "ESCI DALLA BATTAGLIA"
        for (int i = 0; i < 5; i++) { g.input.keyAy = 1; g.tick(); g.input.keyAy = 0; g.tick(); }
        g.input.keyA = true; g.tick(); g.input.keyA = false;
        for (int i = 0; i < 60; i++) g.tick();
        System.out.println("exit: screen " + g.screen + " (10 = battle setup); story hero kept=" + (g.save.hero == hero) + ", progress kept=" + (g.save.unlocked == unlocked));
    }
}
