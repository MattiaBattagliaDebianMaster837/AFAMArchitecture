package it.pixelquest.core;

import java.net.InetSocketAddress;
import java.util.ArrayList;

/**
 * Battle mode screens.
 * <ul>
 * <li>First: against the phone (CPU), create a Wi-Fi match, or join a friend's Wi-Fi match.</li>
 * <li>Setup: 1) your hero, 2) the rivals (CPU heroes; optional in a Wi-Fi match), the CPU level and the lives,
 * 3) the arena (with a preview of each one).</li>
 * <li>Wi-Fi, host: the lobby with this phone's address, waiting for the friend, then FIGHT.</li>
 * <li>Wi-Fi, friend: the matches found on the network (or the host's address typed in), then waiting for the host.</li>
 * <li>Results: winner, ranking, rematch / change / menu.</li>
 * </ul>
 * Everything works by touch (tap the cards and buttons) and with keys / gamepad.
 */
final class BattleMenu {
    static final int MODE_CPU = 0, MODE_HOST = 1, MODE_JOIN = 2;
    static final int S_MODE = -1, S_HERO = 0, S_RIVALS = 1, S_ARENA = 2, S_LOBBY = 3, S_FIND = 4, S_KEYPAD = 5, S_WAIT = 6;
    final Game g;
    final Battle.Config cfg = new Battle.Config();
    int step = S_MODE, cursor, mode = MODE_CPU;
    Sprite[] thumbs;
    Battle last;
    /** Wi-Fi link (host or friend), null when not playing over the network */
    Net net;
    /** host: the configuration of the match being played (for the rematch) */
    Battle.Config netCfg;
    String netProblem;
    final StringBuilder ip = new StringBuilder();

    BattleMenu(Game g) {
        this.g = g;
    }

    void enterSetup(int st) {
        step = st;
        if (mode == MODE_CPU && cfg.rivalCount() == 0) cfg.rivals[(cfg.hero + 1) % Hero.ALL.length] = true;
        cursor = st == S_RIVALS ? firstRivalCard() : st == S_MODE ? mode : 0;
        if (st == S_MODE || st == S_HERO && mode == MODE_CPU) closeNet();
    }

    private int firstRivalCard() {
        return cfg.hero == 0 ? 1 : 0;
    }

    void closeNet() {
        if (net != null) net.close();
        net = null;
        g.netLock(false);
    }

    boolean openNet(boolean host) {
        closeNet();
        try {
            net = host ? Net.host(cfg.hero) : Net.join(cfg.hero);
            g.netLock(true);
            netProblem = null;
            return true;
        } catch (Exception e) {
            netProblem = "RETE NON DISPONIBILE: ATTIVA IL WI-FI O L'HOTSPOT";
            net = null;
            return false;
        }
    }

