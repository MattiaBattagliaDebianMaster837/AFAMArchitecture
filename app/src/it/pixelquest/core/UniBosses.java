package it.pixelquest.core;

import java.util.Random;

/** Bosses of the University of Palermo worlds and their special hazards. */
public final class UniBosses {
    static final int T = Level.T;

    public static Bosses.Boss create(int id, float x, float y) {
        switch (id) {
            case 4: return new Martino(x, y);
            case 5: return new Valenti(x, y);
            case 6: return new Principato(x, y);
            case 7: return new Lily(x, y);
            case 8: return new Cruciata(x, y);
            default: return new Garbo(x, y);
        }
    }

    // ============================================================ shared helpers
    abstract static class Prof extends Bosses.Boss {
        final Random rnd = new Random(7);
        int fade = 255;

        Prof(float x, float y, int w, int h, int hp, String name) {
            super(x, y, w, h, hp, name);
        }

        float aL() {
            return (L.arenaCol + 1) * T;
        }

        float aR() {
            return L.tw * T;
        }

        float floorY() {
            return L.floorRow * T;
        }

        void say(String s) {
            L.spawn(new Ents.FloatText(cx(), y - 10, s, 0xFFFFE070));
        }

        Uni.UShot shot(float cx, float cy, float vx, float vy, Sprite... spr) {
            Uni.UShot s = new Uni.UShot(cx, cy, vx, vy, spr);
            L.spawn(s);
            return s;
        }

        /** Aim a projectile at the player. */
        Uni.UShot aim(float speed, Sprite spr) {
            Player p = p();
            float dx = p.cx() - cx(), dy = p.cy() - (y + 14);
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.01f;
            return shot(cx() + facing * 10, y + 14, dx / d * speed, dy / d * speed, spr);
        }

        void rain(int n, Sprite spr, float speed) {
            for (int k = 0; k < n; k++) {
                float rx = aL() + 20 + rnd.nextFloat() * (aR() - aL() - 40);
                Uni.UShot s = shot(rx, L.camY - 10 - k * 28, 0, speed, spr);
                s.grv = 0.05f;
                s.stompable = true;
            }
        }

        @Override
        public void update() {
            super.update();
            if (defeated) L.gravMul = 1f;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s = frame();
            int px = Math.round(cx() - s.w / 2f) - cx + (defeated ? (deathT % 4) - 2 : 0), py = Math.round(bottom()) - s.h - cy + 2;
            int flash = (inv > 0 && (inv / 3) % 2 == 0) || (defeated && (deathT / 3) % 2 == 0) ? 0xFFFFFFFF : 0;
            g.blit(s, px, py, facing > 0, fade, 0);
            if (flash != 0) g.blit(s, px, py, facing > 0, Math.min(150, fade), flash);
            if (freezeT > 0) g.blit(s, px, py, facing > 0, 120, 0xFF9AE4FF);
        }
    }

    // ============================================================ hazards
    /** Heavy pendulum swinging from the ceiling (Principato). */
    static final class Pendulum extends Entity {
        final float ax, ay, len;
        int life = 420;

        Pendulum(float ax, float ay, float len) {
            this.ax = ax;
            this.ay = ay;
            this.len = len;
            w = h = 20;
            solidTiles = false;
            alwaysActive = true;
        }

        float ang() {
            return (float) StrictMath.sin(t * 0.032) * 1.15f;
        }

        @Override
        public void update() {
            x = ax + (float) StrictMath.sin(ang()) * len - 10;
            y = ay + (float) StrictMath.cos(ang()) * len - 10;
            Player p = L.player;
            if (t > 20 && life > 20 && Math.abs(p.cx() - cx()) < 13 && Math.abs(p.cy() - cy()) < 18) p.hurt(cx(), false);
            if (--life <= 0) dead = true;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int a = Math.min(255, Math.min(t, life) * 12);
            for (int k = 0; k < 24; k++) {
                float f = k / 24f;
                g.psetA((int) (ax + (cx() - ax) * f) - cx, (int) (ay + (cy() - ay) * f) - cy, 0xFFB0B0C0, a);
            }
            int px = (int) cx() - cx, py = (int) cy() - cy;
            g.circle(px, py, 10, 0xFF3A3A48, a);
            g.circle(px - 3, py - 3, 3, 0xFF8A8A9A, a);
            g.text("M", px - 2, py - 3, 0xFFFFE070, 1, a);
        }
    }

