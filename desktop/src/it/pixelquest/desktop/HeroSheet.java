package it.pixelquest.desktop;

import it.pixelquest.core.*;

/** Renders hero animation frames side by side for visual inspection. */
public class HeroSheet {
    public static void main(String[] a) throws Exception {
        HeroArt.Look[] looks = {HeroArt.mattia(), HeroArt.giuseppe()};
        Gfx g = new Gfx(HeroArt.FRAMES * 22 + 4, 2 * 32 + 40);
        g.clear(0xFF6EC6F0);
        g.fill(0, 60, g.w, 4, 0xFF4CAF50);
        for (int i = 0; i < 2; i++) {
            Sprite[] f = HeroArt.frames(looks[i]);
            for (int k = 0; k < f.length; k++) g.blit(f[k], 2 + k * 22, 2 + i * 32);
            g.blit(HeroArt.icon(looks[i]), 2 + i * 20, 70);
        }
        Png.save(g, 6, a[0]);
    }
}
