package it.pixelquest.core;

/**
 * Battle mode screens.
 * Setup, three steps: 1) your hero, 2) the rivals (1-4 CPU heroes), the CPU level and the lives,
 * 3) the arena (with a preview of each one). Results: winner, ranking, rematch / change / menu.
 * Everything works by touch (tap the cards and buttons) and with keys / gamepad.
 */
final class BattleMenu {
    final Game g;
    final Battle.Config cfg = new Battle.Config();
    int step, cursor;
    Sprite[] thumbs;
    Battle last;

    BattleMenu(Game g) {
        this.g = g;
    }

    void enterSetup(int st) {
        step = st;
        if (cfg.rivalCount() == 0) cfg.rivals[(cfg.hero + 1) % Hero.ALL.length] = true;
        cursor = st == 1 ? firstRivalCard() : 0;
    }

    private int firstRivalCard() {
        return cfg.hero == 0 ? 1 : 0;
    }

    // =================================================================== layout
    private int cardW() {
        return Math.min(92, (g.W - 16) / 5);
    }

    private int[] card(int i) {
        int cw = cardW(), x0 = g.W / 2 - cw * 5 / 2;
        return new int[]{x0 + i * cw + 2, 38, cw - 4, 120};
    }

    private int[] backBtn() {
        return new int[]{6, g.H - 26, 92, 20};
    }

    private int[] nextBtn() {
        return new int[]{g.W - 98, g.H - 26, 92, 20};
    }

    private int[] optBtn(int k) {
        return new int[]{g.W / 2 + (k == 0 ? -150 : 10), 164, 140, 20};
    }

    private int thumbW() {
        return Math.min(112, (g.W - 24) / 4);
    }

    private int[] thumb(int i) {
        int tw = thumbW(), th = tw * 224 / 480;
        int x0 = g.W / 2 - (tw * 4 + 12) / 2;
        return new int[]{x0 + (i % 4) * (tw + 4), 38 + (i / 4) * (th + 16), tw, th};
    }

    private int[] resultBtn(int k) {
        return new int[]{g.W / 2 - 160 + k * 110, g.H - 26, 100, 20};
    }

