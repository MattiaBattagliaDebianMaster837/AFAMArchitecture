package it.pixelquest.core;

/** Top-level game: screens, flow between them and persistence. Platform independent. */
public final class Game {
    public static final int TITLE = 0, SELECT = 1, MAP = 2, PLAY = 3, PAUSE = 4, GAMEOVER = 5, ENDING = 6, MENU = 7, SHOP = 8, STORY = 9;

    public int W, H;
    public Gfx gfx;
    public final Input input = new Input();
    public final TouchControls touch = new TouchControls();
    public final Audio audio;
    public final Save save;
    public int screen = TITLE, screenT;
    public Level level;
    public int frame;
    public volatile boolean wantsExit;
    private final java.util.concurrent.atomic.AtomicInteger backPresses = new java.util.concurrent.atomic.AtomicInteger();

    // transitions
    private int fade, fadeDir, nextScreen = -1, nextParam;
    // statistics of the current attempt (for the grade) and things carried between levels
    public int attemptFrames, attemptHits, attemptDeaths, attemptKills;
    public int carryPower;
    public float carryEnergy;
    private boolean restarting;
    // checkpoint memory
    public int checkpointLevel = -1;
    public float checkpointX, checkpointY;

    private int selCursor;
    private int pauseCursor;
    private final WorldMap map;
    private Theme titleTheme;

    public Game(int w, int h, Save.Storage storage) {
        Hero.init();
        Art.init();
        UniArt.init();
        audio = new Audio();
        save = new Save(storage);
        audio.musicOn = save.music;
        audio.sfxOn = save.sfx;
        touch.mode = save.controls;
        Music.prewarm();
        resize(w, h);
        map = new WorldMap(this);
        selCursor = save.hero;
        titleTheme = Theme.get(0);
        audio.music(Music.TITLE);
    }

    /** Physical screen size changed: pick a virtual resolution with fixed height 224. */
    public void resizePhysical(int pw, int ph) {
        int vh = 224;
        int vw = Math.round(vh * pw / (float) ph);
        vw = Math.max(360, Math.min(540, vw));
        resize(vw, vh);
    }

    public void resize(int w, int h) {
        if (gfx != null && w == W && h == H) return;
        W = w;
        H = h;
        gfx = new Gfx(w, h);
        touch.W = w;
        touch.H = h;
    }

    /** Tutorial hints adapt to the chosen control scheme. */
    public String hintText(String h) {
        if (save.controls == TouchControls.ARROWS && h.startsWith("LEVETTA"))
            return "FRECCE: CAMMINA. TIENILE PREMUTE PER CORRERE!";
        return h;
    }

    public Hero hero() {
        return Hero.ALL[save.hero];
    }

    // ------------------------------------------------------------------ flow
    void go(int scr, int param) {
        if (nextScreen >= 0) return;
        nextScreen = scr;
        nextParam = param;
        fadeDir = 1;
    }

    private void doGo(int scr, int param) {
        screen = scr;
        screenT = 0;
        input.consume();
        switch (scr) {
            case TITLE:
                audio.music(Music.TITLE);
                break;
            case SELECT:
                selCursor = save.hero;
                audio.music(Music.TITLE);
                break;
            case SHOP:
                shopHero = save.hero;
                shopRow = 0;
                break;
            case STORY:
                storyPage = 0;
                storyChapter = Math.max(0, Math.min(STORIES.length - 1, param));
                audio.music(storyChapter == 0 ? Music.W1 : Music.W5);
                break;
            case MAP:
                if (!save.seenIntro) { // the very first time: the story of the journey before the map
                    save.seenIntro = true;
                    save.store();
                    doGo(STORY, 0);
                    return;
                }
                level = null;
                map.enter();
                audio.music(Music.MAP);
                break;
            case PLAY:
                startLevel(param);
                break;
            case GAMEOVER:
                level = null;
                audio.music(Music.GAMEOVER);
                break;
            case ENDING:
                level = null;
                audio.music(Music.ENDING);
                break;
        }
    }

    private void startLevel(int index) {
        LevelDef def = Levels.get(index);
        if (!restarting) {
            attemptFrames = attemptHits = attemptDeaths = attemptKills = 0;
        }
        restarting = false;
        boolean fromCk = checkpointLevel == index;
        if (checkpointLevel != index) checkpointLevel = -1;
        level = new Level(this, def, fromCk);
        audio.music(def.music);
        audio.restartMusic();
    }

