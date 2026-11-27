package it.pixelquest.core;

import java.net.InetSocketAddress;
import java.util.HashMap;

/**
 * Desktop check of the Wi-Fi battle: two complete games in one process, talking over real UDP on localhost, like
 * two phones (different screen widths, so different cameras). Both players are driven by CPU brains feeding the
 * games' normal input. Checks discovery, joining, the whole match staying identical on both (the fingerprints are
 * compared all the time: a mismatch ends the match), packet loss, the rematch and a friend leaving.
 */
public final class NetCheck {
    static Save.Storage mem() {
        final HashMap<String, String> m = new HashMap<>();
        return new Save.Storage() {
            public String get(String k) {
                return m.get(k);
            }

            public void put(String k, String v) {
                m.put(k, v);
            }
        };
    }

    static final class Side {
        final Game g;
        Battle seen;
        Cpu brain;

        Side(Game g) {
            this.g = g;
        }

        /** Feed the local fighter's input from a CPU brain (like a person touching the screen). */
        void drive(int lvl, long seed) {
            g.input.keyAx = 0;
            g.input.keyA = g.input.keyB = g.input.keyC = g.input.keyS = false;
            if (g.screen != Game.PLAY || g.level == null || g.level.battle == null) return;
            Battle b = g.level.battle;
            if (b != seen) {
                seen = b;
                brain = new Cpu(b, b.local(), lvl, seed);
            }
            Input ai = brain.think();
            g.input.keyAx = ai.keyAx;
            g.input.keyA = ai.keyA;
            g.input.keyB = ai.keyB;
            g.input.keyC = ai.keyC;
            g.input.keyS = ai.keyS;
        }
    }

    public static void run(float loss, int matches) throws Exception {
        Game hg = new Game(498, 224, mem()), gg = new Game(400, 224, mem());
        hg.save.seenIntro = gg.save.seenIntro = true;
        Side h = new Side(hg), gs = new Side(gg);
        BattleMenu hm = hg.bmenu, gm = gg.bmenu;
        // host: Mattia, Castello di Naso, 2 lives, one CPU (Andrea) too
        hm.mode = BattleMenu.MODE_HOST;
        hm.cfg.hero = 0;
        hm.cfg.arena = 3;
        hm.cfg.stocks = 2;
        hm.cfg.level = 1;
        java.util.Arrays.fill(hm.cfg.rivals, false);
        hm.cfg.rivals[3] = true;
        hm.openNet(true);
        hm.step = BattleMenu.S_LOBBY;
        hg.screen = Game.BSETUP;
        // friend: Francesco, looking for matches (localhost instead of the Wi-Fi broadcast)
        gm.mode = BattleMenu.MODE_JOIN;
        gm.cfg.hero = 2;
        gm.openNet(false);
        gm.step = BattleMenu.S_FIND;
        gg.screen = Game.BSETUP;
        gm.net.extraSeek.add(new InetSocketAddress("127.0.0.1", hm.net.localPort()));
        hm.net.lossForTests = gm.net.lossForTests = loss;
        int t = 0;
        while (gm.net.found.isEmpty() && t++ < 600) tick(hg, gg);
        System.out.println("discovery: friend found " + gm.net.found.size() + " match(es) after " + t + " frames" +
                (gm.net.found.isEmpty() ? "" : ", host hero " + Hero.ALL[gm.net.found.get(0).hero].name));
        gm.joinHost(gm.net.found.get(0).addr);
        t = 0;
        while (!(hm.net.connected() && gm.net.welcomed) && t++ < 600) tick(hg, gg);
        for (int i = 0; i < 90; i++) tick(hg, gg); // a few pings
        System.out.println("joined: host sees the friend's hero " + Hero.ALL[hm.net.peerHero].name + ", ping " + Math.round(hm.net.rttMs) + " ms");
        for (int m = 0; m < matches; m++) {
            if (m == 0) hm.next(); // COMBATTI!
            else { // rematch from the results screen
                hg.input.keyA = true;
                hg.tick();
                hg.input.keyA = false;
            }
            for (int i = 0; i < 300 && !(hg.screen == Game.PLAY && gg.screen == Game.PLAY); i++) tick(hg, gg); // both phones enter the match
            int f = 0, maxWait = 0;
            boolean bothDone = false;
            while (f++ < 60 * 60 * 5) {
                h.drive(2, 11 + m);
                gs.drive(1, 23 + m);
                tick(hg, gg);
                if (hg.level != null && hg.level.battle != null && hg.level.battle.net != null) maxWait = Math.max(maxWait, hg.level.battle.net.waiting);
                if (hg.screen == Game.BRESULT && gg.screen == Game.BRESULT) {
                    bothDone = true;
                    break;
                }
                if (hm.net == null || gm.net == null || hm.net.desync || gm.net.desync || hm.net.error != null || gm.net.error != null) break;
            }
            for (int i = 0; i < 50; i++) tick(hg, gg);
            Battle hb = hm.last, gb = gm.last;
            StringBuilder sb = new StringBuilder();
            boolean same = hb != null && gb != null && hb.fighters.size() == gb.fighters.size();
            for (int i = 0; same && i < hb.fighters.size(); i++) {
                Player a = hb.fighters.get(i), b = gb.fighters.get(i);
                same &= a.place == b.place && a.kos == b.kos && a.falls == b.falls && a.dealt == b.dealt && a.hero == b.hero;
                sb.append(String.format(" %s[pl%d ko%d fall%d %.0f%%]", a.hero.name.substring(0, 3), a.place, a.kos, a.falls, a.dealt));
            }
            System.out.println(String.format("match %d (loss %.0f%%): %s in %ds, frames simulated host %d / friend %d, desync %b/%b, "
                            + "identical results on both phones: %b, longest wait %d frames,%s",
                    m + 1, loss * 100, bothDone ? "both on the results screen" : "NOT FINISHED (host screen " + hg.screen + ", friend " + gg.screen + ")",
                    f / 60, hb != null ? hb.t : -1, gb != null ? gb.t : -1, hm.net != null && hm.net.desync, gm.net != null && gm.net.desync, same, maxWait, sb));
            if (hb != null && gb != null)
                System.out.println("   host says winner " + hb.winner().hero.name + " (you " + (hb.humanLost ? "lost" : "won") + "), friend says winner "
                        + gb.winner().hero.name + " (you " + (gb.humanLost ? "lost" : "won") + ")" + " [friend plays " + gb.local().hero.name + " idx " + gb.localIdx + " out " + gb.local().out + ", host idx " + hb.localIdx + "]");
        }
        // the friend leaves while the host waits: the host must be told
        gm.closeNet();
        for (int i = 0; i < 30; i++) tick(hg, gg);
        System.out.println("friend left: host link error = " + (hm.net != null ? hm.net.error : "closed"));
        hm.closeNet();
    }

