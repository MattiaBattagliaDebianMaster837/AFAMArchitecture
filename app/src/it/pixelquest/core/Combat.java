package it.pixelquest.core;

import java.util.ArrayList;

/**
 * Player-side combat: damage rules, hero projectiles (bullets, bouncing coins, soccer balls, fire/ice orbs,
 * reflected shots, batted enemies) and the entities used by special moves (henchmen, fire totem, super punch).
 */
public final class Combat {
    public static final int NONE = 0, FIRE = 1, ICE = 2;
    static final int T = Level.T;

    /**
     * Applies player damage to an entity. Returns true when the entity "absorbed" the hit (the projectile stops
     * or loses a pierce), false when it should pass through.
     */
    public static boolean hit(Level L, Entity e, int dmg, int bossDmg, int element, int how, boolean force, float fromX) {
        if (e.dead) return false;
        if (e.boss) {
            Bosses.Boss b = (Bosses.Boss) e;
            if (b.defeated || !b.awake) return false;
            b.hit(bossDmg + (element == FIRE ? 1 : 0), fromX, force);
            if (element == ICE) b.freeze(45);
            return true;
        }
        if (e instanceof Ents.Enemy) return ((Ents.Enemy) e).damage(dmg, how, element);
        if (e.projectile) {
            e.kill(how);
            return true;
        }
        if (e.enemy && e.punchable) {
            e.kill(how);
            return true;
        }
        return false;
    }

    /** Things a hero projectile can interact with. */
    static boolean target(Entity e) {
        return !e.dead && (e.enemy || e.projectile || e.boss || (e instanceof Ents.Enemy && ((Ents.Enemy) e).frozen > 0));
    }

    // ============================================================ projectiles
    public static final class PShot extends Entity {
        public static final int BULLET = 0, COIN = 1, BALL = 2, ORB = 3, HENCH = 4, FOUNTAIN = 5, GIANT = 6, REFLECT = 7,
                BATTED = 8, TOTEM = 9, CASH = 10;
        final int kind;
        int element, dmg, bossDmg, bounces, life, pierce = 99;
        float grv, spin;
        boolean force, breaksBricks, wallBounce, groundBounce;
        Sprite spr;
        ArrayList<Entity> hitList = new ArrayList<>();
        float drag = 1f, rot;
        /** battle: the hero who threw it (it hits every other fighter) */
        public Player owner;
        int trail;

        PShot(int kind, float cx, float cy, float vx, float vy, Sprite spr, int hw, int hh) {
            this.kind = kind;
            this.spr = spr;
            w = hw;
            h = hh;
            x = cx - w / 2f;
            y = cy - h / 2f;
            this.vx = vx;
            this.vy = vy;
            solidTiles = false;
            alwaysActive = true;
            facing = vx < 0 ? -1 : 1;
        }

        static Sprite elemSprite(int element, int t) {
            return element == ICE ? Art.orbIce[(t / 3) % 2] : Art.orbFire[(t / 3) % 2];
        }

        public static PShot bullet(float cx, float cy, int dir, int element) {
            PShot s = new PShot(BULLET, cx, cy, 6.5f * dir, 0, Art.bulletP, 7, 3);
            s.element = element;
            s.dmg = 2;
            s.bossDmg = 2;
            s.life = 55;
            s.pierce = element == FIRE ? 1 : 0;
            s.breaksBricks = true;
            return s;
        }

        public static PShot coin(float cx, float cy, int dir, float pvx, int element) {
            PShot s = new PShot(COIN, cx, cy, 3.3f * dir + pvx * 0.35f, -3.4f, Art.coinShot, 8, 10);
            s.element = element;
            s.dmg = 2;
            s.bossDmg = 1;
            s.bounces = 3;
            s.grv = 0.28f;
            s.life = 220;
            s.wallBounce = true;
            s.groundBounce = true;
            return s;
        }

        public static PShot ball(float cx, float cy, int dir, float speed, int bounces, int element) {
            PShot s = new PShot(BALL, cx, cy, speed * dir, -3.2f, Art.soccer, 8, 8);
            s.element = element;
            s.dmg = 2;
            s.bossDmg = 2;
            s.bounces = bounces;
            s.grv = 0.26f;
            s.life = 300;
            s.wallBounce = true;
            s.groundBounce = true;
            return s;
        }

