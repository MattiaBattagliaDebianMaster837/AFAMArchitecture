package it.pixelquest.core;

import java.util.ArrayList;
import java.util.Random;

/**
 * Battle mode, Smash style: the five heroes fight on an arena with the same moves as in the story (walk, run,
 * jump, spin, wall jump, triple jump, the C ability and the special move). Hits raise the damage percentage, and
 * the higher it is the farther a hit sends you flying: fly past the edges of the arena and you lose a life (stock).
 * In mid air B gives one extra spin jump to get back on the arena. Power-ups drop from the sky now and then.
 * The last hero standing wins.
 */
public final class Battle {
    /** upgrade level used by every fighter in battle (same for all: it's fair) */
    public static final int UPG = 1;
    static final int T = Level.T;
    /** frames of "3, 2, 1, VIA!" */
    public static final int COUNTDOWN = 180;
    // damage (percent) and base knockback of the attacks
    static final float PUNCH_PCT = 9, PUNCH_KB = 3.2f, BAT_PCT = 9, BAT_KB = 3.2f, SUPER_PCT = 24, SUPER_KB = 6.5f;
    public static final int[] SLOT_COLORS = {0xFFFF4A4A, 0xFF4A9AFF, 0xFF5AE06A, 0xFFFFD040, 0xFFC07AFF};

    // ------------------------------------------------------------------ arenas
    public static final class Arena {
        public final String name, desc;
        final int world, stage, music;
        final String[] rows;
        final int[] spawns;

        Arena(String name, String desc, int world, int stage, int music, int[] spawns, String... rows) {
            this.name = name;
            this.desc = desc;
            this.world = world;
            this.stage = stage;
            this.music = music;
            this.spawns = spawns;
            this.rows = rows;
        }
    }

    static final String E = "..............................";
    public static final Arena[] ARENAS = {
            new Arena("LUNGOMARE", "CAPO D'ORLANDO: PIATTAFORME", 0, 0, Music.W1, new int[]{7, 22, 11, 18, 14},
                    E, E, E, E, E,
                    "............------............",
                    E,
                    ".....-----..........-----.....",
                    E,
                    "....######################....",
                    "....######################....",
                    "....######################....",
                    "......##################......",
                    E),
            new Arena("SALINE DI PACECO", "PIATTAFORME MOBILI AI LATI", 1, 3, Music.W2, new int[]{8, 21, 11, 18, 14},
                    E, E, E, E, E,
                    "............------............",
                    E,
                    "..m.....................m.....",
                    E,
                    "......##################......",
                    "......##################......",
                    "......##################......",
                    "........##############........",
                    E),
            new Arena("BARCELLONA GHIACCIATA", "PAVIMENTO DI GHIACCIO", 2, 1, Music.W3, new int[]{7, 22, 11, 18, 14},
                    E, E, E, E, E,
                    ".........---......---.........",
                    E,
                    "...............-..............",
                    E,
                    "....IIIIIIIIIIIIIIIIIIIIII....",
                    "....######################....",
                    "....######################....",
                    "......##################......",
                    E),
            new Arena("CASTELLO DI NASO", "POZZA DI LAVA IN MEZZO", 3, 3, Music.W4, new int[]{6, 23, 9, 20, 11},
                    E, E, E, E, E, E,
                    "............------............", // jump-through: never a lid over the lava
                    E, E,
                    "...##########~~~~##########...",
                    "...##########%%%%##########...",
                    "...##########%%%%##########...",
                    "......##################......",
                    E),
            new Arena("AULA MAGNA", "NASTRI CHE SPINGONO VERSO IL VUOTO", 5, 0, Music.W6, new int[]{7, 22, 11, 18, 14},
                    E, E, E, E, E,
                    "............------............",
                    E,
                    ".....------........------.....",
                    E,
                    "....<<<<<<<<<<<>>>>>>>>>>>....",
                    "....######################....",
                    "....######################....",
                    "......##################......",
                    E),
            new Arena("TERRAZZA DI UNIPA", "BLOCCHI A/B CHE SPARISCONO", 6, 4, Music.W7, new int[]{9, 20, 12, 17, 14},
                    E, E, E, E, E,
                    "...........AAAAAAAA...........",
                    E,
                    "...VVVVV..............VVVVV...",
                    E,
                    "...AAAA################VVVV...",
                    ".......################.......",
                    "........##############........",
                    E, E),
            new Arena("PIAZZALE DI UNIPA", "ARENA PIATTA, SENZA AIUTI", 4, 5, Music.FINAL, new int[]{8, 21, 11, 18, 14},
                    E, E, E, E, E, E, E, E, E,
                    ".....####################.....",
                    ".....####################.....",
                    ".....####################.....",
                    ".......################.......",
                    E),
    };

