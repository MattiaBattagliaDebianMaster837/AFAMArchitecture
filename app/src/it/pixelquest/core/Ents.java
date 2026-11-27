package it.pixelquest.core;

/** Enemies, items, platforms, projectiles and particles. */
public final class Ents {
    static final int T = Level.T;

    // ===================================================================== particles
    public abstract static class Particle extends Entity {
        int life;

        Particle(float x, float y, int life) {
            this.x = x;
            this.y = y;
            this.life = life;
            solidTiles = false;
            alwaysActive = true;
        }

        @Override
        public void update() {
            if (t >= life) dead = true;
        }
    }

    public static final class Debris extends Particle {
        final int col;

        Debris(float x, float y, float vx, float vy, Sprite brick) {
            super(x, y, 70);
            this.vx = vx;
            this.vy = vy;
            this.col = brick.get(4, 5);
        }

        @Override
        public void update() {
            super.update();
            vy += 0.3f;
            x += vx;
            y += vy;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int px = (int) x - cx, py = (int) y - cy;
            g.fill(px, py, 6, 6, Art.OUT);
            g.fill(px + 1, py + 1, 4, 4, col);
            g.pset(px + 1, py + 1, Gfx.shade(col, 1.3f));
        }
    }

    public static final class Dust extends Particle {
        Dust(float x, float y) {
            super(x - 5, y - 8, 15);
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int f = Math.min(2, t / 5);
            g.blit(Art.puff[f], (int) x - cx - f, (int) y - cy - f, false, 200 - t * 10, 0);
        }
    }

    public static final class Puff extends Particle {
        Puff(float cx, float cy) {
            super(cx - 8, cy - 8, 18);
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int f = Math.min(2, t / 6);
            g.blit(Art.puff[f], (int) x - cx - f * 2, (int) y - cy - f * 2);
        }
    }

    public static final class Sparkle extends Particle {
        Sparkle(float cx, float cy) {
            super(cx - 2, cy - 2, 16);
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.blit(Art.sparkle[(t / 4) % 2], (int) x - cx, (int) y - cy - t / 3);
        }
    }