        public static PShot orb(float cx, float cy, int dir, int element) {
            PShot s = new PShot(ORB, cx, cy, 3.6f * dir, 1.0f, elemSprite(element, 0), 8, 8);
            s.element = element;
            s.dmg = 1;
            s.bossDmg = 1;
            s.bounces = 5;
            s.grv = 0.3f;
            s.life = 120;
            s.pierce = 0;
            s.groundBounce = true;
            return s;
        }

        public static PShot aimed(int kind, float cx, float cy, float tx, float ty, float speed, int element) {
            float dx = tx - cx, dy = ty - cy;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
            Sprite sp = kind == TOTEM ? Art.orbFire[0] : Art.bulletP;
            PShot s = new PShot(kind, cx, cy, dx / d * speed, dy / d * speed, sp, kind == TOTEM ? 8 : 6, kind == TOTEM ? 8 : 3);
            s.element = element;
            s.dmg = 2;
            s.bossDmg = 1;
            s.life = 90;
            s.pierce = 0;
            return s;
        }

        public static PShot fountain(float cx, float cy, float vx, float vy) {
            PShot s = new PShot(FOUNTAIN, cx, cy, vx, vy, Art.coinShot, 8, 10);
            s.dmg = 2;
            s.bossDmg = 1;
            s.bounces = 1;
            s.grv = 0.22f;
            s.life = 150;
            s.groundBounce = true;
            return s;
        }

        /**
         * Francesco's burst of banknotes: they fly out around him, slow down quickly and cut every enemy (and enemy
         * shot) they touch. The notes of one throw share a hit list, so each enemy is hit once per throw.
         */
        public static void cashBurst(Level L, float cx, float cy, int dir, int element, int upg, Player owner) {
            ArrayList<Entity> shared = new ArrayList<>();
            float sp = upg >= 2 ? 6.0f : 5.3f;
            // forward fan (degrees from straight ahead, negative = upwards) + some behind to cover his back
            float[] fwd = upg >= 2 ? new float[]{-72, -50, -30, -12, 3, 13} : new float[]{-66, -38, -15, 2, 12};
            float[] back = upg >= 2 ? new float[]{-35, -8, 8} : new float[]{-20, 6};
            for (int k = 0; k < fwd.length + back.length; k++) {
                boolean behind = k >= fwd.length;
                double a = Det.rad(behind ? back[k - fwd.length] : fwd[k]);
                float s = behind ? sp * 0.8f : sp;
                float vx = (float) StrictMath.cos(a) * s * (behind ? -dir : dir), vy = (float) StrictMath.sin(a) * s;
                PShot p = new PShot(CASH, cx, cy, vx, vy, Art.banknote, 10, 7);
                p.element = element;
                p.dmg = 1;
                p.bossDmg = 1;
                p.life = upg >= 2 ? 30 : 26;
                p.drag = 0.92f;
                p.grv = 0.02f;
                p.hitList = shared;
                p.rot = k * 0.9f;
                p.owner = owner;
                L.spawn(p);
            }
        }

        public static PShot giant(float cx, float cy, int dir) {
            PShot s = new PShot(GIANT, cx, cy, 5f * dir, 0, Art.giantBall, 28, 28);
            s.dmg = 6;
            s.bossDmg = 5;
            s.force = true;
            s.life = 160;
            s.breaksBricks = true;
            return s;
        }

        public static PShot reflect(Entity shot, int dir) {
            Sprite sp = shot instanceof Ents.Shot ? ((Ents.Shot) shot).sprite() : shot instanceof Uni.UShot ? ((Uni.UShot) shot).sprite() : Art.cannonBall;
            PShot s = new PShot(REFLECT, shot.cx(), shot.cy(), 6.5f * dir, -0.6f, sp, shot.w, shot.h);
            s.dmg = 2;
            s.bossDmg = 2;
            s.life = 90;
            s.pierce = 2;
            s.breaksBricks = true;
            return s;
        }