    /** One bar of Lily's sorting attack: rises from the floor, then retracts. */
    static final class SortBar extends Entity {
        final int delay, maxH, num;
        final float floor;

        SortBar(float x, float floor, int maxH, int delay, int num) {
            this.x = x;
            this.floor = floor;
            this.maxH = maxH;
            this.delay = delay;
            this.num = num;
            w = 14;
            solidTiles = false;
            alwaysActive = true;
        }

        int height() {
            int k = t - delay;
            if (k < 0) return 0;
            if (k < 10) return maxH * k / 10;
            if (k < 45) return maxH;
            if (k < 55) return maxH * (55 - k) / 10;
            return 0;
        }

        @Override
        public void update() {
            int hh = height();
            h = Math.max(1, hh);
            y = floor - hh;
            Player p = L.player;
            if (hh > 4 && p.overlaps(x + 1, y, w - 2, hh)) p.hurt(cx(), false);
            if (t - delay >= 55) dead = true;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int hh = height();
            int px = (int) x - cx, py = (int) floor - cy;
            if (t < delay) {
                if ((t / 4) % 2 == 0) g.fill(px + 2, py - 2, w - 4, 2, 0xFF3AFF6A);
                return;
            }
            g.fill(px, py - hh, w, hh, 0xFF1A1018);
            g.fill(px + 1, py - hh + 1, w - 2, hh - 1, 0xFF3AC060);
            g.fill(px + 2, py - hh + 1, 2, hh - 1, 0xFF8AF0A0);
            if (hh > 10) g.text("" + num, px + 4, py - hh + 3, 0xFFFFFFFF);
        }
    }

    /** Ring of 0s and 1s orbiting the owner (Lily's infinite loop). */
    static final class OrbitBits extends Entity {
        final Entity owner;
        int life = 240;

        OrbitBits(Entity owner) {
            this.owner = owner;
            solidTiles = false;
            alwaysActive = true;
        }

        @Override
        public void update() {
            if (owner.dead || ((Bosses.Boss) owner).defeated || --life <= 0) {
                dead = true;
                return;
            }
            Player p = L.player;
            for (int k = 0; k < 8; k++) {
                double a = t * 0.06 + k * Math.PI / 4;
                float bx = owner.cx() + (float) StrictMath.cos(a) * 36, by = owner.cy() + (float) StrictMath.sin(a) * 30;
                if (Math.abs(p.cx() - bx) < 7 && Math.abs(p.cy() - by) < 12) p.hurt(bx, false);
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            for (int k = 0; k < 8; k++) {
                double a = t * 0.06 + k * Math.PI / 4;
                int bx = (int) (owner.cx() + StrictMath.cos(a) * 36) - cx, by = (int) (owner.cy() + StrictMath.sin(a) * 30) - cy;
                g.blit(UniArt.bits[k % 2], bx - 3, by - 4);
            }
        }
    }

    /** Dirac impulse: a warning on the floor, then a tall deadly spike (Garbo). */
    static final class Dirac extends Entity {
        final float floor;
        static final int WARN = 50, LIFE = 82;

        Dirac(float cx, float floor) {
            x = cx - 5;
            this.floor = floor;
            w = 10;
            h = 5 * T;
            y = floor - h;
            solidTiles = false;
            alwaysActive = true;
        }

        @Override
        public void update() {
            if (t == WARN) Sfx.play(Sfx.SHOOT);
            if (t >= WARN && t < LIFE && L.player.overlaps(this)) L.player.hurt(cx(), false);
            if (t >= LIFE) dead = true;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int px = (int) x - cx, py = (int) floor - cy;
            if (t < WARN) {
                if ((t / 4) % 2 == 0) {
                    g.fill(px, py - 3, w, 3, 0xFFFF4A4A);
                    g.text("\u0004", px + 2, py - 12, 0xFFFF4A4A);
                }
                return;
            }
            int hh = Math.min(h, (t - WARN) * 20);
            g.fillA(px - 2, py - hh, w + 4, hh, 0xFF8AF0FF, 100);
            g.fill(px + 3, py - hh, 4, hh, 0xFFFFFFFF);
            for (int k = 0; k < 5; k++) { // arrow head of the delta
                g.fill(px + 5 - k, py - hh + k, k * 2 + 1, 1, 0xFFFFFFFF);
            }
        }
    }

