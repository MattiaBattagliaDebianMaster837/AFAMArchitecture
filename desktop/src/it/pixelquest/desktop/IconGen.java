package it.pixelquest.desktop;

import it.pixelquest.core.*;

/** Generates the launcher icons (legacy + adaptive foreground/background) from the game's own pixel art. */
public class IconGen {
    public static void main(String[] a) throws Exception {
        String dir = a[0];
        Hero.init();
        Art.init();
        Theme th = Theme.get(0);

        // background 108x108 "dp" art at 1x, scaled x4 = 432
        Sprite bg = new Sprite(108, 108);
        for (int y = 0; y < 108; y++)
            for (int x = 0; x < 108; x++) bg.set(x, y, th.skyRows[Math.min(255, y * 2)]);
        for (int x = 0; x < 108; x++) { // hills + ground
            int hh = 72 + (int) (Math.sin(x * 0.09) * 5);
            for (int y = hh; y < 108; y++) bg.set(x, y, y < hh + 2 ? 0xFF8AE070 : 0xFF4AA858);
            for (int y = 86; y < 108; y++) bg.set(x, y, th.ground[1 | 2 | 8].get(x % 16, (y - 86) % 16));
            for (int y = 84; y < 88; y++) bg.set(x, y, th.ground[2 | 8].get(x % 16, y - 84));
        }
        for (int i = 0; i < 2; i++) { // clouds
            Sprite c = th.cloud;
            stamp(bg, c, 6 + i * 52, 10 + i * 12);
        }
        Png.save(bg, 4, dir + "/ic_bg.png");

        // foreground: both hero heads inside the safe zone (centre 66dp)
        Sprite fg = new Sprite(108, 108);
        Sprite m = Hero.ALL[0].icon.scaled(2), g = Hero.ALL[1].icon.scaled(2);
        stamp(fg, m, 54 - m.w + 2, 54 - m.h / 2 + 4);
        stamp(fg, g, 54 - 2, 54 - g.h / 2 + 4);
        Png.save(fg, 4, dir + "/ic_fg.png");

        // legacy icon 48x48 at 4x = 192
        Sprite leg = new Sprite(48, 48);
        for (int y = 0; y < 48; y++)
            for (int x = 0; x < 48; x++) {
                int bx = x * 108 / 48, by = y * 108 / 48;
                boolean corner = cornerCut(x, y, 48, 7);
                if (!corner) leg.set(x, y, bg.get(bx, by));
            }
        Sprite mm = Hero.ALL[0].icon, gg = Hero.ALL[1].icon;
        stamp(leg, mm, 24 - mm.w + 1, 24 - mm.h / 2 + 3);
        stamp(leg, gg, 24 - 1, 24 - gg.h / 2 + 3);
        Png.save(leg, 4, dir + "/ic_launcher.png");
        // a large preview for the README
        Sprite prev = new Sprite(108, 108);
        stamp(prev, bg, 0, 0);
        stamp(prev, fg, 0, 0);
        Png.save(prev, 3, dir + "/icon_preview.png");
    }

    static boolean cornerCut(int x, int y, int n, int r) {
        int cx = x < r ? r : x >= n - r ? n - r - 1 : x;
        int cy = y < r ? r : y >= n - r ? n - r - 1 : y;
        int dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy > r * r;
    }

    static void stamp(Sprite dst, Sprite src, int ox, int oy) {
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++) {
                int c = src.px[y * src.w + x];
                if ((c >>> 24) != 0) dst.set(ox + x, oy + y, c);
            }
    }
}
