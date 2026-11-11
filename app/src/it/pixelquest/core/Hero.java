package it.pixelquest.core;

/** A playable character: shared platforming moves + a unique ability. */
public final class Hero {
    public static final int PUNCH = 0, GUN = 1;

    public final int id;
    public final String name, ability, abilityDesc;
    public final float walk, run, jump;
    public final int statSpeed, statJump, statPower;
    public final int ability_;
    public Sprite[] frames;
    public Sprite icon;
    public final HeroArt.Look look;

    private Hero(int id, String name, String ability, String desc, int ab, float walk, float run, float jump,
                 int sSpeed, int sJump, int sPower, HeroArt.Look look) {
        this.id = id;
        this.name = name;
        this.ability = ability;
        this.abilityDesc = desc;
        this.ability_ = ab;
        this.walk = walk;
        this.run = run;
        this.jump = jump;
        this.statSpeed = sSpeed;
        this.statJump = sJump;
        this.statPower = sPower;
        this.look = look;
        this.frames = HeroArt.frames(look);
        this.icon = HeroArt.icon(look);
    }

    public static Hero[] ALL;

    public static void init() {
        if (ALL != null) return;
        ALL = new Hero[]{
                new Hero(0, "MATTIA BATTAGLIA", "PUGNO",
                        "PRENDE A PUGNI I NEMICI,\nSPACCA I MATTONI E\nRESPINGE I PROIETTILI.", PUNCH,
                        1.6f, 3.05f, 5.15f, 5, 3, 4, HeroArt.mattia()),
                new Hero(1, "GIUSEPPE MANDANICI", "PISTOLA",
                        "SPARA A DISTANZA: 2 COLPI\nOGNI 10 SECONDI.\nDANNO DOPPIO AI BOSS.", GUN,
                        1.55f, 2.8f, 5.45f, 4, 5, 3, HeroArt.giuseppe()),
        };
    }

    public boolean punch() {
        return ability_ == PUNCH;
    }

    public boolean gun() {
        return ability_ == GUN;
    }
}
