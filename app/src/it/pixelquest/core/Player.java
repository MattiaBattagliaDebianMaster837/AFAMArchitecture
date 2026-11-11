package it.pixelquest.core;

/**
 * The player. Shared moves for every hero: walk, run (stick pushed to the edge), variable jump,
 * spin jump (B), automatic wall slide + wall jump (A), running triple jump ending in a somersault.
 * Hero ability on C: Mattia punches, Giuseppe shoots (2 shots every 10 seconds).
 */
public final class Player extends Entity {
    public static final int MAX_HP = 3, RELOAD = 600;
    static final float GRAV_HOLD = 0.25f, GRAV = 0.6f, MAX_FALL = 6.5f;

    public final Hero hero;
    public int hp = MAX_HP, invuln, starT, hurtT;
    public boolean dying;
    int deathT;

    // movement state
    int coyote, jumpBuf, spinBuf, chain, chainWindow, wallLock, wallStick, pushAway;
    public boolean spinning, flipping, skidding, running, wallSliding, autoWalk;
    public int wallDir;
    float flipAngle;
    Entity riding;
    float prevBottom;
    boolean wasOnGround;
    int lastVxSign;

    // abilities
    public int punchT, punchCD, shootT, shootCD;
    public int ammo = 2, reloadT;
    int curPunch;

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
        if (hurtT > 0) hurtT--;
        if (punchT > 0) punchT--;
        if (punchCD > 0) punchCD--;
        if (shootT > 0) shootT--;
        if (shootCD > 0) shootCD--;
        if (reloadT > 0) {
            reloadT--;
            if (reloadT == 0) {
                ammo = 2;
                Sfx.play(Sfx.RELOAD);
            }
        }

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
        float acc = onGround ? (runHeld ? 0.12f : 0.09f) : 0.1f;
        float dec = onGround ? 0.14f : 0.02f;
        if (onIce) {
            acc *= 0.35f;
            dec = 0.02f;
        }
        if (punchT > 8 && onGround) dir = 0;
        if (dir != 0) {
            if (onGround && vx * dir < 0 && Math.abs(vx) > 0.6f) {
                vx += dir * (onIce ? 0.08f : 0.3f);
                skidding = true;
                if (t % 4 == 0) L.spawn(new Ents.Dust(cx() - dir * 4, bottom()));
                if (t % 10 == 0) Sfx.play(Sfx.SKID);
            } else if (vx * dir < max) {
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
        if (in.aP) jumpBuf = 7;
        if (in.bP) spinBuf = 7;
        if (onGround) {
            coyote = 6;
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
        float g = (vy < 0 && hold) ? GRAV_HOLD : GRAV;
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
        // platforms
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
        // keep inside level horizontally
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

        // ------------------------------------------------------------ ability
        if (in.cP && !autoWalk) {
            if (hero.punch()) {
                if (punchCD == 0) {
                    punchT = 18;
                    punchCD = 24;
                    curPunch = ++L.punchId;
                    Sfx.play(Sfx.PUNCH);
                    if (onGround) vx += facing * 0.8f;
                }
            } else {
                if (shootCD == 0) {
                    if (ammo > 0) {
                        ammo--;
                        if (reloadT == 0) reloadT = RELOAD;
                        shootT = 14;
                        shootCD = 12;
                        float bx = facing > 0 ? right() + 2 : x - 9;
                        L.spawn(new Ents.Bullet(bx, y + 10, facing));
                        L.spawn(new Ents.Flash(facing > 0 ? right() + 6 : x - 6, y + 11));
                        Sfx.play(Sfx.SHOOT);
                        if (onGround) vx -= facing * 0.6f;
                    } else {
                        Sfx.play(Sfx.EMPTY);
                        shootCD = 12;
                    }
                }
            }
        }
        if (punchT >= 5 && punchT <= 14) punchHits();

        // fell off the level
        if (y > L.th * Level.T + 24) die();

        animate();
    }

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
        Sfx.play(spin ? Sfx.SPIN : chain == 2 ? Sfx.JUMP3 : chain == 1 ? Sfx.JUMP2 : Sfx.JUMP);
        if (chain == 2) L.spawn(new Ents.Dust(cx(), bottom()));
    }

    private void wallJump(boolean spin) {
        vy = -(hero.jump + 0.15f);
        vx = -wallDir * 2.7f;
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
        onGround = false;
        coyote = 0;
        if (flipping) flipping = false;
    }

    private void punchHits() {
        float hx = facing > 0 ? right() - 2 : x - 14, hy = y + 5;
        float hw = 16, hh = 12;
        for (Entity e : L.ents) {
            if (e.dead || e.hitStamp == curPunch) continue;
            if (!e.overlaps(hx, hy, hw, hh)) continue;
            if (e.boss) {
                e.hitStamp = curPunch;
                ((Bosses.Boss) e).hit(1, cx());
                Sfx.play(Sfx.HIT);
            } else if ((e.enemy || e.projectile) && e.punchable) {
                e.hitStamp = curPunch;
                e.kill(PUNCH);
                L.spawn(new Ents.Impact(facing > 0 ? hx + hw - 4 : hx + 4, hy + 6));
                L.shake = Math.max(L.shake, 4);
            }
        }
        // bricks / blocks in front
        int c = (int) Math.floor((facing > 0 ? hx + hw : hx) / Level.T);
        int r = (int) Math.floor((hy + hh / 2) / Level.T);
        byte tl = L.tile(c, r);
        if (tl == Level.BRICK || tl == Level.QBLOCK) {
            L.hitBlock(c, r, false);
            L.spawn(new Ents.Impact(c * Level.T + (facing > 0 ? 2 : 14), r * Level.T + 8));
            punchT = Math.min(punchT, 4);
        }
    }

    // --------------------------------------------------------- interactions
    public void interact(Entity e) {
        if (e.dead) return;
        if (!overlaps(e)) return;
        if (e.enemy) {
            if (starT > 0 && !e.boss) {
                e.kill(STAR);
                Sfx.play(Sfx.STOMP);
                return;
            }
            boolean fromAbove = vy > 0 && prevBottom <= e.y + Math.max(6, e.vy + 5);
            if (e.boss) {
                Bosses.Boss b = (Bosses.Boss) e;
                if (fromAbove && b.stompableNow()) {
                    b.hit(1, cx());
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
                    e.kill(SPIN);
                    L.spawn(new Ents.Puff(e.cx(), e.cy()));
                    bounce(false);
                    Sfx.play(Sfx.SPINKILL);
                    return;
                }
                if (e.stompable) {
                    e.kill(STOMP);
                    bounce(L.input.a || L.input.b);
                    Sfx.play(Sfx.STOMP);
                    return;
                }
                if (spinning && !e.spinKill && e instanceof Ents.Hedgehog) {
                    bounce(false);
                    return;
                }
            }
            hurt(e.cx(), false);
        }
    }

    public void hurt(float fromX, boolean spikes) {
        if (dying || invuln > 0 || starT > 0) return;
        if (L.state != Level.PLAY) return;
        hp--;
        if (hp <= 0) {
            die();
            return;
        }
        invuln = 100;
        hurtT = 20;
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
        vy = -6.5f;
        deathT = 0;
        L.playerDied();
    }

    public void heal() {
        if (hp < MAX_HP) hp++;
    }

    public void star() {
        starT = 600;
        L.game.audio.music(Music.STAR);
    }

    // ------------------------------------------------------------- animation
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
        if (punchT > 0) {
            frameIdx = punchT > 14 ? HeroArt.ACT0 : HeroArt.ACT1;
            return;
        }
        if (shootT > 0) {
            frameIdx = shootT > 10 ? HeroArt.ACT1 : HeroArt.ACT0;
            return;
        }
        if (!onGround) {
            if (spinning) frameIdx = -1; // special spin render
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

    @Override
    public void render(Gfx g, int camX, int camY) {
        if (invuln > 0 && (invuln / 3) % 2 == 0 && !dying) return;
        Sprite[] f = hero.frames;
        float px = cx() - camX, py = bottom() - camY;
        int flash = 0;
        if (starT > 0 && (t / 2) % 3 != 0) {
            int[] rb = {0xFFFFE040, 0xFFFF6AD0, 0xFF6AE0FF, 0xFF8AFF6A};
            flash = rb[(t / 2) % 4];
        }
        boolean flip = facing < 0;
        if (skidding) flip = vx < 0;
        if (frameIdx == -1) {
            // spin jump: front, side, back, side (mirrored)
            int k = (t / 2) % 4;
            Sprite s = k == 0 ? f[HeroArt.FRONT] : k == 2 ? f[HeroArt.BACK] : f[HeroArt.JUMP];
            float sx = k == 1 || k == 3 ? 0.8f : 1f;
            g.blitEx(s, px, py - s.h / 2f, sx, 1, 0, k == 3, 255, 0);
            if (flash != 0) g.blitEx(s, px, py - s.h / 2f, sx, 1, 0, k == 3, 120, flash);
            return;
        }
        Sprite s = f[frameIdx];
        if (frameIdx == HeroArt.TUCK) {
            g.blitEx(s, px, py - s.h / 2f, 1, 1, flipAngle, flip, 255, 0);
            if (flash != 0) g.blitEx(s, px, py - s.h / 2f, 1, 1, flipAngle, flip, 120, flash);
            return;
        }
        int sx = Math.round(px - s.w / 2f), sy = Math.round(py) - s.h;
        if (dying) {
            g.blit(s, sx, sy, false);
            return;
        }
        g.blit(s, sx, sy, flip, 255, 0);
        if (flash != 0) g.blit(s, sx, sy, flip, 120, flash);
    }
}