    /** Every frame outside the match: keep the link alive; the friend's phone starts when the host starts. */
    void netTick() {
        if (net == null) return;
        net.poll();
        if (!net.host && net.startCfg != null && g.screen != Game.PLAY) g.go(Game.PLAY, -3);
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

    private int[] modeBtn(int k) {
        return new int[]{g.W / 2 - 150, 40 + k * 52, 300, 44};
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

    private int[] foundBtn(int i) {
        return new int[]{g.W / 2 - 150, 44 + i * 30, 300, 26};
    }

    private int[] ipBtn() {
        return new int[]{g.W / 2 - 70, g.H - 56, 140, 22};
    }

    private static final String KEYS = "123456789.0<";

    private int[] key(int i) {
        int kw = 44, kh = 26, x0 = g.W / 2 - (kw * 3 + 8) / 2;
        return new int[]{x0 + (i % 3) * (kw + 4), 62 + (i / 3) * (kh + 4), kw, kh};
    }

    private static boolean in(int[] r, float x, float y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    // =================================================================== setup: update
    void updateSetup() {
        Input in = g.input;
        if (in.backP || in.bP) {
            Sfx.play(Sfx.SELECT);
            back();
            return;
        }
        if (g.screenT < 8) return;
        int n = Hero.ALL.length;
        switch (step) {
            case S_MODE:
                if (in.upP) cursor = (cursor + 2) % 3;
                if (in.downP) cursor = (cursor + 1) % 3;
                if (in.upP || in.downP) Sfx.play(Sfx.SELECT);
                if (in.aP || in.cP) pickMode(cursor);
                for (float[] tp : g.touch.taps) {
                    for (int k = 0; k < 3; k++) if (in(modeBtn(k), tp[0], tp[1])) pickMode(k);
                    if (in(backBtn(), tp[0], tp[1])) back();
                }
                break;
            case S_HERO:
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
                    if (in(backBtn(), tp[0], tp[1])) back();
                }
                break;
            case S_RIVALS:
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
                    if (in(backBtn(), tp[0], tp[1])) back();
                }
                break;
            case S_ARENA: {
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
                    if (in(backBtn(), tp[0], tp[1])) back();
                }
                break;
            }
            case S_LOBBY:
                if (in.aP || in.cP) next();
                for (float[] tp : g.touch.taps) {
                    if (in(nextBtn(), tp[0], tp[1])) next();
                    if (in(backBtn(), tp[0], tp[1])) back();
                }
                break;
            case S_FIND: {
                ArrayList<Net.Found> f = net != null ? net.found : new ArrayList<Net.Found>();
                if (in.upP) cursor = Math.max(0, cursor - 1);
                if (in.downP) cursor = Math.min(Math.max(0, f.size() - 1), cursor + 1);
                if ((in.aP || in.cP) && cursor < f.size()) joinHost(f.get(cursor).addr);
                for (float[] tp : g.touch.taps) {
                    for (int i = 0; i < Math.min(4, f.size()); i++) if (in(foundBtn(i), tp[0], tp[1])) joinHost(f.get(i).addr);
                    if (in(ipBtn(), tp[0], tp[1])) openKeypad();
                    if (in(backBtn(), tp[0], tp[1])) back();
                }
                break;
            }
            case S_KEYPAD:
                for (float[] tp : g.touch.taps) {
                    for (int i = 0; i < KEYS.length(); i++) if (in(key(i), tp[0], tp[1])) typeKey(KEYS.charAt(i));
                    if (in(nextBtn(), tp[0], tp[1])) next();
                    if (in(backBtn(), tp[0], tp[1])) back();
                }
                if (in.aP) next();
                break;
            default: // S_WAIT: the friend waits for the host to start (netTick switches to the match)
                for (float[] tp : g.touch.taps) if (in(backBtn(), tp[0], tp[1])) back();
                break;
        }
    }

    private void pickMode(int k) {
        Sfx.play(Sfx.CONFIRM);
        mode = k;
        netProblem = null;
        if (k == MODE_HOST) cfg.rivals[cfg.hero] = false;
        enterSetup(S_HERO);
    }

