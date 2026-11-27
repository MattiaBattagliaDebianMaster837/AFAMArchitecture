package it.pixelquest.core;

/** Enemies, hazards and projectiles of the University of Palermo worlds. */
public final class Uni {
    static final int T = Level.T;

    // ================================================================ enemy projectile
    /** Flexible enemy projectile: arcs, sine waves, boomerangs, bouncing cans, bombs that explode. */
    public static final class UShot extends Entity {
        final Sprite[] spr;
        float grv, amp, freq, baseY, vx0;
        boolean sine, boomerang, explode, rotate;
        int bounces, life = 420;
        Entity owner;

        public UShot(float cx, float cy, float vx, float vy, Sprite... spr) {
            this.spr = spr;
            w = Math.max(3, spr[0].w - 4);
            h = Math.max(3, spr[0].h - 4);
            x = cx - w / 2f;
            y = cy - h / 2f;
            baseY = y;
            this.vx = vx;
            this.vy = vy;
            vx0 = vx;
            enemy = true;
            projectile = true;
            solidTiles = false;
            alwaysActive = true;
            stompable = false;
            spinKill = true;
            punchable = true;
        }

        public Sprite sprite() {
            return spr[0];
        }

        @Override
        public void update() {
            if (--life <= 0) {
                vanish();
                return;
            }
            if (boomerang) {
                vx -= Math.signum(vx0) * 0.11f;
                x += vx;
                y += vy;
                if (t > 30 && owner != null && Math.abs(owner.cx() - cx()) < 14 && Math.abs(owner.cy() - cy()) < 30) dead = true;
            } else if (sine) {
                x += vx;
                baseY += vy;
                y = baseY + (float) StrictMath.sin(t * freq) * amp;
            } else {
                vy = Math.min(vy + grv, 7);
                x += vx;
                y += vy;
            }
            if (!boomerang && L.solidAt(cx() + Math.signum(vx) * w / 2f, cy()) && Math.abs(vx) > 0.2f) {
                if (explode) boom();
                else vanish();
                return;
            }
            if (vy > 0 && L.solidAt(cx(), bottom())) {
                if (explode) {
                    boom();
                    return;
                }
                if (bounces > 0) {
                    bounces--;
                    y = (float) Math.floor(bottom() / T) * T - h;
                    vy = -Math.abs(vy) * 0.6f;
                } else if (!sine) {
                    vanish();
                    return;
                }
            }
            if (y > L.th * T + 40 || x < L.camX - 200 || x > L.camX + L.game.W + 200 || y < L.camY - 300) dead = true;
        }

        void boom() {
            dead = true;
            L.spawn(new Explosion(cx(), cy()));
        }

        void vanish() {
            dead = true;
            L.spawn(new Ents.Puff(cx(), cy()));
        }

        @Override
        public void kill(int how) {
            if (explode) boom();
            else vanish();
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s = spr[(t / 4) % spr.length];
            if (rotate || boomerang) g.blitEx(s, cx() - cx, cy() - cy, 1, 1, t * 0.3f, false, 255, 0);
            else g.blit(s, Math.round(cx() - s.w / 2f) - cx, Math.round(cy() - s.h / 2f) - cy, vx > 0);
        }
    }

    /** Blast from a bomb: hurts for a few frames. */
    public static final class Explosion extends Ents.Particle {
        Explosion(float cx, float cy) {
            super(cx, cy, 18);
            Sfx.play(Sfx.BREAK);
        }

        @Override
        public void update() {
            super.update();
            Player p = L.player;
            float r = 6 + t * 1.3f;
            if (t < 10 && Math.abs(p.cx() - x) < r + 4 && Math.abs(p.cy() - y) < r + 8) p.hurt(x, false);
            if (t == 1) L.shake = Math.max(L.shake, 5);
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int r = 6 + (int) (t * 1.3f);
            g.circle((int) x - cx, (int) y - cy, r, 0xFFFF9A2A, 230 - t * 12);
            g.circle((int) x - cx, (int) y - cy, Math.max(1, r - 5), 0xFFFFF0A0, 230 - t * 12);
        }
    }

    // ================================================================ turtle + shell
    /** Four-legged turtle: stomp it and it retreats into its shell. */
    public static final class Turtle extends Ents.Enemy {
        Turtle(float tx, float ty) {
            x = tx + 1;
            y = ty + 6;
            w = 14;
            h = 10;
            energy = 10;
        }

        @Override
        void act() {
            walk(0.42f, true);
        }

        @Override
        public void stomped() {
            enemy = false;
            L.onKill(this);
            dead = true;
            Shell s = new Shell(x, bottom() - 10);
            L.spawn(s);
        }

