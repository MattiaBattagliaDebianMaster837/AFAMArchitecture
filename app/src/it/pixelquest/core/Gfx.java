package it.pixelquest.core;

import java.util.Arrays;

/** Software framebuffer renderer. Everything in the game is drawn into {@link #px} at low resolution. */
public final class Gfx {
    public final int w, h;
    public final int[] px;
    /** Global translation applied to sprite/rect drawing (used for screen shake). */
    public int ox, oy;

    public Gfx(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    public void clear(int c) {
        Arrays.fill(px, c | 0xFF000000);
    }

    public static int blend(int dst, int src, int a) {
        if (a >= 255) return src | 0xFF000000;
        if (a <= 0) return dst;
        int ia = 255 - a;
        int r = (((src >> 16) & 255) * a + ((dst >> 16) & 255) * ia) / 255;
        int g = (((src >> 8) & 255) * a + ((dst >> 8) & 255) * ia) / 255;
        int b = ((src & 255) * a + (dst & 255) * ia) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }

    public static int mix(int c1, int c2, float t) {
        int a = (int) (t * 255);
        return blend(c1, c2, a);
    }

    public static int shade(int c, float k) {
        return rgb((int) (((c >> 16) & 255) * k), (int) (((c >> 8) & 255) * k), (int) ((c & 255) * k));
    }

    public void fill(int x, int y, int rw, int rh, int c) {
        x += ox;
        y += oy;
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(w, x + rw), y1 = Math.min(h, y + rh);
        c |= 0xFF000000;
        for (int yy = y0; yy < y1; yy++) {
            int o = yy * w;
            for (int xx = x0; xx < x1; xx++) px[o + xx] = c;
        }
    }

    public void fillA(int x, int y, int rw, int rh, int c, int a) {
        x += ox;
        y += oy;
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(w, x + rw), y1 = Math.min(h, y + rh);
        for (int yy = y0; yy < y1; yy++) {
            int o = yy * w;
            for (int xx = x0; xx < x1; xx++) px[o + xx] = blend(px[o + xx], c, a);
        }
    }

    public void rect(int x, int y, int rw, int rh, int c) {
        fill(x, y, rw, 1, c);
        fill(x, y + rh - 1, rw, 1, c);
        fill(x, y, 1, rh, c);
        fill(x + rw - 1, y, 1, rh, c);
    }

    public void pset(int x, int y, int c) {
        x += ox;
        y += oy;
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = c | 0xFF000000;
    }

    public void psetA(int x, int y, int c, int a) {
        x += ox;
        y += oy;
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = blend(px[y * w + x], c, a);
    }

    /** Draw sprite with optional horizontal flip. mode: 0 normal, 1 flash with colour, alpha 0..255 */
    public void blit(Sprite s, int x, int y, boolean flip, int alpha, int flashColor) {
        if (s == null) return;
        x += ox;
        y += oy;
        int sx0 = Math.max(0, -x), sy0 = Math.max(0, -y);
        int sx1 = Math.min(s.w, w - x), sy1 = Math.min(s.h, h - y);
        for (int sy = sy0; sy < sy1; sy++) {
            int dRow = (y + sy) * w + x;
            int sRow = sy * s.w;
            for (int sx = sx0; sx < sx1; sx++) {
                int c = s.px[sRow + (flip ? s.w - 1 - sx : sx)];
                int a = c >>> 24;
                if (a == 0) continue;
                if (flashColor != 0) c = flashColor;
                int aa = alpha >= 255 ? a : a * alpha / 255;
                int di = dRow + sx;
                px[di] = aa >= 255 ? (c | 0xFF000000) : blend(px[di], c, aa);
            }
        }
    }

    public void blit(Sprite s, int x, int y) {
        blit(s, x, y, false, 255, 0);
    }

    public void blit(Sprite s, int x, int y, boolean flip) {
        blit(s, x, y, flip, 255, 0);
    }

    /**
     * General transformed blit: sprite centred at (cx,cy) with scale (sx,sy) and rotation (radians).
     * Uses inverse nearest-neighbour mapping so pixels stay crisp.
     */
    public void blitEx(Sprite s, float cx, float cy, float scx, float scy, float ang, boolean flip, int alpha, int flashColor) {
        if (s == null || Math.abs(scx) < 0.02f || Math.abs(scy) < 0.02f) return;
        cx += ox;
        cy += oy;
        float cos = (float) StrictMath.cos(ang), sin = (float) StrictMath.sin(ang);
        float hw = s.w / 2f, hh = s.h / 2f;
        float ext = (float) Math.sqrt(hw * hw * scx * scx + hh * hh * scy * scy) + 1;
        int x0 = Math.max(0, (int) (cx - ext)), x1 = Math.min(w - 1, (int) (cx + ext));
        int y0 = Math.max(0, (int) (cy - ext)), y1 = Math.min(h - 1, (int) (cy + ext));
        for (int y = y0; y <= y1; y++) {
            float dy = y + 0.5f - cy;
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - cx;
                // inverse rotate
                float rx = dx * cos + dy * sin;
                float ry = -dx * sin + dy * cos;
                float u = rx / scx + hw, v = ry / scy + hh;
                int iu = (int) Math.floor(u), iv = (int) Math.floor(v);
                if (iu < 0 || iv < 0 || iu >= s.w || iv >= s.h) continue;
                if (flip) iu = s.w - 1 - iu;
                int c = s.px[iv * s.w + iu];
                int a = c >>> 24;
                if (a == 0) continue;
                if (flashColor != 0) c = flashColor;
                int aa = alpha >= 255 ? a : a * alpha / 255;
                int di = y * w + x;
                px[di] = aa >= 255 ? (c | 0xFF000000) : blend(px[di], c, aa);
            }
        }
    }