    public void onPlayerDied() {
        if (level == null || nextScreen >= 0) return;
        save.lives--;
        attemptDeaths++;
        carryPower = Combat.NONE;
        carryEnergy = level.player.energy * 0.5f;
        if (save.lives <= 0) {
            save.lives = 5;
            checkpointLevel = -1;
            save.store();
            go(GAMEOVER, 0);
        } else {
            save.store();
            restarting = true;
            go(PLAY, level.def.index);
        }
    }

    public void onLevelClear(Level l) {
        if (nextScreen >= 0) return;
        int idx = l.def.index;
        if (l.grade > save.grades[idx]) save.grades[idx] = l.grade;
        carryPower = l.player.power;
        carryEnergy = l.player.energy;
        save.cleared |= 1L << idx;
        save.gems[idx] |= l.gems;
        boolean first = save.unlocked <= idx;
        if (save.unlocked < idx + 1 && idx + 1 < Save.LEVELS) save.unlocked = idx + 1;
        checkpointLevel = -1;
        save.store();
        if (idx == Save.LEVELS - 1) {
            go(ENDING, 0);
            return;
        }
        if (idx == LevelDef.PROLOGUE_LEVELS - 1 && first) {
            map.pendingAdvance = true;
            go(STORY, 1);
            return;
        }
        map.pendingAdvance = first;
        go(MAP, 0);
    }

    /** Hardware back button. Returns true if handled. */
    public void onBack() {
        backPresses.incrementAndGet();
    }

    /** App went to background. */
    public void onPauseApp() {
        if (screen == PLAY && level != null && level.state == Level.PLAY) {
            screen = PAUSE;
            pauseCursor = 0;
        }
        touch.cancelAll();
        save.store();
    }

    // ------------------------------------------------------------------ tick
    public void tick() {
        frame++;
        screenT++;
        touch.enabled = screen == PLAY || screen == MAP;
        touch.showS = screen == PLAY && level != null;
        touch.sFill = level != null ? level.player.energy / Player.ENERGY_MAX : 0;
        touch.poll();
        input.latch(touch);
        input.backP = backPresses.getAndSet(0) > 0;

        if (fadeDir != 0) {
            fade += fadeDir * 34;
            if (fade >= 255) {
                fade = 255;
                fadeDir = -1;
                int s = nextScreen;
                nextScreen = -1;
                doGo(s, nextParam);
            } else if (fade <= 0) {
                fade = 0;
                fadeDir = 0;
            }
            if (fadeDir == 1) return;
        }

        switch (screen) {
            case TITLE: updateTitle(); break;
            case SELECT: updateSelect(); break;
            case MAP: map.update(); break;
            case PLAY: updatePlay(); break;
            case PAUSE: updatePause(); break;
            case MENU: updatePause(); break;
            case SHOP: updateShop(); break;
            case STORY:
                if (screenT > 40 && (input.aP || input.bP || !touch.taps.isEmpty() || input.backP)) {
                    screenT = 0;
                    Sfx.play(Sfx.SELECT);
                    if (++storyPage >= STORIES[storyChapter].length) go(MAP, 0);
                }
                break;
            case GAMEOVER:
                if (screenT > 90 && (input.aP || !touch.taps.isEmpty() || input.backP)) go(MAP, 0);
                break;
            case ENDING:
                if (screenT > 240 && (input.aP || !touch.taps.isEmpty() || input.backP)) go(MAP, 0);
                break;
        }
    }

    private void updateTitle() {
        if (input.backP) {
            wantsExit = true;
            return;
        }
        if (screenT > 20 && (input.aP || input.bP || input.cP || !touch.taps.isEmpty())) {
            Sfx.play(Sfx.CONFIRM);
            go(SELECT, 0);
        }
    }

    private void updateSelect() {
        if (input.backP) {
            go(TITLE, 0);
            return;
        }
        int n = Hero.ALL.length;
        if (input.leftP) {
            selCursor = (selCursor + n - 1) % n;
            Sfx.play(Sfx.SELECT);
        }
        if (input.rightP) {
            selCursor = (selCursor + 1) % n;
            Sfx.play(Sfx.SELECT);
        }
        boolean confirm = input.aP;
        for (float[] tp : touch.taps) {
            float x = tp[0], y = tp[1];
            if (y > 190) { // portrait strip
                int k = portraitAt(x);
                if (k >= 0) {
                    if (k == selCursor) confirm = true;
                    else {
                        selCursor = k;
                        Sfx.play(Sfx.SELECT);
                    }
                }
            } else if (x < W / 2f - 130) {
                selCursor = (selCursor + n - 1) % n;
                Sfx.play(Sfx.SELECT);
            } else if (x > W / 2f + 130) {
                selCursor = (selCursor + 1) % n;
                Sfx.play(Sfx.SELECT);
            } else if (y > 26) confirm = true;
        }
        if (confirm && screenT > 10) {
            save.hero = selCursor;
            save.store();
            Sfx.play(Sfx.CONFIRM);
            go(MAP, 0);
        }
    }