    public static final String[] LEVEL_NAMES = {"FACILE", "NORMALE", "DIFFICILE"};

    /** Choices made in the battle setup screens. */
    public static final class Config {
        public int hero, level = 1, stocks = 3, arena;
        public final boolean[] rivals = new boolean[5];
        /** Wi-Fi battle: the friend's hero (fighter 1) and the random seed shared by both phones */
        public boolean net;
        public int guestHero = -1;
        public long seed;

        public int rivalCount() {
            int n = 0;
            for (int i = 0; i < rivals.length; i++) if (rivals[i] && i != hero && !(net && i == guestHero)) n++;
            return n;
        }

        public Config copy() {
            Config c = new Config();
            c.hero = hero;
            c.level = level;
            c.stocks = stocks;
            c.arena = arena;
            System.arraycopy(rivals, 0, c.rivals, 0, rivals.length);
            c.net = net;
            c.guestHero = guestHero;
            c.seed = seed;
            return c;
        }
    }

    // ------------------------------------------------------------------ state
    final Game game;
    public final Level level;
    public final Config cfg;
    public final Arena arena;
    public final ArrayList<Player> fighters = new ArrayList<>();
    final ArrayList<Cpu> cpus = new ArrayList<>();
    final Input[] inputs;
    /** all the randomness of the match: seeded the same on both phones in a Wi-Fi battle */
    final Random rnd;
    /** which fighter is played on this phone (0 = who created the match, 1 = the friend who joined) */
    public final int localIdx;
    /** Wi-Fi link, or null */
    public final Net net;
    private final Input[] oneHuman = new Input[1];
    public int t, overT;
    public boolean over, humanLost;
    int itemT = 360;
    /** main platform: left / right edge and top, in pixels */
    int stageL, stageR, stageTop;
    /** columns of the main floor that are safe to stand on (not lava) */
    boolean[] safeCol;

    public Battle(Game game, Config cfg) {
        this(game, cfg, 0, null);
    }

    public Battle(Game game, Config cfg, int localIdx, Net net) {
        this.game = game;
        this.cfg = cfg;
        this.net = net;
        this.rnd = cfg.seed != 0 ? new Random(cfg.seed) : new Random();
        this.arena = ARENAS[Math.max(0, Math.min(ARENAS.length - 1, cfg.arena))];
        LevelDef def = new LevelDef(arena.name, arena.world, arena.stage, arena.music, arena.rows);
        level = new Level(game, def, false);
        level.battle = this;
        level.banner = null;
        level.bannerT = 0;
        addFighter(cfg.hero, false, 0);
        int slot = 1;
        if (cfg.net && cfg.guestHero >= 0) addFighter(cfg.guestHero, false, slot++); // the friend on the other phone
        for (int i = 0; i < cfg.rivals.length; i++)
            if (cfg.rivals[i] && i != cfg.hero && !(cfg.net && i == cfg.guestHero)) addFighter(i, true, slot++);
        if (fighters.size() < 2) addFighter((cfg.hero + 1) % Hero.ALL.length, true, 1);
        inputs = new Input[fighters.size()];
        this.localIdx = Math.max(0, Math.min(fighters.size() - 1, localIdx));
        level.player = fighters.get(this.localIdx);
        computeStage();
        float W = game.W;
        level.camX = (level.tw * T - W) / 2f;
        level.camY = level.th * T - game.H;
        game.audio.music(arena.music);
    }

