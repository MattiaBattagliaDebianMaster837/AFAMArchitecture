package it.pixelquest.core;

import java.util.ArrayList;

/** A running level: tile map, entities, camera, rules. */
public final class Level {
    public static final int T = 16;
    // tiles
    public static final byte EMPTY = 0, GROUND = 1, STONE = 2, BRICK = 3, QBLOCK = 4, USED = 5, SEMI = 6, SPIKE = 7,
            LAVA = 8, LAVA_BODY = 9, COIN = 10, ICE = 11, CANNON = 12, GATE = 13, CONV_L = 14, CONV_R = 15,
            TIMED_A = 16, TIMED_B = 17, UBLOCK = 18;
    // q-block contents
    public static final byte C_COIN = 1, C_MULTI = 2, C_HEART = 3, C_STAR = 4, C_LIFE = 5, C_FIRE = 6, C_ICE = 7;

    public final Game game;
    public final LevelDef def;
    public final Theme theme;
    public final int tw, th;
    /** first ground row of the original layout (boss arenas use it as the floor) */
    public final int floorRow;
    public final byte[] tiles, data, variant;
    private final byte[] bump;
    public final ArrayList<Entity> ents = new ArrayList<>();
    private final ArrayList<Entity> adds = new ArrayList<>();
    public Player player;
    public float camX, camY;
    public int frame, coins, gems, gemCount;
    public float spawnX, spawnY;
    public boolean checkpoint;
    public int shake;

    public static final int PLAY = 0, CLEAR = 1, DEAD = 2, BOSSWIN = 3;
    public int state = PLAY, stateT;
    public String banner;
    public int bannerT;

    // boss arena
    public int arenaCol = -1;
    public boolean arenaLocked;
    public Bosses.Boss bossEnt;
    public int punchId;
    public String hint;
    public int hintT;
    private final Input noInput = new Input();
    /** input used by the current update (springs / stomp bounces read held buttons from here) */
    public Input input = noInput;

