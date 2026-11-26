package it.pixelquest.core;

import java.util.Random;

/**
 * A computer-controlled fighter in battle mode. It produces the same input a player would (stick + A/B/C/S)
 * and uses its hero's own technique: Mattia punches up close, Giuseppe shoots when lined up, Francesco throws
 * coins and banknotes, Andrea swings the bat (and bats shots back), Paolo kicks the ball. It goes for the
 * power-ups, keeps away from the edges and lava, and when launched off the arena it tries to come back with
 * wall jumps and the mid-air spin. Difficulty changes reaction time, accuracy and recovery.
 */
final class Cpu {
    final Battle b;
    final Player me;
    final int lvl;
    final Input in = new Input();
    final Random rnd;
    Entity target;
    int retarget, think, holdA, stuck;
    boolean prevA;
    /** frames since anything happened between us and the target (to break stalemates) */
    int idle;
    float lastDmgSum;
    float ax, lastX;
    boolean wantJump, wantAttack, wantSpecial, wantHop;

    Cpu(Battle b, Player me, int lvl, long seed) {
        this.b = b;
        this.me = me;
        this.lvl = Math.max(0, Math.min(2, lvl));
        this.rnd = new Random(seed);
    }

    private int reaction() {
        return lvl == 0 ? 20 : lvl == 1 ? 9 : 3;
    }

    private float attackRate() {
        return lvl == 0 ? 0.3f : lvl == 1 ? 0.65f : 0.95f;
    }

    Input think() {
        boolean A = false, B = false, C = false, S = false;
        if (me.out || me.koT > 0 || me.hurtT > 0 || me.frozenT > 0) {
            holdA = 0;
            return latch(0, false, false, false, false);
        }
        Level L = b.level;
        float mx = me.cx(), mid = (b.stageL + b.stageR) / 2f;
        float home = b.safeX(mx); // nearest safe floor (never the lava)
        boolean offStage = !me.onGround && (mx < b.stageL - 2 || mx > b.stageR + 2 || me.bottom() > b.stageTop + 6);
        if (offStage) {
            // ------------------------------------------------------------- recovery
            int dir = mx < home ? 1 : -1;
            ax = Math.abs(home - mx) < 6 ? 0 : dir;
            boolean below = me.bottom() > b.stageTop - 4;
            float outside = Math.max(b.stageL - mx, mx - b.stageR);
            if (me.spinning && me.vy < 0) B = true; // keep B held: the spin goes higher
            else if (me.airSpins > 0 && me.vy > 0.3f && (me.wallDir != 0 || below || outside > 56 || me.bottom() > b.stageTop - 24 && outside > 20)) {
                // the recovery spin (sliding down the side of the arena it goes straight up the wall);
                // easy CPUs sometimes forget to come back
                if (lvl > 0 || rnd.nextInt(100) < 60) B = true;
            }
            holdA = 0;
            return latch(ax, A, B, false, false);
        }
        // ------------------------------------------------------------------ fighting
        if (--retarget <= 0 || target == null || target.dead || (target instanceof Player && !Battle.alive((Player) target))) {
            retarget = 50 + rnd.nextInt(70);
            target = pickTarget();
        }
        if (--think <= 0) {
            think = reaction() + rnd.nextInt(reaction() + 1);
            decide();
        }
        Entity tg = target;
        if (tg == null) return latch(0, false, false, false, false);
        float dx = tg.cx() - mx, dy = tg.cy() - me.cy();
        int toward = dx < 0 ? -1 : 1;
        float move = ax;
        // lava a little further on, and the target across it: take a run-up (it is crossed running)
        if (me.onGround && move != 0) {
            float fx = mx + Math.signum(move) * 44;
            byte far = L.tile((int) Math.floor(fx / Level.T), (int) Math.floor((me.bottom() + 2) / Level.T));
            if ((far == Level.LAVA || far == Level.LAVA_BODY) && Math.signum(dx) == Math.signum(move) && Math.abs(dx) > 50) move = Math.signum(move);
        }
        // don't walk off the arena or into lava
        if (me.onGround && move != 0) {
            float px = mx + Math.signum(move) * 12;
            boolean ground = groundBelow(px, me.bottom(), 84); // a step down onto something is fine, the void is not
            byte ahead = L.tile((int) Math.floor(px / Level.T), (int) Math.floor((me.bottom() + 2) / Level.T));
            if (ahead == Level.LAVA || ahead == Level.LAVA_BODY) {
                if ((dy < -10 || Math.abs(dx) > 40) && Math.abs(me.vx) > me.hero.walk + 0.6f) { // running: jump over the pool
                    wantJump = true;
                    move = Math.signum(move);
                } else if (dy < -10 || Math.abs(dx) > 40) move = -Math.signum(move) * 0.6f; // back off for a run-up
                else move = 0;
            } else if (!ground) {
                if (dy < -14 && Math.abs(dx) < 90) wantJump = true; // a platform out there: jump to it
                else move = 0;
            }
        }
        // stuck against something: hop
        if (me.onGround && move != 0 && Math.abs(me.x - lastX) < 0.05f) {
            if (++stuck > 12) {
                wantJump = true;
                stuck = 0;
            }
        } else stuck = 0;
        lastX = me.x;
        // dodge / bat back an incoming shot
        Combat.PShot shot = incoming();
        if (shot != null && lvl > 0) {
            if (me.hero.ability_ == Hero.BAT && me.cd == 0 && Math.abs(shot.cx() - mx) < 34) {
                if (Math.signum(shot.cx() - mx) == me.facing || Math.abs(shot.cx() - mx) < 6) C = true;
                else move = Math.signum(shot.cx() - mx) * 0.3f;
            } else if (me.onGround && rnd.nextInt(100) < (lvl == 2 ? 70 : 35)) wantJump = true;
        }
        // something solid right above the head: move out from under it before jumping up
        float ceil = ceiling(mx, me.y);
        if (wantJump && me.onGround && dy < -20 && ceil > 0) {
            int gap = openColumn(mx, ceil);
            if (gap != 0) {
                move = gap;
                wantJump = false;
            }
        }
        // jump
        if (wantJump && me.onGround && holdA == 0 && !prevA) { // A must be released before a new jump
            holdA = dy < -30 ? 22 : 12;
            wantJump = false;
        }
        if (holdA > 0) {
            A = true;
            holdA--;
        }
        // land on the rival's head
        if (!me.onGround && tg instanceof Player && dy > 8 && Math.abs(dx) < 26) move = Math.signum(dx) * 0.7f;
        // attack
        if (wantAttack && tg instanceof Player && ready() && inRange(dx, dy)) {
            if (toward == me.facing || Math.abs(dx) < 6) {
                C = true;
                wantAttack = false;
            } else move = toward * 0.3f; // turn around first
        }
        if (me.carrying != null && tg instanceof Player && toward == me.facing && Math.abs(dx) < 160 && Math.abs(dy) < 20) C = true;
        if (wantSpecial && me.energy >= Player.ENERGY_MAX && tg instanceof Player && Math.abs(dx) < specialRange() && Math.abs(dy) < 50) {
            S = true;
            wantSpecial = false;
        }
        return latch(move, A, B, C, S);
    }

