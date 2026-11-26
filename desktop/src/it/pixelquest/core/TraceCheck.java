package it.pixelquest.core;

/** Desktop check: positions and state of the fighters over time in one arena. */
public final class TraceCheck {
    public static void run(Game g, int arena, int hero, int rival, int frames) {
        Battle.Config cfg = new Battle.Config();
        cfg.hero = hero;
        cfg.rivals[rival] = true;
        for (String r : System.getProperty("traceRivals", "").split(",")) if (!r.isEmpty()) cfg.rivals[Integer.parseInt(r)] = true;
        cfg.level = Integer.getInteger("traceLevel", 1);
        cfg.stocks = 2;
        cfg.arena = arena;
        Battle b = new Battle(g, cfg);
        g.level = b.level;
        Cpu human = new Cpu(b, b.fighters.get(0), 1, Long.getLong("traceSeed", 99));
        for (int f = 0; f < frames; f++) {
            Input in = human.think();
            b.level.update(in);
            if (f % Integer.getInteger("traceEvery", 60) == 0 && f > Battle.COUNTDOWN) {
                StringBuilder sb = new StringBuilder("t=" + f / 60 + "s");
                for (int i = 0; i < b.fighters.size(); i++) {
                    Player p = b.fighters.get(i);
                    Cpu c = i == 0 ? human : b.cpus.get(i);
                    sb.append(String.format("  %s x%.0f y%.0f g%s vx%.1f dmg%.0f ax%.1f tgt=%s", p.hero.name.substring(0, 3), p.cx(), p.bottom(), p.onGround ? "Y" : "n",
                            p.vx, p.dmg, c.ax, c.target == null ? "-" : c.target instanceof Player ? ((Player) c.target).hero.name.substring(0, 3) : c.target.getClass().getSimpleName()));
                }
                System.out.println(sb);
                if (Boolean.getBoolean("traceFull")) for (Player p : b.fighters)
                    System.out.println(String.format("    %s vy%.2f hurt%d frozen%d inv%d wallSl%b wallDir%d spin%b ko%d riding%s air%d frame%d",
                            p.hero.name.substring(0, 3), p.vy, p.hurtT, p.frozenT, p.invuln, p.wallSliding, p.wallDir, p.spinning, p.koT,
                            p.riding, p.airSpins, p.frameIdx));
            }
        }
    }
}