    public Level(Game game, LevelDef def, boolean fromCheckpoint) {
        this.game = game;
        this.def = def;
        this.theme = Theme.get(def.world);
        // two extra rows copied from the bottom so the touch controls sit over solid ground, not over the hero
        String[] rows = new String[def.rows.length + 2];
        System.arraycopy(def.rows, 0, rows, 0, def.rows.length);
        rows[rows.length - 2] = rows[rows.length - 1] = def.rows[def.rows.length - 1];
        floorRow = def.rows.length - 3;
        th = rows.length;
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        tw = w;
        tiles = new byte[tw * th];
        data = new byte[tw * th];
        variant = new byte[tw * th];
        bump = new byte[tw * th];
        int gemIdx = 0, signIdx = 0;
        ArrayList<int[]> gemPos = new ArrayList<>();
        for (int r = 0; r < th; r++) {
            String row = rows[r];
            for (int c = 0; c < tw; c++) {
                char ch = c < row.length() ? row.charAt(c) : ' ';
                int i = r * tw + c;
                float ex = c * T, ey = r * T;
                switch (ch) {
                    case '#': tiles[i] = GROUND; break;
                    case '=': tiles[i] = STONE; break;
                    case 'B': tiles[i] = BRICK; break;
                    case 'b': tiles[i] = BRICK; data[i] = C_MULTI; break;
                    case '?': tiles[i] = QBLOCK; data[i] = C_COIN; break;
                    case 'M': tiles[i] = QBLOCK; data[i] = C_MULTI; break;
                    case 'h': tiles[i] = QBLOCK; data[i] = C_HEART; break;
                    case '*': tiles[i] = QBLOCK; data[i] = C_STAR; break;
                    case 'L': tiles[i] = QBLOCK; data[i] = C_LIFE; break;
                    case 'Y': tiles[i] = QBLOCK; data[i] = C_FIRE; break;
                    case 'Z': tiles[i] = QBLOCK; data[i] = C_ICE; break;
                    case 'z': add(new Ents.Golem(ex + 1, ey)); break;
                    case '<': tiles[i] = CONV_L; break;
                    case '>': tiles[i] = CONV_R; break;
                    case 'A': tiles[i] = TIMED_A; break;
                    case 'V': tiles[i] = TIMED_B; break;
                    case 'U': tiles[i] = UBLOCK; break;
                    case 't': add(new Uni.Turtle(ex, ey)); break;
                    case 'a': add(new Uni.Paper(ex, ey)); break;
                    case 'j': add(new Uni.Book(ex, ey)); break;
                    case 'c': add(new Uni.Vending(ex, ey)); break;
                    case 'd': add(new Uni.Drone(ex, ey)); break;
                    case 'y': add(new Uni.Laser(ex, ey)); break;
                    case 'O': add(new Uni.Atom(ex, ey)); break;
                    case 'q': add(new Ents.Cactus(ex, ey)); break;
                    case 'l': add(new Ents.Lizard(ex, ey)); break;
                    case '-': tiles[i] = SEMI; break;
                    case '^': tiles[i] = SPIKE; break;
                    case '~': tiles[i] = LAVA; break;
                    case '%': tiles[i] = LAVA_BODY; break;
                    case 'o': tiles[i] = COIN; break;
                    case 'I': tiles[i] = ICE; break;
                    case 'k': tiles[i] = CANNON; add(new Ents.Cannon(ex, ey)); break;
                    case 'P': spawnX = ex + 3; spawnY = ey + T - 22; break;
                    case 'C': add(new Ents.Checkpoint(ex, ey)); break;
                    case 'G': add(new Ents.Goal(ex, ey)); break;
                    case 'g': gemPos.add(new int[]{c, r}); break;
                    case 's': add(new Ents.Slime(ex, ey, def.world)); break;
                    case 'r': add(new Ents.Hedgehog(ex, ey, def.world)); break;
                    case 'v': add(new Ents.Bat(ex, ey)); break;
                    case 'f': add(new Ents.Frog(ex, ey)); break;
                    case 'p': add(new Ents.Penguin(ex, ey)); break;
                    case 'x': add(new Ents.FireSpirit(ex, ey)); tiles[i] = LAVA; break;
                    case 'i': add(new Ents.Sign(ex, ey, signIdx++)); break;
                    case 'X': add(new Ents.Crusher(ex, ey)); break;
                    case 'T': add(new Ents.Spring(ex, ey)); break;
                    case 'm': add(new Ents.Platform(ex, ey, 1, 48)); break;
                    case 'w': add(new Ents.Platform(ex, ey, 1, 80)); break;
                    case 'n': add(new Ents.Platform(ex, ey, 2, 48)); break;
                    case 'F': add(new Ents.Platform(ex, ey, 3, 0)); break;
                    case 'e': { Ents.Platform pl = new Ents.Platform(ex, ey, 1, 48); pl.phase = (float) Math.PI; add(pl); break; }
                    case 'u': { Ents.Platform pl = new Ents.Platform(ex, ey, 2, 48); pl.phase = (float) Math.PI; add(pl); break; }
                    case 'K': arenaCol = c; break;
                    default: break;
                }
            }
        }
        // gems: sorted left to right, index = bit
        java.util.Collections.sort(gemPos, new java.util.Comparator<int[]>() {
            public int compare(int[] a, int[] b) {
                return Integer.compare(a[0], b[0]);
            }
        });
        for (int[] p : gemPos) {
            boolean had = (game.save.gems(def.index) & (1 << gemIdx)) != 0;
            add(new Ents.Gem(p[0] * T + 2, p[1] * T + 1, gemIdx, had));
            gemIdx++;
        }
        gemCount = gemIdx;
        computeVariants();
        if (def.boss) {
            // boss spawns at arena centre; arena starts at column marked K
            float bx = (arenaCol + 20) * T;
            bossEnt = Bosses.create(def.bossId, bx, (floorRow - 1) * T);
            add(bossEnt);
        }
        flushAdds();
        if (fromCheckpoint && game.checkpointLevel == def.index) {
            spawnX = game.checkpointX;
            spawnY = game.checkpointY;
            checkpoint = true;
            for (Entity e : ents) if (e instanceof Ents.Checkpoint && Math.abs(e.x - spawnX) < 40) ((Ents.Checkpoint) e).on = true;
        }
        player = new Player(this, game.hero(), spawnX, spawnY);
        camX = Math.max(0, Math.min(tw * T - game.W, player.cx() - game.W / 2f));
        if (tw * T < game.W) camX = (tw * T - game.W) / 2f;
        if (def.autoscroll > 0) camX = Math.max(0, Math.min(tw * T - game.W, player.x - 40));
        camY = Math.max(0, Math.min(th * T - game.H, player.y - game.H * 0.55f));
        banner = def.code + "  " + def.name;
        bannerT = 150;
    }

    void add(Entity e) {
        e.L = this;
        adds.add(e);
    }

    public void spawn(Entity e) {
        add(e);
    }

    private void flushAdds() {
        ents.addAll(adds);
        adds.clear();
    }

    // ----------------------------------------------------------------- tiles
    public byte tile(int c, int r) {
        if (c < 0 || c >= tw) return STONE; // side walls
        if (r < 0) return EMPTY;
        if (r >= th) return EMPTY;
        byte t = tiles[r * tw + c];
        if (t == TIMED_A && !phaseA) return EMPTY;
        if (t == TIMED_B && phaseA) return EMPTY;
        return t;
    }

    /** Timed blocks: 'A' blocks are solid in phase A, 'B' blocks in the other phase. */
    public boolean phaseA = true;
    public static final int PHASE = 150;
    /** gravity multiplier (a physics professor can double it) */
    public float gravMul = 1f;

