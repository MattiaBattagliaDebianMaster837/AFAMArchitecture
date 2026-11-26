package it.pixelquest.core;

/** Desktop check: a CPU launched off the arena (various spots) must get back on it. */
public final class RecoveryCheck {
    public static void run(Game g) {
        float[][] spots = {{20, -30, 2}, {40, -10, 3}, {50, 10, 3}, {70, -40, 1}, {30, 24, 4}, {90, -60, 0}};
        for (int arena = 0; arena < Battle.ARENAS.length; arena++)
        for (int lvl = 1; lvl < 2; lvl++) {
            int ok = 0, total = 0;
            StringBuilder fails = new StringBuilder();
            for (int h = 0; h < Hero.ALL.length; h++)
                for (int side = -1; side <= 1; side += 2)
                    for (float[] sp : spots) {
                        Battle.Config cfg = new Battle.Config();
                        cfg.hero = (h + 1) % Hero.ALL.length;
                        cfg.rivals[h] = true;
                        cfg.level = lvl;
                        cfg.arena = arena;
                        Battle b = new Battle(g, cfg);
                        g.level = b.level;
                        Input none = new Input();
                        for (int i = 0; i < Battle.COUNTDOWN + 2; i++) b.level.update(none);
                        Player cpu = b.fighters.get(1), human = b.fighters.get(0);
                        human.frozenT = 9999;
                        float edge = side > 0 ? b.stageR : b.stageL;
                        cpu.x = edge + side * sp[0] - cpu.w / 2f;
                        cpu.y = b.stageTop + sp[1] - cpu.h;
                        cpu.vx = side * sp[2];
                        cpu.vy = 1;
                        cpu.onGround = false;
                        cpu.airSpins = 1;
                        int f = 0;
                        boolean landed = false;
                        for (; f < 200 && cpu.koT == 0 && cpu.stocks == cfg.stocks && !landed; f++) {
                            b.level.update(none);
                            landed = cpu.onGround && cpu.cx() >= b.stageL && cpu.cx() <= b.stageR;
                        }
                        boolean back = landed && cpu.stocks == cfg.stocks;
                        total++;
                        if (back) ok++;
                        else fails.append(String.format(" %s@%s%.0f/%.0f", Hero.ALL[h].name.substring(0, 3), side > 0 ? "R" : "L", sp[0], sp[1]));
                    }
            System.out.println(Battle.ARENAS[arena].name + " CPU " + Battle.LEVEL_NAMES[lvl] + ": back on the arena " + ok + "/" + total + (fails.length() > 0 ? "  fails:" + fails : ""));
        }
    }
}