    private int portraitAt(float x) {
        int n = Hero.ALL.length;
        int x0 = W / 2 - n * 24 / 2;
        int k = (int) Math.floor((x - x0) / 24);
        return k >= 0 && k < n ? k : -1;
    }

    private void updatePlay() {
        if (input.pauseP || input.backP) {
            if (level.state == Level.PLAY) {
                screen = PAUSE;
                pauseCursor = 0;
                Sfx.play(Sfx.PAUSE);
                input.consume();
                return;
            }
        }
        level.update(input);
    }

    // menu item ids
    private static final int I_CONTINUE = 0, I_CONTROLS = 1, I_VIBRATION = 2, I_MUSIC = 3, I_SFX = 4, I_EXIT_LEVEL = 5,
            I_HERO = 6, I_TITLE = 7, I_SHOP = 8;

    private int[] menuIds() {
        if (screen == MENU) return new int[]{I_CONTINUE, I_SHOP, I_HERO, I_CONTROLS, I_VIBRATION, I_MUSIC, I_SFX, I_TITLE};
        return new int[]{I_CONTINUE, I_CONTROLS, I_VIBRATION, I_MUSIC, I_SFX, I_EXIT_LEVEL};
    }

    private String menuLabel(int id) {
        switch (id) {
            case I_CONTINUE: return "CONTINUA";
            case I_CONTROLS: return "COMANDI: " + (save.controls == TouchControls.ARROWS ? "FRECCE" : "LEVETTA");
            case I_VIBRATION: return "VIBRAZIONE: " + (save.vibration ? "SI" : "NO");
            case I_MUSIC: return "MUSICA: " + (save.music ? "SI" : "NO");
            case I_SFX: return "EFFETTI: " + (save.sfx ? "SI" : "NO");
            case I_EXIT_LEVEL: return "ESCI DAL LIVELLO";
            case I_HERO: return "CAMBIA PERSONAGGIO";
            case I_SHOP: return "POTENZIAMENTI (NEGOZIO)";
            default: return "SCHERMATA DEL TITOLO";
        }
    }

    /** Opens the map menu (options). */
    void openMapMenu() {
        screen = MENU;
        pauseCursor = 0;
        input.consume();
        Sfx.play(Sfx.PAUSE);
    }

    private void closeMenu() {
        screen = screen == MENU ? MAP : PLAY;
        input.consume();
        Sfx.play(Sfx.PAUSE);
    }

    private void updatePause() {
        int[] ids = menuIds();
        if (input.upP) {
            pauseCursor = (pauseCursor + ids.length - 1) % ids.length;
            Sfx.play(Sfx.SELECT);
        }
        if (input.downP) {
            pauseCursor = (pauseCursor + 1) % ids.length;
            Sfx.play(Sfx.SELECT);
        }
        int chosen = -1;
        if (input.aP) chosen = ids[pauseCursor];
        if (input.pauseP || input.backP || input.bP) chosen = I_CONTINUE;
        for (float[] tp : touch.taps) {
            for (int i = 0; i < ids.length; i++) {
                int y = pauseItemY(i);
                if (tp[1] >= y - 5 && tp[1] < y + 13 && Math.abs(tp[0] - W / 2f) < 100) {
                    pauseCursor = i;
                    chosen = ids[i];
                }
            }
        }
        if (chosen < 0) return;
        switch (chosen) {
            case I_CONTINUE:
                closeMenu();
                break;
            case I_CONTROLS:
                save.controls = save.controls == TouchControls.ARROWS ? TouchControls.STICK : TouchControls.ARROWS;
                touch.mode = save.controls;
                touch.cancelAll();
                save.store();
                Sfx.play(Sfx.SELECT);
                break;
            case I_VIBRATION:
                save.vibration = !save.vibration;
                save.store();
                Sfx.play(Sfx.SELECT);
                break;
            case I_MUSIC:
                save.music = !save.music;
                audio.musicOn = save.music;
                save.store();
                break;
            case I_SFX:
                save.sfx = !save.sfx;
                audio.sfxOn = save.sfx;
                save.store();
                Sfx.play(Sfx.SELECT);
                break;
            case I_EXIT_LEVEL:
                carryPower = level.player.power;
                carryEnergy = level.player.energy;
                checkpointLevel = -1;
                go(MAP, 0);
                break;
            case I_HERO:
                Sfx.play(Sfx.CONFIRM);
                go(SELECT, 0);
                break;
            case I_TITLE:
                go(TITLE, 0);
                break;
            case I_SHOP:
                Sfx.play(Sfx.CONFIRM);
                go(SHOP, 0);
                break;
        }
    }

