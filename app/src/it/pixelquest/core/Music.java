package it.pixelquest.core;

/** Original chiptune soundtrack, written in a tiny tracker notation and rendered to PCM. */
public final class Music {
    public static final int TITLE = 0, MAP = 1, W1 = 2, W2 = 3, W3 = 4, W4 = 5, BOSS = 6, STAR = 7, CLEAR = 8,
            GAMEOVER = 9, ENDING = 10, W5 = 11, W6 = 12, W7 = 13, FINAL = 14, N = 15;

    private static final short[][] cache = new short[N][];

    public static boolean loops(int id) {
        return id != CLEAR && id != GAMEOVER;
    }

    public static synchronized short[] get(int id) {
        if (cache[id] == null) cache[id] = render(id);
        return cache[id];
    }

    /** Pre-render every track on a background thread so level starts don't hitch. */
    public static void prewarm() {
        Thread th = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < N; i++) get(i);
            }
        }, "music-prewarm");
        th.setPriority(Thread.MIN_PRIORITY);
        th.setDaemon(true);
        th.start();
    }

    // track data: bpm, lead, harmony(arp), bass, drums
    private static Object[] data(int id) {
        switch (id) {
            case TITLE: return new Object[]{132,
                    "G4:2 C5:2 E5:2 G5:4 E5:2 G5:4 | A5:3 G5:1 F5:2 E5:2 D5:4 -:4 | F4:2 A4:2 C5:2 F5:4 E5:2 D5:2 C5:2 | D5:6 E5:2 D5:4 -:4 |"
                            + "G4:2 C5:2 E5:2 G5:4 E5:2 G5:2 C6:2 | B5:3 A5:1 G5:2 A5:2 B5:4 G5:4 | A5:2 F5:2 D5:2 B4:2 C5:2 E5:2 G5:2 E5:2 | C5:8 -:8",
                    "E4:4 G4:4 E4:4 G4:4 | F4:4 A4:4 G4:4 B4:4 | A4:4 C5:4 A4:4 C5:4 | B4:4 D5:4 B4:4 G4:4 |"
                            + "E4:4 G4:4 E4:4 G4:4 | G#4:4 B4:4 G#4:4 E4:4 | F4:4 A4:4 G4:4 B4:4 | E4:8 -:8",
                    "C3:4 G3:4 C3:4 G3:4 | F3:4 C3:4 G3:4 B2:4 | F2:4 C3:4 F3:4 A3:4 | G2:4 D3:4 G3:4 B3:4 |"
                            + "C3:4 G3:4 C3:4 G3:4 | E3:4 B3:4 E3:4 G#3:4 | F3:4 D3:4 G3:4 G2:4 | C3:4 G2:4 C3:8",
                    "k...s...k.k.s... k...s...k.k.s.hh k...s...k.k.s... k...s...k.ks.sss"};
            case MAP: return new Object[]{100,
                    "F5:4 A5:2 C6:2 A5:4 F5:4 | G5:4 A#5:2 A5:2 G5:4 -:4 | E5:4 G5:2 A#5:2 G5:4 E5:4 | F5:6 G5:2 A5:4 -:4 |"
                            + "D5:4 F5:2 A5:2 F5:4 D5:4 | C5:4 E5:2 G5:2 C6:4 -:4 | A#5:2 A5:2 G5:2 F5:2 E5:2 G5:2 C5:2 E5:2 | F5:8 -:8",
                    "A4:2 C5:2 F5:2 C5:2 A4:2 C5:2 F5:2 C5:2 | A#4:2 D5:2 G5:2 D5:2 C5:2 E5:2 G5:2 E5:2 | C5:2 E5:2 G5:2 E5:2 C5:2 E5:2 G5:2 E5:2 | A4:2 C5:2 F5:2 C5:2 A4:2 C5:2 F5:2 C5:2 |"
                            + "A4:2 D5:2 F5:2 D5:2 A4:2 D5:2 F5:2 D5:2 | G4:2 C5:2 E5:2 C5:2 G4:2 C5:2 E5:2 C5:2 | A#4:2 D5:2 G5:2 D5:2 G4:2 C5:2 E5:2 C5:2 | A4:2 C5:2 F5:2 C5:2 A4:4 -:4",
                    "F3:8 C3:8 | G3:8 C3:8 | C3:8 C3:8 | F3:8 F3:8 | D3:8 A2:8 | C3:8 C3:8 | G2:8 C3:8 | F2:16",
                    "k.......h.......k.......h.......k.......h.......k.......h......."};
            case W1: return new Object[]{150,
                    "D5:2 G5:2 -:1 G5:1 A5:2 B5:2 A5:2 G5:2 E5:2 | D5:2 -:2 B4:2 D5:2 E5:4 D5:4 | C5:2 E5:2 -:1 E5:1 G5:2 A5:2 G5:2 E5:2 C5:2 | D5:6 F#5:2 A5:4 -:4 |"
                            + "D5:2 G5:2 -:1 G5:1 A5:2 B5:2 D6:2 B5:2 G5:2 | C6:2 -:2 A5:2 C6:2 B5:4 G5:4 | A5:2 F#5:2 D5:2 F#5:2 A5:2 C6:2 B5:2 A5:2 | G5:8 -:4 D5:2 F#5:2",
                    "B4:2 -:2 D5:2 -:2 B4:2 -:2 D5:2 -:2 | B4:2 -:2 D5:2 -:2 C5:2 -:2 A4:2 -:2 | G4:2 -:2 C5:2 -:2 G4:2 -:2 C5:2 -:2 | A4:2 -:2 D5:2 -:2 F#4:2 -:2 A4:2 -:2 |"
                            + "B4:2 -:2 D5:2 -:2 B4:2 -:2 D5:2 -:2 | E5:2 -:2 C5:2 -:2 D5:2 -:2 B4:2 -:2 | F#4:2 -:2 A4:2 -:2 C5:2 -:2 F#4:2 -:2 | B4:2 -:2 D5:2 -:2 B4:4 -:4",
                    "G2:2 -:2 D3:2 -:2 G2:2 -:2 D3:2 -:2 | G2:2 -:2 D3:2 -:2 C3:2 -:2 D3:2 -:2 | C3:2 -:2 G3:2 -:2 C3:2 -:2 G3:2 -:2 | D3:2 -:2 A3:2 -:2 D3:2 -:2 F#3:2 -:2 |"
                            + "G2:2 -:2 D3:2 -:2 G2:2 -:2 D3:2 -:2 | A2:2 -:2 E3:2 -:2 G2:2 -:2 D3:2 -:2 | D3:2 -:2 A2:2 -:2 D3:2 -:2 C3:2 -:2 | G2:2 -:2 D3:2 -:2 G2:4 D3:4",
                    "k.h.s.h.k.h.s.hh k.h.s.h.k.k.s.h. k.h.s.h.k.h.s.hh k.h.s.h.k.k.s.ss"};
            case W2: return new Object[]{118,
                    "D5:3 E5:1 F5:2 G#5:2 A5:4 G#5:2 F5:2 | E5:2 F5:2 E5:2 C#5:2 D5:8 | A4:2 D5:2 F5:2 A5:2 A#5:3 A5:1 G5:2 F5:2 | E5:4 C#5:4 A4:8 |"
                            + "D5:3 E5:1 F5:2 G#5:2 A5:4 C6:2 A#5:2 | A5:2 G5:2 F5:2 E5:2 F5:4 D5:4 | A#4:2 D5:2 F5:2 A#5:2 A5:2 G5:2 F5:2 E5:2 | D5:12 -:4",
                    "A4:1 D5:1 F5:1 D5:1 A4:1 D5:1 F5:1 D5:1 A4:1 D5:1 F5:1 D5:1 A4:1 D5:1 F5:1 D5:1 | A4:1 C#5:1 E5:1 C#5:1 A4:1 C#5:1 E5:1 C#5:1 A4:1 D5:1 F5:1 D5:1 A4:1 D5:1 F5:1 D5:1 |"
                            + "A4:1 D5:1 F5:1 D5:1 A4:1 D5:1 F5:1 D5:1 A#4:1 D5:1 F5:1 D5:1 A#4:1 D5:1 F5:1 D5:1 | A4:1 C#5:1 E5:1 C#5:1 A4:1 C#5:1 E5:1 C#5:1 A4:1 C#5:1 E5:1 C#5:1 A4:4 |"
                            + "-:16 | -:16 | -:16 | -:16",
                    "D3:4 A2:4 D3:4 A2:4 | A2:4 E3:4 D3:4 A2:4 | D3:4 A2:4 A#2:4 F3:4 | A2:4 E3:4 A2:8 | D3:4 A2:4 D3:4 F3:4 | D3:4 A2:4 D3:8 | A#2:4 F3:4 A2:4 E3:4 | D3:4 A2:4 D3:8",
                    "k..hs.h.k.khs..h k..hs.h.k.khs..h k..hs.h.k.khs..h k..hs.h.k.khs.ss"};
            case W3: return new Object[]{108,
                    "E5:4 C#5:4 A4:4 C#5:4 | D5:4 F#5:4 E5:8 | C#5:4 E5:4 A5:4 G#5:4 | F#5:12 -:4 | E5:4 C#5:4 A4:4 C#5:4 | D5:4 F#5:4 A5:4 B5:4 | C#6:4 B5:4 A5:4 G#5:4 | A5:12 -:4",
                    "A4:1 C#5:1 E5:1 A5:1 A4:1 C#5:1 E5:1 A5:1 A4:1 C#5:1 E5:1 A5:1 A4:1 C#5:1 E5:1 A5:1 | D5:1 F#5:1 A5:1 D6:1 D5:1 F#5:1 A5:1 D6:1 A4:1 C#5:1 E5:1 A5:1 A4:1 C#5:1 E5:1 A5:1 |"
                            + "F#4:1 A4:1 C#5:1 F#5:1 F#4:1 A4:1 C#5:1 F#5:1 E4:1 G#4:1 B4:1 E5:1 E4:1 G#4:1 B4:1 E5:1 | D5:1 F#5:1 A5:1 D6:1 D5:1 F#5:1 A5:1 D6:1 D5:1 F#5:1 A5:1 D6:1 D5:1 F#5:1 A5:1 D6:1 |"
                            + "A4:1 C#5:1 E5:1 A5:1 A4:1 C#5:1 E5:1 A5:1 A4:1 C#5:1 E5:1 A5:1 A4:1 C#5:1 E5:1 A5:1 | D5:1 F#5:1 A5:1 D6:1 D5:1 F#5:1 A5:1 D6:1 B4:1 D5:1 F#5:1 B5:1 B4:1 D5:1 F#5:1 B5:1 |"
                            + "C#5:1 E5:1 A5:1 C#6:1 C#5:1 E5:1 A5:1 C#6:1 E4:1 G#4:1 B4:1 E5:1 E4:1 G#4:1 B4:1 E5:1 | A4:1 C#5:1 E5:1 A5:1 A4:1 C#5:1 E5:1 A5:1 A4:8",
                    "A2:16 | D3:8 A2:8 | F#2:8 E2:8 | D3:16 | A2:16 | D3:8 B2:8 | C#3:8 E2:8 | A2:16",
                    "k.......s....... k.......s....... k.......s....... k.......s...h.h."};
            case W4: return new Object[]{128,
                    "E5:2 -:2 E5:2 F#5:2 G5:4 F#5:2 E5:2 | D#5:4 B4:4 E5:8 | C5:2 -:2 C5:2 D5:2 E5:4 D5:2 C5:2 | B4:12 -:4 |"
                            + "E5:2 -:2 E5:2 F#5:2 G5:4 A5:2 B5:2 | C6:4 B5:4 A5:4 G5:4 | F#5:2 G5:2 A5:2 F#5:2 D#5:4 B4:4 | E5:12 -:4",
                    "B3:2 E4:2 G4:2 E4:2 B3:2 E4:2 G4:2 E4:2 | B3:2 D#4:2 F#4:2 D#4:2 B3:2 E4:2 G4:2 E4:2 | C4:2 E4:2 G4:2 E4:2 C4:2 E4:2 G4:2 E4:2 | B3:2 D#4:2 F#4:2 D#4:2 B3:2 D#4:2 F#4:2 D#4:2 |"
                            + "B3:2 E4:2 G4:2 E4:2 B3:2 E4:2 G4:2 E4:2 | C4:2 E4:2 A4:2 E4:2 C4:2 E4:2 A4:2 E4:2 | B3:2 D#4:2 F#4:2 D#4:2 B3:2 D#4:2 F#4:2 D#4:2 | B3:2 E4:2 G4:2 E4:2 B3:4 -:4",
                    "E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 | B1:2 B2:2 B1:2 B2:2 E2:2 E3:2 E2:2 E3:2 | C2:2 C3:2 C2:2 C3:2 C2:2 C3:2 C2:2 C3:2 | B1:2 B2:2 B1:2 B2:2 B1:2 B2:2 B1:2 B2:2 |"
                            + "E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 | A2:2 A3:2 A2:2 A3:2 C3:2 C3:2 C2:2 C3:2 | B1:2 B2:2 B1:2 B2:2 B1:2 B2:2 B1:2 B2:2 | E2:2 E3:2 E2:2 E3:2 E2:4 -:4",
                    "k.k.s..hk.k.s.hh k.k.s..hk.k.s.hh k.k.s..hk.k.s.hh k.k.s..hk.kss.ss"};
            case BOSS: return new Object[]{164,
                    "A4:2 C5:2 E5:2 A5:2 G#5:2 E5:2 C5:2 E5:2 | F5:2 D5:2 A4:2 D5:2 E5:4 G#4:4 | A4:2 C5:2 E5:2 A5:2 B5:2 C6:2 B5:2 A5:2 | G#5:4 E5:4 B5:4 G#5:4 |"
                            + "A5:2 E5:2 C5:2 E5:2 A5:2 B5:2 C6:2 A5:2 | F5:2 A5:2 D6:2 A5:2 F5:4 D5:4 | E5:2 G#5:2 B5:2 E6:2 D6:2 B5:2 G#5:2 E5:2 | A5:8 E5:8",
                    "-:16 | -:16 | -:16 | -:16 | E5:1 E5:1 -:2 E5:1 E5:1 -:2 E5:1 E5:1 -:2 E5:1 E5:1 -:2 | D5:1 D5:1 -:2 D5:1 D5:1 -:2 D5:1 D5:1 -:2 D5:1 D5:1 -:2 | B4:1 B4:1 -:2 B4:1 B4:1 -:2 B4:1 B4:1 -:2 B4:1 B4:1 -:2 | C5:4 -:4 B4:4 -:4",
                    "A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 | D3:1 D3:1 D4:1 D3:1 D3:1 D3:1 D4:1 D3:1 E3:1 E3:1 E4:1 E3:1 E3:1 E3:1 E4:1 E3:1 |"
                            + "A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 | E2:1 E2:1 E3:1 E2:1 E2:1 E2:1 E3:1 E2:1 E2:1 E2:1 E3:1 E2:1 E2:1 E2:1 E3:1 E2:1 |"
                            + "A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 | D3:1 D3:1 D4:1 D3:1 D3:1 D3:1 D4:1 D3:1 D3:1 D3:1 D4:1 D3:1 D3:1 D3:1 D4:1 D3:1 |"
                            + "E2:1 E2:1 E3:1 E2:1 E2:1 E2:1 E3:1 E2:1 E2:1 E2:1 E3:1 E2:1 E2:1 E2:1 E3:1 E2:1 | A2:1 A2:1 A3:1 A2:1 A2:1 A2:1 A3:1 A2:1 E2:4 A2:4",
                    "k.h.s.h.k.k.s.hh k.h.s.h.k.k.s.hh k.h.s.h.k.k.s.hh k.h.s.h.k.kss.ss k.h.s.h.k.k.s.hh k.h.s.h.k.k.s.hh k.h.s.h.k.k.s.hh kkkks.s.k.kss.ss"};
            case STAR: return new Object[]{170,
                    "C5 E5 G5 C6 G5 E5 C5 E5 G5 C6 E6 C6 G5 E5 C5 G4 | D5 F5 A5 D6 A5 F5 D5 F5 A5 D6 F6 D6 A5 F5 D5 A4 | C5 E5 G5 C6 G5 E5 C5 E5 G5 C6 E6 C6 G5 E5 C5 G4 | G4 B4 D5 G5 D5 B4 G4 B4 D5 G5 B5 G5 D5 B4 G4 D5",
                    "-:16 | -:16 | -:16 | -:16",
                    "C3:4 C3:4 C3:4 C3:4 | D3:4 D3:4 D3:4 D3:4 | C3:4 C3:4 C3:4 C3:4 | G2:4 G2:4 G2:4 G2:4",
                    "k.h.k.h.k.h.k.hh k.h.k.h.k.h.k.hh k.h.k.h.k.h.k.hh k.h.k.h.k.h.s.ss"};
            case CLEAR: return new Object[]{140,
                    "G4:2 C5:2 E5:2 G5:2 C6:3 -:1 G5:2 C6:2 | E6:12 -:4 | -:16",
                    "E4:2 G4:2 C5:2 E5:2 G5:3 -:1 E5:2 G5:2 | C6:12 -:4 | -:16",
                    "C3:4 E3:4 G3:4 C3:4 | C3:12 -:4 | -:16",
                    "k...k...k...s.s. k............... ................"};
            case GAMEOVER: return new Object[]{84,
                    "C5:4 B4:4 A#4:4 A4:4 | G#4:8 G4:8 | -:16",
                    "-:16 | -:16 | -:16",
                    "C3:8 F2:8 | C2:16 | -:16",
                    "................ ................ ................"};
            case W5: return new Object[]{140, // Primo anno: sunny Palermo morning
                    "A4:2 D5:2 F#5:2 A5:4 F#5:2 A5:2 B5:2 | A5:4 F#5:2 D5:2 E5:8 | G4:2 B4:2 D5:2 G5:4 F#5:2 E5:2 D5:2 | E5:6 F#5:2 E5:4 -:4 |"
                            + "A4:2 D5:2 F#5:2 A5:4 F#5:2 A5:2 D6:2 | C#6:4 B5:2 A5:2 B5:4 G5:4 | F#5:2 A5:2 E5:2 G5:2 D5:2 F#5:2 C#5:2 E5:2 | D5:8 -:8",
                    "D5 F#5 A5 F#5 D5 F#5 A5 F#5 D5 F#5 A5 F#5 D5 F#5 A5 F#5 | C#5 E5 A5 E5 C#5 E5 A5 E5 C#5 E5 A5 E5 C#5 E5 A5 E5 |"
                            + "B4 D5 G5 D5 B4 D5 G5 D5 B4 D5 G5 D5 B4 D5 G5 D5 | C#5 E5 A5 E5 C#5 E5 A5 E5 C#5 E5 A5 E5 C#5 E5 A5 E5 |"
                            + "D5 F#5 A5 F#5 D5 F#5 A5 F#5 D5 F#5 A5 F#5 D5 F#5 A5 F#5 | B4 D5 G5 D5 B4 D5 G5 D5 B4 D5 G5 D5 B4 D5 G5 D5 |"
                            + "C#5 E5 A5 E5 C#5 E5 A5 E5 C#5 E5 G5 E5 C#5 E5 G5 E5 | D5 F#5 A5 F#5 D5 F#5 A5 F#5 D5:8",
                    "D3:2 -:2 A2:2 -:2 D3:2 -:2 A2:2 -:2 | A2:2 -:2 E3:2 -:2 A2:2 -:2 E3:2 -:2 | G2:2 -:2 D3:2 -:2 G2:2 -:2 D3:2 -:2 | A2:2 -:2 E3:2 -:2 A2:2 -:2 C#3:2 -:2 |"
                            + "D3:2 -:2 A2:2 -:2 D3:2 -:2 A2:2 -:2 | G2:2 -:2 D3:2 -:2 G2:2 -:2 B2:2 -:2 | A2:2 -:2 E3:2 -:2 A2:2 -:2 A2:2 -:2 | D3:2 -:2 A2:2 -:2 D3:4 -:4",
                    "k.h.s.h.k.h.s.hh k.h.s.h.k.k.s.h. k.h.s.h.k.h.s.hh k.h.s.h.k.k.s.ss"};
            case W6: return new Object[]{118, // Secondo anno: lessons and labs
                    "A4:2 C5:2 E5:2 A5:2 G5:2 E5:2 C5:2 E5:2 | F5:2 E5:2 D5:2 C5:2 B4:4 E5:4 | A4:2 C5:2 E5:2 A5:2 B5:2 C6:2 B5:2 A5:2 | G#5:8 E5:8 |"
                            + "F5:2 A5:2 C6:2 A5:2 E5:2 G5:2 C6:2 G5:2 | D5:2 F5:2 A5:2 F5:2 B4:2 D5:2 G#5:2 E5:2 | A5:4 E5:4 C5:4 B4:4 | A4:8 -:8",
                    "A3:1 -:1 E4:1 -:1 A3:1 -:1 E4:1 -:1 A3:1 -:1 E4:1 -:1 A3:1 -:1 E4:1 -:1 | D4:1 -:1 A4:1 -:1 D4:1 -:1 A4:1 -:1 E4:1 -:1 B4:1 -:1 E4:1 -:1 B4:1 -:1 |"
                            + "A3:1 -:1 E4:1 -:1 A3:1 -:1 E4:1 -:1 A3:1 -:1 E4:1 -:1 A3:1 -:1 E4:1 -:1 | E4:1 -:1 B4:1 -:1 E4:1 -:1 B4:1 -:1 E4:1 -:1 G#4:1 -:1 E4:1 -:1 B4:1 -:1 |"
                            + "F4:1 -:1 C5:1 -:1 F4:1 -:1 C5:1 -:1 C4:1 -:1 G4:1 -:1 C4:1 -:1 G4:1 -:1 | D4:1 -:1 A4:1 -:1 D4:1 -:1 A4:1 -:1 E4:1 -:1 B4:1 -:1 E4:1 -:1 B4:1 -:1 |"
                            + "A3:1 -:1 E4:1 -:1 A3:1 -:1 E4:1 -:1 E4:1 -:1 B4:1 -:1 E4:1 -:1 B4:1 -:1 | A3:1 -:1 E4:1 -:1 A3:4 -:4 -:4",
                    "A2:4 E3:4 A2:4 E3:4 | D3:4 A2:4 E2:4 B2:4 | A2:4 E3:4 A2:4 E3:4 | E2:4 B2:4 E2:4 G#2:4 | F2:4 C3:4 C3:4 G2:4 | D3:4 A2:4 E2:4 B2:4 | A2:4 E2:4 E2:4 B2:4 | A2:8 -:8",
                    "k...h...s...h... k...h...s...h.h. k...h...s...h... k...h...s.s.h.h."};
            case W7: return new Object[]{168, // Ultimo anno: the great escape
                    "E5:1 E5:1 -:1 E5:1 G5:2 E5:2 B5:2 A5:2 G5:2 F#5:2 | E5:2 D5:2 E5:4 B4:8 | C5:1 C5:1 -:1 C5:1 E5:2 C5:2 G5:2 F#5:2 E5:2 D5:2 | B4:4 D#5:4 F#5:4 B5:4 |"
                            + "E5:1 E5:1 -:1 E5:1 G5:2 E5:2 B5:2 C6:2 B5:2 A5:2 | G5:2 F#5:2 E5:2 D5:2 E5:4 G5:4 | A5:2 G5:2 F#5:2 E5:2 D#5:2 F#5:2 B4:4 | E5:8 -:8",
                    "B4:2 -:2 B4:2 -:2 B4:2 -:2 B4:2 -:2 | B4:2 -:2 G4:2 -:2 G4:2 -:2 G4:2 -:2 | G4:2 -:2 G4:2 -:2 G4:2 -:2 G4:2 -:2 | F#4:2 -:2 F#4:2 -:2 D#5:2 -:2 D#5:2 -:2 |"
                            + "B4:2 -:2 B4:2 -:2 B4:2 -:2 C5:2 -:2 | B4:2 -:2 A4:2 -:2 G4:2 -:2 B4:2 -:2 | C5:2 -:2 A4:2 -:2 F#4:2 -:2 D#4:2 -:2 | E4:4 -:12",
                    "E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 | E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 | C2:2 C3:2 C2:2 C3:2 C2:2 C3:2 C2:2 C3:2 | B1:2 B2:2 B1:2 B2:2 B1:2 B2:2 B1:2 B2:2 |"
                            + "E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 E2:2 E3:2 | G2:2 G3:2 G2:2 G3:2 E2:2 E3:2 E2:2 E3:2 | A2:2 A3:2 A2:2 A3:2 B1:2 B2:2 B1:2 B2:2 | E2:2 E3:2 E2:2 E3:2 E2:8",
                    "k.h.s.hhk.k.s.hh k.h.s.hhk.k.s.hh k.h.s.hhk.k.s.hh k.h.s.hhkkk.ssss"};
            case FINAL: return new Object[]{176, // Prof. Garbo
                    "D5:2 F5:2 A5:2 D6:2 C#6:2 A5:2 F5:2 A5:2 | A#5:4 A5:4 G5:4 E5:4 | D5:2 F5:2 A5:2 D6:2 E6:2 F6:2 E6:2 D6:2 | C#6:8 A5:8 |"
                            + "D6:2 A5:2 F5:2 D5:2 A#5:2 G5:2 E5:2 C#5:2 | D5:2 E5:2 F5:2 G5:2 A5:4 C#6:4 | D6:4 A5:4 F5:4 E5:4 | D5:8 -:8",
                    "A4 D5 F5 D5 A4 D5 F5 D5 A4 D5 F5 D5 A4 D5 F5 D5 | A#4 D5 G5 D5 A#4 D5 G5 D5 A4 C#5 E5 C#5 A4 C#5 E5 C#5 |"
                            + "A4 D5 F5 D5 A4 D5 F5 D5 A4 D5 F5 D5 A4 D5 F5 D5 | A4 C#5 E5 C#5 A4 C#5 E5 C#5 A4 C#5 E5 C#5 A4 C#5 E5 C#5 |"
                            + "A#4 D5 F5 D5 A#4 D5 F5 D5 G4 A#4 E5 A#4 G4 A#4 E5 A#4 | A4 D5 F5 D5 A4 D5 F5 D5 A4 C#5 E5 C#5 A4 C#5 E5 C#5 |"
                            + "A4 D5 F5 D5 A4 D5 F5 D5 A4 C#5 E5 C#5 A4 C#5 E5 C#5 | A4 D5 F5 D5 A4:4 -:8",
                    "D2:1 D2:1 D3:1 D2:1 D2:1 D2:1 D3:1 D2:1 D2:1 D2:1 D3:1 D2:1 D2:1 D2:1 D3:1 D2:1 | A#1:1 A#1:1 A#2:1 A#1:1 A#1:1 A#1:1 A#2:1 A#1:1 A1:1 A1:1 A2:1 A1:1 A1:1 A1:1 A2:1 A1:1 |"
                            + "D2:1 D2:1 D3:1 D2:1 D2:1 D2:1 D3:1 D2:1 D2:1 D2:1 D3:1 D2:1 D2:1 D2:1 D3:1 D2:1 | A1:1 A1:1 A2:1 A1:1 A1:1 A1:1 A2:1 A1:1 A1:1 A1:1 A2:1 A1:1 A1:1 A1:1 A2:1 A1:1 |"
                            + "A#1:1 A#1:1 A#2:1 A#1:1 A#1:1 A#1:1 A#2:1 A#1:1 G1:1 G1:1 G2:1 G1:1 G1:1 G1:1 G2:1 G1:1 | D2:1 D2:1 D3:1 D2:1 D2:1 D2:1 D3:1 D2:1 A1:1 A1:1 A2:1 A1:1 A1:1 A1:1 A2:1 A1:1 |"
                            + "D2:1 D2:1 D3:1 D2:1 D2:1 D2:1 D3:1 D2:1 A1:1 A1:1 A2:1 A1:1 A1:1 A1:1 A2:1 A1:1 | D2:4 A1:4 D2:8",
                    "k.h.s.h.k.k.s.hh k.h.s.h.k.k.s.hh k.h.s.h.k.k.s.hh kkkks.s.k.kss.ss k.h.s.h.k.k.s.hh k.h.s.h.k.k.s.hh k.h.s.h.k.k.s.hh kkkks.s.k.kss.ss"};
            default: // ENDING: the title theme played warmly
                Object[] t = data(TITLE);
                t[0] = 116;
                return t;
        }
    }

    // ------------------------------------------------------------------ renderer
    private static short[] render(int id) {
        Object[] d = data(id);
        int bpm = (Integer) d[0];
        float step = Audio.RATE * 60f / bpm / 4f;
        int steps = countSteps((String) d[1]);
        int len = (int) (steps * step);
        float[] mix = new float[len];
        renderVoice(mix, (String) d[1], step, 0, 0.25f, 0.20f);   // lead: 25% square
        renderVoice(mix, (String) d[2], step, 0, 0.125f, 0.08f);  // harmony/arp: thin square
        renderVoice(mix, (String) d[3], step, 1, 0, 0.32f);       // bass: triangle
        renderDrums(mix, (String) d[4], step, steps);
        short[] out = new short[len];
        for (int i = 0; i < len; i++) out[i] = (short) Math.max(-32767, Math.min(32767, mix[i] * 0.8f * 32767));
        return out;
    }

    private static int countSteps(String s) {
        int n = 0;
        for (String tok : s.replace("|", " ").trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            int c = tok.indexOf(':');
            n += c >= 0 ? Integer.parseInt(tok.substring(c + 1)) : 1;
        }
        return n;
    }

    private static void renderVoice(float[] mix, String s, float step, int type, float duty, float vol) {
        int pos = 0;
        for (String tok : s.replace("|", " ").trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            int c = tok.indexOf(':');
            String name = c >= 0 ? tok.substring(0, c) : tok;
            int dur = c >= 0 ? Integer.parseInt(tok.substring(c + 1)) : 1;
            int start = (int) (pos * step), n = (int) (dur * step);
            pos += dur;
            if (name.equals("-")) continue;
            float f = Audio.noteFreq(name);
            float ph = 0;
            int rel = Math.min(n / 3, (int) (Audio.RATE * 0.04f));
            for (int i = 0; i < n && start + i < mix.length; i++) {
                ph += f / Audio.RATE;
                ph -= (int) ph;
                float env;
                if (type == 1) env = i < n - rel ? 1 : (n - i) / (float) rel;
                else {
                    float a = Math.min(1, i / (Audio.RATE * 0.004f));
                    float dec = 0.62f + 0.38f * (float) Math.exp(-i / (Audio.RATE * 0.08f));
                    env = a * dec * (i < n - rel ? 1 : (n - i) / (float) rel);
                }
                float vib = 1;
                if (type == 0 && n > Audio.RATE / 4 && i > Audio.RATE / 8) {
                    float vph = (float) Math.sin(i * 2 * Math.PI * 5.5 / Audio.RATE) * 0.004f;
                    vib += vph;
                    ph += f * vph / Audio.RATE;
                }
                mix[start + i] += Audio.wave(type, ph, duty) * vol * env;
            }
        }
    }

    private static void renderDrums(float[] mix, String s, float step, int steps) {
        String p = s.replace(" ", "");
        if (p.isEmpty()) return;
        for (int k = 0; k < steps; k++) {
            char c = p.charAt(k % p.length());
            int start = (int) (k * step);
            if (c == 'k') {
                int n = (int) (Audio.RATE * 0.12f);
                float ph = 0;
                for (int i = 0; i < n && start + i < mix.length; i++) {
                    float kk = i / (float) n;
                    float f = 140 - 100 * kk;
                    ph += f / Audio.RATE;
                    mix[start + i] += (float) Math.sin(ph * 2 * Math.PI) * 0.42f * (1 - kk) * (1 - kk);
                }
            } else if (c == 's') {
                int n = (int) (Audio.RATE * 0.11f);
                float y = 0;
                for (int i = 0; i < n && start + i < mix.length; i++) {
                    float kk = i / (float) n;
                    y += (Audio.noise() - y) * 0.55f;
                    mix[start + i] += (y * 0.22f + (float) Math.sin(i * 2 * Math.PI * 190 / Audio.RATE) * 0.08f) * (1 - kk) * (1 - kk);
                }
            } else if (c == 'h') {
                int n = (int) (Audio.RATE * 0.03f);
                float prev = 0;
                for (int i = 0; i < n && start + i < mix.length; i++) {
                    float kk = i / (float) n;
                    float nz = Audio.noise();
                    mix[start + i] += (nz - prev) * 0.06f * (1 - kk);
                    prev = nz;
                }
            }
        }
    }
}