    /** y of something solid within 40 px above the head at x, or -1. */
    private float ceiling(float x, float top) {
        for (float yy = top - 4; yy > top - 44; yy -= 4) if (b.level.solidAt(x, yy)) return yy;
        return -1;
    }

    /** Solid ground (not lava) within {@code depth} px below (x, y). */
    private boolean groundBelow(float x, float y, float depth) {
        Level L = b.level;
        int c = (int) Math.floor(x / Level.T);
        for (float yy = y + 2; yy < y + depth; yy += Level.T / 2f) {
            byte tl = L.tile(c, (int) Math.floor(yy / Level.T));
            if (tl == Level.LAVA || tl == Level.LAVA_BODY) return false;
            if (Level.solid(tl) || tl == Level.SEMI) return true;
        }
        return onPlatformAhead(x);
    }

    /** Direction (-1 / 1) of the nearest column with nothing solid at height y, or 0. */
    private int openColumn(float x, float y) {
        Level L = b.level;
        int c = (int) Math.floor(x / Level.T), r = (int) Math.floor(y / Level.T);
        for (int d = 1; d < 8; d++) {
            if (!Level.solid(L.tile(c - d, r)) && groundBelow((c - d) * Level.T + 8, me.bottom() - 2, 20)) return -1;
            if (!Level.solid(L.tile(c + d, r)) && groundBelow((c + d) * Level.T + 8, me.bottom() - 2, 20)) return 1;
        }
        return 0;
    }

    private boolean onPlatformAhead(float px) {
        for (Entity e : b.level.ents)
            if (e.platform && !e.dead && px > e.x && px < e.right() && Math.abs(e.y - me.bottom()) < 10) return true;
        return false;
    }

