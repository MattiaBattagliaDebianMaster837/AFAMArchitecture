package it.pixelquest.core;

/** End-of-world bosses. Sprites are procedurally shaded pixel art. */
public final class Bosses {
    static final int T = Level.T;

    public static Boss create(int world, float x, float y) {
        switch (world) {
            case 1: return new Scarab(x, y);
            case 2: return new Yeti(x, y);
            case 3: return new Magmus(x, y);
            default: return new Boar(x, y);
        }
    }

    // ------------------------------------------------------------- drawing kit
    static void ell(Sprite s, float cx, float cy, float rx, float ry, int c) {
        int lo = Gfx.shade(c, 0.72f), hi = Gfx.shade(c, 1.22f);
        for (int y = (int) (cy - ry - 1); y <= cy + ry + 1; y++)
            for (int x = (int) (cx - rx - 1); x <= cx + rx + 1; x++) {
                float dx = (x + .5f - cx) / rx, dy = (y + .5f - cy) / ry;
                float d = dx * dx + dy * dy;
                if (d > 1) continue;
                float l = -dx * 0.6f - dy * 0.8f;
                s.set(x, y, l > 0.55f ? hi : (l < -0.35f && d > 0.45f) ? lo : c);
            }
    }

    static void box(Sprite s, int x, int y, int w, int h, int c) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) s.set(xx, yy, c);
    }

    static void tri(Sprite s, int x0, int y0, int x1, int y1, int x2, int y2, int c) {
        int minx = Math.min(x0, Math.min(x1, x2)), maxx = Math.max(x0, Math.max(x1, x2));
        int miny = Math.min(y0, Math.min(y1, y2)), maxy = Math.max(y0, Math.max(y1, y2));
        for (int y = miny; y <= maxy; y++)
            for (int x = minx; x <= maxx; x++) {
                float d1 = (x - x1) * (y0 - y1) - (x0 - x1) * (y - y1);
                float d2 = (x - x2) * (y1 - y2) - (x1 - x2) * (y - y2);
                float d3 = (x - x0) * (y2 - y0) - (x2 - x0) * (y - y0);
                boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
                if (!(neg && pos)) s.set(x, y, c);
            }
    }

    // =================================================================== base
    public abstract static class Boss extends Entity {
        public String name;
        public int hp, maxHp;
        int inv, state, timer, deathT;
        boolean awake, defeated;
        Sprite[] spr;

        Boss(float x, float y, int w, int h, int hp, String name) {
            this.x = x;
            this.y = y - h + T;
            this.w = w;
            this.h = h;
            this.hp = this.maxHp = hp;
            this.name = name;
            enemy = true;
            boss = true;
            alwaysActive = true;
            facing = -1;
        }

        public void wake() {
            awake = true;
            timer = 60;
        }

        public boolean stompableNow() {
            return inv == 0 && !defeated && awake;
        }

        public boolean harmful() {
            return !defeated && inv < 45;
        }

        public void hit(int dmg, float fromX) {
            if (inv > 0 || defeated || !awake) return;
            hp -= dmg;
            inv = 70;
            L.shake = 8;
            Sfx.play(Sfx.BOSSHIT);
            if (hp <= 0) {
                hp = 0;
                defeated = true;
                enemy = false;
                deathT = 0;
            } else onHurt();
        }

        void onHurt() {
        }

        float rage() {
            return 1f - hp / (float) maxHp;
        }

        @Override
        public void update() {
            if (defeated) {
                deathT++;
                vx = 0;
                if (deathT % 6 == 0) {
                    L.spawn(new Ents.Puff(x + (deathT * 13) % w, y + (deathT * 7) % h));
                    Sfx.play(Sfx.BREAK);
                }
                if (deathT == 100) {
                    dead = true;
                    for (int k = 0; k < 10; k++) L.spawn(new Ents.Puff(x + (k * 11) % w, y + (k * 17) % h));
                    L.bossDefeated();
                }
                return;
            }
            if (inv > 0) inv--;
            if (!awake) {
                physics(0.4f, 6);
                return;
            }
            think();
        }

        abstract void think();

        abstract Sprite frame();

        Player p() {
            return L.player;
        }

        void face() {
            facing = p().cx() < cx() ? -1 : 1;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s = frame();
            int flash = (inv > 0 && (inv / 3) % 2 == 0) || (defeated && (deathT / 3) % 2 == 0) ? 0xFFFFFFFF : 0;
            int sx = defeated ? (deathT % 4) - 2 : 0;
            g.blit(s, Math.round(cx() - s.w / 2f) - cx + sx, Math.round(bottom()) - s.h - cy + 2, facing > 0, 255, 0);
            if (flash != 0) g.blit(s, Math.round(cx() - s.w / 2f) - cx + sx, Math.round(bottom()) - s.h - cy + 2, facing > 0, 150, flash);
        }
    }

    // ========================================================= World 1: boar king
    static Sprite[] boarSprites;

    static Sprite boar(int legPhase, boolean charge, boolean dizzy) {
        Sprite s = new Sprite(50, 40);
        int fur = 0xFF7A4A2C, dark = 0xFF4E2C18, belly = 0xFFA87850, snout = 0xFFE8A0A0;
        int lift = charge ? 2 : 0;
        // legs
        int[] lx = {12, 20, 30, 38};
        for (int i = 0; i < 4; i++) {
            int off = ((i + legPhase) % 2 == 0) ? 0 : 2;
            box(s, lx[i], 30 - off, 5, 8, dark);
            box(s, lx[i], 36, 5, 2, 0xFF2A1A12);
        }
        ell(s, 28, 24, 18, 11, fur);       // body
        ell(s, 28, 29, 13, 5, belly);
        for (int i = 0; i < 9; i++) tri(s, 18 + i * 3, 14 - (i % 2), 21 + i * 3, 9 + (i % 3), 23 + i * 3, 15, dark); // mane
        int hx = 11, hy = 20 + lift;
        ell(s, hx, hy, 10, 9, fur);         // head
        ell(s, hx - 7, hy + 4, 5, 4, snout);// snout
        s.set(hx - 10, hy + 3, 0xFF5A2A2A);
        s.set(hx - 8, hy + 3, 0xFF5A2A2A);
        tri(s, hx - 4, hy + 6, hx - 11, hy - 1, hx - 2, hy + 4, 0xFFF8F4E8); // tusk
        tri(s, hx + 1, hy - 7, hx + 4, hy - 13, hx + 6, hy - 6, dark);         // ear
        if (dizzy) {
            box(s, hx - 3, hy - 3, 4, 1, 0xFF1A1018);
            box(s, hx - 2, hy - 4, 2, 3, 0xFF1A1018);
        } else {
            box(s, hx - 4, hy - 3, 4, 3, 0xFFFFFFFF);
            box(s, hx - 4, hy - 2, 2, 2, 0xFFD02020);
            for (int i = 0; i < 5; i++) s.set(hx - 5 + i, hy - 5 + (i < 2 ? 0 : 1), 0xFF1A1018); // angry brow
        }
        // crown
        int cy = hy - 11;
        box(s, hx - 3, cy + 3, 11, 3, 0xFFFFC830);
        tri(s, hx - 3, cy + 3, hx - 2, cy - 2, hx, cy + 3, 0xFFFFC830);
        tri(s, hx + 1, cy + 3, hx + 2, cy - 3, hx + 4, cy + 3, 0xFFFFC830);
        tri(s, hx + 5, cy + 3, hx + 6, cy - 2, hx + 7, cy + 3, 0xFFFFC830);
        s.set(hx + 2, cy + 4, 0xFFE03050);
        box(s, hx - 3, cy + 5, 11, 1, 0xFFC08A10);
        // tail
        s.set(46, 18, dark);
        s.set(47, 17, dark);
        s.set(47, 16, dark);
        return s.outlined(Art.OUT);
    }

    static final class Boar extends Boss {
        Boar(float x, float y) {
            super(x, y, 40, 28, 6, "RE CINGHIALONE");
            if (boarSprites == null)
                boarSprites = new Sprite[]{boar(0, false, false), boar(1, false, false), boar(0, true, false), boar(1, true, false), boar(0, false, true)};
        }

        @Override
        void think() {
            switch (state) {
                case 0: // idle: decide
                    vx *= 0.8f;
                    face();
                    if (--timer <= 0) {
                        if (rage() > 0.4f && (t / 7) % 3 == 0) {
                            state = 4;
                            vy = -8;
                            vx = facing * 1.6f;
                        } else {
                            state = 1;
                            timer = 40;
                        }
                    }
                    break;
                case 1: // paw the ground
                    if (t % 8 == 0) L.spawn(new Ents.Dust(facing < 0 ? right() : x, bottom()));
                    if (--timer <= 0) {
                        state = 2;
                        Sfx.play(Sfx.ROAR);
                    }
                    break;
                case 2: // charge
                    vx = facing * (3.0f + rage() * 1.6f);
                    if (t % 4 == 0) L.spawn(new Ents.Dust(cx() - facing * 16, bottom()));
                    if (hitL || hitR) {
                        state = 3;
                        timer = 100 - (int) (rage() * 30);
                        vx = -facing * 1.5f;
                        vy = -3;
                        L.shake = 14;
                        Sfx.play(Sfx.THUD);
                        // rocks fall from the ceiling
                        for (int k = 0; k < 2 + (int) (rage() * 3); k++)
                            L.spawn(new Ents.Shot(L.camX + 40 + ((t * 37 + k * 97) % (L.game.W - 80)), L.camY - 10 - k * 30, 0, 0.5f, Ents.Shot.ROCK));
                    }
                    break;
                case 3: // stunned
                    vx *= 0.9f;
                    if (--timer <= 0) {
                        state = 0;
                        timer = 30;
                    }
                    break;
                case 4: // jump slam
                    if (onGround && vy >= 0 && t > 2) {
                        state = 0;
                        timer = 50;
                        L.shake = 12;
                        Sfx.play(Sfx.THUD);
                        L.spawn(new Ents.Shot(x - 6, bottom() - 6, -2.2f, 0, Ents.Shot.BALL));
                        L.spawn(new Ents.Shot(right() + 6, bottom() - 6, 2.2f, 0, Ents.Shot.BALL));
                    }
                    break;
            }
            physics(0.4f, 7);
        }

        @Override
        public boolean harmful() {
            return super.harmful();
        }

        @Override
        Sprite frame() {
            if (state == 3) return boarSprites[4];
            if (state == 2) return boarSprites[2 + (t / 3) % 2];
            if (Math.abs(vx) > 0.2f || state == 1) return boarSprites[(t / 6) % 2];
            return boarSprites[0];
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            super.render(g, cx, cy);
            if (state == 3 && !defeated) { // dizzy stars
                for (int k = 0; k < 3; k++) {
                    double a = t * 0.15 + k * 2.1;
                    g.blit(Art.sparkle[k % 2], (int) (cx() + Math.cos(a) * 14) - cx - 2, (int) (y - 8 + Math.sin(a) * 4) - cy);
                }
            }
        }
    }

    // ===================================================== World 2: scarab pharaoh
    static Sprite[] scarabSprites;

    static Sprite scarab(int wing, boolean grounded) {
        Sprite s = new Sprite(48, 40);
        int shell = 0xFF2A5A9A, gold = 0xFFF2C040, dark = 0xFF14284A;
        if (!grounded) { // wings
            int wc = 0xFFBFE8FF;
            int wy = wing == 0 ? 2 : 10;
            tri(s, 26, 16, 46, wy, 44, wy + 14, wc);
            tri(s, 26, 16, 40, wy - 2, 34, wy + 12, Gfx.shade(wc, 0.9f));
        }
        for (int i = 0; i < 3; i++) { // legs
            int lx = 14 + i * 9;
            box(s, lx, 30, 2, 6 + (grounded ? 2 : 0), dark);
            box(s, lx - 1, 36 + (grounded ? 2 : 0) - 1, 3, 1, dark);
        }
        ell(s, 28, 24, 15, 11, shell);
        box(s, 28, 13, 1, 22, dark);
        for (int i = 0; i < 4; i++) box(s, 16 + i * 6, 18 + (i % 2) * 6, 3, 1, gold);
        // head with striped headdress
        ell(s, 11, 22, 9, 8, 0xFF1E3E6E);
        for (int y = 13; y < 31; y++)
            for (int x = 2; x < 21; x++) {
                float dx = (x - 11) / 10f, dy = (y - 21) / 10f;
                if (dx * dx + dy * dy < 1 && (y / 2) % 2 == 0 && x > 12) s.set(x, y, gold);
            }
        ell(s, 7, 23, 5, 5, 0xFFE8B070); // face (golden mask)
        box(s, 4, 21, 3, 2, 0xFF1A1018);
        s.set(5, 21, 0xFFFF4040);
        box(s, 3, 26, 4, 1, 0xFF8A5A20);
        box(s, 6, 28, 3, 5, gold);        // beard
        tri(s, 8, 14, 13, 7, 15, 15, gold); // cobra crest
        s.set(12, 10, 0xFFE03030);
        return s.outlined(Art.OUT);
    }

    static final class Scarab extends Boss {
        int drops;
        float baseY, ang;

        Scarab(float x, float y) {
            super(x, y, 36, 28, 7, "FARAONE SCARABEO");
            if (scarabSprites == null) scarabSprites = new Sprite[]{scarab(0, false), scarab(1, false), scarab(0, true)};
            gravity = true;
        }

        @Override
        public void wake() {
            super.wake();
            state = 0;
            gravity = false;
            solidTiles = false;
            baseY = (L.floorRow - 6) * T;
        }

        @Override
        void think() {
            float arenaL = L.arenaCol * T + 24, arenaR = L.tw * T - 24 - w;
            switch (state) {
                case 0: { // fly in a wave pattern and drop sand
                    ang += 0.02f + rage() * 0.015f;
                    float tx = arenaL + (arenaR - arenaL) * (0.5f + 0.5f * (float) Math.sin(ang));
                    x += (tx - x) * 0.05f;
                    y += (baseY + (float) Math.sin(ang * 3) * 14 - y) * 0.08f;
                    face();
                    if (--timer <= 0 && Math.abs(p().cx() - cx()) < 60) {
                        L.spawn(new Ents.Shot(cx(), bottom(), 0, 1, Ents.Shot.SAND));
                        if (rage() > 0.5f) {
                            L.spawn(new Ents.Shot(cx(), bottom(), -1.2f, 0.5f, Ents.Shot.SAND));
                            L.spawn(new Ents.Shot(cx(), bottom(), 1.2f, 0.5f, Ents.Shot.SAND));
                        }
                        Sfx.play(Sfx.CANNON);
                        timer = 50;
                        if (++drops >= 4) {
                            state = 1;
                            drops = 0;
                            Sfx.play(Sfx.ROAR);
                        }
                    }
                    break;
                }
                case 1: // dive toward the player
                    face();
                    vx = (p().cx() - cx()) * 0.04f;
                    vx = Math.max(-3, Math.min(3, vx));
                    vy = 4.5f;
                    solidTiles = true;
                    L.move(this, vx, vy);
                    if (onGround) {
                        state = 2;
                        timer = 130 - (int) (rage() * 40);
                        L.shake = 10;
                        Sfx.play(Sfx.THUD);
                        L.spawn(new Ents.Shot(x - 4, bottom() - 6, -2f, 0, Ents.Shot.BALL));
                        L.spawn(new Ents.Shot(right() + 4, bottom() - 6, 2f, 0, Ents.Shot.BALL));
                    }
                    break;
                case 2: // grounded: crawl (vulnerable)
                    face();
                    vx = facing * 0.6f;
                    gravity = true;
                    physics(0.4f, 6);
                    if (--timer <= 0) {
                        state = 3;
                        gravity = false;
                        solidTiles = false;
                    }
                    break;
                case 3: // take off
                    y -= 2;
                    if (y <= baseY) {
                        state = 0;
                        timer = 40;
                    }
                    break;
            }
        }

        @Override
        void onHurt() {
            if (state == 2) timer = Math.min(timer, 20);
        }

        @Override
        Sprite frame() {
            if (state == 2) return scarabSprites[2];
            return scarabSprites[(t / 4) % 2];
        }
    }

    // ============================================================ World 3: yeti
    static Sprite[] yetiSprites;

    static Sprite yeti(int pose) {
        Sprite s = new Sprite(46, 50);
        int fur = 0xFFEAF2FA, sh = 0xFFB8CCE2, face = 0xFF6A8AC8, horn = 0xFF8A7A6A;
        // legs
        box(s, 13, 38, 8, 10, sh);
        box(s, 26, 38, 8, 10, sh);
        box(s, 12, 46, 10, 3, face);
        box(s, 25, 46, 10, 3, face);
        ell(s, 23, 28, 15, 15, fur);          // body
        ell(s, 23, 32, 9, 9, Gfx.shade(fur, 0.95f));
        // arms
        if (pose == 1) {                       // throw: arm raised
            ell(s, 37, 12, 5, 9, fur);
            ell(s, 37, 4, 5, 4, fur);
            ell(s, 37, 2, 4, 3, 0xFFFFFFFF);  // snowball in hand
        } else {
            ell(s, 39, 30, 5, 10, fur);
            box(s, 37, 38, 6, 3, face);
        }
        ell(s, 7, 30, 5, 10, fur);
        box(s, 4, 38, 6, 3, face);
        // head
        ell(s, 19, 13, 11, 10, fur);
        ell(s, 16, 15, 7, 6, face);
        tri(s, 9, 6, 4, -2, 13, 5, horn);
        tri(s, 25, 5, 30, -2, 28, 8, horn);
        box(s, 12, 12, 3, 3, 0xFFFFFFFF);
        box(s, 18, 12, 3, 3, 0xFFFFFFFF);
        s.set(12, 13, 0xFF1A1018);
        s.set(18, 13, 0xFF1A1018);
        for (int i = 0; i < 4; i++) {
            s.set(11 + i, 10 + (i > 1 ? 1 : 0), 0xFF2A3A6A);
            s.set(20 - i, 10 + (i > 1 ? 1 : 0), 0xFF2A3A6A);
        }
        box(s, 12, 18, 9, 2, 0xFF2A1A3A);    // mouth
        s.set(13, 18, 0xFFFFFFFF);
        s.set(19, 18, 0xFFFFFFFF);
        if (pose == 2) box(s, 12, 18, 9, 3, 0xFF2A1A3A);
        return s.outlined(Art.OUT);
    }

    static final class Yeti extends Boss {
        int throwsLeft;

        Yeti(float x, float y) {
            super(x, y, 36, 44, 7, "YETI GLACIALE");
            if (yetiSprites == null) yetiSprites = new Sprite[]{yeti(0), yeti(1), yeti(2)};
        }

        @Override
        void think() {
            switch (state) {
                case 0: // walk toward player
                    face();
                    vx = facing * (0.5f + rage() * 0.5f);
                    if (--timer <= 0) {
                        if ((t / 11) % 2 == 0) {
                            state = 1;
                            throwsLeft = 2 + (int) (rage() * 2);
                            timer = 20;
                        } else {
                            state = 2;
                            vy = -8.5f;
                            vx = facing * 1.2f;
                            Sfx.play(Sfx.ROAR);
                        }
                    }
                    break;
                case 1: // throw snowballs
                    vx = 0;
                    face();
                    if (--timer <= 0) {
                        float dx = p().cx() - cx();
                        float tvx = Math.max(-3.2f, Math.min(3.2f, dx / 45f));
                        L.spawn(new Ents.Shot(cx() + facing * 14, y + 4, tvx, -4.2f, Ents.Shot.SNOW));
                        Sfx.play(Sfx.SPIN);
                        timer = 34;
                        if (--throwsLeft <= 0) {
                            state = 0;
                            timer = 70;
                        }
                    }
                    break;
                case 2: // jump and slam: icicles fall
                    if (onGround && vy >= 0 && t > 2) {
                        state = 0;
                        timer = 60;
                        L.shake = 16;
                        Sfx.play(Sfx.THUD);
                        int n = 3 + (int) (rage() * 3);
                        for (int k = 0; k < n; k++) {
                            float ix = L.camX + 24 + ((t * 53 + k * 71) % (L.game.W - 48));
                            L.spawn(new Ents.Shot(ix, L.camY - 8 - k * 26, 0, 0, Ents.Shot.ICICLE));
                        }
                    }
                    break;
            }
            physics(0.4f, 7);
            if (state == 0 && (hitL || hitR)) facing = -facing;
        }

        @Override
        Sprite frame() {
            if (state == 1 && timer > 20) return yetiSprites[1];
            if (state == 2) return yetiSprites[2];
            return yetiSprites[0];
        }
    }

    // ===================================================== World 4: lord Magmus
    static Sprite[] magSprites;

    static Sprite magmus(boolean cast) {
        Sprite s = new Sprite(40, 54);
        int robe = 0xFF3A1E5A, robeD = 0xFF24123A, gold = 0xFFF2C040, skin = 0xFF8A6A9A;
        // robe (trapezoid)
        for (int y = 18; y < 52; y++) {
            int hw = 6 + (y - 18) / 3;
            for (int x = 20 - hw; x <= 20 + hw; x++) s.set(x, y, x < 20 - hw + 3 ? robeD : robe);
        }
        for (int x = 8; x < 33; x++) s.set(x, 51, gold);
        box(s, 19, 20, 2, 30, gold);
        // flames hair
        int[] fl = {0xFFFFE15A, 0xFFFF9A2A, 0xFFE83A1A};
        for (int i = 0; i < 7; i++) {
            int fx = 12 + i * 3;
            int top = 0 + (i % 3) * 2 + (i == 3 ? -2 : 0);
            tri(s, fx - 2, 12, fx + 1, Math.max(0, top), fx + 4, 12, fl[i % 3]);
        }
        ell(s, 20, 13, 7, 7, skin);           // face
        box(s, 16, 12, 3, 2, 0xFFFFF04A);      // glowing eyes
        box(s, 22, 12, 3, 2, 0xFFFFF04A);
        box(s, 17, 17, 6, 1, 0xFF2A0A1A);
        // collar
        tri(s, 10, 18, 20, 24, 14, 26, gold);
        tri(s, 30, 18, 20, 24, 26, 26, gold);
        // arms / staff
        if (cast) {
            box(s, 4, 18, 6, 4, robe);
            ell(s, 4, 14, 4, 4, 0xFFFF9A2A);
            ell(s, 4, 14, 2, 2, 0xFFFFF0A0);
            box(s, 30, 22, 6, 4, robe);
        } else {
            box(s, 8, 26, 5, 8, robe);
            box(s, 28, 26, 5, 8, robe);
        }
        box(s, 34, 6, 2, 46, 0xFF6A4A2A);      // staff
        ell(s, 35, 5, 3, 3, 0xFFE83A1A);
        return s.outlined(Art.OUT);
    }

    static final class Magmus extends Boss {
        int casts;
        float[][] spots;
        int spot, fade = 255;

        Magmus(float x, float y) {
            super(x, y, 26, 46, 9, "LORD MAGMUS");
            if (magSprites == null) magSprites = new Sprite[]{magmus(false), magmus(true)};
            gravity = false;
            solidTiles = false;
        }

        @Override
        public void wake() {
            super.wake();
            float a = L.arenaCol * T;
            float floor = L.floorRow * T;
            spots = new float[][]{{a + 4 * T, floor - h}, {a + 26 * T, floor - h}, {a + 15 * T, floor - h},
                    {a + 7 * T, floor - h - 3 * T}, {a + 24 * T, floor - h - 3 * T}};
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
                case 0: // hover & cast
                    y += (float) Math.sin(t * 0.08) * 0.3f;
                    if (--timer <= 0) {
                        Sfx.play(Sfx.CANNON);
                        float dx = p().cx() - cx(), dy = p().cy() - (y + 12);
                        float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.01f;
                        int n = rage() > 0.5f ? 5 : 3;
                        for (int k = 0; k < n; k++) {
                            double a = Math.atan2(dy, dx) + (k - (n - 1) / 2.0) * 0.28;
                            float sp = 1.8f + rage() * 0.8f;
                            L.spawn(new Ents.Shot(cx() + facing * 10, y + 12, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, Ents.Shot.FIRE));
                        }
                        timer = 55 - (int) (rage() * 15);
                        if (++casts >= 2 + (int) (rage() * 2)) {
                            state = 1;
                            casts = 0;
                        }
                    }
                    break;
                case 1: // fade out
                    fade -= 12;
                    if (fade <= 0) {
                        fade = 0;
                        int ns = (spot + 1 + (t % 3)) % spots.length;
                        if (ns == spot) ns = (ns + 1) % spots.length;
                        spot = ns;
                        x = spots[spot][0];
                        y = spots[spot][1];
                        state = 2;
                        if (rage() > 0.3f) // fire rain while teleporting
                            for (int k = 0; k < 3; k++)
                                L.spawn(new Ents.Shot(L.camX + 30 + ((t * 41 + k * 113) % (L.game.W - 60)), L.camY - 10 - k * 20, 0, 1.8f, Ents.Shot.FIRE));
                    }
                    break;
                case 2: // fade in, stay vulnerable a moment
                    fade += 10;
                    if (fade >= 255) {
                        fade = 255;
                        state = 3;
                        timer = 70 - (int) (rage() * 25);
                    }
                    break;
                case 3: // vulnerable pause then cast
                    if (--timer <= 0) {
                        state = 0;
                        timer = 20;
                    }
                    break;
            }
        }

        @Override
        void onHurt() {
            state = 1;
        }

        @Override
        Sprite frame() {
            return magSprites[state == 0 && timer < 15 ? 1 : 0];
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s = frame();
            int px = Math.round(cx() - s.w / 2f) - cx, py = Math.round(bottom()) - s.h - cy + 2;
            int flash = (inv > 0 && (inv / 3) % 2 == 0) || (defeated && (deathT / 3) % 2 == 0) ? 0xFFFFFFFF : 0;
            g.circle((int) cx() - cx, (int) cy() - cy, 26, 0xFFFF6A2A, fade / 8);
            g.blit(s, px, py, facing > 0, fade, 0);
            if (flash != 0) g.blit(s, px, py, facing > 0, Math.min(150, fade), flash);
        }
    }
}
