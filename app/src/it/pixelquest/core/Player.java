package it.pixelquest.core;

/**
 * The player. Shared moves for every hero: walk, run (stick pushed to the edge), variable jump,
 * spin jump (B), automatic wall slide + wall jump (A), running triple jump ending in a somersault.
 * C: hero ability (punch / pistol / gold coin / totem bat / soccer ball), boosted by fire and ice flowers.
 * S: special move, available when the energy bar (filled by defeating enemies) is full.
 */
public final class Player extends Entity {
    public static final int MAX_HP = 3, ENERGY_MAX = 100;
    static final float GRAV_HOLD = 0.25f, GRAV = 0.6f, MAX_FALL = 6.5f;

    public final Hero hero;
    public final int upg;
    public int hp = MAX_HP, invuln, starT, hurtT;
    public boolean dying;
    int deathT;

    // movement state
    int coyote, jumpBuf, spinBuf, chain, chainWindow, wallLock, wallStick, pushAway, jumpHoldMin;
    public boolean spinning, flipping, skidding, running, wallSliding, autoWalk;
    public int wallDir;
    float flipAngle;
    Entity riding;
    float prevBottom;
    boolean wasOnGround;
    int lastVxSign;

    // ability
    public int power;                 // Combat.NONE / FIRE / ICE
    public int actT, cd, cdTime;
    public int charges, maxCharges, rechargeT, rechargeTime;
    boolean batch;
    int comboUsed, comboMax, comboTimer;
    int curPunch;
    float reach;
    float ballSpeed;
    int ballBounces;
    boolean bigBat;
    /** turtle shell being carried (kicked with C) */
    public Uni.Shell carrying;
    // special
    public float energy;
    public int specialT;

    int anim;
    int frameIdx = HeroArt.IDLE0;

    public Player(Level L, Hero hero, float x, float y) {
        this.L = L;
        this.hero = hero;
        this.x = x;
        this.y = y;
        w = 10;
        h = 22;
        facing = 1;
        upg = L.game.save.upgrade(hero.id);
        power = L.game.carryPower;
        energy = L.game.carryEnergy;
        switch (hero.ability_) {
            case Hero.PUNCH:
                cdTime = upg >= 2 ? 15 : 24;
                comboMax = upg >= 1 ? 2 : 1;
                reach = upg >= 2 ? 21 : 16;
                break;
            case Hero.GUN:
                maxCharges = 2 + upg;
                batch = true;
                rechargeTime = 600;
                break;
            case Hero.COIN:
                maxCharges = upg >= 1 ? 2 : 1;
                rechargeTime = upg >= 2 ? 100 : 150;
                break;
            case Hero.BAT:
                cdTime = upg >= 2 ? 36 : 60;
                bigBat = upg >= 1;
                break;
            default:
                maxCharges = upg >= 1 ? 2 : 1;
                rechargeTime = 150;
                ballBounces = upg >= 2 ? 5 : 3;
                ballSpeed = upg >= 2 ? 4.8f : 4.0f;
                break;
        }
        charges = maxCharges;
    }

