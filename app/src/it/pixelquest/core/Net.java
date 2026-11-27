package it.pixelquest.core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;

/**
 * Two-phone battle over the local network: the same Wi-Fi, or one phone's hotspot. No server, no internet.
 * <p>
 * Finding each other: the friend's phone broadcasts "who is hosting?" on UDP port 47322 and the host answers;
 * the host's address can also be typed in by hand (it is shown on the host's screen). Then the friend joins,
 * the host picks the arena and starts.
 * <p>
 * The match runs in lockstep: both phones simulate the same battle frame by frame (same seed, deterministic
 * engine) and only the buttons are sent, a few bytes per frame. Each packet repeats the last 16 frames, so a lost
 * packet costs nothing. Buttons are applied a few frames later (the input delay, chosen from the measured round
 * trip) so the friend's have time to arrive; if they are late the game waits for them. Every half second the two
 * phones compare a fingerprint of the state, to notice a desync.
 */
public final class Net {
    public static final int PORT = 47322, VERSION = 1, RING = 1024, SEND_BACK = 16;
    static final String MAGIC = "UPARTY";
    static final long SEC = 1_000_000_000L;

    /** A host found on the network. */
    public static final class Found {
        public final InetSocketAddress addr;
        public final int hero;
        public final boolean busy;
        /** the host's random id: the same phone can answer from more than one address */
        final String token;
        long seen;

        Found(InetSocketAddress addr, int hero, boolean busy, String token, long seen) {
            this.addr = addr;
            this.token = token;
            this.hero = hero;
            this.busy = busy;
            this.seen = seen;
        }
    }

    public final boolean host;
    private DatagramSocket sock;
    private Thread rx;
    private volatile boolean running;
    private final ArrayList<Object[]> inbox = new ArrayList<>();
    /** the other phone (null until connected) */
    public InetSocketAddress peer;
    /** set when the link is broken (shown to the player) */
    public String error;
    public boolean peerLeft;
    long lastHeard;
    // ---- lobby
    public int myHero, peerHero = -1;
    public final ArrayList<Found> found = new ArrayList<>();
    public float rttMs = -1;
    private long seekT, pingT, joinT;
    private InetSocketAddress joining;
    public boolean welcomed;
    /** extra addresses to ask "who is hosting?" (tests use 127.0.0.1) */
    public final ArrayList<InetSocketAddress> extraSeek = new ArrayList<>();
    // ---- match
    public int matchId;
    /** friend's phone: the match the host has just started (taken by the game) */
    public volatile Battle.Config startCfg;
    int startMatchId;
    public int delay = 3;
    int inFrame, simFrame;
    private final byte[] myAx = new byte[RING], myBtn = new byte[RING];
    private final byte[] peerAx = new byte[RING], peerBtn = new byte[RING];
    private final int[] peerTag = new int[RING];
    private final int[] sumTag = new int[64], sumVal = new int[64];
    private int lastSumFrame = -1, lastSum, peerSumFrame = -1, peerSum;
    public boolean desync;
    private byte[] startMsg;
    private long startT;
    private boolean peerStarted;
    /** frames this phone has been waiting for the friend's buttons */
    public int waiting;
    private final Random rnd = new Random();
    /** this phone's id in the "who is hosting?" answers */
    private final String token = Long.toHexString(new Random().nextLong() & 0xFFFFFFFFFFFFL);
    private final ByteBuffer out = ByteBuffer.allocate(64 + SEND_BACK * 2);

    private Net(boolean host, int hero) {
        this.host = host;
        this.myHero = hero;
    }

