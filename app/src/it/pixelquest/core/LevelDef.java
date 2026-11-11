package it.pixelquest.core;

/** Static description of a level. */
public final class LevelDef {
    public final int index, world, music;
    public final String code, name;
    public final String[] rows;
    public final boolean boss;
    public String[] hints = new String[0];

    public LevelDef(int index, String name, boolean boss, String[] rows) {
        this.index = index;
        this.world = index / 4;
        this.code = (world + 1) + "-" + (index % 4 + 1);
        this.name = name;
        this.boss = boss;
        this.rows = rows;
        this.music = Music.W1 + world;
    }
}
