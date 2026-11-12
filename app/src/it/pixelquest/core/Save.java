package it.pixelquest.core;

/** Persistent progress. */
public final class Save {
    public interface Storage {
        String get(String key);

        void put(String key, String value);
    }

    public static final int LEVELS = 34;
    private final Storage st;
    public int unlocked;          // highest playable level index
    public long cleared;          // bitmask
    public final int[] gems = new int[LEVELS];
    public int lives = 5, totalCoins, hero, lastNode;
    public boolean music = true, sfx = true, seenIntro;
    /** TouchControls.STICK or TouchControls.ARROWS */
    public int controls = TouchControls.STICK;
    public boolean vibration = true;
    /** upgrade level (0..2) for each hero */
    public final int[] upgrades = new int[8];
    /** best grade per level: 0 none, 1 D, 2 C, 3 B, 4 A, 5 S */
    public final int[] grades = new int[LEVELS];
    /** coins collected towards the next extra life (coins themselves are kept as currency) */
    public int coinLife;

    public static final String[] GRADE_NAMES = {"-", "D", "C", "B", "A", "S"};

    public int upgrade(int hero) {
        return hero >= 0 && hero < upgrades.length ? upgrades[hero] : 0;
    }

    public Save(Storage st) {
        this.st = st;
        load();
    }

    public int gems(int level) {
        return level >= 0 && level < LEVELS ? gems[level] : 0;
    }

    public int totalGems() {
        int n = 0;
        for (int g : gems) n += Integer.bitCount(g);
        return n;
    }

    public boolean isCleared(int level) {
        return (cleared & (1L << level)) != 0;
    }

    public void load() {
        if (st == null) return;
        String s = st.get("save");
        if (s == null || !s.startsWith("v1;")) return;
        try {
            String[] p = s.split(";");
            unlocked = Integer.parseInt(p[1]);
            cleared = Long.parseLong(p[2]);
            String[] g = p[3].split(",");
            for (int i = 0; i < LEVELS && i < g.length; i++) gems[i] = Integer.parseInt(g[i]);
            lives = Math.max(1, Integer.parseInt(p[4]));
            totalCoins = Integer.parseInt(p[5]);
            hero = Integer.parseInt(p[6]);
            music = p[7].equals("1");
            sfx = p[8].equals("1");
            lastNode = Integer.parseInt(p[9]);
            seenIntro = p.length > 10 && p[10].equals("1");
            if (p.length > 11) controls = Integer.parseInt(p[11]) == TouchControls.ARROWS ? TouchControls.ARROWS : TouchControls.STICK;
            if (p.length > 12) vibration = p[12].equals("1");
            if (p.length > 13) {
                String[] u = p[13].split(",");
                for (int i = 0; i < upgrades.length && i < u.length; i++) upgrades[i] = Math.max(0, Math.min(2, Integer.parseInt(u[i])));
            }
            if (p.length > 14) {
                String[] gr = p[14].split(",");
                for (int i = 0; i < LEVELS && i < gr.length; i++) grades[i] = Math.max(0, Math.min(5, Integer.parseInt(gr[i])));
            }
            if (p.length > 15) coinLife = Integer.parseInt(p[15]);
            if (hero >= 5 || hero < 0) hero = 0;
        } catch (Exception e) {
            // corrupted save: start fresh
        }
    }

    private static String join(int[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) b.append(',');
            b.append(a[i]);
        }
        return b.toString();
    }

    public void store() {
        if (st == null) return;
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < LEVELS; i++) {
            if (i > 0) g.append(',');
            g.append(gems[i]);
        }
        st.put("save", "v1;" + unlocked + ";" + cleared + ";" + g + ";" + lives + ";" + totalCoins + ";" + hero + ";"
                + (music ? 1 : 0) + ";" + (sfx ? 1 : 0) + ";" + lastNode + ";" + (seenIntro ? 1 : 0) + ";" + controls
                + ";" + (vibration ? 1 : 0) + ";" + join(upgrades) + ";" + join(grades) + ";" + coinLife);
    }
}