    public static final class Impact extends Particle {
        Impact(float cx, float cy) {
            super(cx, cy, 10);
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int r = 3 + t;
            int px = (int) x - cx, py = (int) y - cy;
            g.ring(px, py, r, 2, 0xFFFFFFFF, 255 - t * 22);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + Math.PI / 4;
                g.fill(px + (int) (StrictMath.cos(a) * (r + 2)) - 1, py + (int) (StrictMath.sin(a) * (r + 2)) - 1, 2, 2, 0xFFFFE040);
            }
        }
    }

    public static final class Flash extends Particle {
        Flash(float cx, float cy) {
            super(cx, cy, 5);
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.circle((int) x - cx, (int) y - cy, 4 - t / 2, 0xFFFFF0A0, 230);
        }
    }

    public static final class FloatText extends Particle {
        final String s;
        final int col;

        FloatText(float cx, float y, String s, int col) {
            super(cx, y, 60);
            this.s = s;
            this.col = col;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.textC(s, (int) x - cx, (int) y - cy - t / 2, col, 0xFF000000, 1);
        }
    }

    public static final class CoinPop extends Particle {
        CoinPop(float x, float y) {
            super(x, y, 24);
            vy = -5;
        }

        @Override
        public void update() {
            super.update();
            vy += 0.4f;
            y += vy;
            if (dead) L.spawn(new Sparkle(x + 5, y + 7));
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.blit(Art.coin[(t / 3) % 4], (int) x - cx, (int) y - cy);
        }
    }

    // ===================================================================== items
    public static final class Item extends Entity {
        public static final int HEART = 0, STAR = 1, LIFE = 2, FIRE = 3, ICE = 4;
        final int kind;
        int rise = 16;

        Item(float tx, float ty, int kind) {
            this.kind = kind;
            x = tx + 2;
            y = ty;
            w = 12;
            h = 12;
            facing = 1;
            alwaysActive = true;
        }

        @Override
        public void update() {
            if (rise > 0) {
                rise--;
                y -= 1;
                // once out of the block it walks off it (towards where the hero is heading), like the hearts
                if (rise == 0 && L.player != null) facing = L.player.facing;
                return;
            }
            float sp = kind == STAR ? 1.3f : kind == LIFE ? 1.1f : 0.7f;
            vx = facing * sp;
            physics(0.3f, 5);
            if (hitL || hitR) facing = -facing;
            if (kind == STAR && onGround) vy = -4.2f;
            if (y > L.th * T + 20) dead = true;
            // story: the hero; battle: whoever grabs it first
            Player taker = L.battle != null ? L.battle.touching(this, null, null)
                    : (L.player.overlaps(this) && !L.player.dying ? L.player : null);
            if (taker != null) {
                dead = true;
                Player p = taker;
                if (L.battle != null && kind == HEART) { // battle: heals 30% of damage
                    p.dmg = Math.max(0, p.dmg - 30);
                    Sfx.play(Sfx.POWER);
                    L.spawn(new FloatText(cx(), y, "-30%", 0xFFFF8A9A));
                } else if (L.battle != null && kind == LIFE) { // battle: the potion fills the special bar
                    p.energy = Player.ENERGY_MAX;
                    Sfx.play(Sfx.LIFE);
                    L.spawn(new FloatText(cx(), y, "SPECIALE PRONTA!", 0xFFFFE070));
                } else if (kind == HEART) {
                    if (p.hp < Player.MAX_HP) p.heal();
                    else L.addCoin(5);
                    Sfx.play(Sfx.POWER);
                    L.spawn(new FloatText(cx(), y, "+1 \u0001", 0xFFFF8A9A));
                } else if (kind == STAR) {
                    p.star();
                    Sfx.play(Sfx.POWER);
                } else if (kind == FIRE || kind == ICE) {
                    p.setPower(kind == FIRE ? Combat.FIRE : Combat.ICE);
                    Sfx.play(Sfx.POWER);
                    L.spawn(new FloatText(cx(), y, kind == FIRE ? "FIORE DI FUOCO!" : "FIORE DI GHIACCIO!", kind == FIRE ? 0xFFFFA060 : 0xFFA0E8FF));
                } else {
                    L.game.save.lives++;
                    Sfx.play(Sfx.LIFE);
                    L.spawn(new FloatText(cx(), y, "+1 VITA", 0xFF7CFF7C));
                }
                for (int k = 0; k < 4; k++) L.spawn(new Sparkle(x + k * 4, y + (k % 2) * 6));
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s = kind == HEART ? Art.heart : kind == STAR ? Art.star[(t / 4) % 2] : kind == FIRE ? Art.fireFlower
                    : kind == ICE ? Art.iceFlower : Art.potion;
            drawSprite(g, s, cx, cy, false);
        }
    }

    public static final class Gem extends Entity {
        final int idx;
        final boolean had;

        Gem(float x, float y, int idx, boolean had) {
            this.x = x;
            this.y = y;
            this.idx = idx;
            this.had = had;
            w = 12;
            h = 13;
        }

        @Override
        public void update() {
            if (L.player.overlaps(this) && !L.player.dying) {
                dead = true;
                L.gemTaken(idx);
                L.spawn(new FloatText(cx(), y, "GEMMA " + (idx + 1) + "/3", 0xFF8AD0FF));
                for (int k = 0; k < 6; k++) L.spawn(new Sparkle(x + (k * 5) % 14, y + (k * 7) % 14));
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int bob = (int) Math.round(StrictMath.sin(L.frame * 0.08) * 2);
            g.blit(Art.gem[(L.frame / 8) % 4], (int) x - cx - 1, (int) y - cy + bob - 1, false, had ? 110 : 255, 0);
            if (!had && L.frame % 30 < 8) g.blit(Art.sparkle[0], (int) x - cx + 9, (int) y - cy - 2 + bob);
        }
    }

    public static final class Checkpoint extends Entity {
        boolean on;

        Checkpoint(float tx, float ty) {
            x = tx + 2;
            y = ty + T - 32;
            w = 12;
            h = 32;
        }

        @Override
        public void update() {
            if (!on && L.player.overlaps(this)) {
                on = true;
                L.checkpointTaken(this);
                L.spawn(new FloatText(cx(), y - 4, "CHECKPOINT!", 0xFF7CFF7C));
                for (int k = 0; k < 6; k++) L.spawn(new Sparkle(x + k * 2, y + k * 3));
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.blit(on ? Art.ckOn : Art.ckOff, (int) x - cx - 1, (int) y - cy - 1);
        }
    }

    public static final class Goal extends Entity {
        float flagY;
        boolean done;

        Goal(float tx, float ty) {
            x = tx + 4;
            w = 8;
            h = T * 6;
            y = ty + T - h;
            flagY = 4;
        }

        @Override
        public void update() {
            if (!done && L.player.right() > x - 2 && L.player.x < x + w + 2 && L.player.y < y + h && !L.player.dying) {
                done = true;
                L.clear();
                for (int k = 0; k < 10; k++) L.spawn(new Sparkle(x + (k * 7) % 16 - 4, y + k * 9));
            }
            if (done && flagY < h - 20) flagY += 2;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int px = (int) x - cx, py = (int) y - cy;
            g.fill(px + 2, py, 4, h, Art.OUT);
            g.fill(px + 3, py, 2, h, 0xFFE8E8F0);
            g.fill(px + 3, py, 1, h, 0xFFFFFFFF);
            g.circle(px + 4, py - 3, 4, Art.OUT, 255);
            g.circle(px + 4, py - 3, 3, 0xFFFFD040, 255);
            g.pset(px + 3, py - 5, 0xFFFFFFFF);
            g.blit((L.frame / 10) % 2 == 0 ? Art.flagGoal0 : Art.flagGoal1, px + 6, py + (int) flagY);
            g.fill(px - 4, py + h - 4, 16, 4, 0xFF5A5A6A);
            g.fill(px - 4, py + h - 4, 16, 1, 0xFF8A8A9A);
        }
    }

    public static final class Trophy extends Entity {
        Trophy(float x, float y) {
            this.x = x;
            this.y = y;
            w = 14;
            h = 14;
            alwaysActive = true;
        }

        @Override
        public void update() {
            physics(0.15f, 2.5f);
            if (t % 6 == 0) L.spawn(new Sparkle(x + (t * 3) % 14, y + (t * 5) % 14));
            if (L.player.overlaps(this) && !L.player.dying) {
                dead = true;
                L.trophyTaken();
                Sfx.play(Sfx.POWER);
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int px = (int) cx() - cx, py = (int) cy() - cy;
            g.circle(px, py, 12 + (t / 4) % 3, 0xFFFFF0A0, 70);
            drawSprite(g, Art.trophy, cx, cy, false);
        }
    }

    public static final class Sign extends Entity {
        final int idx;
        static Sprite spr;

        Sign(float tx, float ty, int idx) {
            this.idx = idx;
            x = tx;
            y = ty;
            w = 16;
            h = 16;
            if (spr == null) spr = Art.raw(new String[]{
                    "wwwwwwwwwwwwww",
                    "wWWWWWWWWWWWWw",
                    "wWkkWkkkWkkWWw",
                    "wWWWWWWWWWWWWw",
                    "wWkkkWkkWkkkWw",
                    "wWWWWWWWWWWWWw",
                    "wwwwwwwwwwwwww",
                    "......ww......",
                    "......ww......",
                    "......ww......",
                    "......ww......",
                    "......ww......",
            }, 'w', 0xFF8A5A30, 'W', 0xFFD8A868, 'k', 0xFF5A3A20).outlined(Art.OUT);
        }

        @Override
        public void update() {
            Player p = L.player;
            if (Math.abs(p.cx() - cx()) < 40 && Math.abs(p.cy() - cy()) < 60 && idx < L.def.hints.length) {
                L.hint = L.game.hintText(L.def.hints[idx]);
                L.hintT = Math.max(L.hintT, 12);
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.blit(spr, (int) x - cx, (int) y - cy + 2);
            if (Math.abs(L.player.cx() - cx()) < 40 && (L.frame / 15) % 2 == 0) g.text("!", (int) x - cx + 6, (int) y - cy - 9, 0xFFFFE070);
        }
    }

    public static final class Spring extends Entity {
        int squash;

        Spring(float tx, float ty) {
            x = tx;
            y = ty;
            w = 16;
            h = 16;
        }

        @Override
        public void update() {
            if (squash > 0) squash--;
            Player p = L.player;
            if (p.vy > 0 && p.x < right() - 2 && p.right() > x + 2 && p.bottom() >= y + 4 && p.bottom() <= y + 14) {
                p.y = y + 4 - p.h;
                p.vy = (L.input.a || L.input.b) ? -9.0f : -8.0f;
                p.onGround = false;
                p.spinning = false;
                squash = 10;
                Sfx.play(Sfx.SPRING);
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.blit(squash > 0 ? Art.spring1 : Art.spring0, (int) x - cx - 1, (int) y - cy - 1);
        }
    }

    // ===================================================================== platforms
    public static final class Platform extends Entity {
        final int type, range;
        final float x0, y0;
        int stepT = -1;
        float phase;
        static Sprite[] spr;

        Platform(float tx, float ty, int type, int range) {
            this.type = type;
            this.range = range;
            x = x0 = tx;
            y = y0 = ty + 4;
            w = 48;
            h = 8;
            platform = true;
            solidTiles = false;
            alwaysActive = true;
            if (spr == null) spr = new Sprite[]{make(0xFFC08A50, 0xFF8A5A30), make(0xFFE07A5A, 0xFFA04A30)};
        }

        static Sprite make(int c, int d) {
            Sprite s = new Sprite(48, 8);
            for (int yy = 0; yy < 8; yy++)
                for (int xx = 0; xx < 48; xx++) {
                    int col = yy == 0 ? Gfx.shade(c, 1.2f) : yy >= 6 ? d : c;
                    if (xx % 16 == 15) col = d;
                    if ((xx % 16 == 3 || xx % 16 == 12) && yy == 3) col = 0xFF3A3A44;
                    s.set(xx, yy, col);
                }
            return s.outlined(Art.OUT);
        }

        void stepped() {
            if (type == 3 && stepT < 0) stepT = 0;
        }

        @Override
        public void update() {
            double ph = t * 2 * Math.PI / 240.0 + phase;
            if (type == 1) x = x0 + (float) StrictMath.sin(ph) * range;
            else if (type == 2) y = y0 + (float) StrictMath.sin(ph) * range;
            else if (type == 3 && stepT >= 0) {
                stepT++;
                if (stepT > 28) {
                    vy = Math.min(vy + 0.25f, 5);
                    y += vy;
                    if (y > L.th * T + 40) dead = true;
                }
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int sx = (type == 3 && stepT > 0 && stepT <= 28) ? ((stepT / 2) % 2) * 2 - 1 : 0;
            g.blit(spr[type == 3 ? 1 : 0], (int) x - cx - 1 + sx, (int) y - cy - 1);
        }
    }

    // ===================================================================== projectiles
    public static final class Bullet extends Entity {
        Bullet(float x, float y, int dir) {
            this.x = x;
            this.y = y;
            w = 7;
            h = 3;
            vx = 6.5f * dir;
            facing = dir;
            solidTiles = false;
            alwaysActive = true;
        }

        @Override
        public void update() {
            x += vx;
            if (t > 55) {
                dead = true;
                return;
            }
            int c = (int) Math.floor((vx > 0 ? right() : x) / T), r = (int) Math.floor(cy() / T);
            byte tl = L.tile(c, r);
            if (Level.solid(tl)) {
                dead = true;
                if (tl == Level.BRICK || tl == Level.QBLOCK) L.hitBlock(c, r, false);
                L.spawn(new Impact(vx > 0 ? c * T : c * T + T, cy()));
                return;
            }
            for (Entity e : L.ents) {
                if (e.dead || e == this || !overlaps(e)) continue;
                if (e.boss) {
                    ((Bosses.Boss) e).hit(2, cx());
                    dead = true;
                    L.spawn(new Impact(cx(), cy()));
                    Sfx.play(Sfx.HIT);
                    return;
                }
                if ((e.enemy || e.projectile) && e.punchable) {
                    e.kill(BULLET);
                    dead = true;
                    L.spawn(new Impact(cx(), cy()));
                    return;
                }
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.blit(Art.bulletP, (int) x - cx, (int) y - cy, facing < 0);
        }
    }

    /** Enemy projectile. */
    public static final class Shot extends Entity {
        public static final int BALL = 0, SAND = 1, SNOW = 2, ICICLE = 3, FIRE = 4, ROCK = 5, SPINE = 6;
        final int kind;
        int bounces;

        public Shot(float cx, float cy, float vx, float vy, int kind) {
            this.kind = kind;
            Sprite s = sprite();
            w = Math.max(3, s.w - 4);
            h = Math.max(3, s.h - 4);
            x = cx - w / 2f;
            y = cy - h / 2f;
            this.vx = vx;
            this.vy = vy;
            enemy = true;
            projectile = true;
            solidTiles = false;
            alwaysActive = true;
            stompable = kind == BALL || kind == SNOW || kind == ROCK;
            spinKill = kind != FIRE;
            punchable = true;
        }

        Sprite sprite() {
            switch (kind) {
                case SAND: return Art.sandBall;
                case SNOW: return Art.snowBall;
                case ICICLE: return Art.icicle;
                case FIRE: return Art.fireball[0];
                case ROCK: return Art.rock;
                case SPINE: return Art.spine;
                default: return Art.cannonBall;
            }
        }

        @Override
        public void update() {
            if (kind == SAND || kind == SNOW || kind == ICICLE || kind == ROCK || kind == SPINE) vy = Math.min(vy + (kind == ICICLE || kind == ROCK ? 0.3f : kind == SPINE ? 0.2f : 0.18f), 6);
            x += vx;
            y += vy;
            if (L.solidAt(cx(), cy() + h / 2f) || L.solidAt(cx() + Math.signum(vx) * w / 2f, cy())) {
                if (kind == SNOW && bounces == 0 && vy > 0) {
                    bounces++;
                    vy = -3;
                    y -= 2;
                } else if (kind != BALL || t > 4) {
                    dead = true;
                    L.spawn(new Puff(cx(), cy()));
                }
            }
            if (t > 600 || y > L.th * T + 32 || x < L.camX - 120 || x > L.camX + L.game.W + 120) dead = true;
        }

        @Override
        public void kill(int how) {
            dead = true;
            L.spawn(new Puff(cx(), cy()));
            if (how == PUNCH) Sfx.play(Sfx.STOMP);
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s = kind == FIRE ? Art.fireball[(t / 4) % 2] : sprite();
            g.blit(s, Math.round(cx() - s.w / 2f) - cx, Math.round(cy() - s.h / 2f) - cy, vx < 0);
        }
    }

    // ===================================================================== enemies
    public abstract static class Enemy extends Entity {
        int ko, koT;
        /** hit points (most enemies die in one hit) */
        public int hp = 1;
        /** frames left frozen by an ice attack */
        public int frozen;
        int hurtFlash;
        /** energy given to the player when defeated */
        int energy = 12;

        Enemy() {
            enemy = true;
        }

        boolean canSquash() {
            return false;
        }

        /** Speed multiplier: later worlds are faster. */
        float speedK() {
            return 1f + Math.min(L.def.world, 5) * 0.12f;
        }

        @Override
        public void kill(int how) {
            if (ko != 0 || dead) return;
            enemy = false;
            frozen = 0;
            L.onKill(this);
            if (how == STOMP && canSquash()) {
                ko = 1;
                vx = 0;
            } else if (how == SPIN) {
                dead = true;
            } else {
                ko = 2;
                vy = -4.5f;
                vx = L.player.cx() < cx() ? 1.4f : -1.4f;
                solidTiles = false;
                if (how != STOMP) Sfx.play(Sfx.KICK);
            }
        }

        /** Damage from a hero attack. Ice freezes, a frozen enemy shatters on any hit. */
        public boolean damage(int dmg, int how, int element) {
            if (ko != 0 || dead) return false;
            if (frozen > 0) {
                shatter();
                return true;
            }
            if (element == Combat.ICE) {
                frozen = 300;
                vx = 0;
                Sfx.play(Sfx.BUMP);
                for (int k = 0; k < 3; k++) L.spawn(new Combat.Ember(x + k * 4, y + 2, 0xFFDFF8FF));
                return true;
            }
            if (element == Combat.FIRE) dmg += 1;
            hp -= dmg;
            if (hp <= 0) kill(how);
            else {
                hurtFlash = 12;
                Sfx.play(Sfx.HIT);
                vx = L.player.cx() < cx() ? 1.5f : -1.5f;
            }
            return true;
        }

        public void shatter() {
            if (ko != 0 || dead) return;
            frozen = 0;
            for (int k = 0; k < 6; k++) L.spawn(new Combat.Shard(cx(), cy(), (k - 2.5f) * 0.8f, -2.5f - (k % 3)));
            Sfx.play(Sfx.BREAK);
            enemy = false;
            L.onKill(this);
            dead = true;
        }

        /** Regular stomp. Tough enemies only lose one hit point. */
        public void stomped() {
            if (hp > 1) damage(1, STOMP, Combat.NONE);
            else kill(STOMP);
        }

        public void spun() {
            if (hp > 2) damage(2, SPIN, Combat.NONE);
            else kill(SPIN);
        }

        @Override
        public final void update() {
            if (ko == 1) {
                if (++koT > 24) {
                    dead = true;
                    L.spawn(new Puff(cx(), cy()));
                }
                return;
            }
            if (ko == 2) {
                vy += 0.3f;
                x += vx;
                y += vy;
                if (y > L.camY + L.game.H + 40) dead = true;
                return;
            }
            if (hurtFlash > 0) hurtFlash--;
            if (frozen > 0) {
                frozen--;
                vx = 0;
                if (gravity && solidTiles) physics(0.4f, 6);
                if (frozen == 0) Sfx.play(Sfx.BUMP);
                return;
            }
            act();
            if (y > L.th * T + 40) dead = true;
        }

        abstract void act();

        abstract Sprite frame();

        boolean spriteFacesRight() {
            return false;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            Sprite s = frame();
            boolean flip = spriteFacesRight() ? facing < 0 : facing > 0;
            if (ko == 2) {
                g.blitEx(s, cx() - cx, cy() - cy, 1, -1, 0, flip, 255, 0);
                return;
            }
            int sx = Math.round(cx() - s.w / 2f) - cx, sy = Math.round(bottom()) - s.h - cy;
            if (frozen > 0) {
                g.blit(s, sx, sy, flip, 255, 0);
                g.blit(s, sx, sy, flip, 150, 0xFF9AE4FF);
                int bw = s.w + 2, bh = s.h + 2;
                g.fillA(sx - 1, sy - 1, bw, bh, 0xFFBFF0FF, 70);
                g.rect(sx - 1, sy - 1, bw, bh, frozen < 60 && (frozen / 4) % 2 == 0 ? 0xFF5AB8F0 : 0xFFFFFFFF);
                return;
            }
            g.blit(s, sx, sy, flip, 255, 0);
            if (hurtFlash > 0 && (hurtFlash / 2) % 2 == 0) g.blit(s, sx, sy, flip, 170, 0xFFFFFFFF);
        }

        void walk(float speed, boolean edgeTurn) {
            vx = facing * speed * speedK();
            physics(0.4f, 6);
            if ((facing < 0 && hitL) || (facing > 0 && hitR)) facing = -facing;
            else if (edgeTurn && onGround) {
                float fx = facing > 0 ? right() + 1 : x - 1;
                if (!L.solidAt(fx, bottom() + 2) && L.tile((int) Math.floor(fx / T), (int) Math.floor((bottom() + 2) / T)) != Level.SEMI)
                    facing = -facing;
            }
        }
    }

    /** Stone golem: slow but needs three hits (a stomp only takes one). */
    public static final class Golem extends Enemy {
        Golem(float tx, float ty) {
            x = tx;
            y = ty;
            w = 14;
            h = 16;
            hp = 3;
            energy = 25;
        }

        @Override
        void act() {
            walk(0.32f, true);
        }

        @Override
        Sprite frame() {
            return Art.golem[(t / 14) % 2];
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            super.render(g, cx, cy);
            if (ko == 0 && frozen == 0 && hp < 3)
                for (int i = 0; i < hp; i++) g.fill((int) x - cx + 2 + i * 4, (int) y - cy - 6, 3, 2, 0xFFE83A3A);
        }
    }

    /** Spiky cactus: stands still and fires spines in an arc toward the player. */
    public static final class Cactus extends Enemy {
        int cool;

        Cactus(float tx, float ty) {
            x = tx + 3;
            y = ty + 4;
            w = 10;
            h = 12;
            hp = 2;
            energy = 15;
            stompable = false;
            cool = 60 + (int) (tx / 5) % 60;
        }

        @Override
        void act() {
            physics(0.4f, 6);
            Player p = L.player;
            facing = p.cx() < cx() ? -1 : 1;
            float d = Math.abs(p.cx() - cx());
            if (--cool <= 0 && d < 230 && d > 20 && !p.dying) {
                cool = (int) (150 / speedK());
                for (int k = -1; k <= 1; k++) {
                    float sp = 1.6f + k * 0.5f;
                    L.spawn(new Shot(cx() + facing * 4, y + 3, facing * sp, -3.4f + k * 0.4f, Shot.SPINE));
                }
                Sfx.play(Sfx.SPIN);
            }
        }

        @Override
        Sprite frame() {
            return Art.cactus[cool < 20 ? 1 : 0];
        }

        @Override
        boolean spriteFacesRight() {
            return true;
        }
    }

    /** Desert / lava lizard: dashes at the player when it sees him. */
    public static final class Lizard extends Enemy {
        int dash, rest = 30;

        Lizard(float tx, float ty) {
            x = tx + 1;
            y = ty + 10;
            w = 14;
            h = 6;
            energy = 12;
        }

        @Override
        boolean canSquash() {
            return true;
        }

        @Override
        void act() {
            Player p = L.player;
            if (dash > 0) {
                dash--;
                walk(2.5f, false);
                if (t % 4 == 0) L.spawn(new Dust(cx() - facing * 6, bottom()));
            } else {
                walk(0, false);
                if (--rest <= 0 && Math.abs(p.cx() - cx()) < 170 && Math.abs(p.cy() - cy()) < 50 && !p.dying) {
                    facing = p.cx() < cx() ? -1 : 1;
                    dash = 50;
                    rest = 70;
                }
            }
        }

        @Override
        Sprite frame() {
            return Art.lizard[dash > 0 ? (t / 3) % 2 : 0];
        }

        @Override
        boolean spriteFacesRight() {
            return true;
        }
    }

    public static final class Slime extends Enemy {
        final int world;

        public Slime(float tx, float ty, int world) {
            this.world = world;
            x = tx + 2;
            y = ty + 6;
            w = 12;
            h = 10;
        }

        @Override
        boolean canSquash() {
            return true;
        }

        @Override
        void act() {
            walk(0.5f, true);
        }

        @Override
        Sprite frame() {
            if (ko == 1) return Art.slime[world][2];
            return Art.slime[world][(t / 12) % 2];
        }
    }

    public static final class Hedgehog extends Enemy {
        final int world;

        Hedgehog(float tx, float ty, int world) {
            this.world = world;
            x = tx + 2;
            y = ty + 6;
            w = 12;
            h = 10;
            stompable = false;
        }

        @Override
        void act() {
            walk(0.45f, true);
        }

        @Override
        boolean spriteFacesRight() {
            return true;
        }

        @Override
        Sprite frame() {
            return Art.hedgehog[world][(t / 10) % 2];
        }
    }

    public static final class Bat extends Enemy {
        boolean awake;
        float baseY;

        Bat(float tx, float ty) {
            x = tx + 2;
            y = ty + 2;
            w = 12;
            h = 8;
            gravity = false;
            solidTiles = false;
            baseY = y;
        }

        @Override
        void act() {
            Player p = L.player;
            if (!awake) {
                if (Math.abs(p.cx() - cx()) < 120) {
                    awake = true;
                    facing = p.cx() < cx() ? -1 : 1;
                }
                return;
            }
            vx = facing * 1.0f;
            float ty = p.cy() - 6;
            baseY += Math.signum(ty - baseY) * 0.35f;
            x += vx;
            y = baseY + (float) StrictMath.sin(t * 0.09) * 12;
        }

        @Override
        Sprite frame() {
            return awake ? Art.bat[(t / 6) % 2] : Art.bat[1];
        }
    }

    public static final class Frog extends Enemy {
        int wait = 50;

        Frog(float tx, float ty) {
            x = tx + 2;
            y = ty + 6;
            w = 12;
            h = 10;
        }

        @Override
        boolean canSquash() {
            return true;
        }

        @Override
        void act() {
            if (onGround) {
                vx = 0;
                if (--wait <= 0) {
                    facing = L.player.cx() < cx() ? -1 : 1;
                    vy = -5.2f;
                    vx = facing * 1.4f;
                    onGround = false;
                    wait = 70 + (int) (Math.abs(x) % 40);
                }
            }
            float ovx = vx;
            physics(0.3f, 6);
            if (!onGround && (hitL || hitR)) vx = -ovx;
        }

        @Override
        Sprite frame() {
            return onGround ? Art.frog[0] : Art.frog[1];
        }
    }

    public static final class Penguin extends Enemy {
        Penguin(float tx, float ty) {
            x = tx + 2;
            y = ty + 2;
            w = 12;
            h = 14;
        }

        @Override
        void act() {
            walk(1.4f, false);
        }

        @Override
        Sprite frame() {
            return Art.penguin[(t / 8) % 2];
        }

        @Override
        boolean spriteFacesRight() {
            return true;
        }
    }

    public static final class FireSpirit extends Enemy {
        final float homeY;
        int wait;

        FireSpirit(float tx, float ty) {
            x = tx + 2;
            y = homeY = ty + 4;
            w = 10;
            h = 10;
            gravity = false;
            solidTiles = false;
            stompable = false;
            spinKill = false;
            wait = (int) (tx / 7) % 90;
        }

        @Override
        void act() {
            if (y >= homeY && vy >= 0) {
                y = homeY;
                vy = 0;
                if (--wait <= 0) {
                    vy = -7.2f;
                    wait = 110;
                }
                return;
            }
            vy += 0.2f;
            y += vy;
            if (t % 5 == 0) L.spawn(new Sparkle(cx(), bottom()));
        }

        @Override
        Sprite frame() {
            return Art.fire[(t / 5) % 2];
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            if (y >= homeY && ko == 0) return;
            Sprite s = frame();
            g.blitEx(s, cx() - cx, cy() - cy, 1, vy > 0 ? -1 : 1, 0, false, 255, 0);
        }
    }

    public static final class Cannon extends Entity {
        int cool;

        Cannon(float tx, float ty) {
            x = tx;
            y = ty;
            w = 16;
            h = 16;
            solidTiles = false;
            gravity = false;
            cool = 90;
        }

        @Override
        public void update() {
            Player p = L.player;
            float d = p.cx() - cx();
            if (--cool <= 0 && Math.abs(d) < 270 && Math.abs(d) > 24 && !p.dying) {
                int dir = d < 0 ? -1 : 1;
                L.spawn(new Shot(cx() + dir * 12, cy(), dir * 1.7f, 0, Shot.BALL));
                L.spawn(new Puff(cx() + dir * 10, cy()));
                Sfx.play(Sfx.CANNON);
                cool = 160;
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
        }
    }

    public static final class Crusher extends Entity {
        final float homeY;
        int state, timer;

        Crusher(float tx, float ty) {
            x = tx - 6;
            y = homeY = ty;
            w = 28;
            h = 26;
            platform = true;
            enemy = true;
            stompable = false;
            spinKill = false;
            punchable = false;
            alwaysActive = true;
        }

        @Override
        public void update() {
            Player p = L.player;
            switch (state) {
                case 0:
                    if (Math.abs(p.cx() - cx()) < 30 && p.y > y && Math.abs(p.cx() - cx()) < L.game.W) {
                        state = 1;
                        timer = 12;
                    }
                    break;
                case 1:
                    if (--timer <= 0) state = 2;
                    break;
                case 2:
                    vy = Math.min(vy + 0.6f, 8);
                    L.move(this, 0, vy);
                    if (onGround) {
                        state = 3;
                        timer = 50;
                        vy = 0;
                        if (Math.abs(cx() - (L.camX + L.game.W / 2f)) < L.game.W) {
                            L.shake = 8;
                            Sfx.play(Sfx.THUD);
                        }
                    }
                    if (y > L.th * T) dead = true;
                    break;
                case 3:
                    if (--timer <= 0) state = 4;
                    break;
                case 4:
                    y -= 1;
                    if (y <= homeY) {
                        y = homeY;
                        state = 0;
                    }
                    break;
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int sx = state == 1 ? ((timer / 2) % 2) * 2 - 1 : 0;
            Sprite s = Art.crusher[state == 2 || state == 3 ? 1 : 0];
            g.blit(s, (int) x - cx - 1 + sx, (int) y - cy - 1);
        }
    }
}