    private static boolean in(int[] r, float x, float y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    // =================================================================== setup: update
    void updateSetup() {
        Input in = g.input;
        if (in.backP || in.bP) {
            Sfx.play(Sfx.SELECT);
            if (step == 0) g.go(Game.TITLE, 0);
            else enterSetup(step - 1);
            return;
        }
        if (g.screenT < 8) return;
        int n = Hero.ALL.length;
        switch (step) {
            case 0:
                if (in.leftP) move(-1);
                if (in.rightP) move(1);
                if (in.aP || in.cP) next();
                for (float[] tp : g.touch.taps) {
                    for (int i = 0; i < n; i++)
                        if (in(card(i), tp[0], tp[1])) {
                            if (cfg.hero == i) next();
                            else {
                                cfg.hero = i;
                                cfg.rivals[i] = false;
                                Sfx.play(Sfx.SELECT);
                            }
                            return;
                        }
                    if (in(nextBtn(), tp[0], tp[1])) next();
                    if (in(backBtn(), tp[0], tp[1])) g.go(Game.TITLE, 0);
                }
                break;
            case 1:
                if (in.leftP) cursor = (cursor + 7) % 8;
                if (in.rightP) cursor = (cursor + 1) % 8;
                if (cursor < n && cursor == cfg.hero) cursor = (cursor + (in.leftP ? 7 : 1)) % 8;
                if (in.aP) activate(cursor);
                if (in.cP || in.sP) next();
                for (float[] tp : g.touch.taps) {
                    for (int i = 0; i < n; i++) if (in(card(i), tp[0], tp[1])) activate(i);
                    if (in(optBtn(0), tp[0], tp[1])) activate(5);
                    if (in(optBtn(1), tp[0], tp[1])) activate(6);
                    if (in(nextBtn(), tp[0], tp[1])) next();
                    if (in(backBtn(), tp[0], tp[1])) enterSetup(0);
                }
                break;
            default:
                int na = Battle.ARENAS.length;
                if (in.leftP) cfg.arena = (cfg.arena + na - 1) % na;
                if (in.rightP) cfg.arena = (cfg.arena + 1) % na;
                if (in.upP && cfg.arena >= 4) cfg.arena -= 4;
                if (in.downP && cfg.arena + 4 < na) cfg.arena += 4;
                if (in.leftP || in.rightP || in.upP || in.downP) Sfx.play(Sfx.SELECT);
                if (in.aP || in.cP) next();
                for (float[] tp : g.touch.taps) {
                    for (int i = 0; i < na; i++)
                        if (in(thumb(i), tp[0], tp[1])) {
                            if (cfg.arena == i) next();
                            else {
                                cfg.arena = i;
                                Sfx.play(Sfx.SELECT);
                            }
                            return;
                        }
                    if (in(nextBtn(), tp[0], tp[1])) next();
                    if (in(backBtn(), tp[0], tp[1])) enterSetup(1);
                }
                break;
        }
    }

    private void move(int d) {
        int n = Hero.ALL.length;
        cfg.hero = (cfg.hero + n + d) % n;
        cfg.rivals[cfg.hero] = false;
        Sfx.play(Sfx.SELECT);
    }

    void activate(int i) {
        int n = Hero.ALL.length;
        if (i < n) {
            if (i == cfg.hero) return;
            if (cfg.rivals[i] && cfg.rivalCount() <= 1) {
                Sfx.play(Sfx.EMPTY); // at least one rival
                return;
            }
            cfg.rivals[i] = !cfg.rivals[i];
        } else if (i == 5) cfg.level = (cfg.level + 1) % 3;
        else if (i == 6) cfg.stocks = cfg.stocks % 5 + 1;
        else {
            next();
            return;
        }
        Sfx.play(Sfx.SELECT);
    }

    private void next() {
        Sfx.play(Sfx.CONFIRM);
        if (step < 2) enterSetup(step + 1);
        else g.go(Game.PLAY, -1); // fight!
    }

    // =================================================================== setup: render
    private void backdrop(Gfx gx) {
        for (int y = 0; y < g.H; y++) gx.fill(0, y, g.W, 1, Gfx.mix(0xFF2A0E1A, 0xFF5A1A2A, y / (float) g.H));
        for (int k = 0; k < 30; k++) { // confetti
            int sx = (k * 97 + g.frame / 2) % g.W, sy = (k * 53 + g.frame) % g.H;
            gx.fill(sx, sy, 2, 1, Battle.SLOT_COLORS[k % Battle.SLOT_COLORS.length]);
        }
        gx.textC("BATTAGLIA", g.W / 2, 4, 0xFFFFE070, 0xFF000000, 2);
    }

    private void button(Gfx gx, int[] r, String label, boolean hot) {
        gx.fillA(r[0], r[1], r[2], r[3], hot ? 0xFF8A1A2A : 0xFF1A1A2A, 220);
        gx.rect(r[0], r[1], r[2], r[3], hot ? 0xFFFFE070 : 0xFF8A8AA8);
        gx.textC(label, r[0] + r[2] / 2, r[1] + 7, hot ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000, 1);
    }

    void renderSetup(Gfx gx) {
        backdrop(gx);
        int n = Hero.ALL.length;
        String sub = step == 0 ? "1/3  SCEGLI IL TUO PERSONAGGIO" : step == 1 ? "2/3  SCEGLI GLI AVVERSARI (TOCCA PER AGGIUNGERE)"
                : "3/3  SCEGLI L'ARENA";
        gx.textC(sub, g.W / 2, 24, 0xFFFFFFFF, 0xFF000000, 1);
        if (step < 2) {
            for (int i = 0; i < n; i++) heroCard(gx, i);
            if (step == 1) {
                button(gx, optBtn(0), "CPU: " + Battle.LEVEL_NAMES[cfg.level], cursor == 5);
                button(gx, optBtn(1), "VITE: " + cfg.stocks, cursor == 6);
            }
        } else {
            for (int i = 0; i < Battle.ARENAS.length; i++) arenaCard(gx, i);
            Battle.Arena a = Battle.ARENAS[cfg.arena];
            int[] r = thumb(Battle.ARENAS.length - 1);
            gx.textC(a.name + ": " + a.desc, g.W / 2, r[1] + r[3] + 17, 0xFFFFE070, 0xFF000000, 1);
        }
        button(gx, backBtn(), step == 0 ? "MENU" : "INDIETRO", false);
        button(gx, nextBtn(), step == 2 ? "COMBATTI!" : "AVANTI", step == 2 || (step == 1 && cursor == 7));
    }

    private void heroCard(Gfx gx, int i) {
        Hero h = Hero.ALL[i];
        int[] r = card(i);
        boolean me = i == cfg.hero, rival = cfg.rivals[i] && !me;
        boolean lit = step == 0 ? me : rival;
        int border = step == 0 ? (me ? 0xFFFFE070 : 0xFF5A5A7A) : me ? Battle.SLOT_COLORS[0] : rival ? 0xFF4A9AFF : 0xFF4A4A60;
        boolean cur = step == 1 && cursor == i;
        gx.fillA(r[0], r[1], r[2], r[3], lit ? 0xFF2A2A5A : 0xFF14142A, 225);
        gx.rect(r[0], r[1], r[2], r[3], border);
        if (lit || cur) gx.rect(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, cur ? 0xFFFFFFFF : border);
        int bob = lit ? (int) Math.round(Math.sin((g.frame + i * 13) * 0.12) * 2) : 0;
        Sprite s = h.frames[lit ? (step == 0 ? HeroArt.RUN0 + (g.frame / 5) % 4 : HeroArt.IDLE0 + (g.frame / 30) % 2) : HeroArt.IDLE0].scaled(2);
        gx.blit(s, r[0] + r[2] / 2 - s.w / 2, r[1] + 6 + bob, i % 2 == 1 && step == 1, step == 1 && !lit && !me ? 110 : 255, 0);
        String first = h.name.substring(0, h.name.indexOf(' '));
        gx.textC(first, r[0] + r[2] / 2, r[1] + 68, 0xFFFFFFFF, 0xFF000000, 1);
        String ab = h.ability;
        if (Gfx.textW(ab, 1) > r[2] - 4) ab = ab.substring(0, Math.max(1, (r[2] - 4) / 6));
        gx.textC(ab, r[0] + r[2] / 2, r[1] + 80, 0xFFFF9A5A, 0xFF000000, 1);
        if (step == 1) {
            String tag = me ? "TU" : rival ? "CPU" : "+";
            gx.textC(tag, r[0] + r[2] / 2, r[1] + 100, me ? Battle.SLOT_COLORS[0] : rival ? 0xFF4A9AFF : 0xFF8A8AA8, 0xFF000000, 2);
        } else if (me) gx.textC("TU", r[0] + r[2] / 2, r[1] + 100, 0xFFFFE070, 0xFF000000, 2);
    }

    private void arenaCard(Gfx gx, int i) {
        if (thumbs == null) makeThumbs();
        int[] r = thumb(i);
        boolean sel = cfg.arena == i;
        gx.blit(thumbs[i], r[0], r[1]);
        gx.rect(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, sel ? 0xFFFFE070 : 0xFF3A3A5A);
        if (sel) gx.rect(r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, 0xFFFFE070);
        String nm = Battle.ARENAS[i].name;
        if (Gfx.textW(nm, 1) > r[2]) nm = nm.substring(0, r[2] / 6);
        gx.textC(nm, r[0] + r[2] / 2, r[1] + r[3] + 4, sel ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000, 1);
    }

    /** A small picture of each arena: the real background and tiles, scaled down. */
    private void makeThumbs() {
        thumbs = new Sprite[Battle.ARENAS.length];
        Gfx big = new Gfx(480, 224);
        int tw = thumbW(), th = tw * 224 / 480;
        for (int i = 0; i < thumbs.length; i++) {
            Battle.Arena a = Battle.ARENAS[i];
            Level L = new Level(g, new LevelDef(a.name, a.world, a.stage, a.music, a.rows), false);
            L.player.x = -999;
            L.camX = (L.tw * Level.T - 480) / 2f;
            L.camY = L.th * Level.T - 224;
            L.render(big);
            Sprite s = new Sprite(tw, th);
            for (int y = 0; y < th; y++)
                for (int x = 0; x < tw; x++) { // box filter
                    int x0 = x * 480 / tw, x1 = Math.max(x0 + 1, (x + 1) * 480 / tw), y0 = y * 224 / th, y1 = Math.max(y0 + 1, (y + 1) * 224 / th);
                    int rr = 0, gg = 0, bb = 0, n = 0;
                    for (int yy = y0; yy < y1; yy++)
                        for (int xx = x0; xx < x1; xx++) {
                            int c = big.px[yy * 480 + xx];
                            rr += (c >> 16) & 255;
                            gg += (c >> 8) & 255;
                            bb += c & 255;
                            n++;
                        }
                    s.set(x, y, 0xFF000000 | (rr / n) << 16 | (gg / n) << 8 | (bb / n));
                }
            thumbs[i] = s;
        }
    }

    // =================================================================== results
    void updateResults() {
        Input in = g.input;
        if (g.screenT < 40) return;
        int pick = -1;
        if (in.aP) pick = 0;
        if (in.bP) pick = 1;
        if (in.backP || in.cP) pick = 2;
        for (float[] tp : g.touch.taps) for (int k = 0; k < 3; k++) if (in(resultBtn(k), tp[0], tp[1])) pick = k;
        if (pick < 0) return;
        Sfx.play(Sfx.CONFIRM);
        if (pick == 0) g.go(Game.PLAY, -1); // rematch, same choices
        else if (pick == 1) g.go(Game.BSETUP, 0);
        else g.go(Game.TITLE, 0);
    }

    void renderResults(Gfx gx) {
        if (last == null) {
            backdrop(gx);
            return;
        }
        Level L = last.level;
        L.theme.stage = L.def.pos;
        L.theme.drawBackground(gx, L.camX + g.frame * 0.2f, L.camY, L.th * Level.T, g.frame);
        gx.dim(0xFF000010, 150);
        Player win = last.winner();
        boolean youWon = !last.humanLost && win == last.fighters.get(0);
        String first = win.hero.name.substring(0, win.hero.name.indexOf(' '));
        gx.textC(youWon ? "HAI VINTO!" : "HAI PERSO...", g.W / 2, 6, youWon ? 0xFFFFE070 : 0xFFFF8A7A, 0xFF000000, 3);
        gx.textC((last.humanLost && win == last.fighters.get(0) ? "" : "VINCE " + first + "!"), g.W / 2, 32, 0xFFFFFFFF, 0xFF000000, 1);
        // the winner celebrating
        Sprite s = win.hero.frames[(g.frame / 20) % 2 == 0 ? HeroArt.VICTORY : HeroArt.FRONT].scaled(3);
        int wx = Math.max(10, g.W / 2 - 220), wy = 60 + (int) Math.abs(Math.sin(g.frame * 0.08) * 6);
        gx.blit(s, wx, wy);
        gx.fill(wx + 6, wy - 6, 48, 6, 0xFF1A1A22); // graduation cap: the champion of Unipa
        gx.fill(wx + 18, wy - 1, 24, 5, 0xFF1A1A22);
        gx.fill(wx + 52, wy - 4, 2, 12, 0xFFFFD040);
        for (int k = 0; k < 16; k++) {
            int cx = (int) ((k * 83 + g.frame * (1 + k % 3)) % g.W), cy = (int) ((k * 47 + g.frame * 2) % g.H);
            gx.fill(cx, cy, 3, 3, Battle.SLOT_COLORS[k % Battle.SLOT_COLORS.length]);
        }
        // ranking
        int tx = wx + 80, ty = 52;
        gx.textO("POS.", tx, ty, 0xFFB0B0D0, 0xFF000000, 1);
        gx.textO("KO", tx + 130, ty, 0xFFB0B0D0, 0xFF000000, 1);
        gx.textO("CADUTE", tx + 158, ty, 0xFFB0B0D0, 0xFF000000, 1);
        gx.textO("DANNI", tx + 210, ty, 0xFFB0B0D0, 0xFF000000, 1);
        java.util.ArrayList<Player> rank = last.ranking();
        for (int i = 0; i < rank.size(); i++) {
            Player p = rank.get(i);
            int y = ty + 16 + i * 19, col = Battle.SLOT_COLORS[p.slot % Battle.SLOT_COLORS.length];
            gx.fillA(tx - 4, y - 4, 262, 17, col, p.cpu ? 40 : 80);
            gx.textO(p.place + ".", tx, y, p.place == 1 ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000, 1);
            gx.blit(p.hero.icon, tx + 16, y - 4);
            String nm = p.hero.name.substring(0, p.hero.name.indexOf(' ')) + (p.cpu ? "" : " (TU)");
            gx.textO(nm, tx + 36, y, 0xFFFFFFFF, 0xFF000000, 1);
            gx.textO("" + p.kos, tx + 132, y, 0xFFFFFFFF, 0xFF000000, 1);
            gx.textO("" + p.falls, tx + 170, y, 0xFFFFFFFF, 0xFF000000, 1);
            gx.textO(Math.round(p.dealt) + "%", tx + 210, y, 0xFFFFFFFF, 0xFF000000, 1);
        }
        String[] lb = {"RIVINCITA", "CAMBIA SFIDA", "MENU"};
        for (int k = 0; k < 3; k++) button(gx, resultBtn(k), lb[k], k == 0);
    }
}
