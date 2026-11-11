package it.pixelquest.core;

/** Persistent progress. */
public final class Save {
    public interface Storage {
        String get(String key);

        void put(String key, String value);
    }

    public static final int LEVELS = 16;
    private final Storage st;
    public int unlocked;          // highest playable level index
    public int cleared;           // bitmask
    public final int[] gems = new int[LEVELS];
    public int lives = 5, totalCoins, hero, lastNode;
    public boolean music = true, sfx = true, seenIntro;
    /** TouchControls.STICK or TouchControls.ARROWS */
    public int controls = TouchControls.STICK;
    public boolean vibration = true;

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
        return (cleared & (1 << level)) != 0;
    }

    public void load() {
        if (st == null) return;
        String s = st.get("save");
        if (s == null || !s.startsWith("v1;")) return;
        try {
            String[] p = s.split(";");
            unlocked = Integer.parseInt(p[1]);
            cleared = Integer.parseInt(p[2]);
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
        } catch (Exception e) {
            // corrupted save: start fresh
        }
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
                + ";" + (vibration ? 1 : 0));
    }
}