    /** Decide where to go and whether to attack (re-evaluated every few frames: that is the reaction time). */
    private void decide() {
        Entity tg = target;
        if (tg == null) {
            ax = 0;
            return;
        }
        float dx = tg.cx() - me.cx(), dy = tg.cy() - me.cy(), adx = Math.abs(dx);
        int toward = dx < 0 ? -1 : 1;
        if (!(tg instanceof Player)) { // going for a power-up
            ax = adx > 6 ? toward * (adx > 60 ? 1f : 0.6f) : 0;
            wantJump = dy < -24 && adx < 50;
            return;
        }
        // stalemate: nobody hurt for a while, go and get the rival (also ranged heroes, also down from a ledge)
        float dmgSum = me.dmg + ((Player) tg).dmg;
        if (dmgSum != lastDmgSum) idle = 0;
        else idle += reaction();
        lastDmgSum = dmgSum;
        float want = idle > 360 ? 10 : preferredDistance();
        if (idle > 360 && dy > 24 && adx < 40 && me.onGround) { // the rival is below: get off this ledge
            ax = me.cx() < (b.stageL + b.stageR) / 2f ? -0.6f : 0.6f;
            wantAttack = false;
            return;
        }
        if (adx > want + 10) ax = toward * (adx > want + 70 ? 1f : 0.6f);
        else if (adx < want - 26 && want > 30) ax = -toward * 0.6f; // ranged heroes keep their distance
        else ax = 0;
        if (dy < -26 && adx < 80) wantJump = true;
        if (rnd.nextInt(100) < (lvl == 0 ? 4 : 8)) wantJump = true; // a hop now and then
        wantAttack = rnd.nextFloat() < attackRate();
        wantSpecial = rnd.nextFloat() < (lvl == 0 ? 0.25f : 0.6f);
    }

    private float preferredDistance() {
        switch (me.hero.ability_) {
            case Hero.PUNCH: return 14;
            case Hero.BAT: return 20;
            case Hero.GUN: return 100;
            case Hero.COIN: return 34;
            default: return 70;
        }
    }

    private boolean ready() {
        return me.cd == 0 && (me.maxCharges == 0 || me.charges > 0) && me.actT == 0;
    }

    private boolean inRange(float dx, float dy) {
        float adx = Math.abs(dx), ady = Math.abs(dy);
        switch (me.hero.ability_) {
            case Hero.PUNCH: return adx < 26 && ady < 16;
            case Hero.BAT: return adx < 34 && ady < 22;
            case Hero.GUN: return adx < 210 && ady < 12;
            case Hero.COIN: return adx < 52 && ady < 30 || adx < 140 && ady < 14;
            default: return adx < 150 && ady < 14;
        }
    }

    private float specialRange() {
        switch (me.hero.ability_) {
            case Hero.PUNCH: return 110;
            case Hero.COIN: return 120;
            default: return 220;
        }
    }

    /** Nearest rival (the human counts a bit nearer), or a power-up when nobody is close. */
    private Entity pickTarget() {
        Entity best = null;
        float bd = Float.MAX_VALUE;
        for (Player f : b.fighters) {
            if (f == me || !Battle.alive(f)) continue;
            float d = Math.abs(f.cx() - me.cx()) + Math.abs(f.cy() - me.cy()) * 0.7f;
            if (!f.cpu) d *= 0.75f;
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        if (lvl > 0 && (bd > 90 || rnd.nextInt(100) < 20)) {
            for (Entity e : b.level.ents) {
                if (e.dead || !(e instanceof Ents.Item || (e instanceof Uni.Shell && me.carrying == null && ((Uni.Shell) e).state == Uni.Shell.IDLE)))
                    continue;
                if (e.cx() < b.stageL || e.cx() > b.stageR) continue;
                float d = Math.abs(e.cx() - me.cx()) + Math.abs(e.cy() - me.cy());
                if (d < 150) return e;
            }
        }
        return best;
    }

    /** A rival's shot flying at us. */
    private Combat.PShot incoming() {
        for (Entity e : b.level.ents) {
            if (!(e instanceof Combat.PShot) || e.dead) continue;
            Combat.PShot s = (Combat.PShot) e;
            if (s.owner == null || s.owner == me) continue;
            float dx = me.cx() - s.cx();
            if (Math.abs(dx) > 70 || Math.abs(me.cy() - s.cy()) > 16) continue;
            if (s.vx != 0 && Math.signum(s.vx) != Math.signum(dx)) continue;
            return s;
        }
        return null;
    }

    private Input latch(float ax, boolean a, boolean b2, boolean c, boolean s) {
        prevA = a;
        in.keyAx = Math.max(-1, Math.min(1, ax));
        in.keyAy = 0;
        in.keyA = a;
        in.keyB = b2;
        in.keyC = c;
        in.keyS = s;
        in.latch(null);
        return in;
    }
}
