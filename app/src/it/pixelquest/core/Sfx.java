package it.pixelquest.core;

/** Procedurally synthesised sound effects. */
public final class Sfx {
    public static final int JUMP = 0, JUMP2 = 1, JUMP3 = 2, SPIN = 3, WALLJUMP = 4, COIN = 5, STOMP = 6, SPINKILL = 7,
            KICK = 8, PUNCH = 9, SHOOT = 10, EMPTY = 11, RELOAD = 12, HURT = 13, DIE = 14, BUMP = 15, BREAK = 16,
            SPROUT = 17, POWER = 18, LIFE = 19, GEM = 20, CHECK = 21, SPRING = 22, CANNON = 23, THUD = 24, HIT = 25,
            BOSSHIT = 26, BOSSDIE = 27, ROAR = 28, GATE = 29, SKID = 30, SELECT = 31, CONFIRM = 32, PAUSE = 33, N = 34;

    static short[][] pcm;
    static Audio audio;

    public static void play(int id) {
        if (audio != null) audio.sfx(id);
    }

    static final int R = Audio.RATE;

    /** Builder that appends synthesised segments. */
    static final class B {
        float[] buf = new float[R * 2];
        int len;

        B tone(int type, float f0, float f1, float dur, float vol, float duty, float vib) {
            int n = (int) (dur * R);
            ensure(len + n);
            float ph = 0;
            for (int i = 0; i < n; i++) {
                float k = i / (float) n;
                float f = f0 + (f1 - f0) * k;
                if (vib > 0) f *= 1 + 0.06f * (float) Math.sin(i * 2 * Math.PI * vib / R);
                ph += f / R;
                ph -= (int) ph;
                float env = Math.min(1, i / (R * 0.004f)) * (1 - k) * (1 - k * 0.3f);
                buf[len + i] += Audio.wave(type, ph, duty) * vol * env;
            }
            len += n;
            return this;
        }

        B noise(float dur, float vol, float lp) {
            int n = (int) (dur * R);
            ensure(len + n);
            float y = 0;
            for (int i = 0; i < n; i++) {
                float k = i / (float) n;
                y += (Audio.noise() - y) * lp;
                buf[len + i] += y * vol * (1 - k) * (1 - k);
            }
            len += n;
            return this;
        }

        /** overlay: rewind so the next segment mixes over the previous one */
        B back(float dur) {
            len = Math.max(0, len - (int) (dur * R));
            return this;
        }

        B gap(float dur) {
            int n = (int) (dur * R);
            ensure(len + n);
            len += n;
            return this;
        }

        B notes(int type, float dur, float vol, String... names) {
            for (String s : names) tone(type, Audio.noteFreq(s), Audio.noteFreq(s), dur, vol, 0.25f, 0);
            return this;
        }

        void ensure(int n) {
            if (n > buf.length) buf = java.util.Arrays.copyOf(buf, n * 2);
        }

        short[] done() {
            short[] s = new short[len];
            for (int i = 0; i < len; i++) s[i] = (short) Math.max(-32767, Math.min(32767, buf[i] * 32767));
            return s;
        }
    }