    /** Square wave: alternating floor segments get electrified (Garbo). */
    static final class SquareWave extends Entity {
        final float a, b, floor;
        int life = 420;
        static final int PERIOD = 80, SEG = 4 * T;

        SquareWave(float a, float b, float floor) {
            this.a = a;
            this.b = b;
            this.floor = floor;
            solidTiles = false;
            alwaysActive = true;
        }

        boolean activeSeg(int i) {
            return ((t / PERIOD) + i) % 2 == 0;
        }

        boolean warning() {
            return PERIOD - t % PERIOD < 22;
        }

        @Override
        public void update() {
            Player p = L.player;
            if (t > 40 && p.onGround && p.bottom() >= floor - 1 && !warning()) {
                int i = (int) ((p.cx() - a) / SEG);
                if (i >= 0 && activeSeg(i)) p.hurt(p.cx() + 1, false);
            }
            if (--life <= 0) dead = true;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int n = (int) Math.ceil((b - a) / SEG);
            for (int i = 0; i < n; i++) {
                int sx = (int) (a + i * SEG) - cx, sy = (int) floor - cy;
                boolean on = activeSeg(i) && t > 40;
                if (on && warning() && (t / 3) % 2 == 0) continue;
                if (on) {
                    g.fillA(sx, sy - 6, SEG, 6, 0xFFFFE040, 120);
                    for (int x = 0; x < SEG; x += 4) g.fill(sx + x, sy - 2 - ((x / 4 + t / 3) % 2) * 3, 4, 1, 0xFFFFFFFF);
                } else if (t > 40) g.fillA(sx, sy - 2, SEG, 2, 0xFF3A6AFF, 90);
            }
        }
    }

    /** Fourier "harmonic" of Garbo: a ghost copy that fires one sine volley and fades. */
    static final class Ghost extends Entity {
        final Prof owner;
        final int dir;

        Ghost(Prof owner, float x, float y, int dir) {
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.dir = dir;
            w = 22;
            h = 46;
            solidTiles = false;
            alwaysActive = true;
        }

        @Override
        public void update() {
            if (t == 40) {
                for (int k = 0; k < 3; k++) {
                    Uni.UShot s = new Uni.UShot(cx() + dir * 10, y + 14, dir * 2.2f, 0, UniArt.sineOrb);
                    s.sine = true;
                    s.amp = 22;
                    s.freq = 0.1f;
                    s.t = k * 20;
                    L.spawn(s);
                }
                Sfx.play(Sfx.CANNON);
            }
            if (t > 110 || owner.defeated) dead = true;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s = UniArt.garbo[t > 30 && t < 50 ? 1 : 0];
            int a = t < 20 ? t * 6 : t > 80 ? Math.max(0, (110 - t) * 4) : 120;
            g.blit(s, Math.round(cx() - s.w / 2f) - cx, Math.round(bottom()) - s.h - cy, dir > 0, a, 0xFF8AF0FF);
        }
    }

    // ============================================================ Martino (mid boss, 1st year)
    static final class Martino extends Prof {
        int throwsLeft, rainLeft;

        Martino(float x, float y) {
            super(x, y, 22, 46, 18, "MARTINO");
        }

