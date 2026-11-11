package it.pixelquest.core;

import java.util.HashMap;

/** An ARGB pixel image. Pixels with alpha 0 are transparent. */
public final class Sprite {
    public final int w, h;
    public final int[] px;

    public Sprite(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    public Sprite(int w, int h, int[] px) {
        this.w = w;
        this.h = h;
        this.px = px;
    }

    /** Build a sprite from rows of characters; each char is looked up in the palette ('.' and ' ' are transparent). */
    public static Sprite parse(String[] rows, HashMap<Character, Integer> pal) {
        int h = rows.length, w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        Sprite s = new Sprite(w, h);
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            for (int x = 0; x < r.length(); x++) {
                char c = r.charAt(x);
                if (c == '.' || c == ' ') continue;
                Integer col = pal.get(c);
                if (col == null) throw new IllegalArgumentException("no colour for '" + c + "'");
                s.px[y * w + x] = col;
            }
        }
        return s;
    }

    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return 0;
        return px[y * w + x];
    }

    public void set(int x, int y, int c) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = c;
    }

    public Sprite copy() {
        return new Sprite(w, h, px.clone());
    }

    public Sprite flipped() {
        Sprite s = new Sprite(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) s.px[y * w + x] = px[y * w + (w - 1 - x)];
        return s;
    }

    /** Nearest neighbour integer upscale. */
    public Sprite scaled(int k) {
        Sprite s = new Sprite(w * k, h * k);
        for (int y = 0; y < s.h; y++)
            for (int x = 0; x < s.w; x++) s.px[y * s.w + x] = px[(y / k) * w + x / k];
        return s;
    }

    /** Copy with every opaque pixel replaced by colour c (keeps alpha). */
    public Sprite silhouette(int c) {
        Sprite s = new Sprite(w, h);
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a != 0) s.px[i] = (a << 24) | (c & 0xFFFFFF);
        }
        return s;
    }

    /** Adds a 1px dark outline around opaque pixels (grows the sprite by 1px each side). */
    public Sprite outlined(int c) {
        Sprite s = new Sprite(w + 2, h + 2);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) if ((px[y * w + x] >>> 24) != 0) s.px[(y + 1) * s.w + x + 1] = px[y * w + x];
        int[] out = s.px.clone();
        for (int y = 0; y < s.h; y++)
            for (int x = 0; x < s.w; x++) {
                if ((s.px[y * s.w + x] >>> 24) != 0) continue;
                boolean n = false;
                for (int d = 0; d < 4 && !n; d++) {
                    int xx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), yy = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (xx >= 0 && yy >= 0 && xx < s.w && yy < s.h && (s.px[yy * s.w + xx] >>> 24) == 255) n = true;
                }
                if (n) out[y * s.w + x] = c;
            }
        return new Sprite(s.w, s.h, out);
    }

    public Sprite sub(int sx, int sy, int sw, int sh) {
        Sprite s = new Sprite(sw, sh);
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++) s.px[y * sw + x] = get(sx + x, sy + y);
        return s;
    }
}
