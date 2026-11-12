package it.pixelquest.core;

/** A playable character: shared platforming moves + a unique ability (C) + a special move (S). */
public final class Hero {
    public static final int PUNCH = 0, GUN = 1, COIN = 2, BAT = 3, BALL = 4;
    /** Upgrade prices in coins (level 1, level 2). */
    public static final int[] UPGRADE_COST = {120, 280};

    public final int id;
    public final String name, ability, abilityDesc, special, specialDesc;
    public final String[] upgrades, upgradeDesc;
    public final float walk, run, jump;
    public final int statSpeed, statJump, statPower;
    public final int ability_;
    public Sprite[] frames;
    public Sprite icon;
    public final HeroArt.Look look;

    private Hero(int id, String name, int ab, String ability, String desc, String special, String specialDesc,
                 String[] upgrades, String[] upgradeDesc, float walk, float run, float jump,
                 int sSpeed, int sJump, int sPower, HeroArt.Look look) {
        this.id = id;
        this.name = name;
        this.ability_ = ab;
        this.ability = ability;
        this.abilityDesc = desc;
        this.special = special;
        this.specialDesc = specialDesc;
        this.upgrades = upgrades;
        this.upgradeDesc = upgradeDesc;
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
                new Hero(0, "MATTIA BATTAGLIA", PUNCH, "PUGNO",
                        "STENDE I NEMICI, SPACCA I\nMATTONI, RESPINGE I COLPI.",
                        "SUPER PUGNO", "ONDA D'URTO ENORME:\nDANNO 5 A TUTTI I NEMICI.",
                        new String[]{"DOPPIO PUGNO", "PUGNO RAPIDO"},
                        new String[]{"DUE PUGNI DI FILA", "RICARICA RAPIDA, PIÙ LUNGO"},
                        1.6f, 3.05f, 5.15f, 5, 3, 4, HeroArt.mattia()),
                new Hero(1, "GIUSEPPE MANDANICI", GUN, "PISTOLA",
                        "SPARA A DISTANZA: 2 COLPI\nOGNI 10 SECONDI. DANNO 2.",
                        "SCAGNOZZI", "DUE SCAGNOZZI SPARANO\nAI NEMICI PER 5 SECONDI.",
                        new String[]{"3 COLPI", "4 COLPI"},
                        new String[]{"3 COLPI OGNI 10 SECONDI", "4 COLPI OGNI 10 SECONDI"},
                        1.55f, 2.8f, 5.45f, 4, 5, 3, HeroArt.giuseppe()),
                new Hero(2, "FRANCESCO INZERILLO", COIN, "MONETA D'ORO",
                        "BANCONOTE TAGLIENTI E UNA\nMONETA CHE RIMBALZA. 2,5S.",
                        "TOCCO DI RE MIDA", "DIVENTA D'ORO: UNA FONTANA\nDI MONETE COLPISCE TUTTO.",
                        new String[]{"DUE MONETE", "MONETA RAPIDA"},
                        new String[]{"2 MONETE PRONTE", "RICARICA 1,7S + BANCONOTE"},
                        1.6f, 2.95f, 5.3f, 4, 4, 3, HeroArt.francesco()),
                new Hero(3, "ANDREA SAPIENZA", BAT, "MAZZA TOTEM",
                        "SWING POTENTE: RISPEDISCE\nPROIETTILI E NEMICI. 1S.",
                        "TOTEM DI FUOCO", "IL TOTEM APPARE E LANCIA\nPALLE DI FUOCO PER 4S.",
                        new String[]{"MAZZA PESANTE", "SWING RAPIDO"},
                        new String[]{"PIÙ GRANDE, 2 DANNI AI BOSS", "SWING OGNI 0,6 SECONDI"},
                        1.5f, 2.75f, 5.2f, 3, 3, 5, HeroArt.andrea()),
                new Hero(4, "PAOLO ZINGALES", BALL, "PALLONE",
                        "CALCIA UN PALLONE CHE FA\n3 GRANDI RIMBALZI. DANNO 2.",
                        "SUPER TIRO", "UN PALLONE GIGANTE VA\nDRITTO E TRAVOLGE TUTTO.",
                        new String[]{"DUE PALLONI", "TIRO POTENTE"},
                        new String[]{"2 PALLONI PRONTI", "5 RIMBALZI, PIÙ VELOCE"},
                        1.65f, 3.1f, 5.25f, 5, 4, 3, HeroArt.paolo()),
        };
    }

    public boolean punch() {
        return ability_ == PUNCH;
    }

    public boolean gun() {
        return ability_ == GUN;
    }

    /** Short label shown above the C button. */
    public String cLabel() {
        switch (ability_) {
            case PUNCH: return "PUGNO";
            case GUN: return "SPARO";
            case COIN: return "MONETA";
            case BAT: return "MAZZA";
            default: return "CALCIO";
        }
    }
}
