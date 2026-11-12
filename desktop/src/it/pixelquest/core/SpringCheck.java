package it.pixelquest.core;

/** Desktop check: how high a spring sends the hero (holding A), in tiles above the spring's base. */
public final class SpringCheck {
    public static void run(Game g, int level) {
        Level l = g.level;
        Ents.Spring sp = null;
        for (Entity e : l.ents) if (e instanceof Ents.Spring && (sp == null || e.y > sp.y)) sp = (Ents.Spring) e;
        // keep only the spring: no enemies in the way
        l.ents.clear();
        l.ents.add(sp);
        Player p = l.player;
        p.x = sp.cx() - p.w / 2f;
        p.y = sp.y - p.h - 20;
        p.vy = 0;
        p.invuln = 100000;
        float base = sp.bottom(), top = p.y;
        for (int i = 0; i < 120; i++) {
            g.input.keyA = true;
            g.tick();
            top = Math.min(top, p.y);
        }
        g.input.keyA = false;
        System.out.println(String.format("level %d spring at row %d: feet reach %.1f tiles above its base (row %.1f)", level,
                (int) (sp.y / Level.T), (base - (top + p.h)) / Level.T, (top + p.h) / Level.T));
    }

    /** Stand at (col,row-top-of-floor), hop toward dir0 holding A; when a spring launches, steer to dir1. Prints the path. */
    public static void path(Game g, int col, int floorRow, float dir0, float dir1) {
        Level l = g.level;
        java.util.List<Entity> keep = new java.util.ArrayList<>();
        for (Entity e : l.ents) if (e instanceof Ents.Spring || e instanceof Ents.Platform) keep.add(e);
        l.ents.clear();
        l.ents.addAll(keep);
        Player p = l.player;
        p.x = col * Level.T + 2;
        p.y = floorRow * Level.T - p.h;
        p.vy = 0;
        p.invuln = 100000;
        for (int i = 0; i < 5; i++) g.tick();
        float dir = dir0;
        boolean turned = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            if (!turned && p.vy < -7f) {
                dir = dir1;
                turned = true;
                sb.append(" [SPRING]");
            }
            g.input.keyAx = dir;
            g.input.keyA = i < 120;
            g.tick();
            if (i % 6 == 0) sb.append(String.format(" (%.1f,%.1f%s)", p.cx() / Level.T, p.bottom() / Level.T, p.onGround ? "g" : ""));
        }
        g.input.keyAx = 0;
        g.input.keyA = false;
        System.out.println("path:" + sb);
        if (Boolean.getBoolean("pathShot")) {
            p.x = col * Level.T + 2;
            p.y = floorRow * Level.T - p.h;
            p.vy = 0;
            for (int i = 0; i < 40; i++) g.tick();
        }
    }
}