    private void updatePhase() {
        boolean pa = (frame / PHASE) % 2 == 0;
        if (pa == phaseA) return;
        phaseA = pa;
        Sfx.play(Sfx.SELECT);
        // a block appearing inside the hero pushes him up on top of it
        if (player != null && !player.dying) {
            int l = (int) Math.floor(player.x / T), r = (int) Math.floor((player.right() - 0.01f) / T);
            int tp = (int) Math.floor(player.y / T), bt = (int) Math.floor((player.bottom() - 0.01f) / T);
            for (int rr = tp; rr <= bt; rr++)
                for (int cc = l; cc <= r; cc++)
                    if (solid(tile(cc, rr))) {
                        if (solid(tile(cc, rr - 1)) || solid(tile(cc, rr + 1)) && rr < bt) {
                            // a timed door closing on the hero: push him out sideways
                            float mid = cc * T + T / 2f;
                            if (player.cx() < mid && !solid(tile(cc - 1, rr))) player.x = cc * T - player.w - 0.5f;
                            else player.x = (cc + 1) * T + 0.5f;
                        } else {
                            player.y = rr * T - player.h;
                            player.vy = Math.min(player.vy, 0);
                        }
                        return;
                    }
        }
    }

    public void setTile(int c, int r, byte t) {
        if (c < 0 || c >= tw || r < 0 || r >= th) return;
        tiles[r * tw + c] = t;
        computeVariantsAround(c, r);
    }

    public static boolean solid(byte t) {
        return t == GROUND || t == STONE || t == BRICK || t == QBLOCK || t == USED || t == SPIKE || t == ICE || t == CANNON || t == GATE
                || t == CONV_L || t == CONV_R || t == TIMED_A || t == TIMED_B || t == UBLOCK;
    }

    public boolean solidAt(float px, float py) {
        return solid(tile((int) Math.floor(px / T), (int) Math.floor(py / T)));
    }

    private void computeVariants() {
        for (int r = 0; r < th; r++)
            for (int c = 0; c < tw; c++) computeVariant(c, r);
    }