    private int pauseItemY(int i) {
        return 56 + i * 18;
    }

    // ------------------------------------------------------------------ render
    public void render() {
        Gfx g = gfx;
        switch (screen) {
            case TITLE: renderTitle(g); break;
            case SELECT: renderSelect(g); break;
            case MAP: map.render(g); break;
            case PLAY: renderPlay(g, true); break;
            case PAUSE: renderPause(g); break;
            case MENU: renderPause(g); break;
            case SHOP: renderShop(g); break;
            case STORY: renderStory(g); break;
            case GAMEOVER: renderGameOver(g); break;
            case ENDING: renderEnding(g); break;
        }
        if (fade > 0) g.fillA(0, 0, W, H, 0xFF000000, fade);
    }

    private void renderPlay(Gfx g, boolean controls) {
        level.render(g);
        level.renderHud(g);
        if (controls && level.state == Level.PLAY) {
            touch.visible = true;
            touch.draw(g, level.player.carrying != null ? "CALCIA" : hero().cLabel(), true, true);
        }
    }

    private void renderPause(Gfx g) {
        boolean mapMenu = screen == MENU;
        if (mapMenu) map.render(g);
        else renderPlay(g, false);
        g.dim(0xFF000010, 160);
        g.textC(mapMenu ? "MENU" : "PAUSA", W / 2, 24, 0xFFFFE070, 0xFF000000, 3);
        int[] ids = menuIds();
        for (int i = 0; i < ids.length; i++) {
            int y = pauseItemY(i);
            boolean sel = i == pauseCursor;
            if (sel) {
                g.fillA(W / 2 - 96, y - 5, 192, 17, 0xFFFFFFFF, 40);
                g.text("\u0004", W / 2 - 90, y, 0xFFFFE070);
            }
            g.textC(menuLabel(ids[i]), W / 2, y, sel ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000, 1);
        }
        String foot;
        if (ids[pauseCursor] == I_CONTROLS)
            foot = save.controls == TouchControls.ARROWS ? "FRECCE: TIENI PREMUTO PER CORRERE" : "LEVETTA: SPINGI AL BORDO PER CORRERE";
        else foot = mapMenu ? "B: CHIUDI" : level.def.code + " " + level.def.name;
        g.textC(foot, W / 2, H - 16, 0xFFAAB4D0, 0xFF000000, 1);
    }

    private void renderTitle(Gfx g) {
        float cam = frame * 0.6f;
        titleTheme.drawBackground(g, cam, 0, H, frame);
        // ground strip
        Theme th = titleTheme;
        int gy = H - 40;
        for (int x = -16; x < W + 16; x += 16) {
            int ox = x - ((int) cam % 16);
            g.blit(th.ground[2 | 8], ox, gy);
            g.blit(th.ground[1 | 2 | 8], ox, gy + 16);
            g.blit(th.ground[1 | 2 | 8], ox, gy + 32);
        }
        // logo
        int bob = (int) Math.round(Math.sin(frame * 0.05) * 3);
        String t1 = "PIXEL", t2 = "QUEST";
        g.textC(t1, W / 2 + 3, 26 + bob + 3, 0xFF3A1A08, 0xFF3A1A08, 5);
        g.textC(t1, W / 2, 26 + bob, 0xFFFFD040, 0xFF5A2A08, 5);
        g.textC(t2, W / 2 + 3, 66 + bob + 3, 0xFF3A1A08, 0xFF3A1A08, 5);
        g.textC(t2, W / 2, 66 + bob, 0xFFFF7A3A, 0xFF5A1A08, 5);
        g.textC("MATTIA & GIUSEPPE", W / 2, 112, 0xFFFFFFFF, 0xFF1A1A3A, 2);
        // heroes running
        for (int i = 0; i < 2; i++) {
            Hero h = Hero.ALL[i];
            Sprite s = h.frames[HeroArt.RUN0 + (frame / 5 + i * 2) % 4];
            g.blit(s.scaled(2), W / 2 - 70 + i * 90, gy - 56 + (i == 0 ? 0 : 0));
        }
        // slime chasing for fun
        g.blit(Art.slime[0][(frame / 12) % 2].scaled(2), W / 2 + 70, gy - 22);
        if ((frame / 30) % 2 == 0) g.textC("TOCCA PER INIZIARE", W / 2, H - 14, 0xFFFFFFFF, 0xFF000000, 1);
        g.text("V1.6", W - 28, H - 10, 0xFFFFFFFF, 1, 160);
    }