    // ------------------------------------------------------------------ update
    public void update(Input in) {
        t++;
        if (dying) {
            deathT++;
            if (deathT > 30) {
                vy = Math.min(vy + 0.35f, 7);
                y += vy;
            }
            return;
        }
        prevBottom = bottom();
        if (invuln > 0) invuln--;
        if (starT > 0) {
            starT--;
            if (starT == 0) L.game.audio.music(L.arenaLocked ? Music.BOSS : L.def.music);
            if (t % 4 == 0) L.spawn(new Ents.Sparkle(x + (t * 7 % w), y + (t * 5 % h)));
        }
        if (power != Combat.NONE && t % 7 == 0)
            L.spawn(new Combat.Ember(x + (t * 3) % w, y + 4 + (t * 5) % 14, power == Combat.FIRE ? 0xFFFF9A2A : 0xFFDFF8FF));
        if (hurtT > 0) hurtT--;
        if (actT > 0) actT--;
        if (cd > 0) cd--;
        if (comboTimer > 0 && --comboTimer == 0) comboUsed = 0;
        rechargeTick();
        specialTick();

        // ride platforms
        if (riding != null) {
            if (riding.dead) riding = null;
            else {
                x += riding.pdx;
                y += riding.pdy;
                if (riding.pdx != 0) L.moveX(this, 0.001f * Math.signum(riding.pdx));
            }
        }

        int dir = in.dir();
        boolean runHeld = in.runHeld();
        if (autoWalk) {
            dir = 1;
            runHeld = false;
        }
        if (hurtT > 10 || wallLock > 0) dir = wallLock > 0 ? 0 : dir;
        if (wallLock > 0) wallLock--;

        boolean onIce = onGround && L.groundTile == Level.ICE;
        // ---------------------------------------------------------- horizontal
        float max = runHeld ? hero.run : hero.walk;
        running = runHeld && dir != 0;
        skidding = false;
        float acc;
        if (onGround) acc = Math.abs(vx) < hero.walk ? 0.24f : 0.17f;
        else acc = 0.17f;
        float dec = onGround ? 0.28f : 0.05f;
        if (onIce) {
            acc *= 0.35f;
            dec = 0.03f;
        }
        if (actT > 8 && onGround && (hero.ability_ == Hero.PUNCH || hero.ability_ == Hero.BAT)) dir = 0;
        if (dir != 0) {
            if (onGround && vx * dir < 0 && Math.abs(vx) > 0.6f) {
                vx += dir * (onIce ? 0.1f : 0.45f);
                skidding = true;
                if (t % 4 == 0) L.spawn(new Ents.Dust(cx() - dir * 4, bottom()));
                if (t % 10 == 0) Sfx.play(Sfx.SKID);
            } else if (vx * dir < max) {
                if (onGround && !onIce && vx * dir < 0.8f) vx = dir * Math.min(0.8f, max);
                vx += dir * acc;
                if (vx * dir > max) vx = max * dir;
            } else if (vx * dir > max) {
                vx -= dir * Math.min(dec * 0.5f, vx * dir - max);
            }
            if (!skidding && (onGround || wallLock == 0)) facing = dir;
        } else {
            if (Math.abs(vx) <= dec) vx = 0;
            else vx -= Math.signum(vx) * dec;
        }
        if (vx != 0) lastVxSign = vx > 0 ? 1 : -1;

        // ---------------------------------------------------------------- jumps
        // generous buffers: a press slightly before landing / just after leaving a ledge still jumps
        if (in.aP) jumpBuf = 9;
        if (in.bP) spinBuf = 9;
        if (onGround) {
            coyote = 7;
            if (chainWindow > 0) chainWindow--;
            else chain = 0;
        } else if (coyote > 0) coyote--;
        boolean canGroundJump = onGround || coyote > 0;
        if (jumpBuf > 0 && canGroundJump) doJump(false);
        else if (spinBuf > 0 && canGroundJump) doJump(true);
        else if ((jumpBuf > 0 || spinBuf > 0) && !onGround && wallDir != 0) wallJump(spinBuf > 0 && jumpBuf == 0);
        if (jumpBuf > 0) jumpBuf--;
        if (spinBuf > 0) spinBuf--;

        // -------------------------------------------------------------- gravity
        boolean hold = spinning ? (in.b || in.a) : in.a;
        if (autoWalk) hold = false;
        if (jumpHoldMin > 0) {
            jumpHoldMin--;
            hold = true;
        }
        float g = ((vy < 0 && hold) ? GRAV_HOLD : GRAV) * L.gravMul;
        vy = Math.min(vy + g, MAX_FALL);
        if (wallSliding && vy > 1.3f) vy = 1.3f;

        // ------------------------------------------------------------- movement
        wasOnGround = onGround;
        boolean wasRiding = riding != null;
        L.move(this, vx, vy);
        if (hitL || hitR) {
            if (Math.abs(vx) > 2.4f && onGround) Sfx.play(Sfx.BUMP);
            vx = 0;
        }
        riding = null;
        if (vy >= 0) {
            for (Entity e : L.ents) {
                if (!e.platform || e.dead) continue;
                if (x + w <= e.x || x >= e.x + e.w) continue;
                float top = e.y;
                if (prevBottom <= top + 1 + Math.max(0, e.pdy) + (wasRiding ? 2 : 0) && bottom() >= top - 0.01f) {
                    y = top - h;
                    vy = 0;
                    onGround = true;
                    riding = e;
                    L.groundTile = Level.STONE;
                    if (e instanceof Ents.Platform) ((Ents.Platform) e).stepped();
                    break;
                }
            }
        }
        if (onGround && !wasOnGround) land();
        // conveyor belts carry the hero
        if (onGround && riding == null && (L.groundTile == Level.CONV_L || L.groundTile == Level.CONV_R))
            L.moveX(this, L.groundTile == Level.CONV_L ? -1.2f : 1.2f);
        if (x < 0) {
            x = 0;
            vx = 0;
        }
        if (x + w > L.tw * Level.T) {
            x = L.tw * Level.T - w;
            vx = 0;
        }

        // -------------------------------------------------------------- walls
        wallDir = 0;
        if (!onGround) {
            if (touchingWall(-1)) wallDir = -1;
            else if (touchingWall(1)) wallDir = 1;
        }
        if (wallDir != 0 && vy > 0 && !onGround) {
            if (dir == wallDir || lastVxSign == wallDir || wallStick > 0) wallStick = 1;
            if (dir == -wallDir) pushAway++;
            else pushAway = 0;
            if (pushAway > 8) wallStick = 0;
        } else {
            wallStick = 0;
            pushAway = 0;
        }
        boolean wasSliding = wallSliding;
        wallSliding = wallStick > 0 && wallDir != 0 && vy > 0;
        if (wallSliding) {
            facing = wallDir;
            spinning = false;
            flipping = false;
            if (!wasSliding) chain = 0;
            if (t % 6 == 0) L.spawn(new Ents.Dust(wallDir > 0 ? right() : x, y + 4));
        }

        if (flipping) flipAngle += 0.24f * facing;

        // ------------------------------------------------------- ability (C)
        if (in.cP && !autoWalk) useAbility();
        if (hero.ability_ == Hero.PUNCH && actT >= 5 && actT <= 14) punchHits();
        if (hero.ability_ == Hero.BAT && actT >= 6 && actT <= 14) batHits();

        // ---------------------------------------------------------- special (S)
        if (in.sP && !autoWalk) {
            if (energy >= ENERGY_MAX && specialT == 0) startSpecial();
            else Sfx.play(Sfx.EMPTY);
        }

        if (y > L.th * Level.T + 24) die();

        animate();
    }