        @Override
        Sprite frame() {
            return UniArt.turtle[(t / 10) % 2];
        }

        @Override
        boolean spriteFacesRight() {
            return true;
        }
    }

    /** Turtle shell: pick it up by walking into it, kick it with C. A moving shell flattens everything. */
    public static final class Shell extends Entity {
        public static final int IDLE = 0, CARRIED = 1, MOVING = 2;
        public int state = IDLE;
        int idleT, safeT, bounces, moveT;
        Player carrier;
        /** battle: who kicked it */
        Player kicker;

        Shell(float x, float y) {
            this.x = x;
            this.y = y;
            w = 14;
            h = 9;
            alwaysActive = true;
        }

        void pickUp(Player p) {
            state = CARRIED;
            carrier = p;
            p.carrying = this;
            Sfx.play(Sfx.SELECT);
        }

        void drop() {
            state = IDLE;
            idleT = 0;
            vy = -2;
            vx = 0;
            if (carrier != null) carrier.carrying = null;
            carrier = null;
        }

        public void kick(Player p) {
            kicker = p;
            state = MOVING;
            carrier = null;
            p.carrying = null;
            facing = p.facing;
            vx = facing * 5.2f;
            vy = 0;
            x = facing > 0 ? p.right() + 1 : p.x - w - 1;
            y = p.bottom() - h - 1;
            safeT = 24;
            moveT = 0;
            bounces = 0;
            Sfx.play(Sfx.KICK);
            L.spawn(new Ents.Dust(cx(), bottom()));
        }

        /** The player touched the shell. */
        void touch(Player p, boolean fromAbove) {
            switch (state) {
                case IDLE:
                    if (fromAbove) p.bounce(false);
                    else if (p.carrying == null && idleT > 10) pickUp(p);
                    break;
                case MOVING:
                    if (fromAbove) {
                        state = IDLE;
                        idleT = 0;
                        vx = 0;
                        p.bounce(L.input.a || L.input.b);
                        Sfx.play(Sfx.STOMP);
                    } else if (L.battle != null) {
                        if (p != kicker || safeT <= 0) L.battle.shellHit(this, p);
                    } else if (safeT <= 0) p.hurt(cx(), false);
                    break;
                default:
                    break;
            }
        }

        void breakApart() {
            dead = true;
            if (carrier != null) carrier.carrying = null;
            L.spawn(new Ents.Puff(cx(), cy()));
            for (int k = 0; k < 4; k++) L.spawn(new Combat.Shard(cx(), cy(), (k - 1.5f) * 1.2f, -2.5f));
        }

        @Override
        public void update() {
            switch (state) {
                case IDLE:
                    idleT++;
                    vx = 0;
                    physics(0.4f, 6);
                    if (idleT > 480 && L.battle != null) { // battle: no turtles, the shell just breaks
                        breakApart();
                        break;
                    }
                    if (idleT > 480) { // the turtle comes back out
                        dead = true;
                        Turtle tt = new Turtle(x - 1, bottom() - 16);
                        L.spawn(tt);
                    }
                    break;
                case CARRIED: {
                    Player p = carrier;
                    if (p == null || p.dying || p.hurtT > 0 || p.carrying != this) {
                        drop();
                        break;
                    }
                    x = p.facing > 0 ? p.right() - 3 : p.x - w + 3;
                    y = p.y + 6;
                    if (L.battle != null) { // battle: a carried shell hits the rival it touches
                        Player f = L.battle.touching(this, p, null);
                        if (f != null) {
                            L.battle.shellHit(this, f);
                            breakApart();
                            return;
                        }
                    }
                    for (int i = 0; i < L.ents.size(); i++) {
                        Entity e = L.ents.get(i);
                        if (e == this || e.dead || !(e.enemy || e.boss) || !overlaps(e)) continue;
                        Combat.hit(L, e, 3, 3, Combat.NONE, Entity.BULLET, false, cx());
                        breakApart();
                        return;
                    }
                    break;
                }
                default: { // MOVING
                    moveT++;
                    if (safeT > 0) safeT--;
                    vy = Math.min(vy + 0.4f, 6);
                    float ovx = vx;
                    L.move(this, vx, vy);
                    if (hitL || hitR) {
                        int c = (int) Math.floor((ovx > 0 ? right() + 1 : x - 1) / T), r = (int) Math.floor(cy() / T);
                        byte tl = L.tile(c, r);
                        if (tl == Level.BRICK || tl == Level.QBLOCK) L.hitBlock(c, r, false);
                        vx = -ovx;
                        bounces++;
                        safeT = 0;
                        Sfx.play(Sfx.BUMP);
                    }
                    for (int i = 0; i < L.ents.size(); i++) {
                        Entity e = L.ents.get(i);
                        if (e == this || e.dead || !overlaps(e)) continue;
                        if (e instanceof Shell) {
                            ((Shell) e).breakApart();
                            breakApart();
                            return;
                        }
                        if (e.boss) {
                            Combat.hit(L, e, 3, 3, Combat.NONE, Entity.BULLET, false, cx());
                            breakApart();
                            return;
                        }
                        if (e.enemy || e.projectile) {
                            Combat.hit(L, e, 3, 3, Combat.NONE, Entity.BULLET, false, cx());
                            L.spawn(new Ents.Impact(e.cx(), e.cy()));
                        }
                    }
                    if (moveT > 600 || y > L.th * T + 30) breakApart();
                    break;
                }
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s;
            if (state == MOVING) s = UniArt.shell[(moveT / 3) % 4];
            else s = UniArt.shell[0];
            int sx = 0;
            if (state == IDLE && idleT > 400) sx = (idleT / 2) % 2 == 0 ? -1 : 1;
            g.blit(s, Math.round(cx() - s.w / 2f) - cx + sx, Math.round(bottom()) - s.h - cy + 1);
        }
    }