        public static PShot batted(Ents.Enemy e, int dir) {
            PShot s = new PShot(BATTED, e.cx(), e.cy(), 6f * dir, -2.2f, e.frame(), e.w, e.h);
            s.dmg = 2;
            s.bossDmg = 1;
            s.grv = 0.16f;
            s.life = 120;
            s.pierce = 3;
            s.spin = 0.35f * dir;
            return s;
        }

        @Override
        public void update() {
            if (--life <= 0) {
                vanish();
                return;
            }
            if (kind == ORB) spr = elemSprite(element, t);
            if (drag != 1f) {
                vx *= drag;
                vy *= drag;
                rot += 0.25f + Math.abs(vx) * 0.05f;
            }
            if (kind == TOTEM && t % 3 == 0) L.spawn(new Ember(cx(), cy(), 0xFFFF9A2A));
            if (element != NONE && t % 4 == 0 && kind != ORB) L.spawn(new Ember(cx(), cy(), element == ICE ? 0xFFBFF0FF : 0xFFFF9A2A));
            vy += grv;
            if (vy > 7) vy = 7;
            // ---- horizontal
            x += vx;
            if (kind != GIANT) {
                float fx = vx > 0 ? right() : x;
                int c = (int) Math.floor(fx / T), r = (int) Math.floor(cy() / T);
                byte tl = L.tile(c, r);
                if (Level.solid(tl)) {
                    // bullets, the heavy gold coin and the ball all smash bricks they fly into (every hero can open
                    // a brick wall with the C ability)
                    if ((breaksBricks || kind == COIN || kind == BALL) && (tl == Level.BRICK || tl == Level.QBLOCK)) L.hitBlock(c, r, false);
                    if (wallBounce) {
                        x -= vx;
                        vx = -vx * 0.9f;
                        facing = -facing;
                    } else {
                        if (kind != CASH) L.spawn(new Ents.Impact(vx > 0 ? c * T : c * T + T, cy()));
                        vanish();
                        return;
                    }
                }
            } else {
                // the giant ball rolls straight through, smashing bricks and blocks
                for (int r = (int) (y / T); r <= (int) ((bottom() - 1) / T); r++) {
                    int c = (int) Math.floor((vx > 0 ? right() : x) / T);
                    byte tl = L.tile(c, r);
                    if (tl == Level.BRICK || tl == Level.QBLOCK) L.hitBlock(c, r, false);
                }
                if (t % 5 == 0) L.shake = Math.max(L.shake, 2);
            }
            // ---- vertical
            y += vy;
            if (grv > 0 || groundBounce) {
                if (vy > 0 && L.solidAt(cx(), bottom()) && !L.solidAt(cx(), bottom() - vy - 1)) {
                    int r = (int) Math.floor(bottom() / T);
                    y = r * T - h;
                    if (groundBounce && bounces > 0) {
                        bounces--;
                        if (kind == BALL) vy = -Math.max(4.6f, Math.abs(vy) * 0.95f);
                        else if (kind == ORB) vy = -3.3f;
                        else vy = -Math.abs(vy) * 0.62f; // damped coin bounce
                        if (kind == COIN || kind == FOUNTAIN) vx *= 0.88f;
                        if (kind == BALL || kind == COIN) Sfx.play(Sfx.BUMP);
                    } else {
                        vanish();
                        return;
                    }
                } else if (vy < 0 && L.solidAt(cx(), y)) {
                    vy = Math.abs(vy) * 0.4f;
                    int c = (int) Math.floor(cx() / T), r = (int) Math.floor(y / T);
                    if (breaksBricks || kind == BALL || kind == COIN) {
                        byte tl = L.tile(c, r);
                        if (tl == Level.BRICK || tl == Level.QBLOCK) L.hitBlock(c, r, false);
                    }
                }
            }
            // ---- hit things
            for (int i = 0; i < L.ents.size(); i++) {
                Entity e = L.ents.get(i);
                if (e == this || !target(e) || !overlaps(e) || hitList.contains(e)) continue;
                if (Combat.hit(L, e, dmg, bossDmg, element, Entity.BULLET, force, cx())) {
                    hitList.add(e);
                    L.spawn(new Ents.Impact(cx(), cy()));
                    if (kind == COIN || kind == FOUNTAIN) Sfx.play(Sfx.COIN);
                    else if (kind == CASH) {
                        if (!e.projectile) Sfx.play(Sfx.HIT);
                    } else Sfx.play(Sfx.HIT);
                    if (kind == FOUNTAIN && !e.boss) L.addCoin(1);
                    if (--pierce < 0) {
                        vanish();
                        return;
                    }
                }
            }
            // battle: the shot hits the other fighters
            if (L.battle != null && owner != null) {
                Player f = L.battle.touching(this, owner, hitList);
                if (f != null) {
                    hitList.add(f);
                    L.battle.shotHit(this, f);
                    L.spawn(new Ents.Impact(cx(), cy()));
                    if (kind == COIN || kind == FOUNTAIN) Sfx.play(Sfx.COIN);
                    if (--pierce < 0) {
                        vanish();
                        return;
                    }
                }
            }
            if (L.battle != null) { // battle: the arena bounds (the camera differs from phone to phone)
                if (y > L.th * T + 40 || x < -200 || x > L.tw * T + 200) dead = true;
            } else if (y > L.th * T + 40 || x < L.camX - 200 || x > L.camX + L.game.W + 200) dead = true;
        }