    // ================================================================ ability
    private void rechargeTick() {
        if (maxCharges <= 0 || charges >= maxCharges) {
            rechargeT = 0;
            return;
        }
        if (rechargeT <= 0) rechargeT = rechargeTime;
        if (--rechargeT == 0) {
            if (batch) charges = maxCharges;
            else charges++;
            Sfx.play(Sfx.RELOAD);
        }
    }

    /** Remaining reload as 0..1 (for the HUD). */
    public float reloadFrac() {
        if (maxCharges > 0) return charges >= maxCharges ? 0 : rechargeT / (float) rechargeTime;
        return cdTime > 0 ? cd / (float) cdTime : 0;
    }

    private void useAbility() {
        if (carrying != null) {
            carrying.kick(this);
            actT = 12;
            return;
        }
        switch (hero.ability_) {
            case Hero.PUNCH:
                if (cd > 0) return;
                actT = 18;
                curPunch = ++L.punchId;
                comboUsed++;
                if (comboUsed < comboMax) {
                    cd = 7;
                    comboTimer = 24;
                } else {
                    cd = cdTime;
                    comboUsed = 0;
                }
                Sfx.play(Sfx.PUNCH);
                if (onGround) vx += facing * 0.8f;
                if (power != Combat.NONE) L.spawn(Combat.PShot.orb(cx() + facing * 10, y + 10, facing, power));
                break;
            case Hero.GUN:
                if (cd > 0) return;
                cd = 12;
                if (charges <= 0) {
                    Sfx.play(Sfx.EMPTY);
                    return;
                }
                charges--;
                actT = 14;
                L.spawn(Combat.PShot.bullet(facing > 0 ? right() + 5 : x - 5, y + 11, facing, power));
                L.spawn(new Ents.Flash(facing > 0 ? right() + 6 : x - 6, y + 11));
                Sfx.play(Sfx.SHOOT);
                if (onGround) vx -= facing * 0.6f;
                break;
            case Hero.COIN:
                if (cd > 0) return;
                cd = 10;
                if (charges <= 0) {
                    Sfx.play(Sfx.EMPTY);
                    return;
                }
                charges--;
                actT = 14;
                L.spawn(Combat.PShot.coin(cx() + facing * 8, y + 6, facing, vx, power));
                // the banknotes are a real attack: they cut every enemy around him
                Combat.PShot.cashBurst(L, cx() + facing * 4, y + 9, facing, power, upg);
                Sfx.play(Sfx.COIN);
                break;
            case Hero.BAT:
                if (cd > 0) return;
                actT = 18;
                cd = cdTime;
                curPunch = ++L.punchId;
                Sfx.play(Sfx.SPIN);
                if (power != Combat.NONE) L.spawn(Combat.PShot.orb(cx() + facing * 12, y + 8, facing, power));
                break;
            default: // BALL
                if (cd > 0) return;
                cd = 10;
                if (charges <= 0) {
                    Sfx.play(Sfx.EMPTY);
                    return;
                }
                charges--;
                actT = 16;
                L.spawn(Combat.PShot.ball(cx() + facing * 9, bottom() - 6, facing, ballSpeed, ballBounces, power));
                Sfx.play(Sfx.KICK);
                break;
        }
    }