    private void renderSelect(Gfx g) {
        for (int y = 0; y < H; y++) g.fill(0, y, W, 1, Gfx.mix(0xFF1A1440, 0xFF3A2A6A, y / (float) H));
        for (int k = 0; k < 40; k++) {
            int sx = (k * 97 + frame / 3) % W, sy = (k * 53) % H;
            g.pset(sx, sy, (k + frame / 10) % 5 == 0 ? 0xFFFFFFFF : 0xFF8A80C0);
        }
        g.textC("SCEGLI IL PERSONAGGIO", W / 2, 6, 0xFFFFE070, 0xFF000000, 2);
        Hero h = Hero.ALL[selCursor];
        int cw = 260, ch = 160, cx = W / 2 - cw / 2, cy = 26;
        g.fillA(cx, cy, cw, ch, 0xFF2A2A5A, 225);
        g.rect(cx, cy, cw, ch, 0xFFFFD040);
        g.rect(cx + 1, cy + 1, cw - 2, ch - 2, 0xFFFFD040);
        // arrows
        int bob = (frame / 15) % 2;
        g.textC("<", cx - 16 - bob, cy + ch / 2 - 10, 0xFFFFE070, 0xFF000000, 3);
        g.textC(">", cx + cw + 16 + bob, cy + ch / 2 - 10, 0xFFFFE070, 0xFF000000, 3);
        // animated preview
        Sprite s = h.frames[(screenT / 40) % 3 == 2 ? HeroArt.ACT1 : HeroArt.RUN0 + (frame / 5) % 4];
        Sprite big = s.scaled(2);
        g.fillA(cx + 6, cy + 6, 50, 62, 0xFF000000, 60);
        g.blit(big, cx + 31 - big.w / 2, cy + 8);
        int tx = cx + 62;
        String[] nm = h.name.split(" ");
        g.textO(nm[0], tx, cy + 8, 0xFFFFFFFF, 0xFF000000, 1);
        g.textO(nm.length > 1 ? nm[1] : "", tx, cy + 18, 0xFFFFFFFF, 0xFF000000, 1);
        int u = save.upgrade(h.id);
        g.textO("LV " + (u + 1), cx + cw - 30, cy + 8, 0xFF7CFF7C, 0xFF000000, 1);
        stat(g, tx, cy + 32, "VELOCIT\u00c0", h.statSpeed, cw - 66);
        stat(g, tx, cy + 43, "SALTO", h.statJump, cw - 66);
        stat(g, tx, cy + 54, "POTENZA", h.statPower, cw - 66);
        g.textO("C: " + h.ability, cx + 6, cy + 74, 0xFFFF9A5A, 0xFF000000, 1);
        g.text(h.abilityDesc, cx + 6, cy + 86, 0xFFE0E0F0);
        g.textO("\u0006 SPECIALE: " + h.special, cx + 6, cy + 112, 0xFFFFE070, 0xFF000000, 1);
        g.text(h.specialDesc, cx + 6, cy + 124, 0xFFE0E0F0);
        // portraits
        int n = Hero.ALL.length, x0 = W / 2 - n * 24 / 2;
        for (int i = 0; i < n; i++) {
            int px = x0 + i * 24;
            if (i == selCursor) g.fillA(px, 190, 22, 20, 0xFFFFE070, 120);
            g.blit(Hero.ALL[i].icon, px + 3, 192);
        }
        if ((frame / 25) % 2 == 0) g.textC("A / TOCCA LA SCHEDA: CONFERMA", W / 2, H - 11, 0xFFFFFFFF, 0xFF000000, 1);
    }