        /** Battle: batted back by Andrea's bat, it now belongs to him and flies at the others. */
        void reflectBy(Player p, int dir) {
            owner = p;
            hitList.clear();
            float sp = Math.max(4.5f, Math.abs(vx) * 1.15f);
            vx = dir * sp;
            if (grv == 0) vy = -0.4f;
            else vy = Math.min(vy, -2.5f);
            facing = dir;
            life = Math.max(life, 60);
        }

        void vanish() {
            dead = true;
            if (kind == CASH) L.spawn(new Banknote(x, y, vx * 0.5f, vy * 0.5f - 0.4f)); // flutters away harmlessly
            else if (kind == COIN || kind == FOUNTAIN) L.spawn(new Ents.Sparkle(cx(), cy()));
            else if (kind == BATTED || kind == GIANT || kind == BALL) L.spawn(new Ents.Puff(cx(), cy()));
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            float px = cx() - cx, py = cy() - cy;
            if (element != NONE && kind != ORB)
                g.circle((int) px, (int) py, Math.max(w, h) / 2 + 3, element == ICE ? 0xFF8AE0FF : 0xFFFF8A2A, 90);
            switch (kind) {
                case CASH:
                    g.blitEx(spr, px, py, 1, Math.max(0.3f, Math.abs((float) StrictMath.cos(rot))), (float) StrictMath.sin(rot) * 0.5f, false,
                            life < 6 ? life * 40 : 255, element == FIRE && t % 4 < 2 ? 0xFFFFA040 : element == ICE && t % 4 < 2 ? 0xFFBFF0FF : 0);
                    break;
                case COIN:
                case FOUNTAIN: {
                    float sx = (float) StrictMath.cos(t * 0.45);
                    g.blitEx(spr, px, py, Math.abs(sx) < 0.25f ? 0.25f : Math.abs(sx), 1, 0, false, 255,
                            element == FIRE && t % 4 < 2 ? 0xFFFFA040 : element == ICE && t % 4 < 2 ? 0xFFBFF0FF : 0);
                    break;
                }
                case BALL:
                case GIANT:
                case BATTED:
                    g.blitEx(spr, px, py, 1, 1, kind == BATTED ? t * spin : t * 0.25f * facing, false, 255,
                            element == FIRE && t % 4 < 2 ? 0xFFFFA040 : element == ICE && t % 4 < 2 ? 0xFFBFF0FF : 0);
                    break;
                case BULLET:
                    g.blit(spr, (int) x - cx, (int) y - cy, vx < 0, 255, element == FIRE ? 0xFFFF7A2A : element == ICE ? 0xFF8AE0FF : 0);
                    break;
                default:
                    g.blitEx(spr, px, py, 1, 1, 0, vx < 0, 255, 0);
                    break;
            }
        }
    }

    // ============================================================ effects
    /** Short-lived ember / snow particle. */
    public static final class Ember extends Ents.Particle {
        final int col;

        Ember(float x, float y, int col) {
            super(x, y, 16);
            this.col = col;
            vy = -0.5f;
            vx = (float) StrictMath.sin(x * 13 + y) * 0.4f;
        }