    private void punchHits() {
        float hx = facing > 0 ? right() - 2 : x - reach + 2, hy = y + 5;
        float hw = reach, hh = 12;
        for (int i = 0; i < L.ents.size(); i++) {
            Entity e = L.ents.get(i);
            if (e.dead || e.hitStamp == curPunch || !Combat.target(e)) continue;
            if (!e.overlaps(hx, hy, hw, hh)) continue;
            e.hitStamp = curPunch;
            if (Combat.hit(L, e, 2, 1, power, PUNCH, false, cx())) {
                L.spawn(new Ents.Impact(facing > 0 ? hx + hw - 4 : hx + 4, hy + 6));
                L.shake = Math.max(L.shake, 4);
                Sfx.play(Sfx.HIT);
            }
        }
        int c = (int) Math.floor((facing > 0 ? hx + hw : hx) / Level.T);
        int r = (int) Math.floor((hy + hh / 2) / Level.T);
        byte tl = L.tile(c, r);
        if (tl == Level.BRICK || tl == Level.QBLOCK) {
            L.hitBlock(c, r, false);
            L.spawn(new Ents.Impact(c * Level.T + (facing > 0 ? 2 : 14), r * Level.T + 8));
            actT = Math.min(actT, 4);
        }
    }

    /** Totem bat swing: reflects enemy projectiles, launches enemies, smashes bricks. */
    private void batHits() {
        float bw = bigBat ? 30 : 24, bh = bigBat ? 32 : 26;
        float hx = facing > 0 ? cx() : cx() - bw, hy = bottom() - bh - 2;
        for (int i = 0; i < L.ents.size(); i++) {
            Entity e = L.ents.get(i);
            if (e.dead || e.hitStamp == curPunch || !Combat.target(e)) continue;
            if (!e.overlaps(hx, hy, bw, bh)) continue;
            e.hitStamp = curPunch;
            L.spawn(new Ents.Impact(e.cx(), e.cy()));
            L.shake = Math.max(L.shake, 5);
            Sfx.play(Sfx.HIT);
            if (e.projectile) {
                // send it back where it came from
                e.dead = true;
                L.spawn(Combat.PShot.reflect(e, facing));
            } else if (e.boss) {
                Combat.hit(L, e, 2, bigBat ? 2 : 1, power, PUNCH, false, cx());
            } else if (e instanceof Ents.Enemy) {
                Ents.Enemy en = (Ents.Enemy) e;
                if (en.frozen > 0 || power != Combat.NONE || en.hp > 2) {
                    en.damage(2, PUNCH, power);
                } else {
                    // knocked flying: the enemy becomes a projectile that hits others
                    en.enemy = false;
                    L.onKill(en);
                    en.dead = true;
                    L.spawn(Combat.PShot.batted(en, facing));
                }
            } else Combat.hit(L, e, 2, 1, power, PUNCH, false, cx());
        }
        // smash the first brick along the swing, from the hero's front to the tip of the bat
        // (checking only the tip column missed a wall the hero was standing right against)
        int c0 = (int) Math.floor((facing > 0 ? right() - 1 : x + 1) / Level.T);
        int c1 = (int) Math.floor((facing > 0 ? hx + bw : hx) / Level.T);
        outer:
        for (int c = c0; facing > 0 ? c <= c1 : c >= c1; c += facing > 0 ? 1 : -1)
            for (int r = (int) (hy / Level.T); r <= (int) ((hy + bh - 1) / Level.T); r++) {
                byte tl = L.tile(c, r);
                if (tl == Level.BRICK || tl == Level.QBLOCK) {
                    L.hitBlock(c, r, false);
                    break outer;
                }
            }
    }