    private void stat(Gfx g, int x, int y, String label, int v, int width) {
        g.text(label, x, y, 0xFFB0B0D0);
        int px = x + Math.max(50, width - 42);
        for (int i = 0; i < 5; i++) {
            g.fill(px + i * 8, y, 6, 6, i < v ? 0xFFFFD040 : 0xFF3A3A5A);
            if (i < v) g.fill(px + i * 8, y, 6, 1, 0xFFFFF0A0);
        }
    }

    // ------------------------------------------------------------------ shop
    private int shopHero, shopRow;
    private String shopMsg;
    private int shopMsgT;

    private static final int LIFE_COST = 60;

    private void updateShop() {
        if (shopMsgT > 0) shopMsgT--;
        if (input.backP || input.bP) {
            Sfx.play(Sfx.PAUSE);
            go(MAP, 0);
            return;
        }
        int n = Hero.ALL.length;
        if (input.leftP) {
            shopHero = (shopHero + n - 1) % n;
            Sfx.play(Sfx.SELECT);
        }
        if (input.rightP) {
            shopHero = (shopHero + 1) % n;
            Sfx.play(Sfx.SELECT);
        }
        if (input.upP) shopRow = (shopRow + 3) % 4;
        if (input.downP) shopRow = (shopRow + 1) % 4;
        int chosen = input.aP ? shopRow : -1;
        for (float[] tp : touch.taps) {
            float x = tp[0], y = tp[1];
            if (y < 96 && x < W / 2f - 100) {
                shopHero = (shopHero + n - 1) % n;
                Sfx.play(Sfx.SELECT);
            } else if (y < 96 && x > W / 2f + 100) {
                shopHero = (shopHero + 1) % n;
                Sfx.play(Sfx.SELECT);
            }
            for (int r = 0; r < 4; r++) {
                int ry = shopRowY(r);
                if (y >= ry - 3 && y < ry + 17 && Math.abs(x - W / 2f) < 150) {
                    shopRow = r;
                    chosen = r;
                }
            }
        }
        if (chosen < 0) return;
        Hero h = Hero.ALL[shopHero];
        int u = save.upgrade(h.id);
        if (chosen == 0 || chosen == 1) {
            int lv = chosen; // 0 -> buy level 1, 1 -> buy level 2
            if (u > lv) msg("GI\u00c0 ACQUISTATO");
            else if (u < lv) msg("PRIMA COMPRA IL LIVELLO PRECEDENTE");
            else if (save.totalCoins < Hero.UPGRADE_COST[lv]) msg("MONETE INSUFFICIENTI");
            else {
                save.totalCoins -= Hero.UPGRADE_COST[lv];
                save.upgrades[h.id] = lv + 1;
                save.store();
                Sfx.play(Sfx.POWER);
                msg(h.upgrades[lv] + " SBLOCCATO!");
            }
        } else if (chosen == 2) {
            if (save.totalCoins < LIFE_COST) msg("MONETE INSUFFICIENTI");
            else if (save.lives >= 99) msg("VITE AL MASSIMO");
            else {
                save.totalCoins -= LIFE_COST;
                save.lives++;
                save.store();
                Sfx.play(Sfx.LIFE);
                msg("+1 VITA!");
            }
        } else {
            Sfx.play(Sfx.PAUSE);
            go(MAP, 0);
        }
    }

    private void msg(String m) {
        shopMsg = m;
        shopMsgT = 120;
        if (!m.endsWith("!")) Sfx.play(Sfx.EMPTY);
    }

    private int shopRowY(int r) {
        return 104 + r * 24;
    }