    // ================================================================ other enemies
    /** Flying exam sheet: swoops toward the player in waves. */
    public static final class Paper extends Ents.Enemy {
        boolean awake;
        float baseY;

        Paper(float tx, float ty) {
            x = tx + 2;
            y = ty + 3;
            w = 11;
            h = 8;
            baseY = y;
            gravity = false;
            solidTiles = false;
            energy = 10;
        }

        @Override
        void act() {
            Player p = L.player;
            if (!awake) {
                if (Math.abs(p.cx() - cx()) < 190) {
                    awake = true;
                    facing = p.cx() < cx() ? -1 : 1;
                }
                return;
            }
            if (t % 90 == 0) facing = p.cx() < cx() ? -1 : 1;
            vx += (facing * 1.7f * speedK() - vx) * 0.05f;
            baseY += Math.signum(p.cy() - 8 - baseY) * 0.45f;
            x += vx;
            y = baseY + (float) StrictMath.sin(t * 0.12) * 14;
        }

        @Override
        Sprite frame() {
            return UniArt.paper[(t / 6) % 2];
        }
    }

    /** Biting book: hops toward the player; needs two stomps. */
    public static final class Book extends Ents.Enemy {
        int wait = 30;

        Book(float tx, float ty) {
            x = tx + 2;
            y = ty + 8;
            w = 11;
            h = 8;
            hp = 2;
            energy = 14;
        }

        @Override
        void act() {
            if (onGround) {
                vx = 0;
                if (--wait <= 0) {
                    facing = L.player.cx() < cx() ? -1 : 1;
                    vy = -4.8f;
                    vx = facing * 1.9f * speedK();
                    onGround = false;
                    wait = 34;
                }
            }
            float ovx = vx;
            physics(0.32f, 6);
            if (!onGround && (hitL || hitR)) vx = -ovx;
        }

        @Override
        Sprite frame() {
            return UniArt.book[onGround ? 0 : (t / 4) % 2];
        }
    }

    /** Vending machine: tough, shoots bouncing cans. */
    public static final class Vending extends Ents.Enemy {
        int cool;

        Vending(float tx, float ty) {
            x = tx + 1;
            y = ty - 2;
            w = 14;
            h = 18;
            hp = 4;
            energy = 30;
            cool = 50 + (int) (tx / 3) % 60;
        }

        @Override
        void act() {
            physics(0.4f, 6);
            vx = 0;
            Player p = L.player;
            facing = p.cx() < cx() ? -1 : 1;
            float d = Math.abs(p.cx() - cx());
            if (--cool <= 0 && d < 260 && d > 16 && !p.dying) {
                UShot c = new UShot(cx() + facing * 8, y + 8, facing * 2.6f * speedK(), -1.8f, UniArt.can);
                c.grv = 0.12f;
                c.bounces = 2;
                c.rotate = true;
                c.stompable = true;
                L.spawn(c);
                Sfx.play(Sfx.CANNON);
                cool = (int) (125 / speedK());
            }
        }

        @Override
        Sprite frame() {
            return UniArt.vending[cool < 18 ? 1 : 0];
        }
    }

    /** Security drone: patrols in the air and drops bombs when the player is below. */
    public static final class Drone extends Ents.Enemy {
        final float x0, y0;
        int cool = 40;

        Drone(float tx, float ty) {
            x = x0 = tx + 1;
            y = y0 = ty + 4;
            w = 14;
            h = 8;
            hp = 2;
            gravity = false;
            solidTiles = false;
            energy = 15;
        }