    private void addFighter(int heroIdx, boolean cpu, int slot) {
        int col = arena.spawns[slot % arena.spawns.length];
        Player p = new Player(level, Hero.ALL[heroIdx], col * T + 3, spawnY(col));
        p.cpu = cpu;
        p.slot = slot;
        p.stocks = Math.max(1, cfg.stocks);
        p.facing = col * T < level.tw * T / 2 ? 1 : -1;
        p.invuln = 0;
        fighters.add(p);
        cpus.add(cpu ? new Cpu(this, p, cfg.level, 1234L * (slot + 1) + rnd.nextInt(1000)) : null);
    }

    private float spawnY(int col) {
        for (int r = 0; r < level.th; r++)
            if (Level.solid(level.tile(col, r)) || level.tile(col, r) == Level.SEMI) return r * T - 22;
        return 40;
    }

    /** The main floor: the row with the most solid tiles; its first and last solid tile bound the stage. */
    private void computeStage() {
        int bestRow = 0, bestCount = -1;
        for (int r = 0; r < level.th; r++) {
            int n = 0;
            for (int c = 0; c < level.tw; c++) if (Level.solid(level.tile(c, r)) || level.tile(c, r) == Level.LAVA) n++;
            if (n > bestCount) {
                bestCount = n;
                bestRow = r;
            }
        }
        int l = level.tw, rr = 0;
        for (int c = 0; c < level.tw; c++) {
            byte tl = level.tiles[bestRow * level.tw + c];
            if (tl != Level.EMPTY) {
                l = Math.min(l, c);
                rr = Math.max(rr, c);
            }
        }
        stageL = l * T;
        stageR = (rr + 1) * T;
        stageTop = bestRow * T;
        safeCol = new boolean[level.tw];
        for (int c = 0; c < level.tw; c++) {
            byte tl = level.tiles[bestRow * level.tw + c];
            safeCol[c] = tl != Level.EMPTY && tl != Level.LAVA && tl != Level.LAVA_BODY;
        }
    }

    /** x of the safe floor nearest to {@code x} (where to come back to after a launch). */
    float safeX(float x) {
        int c0 = (int) Math.floor(x / T), best = -1;
        for (int d = 0; d < level.tw; d++) {
            if (c0 - d >= 0 && c0 - d < level.tw && safeCol[c0 - d]) { best = c0 - d; break; }
            if (c0 + d >= 0 && c0 + d < level.tw && safeCol[c0 + d]) { best = c0 + d; break; }
        }
        if (best < 0) return (stageL + stageR) / 2f;
        // aim one tile inside the safe floor, not at its very edge (towards the safe side, never the lava)
        boolean l = best - 1 >= 0 && safeCol[best - 1], r = best + 1 < level.tw && safeCol[best + 1];
        int inward = l && !r ? -1 : r && !l ? 1 : 0;
        return (best + inward) * T + T / 2f;
    }

    public boolean countdown() {
        return t < COUNTDOWN;
    }

    // ------------------------------------------------------------------ update
    /** The fighter played on this phone. */
    public Player local() {
        return fighters.get(localIdx);
    }

    void update(Input human) {
        oneHuman[0] = human;
        update(oneHuman);
    }