        @Override
        void think() {
            Player p = p();
            switch (state) {
                case 0: // walk toward the player
                    face();
                    vx = facing * (0.8f + rage() * 0.8f);
                    if (onGround && p.y < y - 30 && Math.abs(p.cx() - cx()) < 50 && t % 30 == 0) vy = -7.5f;
                    if (--timer <= 0) {
                        int pick = (t / 13) % (rage() > 0.35f ? 3 : 2);
                        if (pick == 0) {
                            state = 1;
                            throwsLeft = 3 + (int) (rage() * 3);
                            timer = 12;
                            say("LIBRI IN TESTA!");
                        } else if (pick == 1) {
                            state = 2;
                            timer = 30;
                            Sfx.play(Sfx.ROAR);
                        } else {
                            state = 3;
                            rainLeft = 6;
                            timer = 0;
                            say("PIOGGIA DI LIBRI!");
                        }
                    }
                    break;
                case 1: // throw books in arcs
                    vx = 0;
                    face();
                    if (--timer <= 0) {
                        float dx = p.cx() - cx();
                        Uni.UShot s = shot(cx() + facing * 10, y + 10, Math.max(-3.4f, Math.min(3.4f, dx / 48f)), -4.6f, UniArt.bookShot);
                        s.grv = 0.2f;
                        s.rotate = true;
                        Sfx.play(Sfx.SPIN);
                        timer = 16;
                        if (--throwsLeft <= 0) {
                            state = 0;
                            timer = 60;
                        }
                    }
                    break;
                case 2: // dash
                    if (timer > 0) {
                        timer--;
                        vx = 0;
                        if (t % 6 == 0) L.spawn(new Ents.Dust(cx() - facing * 10, bottom()));
                    } else {
                        vx = facing * (4f + rage());
                        if (hitL || hitR) {
                            state = 4;
                            timer = 55;
                            vy = -3;
                            vx = -facing * 1.5f;
                            L.shake = 10;
                            Sfx.play(Sfx.THUD);
                        }
                    }
                    break;
                case 3: // book rain
                    vx = 0;
                    if (--timer <= 0) {
                        rain(1, UniArt.bookShot, 1.2f);
                        timer = 9;
                        if (--rainLeft <= 0) {
                            state = 0;
                            timer = 70;
                        }
                    }
                    break;
                default: // stunned
                    vx *= 0.9f;
                    if (--timer <= 0) {
                        state = 0;
                        timer = 30;
                    }
                    break;
            }
            physics(0.4f, 7);
        }

        @Override
        Sprite frame() {
            return UniArt.martino[state == 1 || state == 3 ? 1 : 0];
        }
    }

    // ============================================================ Angela Valenti (end boss, 1st year)
    static final class Valenti extends Prof {
        float[][] spots;
        int spot, casts;

        Valenti(float x, float y) {
            super(x, y, 22, 46, 22, "ANGELA VALENTI");
            gravity = false;
            solidTiles = false;
        }

        @Override
        public void wake() {
            super.wake();
            float a = L.arenaCol * T;
            spots = new float[][]{{a + 7 * T, 9 * T - h}, {a + 24 * T, 9 * T - h}, {a + 15 * T, floorY() - h}, {a + 4 * T, floorY() - h}, {a + 28 * T, floorY() - h}};
            state = 0;
            timer = 40;
        }

        @Override
        public boolean stompableNow() {
            return super.stompableNow() && fade > 200;
        }

        @Override
        public boolean harmful() {
            return super.harmful() && fade > 120;
        }