    /** Open a match on this phone: listen for friends on port 47322. */
    public static Net host(int hero) throws SocketException {
        Net n = new Net(true, hero);
        SocketException last = null;
        for (int p = PORT; p < PORT + 4 && n.sock == null; p++) {
            try {
                DatagramSocket s = new DatagramSocket(null);
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(p));
                n.sock = s;
            } catch (SocketException e) {
                last = e;
            }
        }
        if (n.sock == null) throw last;
        n.sock.setBroadcast(true);
        n.startRx();
        return n;
    }

    /** Look for a match to join. */
    public static Net join(int hero) throws SocketException {
        Net n = new Net(false, hero);
        n.sock = new DatagramSocket();
        n.sock.setBroadcast(true);
        n.startRx();
        return n;
    }

    public int localPort() {
        return sock != null ? sock.getLocalPort() : -1;
    }

    private void startRx() {
        running = true;
        lastHeard = System.nanoTime();
        rx = new Thread(new Runnable() {
            public void run() {
                byte[] buf = new byte[1500];
                while (running) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        sock.receive(p);
                        byte[] d = new byte[p.getLength()];
                        System.arraycopy(p.getData(), p.getOffset(), d, 0, d.length);
                        synchronized (inbox) {
                            if (inbox.size() < 512) inbox.add(new Object[]{d, new InetSocketAddress(p.getAddress(), p.getPort())});
                        }
                    } catch (Exception e) {
                        if (!running) break;
                    }
                }
            }
        }, "uniparty-net");
        rx.setDaemon(true);
        rx.start();
    }

    public void close() {
        if (peer != null) for (int i = 0; i < 3; i++) sendText(peer, "BYE");
        running = false;
        if (sock != null) sock.close();
    }

    // =================================================================== sending
    private void sendText(InetSocketAddress to, String msg) {
        send(to, (MAGIC + "|" + msg).getBytes(java.nio.charset.Charset.forName("UTF-8")));
    }

    /** Test hook: drop this fraction of the outgoing packets (simulates a bad Wi-Fi). */
    public float lossForTests;

    private void send(InetSocketAddress to, byte[] d) {
        if (to == null || sock == null) return;
        if (lossForTests > 0 && rnd.nextFloat() < lossForTests) return;
        try {
            sock.send(new DatagramPacket(d, d.length, to));
        } catch (Exception ignored) {
            // no network right now: the next packet will try again
        }
    }

    /** "Who is hosting?" to every broadcast address of every network (Wi-Fi, hotspot...). */
    private void seek() {
        String msg = "SEEK|" + VERSION;
        InetAddress all = broadcastAll();
        if (all != null) sendText(new InetSocketAddress(all, PORT), msg);
        for (InetAddress b : broadcasts()) sendText(new InetSocketAddress(b, PORT), msg);
        for (InetSocketAddress a : extraSeek) sendText(a, msg);
    }

    private static InetAddress broadcastAll() {
        try {
            return InetAddress.getByAddress(new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        } catch (Exception e) {
            return null;
        }
    }

    static ArrayList<InetAddress> broadcasts() {
        ArrayList<InetAddress> r = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            while (it != null && it.hasMoreElements()) {
                NetworkInterface ni = it.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) if (ia.getBroadcast() != null) r.add(ia.getBroadcast());
            }
        } catch (Exception ignored) {
        }
        return r;
    }

    /** This phone's addresses on the local networks (to show to the friend). */
    public static ArrayList<String> myAddresses() {
        ArrayList<String> r = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            while (it != null && it.hasMoreElements()) {
                NetworkInterface ni = it.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String s = a.getHostAddress();
                        if (a.isSiteLocalAddress()) r.add(0, s);
                        else r.add(s);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return r;
    }

    // =================================================================== lobby
    /** Friend's phone: join the host at this address. */
    public void connect(InetSocketAddress hostAddr) {
        joining = hostAddr;
        welcomed = false;
        joinT = 0;
        error = null;
    }

    public boolean connected() {
        return peer != null && error == null && !peerLeft;
    }

    /** Called every frame on the game thread: handles what arrived and the periodic messages. */
    public void poll() {
        long now = System.nanoTime();
        ArrayList<Object[]> got;
        synchronized (inbox) {
            got = new ArrayList<>(inbox);
            inbox.clear();
        }
        for (Object[] m : got) handle((byte[]) m[0], (InetSocketAddress) m[1], now);
        if (!host && peer == null && joining == null && now - seekT > SEC * 7 / 10) {
            seekT = now;
            seek();
        }
        if (!host && joining != null && !welcomed && now - joinT > SEC * 3 / 10) {
            joinT = now;
            sendText(joining, "JOIN|" + VERSION + "|" + myHero);
        }
        if (peer != null && now - pingT > SEC / 2) { // keep-alive and round trip, also between matches
            pingT = now;
            sendText(peer, "PING|" + now);
        }
        if (host && startMsg != null && !peerStarted && now - startT > SEC / 10) { // repeat START until the friend plays
            startT = now;
            send(peer, startMsg);
        }
        for (int i = found.size() - 1; i >= 0; i--) if (now - found.get(i).seen > SEC * 4) found.remove(i);
        if (peer != null && !peerLeft && now - lastHeard > SEC * 8) error = "CONNESSIONE PERSA";
    }

    private void handle(byte[] d, InetSocketAddress from, long now) {
        if (d.length > 0 && d[0] == 'I') {
            if (peer != null && from.equals(peer)) {
                lastHeard = now;
                inputs(d);
            }
            return;
        }
        String s = new String(d, java.nio.charset.Charset.forName("UTF-8"));
        if (!s.startsWith(MAGIC + "|")) return;
        String[] f = s.split("\\|");
        if (f.length < 2) return;
        String type = f[1];
        if (peer != null && from.equals(peer)) lastHeard = now;
        try {
            switch (type) {
                case "SEEK":
                    if (host) sendText(from, "HOST|" + VERSION + "|" + myHero + "|" + (peer != null && !from.equals(peer) ? 1 : 0) + "|" + token);
                    break;
                case "HOST":
                    if (!host && Integer.parseInt(f[2]) == VERSION) {
                        int hero = Integer.parseInt(f[3]);
                        boolean busy = f.length > 4 && f[4].equals("1");
                        String tok = f.length > 5 ? f[5] : from.toString();
                        int at = -1;
                        for (int i = 0; i < found.size(); i++) if (found.get(i).addr.equals(from) || found.get(i).token.equals(tok)) at = i;
                        // keep the place in the list and the first address it answered from (no flicker)
                        if (at >= 0) found.set(at, new Found(found.get(at).addr, hero, busy, tok, now));
                        else found.add(new Found(from, hero, busy, tok, now));
                    }
                    break;
                case "JOIN":
                    if (!host) break;
                    if (Integer.parseInt(f[2]) != VERSION) {
                        sendText(from, "OLD");
                        break;
                    }
                    if (peer == null || peer.equals(from) || peerLeft) {
                        peer = from;
                        peerLeft = false;
                        error = null;
                        peerHero = Integer.parseInt(f[3]);
                        lastHeard = now;
                        sendText(from, "WELCOME|" + VERSION + "|" + myHero);
                    } else sendText(from, "FULL");
                    break;
                case "WELCOME":
                    if (!host && joining != null && from.getAddress().equals(joining.getAddress())) {
                        peer = from;
                        peerHero = Integer.parseInt(f[3]);
                        welcomed = true;
                        lastHeard = now;
                    }
                    break;
                case "FULL":
                    if (!host) error = "PARTITA AL COMPLETO";
                    break;
                case "OLD":
                    error = "VERSIONI DIVERSE DEL GIOCO";
                    break;
                case "PING":
                    sendText(from, "PONG|" + f[2]);
                    break;
                case "PONG": {
                    float ms = (now - Long.parseLong(f[2])) / 1e6f;
                    rttMs = rttMs < 0 ? ms : rttMs * 0.7f + ms * 0.3f;
                    break;
                }
                case "START":
                    if (!host && peer != null && from.equals(peer)) {
                        int id = Integer.parseInt(f[2]);
                        if (id != startMatchId) {
                            startMatchId = id;
                            Battle.Config c = new Battle.Config();
                            c.seed = Long.parseLong(f[3]);
                            int dl = Integer.parseInt(f[4]);
                            c.arena = Integer.parseInt(f[5]);
                            c.stocks = Integer.parseInt(f[6]);
                            c.level = Integer.parseInt(f[7]);
                            c.hero = Integer.parseInt(f[8]);
                            c.guestHero = Integer.parseInt(f[9]);
                            int mask = Integer.parseInt(f[10]);
                            for (int i = 0; i < c.rivals.length; i++) c.rivals[i] = (mask & (1 << i)) != 0;
                            c.net = true;
                            delay = dl;
                            matchId = id;
                            startCfg = c;
                        }
                    }
                    break;
                case "BYE":
                    if (peer != null && from.equals(peer)) {
                        peerLeft = true;
                        error = "L'AMICO È USCITO";
                    }
                    break;
                default:
                    break;
            }
        } catch (RuntimeException ignored) {
            // malformed packet: ignore
        }
    }

    // =================================================================== match
    /** Host: start a match (or a rematch) with this configuration. Returns the config to play. */
    public Battle.Config startMatch(Battle.Config base) {
        Battle.Config c = base.copy();
        c.net = true;
        c.guestHero = peerHero;
        c.hero = myHero;
        c.seed = rnd.nextLong() | 1;
        int id;
        do id = rnd.nextInt(); while (id == 0 || id == matchId);
        matchId = id;
        // input delay from the round trip: half of it, in frames, plus one frame of margin
        float rtt = rttMs < 0 ? 40 : rttMs;
        delay = Math.max(2, Math.min(6, (int) Math.ceil(rtt / 2 / 16.7f) + 1));
        int mask = 0;
        for (int i = 0; i < c.rivals.length; i++) if (c.rivals[i]) mask |= 1 << i;
        startMsg = (MAGIC + "|START|" + id + "|" + c.seed + "|" + delay + "|" + c.arena + "|" + c.stocks + "|" + c.level + "|" + c.hero + "|"
                + c.guestHero + "|" + mask).getBytes(java.nio.charset.Charset.forName("UTF-8"));
        peerStarted = false;
        startT = 0;
        send(peer, startMsg);
        return c;
    }

    /** Both phones, right before frame 0 of a match. */
    public void beginMatch() {
        for (int i = 0; i < RING; i++) peerTag[i] = -1;
        for (int i = 0; i < sumTag.length; i++) sumTag[i] = -1;
        lastSumFrame = -1;
        peerSumFrame = -1;
        desync = false;
        waiting = 0;
        simFrame = 0;
        // the first frames have no buttons on either phone
        for (int f = 0; f < delay; f++) {
            myAx[f] = 0;
            myBtn[f] = 0;
        }
        inFrame = delay;
        startCfg = null;
    }

    /** Record this phone's buttons for the next input frame and send the recent ones. */
    public void pushLocal(Input in) {
        if (inFrame < simFrame + delay + 1) {
            int k = inFrame % RING;
            myAx[k] = (byte) Math.max(-100, Math.min(100, Math.round(in.ax * 100)));
            myBtn[k] = (byte) ((in.a ? 1 : 0) | (in.b ? 2 : 0) | (in.c ? 4 : 0) | (in.s ? 8 : 0));
            inFrame++;
        }
        sendInputs();
    }

    private void sendInputs() {
        if (peer == null || matchId == 0) return;
        int n = Math.min(SEND_BACK, inFrame);
        out.clear();
        out.put((byte) 'I');
        out.putInt(matchId);
        out.putInt(inFrame - 1);
        out.put((byte) n);
        for (int f = inFrame - n; f < inFrame; f++) {
            out.put(myAx[f % RING]);
            out.put(myBtn[f % RING]);
        }
        out.putInt(lastSumFrame);
        out.putInt(lastSum);
        byte[] d = new byte[out.position()];
        out.flip();
        out.get(d);
        send(peer, d);
    }

    private void inputs(byte[] d) {
        ByteBuffer b = ByteBuffer.wrap(d);
        b.get();
        int id = b.getInt();
        if (id != matchId || matchId == 0) return;
        peerStarted = true;
        int last = b.getInt(), n = b.get() & 0xFF;
        for (int f = last - n + 1; f <= last; f++) {
            byte ax = b.get(), btn = b.get();
            if (f < 0) continue;
            int k = f % RING;
            if (f >= simFrame && f < simFrame + RING - 1) {
                peerAx[k] = ax;
                peerBtn[k] = btn;
                peerTag[k] = f;
            }
        }
        int sf = b.getInt(), sv = b.getInt();
        if (sf > peerSumFrame) {
            peerSumFrame = sf;
            peerSum = sv;
        }
        if (sf >= 0 && sumTag[(sf / 30) % sumTag.length] == sf && sumVal[(sf / 30) % sumTag.length] != sv) desync = true;
    }

    /** Both buttons known for the next frame: it can be simulated. */
    public boolean canStep() {
        return matchId != 0 && simFrame < inFrame && peerTag[simFrame % RING] == simFrame;
    }

    /** Fill the inputs of the two human fighters for the next frame (host's first). */
    public void fill(Input hostIn, Input guestIn) {
        int k = simFrame % RING;
        Input mine = host ? hostIn : guestIn, theirs = host ? guestIn : hostIn;
        set(mine, myAx[k], myBtn[k]);
        set(theirs, peerAx[k], peerBtn[k]);
    }

    private static void set(Input in, byte ax, byte btn) {
        in.keyAx = ax / 100f;
        in.keyAy = 0;
        in.keyA = (btn & 1) != 0;
        in.keyB = (btn & 2) != 0;
        in.keyC = (btn & 4) != 0;
        in.keyS = (btn & 8) != 0;
        in.keyPause = false;
        in.latch(null);
    }

    /** After simulating a frame: every half second remember a fingerprint of the state. */
    public void stepped(Battle b) {
        int f = simFrame;
        if (f % 30 == 0) {
            int s = b.checksum();
            int slot = (f / 30) % sumTag.length;
            sumTag[slot] = f;
            sumVal[slot] = s;
            lastSumFrame = f;
            lastSum = s;
            if (peerSumFrame == f && peerSum != s) desync = true;
        }
        simFrame++;
    }
}