    private void computeVariantsAround(int c, int r) {
        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++) {
                int cc = c + dc, rr = r + dr;
                if (cc >= 0 && rr >= 0 && cc < tw && rr < th) computeVariant(cc, rr);
            }
    }

    private boolean groundLike(int c, int r, boolean outsideSolid) {
        if (c < 0 || c >= tw || r >= th) return outsideSolid;
        if (r < 0) return false;
        byte t = tiles[r * tw + c];
        return t == GROUND || t == STONE || t == ICE || t == UBLOCK;
    }

    private void computeVariant(int c, int r) {
        int i = r * tw + c;
        if (tiles[i] == GROUND) {
            int m = 0;
            if (groundLike(c, r - 1, false)) m |= 1;
            if (groundLike(c + 1, r, true)) m |= 2;
            if (groundLike(c, r + 1, true)) m |= 4;
            if (groundLike(c - 1, r, true)) m |= 8;
            variant[i] = (byte) m;
        } else if (tiles[i] == SEMI) {
            boolean l = c > 0 && tiles[i - 1] == SEMI, rr = c < tw - 1 && tiles[i + 1] == SEMI;
            variant[i] = (byte) (!l ? 0 : !rr ? 2 : 1);
        } else if (tiles[i] == LAVA_BODY) {
            variant[i] = (byte) (r > 0 && tiles[i - tw] != LAVA && tiles[i - tw] != LAVA_BODY ? 1 : 0);
        }
    }

    // ------------------------------------------------------------- collision
    /** Moves an entity with tile collision (x then y). */
    public void move(Entity e, float dx, float dy) {
        e.hitL = e.hitR = e.hitCeil = false;
        e.onGround = false;
        moveX(e, dx);
        moveY(e, dy);
    }

    public void moveX(Entity e, float dx) {
        e.x += dx;
        if (!e.solidTiles || dx == 0) return;
        int top = (int) Math.floor(e.y / T), bot = (int) Math.floor((e.y + e.h - 0.01f) / T);
        if (dx > 0) {
            int col = (int) Math.floor((e.x + e.w - 0.01f) / T);
            for (int r = top; r <= bot; r++)
                if (solid(tile(col, r))) {
                    e.x = col * T - e.w;
                    e.hitR = true;
                    if (e == player) player.touchTile(col, r);
                    break;
                }
        } else {
            int col = (int) Math.floor(e.x / T);
            for (int r = top; r <= bot; r++)
                if (solid(tile(col, r))) {
                    e.x = (col + 1) * T;
                    e.hitL = true;
                    if (e == player) player.touchTile(col, r);
                    break;
                }
        }
    }

    public int lastBumpCol = -1, lastBumpRow = -1;
    public byte groundTile;

    public void moveY(Entity e, float dy) {
        float oldBottom = e.y + e.h;
        e.y += dy;
        if (!e.solidTiles) return;
        int l = (int) Math.floor(e.x / T), r = (int) Math.floor((e.x + e.w - 0.01f) / T);
        if (dy >= 0) {
            int row = (int) Math.floor((e.y + e.h) / T);
            for (int c = l; c <= r; c++) {
                byte t = tile(c, row);
                if (solid(t) || (t == SEMI && oldBottom <= row * T + 0.5f)) {
                    e.y = row * T - e.h;
                    e.vy = 0;
                    e.onGround = true;
                    if (e == player) {
                        groundTile = t;
                        if (t == SPIKE) player.hurt(e.cx(), true);
                    }
                    break;
                }
            }
        } else {
            int row = (int) Math.floor(e.y / T);
            int best = -1;
            float bestD = 99;
            for (int c = l; c <= r; c++)
                if (solid(tile(c, row))) {
                    float d = Math.abs((c * T + T / 2f) - e.cx());
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
            if (best >= 0) {
                e.y = (row + 1) * T;
                e.vy = 0;
                e.hitCeil = true;
                if (e == player) {
                    hitBlock(best, row, true);
                }
            }
        }
    }

    // --------------------------------------------------------- block actions
    public void hitBlock(int c, int r, boolean fromBelow) {
        byte t = tile(c, r);
        int i = r * tw + c;
        if (c < 0 || c >= tw || r < 0 || r >= th) return;
        if (t == QBLOCK || (t == BRICK && data[i] != 0)) {
            byte content = data[i];
            bump[i] = 10;
            float bx = c * T, by = r * T;
            switch (content) {
                case C_COIN:
                    spawn(new Ents.CoinPop(bx + 3, by - 14));
                    addCoin(1);
                    tiles[i] = USED;
                    break;
                case C_MULTI:
                    spawn(new Ents.CoinPop(bx + 3, by - 14));
                    addCoin(1);
                    if (++variant[i] >= 6) tiles[i] = USED;
                    break;
                case C_HEART:
                    spawn(new Ents.Item(bx, by, Ents.Item.HEART));
                    tiles[i] = USED;
                    Sfx.play(Sfx.SPROUT);
                    break;
                case C_STAR:
                    spawn(new Ents.Item(bx, by, Ents.Item.STAR));
                    tiles[i] = USED;
                    Sfx.play(Sfx.SPROUT);
                    break;
                case C_FIRE:
                case C_ICE:
                    spawn(new Ents.Item(bx, by, content == C_FIRE ? Ents.Item.FIRE : Ents.Item.ICE));
                    tiles[i] = USED;
                    Sfx.play(Sfx.SPROUT);
                    break;
                case C_LIFE:
                    spawn(new Ents.Item(bx, by, Ents.Item.LIFE));
                    tiles[i] = USED;
                    Sfx.play(Sfx.SPROUT);
                    break;
            }
            if (tiles[i] == USED) data[i] = 0;
            bumpKill(c, r);
            computeVariantsAround(c, r);
        } else if (t == BRICK) {
            breakBrick(c, r);
        } else if (fromBelow && solid(t)) {
            Sfx.play(Sfx.BUMP);
        }
    }

    public void breakBrick(int c, int r) {
        int i = r * tw + c;
        tiles[i] = EMPTY;
        computeVariantsAround(c, r);
        float bx = c * T, by = r * T;
        for (int k = 0; k < 4; k++)
            spawn(new Ents.Debris(bx + (k % 2) * 8, by + (k / 2) * 8, (k % 2 == 0 ? -1.2f : 1.2f), k < 2 ? -4.5f : -3f, theme.brick));
        bumpKill(c, r);
        Sfx.play(Sfx.BREAK);
        shake = Math.max(shake, 3);
    }

    private void bumpKill(int c, int r) {
        float bx = c * T, by = r * T;
        for (Entity e : ents)
            if (e.enemy && !e.boss && !e.dead && e.overlaps(bx, by - 4, T, 6)) e.kill(Entity.FALL);
        // coins above get collected
        if (tile(c, r - 1) == COIN) {
            setTile(c, r - 1, EMPTY);
            spawn(new Ents.CoinPop(bx + 3, by - 16));
            addCoin(1);
        }
    }

    public void addCoin(int n) {
        coins += n;
        game.save.totalCoins += n; // coins are kept: they buy upgrades in the shop
        game.save.coinLife += n;
        Sfx.play(Sfx.COIN);
        if (player != null) player.addEnergy(n);
        if (game.save.coinLife >= 100) {
            game.save.coinLife -= 100;
            game.save.lives++;
            Sfx.play(Sfx.LIFE);
            if (player != null) spawn(new Ents.FloatText(player.cx(), player.y - 10, "+1 VITA", 0xFF7CFF7C));
        }
    }

    /** An enemy was defeated by the player: energy for the special move. */
    public void onKill(Ents.Enemy e) {
        if (player != null) player.addEnergy(e.energy);
        game.attemptKills++;
    }

    // ------------------------------------------------------------------ update
    public void update(Input in) {
        frame++;
        if (bannerT > 0) bannerT--;
        if (shake > 0) shake--;
        for (int i = 0; i < bump.length; i++) if (bump[i] > 0) bump[i]--;

        Input pin = (state == PLAY) ? in : noInput;
        input = pin;
        updatePhase();
        // platforms first so riders follow them
        for (Entity e : ents) if (e.platform && !e.dead && active(e)) {
            float ox = e.x, oy = e.y;
            e.update();
            e.pdx = e.x - ox;
            e.pdy = e.y - oy;
            e.t++;
        }
        if (state == CLEAR || state == BOSSWIN) player.autoWalk = true;
        player.update(pin);
        if (def.autoscroll > 0 && state == PLAY && !player.dying) {
            // auto-scrolling level: the left edge of the screen pushes the hero; squeezed against a wall = out
            float left = camX + 2;
            if (player.x < left) {
                player.x = left;
                if (solidAt(player.right() + 0.5f, player.cy())) player.die();
            }
            if (player.right() > camX + game.W - 4) player.x = camX + game.W - 4 - player.w;
        }
        // arena lock
        if (arenaCol >= 0 && !arenaLocked && player.x > (arenaCol + 1) * T) lockArena();

        for (Entity e : ents) {
            if (e.platform || e.dead) continue;
            if (!active(e)) continue;
            e.update();
            e.t++;
        }
        // interactions
        if (!player.dead) {
            for (Entity e : ents) {
                if (e.dead || !active(e)) continue;
                player.interact(e);
            }
            collectTileCoins();
        }
        // remove dead
        for (int i = ents.size() - 1; i >= 0; i--) if (ents.get(i).dead) ents.remove(i);
        flushAdds();

        updateCamera();
        stateT++;
        if (state == PLAY) game.attemptFrames++;
        boolean skip = stateT > 130 && (in.aP || in.cP || !game.touch.taps.isEmpty());
        if (state == DEAD && stateT > 110) game.onPlayerDied();
        if (state == CLEAR && (stateT > 420 || skip)) game.onLevelClear(this);
        if (state == BOSSWIN && (stateT > 460 || skip)) game.onLevelClear(this);
    }

    private boolean active(Entity e) {
        if (e.alwaysActive || e.boss) return true;
        float d = Math.abs(e.cx() - (camX + game.W / 2f));
        if (d < game.W * 0.5f + 64) {
            e.alwaysActive = e.enemy; // once seen, stay alive
            return true;
        }
        return false;
    }

    private void collectTileCoins() {
        Player p = player;
        int l = (int) Math.floor(p.x / T), r = (int) Math.floor((p.right() - 0.01f) / T);
        int tp = (int) Math.floor((p.y - 4) / T), bt = (int) Math.floor((p.bottom() - 0.01f) / T);
        for (int rr = tp; rr <= bt; rr++)
            for (int cc = l; cc <= r; cc++) {
                byte t = tile(cc, rr);
                if (t == COIN) {
                    setTile(cc, rr, EMPTY);
                    addCoin(1);
                    spawn(new Ents.Sparkle(cc * T + 8, rr * T + 8));
                } else if ((t == LAVA || t == LAVA_BODY) && p.bottom() > rr * T + 6) {
                    p.die();
                }
            }
    }

    private void lockArena() {
        arenaLocked = true;
        for (int r = 0; r < th; r++) if (!solid(tile(arenaCol, r))) setTile(arenaCol, r, GATE);
        shake = 12;
        Sfx.play(Sfx.GATE);
        game.audio.music(def.bossId == 9 ? Music.FINAL : Music.BOSS);
        if (bossEnt != null) bossEnt.wake();
        banner = bossEnt != null ? bossEnt.name : "BOSS";
        bannerT = 150;
    }

    private void updateCamera() {
        int W = game.W, H = game.H;
        float minX = 0, maxX = tw * T - W;
        if (arenaLocked) minX = Math.min(maxX, arenaCol * T);
        float targetX = player.cx() - W / 2f + player.facing * 28;
        if (state != PLAY) targetX = camX;
        if (def.autoscroll > 0) {
            if (state == PLAY && frame > 100 && !player.dying) camX += def.autoscroll;
        } else camX += (targetX - camX) * 0.12f;
        camX = Math.max(minX, Math.min(maxX, camX));
        if (maxX < 0) camX = maxX / 2f; // level narrower than the screen: centre it
        float targetY = player.y - H * 0.52f;
        if (player.onGround || player.vy > 3 || player.y - camY > H * 0.7f || player.y - camY < H * 0.25f)
            camY += (targetY - camY) * (player.onGround ? 0.12f : 0.08f);
        camY = Math.max(0, Math.min(th * T - H, camY));
    }

    public void playerDied() {
        if (state == DEAD) return;
        state = DEAD;
        stateT = 0;
        game.audio.music(-1);
        Sfx.play(Sfx.DIE);
    }

    // ----------------------------------------------------------------- grades
    public int grade, gradeScore;
    public boolean newRecord;

    /** Target time in seconds for an S rank. */
    public int parSeconds() {
        if (def.boss) return 95;
        if (th > 22) return 30 + th;
        return 12 + tw / 4;
    }

    private void computeGrade() {
        int secs = game.attemptFrames / 60;
        int par = parSeconds();
        int score = 100;
        if (secs > par) score -= Math.min(45, (secs - par) * 45 / Math.max(1, par));
        score -= game.attemptHits * 12;
        score -= game.attemptDeaths * 20;
        score += Integer.bitCount(gems) * 3;
        gradeScore = score;
        if (secs <= par && game.attemptHits == 0 && game.attemptDeaths == 0) grade = 5;
        else if (score >= 82) grade = 4;
        else if (score >= 64) grade = 3;
        else if (score >= 45) grade = 2;
        else grade = 1;
        newRecord = grade > game.save.grades[def.index];
    }

    public void clear() {
        if (state != PLAY) return;
        computeGrade();
        state = CLEAR;
        stateT = 0;
        game.audio.music(Music.CLEAR);
        banner = "LIVELLO COMPLETATO!";
        bannerT = 999;
    }

    public void bossDefeated() {
        shake = 30;
        game.audio.music(-1);
        Sfx.play(Sfx.BOSSDIE);
        spawn(new Ents.Trophy(camX + game.W / 2f - 7, camY - 20));
        banner = "BOSS SCONFITTO!";
        bannerT = 180;
    }

    public void trophyTaken() {
        if (state != PLAY) return;
        computeGrade();
        state = BOSSWIN;
        stateT = 0;
        game.audio.music(Music.CLEAR);
        banner = def.worldBoss() ? (def.world >= 4 ? Theme.get(def.world).name + " SUPERATO!" : Theme.get(def.world).shortName + " LIBERATA!")
                : "CASTELLO SUPERATO!";
        bannerT = 999;
    }

    public void gemTaken(int idx) {
        gems |= 1 << idx;
        Sfx.play(Sfx.GEM);
        if (player != null) player.addEnergy(20);
    }

    public void checkpointTaken(Ents.Checkpoint c) {
        game.checkpointLevel = def.index;
        game.checkpointX = c.x;
        game.checkpointY = c.y + c.h - 22;
        Sfx.play(Sfx.CHECK);
    }

    // ------------------------------------------------------------------ render
    public void render(Gfx g) {
        int sx = 0, sy = 0;
        if (shake > 0) {
            sx = (frame * 7 % 5) - 2;
            sy = (frame * 13 % 5) - 2;
        }
        theme.stage = def.pos;
        theme.drawBackground(g, camX, camY, th * T, frame);
        g.ox = sx;
        g.oy = sy;
        int cx = Math.round(camX), cy = Math.round(camY);
        int c0 = Math.max(0, cx / T), c1 = Math.min(tw - 1, (cx + g.w) / T + 1);
        int r0 = Math.max(0, cy / T), r1 = Math.min(th - 1, (cy + g.h) / T + 1);
        // decorations behind tiles
        for (int c = c0; c <= c1; c++)
            for (int r = Math.max(1, r0); r <= r1; r++) {
                int i = r * tw + c;
                if (tiles[i] == GROUND && tiles[i - tw] == EMPTY && (variant[i] & 1) == 0) {
                    int hsh = Theme.hash(c, r, def.index);
                    if ((hsh & 7) < 3) {
                        Sprite d = theme.decor[(hsh >>> 4) % 3];
                        g.blit(d, c * T + ((hsh >>> 8) & 7) - cx, r * T - d.h - cy + 1);
                    }
                }
            }
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++) {
                int i = r * tw + c;
                byte t = tiles[i];
                if (t == EMPTY) continue;
                int x = c * T - cx, y = r * T - cy;
                if (bump[i] > 0) y -= (int) (Math.sin(bump[i] / 10.0 * Math.PI) * 5);
                Sprite s = null;
                switch (t) {
                    case GROUND: s = theme.ground[variant[i]]; break;
                    case STONE: s = theme.stone; break;
                    case BRICK: s = theme.brick; break;
                    case QBLOCK: s = data[i] == C_MULTI && variant[i] > 0 ? theme.qblock[0] : theme.qblock[(frame / 8) % 4 == 3 ? 1 : 0]; break;
                    case USED: s = theme.used; break;
                    case SEMI: s = variant[i] == 0 ? theme.semiL : variant[i] == 2 ? theme.semiR : theme.semiM; break;
                    case SPIKE: s = theme.spike; break;
                    case LAVA: s = theme.lavaTop[(frame / 12 + c) % 2]; break;
                    case LAVA_BODY: s = theme.lavaBody; break;
                    case COIN: s = Art.coin[(frame / 6 + c) % 4]; x += 3; y += 1; break;
                    case ICE: s = theme.ice; break;
                    case CANNON: s = Art.cannon[0]; break;
                    case GATE: s = theme.gate; break;
                    case CONV_L: s = theme.conv[(frame / 6) % 2]; break;
                    case CONV_R: s = theme.conv[1 - (frame / 6) % 2]; break;
                    case UBLOCK: s = theme.ublock; break;
                    case TIMED_A:
                    case TIMED_B: {
                        boolean on = (t == TIMED_A) == phaseA;
                        int left = PHASE - frame % PHASE;
                        Sprite ts = t == TIMED_A ? theme.timedA : theme.timedB;
                        if (on) g.blit(ts, x, y, false, left < 40 && (left / 5) % 2 == 0 ? 120 : 255, 0);
                        else {
                            g.blit(ts, x, y, false, left < 40 && (left / 5) % 2 == 0 ? 140 : 45, 0);
                        }
                        continue;
                    }
                }
                g.blit(s, x, y);
            }
        for (Entity e : ents) if (!(e instanceof Ents.Particle)) e.render(g, cx, cy);
        player.render(g, cx, cy);
        for (Entity e : ents) if (e instanceof Ents.Particle) e.render(g, cx, cy);
        // lava glow over everything near the bottom in lava world
        g.ox = 0;
        g.oy = 0;
    }

    private void renderResults(Gfx g) {
        int k = Math.min(255, (stateT - 70) * 12);
        int pw = 220, ph = 112, px = g.w / 2 - pw / 2, py = 84;
        g.fillA(px, py, pw, ph, 0xFF0A0A1A, Math.min(220, k));
        g.rect(px, py, pw, ph, 0xFFFFE070);
        int secs = game.attemptFrames / 60, par = parSeconds();
        String tm = (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
        String pt = (par / 60) + ":" + (par % 60 < 10 ? "0" : "") + (par % 60);
        int ty = py + 10;
        if (stateT > 85) g.textO("TEMPO   " + tm + "  (S: " + pt + ")", px + 10, ty, secs <= par ? 0xFF7CFF7C : 0xFFFFFFFF, 0xFF000000, 1);
        if (stateT > 100) g.textO("DANNI   " + game.attemptHits, px + 10, ty + 14, game.attemptHits == 0 ? 0xFF7CFF7C : 0xFFFFFFFF, 0xFF000000, 1);
        if (stateT > 115) g.textO("CADUTE  " + game.attemptDeaths, px + 10, ty + 28, game.attemptDeaths == 0 ? 0xFF7CFF7C : 0xFFFFFFFF, 0xFF000000, 1);
        if (stateT > 130) g.textO("NEMICI  " + game.attemptKills + "    MONETE " + coins, px + 10, ty + 42, 0xFFFFFFFF, 0xFF000000, 1);
        if (stateT > 145) g.textO("GEMME   " + Integer.bitCount(gems) + "/" + gemCount, px + 10, ty + 56, 0xFF8AD0FF, 0xFF000000, 1);
        if (stateT > 170) {
            int[] cols = {0xFFFFFFFF, 0xFFB0B0C0, 0xFF8AD0FF, 0xFF7CFF7C, 0xFFFFB040, 0xFFFFE040};
            String gl = Save.GRADE_NAMES[grade];
            int sc = stateT < 185 ? 7 - (stateT - 170) / 5 : 4;
            g.textO("GRADO", px + pw - 52, py + 12, 0xFFFFFFFF, 0xFF000000, 1);
            g.textC(gl, px + pw - 37, py + 30, cols[grade], 0xFF000000, Math.max(4, sc));
            if (newRecord && (frame / 10) % 2 == 0) g.textC("NUOVO RECORD!", px + pw / 2, py + ph - 14, 0xFFFFE070, 0xFF000000, 1);
        }
        if (stateT > 130 && (frame / 20) % 2 == 0) g.textC("A: CONTINUA", g.w / 2, py + ph + 6, 0xFFC0C0D8, 0xFF000000, 1);
    }

    public void renderHud(Gfx g) {
        Player p = player;
        // hearts + flower power
        for (int i = 0; i < Player.MAX_HP; i++) g.blit(i < p.hp ? Art.hudHeart : Art.hudHeartEmpty, 4 + i * 12, 4);
        if (p.power != Combat.NONE) g.blit(p.power == Combat.FIRE ? Art.fireFlower : Art.iceFlower, 41, 2);
        // lives with hero face, coins
        Sprite face = p.hero.icon;
        g.blit(face, 4, 17);
        g.textO("x" + game.save.lives, 21, 22, 0xFFFFFFFF, 0xFF000000, 1);
        g.blit(Art.hudCoin, 44, 18);
        g.textO("" + game.save.totalCoins, 57, 22, 0xFFFFE070, 0xFF000000, 1);
        // energy bar for the special move
        {
            int ex = 4, ey = 34, ew = 62;
            boolean full = p.energy >= Player.ENERGY_MAX;
            g.blit(Art.hudEnergy, ex, ey - 1);
            g.fill(ex + 9, ey, ew + 2, 7, 0xFF000000);
            g.fill(ex + 10, ey + 1, ew, 5, 0xFF2A2440);
            int fw = (int) (ew * p.energy / Player.ENERGY_MAX);
            int col = full ? ((frame / 6) % 2 == 0 ? 0xFFFFE070 : 0xFFFFFFFF) : 0xFFE0A030;
            g.fill(ex + 10, ey + 1, fw, 5, col);
            g.fill(ex + 10, ey + 1, fw, 1, 0xFFFFF4C0);
            if (full) g.textO("S!", ex + ew + 14, ey - 1, 0xFFFFE070, 0xFF000000, 1);
        }
        // ability charges / reload
        {
            int bx = 4, by = 45;
            if (p.maxCharges > 0) {
                Sprite on = p.hero.ability_ == Hero.GUN ? Art.hudBullet : p.hero.ability_ == Hero.COIN ? Art.coinShot : Art.soccer;
                Sprite off = on.silhouette(0xFF4A4A5A);
                for (int i = 0; i < p.maxCharges; i++) g.blit(i < p.charges ? on : off, bx + i * 11, by);
                bx += p.maxCharges * 11 + 2;
            }
            float rf = p.reloadFrac();
            if (rf > 0) {
                g.fill(bx, by + 2, 32, 6, 0xFF000000);
                g.fill(bx + 1, by + 3, 30, 4, 0xFF303048);
                g.fill(bx + 1, by + 3, (int) (30 * (1 - rf)), 4, 0xFFFFD040);
            }
        }
        // gems
        for (int i = 0; i < gemCount; i++) {
            boolean got = (gems & (1 << i)) != 0 || (game.save.gems(def.index) & (1 << i)) != 0;
            g.blit(got ? Art.hudGem : Art.hudGemEmpty, g.w / 2 - 22 + i * 15, 4);
        }
        // timer (top right, under the pause button)
        if (state == PLAY) {
            int secs = game.attemptFrames / 60;
            String tm = (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
            g.textO(tm, g.w - 34 - Gfx.textW(tm, 1), 10, secs <= parSeconds() ? 0xFFFFFFFF : 0xFFFF9A7A, 0xFF000000, 1);
        }
        // boss bar
        if (bossEnt != null && arenaLocked && !bossEnt.dead && bossEnt.hp > 0) {
            int bw = 120, bx = g.w / 2 - bw / 2, by = 22;
            g.fill(bx - 1, by - 1, bw + 2, 7, 0xFF000000);
            g.fill(bx, by, bw, 5, 0xFF401018);
            g.fill(bx, by, bw * bossEnt.hp / bossEnt.maxHp, 5, 0xFFE83A3A);
            g.fill(bx, by, bw * bossEnt.hp / bossEnt.maxHp, 1, 0xFFFF9A8A);
            g.textC(bossEnt.name, g.w / 2, by + 8, 0xFFFFFFFF, 0xFF000000, 1);
        }
        if ((state == CLEAR || state == BOSSWIN) && stateT > 70) renderResults(g);
        if (hintT > 0 && hint != null && state == PLAY) {
            int a = Math.min(220, hintT * 20);
            int tw2 = Gfx.textW(hint, 1) + 12;
            int hx = g.w / 2 - tw2 / 2, hy = 31;
            g.fillA(hx, hy, tw2, 15, 0xFF101830, a);
            g.rect(hx, hy, tw2, 15, 0xFFFFE070);
            g.text(hint, hx + 6, hy + 4, 0xFFFFFFFF, 1, Math.min(255, hintT * 25));
        }
        if (hintT > 0) hintT--;
        if (bannerT > 0 && banner != null) {
            int a = Math.min(255, bannerT * 8);
            int y = state == PLAY ? 58 : 70;
            g.fillA(0, y - 6, g.w, 21, 0xFF000000, a / 2);
            g.textC(banner, g.w / 2, y, 0xFFFFFFFF, 0xFF000000, state == PLAY ? 1 : 2);
        }
    }
}