        @Override
        void think() {
            face();
            switch (state) {
                case 0: // chalk fan
                    if (--timer <= 0) {
                        Player p = p();
                        double base = StrictMath.atan2(p.cy() - (y + 14), p.cx() - cx());
                        int n = rage() > 0.5f ? 7 : 5;
                        for (int k = 0; k < n; k++) {
                            double ang = base + (k - (n - 1) / 2.0) * 0.22;
                            float sp = 2.6f + rage();
                            Uni.UShot s = shot(cx() + facing * 10, y + 14, (float) StrictMath.cos(ang) * sp, (float) StrictMath.sin(ang) * sp, UniArt.chalk);
                            s.rotate = true;
                        }
                        Sfx.play(Sfx.SHOOT);
                        timer = 45;
                        if (++casts >= 2) {
                            casts = 0;
                            state = 1;
                            timer = 0;
                            say("INTERROGAZIONE!");
                        }
                    }
                    break;
                case 1: // question marks raining down in waves
                    if (--timer <= 0) {
                        float rx = aL() + 20 + rnd.nextFloat() * (aR() - aL() - 40);
                        Uni.UShot s = shot(rx, L.camY - 10, 0, 1.3f + rage() * 0.6f, UniArt.qmark);
                        s.sine = true;
                        s.amp = 6;
                        s.freq = 0.15f;
                        timer = 10;
                        if (++casts >= 8) {
                            casts = 0;
                            state = 2;
                        }
                    }
                    break;
                case 2: // fade out and teleport
                    fade -= 15;
                    if (fade <= 0) {
                        fade = 0;
                        spot = (spot + 1 + (t % 3)) % spots.length;
                        x = spots[spot][0];
                        y = spots[spot][1];
                        state = 3;
                        if (rage() > 0.5f) { // homework: exam sheets fly in
                            int papers = 0;
                            for (Entity e : L.ents) if (e instanceof Uni.Paper && !e.dead) papers++;
                            for (int k = papers; k < 3; k++) L.spawn(new Uni.Paper(aL() + (k * 7 + 3) * T, 3 * T));
                        }
                    }
                    break;
                default: // fade in, then a vulnerable pause
                    if (fade < 255) fade = Math.min(255, fade + 12);
                    else if (++timer > 50 - (int) (rage() * 20)) {
                        state = 0;
                        timer = 10;
                    }
                    break;
            }
        }

        @Override
        void onHurt() {
            if (state == 3 || state == 0) {
                state = 2;
                timer = 0;
            }
        }

        @Override
        Sprite frame() {
            return UniArt.valenti[state == 0 && timer < 12 || state == 1 ? 1 : 0];
        }
    }

    // ============================================================ Principato (mid boss, 2nd year, physics)
    static final class Principato extends Prof {
        int sub, gravT;

        Principato(float x, float y) {
            super(x, y, 24, 46, 26, "PROF. PRINCIPATO");
        }

        @Override
        void think() {
            Player p = p();
            if (gravT > 0) {
                gravT--;
                if (gravT % 22 == 0) rain(1, UniArt.apple, 1.5f);
                if (gravT == 0) {
                    L.gravMul = 1f;
                    say("GRAVITÀ NORMALE");
                }
            }
            switch (state) {
                case 0: // grumpy slow walk
                    face();
                    vx = facing * (0.45f + rage() * 0.5f);
                    if (--timer <= 0) {
                        state = 1 + (sub++ % 4);
                        timer = 20;
                        if (state == 1) say("VETTORI!");
                        if (state == 2) say("PENDOLO SEMPLICE!");
                        if (state == 3) say("GRAVITÀ RADDOPPIATA!");
                    }
                    break;
                case 1: // radial burst of vectors
                    vx = 0;
                    if (--timer <= 0) {
                        int n = rage() > 0.5f ? 16 : 8;
                        for (int k = 0; k < n; k++) {
                            double a = k * Math.PI * 2 / n + (sub % 2) * 0.2;
                            int d = (int) Math.round((a / (Math.PI / 4))) & 7;
                            shot(cx(), y + 18, (float) StrictMath.cos(a) * 2.6f, (float) StrictMath.sin(a) * 2.6f, UniArt.arrow[d]);
                        }
                        Sfx.play(Sfx.CANNON);
                        state = 0;
                        timer = 80;
                    }
                    break;
                case 2: // pendulum
                    vx = 0;
                    if (--timer <= 0) {
                        boolean exists = false;
                        for (Entity e : L.ents) if (e instanceof Pendulum && !e.dead) exists = true;
                        if (!exists) L.spawn(new Pendulum((aL() + aR()) / 2f, 1 * T, 9.4f * T));
                        state = 0;
                        timer = 70;
                    }
                    break;
                case 3: // doubled gravity + falling apples
                    vx = 0;
                    if (--timer <= 0) {
                        L.gravMul = 1.75f;
                        gravT = 300;
                        L.banner = "GRAVITÀ RADDOPPIATA!";
                        L.bannerT = 60;
                        state = 0;
                        timer = 90;
                    }
                    break;
                default: // jump and stomp: shock waves
                    if (timer == 20) {
                        vy = -7.5f;
                        vx = facing * 1.4f;
                        timer = 18;
                    } else if (onGround && vy >= 0 && timer < 19) {
                        L.shake = 12;
                        Sfx.play(Sfx.THUD);
                        L.spawn(new Ents.Shot(x - 4, bottom() - 6, -2.4f, 0, Ents.Shot.BALL));
                        L.spawn(new Ents.Shot(right() + 4, bottom() - 6, 2.4f, 0, Ents.Shot.BALL));
                        state = 0;
                        timer = 70;
                    }
                    break;
            }
            physics(0.4f, 7);
            if (state == 0 && (hitL || hitR)) facing = -facing;
        }