    // ================================================================ special
    public void addEnergy(float e) {
        if (specialT > 0) return;
        boolean wasFull = energy >= ENERGY_MAX;
        energy = Math.min(ENERGY_MAX, energy + e);
        if (!wasFull && energy >= ENERGY_MAX) {
            Sfx.play(Sfx.CHECK);
            L.spawn(new Ents.FloatText(cx(), y - 8, "SPECIALE PRONTA!", 0xFFFFE070));
        }
    }

    private void startSpecial() {
        energy = 0;
        invuln = Math.max(invuln, 50);
        L.shake = Math.max(L.shake, 8);
        L.banner = hero.special + "!";
        L.bannerT = 70;
        Sfx.play(Sfx.POWER);
        switch (hero.ability_) {
            case Hero.PUNCH:
                actT = 22;
                specialT = 34;
                L.spawn(new Combat.SuperPunch(this));
                break;
            case Hero.GUN:
                specialT = 330;
                L.spawn(new Combat.Henchman(this, -1));
                L.spawn(new Combat.Henchman(this, 1));
                break;
            case Hero.COIN:
                specialT = 170;
                break;
            case Hero.BAT:
                specialT = 270;
                L.spawn(new Combat.TotemSpirit(this));
                break;
            default:
                actT = 16;
                specialT = 60;
                L.spawn(Combat.PShot.giant(cx() + facing * 20, bottom() - 16, facing));
                Sfx.play(Sfx.KICK);
                break;
        }
    }

    private void specialTick() {
        if (specialT <= 0) return;
        specialT--;
        if (hero.ability_ == Hero.COIN) {
            // Re Mida: a golden fountain of coins and banknotes in every direction
            if (specialT % 3 == 0) {
                double a = -Math.PI / 2 + Math.sin(t * 0.7) * 1.25;
                float sp = 4.2f + (t * 7 % 10) * 0.25f;
                L.spawn(Combat.PShot.fountain(cx(), y + 2, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp));
            }
            if (specialT % 5 == 0) L.spawn(new Combat.Banknote(cx(), y + 2, (float) Math.sin(t) * 2.5f, -3f - (t % 3)));
            if (specialT % 4 == 0) L.spawn(new Ents.Sparkle(x + (t * 5) % 14 - 2, y + (t * 3) % 20));
            if (specialT % 12 == 0) Sfx.play(Sfx.COIN);
        }
    }

