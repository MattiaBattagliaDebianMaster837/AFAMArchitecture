package it.pixelquest.core;

/** Top-level game: screens, flow between them and persistence. Platform independent. */
public final class Game {
    public static final int TITLE = 0, SELECT = 1, MAP = 2, PLAY = 3, PAUSE = 4, GAMEOVER = 5, ENDING = 6;

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
        audio = new Audio();
        save = new Save(storage);
        audio.musicOn = save.music;
        audio.sfxOn = save.sfx;
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
            case MAP:
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
        boolean fromCk = checkpointLevel == index;
        if (checkpointLevel != index) checkpointLevel = -1;
        level = new Level(this, def, fromCk);
        audio.music(def.music);
        audio.restartMusic();
    }

    public void onPlayerDied() {
        if (level == null || nextScreen >= 0) return;
        save.lives--;
        if (save.lives <= 0) {
            save.lives = 5;
            checkpointLevel = -1;
            save.store();
            go(GAMEOVER, 0);
        } else {
            save.store();
            go(PLAY, level.def.index);
        }
    }

    public void onLevelClear(Level l) {
        if (nextScreen >= 0) return;
        int idx = l.def.index;
        save.cleared |= 1 << idx;
        save.gems[idx] |= l.gems;
        boolean first = save.unlocked <= idx;
        if (save.unlocked < idx + 1 && idx + 1 < Save.LEVELS) save.unlocked = idx + 1;
        checkpointLevel = -1;
        save.store();
        if (idx == Save.LEVELS - 1) {
            go(ENDING, 0);
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
            int card = cardAt(tp[0], tp[1]);
            if (card >= 0) {
                if (card == selCursor) confirm = true;
                else {
                    selCursor = card;
                    Sfx.play(Sfx.SELECT);
                }
            }
            if (tp[1] > H - 26 && Math.abs(tp[0] - W / 2f) < 70) confirm = true;
        }
        if (confirm && screenT > 10) {
            save.hero = selCursor;
            save.store();
            Sfx.play(Sfx.CONFIRM);
            go(MAP, 0);
        }
    }

    private int cardW() {
        return Math.min(220, (W - 36) / 2);
    }

    private int cardAt(float x, float y) {
        int cw = cardW(), gap = 12;
        int x0 = W / 2 - cw - gap / 2;
        if (y < 32 || y > 32 + 160) return -1;
        if (x >= x0 && x < x0 + cw) return 0;
        if (x >= x0 + cw + gap && x < x0 + 2 * cw + gap) return 1;
        return -1;
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

    private String[] pauseItems() {
        return new String[]{"CONTINUA", "MUSICA: " + (save.music ? "SI" : "NO"), "EFFETTI: " + (save.sfx ? "SI" : "NO"), "ESCI DAL LIVELLO"};
    }

    private void updatePause() {
        String[] items = pauseItems();
        if (input.upP) {
            pauseCursor = (pauseCursor + items.length - 1) % items.length;
            Sfx.play(Sfx.SELECT);
        }
        if (input.downP) {
            pauseCursor = (pauseCursor + 1) % items.length;
            Sfx.play(Sfx.SELECT);
        }
        int chosen = -1;
        if (input.aP) chosen = pauseCursor;
        if (input.pauseP || input.backP || input.bP) chosen = 0;
        for (float[] tp : touch.taps) {
            for (int i = 0; i < items.length; i++) {
                int y = pauseItemY(i);
                if (tp[1] >= y - 6 && tp[1] < y + 14 && Math.abs(tp[0] - W / 2f) < 90) {
                    pauseCursor = i;
                    chosen = i;
                }
            }
        }
        if (chosen < 0) return;
        switch (chosen) {
            case 0:
                screen = PLAY;
                input.consume();
                Sfx.play(Sfx.PAUSE);
                break;
            case 1:
                save.music = !save.music;
                audio.musicOn = save.music;
                save.store();
                break;
            case 2:
                save.sfx = !save.sfx;
                audio.sfxOn = save.sfx;
                save.store();
                Sfx.play(Sfx.SELECT);
                break;
            case 3:
                checkpointLevel = -1;
                go(MAP, 0);
                break;
        }
    }

    private int pauseItemY(int i) {
        return 84 + i * 24;
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
            touch.draw(g, hero().punch() ? "PUGNO" : "SPARO", true, true);
        }
    }

    private void renderPause(Gfx g) {
        renderPlay(g, false);
        g.dim(0xFF000010, 150);
        g.textC("PAUSA", W / 2, 40, 0xFFFFE070, 0xFF000000, 3);
        String[] items = pauseItems();
        for (int i = 0; i < items.length; i++) {
            int y = pauseItemY(i);
            boolean sel = i == pauseCursor;
            if (sel) {
                g.fillA(W / 2 - 86, y - 5, 172, 17, 0xFFFFFFFF, 40);
                g.text("\u0004", W / 2 - 80, y, 0xFFFFE070);
            }
            g.textC(items[i], W / 2, y, sel ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000, 1);
        }
        g.textC(level.def.code + " " + level.def.name, W / 2, H - 20, 0xFFAAB4D0, 0xFF000000, 1);
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
        g.text("V1.0", W - 28, H - 10, 0xFFFFFFFF, 1, 160);
    }

    private void renderSelect(Gfx g) {
        for (int y = 0; y < H; y++) g.fill(0, y, W, 1, Gfx.mix(0xFF1A1440, 0xFF3A2A6A, y / (float) H));
        for (int k = 0; k < 40; k++) {
            int sx = (k * 97 + frame / 3) % W, sy = (k * 53) % H;
            g.pset(sx, sy, (k + frame / 10) % 5 == 0 ? 0xFFFFFFFF : 0xFF8A80C0);
        }
        g.textC("SCEGLI IL PERSONAGGIO", W / 2, 10, 0xFFFFE070, 0xFF000000, 2);
        int cw = cardW(), gap = 12;
        int x0 = W / 2 - cw - gap / 2;
        for (int i = 0; i < Hero.ALL.length; i++) {
            Hero h = Hero.ALL[i];
            int cx = x0 + i * (cw + gap), cy = 32;
            boolean sel = i == selCursor;
            int ch = 160;
            g.fillA(cx, cy, cw, ch, sel ? 0xFF2A2A5A : 0xFF14102A, 220);
            g.rect(cx, cy, cw, ch, sel ? 0xFFFFD040 : 0xFF5A5A8A);
            if (sel) g.rect(cx + 1, cy + 1, cw - 2, ch - 2, 0xFFFFD040);
            // hero preview
            Sprite s;
            if (sel) s = h.frames[(screenT / 40) % 3 == 2 ? HeroArt.ACT1 : HeroArt.RUN0 + (frame / 5) % 4];
            else s = h.frames[HeroArt.IDLE0 + (frame / 30) % 2];
            Sprite big = s.scaled(2);
            g.fillA(cx + 6, cy + 6, 48, 62, 0xFF000000, 60);
            g.blit(big, cx + 30 - big.w / 2, cy + 8);
            // name & stats
            int tx = cx + 60;
            String[] nm = h.name.split(" ");
            g.textO(nm[0], tx, cy + 8, sel ? 0xFFFFFFFF : 0xFFC0C0D8, 0xFF000000, 1);
            g.textO(nm.length > 1 ? nm[1] : "", tx, cy + 18, sel ? 0xFFFFFFFF : 0xFFC0C0D8, 0xFF000000, 1);
            stat(g, tx, cy + 32, "VELOCITÀ", h.statSpeed, cw - 64);
            stat(g, tx, cy + 44, "SALTO", h.statJump, cw - 64);
            stat(g, tx, cy + 56, "POTENZA", h.statPower, cw - 64);
            g.textO("ABILITÀ (C): " + h.ability, cx + 6, cy + 76, 0xFFFF9A5A, 0xFF000000, 1);
            g.text(h.abilityDesc, cx + 6, cy + 90, 0xFFE0E0F0);
            g.text("COMUNI: CORSA, SALTO,\nSPIN, WALL JUMP, TRIPLO", cx + 6, cy + 128, 0xFF9A9AC0);
        }
        if ((frame / 25) % 2 == 0) g.textC("A / TOCCA DI NUOVO: CONFERMA", W / 2, H - 18, 0xFFFFFFFF, 0xFF000000, 1);
    }

    private void stat(Gfx g, int x, int y, String label, int v, int width) {
        g.text(label, x, y, 0xFFB0B0D0);
        int px = x + Math.max(50, width - 42);
        for (int i = 0; i < 5; i++) {
            g.fill(px + i * 8, y, 6, 6, i < v ? 0xFFFFD040 : 0xFF3A3A5A);
            if (i < v) g.fill(px + i * 8, y, 6, 1, 0xFFFFF0A0);
        }
    }

    private void renderGameOver(Gfx g) {
        g.clear(0xFF0A0610);
        g.textC("GAME OVER", W / 2, H / 2 - 30, 0xFFE83A3A, 0xFF3A0A0A, 4);
        Sprite s = hero().frames[HeroArt.DEAD].scaled(2);
        g.blit(s, W / 2 - s.w / 2, H / 2 + 6);
        if (screenT > 90 && (frame / 25) % 2 == 0) g.textC("TOCCA PER RIPROVARE", W / 2, H - 22, 0xFFFFFFFF, 0xFF000000, 1);
    }

    private void renderEnding(Gfx g) {
        Theme th = Theme.get(0);
        th.drawBackground(g, frame * 0.3f, 0, H, frame);
        g.dim(0xFF000000, 90);
        g.textC("CONGRATULAZIONI!", W / 2, 14, 0xFFFFD040, 0xFF3A1A08, 3);
        for (int i = 0; i < 2; i++) {
            Sprite s = Hero.ALL[i].frames[(frame / 20 + i) % 2 == 0 ? HeroArt.VICTORY : HeroArt.FRONT].scaled(3);
            g.blit(s, W / 2 - 80 + i * 100, 50);
        }
        String[] lines = {"LORD MAGMUS È STATO SCONFITTO", "E IL REGNO DEI PIXEL È SALVO!", "",
                "GEMME TROVATE: " + save.totalGems() + "/48", "GRAZIE PER AVER GIOCATO!"};
        for (int i = 0; i < lines.length; i++) g.textC(lines[i], W / 2, 140 + i * 13, 0xFFFFFFFF, 0xFF000000, 1);
        for (int k = 0; k < 6; k++) {
            int sx = (int) ((k * 83 + frame * (1 + k % 3)) % W), sy = (int) ((k * 47 + frame * 2) % H);
            g.blit(Art.sparkle[(frame / 6 + k) % 2], sx, sy);
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