        @Override
        Sprite frame() {
            return UniArt.principato[state == 1 || state == 3 || state == 2 ? 1 : 0];
        }
    }

    // ============================================================ Lily (end boss, 2nd year, algorithm blob)
    static final class Lily extends Prof {
        int jumps, sub;
        float targetX;

        Lily(float x, float y) {
            super(x, y, 36, 28, 24, "LILY, IL BLOB DEGLI ALGORITMI");
        }

        @Override
        void think() {
            Player p = p();
            switch (state) {
                case 0: // small hops
                    face();
                    if (onGround) {
                        vx = 0;
                        if (t % 40 == 0) {
                            vy = -4.5f;
                            vx = facing * (1.2f + rage());
                        }
                    }
                    if (--timer <= 0) {
                        state = 1 + (sub++ % 3);
                        timer = 25;
                        jumps = 3;
                        if (state == 1) say("RICERCA BINARIA!");
                        if (state == 2) say("ORDINAMENTO!");
                        if (state == 3) say("CICLO INFINITO!");
                    }
                    break;
                case 1: // binary search: jump to the midpoint between herself and the player
                    if (onGround && --timer <= 0) {
                        if (jumps <= 0) {
                            state = 0;
                            timer = 60;
                            break;
                        }
                        targetX = jumps == 1 ? p.cx() : (cx() + p.cx()) / 2f;
                        vy = -7.2f;
                        vx = (targetX - cx()) / 28f;
                        timer = 8;
                        jumps--;
                    }
                    if (onGround && vy >= 0 && Math.abs(vx) > 0.01f) {
                        vx = 0;
                        L.shake = 6;
                        Sfx.play(Sfx.THUD);
                        if (jumps == 0) {
                            L.spawn(new Ents.Shot(x - 4, bottom() - 6, -2.4f, 0, Ents.Shot.BALL));
                            L.spawn(new Ents.Shot(right() + 4, bottom() - 6, 2.4f, 0, Ents.Shot.BALL));
                        }
                    }
                    break;
                case 2: { // sorting: bars rise in ascending order across the arena
                    vx = 0;
                    if (--timer <= 0) {
                        int n = 9;
                        int[] hs = new int[n];
                        for (int k = 0; k < n; k++) hs[k] = 18 + rnd.nextInt(48);
                        java.util.Arrays.sort(hs);
                        boolean rev = p.cx() > cx();
                        float span = (aR() - aL() - 40) / n;
                        for (int k = 0; k < n; k++) {
                            int idx = rev ? n - 1 - k : k;
                            L.spawn(new SortBar(aL() + 20 + idx * span, floorY(), hs[k], 30 + k * (int) (11 - rage() * 4), hs[k] / 8));
                        }
                        state = 0;
                        timer = 150;
                    }
                    break;
                }
                default: // infinite loop: orbiting bits
                    vx = 0;
                    if (--timer <= 0) {
                        L.spawn(new OrbitBits(this));
                        state = 0;
                        timer = 90;
                    }
                    break;
            }
            physics(0.35f, 7);
        }

        @Override
        void onHurt() {
            // recursion: she splits off small blobs
            int minis = 0;
            for (Entity e : L.ents) if (e instanceof Ents.Slime && !e.dead) minis++;
            if (minis < 4) {
                Ents.Slime s = new Ents.Slime(cx() - 6, y - 4, 5);
                s.vy = -3;
                L.spawn(s);
                say("RICORSIONE!");
            }
        }

        @Override
        Sprite frame() {
            if (!onGround) return UniArt.lily[2];
            return UniArt.lily[(t / 15) % 2];
        }
    }

