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
        int[] confetti = {0xFFFF4A6A, 0xFF4AE0FF, 0xFFFFE04A, 0xFFB07AFF, 0xFFFFA03A, 0xFF7CFF5A};
        for (int k = 0; k < 26; k++) { // party confetti in the sky
            int x = (k * 37 + 11) % 104, y = (k * 23 + 5) % 70;
            bg.set(x, y, confetti[k % confetti.length]);
            bg.set(x + 1, y, confetti[k % confetti.length]);
        }
        Png.save(bg, 4, dir + "/ic_bg.png");

        // foreground: the five heroes' heads inside the safe zone (centre 66dp): Francesco and Andrea behind,
        // Giuseppe, Mattia and Paolo in front
        Sprite fg = new Sprite(108, 108);
        group(fg, 2, 54, 56);
        Png.save(fg, 4, dir + "/ic_fg.png");

        // legacy icon 48x48 at 4x = 192
        Sprite leg = new Sprite(48, 48);
        for (int y = 0; y < 48; y++)
            for (int x = 0; x < 48; x++) {
                int bx = x * 108 / 48, by = y * 108 / 48;
                boolean corner = cornerCut(x, y, 48, 7);
                if (!corner) leg.set(x, y, bg.get(bx, by));
            }
        group(leg, 1, 24, 25);
        Png.save(leg, 4, dir + "/ic_launcher.png");
        // a large preview for the README
        Sprite prev = new Sprite(108, 108);
        stamp(prev, bg, 0, 0);
        stamp(prev, fg, 0, 0);
        Png.save(prev, 3, dir + "/icon_preview.png");
    }

    /** All five heads in a little group centred on (cx, cy): two behind, three in front. */
    static void group(Sprite dst, int k, int cx, int cy) {
        int[] back = {2, 3}, front = {1, 0, 4};
        Sprite h0 = Hero.ALL[0].icon.scaled(k);
        int w = h0.w, h = h0.h;
        int stepB = w - 3 * k, stepF = w - 6 * k, lift = (int) (h * 0.62f);
        for (int i = 0; i < back.length; i++) {
            Sprite s = Hero.ALL[back[i]].icon.scaled(k);
            stamp(dst, s, cx - stepB / 2 - w / 2 + i * stepB, cy - h / 2 - lift);
        }
        for (int i = 0; i < front.length; i++) {
            Sprite s = Hero.ALL[front[i]].icon.scaled(k);
            stamp(dst, s, cx - w / 2 + (i - 1) * stepF, cy - h / 2 + lift / 2);
        }
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
