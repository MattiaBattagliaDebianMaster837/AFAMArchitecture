package it.pixelquest.core;

/** Base class for everything that moves in a level. Positions are the top-left of the hitbox, in pixels. */
public abstract class Entity {
    public static final int STOMP = 0, SPIN = 1, PUNCH = 2, BULLET = 3, STAR = 4, FALL = 5;

    public Level L;
    public float x, y, vx, vy;
    public int w, h;
    public boolean dead, onGround, hitL, hitR, hitCeil;
    public int facing = -1, t;

    // behaviour flags
    public boolean solidTiles = true, gravity = true;
    public boolean enemy;           // touches hurt the player
    public boolean stompable = true; // regular jump kills it
    public boolean spinKill = true;  // spin jump kills it
    public boolean punchable = true; // fist / bullet kill it
    public boolean platform;        // player can stand on top
    public boolean boss;
    public boolean alwaysActive;
    public boolean projectile;      // enemy projectile (fist deflects it)
    public float pdx, pdy;          // last movement (for platforms)
    public int hitStamp = -1;       // last punch id that hit this entity

    public float cx() {
        return x + w / 2f;
    }

    public float cy() {
        return y + h / 2f;
    }

    public float right() {
        return x + w;
    }

    public float bottom() {
        return y + h;
    }

    public boolean overlaps(Entity o) {
        return x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y;
    }

    public boolean overlaps(float rx, float ry, float rw, float rh) {
        return x < rx + rw && x + w > rx && y < ry + rh && y + h > ry;
    }

    public void update() {
    }

    public abstract void render(Gfx g, int camX, int camY);

    /** Defeat this entity. */
    public void kill(int how) {
        dead = true;
    }

    /** Standard physics step: gravity + tile collision. */
    protected void physics(float grav, float maxFall) {
        if (gravity) vy = Math.min(vy + grav, maxFall);
        L.move(this, vx, vy);
    }

    protected void drawSprite(Gfx g, Sprite s, int camX, int camY, boolean flip) {
        if (s == null) return;
        g.blit(s, Math.round(cx() - s.w / 2f) - camX, Math.round(bottom()) - s.h - camY, flip);
    }
}