    static synchronized void init(Audio a) {
        audio = a;
        if (pcm != null) return;
        short[][] p = new short[N][];
        p[JUMP] = new B().tone(0, 300, 720, 0.13f, 0.22f, 0.25f, 0).done();
        p[JUMP2] = new B().tone(0, 360, 880, 0.14f, 0.22f, 0.25f, 0).done();
        p[JUMP3] = new B().tone(0, 420, 1100, 0.18f, 0.22f, 0.125f, 0).back(0.1f).tone(0, 1300, 1800, 0.1f, 0.08f, 0.5f, 0).done();
        p[SPIN] = new B().tone(0, 900, 380, 0.24f, 0.18f, 0.5f, 28).done();
        p[WALLJUMP] = new B().noise(0.04f, 0.3f, 0.6f).tone(0, 520, 960, 0.1f, 0.2f, 0.25f, 0).done();
        p[COIN] = new B().tone(0, 988, 988, 0.06f, 0.18f, 0.5f, 0).tone(0, 1319, 1319, 0.28f, 0.18f, 0.5f, 0).done();
        p[STOMP] = new B().tone(0, 260, 80, 0.12f, 0.3f, 0.5f, 0).back(0.1f).noise(0.08f, 0.25f, 0.3f).done();
        p[SPINKILL] = new B().noise(0.16f, 0.35f, 0.5f).back(0.15f).tone(0, 500, 150, 0.15f, 0.2f, 0.5f, 0).done();
        p[KICK] = new B().tone(0, 760, 180, 0.14f, 0.22f, 0.25f, 0).done();
        p[PUNCH] = new B().noise(0.09f, 0.45f, 0.35f).back(0.09f).tone(3, 160, 50, 0.12f, 0.6f, 0, 0).done();
        p[SHOOT] = new B().noise(0.14f, 0.5f, 0.8f).back(0.14f).tone(0, 1400, 180, 0.1f, 0.15f, 0.5f, 0).done();
        p[EMPTY] = new B().tone(0, 2200, 2000, 0.025f, 0.15f, 0.5f, 0).done();
        p[RELOAD] = new B().noise(0.03f, 0.35f, 0.9f).gap(0.06f).noise(0.04f, 0.4f, 0.9f).tone(0, 1500, 1500, 0.04f, 0.1f, 0.5f, 0).done();
        p[HURT] = new B().tone(0, 640, 180, 0.32f, 0.22f, 0.5f, 18).done();
        p[DIE] = new B().notes(0, 0.09f, 0.2f, "B5", "F5", "B4").tone(0, 400, 90, 0.6f, 0.2f, 0.5f, 6).done();
        p[BUMP] = new B().tone(1, 140, 90, 0.08f, 0.5f, 0, 0).done();
        p[BREAK] = new B().noise(0.25f, 0.45f, 0.4f).back(0.24f).tone(1, 160, 60, 0.15f, 0.5f, 0, 0).done();
        p[SPROUT] = new B().notes(0, 0.05f, 0.16f, "C5", "E5", "G5", "C6", "E6").done();
        p[POWER] = new B().notes(0, 0.045f, 0.16f, "C5", "E5", "G5", "C6", "E6", "G6", "C7").done();
        p[LIFE] = new B().notes(0, 0.07f, 0.16f, "G5", "C6", "E6", "G6", "E6", "C7").done();
        p[GEM] = new B().notes(0, 0.06f, 0.15f, "E6", "B6", "E7").gap(0.04f).notes(0, 0.06f, 0.07f, "E6", "B6", "E7").done();
        p[CHECK] = new B().notes(0, 0.08f, 0.16f, "C6", "G6", "C7").done();
        p[SPRING] = new B().tone(1, 180, 980, 0.28f, 0.45f, 0, 12).done();
        p[CANNON] = new B().noise(0.3f, 0.5f, 0.15f).back(0.3f).tone(3, 110, 40, 0.25f, 0.6f, 0, 0).done();
        p[THUD] = new B().tone(3, 70, 30, 0.4f, 0.8f, 0, 0).back(0.4f).noise(0.3f, 0.35f, 0.08f).done();
        p[HIT] = new B().noise(0.08f, 0.35f, 0.7f).back(0.08f).tone(0, 320, 200, 0.08f, 0.2f, 0.5f, 0).done();
        p[BOSSHIT] = new B().tone(0, 340, 90, 0.28f, 0.25f, 0.5f, 10).back(0.28f).noise(0.2f, 0.3f, 0.3f).done();
        p[BOSSDIE] = new B().noise(1.0f, 0.45f, 0.12f).back(1.0f).tone(0, 600, 60, 1.0f, 0.2f, 0.5f, 8).done();
        p[ROAR] = new B().noise(0.55f, 0.35f, 0.1f).back(0.55f).tone(2, 110, 70, 0.55f, 0.25f, 0, 9).done();
        p[GATE] = new B().noise(0.45f, 0.4f, 0.06f).back(0.45f).tone(1, 60, 45, 0.45f, 0.6f, 0, 0).done();
        p[SKID] = new B().noise(0.07f, 0.18f, 0.9f).done();
        p[SELECT] = new B().tone(0, 880, 880, 0.04f, 0.14f, 0.5f, 0).done();
        p[CONFIRM] = new B().tone(0, 660, 660, 0.06f, 0.16f, 0.5f, 0).tone(0, 990, 990, 0.1f, 0.16f, 0.5f, 0).done();
        p[PAUSE] = new B().tone(0, 990, 990, 0.05f, 0.14f, 0.5f, 0).tone(0, 660, 660, 0.08f, 0.14f, 0.5f, 0).done();
        pcm = p;
    }
}
