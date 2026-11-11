package it.pixelquest.core;

/** Software mixer: one looping music stream + several sound-effect voices. 16-bit mono PCM. */
public final class Audio {
    public static final int RATE = 22050;

    private short[] music;
    private int musicPos, musicLoopStart;
    private boolean musicLoop;
    private int currentTrack = -99;
    private final short[][] vPcm = new short[8][];
    private final int[] vPos = new int[8];
    private final int[] vAge = new int[8];
    private int ageCounter;
    public volatile boolean musicOn = true, sfxOn = true;
    private float musicFade = 1f;

    public Audio() {
        Sfx.init(this);
    }

    public synchronized void music(int id) {
        if (id == currentTrack) return;
        currentTrack = id;
        if (id < 0) {
            music = null;
            return;
        }
        music = Music.get(id);
        musicPos = 0;
        musicLoop = Music.loops(id);
        musicLoopStart = 0;
        musicFade = 1f;
    }

    public synchronized void restartMusic() {
        musicPos = 0;
    }

    public int track() {
        return currentTrack;
    }

    synchronized void sfx(int id) {
        if (!sfxOn || Sfx.pcm == null || Sfx.pcm[id] == null) return;
        // the same effect restarting replaces itself (avoids stacking)
        int slot = -1;
        for (int i = 0; i < vPcm.length && slot < 0; i++) if (vPcm[i] == Sfx.pcm[id]) slot = i;
        for (int i = 0; i < vPcm.length && slot < 0; i++) if (vPcm[i] == null) slot = i;
        if (slot < 0) {
            slot = 0;
            for (int i = 1; i < vPcm.length; i++) if (vAge[i] < vAge[slot]) slot = i;
        }
        vPcm[slot] = Sfx.pcm[id];
        vPos[slot] = 0;
        vAge[slot] = ++ageCounter;
    }

    /** Fill out[0..n) with mixed samples. Called from the audio thread. */
    public synchronized void render(short[] out, int n) {
        for (int i = 0; i < n; i++) {
            int s = 0;
            if (music != null && musicOn) {
                if (musicPos >= music.length) {
                    if (musicLoop) musicPos = musicLoopStart;
                }
                if (musicPos < music.length) s += (int) (music[musicPos++] * 0.62f * musicFade);
            }
            for (int v = 0; v < vPcm.length; v++) {
                short[] p = vPcm[v];
                if (p == null) continue;
                s += p[vPos[v]++];
                if (vPos[v] >= p.length) vPcm[v] = null;
            }
            if (s > 32767) s = 32767;
            if (s < -32768) s = -32768;
            out[i] = (short) s;
        }
    }

    // ------------------------------------------------------------------ synthesis
    static float noteFreq(String name) {
        int oct = name.charAt(name.length() - 1) - '0';
        String n = name.substring(0, name.length() - 1);
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int semi = 0;
        for (int i = 0; i < 12; i++) if (names[i].equals(n)) semi = i;
        int midi = 12 * (oct + 1) + semi;
        return (float) (440.0 * Math.pow(2, (midi - 69) / 12.0));
    }

    /** Simple PRNG for noise (deterministic). */
    static int seed = 12345;

    static float noise() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 16) & 0x7FFF) / 16384f - 1f;
    }

    /** Waveform value for phase 0..1. type: 0 square, 1 triangle, 2 saw, 3 sine. */
    static float wave(int type, float ph, float duty) {
        switch (type) {
            case 0: return ph < duty ? 1 : -1;
            case 1: return ph < 0.5f ? ph * 4 - 1 : 3 - ph * 4;
            case 2: return ph * 2 - 1;
            default: return (float) Math.sin(ph * 2 * Math.PI);
        }
    }
}