        @Override
        void act() {
            x = x0 + (float) StrictMath.sin(t * 0.02 * speedK()) * 70;
            y = y0 + (float) StrictMath.sin(t * 0.07) * 3;
            Player p = L.player;
            if (cool > 0) cool--;
            if (cool == 0 && Math.abs(p.cx() - cx()) < 18 && p.y > y && !p.dying) {
                UShot b = new UShot(cx(), bottom() + 4, 0, 0.5f, UniArt.bomb);
                b.grv = 0.25f;
                b.explode = true;
                L.spawn(b);
                cool = (int) (70 / speedK());
            }
        }

        @Override
        Sprite frame() {
            return UniArt.drone[(t / 3) % 2];
        }
    }

    // ================================================================ hazards
    /** Ceiling security laser: blinks a warning, then fires a deadly vertical beam. */
    public static final class Laser extends Entity {
        int phase;
        float beamEnd;
        static final int CYCLE = 170, ON = 100, WARN = 72;

        Laser(float tx, float ty) {
            x = tx;
            y = ty;
            w = 16;
            h = 6;
            solidTiles = false;
            gravity = false;
            alwaysActive = true;
            phase = ((int) (tx / T) % 3) * 45;
        }

        boolean on() {
            return (t + phase) % CYCLE >= ON;
        }

        boolean warn() {
            int k = (t + phase) % CYCLE;
            return k >= WARN && k < ON;
        }

        @Override
        public void update() {
            if (beamEnd == 0) {
                beamEnd = y + 6;
                while (beamEnd < L.th * T && !L.solidAt(cx(), beamEnd + 1)) beamEnd += 4;
            }
            if ((t + phase) % CYCLE == ON && Math.abs(cx() - L.player.cx()) < L.game.W) Sfx.play(Sfx.SHOOT);
            Player p = L.player;
            if (on() && !p.dying && p.overlaps(x + 5, y + 4, 6, beamEnd - y - 4)) p.hurt(cx(), false);
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int px = (int) x - cx, py = (int) y - cy;
            int len = (int) (beamEnd - y);
            if (on()) {
                g.fillA(px + 4, py + 4, 8, len, 0xFFFF2A2A, 120);
                g.fill(px + 6, py + 4, 4, len, 0xFFFF6A6A);
                g.fill(px + 7, py + 4, 2, len, 0xFFFFFFFF);
            } else if (warn() && (t / 3) % 2 == 0) g.fillA(px + 7, py + 4, 2, len, 0xFFFF4A4A, 120);
            g.blit(UniArt.laserHead, px + 3, py);
        }
    }

    /** Atom: electrons orbit the nucleus; touching an electron hurts. */
    public static final class Atom extends Entity {
        final float ox, oy;
        static final int R = 36;

        Atom(float tx, float ty) {
            ox = tx + 8;
            oy = ty + 8;
            x = ox - R;
            y = oy - R;
            w = R * 2;
            h = R * 2;
            solidTiles = false;
            gravity = false;
            alwaysActive = true;
        }

        float angle() {
            return t * 0.035f * (1f + Math.min(L.def.world, 5) * 0.05f);
        }

        @Override
        public void update() {
            Player p = L.player;
            if (p.dying) return;
            for (int k = 0; k < 3; k++) {
                double a = angle() + k * Math.PI * 2 / 3;
                float ex = ox + (float) StrictMath.cos(a) * R, ey = oy + (float) StrictMath.sin(a) * R * (k == 1 ? 0.5f : 1f);
                if (Math.abs(p.cx() - ex) < 8 && Math.abs(p.cy() - ey) < 13) p.hurt(ex, false);
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int px = (int) ox - cx, py = (int) oy - cy;
            g.ring(px, py, R, 1, 0xFF8AE0FF, 50);
            g.circle(px - 2, py, 4, 0xFFE83A3A, 255);
            g.circle(px + 2, py + 1, 4, 0xFF3A6AE8, 255);
            g.circle(px, py - 2, 3, 0xFFE83A3A, 255);
            for (int k = 0; k < 3; k++) {
                double a = angle() + k * Math.PI * 2 / 3;
                int ex = px + (int) (StrictMath.cos(a) * R), ey = py + (int) (StrictMath.sin(a) * R * (k == 1 ? 0.5f : 1f));
                g.circle(ex, ey, 6, 0xFF8AF0FF, 80);
                g.circle(ex, ey, 4, 0xFF2AB0F0, 255);
                g.circle(ex - 1, ey - 1, 1, 0xFFFFFFFF, 255);
            }
        }
    }
}