    // ================================================================ movement helpers
    private boolean touchingWall(int d) {
        float px = d < 0 ? x - 1 : right() + 0.5f;
        return L.solidAt(px, y + 5) && L.solidAt(px, bottom() - 5);
    }

    void touchTile(int c, int r) {
        if (L.tile(c, r) == Level.SPIKE) hurt(c * Level.T + 8, true);
    }

    private void doJump(boolean spin) {
        float speedFrac = Math.min(1f, Math.abs(vx) / hero.run);
        if (!spin) {
            if (chainWindow > 0 && Math.abs(vx) > hero.walk + 0.25f && chain < 2) chain++;
            else chain = 0;
        } else chain = 0;
        float v = hero.jump + 0.55f * speedFrac;
        if (chain == 1) v += 0.6f;
        if (chain == 2) v += 1.2f;
        if (spin) v = hero.jump - 0.25f + 0.3f * speedFrac;
        vy = -v;
        onGround = false;
        coyote = 0;
        jumpBuf = spinBuf = 0;
        spinning = spin;
        flipping = chain == 2;
        flipAngle = 0;
        riding = null;
        jumpHoldMin = 7;
        Sfx.play(spin ? Sfx.SPIN : chain == 2 ? Sfx.JUMP3 : chain == 1 ? Sfx.JUMP2 : Sfx.JUMP);
        if (chain == 2) L.spawn(new Ents.Dust(cx(), bottom()));
    }

    private void wallJump(boolean spin) {
        vy = -(hero.jump + 0.15f);
        vx = -wallDir * 2.7f;
        jumpHoldMin = 7;
        facing = -wallDir;
        wallLock = 11;
        wallStick = 0;
        wallSliding = false;
        chain = 0;
        spinning = spin;
        flipping = false;
        jumpBuf = spinBuf = 0;
        L.spawn(new Ents.Dust(wallDir > 0 ? right() : x, y + 12));
        Sfx.play(Sfx.WALLJUMP);
        wallDir = 0;
    }

    private void land() {
        chainWindow = chain == 2 ? 0 : 9;
        if (chain == 2) chain = 0;
        spinning = false;
        flipping = false;
        if (prevBottom < bottom() - 3 || vy > 3) L.spawn(new Ents.Dust(cx(), bottom()));
    }

    /** Bounce after stomping something. */
    public void bounce(boolean big) {
        vy = big ? -6.4f : -4.6f;
        jumpHoldMin = 0;
        onGround = false;
        coyote = 0;
        if (flipping) flipping = false;
    }

    // --------------------------------------------------------- interactions
    public void interact(Entity e) {
        if (e.dead) return;
        if (!overlaps(e)) return;
        Ents.Enemy en = e instanceof Ents.Enemy ? (Ents.Enemy) e : null;
        boolean fromAbove = vy > 0 && prevBottom <= e.y + Math.max(6, e.vy + 5);
        if (e instanceof Uni.Shell) {
            ((Uni.Shell) e).touch(this, fromAbove);
            return;
        }
        if (en != null && en.frozen > 0) {
            // frozen enemies are harmless blocks of ice: stomping or spinning shatters them
            if (fromAbove) {
                en.shatter();
                bounce(L.input.a || L.input.b);
            }
            return;
        }
        if (!e.enemy) return;
        if (starT > 0 && !e.boss) {
            e.kill(STAR);
            Sfx.play(Sfx.STOMP);
            return;
        }
        if (e.boss) {
            Bosses.Boss b = (Bosses.Boss) e;
            if (fromAbove && b.stompableNow()) {
                b.hit(1, cx(), false);
                bounce(true);
                Sfx.play(Sfx.STOMP);
                y = Math.min(y, e.y - h);
                return;
            }
            if (b.harmful()) hurt(e.cx(), false);
            return;
        }
        if (fromAbove) {
            if (spinning && e.spinKill) {
                if (en != null) en.spun();
                else e.kill(SPIN);
                L.spawn(new Ents.Puff(e.cx(), e.cy()));
                bounce(false);
                Sfx.play(Sfx.SPINKILL);
                return;
            }
            if (e.stompable) {
                if (en != null) en.stomped();
                else e.kill(STOMP);
                bounce(L.input.a || L.input.b);
                Sfx.play(Sfx.STOMP);
                return;
            }
        }
        hurt(e.cx(), false);
    }