        @Override
        public void update() {
            super.update();
            x += vx;
            y += vy;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.fillA((int) x - cx, (int) y - cy, 2, 2, col, 230 - t * 12);
        }
    }

    /** Banknote fluttering away (Francesco's throws / special). */
    public static final class Banknote extends Ents.Particle {
        float rot;

        Banknote(float x, float y, float vx, float vy) {
            super(x, y, 45);
            this.vx = vx;
            this.vy = vy;
        }

        @Override
        public void update() {
            super.update();
            vy += 0.08f;
            vx *= 0.97f;
            x += vx + (float) StrictMath.sin(t * 0.3) * 0.6f;
            y += vy;
            rot += 0.15f;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            g.blitEx(Art.banknote, x - cx, y - cy, 1, Math.max(0.3f, Math.abs((float) StrictMath.cos(rot))), (float) StrictMath.sin(rot) * 0.4f,
                    false, Math.max(0, 255 - Math.max(0, t - 25) * 12), 0);
        }
    }

    /** Ice shards when a frozen enemy shatters. */
    public static final class Shard extends Ents.Particle {
        Shard(float x, float y, float vx, float vy) {
            super(x, y, 40);
            this.vx = vx;
            this.vy = vy;
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
            g.fill((int) x - cx, (int) y - cy, 3, 3, 0xFF1A4A7A);
            g.fill((int) x - cx, (int) y - cy, 2, 2, 0xFFDFF8FF);
        }
    }

    // ============================================================ specials
    /** Mattia: a huge shockwave punch in front of him. */
    public static final class SuperPunch extends Ents.Particle {
        final int dir;
        final Player p;

        SuperPunch(Player p) {
            super(p.cx(), p.cy(), 34);
            this.p = p;
            this.dir = p.facing;
        }

        @Override
        public void update() {
            super.update();
            x = p.cx();
            y = p.cy();
            if (t == 6) {
                float hx = dir > 0 ? x - 8 : x - 128, hy = y - 40;
                int id = ++p.L.punchId;
                for (int i = 0; i < p.L.ents.size(); i++) {
                    Entity e = p.L.ents.get(i);
                    if (!target(e) || !e.overlaps(hx, hy, 136, 80) || e.hitStamp == id) continue;
                    e.hitStamp = id;
                    Combat.hit(p.L, e, 6, 4, p.power, Entity.PUNCH, true, p.cx());
                    p.L.spawn(new Ents.Impact(e.cx(), e.cy()));
                }
                if (p.L.battle != null) p.L.battle.melee(p, hx, hy, 136, 80, id, Battle.SUPER_PCT, Battle.SUPER_KB, p.power);
                // smash bricks in the area
                for (int r = (int) (hy / T); r <= (int) ((hy + 80) / T); r++)
                    for (int c = (int) (hx / T); c <= (int) ((hx + 136) / T); c++) {
                        byte tl = p.L.tile(c, r);
                        if (tl == Level.BRICK) p.L.breakBrick(c, r);
                    }
                p.L.shake = 16;
                Sfx.play(Sfx.THUD);
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int px = (int) x - cx, py = (int) y - cy;
            float k = t / 34f;
            int r = 10 + t * 4;
            int a = (int) (230 * (1 - k));
            g.ring(px + dir * (20 + t * 2), py, r, 4, 0xFFFFFFFF, a);
            g.ring(px + dir * (20 + t * 2), py, Math.max(2, r - 8), 3, 0xFFFFD040, a);
            // giant fist
            if (t < 22) {
                Sprite fist = p.hero.frames[HeroArt.ACT1];
                g.blitEx(fist, px + dir * (26 + t * 3), py - 4, 2.2f, 2.2f, 0, dir < 0, 200 - t * 8, p.power == ICE ? 0xFF8AE0FF : p.power == FIRE ? 0xFFFF8A2A : 0xFFFFE070);
            }
        }
    }

    /** Giuseppe's henchmen: follow him and shoot the nearest enemy. */
    public static final class Henchman extends Entity {
        final Player p;
        final int side;
        int life = 330, cool;

