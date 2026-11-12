package it.pixelquest.core;

/** Desktop check: every hero can break a brick wall in front of him with the C ability. */
public final class BrickCheck {
    public static void run(Game g, Runnable restart) {
        String[] nm = {"mattia", "giuseppe", "francesco", "andrea", "paolo"};
        for (int h = 0; h < 5; h++) {
            g.save.hero = h;
            restart.run();
            Level l = g.level;
            Player p = l.player;
            l.ents.clear();
            p.facing = 1;
            int c = (int) (p.right() / Level.T) + 2, r = (int) ((p.bottom() - 1) / Level.T);
            // a wall of bricks two tiles ahead, as tall as the hero
            for (int rr = r - 1; rr <= r; rr++) l.setTile(c, rr, Level.BRICK);
            g.input.keyAx = 0.5f; // walk up to the wall
            for (int i = 0; i < 40; i++) g.tick();
            g.input.keyAx = 0;
            for (int i = 0; i < 5; i++) g.tick();
            int broken = 0;
            for (int k = 0; k < 3; k++) {
                g.input.keyC = true;
                g.tick();
                g.input.keyC = false;
                for (int i = 0; i < 70; i++) g.tick();
            }
            for (int rr = r - 1; rr <= r; rr++) if (l.tile(c, rr) != Level.BRICK) broken++;
            System.out.println(String.format("%-10s breaks %d/2 bricks of the wall", nm[h], broken));
        }
    }
}