    private void renderShop(Gfx g) {
        for (int y = 0; y < H; y++) g.fill(0, y, W, 1, Gfx.mix(0xFF142A30, 0xFF2A4A3A, y / (float) H));
        g.textC("POTENZIAMENTI", W / 2, 6, 0xFFFFE070, 0xFF000000, 2);
        g.blit(Art.hudCoin, W - 70, 6);
        g.textO("" + save.totalCoins, W - 56, 10, 0xFFFFE070, 0xFF000000, 1);
        Hero h = Hero.ALL[shopHero];
        int u = save.upgrade(h.id);
        // hero header
        int bob = (frame / 15) % 2;
        g.textC("<", W / 2 - 120 - bob, 44, 0xFFFFE070, 0xFF000000, 3);
        g.textC(">", W / 2 + 120 + bob, 44, 0xFFFFE070, 0xFF000000, 3);
        Sprite big = h.frames[HeroArt.IDLE0 + (frame / 30) % 2].scaled(2);
        g.blit(big, W / 2 - 90, 26);
        g.textO(h.name, W / 2 - 40, 34, 0xFFFFFFFF, 0xFF000000, 1);
        g.textO("ABILIT\u00c0: " + h.ability, W / 2 - 40, 48, 0xFFFF9A5A, 0xFF000000, 1);
        g.textO("LIVELLO " + (u + 1) + "/3", W / 2 - 40, 62, 0xFF7CFF7C, 0xFF000000, 1);
        String[] labels = new String[4];
        for (int lv = 0; lv < 2; lv++) {
            String state = u > lv ? "OK" : Hero.UPGRADE_COST[lv] + "";
            labels[lv] = "LV" + (lv + 2) + " " + h.upgrades[lv] + "   [" + (u > lv ? "ACQUISTATO" : state + " MONETE") + "]";
        }
        labels[2] = "VITA EXTRA  [" + LIFE_COST + "]   (VITE: " + save.lives + ")";
        labels[3] = "TORNA ALLA MAPPA";
        for (int r = 0; r < 4; r++) {
            int y = shopRowY(r);
            boolean sel = r == shopRow;
            boolean owned = r < 2 && u > r;
            g.fillA(W / 2 - 150, y - 4, 300, 18, sel ? 0xFFFFFFFF : 0xFF000000, sel ? 50 : 90);
            if (sel) g.rect(W / 2 - 150, y - 4, 300, 18, 0xFFFFE070);
            int col = owned ? 0xFF7CFF7C : sel ? 0xFFFFE070 : 0xFFFFFFFF;
            g.textO(labels[r], W / 2 - 144, y + 1, col, 0xFF000000, 1);
        }
        if (shopRow < 2) g.textC(h.upgradeDesc[shopRow], W / 2, 200, 0xFFB0F0C0, 0xFF000000, 1);
        if (shopMsgT > 0 && shopMsg != null) g.textC(shopMsg, W / 2, 84, 0xFFFFE070, 0xFF000000, 1);
        g.textC("A: COMPRA   B: ESCI", W / 2, H - 12, 0xFFC0C0D8, 0xFF000000, 1);
    }

    private void renderGameOver(Gfx g) {
        g.clear(0xFF0A0610);
        g.textC("GAME OVER", W / 2, H / 2 - 30, 0xFFE83A3A, 0xFF3A0A0A, 4);
        Sprite s = hero().frames[HeroArt.DEAD].scaled(2);
        g.blit(s, W / 2 - s.w / 2, H / 2 + 6);
        if (screenT > 90 && (frame / 25) % 2 == 0) g.textC("TOCCA PER RIPROVARE", W / 2, H - 22, 0xFFFFFFFF, 0xFF000000, 1);
    }

    private int storyPage, storyChapter;
    /** Chapter 0: the journey through the four towns before university. Chapter 1: off to Palermo, to Unipa. */
    private static final String[][][] STORIES = {
            {
                    {"ESTATE IN SICILIA", "\u00c8 ARRIVATA LA LETTERA:", "SEI STATO AMMESSO ALL'UNIVERSIT\u00c0 DI PALERMO!"},
                    {"MA C'\u00c8 UN PROBLEMA...", "IL PROF. GIOVANNI GARBO NON VUOLE NUOVE MATRICOLE:", "CON I SUOI SEGNALI ALEATORI",
                            "HA MESSO SOTTOSOPRA MEZZA SICILIA."},
                    {"LA SICILIA \u00c8 IN PERICOLO", "UN CINGHIALONE GIGANTE SCORRAZZA A CAPO D'ORLANDO,",
                            "UNO SCARABEO HA INVASO LE SALINE DI PACECO,", "BARCELLONA POZZO DI GOTTO \u00c8 SOTTO IL GHIACCIO",
                            "E A NASO LO STROMBOLI HA SVEGLIATO LORD MAGMUS."},
                    {"IL VIAGGIO", "LIBERA LE QUATTRO CITT\u00c0", "E RAGGIUNGI PALERMO PER IMMATRICOLARTI.", "",
                            "IN BOCCA AL LUPO, MATRICOLA!"}},
            {
                    {"HAI SCONFITTO LORD MAGMUS!", "CAPO D'ORLANDO, PACECO, BARCELLONA E NASO", "SONO DI NUOVO LIBERE."},
                    {"SI PARTE PER PALERMO", "ORA INIZIA IL VERO GIOCO:", "L'UNIVERSIT\u00c0 DI PALERMO.", "",
                            "PRIMO ANNO, SECONDO ANNO, ULTIMO ANNO."},
                    {"IL PROF. GIOVANNI GARBO", "TI ASPETTA: HA BLOCCATO L'USCITA DI UNIPA", "CON I SUOI SEGNALI ALEATORI."},
                    {"SUPERA GLI ESAMI...", "E CONQUISTA LA LAUREA!", "", "IN BOCCA AL LUPO!"}}};
    /** Background of every page: {theme, stage, show Garbo}. */
    private static final int[][][] STORY_BG = {
            {{0, 0, 0}, {6, 4, 1}, {2, 1, 0}, {3, 0, 0}},
            {{3, 3, 0}, {4, 1, 0}, {6, 4, 1}, {4, 3, 0}}};

