package it.pixelquest.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Reachability bot used to verify that every level can be finished with the real player physics.
 * Breadth-first search over "macro actions" (run-ups, jumps of various lengths, wall-jump climbs,
 * triple jumps, waiting on platforms) between landing positions. Enemies are removed.
 */
public final class Bot {
    final Game game;
    final Level L;
    final Input in = new Input();
    final List<Entity> keep;
    int sims;
    int[] bricks;

    public Bot(Game game, int levelIndex, int hero) {
        this.game = game;
        game.save.hero = hero;
        L = new Level(game, Levels.get(levelIndex), false);
        keep = new ArrayList<>();
        for (Entity e : L.ents)
            if (e instanceof Ents.Platform || e instanceof Ents.Spring || e instanceof Ents.Goal || e instanceof Ents.Gem) keep.add(e);
        L.ents.clear();
        L.ents.addAll(keep);
        L.bannerT = 0;
        ArrayList<Integer> bl = new ArrayList<>();
        for (int i = 0; i < L.tiles.length; i++) if (L.tiles[i] == Level.BRICK) bl.add(i);
        bricks = new int[bl.size()];
        for (int i = 0; i < bricks.length; i++) bricks[i] = bl.get(i);
        if (L.bossEnt != null) L.bossEnt = null;
    }

    // ------------------------------------------------------------ snapshots
    static final HashMap<Class<?>, List<Field>> FIELDS = new HashMap<>();

    static List<Field> fields(Class<?> c) {
        List<Field> f = FIELDS.get(c);
        if (f != null) return f;
        f = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            for (Field x : k.getDeclaredFields()) {
                if (Modifier.isStatic(x.getModifiers())) continue;
                Class<?> t = x.getType();
                if (t.isPrimitive() || t == String.class || x.getName().equals("riding")) {
                    x.setAccessible(true);
                    f.add(x);
                }
            }
        FIELDS.put(c, f);
        return f;
    }