    private static void tick(Game a, Game b) throws InterruptedException {
        a.tick();
        b.tick();
        Thread.sleep(0, 200_000);
    }

    public interface Shot {
        void save(Game g, String name);
    }

    /** Screenshots of the Wi-Fi screens on both phones. */
    public static void screens(Shot shot) throws Exception {
        Game hg = new Game(498, 224, mem()), gg = new Game(498, 224, mem());
        hg.save.seenIntro = gg.save.seenIntro = true;
        BattleMenu hm = hg.bmenu, gm = gg.bmenu;
        hm.mode = BattleMenu.MODE_HOST;
        hm.cfg.hero = 0;
        hm.cfg.arena = 0;
        java.util.Arrays.fill(hm.cfg.rivals, false);
        hg.screen = gg.screen = Game.BSETUP;
        hm.step = BattleMenu.S_MODE;
        hm.cursor = 1;
        for (int i = 0; i < 10; i++) hg.tick();
        shot.save(hg, "wifi_0_mode");
        hm.openNet(true);
        hm.step = BattleMenu.S_LOBBY;
        for (int i = 0; i < 30; i++) hg.tick();
        shot.save(hg, "wifi_1_lobby_waiting");
        gm.mode = BattleMenu.MODE_JOIN;
        gm.cfg.hero = 4;
        gm.openNet(false);
        gm.step = BattleMenu.S_FIND;
        gm.net.extraSeek.add(new InetSocketAddress("127.0.0.1", hm.net.localPort()));
        for (int i = 0; i < 80; i++) tick(hg, gg);
        shot.save(gg, "wifi_2_find");
        gm.step = BattleMenu.S_KEYPAD;
        gm.ip.setLength(0);
        gm.ip.append("192.168.1.2");
        for (int i = 0; i < 5; i++) tick(hg, gg);
        shot.save(gg, "wifi_3_keypad");
        gm.step = BattleMenu.S_FIND;
        gm.joinHost(gm.net.found.get(0).addr);
        for (int i = 0; i < 90; i++) tick(hg, gg);
        shot.save(hg, "wifi_4_lobby_ready");
        shot.save(gg, "wifi_5_friend_waiting");
        hm.next();
        for (int i = 0; i < 400; i++) tick(hg, gg);
        shot.save(hg, "wifi_6_match_host");
        shot.save(gg, "wifi_7_match_friend");
        hm.closeNet();
        for (int i = 0; i < 600; i++) tick(hg, gg);
        shot.save(gg, "wifi_8_friend_left");
        gm.closeNet();
    }
}