        Henchman(Player p, int side) {
            this.p = p;
            this.side = side;
            x = p.cx() + side * 26;
            y = p.y - 30;
            w = 10;
            h = 14;
            solidTiles = false;
            alwaysActive = true;
            cool = side > 0 ? 6 : 18;
        }

        @Override
        public void update() {
            float tx = p.cx() + side * 24 - w / 2f, ty = p.y - 6 + (float) StrictMath.sin((t + side * 20) * 0.1) * 3;
            x += (tx - x) * 0.18f;
            y += (ty - y) * 0.18f;
            Entity best = null;
            float bd = 300 * 300;
            for (int i = 0; i < L.ents.size(); i++) {
                Entity e = L.ents.get(i);
                if (e.dead || !(e.enemy || e.boss) || (e.boss && ((Bosses.Boss) e).defeated)) continue;
                float dx = e.cx() - cx(), dy = e.cy() - cy();
                float d = dx * dx + dy * dy;
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (L.battle != null) best = L.battle.nearestRival(p, cx(), cy(), 300);
            if (best != null) facing = best.cx() < cx() ? -1 : 1;
            else facing = p.facing;
            if (--cool <= 0 && best != null) {
                PShot hs = PShot.aimed(PShot.HENCH, cx() + facing * 6, cy(), best.cx(), best.cy(), 6.5f, p.power);
                hs.owner = p;
                L.spawn(hs);
                L.spawn(new Ents.Flash(cx() + facing * 8, cy()));
                Sfx.play(Sfx.SHOOT);
                cool = 24;
            }
            if (--life <= 0) {
                dead = true;
                L.spawn(new Ents.Puff(cx(), cy()));
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            if (life < 40 && (life / 3) % 2 == 0) return;
            g.blit(Art.henchman[0], Math.round(cx() - 6) - cx, Math.round(bottom()) - 16 - cy, facing < 0);
        }
    }

    /** Andrea's fire totem: floats above him and throws fireballs. */
    public static final class TotemSpirit extends Entity {
        final Player p;
        int life = 270, cool = 10;

        TotemSpirit(Player p) {
            this.p = p;
            x = p.cx() - 10;
            y = p.y - 50;
            w = 20;
            h = 30;
            solidTiles = false;
            alwaysActive = true;
        }

        @Override
        public void update() {
            float tx = p.cx() - w / 2f, ty = p.y - 44 + (float) StrictMath.sin(t * 0.08) * 4;
            x += (tx - x) * 0.15f;
            y += (ty - y) * 0.15f;
            if (t % 3 == 0) L.spawn(new Ember(x + (t * 7) % w, bottom(), 0xFFFF7A2A));
            if (--cool <= 0) {
                Entity best = null;
                float bd = 320 * 320;
                for (int i = 0; i < L.ents.size(); i++) {
                    Entity e = L.ents.get(i);
                    if (e.dead || !(e.enemy || e.boss) || (e.boss && ((Bosses.Boss) e).defeated)) continue;
                    float dx = e.cx() - cx(), dy = e.cy() - cy();
                    float d = dx * dx + dy * dy;
                    if (d < bd) {
                        bd = d;
                        best = e;
                    }
                }
                if (L.battle != null) best = L.battle.nearestRival(p, cx(), cy(), 320);
                float tx2 = best != null ? best.cx() : cx() + p.facing * 100, ty2 = best != null ? best.cy() : cy() + 30;
                PShot s = PShot.aimed(PShot.TOTEM, cx(), cy() + 4, tx2, ty2, 4.5f, FIRE);
                s.dmg = 3;
                s.owner = p;
                L.spawn(s);
                Sfx.play(Sfx.CANNON);
                cool = 18;
            }
            if (--life <= 0) {
                dead = true;
                for (int k = 0; k < 4; k++) L.spawn(new Ents.Puff(x + k * 5, y + k * 6));
            }
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            if (life < 40 && (life / 3) % 2 == 0) return;
            int px = (int) cx() - cx, py = (int) cy() - cy;
            g.circle(px, py, 22 + (t / 3) % 3, 0xFFFF6A2A, 50);
            g.blit(Art.bigTotem, px - Art.bigTotem.w / 2, py - Art.bigTotem.h / 2);
        }
    }
}
