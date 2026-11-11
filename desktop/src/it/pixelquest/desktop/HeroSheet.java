package it.pixelquest.desktop;

import it.pixelquest.core.*;

/** Renders hero animation frames side by side for visual inspection. */
public class HeroSheet {
    public static void main(String[] a) throws Exception {
        Hero.init();
        int n = Hero.ALL.length;
        Gfx g = new Gfx(HeroArt.FRAMES * 22 + 4, n * 32 + 40);
        g.clear(0xFF6EC6F0);
        for (int i = 0; i < n; i++) {
            Sprite[] f = Hero.ALL[i].frames;
            g.fill(0, 30 + i * 32, g.w, 2, 0xFF4CAF50);
            for (int k = 0; k < f.length; k++) g.blit(f[k], 2 + k * 22, 2 + i * 32);
            g.blit(Hero.ALL[i].icon, 2 + i * 20, n * 32 + 8);
        }
        Png.save(g, 4, a[0]);
    }
}
