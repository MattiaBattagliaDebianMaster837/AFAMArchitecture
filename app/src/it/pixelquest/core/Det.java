package it.pixelquest.core;

/**
 * Deterministic math helpers. In a two-phone battle both phones simulate the same match frame by frame, so every
 * calculation must give bit-identical results on any device: the game uses StrictMath (same results everywhere)
 * instead of Math for trigonometry, and fixed conversions instead of Math.toRadians (whose formula changed between
 * Java versions).
 */
final class Det {
    private static final double D2R = Math.PI / 180.0, R2D = 180.0 / Math.PI;

    static double rad(double deg) {
        return deg * D2R;
    }

    static double deg(double rad) {
        return rad * R2D;
    }
}