    private void back() {
        switch (step) {
            case S_MODE: g.go(Game.TITLE, 0); break;
            case S_HERO: enterSetup(S_MODE); break;
            case S_RIVALS: enterSetup(S_HERO); break;
            case S_ARENA: enterSetup(S_RIVALS); break;
            case S_LOBBY:
                closeNet();
                enterSetup(S_ARENA);
                break;
            case S_FIND:
                closeNet();
                enterSetup(S_HERO);
                break;
            case S_KEYPAD: step = S_FIND; break;
            default: // waiting: leave this host, look again
                openNet(false);
                step = S_FIND;
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
            if (mode == MODE_CPU && cfg.rivals[i] && cfg.rivalCount() <= 1) {
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

    void next() {
        switch (step) {
            case S_HERO:
                Sfx.play(Sfx.CONFIRM);
                if (mode == MODE_JOIN) {
                    if (openNet(false)) {
                        cursor = 0;
                        step = S_FIND;
                    }
                } else enterSetup(S_RIVALS);
                break;
            case S_RIVALS:
                Sfx.play(Sfx.CONFIRM);
                enterSetup(S_ARENA);
                break;
            case S_ARENA:
                Sfx.play(Sfx.CONFIRM);
                if (mode == MODE_CPU) g.go(Game.PLAY, -1); // fight!
                else if (net != null && net.host) step = S_LOBBY; // back from "change arena": keep the friend
                else if (openNet(true)) step = S_LOBBY;
                break;
            case S_LOBBY:
                if (net == null || !net.connected()) {
                    Sfx.play(Sfx.EMPTY);
                    return;
                }
                Sfx.play(Sfx.CONFIRM);
                netCfg = net.startMatch(cfg);
                g.go(Game.PLAY, -2);
                break;
            case S_KEYPAD:
                try {
                    String s = ip.toString().trim();
                    String[] parts = s.split("\\.");
                    if (parts.length != 4) throw new IllegalArgumentException();
                    byte[] b = new byte[4];
                    for (int k = 0; k < 4; k++) {
                        int v = Integer.parseInt(parts[k]);
                        if (v < 0 || v > 255) throw new IllegalArgumentException();
                        b[k] = (byte) v;
                    }
                    joinHost(new InetSocketAddress(java.net.InetAddress.getByAddress(b), Net.PORT));
                } catch (Exception e) {
                    Sfx.play(Sfx.EMPTY);
                    netProblem = "INDIRIZZO NON VALIDO";
                }
                break;
            default:
                break;
        }
    }

    void joinHost(InetSocketAddress a) {
        if (net == null) return;
        Sfx.play(Sfx.CONFIRM);
        netProblem = null;
        net.connect(a);
        step = S_WAIT;
    }

    private void openKeypad() {
        Sfx.play(Sfx.SELECT);
        if (ip.length() == 0) { // start from this phone's network, e.g. "192.168.1."
            ArrayList<String> mine = Net.myAddresses();
            if (!mine.isEmpty()) ip.append(mine.get(0), 0, mine.get(0).lastIndexOf('.') + 1);
        }
        netProblem = null;
        step = S_KEYPAD;
    }

    private void typeKey(char c) {
        Sfx.play(Sfx.SELECT);
        if (c == '<') {
            if (ip.length() > 0) ip.setLength(ip.length() - 1);
        } else if (ip.length() < 15) ip.append(c);
    }

    // =================================================================== setup: render
    private void backdrop(Gfx gx) {
        for (int y = 0; y < g.H; y++) gx.fill(0, y, g.W, 1, Gfx.mix(0xFF2A0E1A, 0xFF5A1A2A, y / (float) g.H));
        for (int k = 0; k < 30; k++) { // confetti
            int sx = (k * 97 + g.frame / 2) % g.W, sy = (k * 53 + g.frame) % g.H;
            gx.fill(sx, sy, 2, 1, Battle.SLOT_COLORS[k % Battle.SLOT_COLORS.length]);
        }
        gx.textC(mode == MODE_CPU || step == S_MODE ? "BATTAGLIA" : "BATTAGLIA IN WI-FI", g.W / 2, 4, 0xFFFFE070, 0xFF000000, 2);
    }

    private void button(Gfx gx, int[] r, String label, boolean hot) {
        gx.fillA(r[0], r[1], r[2], r[3], hot ? 0xFF8A1A2A : 0xFF1A1A2A, 220);
        gx.rect(r[0], r[1], r[2], r[3], hot ? 0xFFFFE070 : 0xFF8A8AA8);
        gx.textC(label, r[0] + r[2] / 2, r[1] + r[3] / 2 - 3, hot ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000, 1);
    }

    void renderSetup(Gfx gx) {
        backdrop(gx);
        int n = Hero.ALL.length;
        boolean wifi = mode != MODE_CPU;
        switch (step) {
            case S_MODE: {
                gx.textC("COME VUOI GIOCARE?", g.W / 2, 24, 0xFFFFFFFF, 0xFF000000, 1);
                String[] t = {"CONTRO IL TELEFONO", "CREA UNA PARTITA WI-FI", "UNISCITI A UNA PARTITA WI-FI"};
                String[] d = {"TU CONTRO 1-4 PERSONAGGI GUIDATI DAL TELEFONO", "UN AMICO GIOCA DAL SUO TELEFONO: STESSA WI-FI O HOTSPOT",
                        "ENTRA NELLA PARTITA CHE HA CREATO UN AMICO"};
                for (int k = 0; k < 3; k++) {
                    int[] r = modeBtn(k);
                    boolean sel = cursor == k;
                    gx.fillA(r[0], r[1], r[2], r[3], sel ? 0xFF8A1A2A : 0xFF1A1A2A, 225);
                    gx.rect(r[0], r[1], r[2], r[3], sel ? 0xFFFFE070 : 0xFF8A8AA8);
                    gx.textC(t[k], g.W / 2, r[1] + 9, sel ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000, 1);
                    gx.textC(d[k], g.W / 2, r[1] + 26, 0xFFC0C0D8, 0xFF000000, 1);
                }
                button(gx, backBtn(), "MENU", false);
                break;
            }
            case S_HERO:
            case S_RIVALS: {
                String sub = step == S_HERO ? (wifi ? "SCEGLI IL TUO PERSONAGGIO" : "1/3  SCEGLI IL TUO PERSONAGGIO")
                        : mode == MODE_HOST ? "AGGIUNGI PERSONAGGI DEL TELEFONO (FACOLTATIVO)" : "2/3  SCEGLI GLI AVVERSARI (TOCCA PER AGGIUNGERE)";
                gx.textC(sub, g.W / 2, 24, 0xFFFFFFFF, 0xFF000000, 1);
                for (int i = 0; i < n; i++) heroCard(gx, i);
                if (step == S_RIVALS) {
                    button(gx, optBtn(0), "CPU: " + Battle.LEVEL_NAMES[cfg.level], cursor == 5);
                    button(gx, optBtn(1), "VITE: " + cfg.stocks, cursor == 6);
                }
                button(gx, backBtn(), "INDIETRO", false);
                button(gx, nextBtn(), mode == MODE_JOIN ? "CERCA PARTITE" : "AVANTI", step == S_RIVALS && cursor == 7);
                break;
            }
            case S_ARENA: {
                gx.textC(wifi ? "SCEGLI L'ARENA" : "3/3  SCEGLI L'ARENA", g.W / 2, 24, 0xFFFFFFFF, 0xFF000000, 1);
                for (int i = 0; i < Battle.ARENAS.length; i++) arenaCard(gx, i);
                Battle.Arena a = Battle.ARENAS[cfg.arena];
                int[] r = thumb(Battle.ARENAS.length - 1);
                gx.textC(a.name + ": " + a.desc, g.W / 2, r[1] + r[3] + 17, 0xFFFFE070, 0xFF000000, 1);
                button(gx, backBtn(), "INDIETRO", false);
                button(gx, nextBtn(), mode == MODE_CPU ? "COMBATTI!" : "APRI PARTITA", true);
                break;
            }
            case S_LOBBY:
                renderLobby(gx);
                break;
            case S_FIND:
                renderFind(gx);
                break;
            case S_KEYPAD:
                renderKeypad(gx);
                break;
            default:
                renderWait(gx);
                break;
        }
        if (netProblem != null) gx.textC(netProblem, g.W / 2, g.H - 40, 0xFFFF8A7A, 0xFF000000, 1);
        else if (net != null && net.error != null) gx.textC(net.error, g.W / 2, g.H - 40, 0xFFFF8A7A, 0xFF000000, 1);
    }

    private void dots(Gfx gx, String s, int y, int col) {
        gx.textC(s + "...".substring(0, (g.frame / 20) % 4), g.W / 2, y, col, 0xFF000000, 1);
    }

    private void bigHero(Gfx gx, int hero, int cx, int y, String label, int col) {
        if (hero < 0) return;
        Sprite s = Hero.ALL[hero].frames[HeroArt.IDLE0 + (g.frame / 30) % 2].scaled(2);
        gx.blit(s, cx - s.w / 2, y);
        String nm = Hero.ALL[hero].name;
        gx.textC(nm.substring(0, nm.indexOf(' ')), cx, y + s.h + 4, 0xFFFFFFFF, 0xFF000000, 1);
        gx.textC(label, cx, y + s.h + 15, col, 0xFF000000, 1);
    }

    private void renderLobby(Gfx gx) {
        gx.textC("LA TUA PARTITA È APERTA", g.W / 2, 24, 0xFFFFFFFF, 0xFF000000, 1);
        bigHero(gx, cfg.hero, g.W / 2 - 110, 44, "TU", Battle.SLOT_COLORS[0]);
        gx.textC("VS", g.W / 2, 74, 0xFFFFE070, 0xFF000000, 3);
        boolean ok = net != null && net.connected();
        if (ok) {
            bigHero(gx, net.peerHero, g.W / 2 + 110, 44, "AMICO", Battle.SLOT_COLORS[1]);
            gx.textC("L'AMICO È CONNESSO" + (net.rttMs >= 0 ? "  (PING " + Math.round(net.rttMs) + " MS)" : ""), g.W / 2, 132, 0xFF7CFF7C, 0xFF000000, 1);
        } else {
            gx.textC("?", g.W / 2 + 110, 64, 0xFF8A8AA8, 0xFF000000, 4);
            dots(gx, "IN ATTESA DELL'AMICO", 132, 0xFFFFFFFF);
        }
        ArrayList<String> ips = Net.myAddresses();
        String addr = ips.isEmpty() ? "NESSUNA RETE: ATTIVA WI-FI O HOTSPOT" : "INDIRIZZO DI QUESTO TELEFONO: " + ips.get(0);
        gx.textC(addr, g.W / 2, 148, 0xFFFFE070, 0xFF000000, 1);
        gx.textC("SULL'ALTRO TELEFONO: BATTAGLIA, UNISCITI A UNA PARTITA WI-FI", g.W / 2, 162, 0xFFC0C0D8, 0xFF000000, 1);
        gx.textC(Battle.ARENAS[cfg.arena].name + "  -  VITE " + cfg.stocks + (cfg.rivalCount() > 0 ? "  -  CPU " + cfg.rivalCount() : ""), g.W / 2, 176, 0xFFFFFFFF, 0xFF000000, 1);
        button(gx, backBtn(), "INDIETRO", false);
        button(gx, nextBtn(), "COMBATTI!", ok);
    }

    private void renderFind(Gfx gx) {
        gx.textC("PARTITE TROVATE SULLA TUA RETE", g.W / 2, 24, 0xFFFFFFFF, 0xFF000000, 1);
        ArrayList<Net.Found> f = net != null ? net.found : new ArrayList<Net.Found>();
        if (f.isEmpty()) {
            dots(gx, "CERCO", 70, 0xFFFFFFFF);
            gx.textC("I DUE TELEFONI DEVONO ESSERE SULLA STESSA WI-FI,", g.W / 2, 92, 0xFFC0C0D8, 0xFF000000, 1);
            gx.textC("OPPURE UNO COLLEGATO ALL'HOTSPOT DELL'ALTRO.", g.W / 2, 104, 0xFFC0C0D8, 0xFF000000, 1);
            gx.textC("SE NON LA TROVA, INSERISCI L'INDIRIZZO SCRITTO SULL'ALTRO TELEFONO.", g.W / 2, 124, 0xFFC0C0D8, 0xFF000000, 1);
        }
        for (int i = 0; i < Math.min(4, f.size()); i++) {
            Net.Found h = f.get(i);
            int[] r = foundBtn(i);
            boolean sel = cursor == i;
            gx.fillA(r[0], r[1], r[2], r[3], sel ? 0xFF8A1A2A : 0xFF1A1A2A, 225);
            gx.rect(r[0], r[1], r[2], r[3], sel ? 0xFFFFE070 : 0xFF8A8AA8);
            Hero hh = Hero.ALL[Math.max(0, Math.min(Hero.ALL.length - 1, h.hero))];
            gx.blit(hh.icon, r[0] + 6, r[1] + 5);
            String nm = hh.name.substring(0, hh.name.indexOf(' '));
            gx.textO("PARTITA DI " + nm + (h.busy ? " (PIENA)" : ""), r[0] + 26, r[1] + 5, 0xFFFFFFFF, 0xFF000000, 1);
            gx.textO(h.addr.getAddress().getHostAddress(), r[0] + 26, r[1] + 15, 0xFFC0C0D8, 0xFF000000, 1);
        }
        button(gx, ipBtn(), "INSERISCI INDIRIZZO", false);
        button(gx, backBtn(), "INDIETRO", false);
    }

    private void renderKeypad(Gfx gx) {
        gx.textC("INDIRIZZO DELL'ALTRO TELEFONO", g.W / 2, 24, 0xFFFFFFFF, 0xFF000000, 1);
        String shown = ip + ((g.frame / 20) % 2 == 0 ? "_" : " ");
        gx.fillA(g.W / 2 - 90, 38, 180, 18, 0xFF000000, 160);
        gx.textC(shown, g.W / 2, 42, 0xFFFFE070, 0xFF000000, 1);
        for (int i = 0; i < KEYS.length(); i++) {
            char c = KEYS.charAt(i);
            button(gx, key(i), c == '<' ? "CANC" : String.valueOf(c), false);
        }
        button(gx, backBtn(), "INDIETRO", false);
        button(gx, nextBtn(), "COLLEGATI", true);
    }

    private void renderWait(Gfx gx) {
        boolean ok = net != null && net.welcomed && net.error == null;
        if (!ok) {
            dots(gx, "COLLEGAMENTO", 70, 0xFFFFFFFF);
            gx.textC("SE NON SI COLLEGA, CONTROLLA CHE I TELEFONI SIANO SULLA STESSA RETE", g.W / 2, 92, 0xFFC0C0D8, 0xFF000000, 1);
        } else {
            gx.textC("CONNESSO!" + (net.rttMs >= 0 ? "  (PING " + Math.round(net.rttMs) + " MS)" : ""), g.W / 2, 24, 0xFF7CFF7C, 0xFF000000, 1);
            bigHero(gx, net.peerHero, g.W / 2 - 110, 44, "HOST", Battle.SLOT_COLORS[0]);
            gx.textC("VS", g.W / 2, 74, 0xFFFFE070, 0xFF000000, 3);
            bigHero(gx, cfg.hero, g.W / 2 + 110, 44, "TU", Battle.SLOT_COLORS[1]);
            dots(gx, "L'HOST STA SCEGLIENDO L'ARENA", 140, 0xFFFFFFFF);
        }
        button(gx, backBtn(), "INDIETRO", false);
    }

    private void heroCard(Gfx gx, int i) {
        Hero h = Hero.ALL[i];
        int[] r = card(i);
        boolean me = i == cfg.hero, rival = cfg.rivals[i] && !me;
        boolean lit = step == S_HERO ? me : rival;
        int border = step == S_HERO ? (me ? 0xFFFFE070 : 0xFF5A5A7A) : me ? Battle.SLOT_COLORS[0] : rival ? 0xFF4A9AFF : 0xFF4A4A60;
        boolean cur = step == S_RIVALS && cursor == i;
        gx.fillA(r[0], r[1], r[2], r[3], lit ? 0xFF2A2A5A : 0xFF14142A, 225);
        gx.rect(r[0], r[1], r[2], r[3], border);
        if (lit || cur) gx.rect(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, cur ? 0xFFFFFFFF : border);
        int bob = lit ? (int) Math.round(StrictMath.sin((g.frame + i * 13) * 0.12) * 2) : 0;
        Sprite s = h.frames[lit ? (step == S_HERO ? HeroArt.RUN0 + (g.frame / 5) % 4 : HeroArt.IDLE0 + (g.frame / 30) % 2) : HeroArt.IDLE0].scaled(2);
        gx.blit(s, r[0] + r[2] / 2 - s.w / 2, r[1] + 6 + bob, i % 2 == 1 && step == S_RIVALS, step == S_RIVALS && !lit && !me ? 110 : 255, 0);
        String first = h.name.substring(0, h.name.indexOf(' '));
        gx.textC(first, r[0] + r[2] / 2, r[1] + 68, 0xFFFFFFFF, 0xFF000000, 1);
        String ab = h.ability;
        if (Gfx.textW(ab, 1) > r[2] - 4) ab = ab.substring(0, Math.max(1, (r[2] - 4) / 6));
        gx.textC(ab, r[0] + r[2] / 2, r[1] + 80, 0xFFFF9A5A, 0xFF000000, 1);
        if (step == S_RIVALS) {
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
    private boolean netResults() {
        return last != null && last.net != null;
    }

    private String[] resultLabels() {
        if (!netResults()) return new String[]{"RIVINCITA", "CAMBIA SFIDA", "MENU"};
        boolean linked = net != null && net.connected();
        if (net != null && net.host && linked) return new String[]{"RIVINCITA", "CAMBIA ARENA", "ESCI"};
        return new String[]{null, null, "ESCI"};
    }

    void updateResults() {
        Input in = g.input;
        if (g.screenT < 40) return;
        String[] lb = resultLabels();
        int pick = -1;
        if (in.aP) pick = 0;
        if (in.bP) pick = 1;
        if (in.backP || in.cP) pick = 2;
        for (float[] tp : g.touch.taps) for (int k = 0; k < 3; k++) if (lb[k] != null && in(resultBtn(k), tp[0], tp[1])) pick = k;
        if (pick < 0 || lb[pick] == null) return;
        Sfx.play(Sfx.CONFIRM);
        if (!netResults()) {
            if (pick == 0) g.go(Game.PLAY, -1); // rematch, same choices
            else if (pick == 1) g.go(Game.BSETUP, S_HERO);
            else g.go(Game.TITLE, 0);
            return;
        }
        if (pick == 0) { // host: rematch for both phones
            netCfg = net.startMatch(cfg);
            g.go(Game.PLAY, -2);
        } else if (pick == 1) { // host: choose another arena, the friend stays connected
            g.go(Game.BSETUP, S_ARENA);
        } else {
            closeNet();
            g.go(Game.BSETUP, S_MODE);
        }
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
        boolean youWon = !last.humanLost && win == last.local();
        String first = win.hero.name.substring(0, win.hero.name.indexOf(' '));
        gx.textC(youWon ? "HAI VINTO!" : "HAI PERSO...", g.W / 2, 6, youWon ? 0xFFFFE070 : 0xFFFF8A7A, 0xFF000000, 3);
        gx.textC(youWon ? "" : "VINCE " + first + "!", g.W / 2, 32, 0xFFFFFFFF, 0xFF000000, 1);
        // the winner celebrating
        Sprite s = win.hero.frames[(g.frame / 20) % 2 == 0 ? HeroArt.VICTORY : HeroArt.FRONT].scaled(3);
        int wx = Math.max(10, g.W / 2 - 220), wy = 60 + (int) Math.abs(StrictMath.sin(g.frame * 0.08) * 6);
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
            String nm = p.hero.name.substring(0, p.hero.name.indexOf(' ')) + (p == last.local() ? " (TU)" : p.cpu ? "" : " (AMICO)");
            gx.textO(nm, tx + 36, y, 0xFFFFFFFF, 0xFF000000, 1);
            gx.textO("" + p.kos, tx + 132, y, 0xFFFFFFFF, 0xFF000000, 1);
            gx.textO("" + p.falls, tx + 170, y, 0xFFFFFFFF, 0xFF000000, 1);
            gx.textO(Math.round(p.dealt) + "%", tx + 210, y, 0xFFFFFFFF, 0xFF000000, 1);
        }
        String[] lb = resultLabels();
        for (int k = 0; k < 3; k++) if (lb[k] != null) button(gx, resultBtn(k), lb[k], k == 0);
        if (netResults()) {
            String msg = net == null || !net.connected() ? (net != null && net.error != null ? net.error : "COLLEGAMENTO CHIUSO")
                    : net.host ? "" : "ASPETTA: L'HOST PUÒ AVVIARE LA RIVINCITA";
            if (!msg.isEmpty()) gx.textC(msg, g.W / 2, g.H - 40, 0xFFFFE070, 0xFF000000, 1);
        }
    }
}