    public void hurt(float fromX, boolean spikes) {
        if (dying || invuln > 0 || starT > 0) return;
        if (L.state != Level.PLAY) return;
        L.game.attemptHits++;
        carrying = null;
        if (power != Combat.NONE) {
            // the flower power absorbs the hit
            power = Combat.NONE;
            for (int k = 0; k < 6; k++) L.spawn(new Ents.Sparkle(x + k * 2, y + (k * 5) % 20));
        } else {
            hp--;
            if (hp <= 0) {
                die();
                return;
            }
        }
        invuln = 100;
        hurtT = 20;
        jumpHoldMin = 0;
        vy = spikes ? -5f : -3.2f;
        vx = (cx() < fromX ? -1.6f : 1.6f);
        spinning = flipping = false;
        L.shake = 6;
        Sfx.play(Sfx.HURT);
    }

    public void die() {
        if (dying) return;
        dying = true;
        hp = 0;
        power = Combat.NONE;
        carrying = null;
        vy = -6.5f;
        deathT = 0;
        L.playerDied();
    }

    public void heal() {
        if (hp < MAX_HP) hp++;
    }

    public void setPower(int p) {
        power = p;
        if (hp < MAX_HP) hp = Math.max(hp, 1);
    }

    public void star() {
        starT = 600;
        L.game.audio.music(Music.STAR);
    }

    // ------------------------------------------------------------- animation
    private int actFrame() {
        switch (hero.ability_) {
            case Hero.PUNCH: return actT > 14 ? HeroArt.ACT0 : HeroArt.ACT1;
            case Hero.GUN: return actT > 10 ? HeroArt.ACT1 : HeroArt.ACT0;
            case Hero.COIN: return actT > 11 ? HeroArt.ACT0 : HeroArt.ACT1;
            case Hero.BAT: return actT > 14 ? HeroArt.ACT0 : HeroArt.ACT1;
            default: return actT > 12 ? HeroArt.ACT0 : HeroArt.ACT1;
        }
    }

    private void animate() {
        anim++;
        if (dying) {
            frameIdx = HeroArt.DEAD;
            return;
        }
        if (L.state == Level.CLEAR || L.state == Level.BOSSWIN) {
            if (onGround && L.stateT > 60) {
                frameIdx = HeroArt.VICTORY;
                vx = 0;
                autoWalk = false;
                return;
            }
        }
        if (hurtT > 0) {
            frameIdx = HeroArt.HURT;
            return;
        }
        if (actT > 0) {
            frameIdx = actFrame();
            return;
        }
        if (!onGround) {
            if (spinning) frameIdx = -1;
            else if (flipping) frameIdx = HeroArt.TUCK;
            else if (wallSliding) frameIdx = HeroArt.WALL;
            else frameIdx = vy < 0 ? HeroArt.JUMP : HeroArt.FALL;
            return;
        }
        float s = Math.abs(vx);
        if (skidding) frameIdx = HeroArt.SKID;
        else if (s > hero.walk + 0.3f) frameIdx = HeroArt.RUN0 + (t / Math.max(3, (int) (7 - s * 1.2f))) % 4;
        else if (s > 0.05f) frameIdx = HeroArt.WALK0 + (t / 7) % 4;
        else frameIdx = (t / 30) % 2 == 0 ? HeroArt.IDLE0 : HeroArt.IDLE1;
    }

    /** Overlay colour for star / flower power / Re Mida. */
    private int tint() {
        if (starT > 0 && (t / 2) % 3 != 0) {
            int[] rb = {0xFFFFE040, 0xFFFF6AD0, 0xFF6AE0FF, 0xFF8AFF6A};
            return rb[(t / 2) % 4];
        }
        if (hero.ability_ == Hero.COIN && specialT > 0) return 0xFFFFD040;
        if (power == Combat.FIRE) return 0xFFFF7A2A;
        if (power == Combat.ICE) return 0xFF7AD8FF;
        return 0;
    }