    static Object[] snap(Object o) {
        List<Field> fs = fields(o.getClass());
        Object[] v = new Object[fs.size()];
        try {
            for (int i = 0; i < v.length; i++) v[i] = fs.get(i).get(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return v;
    }

    static void restore(Object o, Object[] v) {
        List<Field> fs = fields(o.getClass());
        try {
            for (int i = 0; i < v.length; i++) {
                Field f = fs.get(i);
                if (Modifier.isFinal(f.getModifiers())) continue;
                f.set(o, v[i]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    final class State {
        Object[] player, input, level;
        Object[][] ents;
        byte[] tiles, data, variant;
        float x, y;

        State() {
            player = snap(L.player);
            input = snap(in);
            level = snap(L);
            ents = new Object[keep.size()][];
            for (int i = 0; i < keep.size(); i++) ents[i] = snap(keep.get(i));
            tiles = L.tiles.clone();
            data = L.data.clone();
            variant = L.variant.clone();
            x = L.player.x;
            y = L.player.y;
        }

        void load() {
            restore(L.player, player);
            restore(in, input);
            restore(L, level);
            for (int i = 0; i < keep.size(); i++) restore(keep.get(i), ents[i]);
            L.ents.clear();
            for (Entity e : keep) if (!e.dead) L.ents.add(e);
            System.arraycopy(tiles, 0, L.tiles, 0, tiles.length);
            System.arraycopy(data, 0, L.data, 0, data.length);
            System.arraycopy(variant, 0, L.variant, 0, variant.length);
        }
    }

    // --------------------------------------------------------------- actions
    static final class Act {
        int dir, wait, prerun, hold;
        float speed;
        boolean spin, wallClimb, triple, releaseAfterTakeoff, reverseAir, alternate;

        public String toString() {
            return "dir=" + dir + " spd=" + speed + " wait=" + wait + " pre=" + prerun + " hold=" + hold + (spin ? " spin" : "")
                    + (wallClimb ? " wall" : "") + (triple ? " triple" : "") + (releaseAfterTakeoff ? " rel" : "") + (reverseAir ? " rev" : "");
        }
    }

    List<Act> actions(boolean nearPlatform) {
        List<Act> list = new ArrayList<>();
        int[] holds = {0, 5, 12, 40, 90};
        int[] waits = nearPlatform ? new int[]{0, 30, 60, 90, 120, 150, 180, 210} : new int[]{0};
        for (int dir = -1; dir <= 1; dir += 2)
            for (int w : waits)
                for (float sp : new float[]{0.6f, 1f})
                    for (int pre : sp > 0.9f ? new int[]{0, 12, 30} : new int[]{0, 8})
                        for (int h : holds) {
                            Act a = new Act();
                            a.dir = dir;
                            a.wait = w;
                            a.speed = sp;
                            a.prerun = pre;
                            a.hold = h;
                            list.add(a);
                            if (h == 40) {
                                Act b = copy(a);
                                b.wallClimb = true;
                                list.add(b);
                                Act c = copy(a);
                                c.releaseAfterTakeoff = true;
                                list.add(c);
                                if (sp > 0.9f) {
                                    Act d = copy(a);
                                    d.triple = true;
                                    list.add(d);
                                }
                                Act e = copy(a);
                                e.reverseAir = true;
                                e.wallClimb = true;
                                list.add(e);
                                Act f = copy(a);
                                f.wallClimb = true;
                                f.alternate = true;
                                list.add(f);
                            }
                        }
        // jump straight up (for springs / blocks)
        for (int w : waits) {
            Act a = new Act();
            a.dir = 0;
            a.wait = w;
            a.hold = 40;
            a.speed = 0;
            list.add(a);
        }
        return list;
    }

    static Act copy(Act a) {
        Act b = new Act();
        b.dir = a.dir;
        b.wait = a.wait;
        b.speed = a.speed;
        b.prerun = a.prerun;
        b.hold = a.hold;
        return b;
    }

    void frame(float ax, boolean a, boolean b) {
        in.keyAx = ax;
        in.keyA = a;
        in.keyB = b;
        in.latch(null);
        L.update(in);
        sims++;
    }

    /** Run an action from the current state. Returns true when the player ended up standing (or finished). */
    boolean run(Act act) {
        Player p = L.player;
        for (int i = 0; i < act.wait; i++) {
            frame(0, false, false);
            if (p.dying) return false;
        }
        for (int i = 0; i < act.prerun; i++) {
            frame(act.dir * act.speed, false, false);
            if (p.dying) return false;
            if (!p.onGround) break;
        }
        int landings = 0, airborne = 0, total = 0;
        boolean pressed = false;
        int holdLeft = act.hold;
        boolean prevA = false;
        int curDir = act.dir;
        while (total++ < 260) {
            float ax = curDir * act.speed;
            boolean a = false;
            if (act.hold > 0) {
                if (!pressed) {
                    a = true;
                    pressed = true;
                } else if (holdLeft > 0) a = true;
                if (holdLeft > 0) holdLeft--;
            }
            if (!p.onGround) {
                airborne++;
                if (act.releaseAfterTakeoff) ax = 0;
                if (act.reverseAir && airborne > 6 && airborne < 20) ax = -ax;
                if (act.wallClimb && (act.alternate ? p.wallDir != 0 && airborne > 2 : p.wallSliding)) {
                    a = !prevA;
                    holdLeft = 30;
                    if (act.alternate && a) {
                        curDir = -p.wallDir;
                        ax = curDir * act.speed;
                    }
                }
            }
            prevA = a;
            frame(ax, a && !act.spin, a && act.spin);
            if (p.dying || L.state == Level.DEAD) return false;
            if (L.state == Level.CLEAR) return true;
            if (p.onGround && airborne > 0) {
                landings++;
                if (act.triple && landings < 3) {
                    airborne = 0;
                    pressed = false;
                    holdLeft = act.hold;
                    continue;
                }
                return true;
            }
            if (act.hold == 0 && p.onGround && total > 40) return true; // plain walk
        }
        return p.onGround;
    }

    String key(Player p) {
        String r = "";
        if (L.player.onGround) {
            for (int i = 0; i < keep.size(); i++)
                if (keep.get(i) instanceof Ents.Platform && Math.abs(p.bottom() - keep.get(i).y) < 1.5f && p.right() > keep.get(i).x && p.x < keep.get(i).right()) r = "P" + i;
        }
        String base = r.isEmpty() ? ((int) (p.x / 12)) + "," + ((int) (p.y / 12)) : r + ":" + ((int) (p.x / 24));
        int broken = 0;
        for (int i = 0; i < bricks.length; i++) if (L.tiles[bricks[i]] != Level.BRICK) broken++;
        return base + "|" + Math.min(broken, 3);
    }

    static float score(State s, boolean vertical) {
        return vertical ? -s.y * 3 + s.x * 0.1f : s.x - s.y * 0.3f;
    }

    public boolean finished;
    public int gemsFound;

    /** Explores the level. Returns a summary line. */
    public String solve(int maxNodes) {
        for (int i = 0; i < 10; i++) frame(0, false, false);
        final boolean vertical = L.th > 20;
        PriorityQueue<State> q = new PriorityQueue<>(64, new Comparator<State>() {
            public int compare(State a, State b) {
                return Float.compare(score(b, vertical), score(a, vertical));
            }
        });
        HashSet<String> seen = new HashSet<>();
        q.add(new State());
        seen.add(key(L.player));
        float bestX = L.player.x;
        int arenaX = L.arenaCol >= 0 ? (L.arenaCol + 1) * Level.T : -1;
        int nodes = 0;
        boolean hasPlat = false;
        for (Entity e : keep) if (e instanceof Ents.Platform) hasPlat = true;
        int allGems = (1 << L.gemCount) - 1;
        while (!q.isEmpty() && nodes < maxNodes) {
            if (finished && (gemsFound & allGems) == allGems) break;
            State s = q.poll();
            nodes++;
            boolean near = false;
            if (hasPlat) {
                s.load();
                for (Entity e : keep)
                    if (e instanceof Ents.Platform && Math.abs(e.cx() - L.player.cx()) < 220) near = true;
            }
            for (Act a : actions(near)) {
                s.load();
                boolean ok = run(a);
                Player p = L.player;
                gemsFound |= L.gems;
                if (L.state == Level.CLEAR) {
                    finished = true;
                    continue;
                }
                if (arenaX > 0 && p.x > arenaX + 8 && p.onGround) {
                    finished = true;
                    continue;
                }
                if (!ok || p.dying) continue;
                String k = key(p);
                if (seen.add(k)) {
                    q.add(new State());
                    bestX = Math.max(bestX, p.x);
                }
            }
        }
        String gemsStr = L.gemCount == 0 ? "" : " gems=" + Integer.bitCount(gemsFound & allGems) + "/" + L.gemCount;
        return (finished ? "FINISHED" + " gemMask=" + gemsFound + " " : "STUCK furthest col " + (int) (bestX / 16) + "/" + L.tw) + gemsStr + " nodes=" + nodes;
    }
}
