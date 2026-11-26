package it.pixelquest.core;

/** Desktop check: trajectory of a fighter hit at high damage (must fly off the arena: KO). */
public final class LaunchCheck {
    public static void run(Game g) {
        for (float d : new float[]{0, 60, 120, 200}) {
            Battle.Config cfg = new Battle.Config();
            cfg.hero = 0;
            cfg.rivals[1] = true;
            cfg.arena = 0;
            Battle b = new Battle(g, cfg);
            g.level = b.level;
            Input none = new Input();
            for (int i = 0; i < Battle.COUNTDOWN + 5; i++) b.level.update(none);
            Player me = b.fighters.get(0), foe = b.fighters.get(1);
            foe.dmg = d;
            foe.x = me.right() + 6;
            foe.y = me.y;
            foe.frozenT = 0;
            me.facing = 1;
            foe.battleHit(Battle.PUNCH_PCT, Battle.PUNCH_KB, me.cx(), me, Combat.NONE);
            StringBuilder sb = new StringBuilder();
            int f = 0;
            for (; f < 240 && foe.koT == 0 && foe.stocks == cfg.stocks; f++) {
                b.level.update(none);
                if (f % 10 == 0) sb.append(String.format(" (%.0f,%.0f v%.1f h%d)", foe.cx(), foe.y, foe.vx, foe.hurtT));
            }
            System.out.println(String.format("dmg %.0f%%: %s after %d frames:%s", d, foe.koT > 0 || foe.stocks < cfg.stocks ? "KO" : "still in", f, sb));
        }
    }
}