    // ============================================================ Luca Cruciata (mid boss, final year)
    static final class Cruciata extends Prof {
        int dashes, sub, waves;

        Cruciata(float x, float y) {
            super(x, y, 22, 46, 26, "LUCA CRUCIATA");
        }

        @Override
        void think() {
            Player p = p();
            switch (state) {
                case 0: // chase
                    face();
                    vx = facing * (1.8f + rage() * 1.2f);
                    if (onGround && Math.abs(p.cx() - cx()) < 40 && t % 25 == 0) vy = -7f;
                    if (--timer <= 0) {
                        state = 1 + (sub++ % 3);
                        timer = 20;
                        dashes = 3;
                        waves = 3;
                        if (state == 1) say("SCATTO!");
                        if (state == 2) say("FRISBEE!");
                        if (state == 3) say("MEGAFONO!");
                    }
                    break;
                case 1: // three quick dashes from wall to wall
                    if (timer > 0) {
                        timer--;
                        vx = 0;
                    } else {
                        vx = facing * (5f + rage());
                        if (onGround && Math.abs(p.cx() - cx()) < 30 && t % 8 == 0) vy = -6f;
                        if (hitL || hitR) {
                            facing = -facing;
                            timer = 12;
                            L.shake = 4;
                            if (--dashes <= 0) {
                                state = 0;
                                timer = 70;
                            }
                        }
                    }
                    break;
                case 2: // boomerang frisbees
                    vx = 0;
                    face();
                    if (--timer <= 0) {
                        Uni.UShot d = aim(5f, UniArt.disc);
                        d.boomerang = true;
                        d.owner = this;
                        d.vx0 = d.vx;
                        d.life = 160;
                        Sfx.play(Sfx.SPIN);
                        timer = 30;
                        if (--waves <= 1) {
                            state = 0;
                            timer = 80;
                        }
                    }
                    break;
                default: // megaphone: sound waves along the floor
                    vx = 0;
                    if (--timer <= 0) {
                        for (int dir = -1; dir <= 1; dir += 2) {
                            Uni.UShot w = shot(cx() + dir * 12, bottom() - 6, dir * (2.6f + rage()), 0, UniArt.wave);
                            w.sine = true;
                            w.amp = 3;
                            w.freq = 0.4f;
                        }
                        Sfx.play(Sfx.ROAR);
                        timer = 22;
                        if (--waves <= 0) {
                            state = 0;
                            timer = 70;
                        }
                    }
                    break;
            }
            physics(0.4f, 7);
            if (state == 0 && (hitL || hitR)) facing = -facing;
        }

        @Override
        Sprite frame() {
            return UniArt.cruciata[state == 2 || state == 3 ? 1 : 0];
        }
    }

    // ============================================================ Giovanni Garbo (final boss)
    static final class Garbo extends Prof {
        float[][] spots;
        int spot, sub, bursts;

        Garbo(float x, float y) {
            super(x, y, 22, 46, 36, "PROF. GIOVANNI GARBO");
            gravity = false;
            solidTiles = false;
        }

        @Override
        public void wake() {
            super.wake();
            float a = L.arenaCol * T;
            spots = new float[][]{{a + 15 * T, floorY() - h}, {a + 7 * T, 9 * T - h}, {a + 24 * T, 9 * T - h}, {a + 4 * T, floorY() - h}, {a + 28 * T, floorY() - h}};
            x = spots[0][0];
            y = spots[0][1];
            state = 0;
            timer = 50;
            say("BENVENUTI ALL'ESAME DI TEORIA DEI SEGNALI!");
        }

        @Override
        public boolean stompableNow() {
            return super.stompableNow() && fade > 200;
        }

        @Override
        public boolean harmful() {
            return super.harmful() && fade > 120;
        }

