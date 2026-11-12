package it.pixelquest.core;

/** Desktop check: power-ups popped from a block walk off it like hearts. */
public final class ItemCheck {
    public static void run(Game g) {
        int[] kinds = {Ents.Item.HEART, Ents.Item.FIRE, Ents.Item.ICE};
        String[] nm = {"cuore", "fiore di fuoco", "fiore di ghiaccio"};
        for (int k = 0; k < kinds.length; k++) {
            Level l = g.level;
            l.ents.clear();
            Player p = l.player;
            p.facing = 1;
            // a block floating 4 tiles above the ground, 6 tiles ahead of the hero
            int c = (int) (p.cx() / Level.T) + 6, r = (int) (p.bottom() / Level.T) - 4;
            Ents.Item it = new Ents.Item(c * Level.T, r * Level.T, kinds[k]);
            l.spawn(it);
            float x0 = it.x;
            for (int i = 0; i < 120; i++) g.tick(); // the hero stands still, the item walks away from him
            System.out.println(String.format("%-18s moved %.0f px, now %.0f px above the block row, on ground=%b", nm[k], it.x - x0,
                    r * Level.T - it.bottom(), it.onGround));
        }
    }
}