    private int tintAlpha() {
        if (starT > 0) return 120;
        if (hero.ability_ == Hero.COIN && specialT > 0) return 150;
        return (t / 6) % 2 == 0 ? 70 : 50;
    }

    @Override
    public void render(Gfx g, int camX, int camY) {
        if (invuln > 0 && (invuln / 3) % 2 == 0 && !dying && specialT == 0) return;
        Sprite[] f = hero.frames;
        float px = cx() - camX, py = bottom() - camY;
        int flash = tint(), fa = tintAlpha();
        boolean flip = facing < 0;
        if (skidding) flip = vx < 0;
        if (hero.ability_ == Hero.COIN && specialT > 0) g.circle((int) px, (int) py - 12, 20 + (t / 3) % 3, 0xFFFFE070, 70);
        if (frameIdx == -1) {
            int k = (t / 2) % 4;
            Sprite s = k == 0 ? f[HeroArt.FRONT] : k == 2 ? f[HeroArt.BACK] : f[HeroArt.JUMP];
            float sx = k == 1 || k == 3 ? 0.8f : 1f;
            g.blitEx(s, px, py - s.h / 2f, sx, 1, 0, k == 3, 255, 0);
            if (flash != 0) g.blitEx(s, px, py - s.h / 2f, sx, 1, 0, k == 3, fa, flash);
            return;
        }
        Sprite s = f[frameIdx];
        if (frameIdx == HeroArt.TUCK) {
            g.blitEx(s, px, py - s.h / 2f, 1, 1, flipAngle, flip, 255, 0);
            if (flash != 0) g.blitEx(s, px, py - s.h / 2f, 1, 1, flipAngle, flip, fa, flash);
            return;
        }
        int sx = Math.round(px - s.w / 2f), sy = Math.round(py) - s.h;
        if (dying) {
            g.blit(s, sx, sy, false);
            return;
        }
        boolean batBehind = hero.ability_ == Hero.BAT && actT == 0;
        if (batBehind) drawBat(g, px, py);
        g.blit(s, sx, sy, flip, 255, 0);
        if (flash != 0) g.blit(s, sx, sy, flip, fa, flash);
        if (hero.ability_ == Hero.BAT && !batBehind) drawBat(g, px, py);
        if (hero.ability_ == Hero.COIN && specialT > 0) g.blit(Art.crown, sx + 6 + (flip ? 0 : 1), sy - 2, flip);
    }

    /** Andrea's black totem bat, rotating around his hands. */
    private void drawBat(Gfx g, float px, float py) {
        float th; // 0 = pointing up, positive = towards the front
        if (actT == 0) th = -0.45f;
        else if (actT > 14) th = -0.8f;
        else th = -0.6f + (14 - Math.max(6, actT)) / 8f * 2.6f;
        int dir = facing;
        float hx = px + dir * (actT == 0 ? -2 : 3), hy = py - (actT == 0 ? 13 : 12);
        float len = 8;
        float cxp = hx + dir * (float) Math.sin(th) * len, cyp = hy - (float) Math.cos(th) * len;
        int fl = power == Combat.FIRE ? 0xFFFF7A2A : power == Combat.ICE ? 0xFF8AE0FF : 0;
        g.blitEx(Art.batTotem, cxp, cyp, 1, 1, th * dir, dir < 0, 255, 0);
        if (fl != 0) g.blitEx(Art.batTotem, cxp, cyp, 1, 1, th * dir, dir < 0, 90, fl);
        if (actT > 5 && actT <= 14) { // swoosh
            for (int k = 1; k <= 3; k++) {
                float tk = th - k * 0.35f;
                g.psetA((int) (hx + dir * Math.sin(tk) * 14), (int) (hy - Math.cos(tk) * 14), 0xFFFFFFFF, 160 - k * 40);
            }
        }
    }
}