        @Override
        void think() {
            face();
            Player p = p();
            switch (state) {
                case 0: // choose the next signal
                    if (--timer <= 0) {
                        int n = rage() > 0.45f ? 5 : 3;
                        state = 1 + (sub++ % n);
                        timer = 18;
                        bursts = 3;
                        switch (state) {
                            case 1: say("SEGNALI ALEATORI!"); break;
                            case 2: say("SINUSOIDE!"); break;
                            case 3: say("IMPULSI DI DIRAC!"); break;
                            case 4: say("ONDA QUADRA!"); break;
                            default: say("TRASFORMATA DI FOURIER!"); break;
                        }
                    }
                    break;
                case 1: // random signals: white-noise bursts
                    if (--timer <= 0) {
                        double base = StrictMath.atan2(p.cy() - (y + 14), p.cx() - cx());
                        int n = 9 + (int) (rage() * 5);
                        for (int k = 0; k < n; k++) {
                            double a = base + (rnd.nextFloat() - 0.5f) * 2.6f;
                            float sp = 1.3f + rnd.nextFloat() * (2.4f + rage());
                            shot(cx() + facing * 8, y + 14, (float) StrictMath.cos(a) * sp, (float) StrictMath.sin(a) * sp, UniArt.noise[rnd.nextInt(UniArt.noise.length)]);
                        }
                        Sfx.play(Sfx.CANNON);
                        timer = 34;
                        if (--bursts <= 0) next();
                    }
                    break;
                case 2: // sinusoids
                    if (--timer <= 0) {
                        int dir = p.cx() < cx() ? -1 : 1;
                        for (int k = 0; k < 4; k++) {
                            Uni.UShot s = shot(cx() + dir * 10, y + 16, dir * (2.2f + rage() * 0.8f), 0, UniArt.sineOrb);
                            s.sine = true;
                            s.amp = 26;
                            s.freq = 0.09f;
                            s.t = k * 17; // phase shift
                        }
                        Sfx.play(Sfx.SHOOT);
                        timer = 50;
                        if (--bursts <= 1) next();
                    }
                    break;
                case 3: // Dirac impulses
                    if (--timer <= 0) {
                        int n = 5 + (int) (rage() * 3);
                        L.spawn(new Dirac(p.cx(), floorY()));
                        for (int k = 1; k < n; k++) L.spawn(new Dirac(aL() + 16 + rnd.nextFloat() * (aR() - aL() - 32), floorY()));
                        timer = 70;
                        if (--bursts <= 1) next();
                    }
                    break;
                case 4: // square wave on the floor
                    if (--timer <= 0) {
                        boolean exists = false;
                        for (Entity e : L.ents) if (e instanceof SquareWave && !e.dead) exists = true;
                        if (!exists) L.spawn(new SquareWave(aL(), aR(), floorY()));
                        next();
                    }
                    break;
                case 5: // Fourier: harmonics (ghost copies) attack from other spots
                    if (--timer <= 0) {
                        for (int k = 1; k <= 2; k++) {
                            float[] sp = spots[(spot + k * 2) % spots.length];
                            L.spawn(new Ghost(this, sp[0], sp[1], sp[0] < p.cx() ? 1 : -1));
                        }
                        next();
                    }
                    break;
                case 6: // fade out and move
                    fade -= 14;
                    if (fade <= 0) {
                        fade = 0;
                        spot = (spot + 1 + rnd.nextInt(spots.length - 1)) % spots.length;
                        x = spots[spot][0];
                        y = spots[spot][1];
                        state = 7;
                        timer = 0;
                    }
                    break;
                default: // fade in, then a vulnerable pause
                    if (fade < 255) fade = Math.min(255, fade + 12);
                    else if (++timer > 55 - (int) (rage() * 25)) {
                        state = 0;
                        timer = 10;
                    }
                    break;
            }
        }

        void next() {
            state = 6;
            timer = 0;
        }

        @Override
        void onHurt() {
            if (state == 7 || state == 0) {
                state = 6;
                timer = 0;
            }
        }

        @Override
        Sprite frame() {
            return UniArt.garbo[state >= 1 && state <= 5 ? 1 : 0];
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            // a little sine wave dances around him
            if (!defeated && fade > 40)
                for (int k = 0; k < 30; k++) {
                    int sx = (int) (cx() - 30 + k * 2) - cx, sy = (int) (y - 8 + StrictMath.sin(k * 0.4 + t * 0.2) * 4) - cy;
                    g.psetA(sx, sy, 0xFF3AFF6A, fade / 2);
                }
            super.render(g, cx, cy);
        }
    }
}