    private void renderStory(Gfx g) {
        String[][] pages = STORIES[storyChapter];
        int page = Math.min(storyPage, pages.length - 1);
        int[] bg = STORY_BG[storyChapter][page];
        Theme th = Theme.get(bg[0]);
        th.stage = bg[1];
        th.drawBackground(g, frame * 0.4f, 0, H, frame);
        g.dim(0xFF000000, 120);
        String[] lines = pages[page];
        int y0 = H / 2 - lines.length * 9 - 4;
        for (int i = 0; i < lines.length; i++)
            g.textC(lines[i], W / 2, y0 + i * 16 + (i > 0 ? 10 : 0), i == 0 ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000, i == 0 ? 2 : 1);
        if (bg[2] != 0) {
            Sprite gb = UniArt.garbo[(frame / 30) % 2];
            g.blit(gb, W - 70, H - 80, false);
        }
        if (screenT > 40 && (frame / 25) % 2 == 0) g.textC("TOCCA PER CONTINUARE", W / 2, H - 14, 0xFFC0C0D8, 0xFF000000, 1);
    }

    private void renderEnding(Gfx g) {
        Theme th = Theme.get(4);
        th.stage = 0;
        th.drawBackground(g, frame * 0.3f, 0, H, frame);
        g.dim(0xFF000000, 90);
        g.textC("CONGRATULAZIONI, DOTTORE!", W / 2, 10, 0xFFFFD040, 0xFF3A1A08, 2);
        int n = Hero.ALL.length;
        for (int i = 0; i < n; i++) {
            Sprite s = Hero.ALL[i].frames[(frame / 20 + i) % 2 == 0 ? HeroArt.VICTORY : HeroArt.FRONT].scaled(2);
            int hx = W / 2 - n * 30 + i * 60 + 6, hy = 40 + (int) Math.abs(Math.sin((frame + i * 20) * 0.08) * 6);
            g.blit(s, hx, hy);
            // graduation cap
            g.fill(hx + 4, hy - 4, 32, 4, 0xFF1A1A22);
            g.fill(hx + 12, hy - 1, 16, 4, 0xFF1A1A22);
            g.fill(hx + 34, hy - 2, 2, 8, 0xFFE03A3A);
        }
        String[] lines = {"HAI SCONFITTO IL PROF. GIOVANNI GARBO", "E SEI USCITO DA UNIPA... CON LA LAUREA!",
                "CAPO D'ORLANDO, PACECO, BARCELLONA E NASO FESTEGGIANO!", "",
                "GEMME TROVATE: " + save.totalGems() + "/" + WorldMap.totalGems(), "GRAZIE PER AVER GIOCATO!"};
        for (int i = 0; i < lines.length; i++) g.textC(lines[i], W / 2, 120 + i * 13, 0xFFFFFFFF, 0xFF000000, 1);
        for (int k = 0; k < 10; k++) { // confetti
            int sx = (int) ((k * 83 + frame * (1 + k % 3)) % W), sy = (int) ((k * 47 + frame * 2) % H);
            g.fill(sx, sy, 3, 3, (new int[]{0xFFFF4A4A, 0xFF4AFF6A, 0xFF4A8AFF, 0xFFFFE04A})[k % 4]);
        }
        if (screenT > 240 && (frame / 25) % 2 == 0) g.textC("TOCCA PER TORNARE ALLA MAPPA", W / 2, H - 12, 0xFFFFE070, 0xFF000000, 1);
    }

    // used by map
    void playLevel(int idx) {
        save.lastNode = idx;
        save.store();
        Sfx.play(Sfx.CONFIRM);
        go(PLAY, idx);
    }

    void openSelect() {
        go(SELECT, 0);
    }

    void openTitle() {
        go(TITLE, 0);
    }
}
