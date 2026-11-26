package it.pixelquest.core;

/**
 * Static description of a level.
 * Worlds 1-4 (the prologue) have 4 levels each: 3 levels + boss.
 * Worlds 5-7 (the "real game" at the University of Palermo) have 6 levels each:
 * 2 levels, a mid-world boss castle, 2 levels, the end-of-world boss castle.
 */
public final class LevelDef {
    public static final int WORLDS = 7, PROLOGUE_LEVELS = 16;

    public final int index, world, pos, music, bossId;
    public final String code, name;
    public final String[] rows;
    public final boolean boss;
    public String[] hints = new String[0];
    /** Camera auto-scroll speed in px/frame (0 = normal camera). */
    public float autoscroll;

    public static int worldOf(int index) {
        return index < PROLOGUE_LEVELS ? index / 4 : 4 + (index - PROLOGUE_LEVELS) / 6;
    }

    public static int posOf(int index) {
        return index < PROLOGUE_LEVELS ? index % 4 : (index - PROLOGUE_LEVELS) % 6;
    }

    public LevelDef(int index, String name, boolean boss, String[] rows) {
        this.index = index;
        this.world = worldOf(index);
        this.pos = posOf(index);
        this.code = (world + 1) + "-" + (pos + 1);
        this.name = name;
        this.boss = boss;
        this.rows = rows;
        if (!boss) bossId = -1;
        else if (world < 4) bossId = world;
        else bossId = 4 + (world - 4) * 2 + (pos == 2 ? 0 : 1);
        this.music = world < 4 ? Music.W1 + world : Music.W5 + (world - 4);
    }

    /** A battle arena: tiles from {@code rows}, drawn with the theme of {@code world} at level position {@code pos}. */
    public LevelDef(String name, int world, int pos, int music, String[] rows) {
        this.index = -1;
        this.world = world;
        this.pos = pos;
        this.code = "ARENA";
        this.name = name;
        this.boss = false;
        this.rows = rows;
        this.bossId = -1;
        this.music = music;
    }

    /** True for the last boss of a world (mid-world castles of the Unipa worlds are not). */
    public boolean worldBoss() {
        return boss && (world < 4 || pos == 5);
    }
}