    public void circle(int cx, int cy, int r, int c, int a) {
        cx += ox;
        cy += oy;
        int r2 = r * r + r;
        for (int y = Math.max(0, cy - r); y <= Math.min(h - 1, cy + r); y++) {
            int dy = y - cy;
            for (int x = Math.max(0, cx - r); x <= Math.min(w - 1, cx + r); x++) {
                int dx = x - cx;
                if (dx * dx + dy * dy <= r2) px[y * w + x] = blend(px[y * w + x], c, a);
            }
        }
    }

    public void ring(int cx, int cy, int r, int t, int c, int a) {
        cx += ox;
        cy += oy;
        int ro = r * r + r, ri = (r - t) * (r - t) + (r - t);
        for (int y = Math.max(0, cy - r); y <= Math.min(h - 1, cy + r); y++) {
            int dy = y - cy;
            for (int x = Math.max(0, cx - r); x <= Math.min(w - 1, cx + r); x++) {
                int dx = x - cx;
                int d = dx * dx + dy * dy;
                if (d <= ro && d > ri) px[y * w + x] = blend(px[y * w + x], c, a);
            }
        }
    }

    // ---------------------------------------------------------------- text
    public void text(String s, int x, int y, int c) {
        text(s, x, y, c, 1, 255);
    }

    public void text(String s, int x, int y, int c, int scale, int a) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n') {
                cx = x;
                y += (Font.GH + 3) * scale;
                continue;
            }
            int acc = Font.accent(ch);
            if (acc != 0) {
                ch = Font.base(ch);
                int ax0 = acc == 1 ? 1 : 3, ax1 = acc == 1 ? 2 : 2;
                if (scale == 1) {
                    psetA(cx + ax0, y - 3, c, a);
                    psetA(cx + ax1, y - 2, c, a);
                } else {
                    fillA(cx + ax0 * scale, y - 3 * scale, scale, scale, c, a);
                    fillA(cx + ax1 * scale, y - 2 * scale, scale, scale, c, a);
                }
            }
            for (int gy = 0; gy < Font.GH; gy++)
                for (int gx = 0; gx < Font.GW; gx++)
                    if (Font.pixel(ch, gx, gy)) {
                        if (scale == 1) psetA(cx + gx, y + gy, c, a);
                        else fillA(cx + gx * scale, y + gy * scale, scale, scale, c, a);
                    }
            cx += Font.ADV * scale;
        }
    }

    /** Text with 1px dark outline (readable over any background). */
    public void textO(String s, int x, int y, int c, int outline, int scale) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dy != 0) text(s, x + dx * Math.max(1, scale / 2), y + dy * Math.max(1, scale / 2), outline, scale, 255);
        text(s, x, y, c, scale, 255);
    }

    public void textC(String s, int cx, int y, int c, int outline, int scale) {
        int tw = Font.width(s) * scale;
        textO(s, cx - tw / 2, y, c, outline, scale);
    }

    public static int textW(String s, int scale) {
        return Font.width(s) * scale;
    }

    /** Darken whole screen (for pause / transitions). */
    public void dim(int c, int a) {
        for (int i = 0; i < px.length; i++) px[i] = blend(px[i], c, a);
    }
}