    /** One frame. {@code humans}: the input of each human fighter, in order (host, friend). */
    void update(Input[] humans) {
        Level L = level;
        Input human = humans[0];
        t++;
        L.frame++;
        if (L.bannerT > 0) L.bannerT--;
        if (L.shake > 0) L.shake--;
        for (int i = 0; i < L.bump.length; i++) if (L.bump[i] > 0) L.bump[i]--;
        if (t == 1 || t == 46 || t == 91) Sfx.play(Sfx.SELECT);
        if (t == 136) Sfx.play(Sfx.CONFIRM);
        L.updatePhase();
        for (Entity e : L.ents) if (e.platform && !e.dead) {
            float ox = e.x, oy = e.y;
            e.update();
            e.pdx = e.x - ox;
            e.pdy = e.y - oy;
            e.t++;
        }
        // fighters
        int hi = 0;
        for (int i = 0; i < fighters.size(); i++) {
            Player f = fighters.get(i);
            Input in = f.cpu ? cpus.get(i).think() : humans[Math.min(hi++, humans.length - 1)];
            if (countdown() || over) in = Player.NO_INPUT;
            inputs[i] = in;
            L.input = in;
            f.update(in);
        }
        L.input = human;
        for (Entity e : L.ents) {
            if (e.platform || e.dead) continue;
            e.update();
            e.t++;
        }
        // fighters vs shells, lava, each other, the edges of the arena
        for (int i = 0; i < fighters.size(); i++) {
            Player f = fighters.get(i);
            if (!alive(f)) continue;
            L.input = inputs[i] != null ? inputs[i] : Player.NO_INPUT;
            for (int k = 0; k < L.ents.size(); k++) {
                Entity e = L.ents.get(k);
                if (!e.dead && e instanceof Uni.Shell) f.interact(e);
            }
            lava(f);
            if (f.hurtT > 0 && Math.abs(f.vx) + Math.abs(f.vy) > 6 && t % 2 == 0) L.spawn(new Ents.Puff(f.cx(), f.cy()));
            for (int j = 0; j < fighters.size(); j++) {
                Player o = fighters.get(j);
                if (o == f || !alive(o)) continue;
                contact(f, o, i);
            }
            if (f.x < -56 || f.right() > L.tw * T + 56 || f.y > L.th * T + 16 || f.bottom() < -110) ko(f);
        }
        L.input = human;
        for (int i = L.ents.size() - 1; i >= 0; i--) if (L.ents.get(i).dead) L.ents.remove(i);
        L.flushAdds();
        items();
        camera();
        if (over) overT++;
    }

    static boolean alive(Player f) {
        return !f.out && f.koT == 0 && !f.dying;
    }

    /** Stomping on a rival's head, and the star that hurts on touch. */
    private void contact(Player a, Player b, int ai) {
        if (!a.overlaps(b)) return;
        if (a.starT > 0 && b.starT == 0) {
            b.battleHit(10, 3.4f, a.cx(), a, Combat.NONE);
            return;
        }
        boolean fromAbove = a.vy > 0 && a.prevBottom <= b.y + 6;
        if (fromAbove && b.invuln == 0) {
            b.battleHit(a.spinning ? 7 : 5, 1.6f, a.cx(), a, Combat.NONE);
            Input in = inputs[ai] != null ? inputs[ai] : Player.NO_INPUT;
            a.y = b.y - a.h;
            a.bounce(in.a || in.b);
            Sfx.play(a.spinning ? Sfx.SPINKILL : Sfx.STOMP);
        }
    }

