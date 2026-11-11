package it.pixelquest.desktop;

import it.pixelquest.core.Gfx;
import it.pixelquest.core.Sprite;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Writes framebuffers / sprites to PNG (nearest neighbour upscale). */
public final class Png {
    public static void save(int[] px, int w, int h, int scale, String path) throws Exception {
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h * scale; y++)
            for (int x = 0; x < w * scale; x++) img.setRGB(x, y, px[(y / scale) * w + x / scale]);
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    public static void save(Gfx g, int scale, String path) throws Exception {
        save(g.px, g.w, g.h, scale, path);
    }

    public static void save(Sprite s, int scale, String path) throws Exception {
        save(s.px, s.w, s.h, scale, path);
    }
}