    /** Lava throws you up in the air with a burn, instead of killing you. */
    private void lava(Player f) {
        int l = (int) Math.floor(f.x / T), r = (int) Math.floor((f.right() - 0.01f) / T);
        int row = (int) Math.floor((f.bottom() - 3) / T);
        for (int c = l; c <= r; c++) {
            byte tl = level.tile(c, row);
            if ((tl == Level.LAVA || tl == Level.LAVA_BODY) && f.vy >= 0) {
                f.dmg = Math.min(999, f.dmg + 10); // every dip burns
                f.vy = -Math.min(11f, 8f + f.dmg / 60f);
                float home = safeX(f.cx()); // up and towards the nearest bank
                f.vx = Math.signum(home - f.cx()) * 1.8f;
                f.hurtT = 8; // control comes back quickly: steer out of the pool
                f.invuln = Math.max(f.invuln, 30);
                f.onGround = false;
                Sfx.play(Sfx.HURT);
                for (int k = 0; k < 6; k++) level.spawn(new Combat.Ember(f.x + k * 2, f.bottom() - (k % 3) * 4, 0xFFFF9A2A));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ hits
    /** A melee attack (punch, bat, super punch) on every rival inside the box. */
    void melee(Player by, float hx, float hy, float hw, float hh, int id, float pct, float kb, int element) {
        for (Player f : fighters) {
            if (f == by || !alive(f) || f.hitStamp == id || !f.overlaps(hx, hy, hw, hh)) continue;
            f.hitStamp = id;
            if (element == Combat.FIRE) pct += 3;
            f.battleHit(pct, kb, by.cx(), by, element);
            level.spawn(new Ents.Impact(f.cx(), f.cy()));
            level.shake = Math.max(level.shake, 4);
        }
    }

    /** The first fighter (not {@code skip}, not already in {@code done}) touching {@code e}. */
    Player touching(Entity e, Player skip, ArrayList<Entity> done) {
        for (Player f : fighters) {
            if (f == skip || !alive(f) || (done != null && done.contains(f))) continue;
            if (e.overlaps(f)) return f;
        }
        return null;
    }

    Player nearestRival(Player p, float x, float y, float max) {
        Player best = null;
        float bd = max * max;
        for (Player f : fighters) {
            if (f == p || !alive(f)) continue;
            float dx = f.cx() - x, dy = f.cy() - y, d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        return best;
    }

    /** Damage and knockback of each kind of shot. */
    void shotHit(Combat.PShot s, Player f) {
        float pct, kb;
        switch (s.kind) {
            case Combat.PShot.BULLET: pct = 9; kb = 2.9f; break;
            case Combat.PShot.COIN: pct = 6; kb = 2.2f; break;
            case Combat.PShot.CASH: pct = 3; kb = 1.4f; break;
            case Combat.PShot.BALL: pct = 8; kb = 2.9f; break;
            case Combat.PShot.ORB: pct = 4; kb = 1.8f; break;
            case Combat.PShot.HENCH: pct = 4; kb = 1.5f; break;
            case Combat.PShot.FOUNTAIN: pct = 4; kb = 1.7f; break;
            case Combat.PShot.GIANT: pct = 22; kb = 6.2f; break;
            case Combat.PShot.TOTEM: pct = 6; kb = 2.4f; break;
            case Combat.PShot.REFLECT: pct = 10; kb = 3.2f; break;
            default: pct = 5; kb = 2f; break;
        }
        if (s.owner != null && s.owner.hero.ability_ == Hero.BAT && s.kind != Combat.PShot.TOTEM) { // batted back: stronger
            pct *= 1.3f;
            kb *= 1.2f;
        }
        float dir = s.vx != 0 ? Math.signum(s.vx) : (f.cx() < s.cx() ? -1 : 1);
        f.battleHit(pct, kb, f.cx() - dir * 10, s.owner, s.element);
    }

    void shellHit(Uni.Shell s, Player f) {
        float dir = s.vx != 0 ? Math.signum(s.vx) : (f.cx() < s.cx() ? -1 : 1);
        f.battleHit(10, 3.2f, f.cx() - dir * 10, s.kicker, Combat.NONE);
    }

    // ------------------------------------------------------------------ KO / respawn / end
    void ko(Player f) {
        if (f.koT > 0 || f.out || over) return; // once the battle is decided nobody loses a life any more (double KO on the last frame)
        f.stocks--;
        f.falls++;
        if (f.lastHitBy != null && f.lastHitBy != f) f.lastHitBy.kos++;
        // a burst where it left the screen
        float ex = Math.max(level.camX + 10, Math.min(level.camX + game.W - 10, f.cx()));
        float ey = Math.max(level.camY + 10, Math.min(level.camY + game.H - 10, f.cy()));
        level.spawn(new KoBurst(ex, ey, SLOT_COLORS[f.slot % SLOT_COLORS.length]));
        level.shake = 14;
        Sfx.play(Sfx.BOSSDIE);
        if (f.carrying != null) f.carrying = null;
        f.power = Combat.NONE;
        f.specialT = 0;
        f.starT = 0;
        f.frozenT = 0;
        f.hurtT = 0;
        f.vx = f.vy = 0;
        f.x = -9999;
        f.y = -9999;
        if (f.stocks <= 0) {
            f.out = true;
            int left = 0;
            for (Player p : fighters) if (!p.out) left++;
            f.place = left + 1;
            level.banner = f.hero.name.substring(0, f.hero.name.indexOf(' ')) + " ELIMINATO!";
            level.bannerT = 110;
            checkEnd();
        } else f.koT = 100;
    }

    void respawn(Player f) {
        // drop in from the top, above the spawn point farthest from the others
        int best = arena.spawns[0];
        float bestD = -1;
        for (int col : arena.spawns) {
            float d = 9999;
            for (Player o : fighters) if (o != f && alive(o)) d = Math.min(d, Math.abs(o.cx() - col * T));
            if (d > bestD) {
                bestD = d;
                best = col;
            }
        }
        f.x = best * T + 3;
        f.y = -26;
        f.vx = 0;
        f.vy = 0;
        f.dmg = 0;
        f.invuln = 150;
        f.hurtT = 0;
        f.airSpins = 1;
        f.onGround = false;
        f.lastHitBy = null;
        Sfx.play(Sfx.SPROUT);
    }

    private void checkEnd() {
        int left = 0, humans = 0;
        for (Player p : fighters) {
            if (!p.out) left++;
            if (!p.out && !p.cpu) humans++;
        }
        if (left <= 1 || humans == 0) {
            over = true;
            humanLost = local().out;
            // places of those still in: more lives first, then less damage
            ArrayList<Player> in = new ArrayList<>();
            for (Player p : fighters) if (!p.out) in.add(p);
            java.util.Collections.sort(in, new java.util.Comparator<Player>() {
                public int compare(Player a, Player b) {
                    if (a.stocks != b.stocks) return b.stocks - a.stocks;
                    return Float.compare(a.dmg, b.dmg);
                }
            });
            for (int i = 0; i < in.size(); i++) in.get(i).place = i + 1;
            level.banner = null;
            level.bannerT = 0;
            game.audio.music(-1);
            Sfx.play(Sfx.GATE);
        }
    }

    /** State fingerprint, compared between the two phones in a Wi-Fi battle to detect a desync. */
    int checksum() {
        int h = 17;
        for (Player p : fighters) {
            h = h * 31 + Float.floatToIntBits(p.x);
            h = h * 31 + Float.floatToIntBits(p.y);
            h = h * 31 + Float.floatToIntBits(p.vx);
            h = h * 31 + Float.floatToIntBits(p.vy);
            h = h * 31 + Float.floatToIntBits(p.dmg);
            h = h * 31 + p.stocks * 7 + p.koT;
        }
        int shots = 0;
        for (Entity e : level.ents) if (!(e instanceof Ents.Particle)) shots++;
        return h * 31 + shots;
    }

    public Player winner() {
        for (Player p : fighters) if (p.place == 1) return p;
        return fighters.get(0);
    }

    /** Fighters sorted by final place. */
    public ArrayList<Player> ranking() {
        ArrayList<Player> r = new ArrayList<>(fighters);
        java.util.Collections.sort(r, new java.util.Comparator<Player>() {
            public int compare(Player a, Player b) {
                return a.place - b.place;
            }
        });
        return r;
    }

    // ------------------------------------------------------------------ power-ups
    private void items() {
        if (countdown() || over) return;
        if (--itemT > 0) return;
        itemT = 420 + rnd.nextInt(300);
        int onStage = 0;
        for (Entity e : level.ents) if (e instanceof Ents.Item || e instanceof Uni.Shell) onStage++;
        if (onStage >= 2) return;
        float x = stageL + 16 + rnd.nextInt(Math.max(1, stageR - stageL - 40));
        int roll = rnd.nextInt(100);
        if (roll < 20) {
            level.spawn(new Uni.Shell(x, -14));
        } else {
            int kind = roll < 40 ? Ents.Item.HEART : roll < 50 ? Ents.Item.STAR : roll < 66 ? Ents.Item.FIRE
                    : roll < 80 ? Ents.Item.ICE : Ents.Item.LIFE;
            Ents.Item it = new Ents.Item(x, -16, kind);
            it.rise = 0;
            it.facing = rnd.nextBoolean() ? 1 : -1;
            level.spawn(it);
        }
        level.spawn(new Ents.Sparkle(x + 6, 8));
        Sfx.play(Sfx.SPROUT);
    }

    // ------------------------------------------------------------------ camera
    private void camera() {
        Level L = level;
        float W = game.W, aw = L.tw * T;
        if (aw <= W) L.camX = (aw - W) / 2f;
        else {
            float sx = 0, n = 0;
            for (int i = 0; i < fighters.size(); i++) {
                Player f = fighters.get(i);
                if (!alive(f)) continue;
                float wgt = i == localIdx ? 2 : 1;
                sx += f.cx() * wgt;
                n += wgt;
            }
            float target = n > 0 ? sx / n - W / 2f : (aw - W) / 2f;
            target = Math.max(-24, Math.min(aw - W + 24, target));
            L.camX += (target - L.camX) * 0.08f;
        }
        L.camY = L.th * T - game.H;
    }

    // ------------------------------------------------------------------ render
    void renderFighters(Gfx g, int cx, int cy) {
        for (int i = fighters.size() - 1; i >= 0; i--) {
            Player f = fighters.get(i);
            if (!alive(f)) continue;
            f.render(g, cx, cy);
            int col = SLOT_COLORS[f.slot % SLOT_COLORS.length];
            int mx = Math.round(f.cx()) - cx, my = Math.round(f.y) - cy - 12;
            String tag = f.cpu ? "CPU" : i == localIdx ? "TU" : "AMICO";
            g.textC(tag, mx, my - 8, col, 0xFF000000, 1);
            g.fill(mx - 2, my, 5, 1, col);
            g.fill(mx - 1, my + 1, 3, 1, col);
            g.pset(mx, my + 2, col);
            // off screen: a bubble on the edge with the hero's face
            boolean offL = f.right() < cx, offR = f.x > cx + g.w, offU = f.bottom() < cy;
            if (offL || offR || offU) {
                int bx = offL ? 12 : offR ? g.w - 12 : Math.max(12, Math.min(g.w - 12, mx));
                int by = offU ? 44 : Math.max(44, Math.min(g.h - 30, Math.round(f.cy()) - cy));
                g.circle(bx, by, 10, 0xFF101020, 200);
                g.ring(bx, by, 10, 1, col, 255);
                g.blit(f.hero.icon, bx - f.hero.icon.w / 2, by - f.hero.icon.h / 2);
            }
        }
    }

    void renderHud(Gfx g) {
        int n = fighters.size();
        int avail = g.w - 34;
        int sw = Math.min(96, avail / n), x0 = (avail - n * sw) / 2;
        for (int i = 0; i < n; i++) {
            Player f = fighters.get(i);
            int sx = x0 + i * sw, col = SLOT_COLORS[f.slot % SLOT_COLORS.length];
            g.fillA(sx + 1, 2, sw - 2, 30, 0xFF0A0A1A, f.out ? 90 : 150);
            g.fill(sx + 1, 2, sw - 2, 2, col);
            g.blit(f.hero.icon, sx + 3, 7, false, f.out ? 90 : 255, 0);
            if (f.out) {
                g.textO("FUORI", sx + 22, 11, 0xFF8A8A9A, 0xFF000000, 1);
            } else {
                int d = Math.round(f.dmg);
                int dc = d < 35 ? 0xFFFFFFFF : d < 70 ? 0xFFFFE070 : d < 110 ? 0xFFFFA040 : (t / 6) % 2 == 0 ? 0xFFFF4A3A : 0xFFFF8A6A;
                String ds = f.koT > 0 ? "KO" : d + "%";
                g.textO(ds, sx + 22, 6, dc, 0xFF000000, 2);
            }
            String nm = f.hero.name.substring(0, f.hero.name.indexOf(' '));
            if (Gfx.textW(nm, 1) > sw - 8 - Math.min(5, f.stocks) * 6) nm = nm.substring(0, 3);
            g.textO(nm, sx + 3, 23, f.cpu ? 0xFFC0C0D8 : 0xFFFFFFFF, 0xFF000000, 1);
            for (int k = 0; k < Math.min(5, f.stocks); k++) g.circle(sx + sw - 8 - k * 6, 26, 2, col, 255);
            if (i == localIdx) { // the special bar of this phone's hero, under the slot
                int bw = sw - 6, fw = (int) (bw * Math.min(1f, f.energy / Player.ENERGY_MAX));
                g.fill(sx + 3, 33, bw, 2, 0xFF2A2440);
                g.fill(sx + 3, 33, fw, 2, f.energy >= Player.ENERGY_MAX && (t / 6) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFC030);
            }
        }
        if (countdown()) {
            int k = t / 45;
            String s = k < 3 ? String.valueOf(3 - k) : "VIA!";
            int sc = k < 3 ? 6 : 5;
            g.textC(s, g.w / 2 + 2, g.h / 2 - 24 + 2, 0xFF000000, 0xFF000000, sc);
            g.textC(s, g.w / 2, g.h / 2 - 24, k < 3 ? 0xFFFFFFFF : 0xFFFFE070, 0xFF3A1A08, sc);
            if (k == 0) g.textC(arena.name, g.w / 2, g.h / 2 + 22, 0xFFFFE070, 0xFF000000, 1);
        }
        if (over) {
            String s = "GAME!";
            g.textC(s, g.w / 2 + 3, g.h / 2 - 24 + 3, 0xFF000000, 0xFF000000, 6);
            g.textC(s, g.w / 2, g.h / 2 - 24, 0xFFFFE070, 0xFF8A1A2A, 6);
        }
        if (level.bannerT > 0 && level.banner != null) {
            int a = Math.min(255, level.bannerT * 8);
            g.fillA(0, 52, g.w, 19, 0xFF000000, a / 2);
            g.textC(level.banner, g.w / 2, 58, 0xFFFFFFFF, 0xFF000000, 1);
        }
    }

    /** Flash and rays where a hero left the arena. */
    static final class KoBurst extends Ents.Particle {
        final int col;

        KoBurst(float x, float y, int col) {
            super(x, y, 40);
            this.col = col;
        }

        @Override
        public void render(Gfx g, int cx, int cy) {
            int px = (int) x - cx, py = (int) y - cy;
            float k = t / 40f;
            g.circle(px, py, (int) (8 + t * 2.2f), 0xFFFFFFFF, (int) (200 * (1 - k)));
            g.circle(px, py, (int) (5 + t * 1.4f), col, (int) (230 * (1 - k)));
            for (int r = 0; r < 8; r++) {
                double a = r * Math.PI / 4 + t * 0.05;
                for (int d = 6 + t; d < 14 + t * 3; d += 2)
                    g.psetA(px + (int) (StrictMath.cos(a) * d), py + (int) (StrictMath.sin(a) * d), 0xFFFFF4C0, (int) (220 * (1 - k)));
            }
            if (t < 30) g.textC("KO!", px, py - 6, 0xFFFFFFFF, 0xFF000000, 2);
        }
    }
}
