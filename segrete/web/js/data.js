'use strict';
// Dati di gioco: oggetti, nemici, boss, stanze.

// ------------------------------------------------------------------ OGGETTI PASSIVI
// pools: treasure (stanza del tesoro), shop (negozio), boss (dopo il boss), pact (stanza del patto), secret
const ITEMS = {
  caffe: { name: 'Caffè Doppio', desc: 'Cadenza e velocità su', pools: ['treasure', 'shop', 'boss'], apply(p) { p.mods.tears += 0.5; p.mods.speed += 0.12; } },
  cornetto: { name: 'Cornetto alla Crema', desc: 'Vita su', pools: ['shop', 'boss', 'treasure'], apply(p) { p.addContainers(1); p.heal(2); } },
  pizza: { name: 'Pizza al Taglio', desc: 'Vita su, cura totale', pools: ['boss', 'shop'], apply(p) { p.addContainers(1); p.heal(99); } },
  china: { name: 'Inchiostro di China', desc: 'Danno su', pools: ['treasure', 'boss'], apply(p) { p.mods.dmgUp += 1; } },
  rapido: { name: 'Penna Tecnica 0.1', desc: 'Tiri veloci e precisi', pools: ['treasure', 'boss'], apply(p) { p.mods.shotSpeed += 0.3; p.mods.range += 30; p.mods.dmgUp += 0.5; } },
  squadra: { name: 'Squadra a 45°', desc: 'Tiro triplo', pools: ['treasure', 'pact'], apply(p) { p.fx.triple = true; p.mods.tearMult *= 0.8; } },
  compasso: { name: 'Compasso', desc: 'Tiri a ricerca', pools: ['treasure', 'pact'], apply(p) { p.fx.homing = true; } },
  scalimetro: { name: 'Scalimetro', desc: 'Gittata su', pools: ['treasure', 'boss', 'shop'], apply(p) { p.mods.range += 60; p.mods.shotSpeed += 0.1; } },
  cutter: { name: 'Cutter', desc: 'Danno su', pools: ['treasure', 'boss'], apply(p) { p.mods.dmgFlat += 1; } },
  matita: { name: 'Matita 2B', desc: 'Tiri perforanti', pools: ['treasure', 'pact'], apply(p) { p.fx.pierce = true; p.mods.dmgUp += 0.3; } },
  lucido: { name: 'Carta da Lucido', desc: 'Tiri spettrali', pools: ['treasure', 'secret'], apply(p) { p.fx.spectral = true; p.mods.range += 20; } },
  specchio: { name: 'Specchio', desc: 'I tiri rimbalzano', pools: ['treasure', 'secret'], apply(p) { p.fx.bounce = true; p.mods.range += 40; } },
  pennello: { name: 'Pennello Velenoso', desc: 'Tiri velenosi', pools: ['treasure', 'shop'], apply(p) { p.fx.poison = true; } },
  colla: { name: 'Colla Vinilica', desc: 'Tiri che rallentano', pools: ['treasure', 'shop'], apply(p) { p.fx.slow = true; } },
  accendino: { name: 'Accendino', desc: 'Tiri infuocati', pools: ['treasure', 'pact'], apply(p) { p.fx.burn = true; p.mods.dmgUp += 0.3; } },
  tavola: { name: 'Tavola A0', desc: 'Tiri giganti', pools: ['treasure', 'pact'], apply(p) { p.mods.size += 2; p.mods.dmgUp += 0.6; p.mods.shotSpeed -= 0.1; } },
  lattina: { name: 'Lattina Energetica', desc: 'Velocità su!', pools: ['treasure', 'shop', 'boss'], apply(p) { p.mods.speed += 0.3; } },
  nottebianca: { name: 'Notte in Bianco', desc: 'Cadenza molto su', pools: ['pact', 'treasure'], apply(p) { p.mods.tears += 1.3; p.mods.luck -= 1; } },
  modulor: { name: 'Modulor', desc: 'Tutto su!', pools: ['boss', 'treasure'], apply(p) { p.mods.dmgUp += 0.5; p.mods.tears += 0.3; p.mods.range += 20; p.mods.speed += 0.1; p.mods.shotSpeed += 0.1; p.mods.luck += 1; } },
  sezione: { name: 'Sezione Aurea', desc: 'Danno x1.5', pools: ['pact'], apply(p) { p.mods.dmgMult *= 1.5; } },
  colonna: { name: 'Colonna Dorica', desc: 'Vita e danno su', pools: ['boss', 'treasure'], apply(p) { p.addContainers(1); p.heal(2); p.mods.dmgUp += 0.5; } },
  cupola: { name: 'Cupola', desc: 'Ti protegge girando', pools: ['treasure', 'pact', 'secret'], apply(p) { p.familiars.push({ kind: 'orbit', a: 0 }); } },
  modellino: { name: 'Modellino in Scala', desc: 'Un piccolo amico', pools: ['treasure', 'shop'], apply(p) { p.familiars.push({ kind: 'mini', cd: 0 }); } },
  gomma: { name: 'Gomma Pane', desc: 'Uno scudo per stanza', pools: ['treasure', 'shop'], apply(p) { p.fx.shieldItem = true; p.shield = true; } },
  borsa: { name: 'Borsa di Studio', desc: '+20 monete', pools: ['treasure', 'secret'], apply(p) { p.coins = Math.min(99, p.coins + 20); p.mods.luck += 1; } },
  cartella: { name: 'Cartella Portadisegni', desc: '+5 bombe', pools: ['shop', 'treasure'], apply(p) { p.bombs = Math.min(99, p.bombs + 5); } },
  chiavi: { name: 'Mazzo di Chiavi', desc: '+5 chiavi', pools: ['shop', 'treasure'], apply(p) { p.keys = Math.min(99, p.keys + 5); } },
  calamita: { name: 'Calamita', desc: 'Attira i bottini', pools: ['shop', 'treasure'], apply(p) { p.fx.magnet = true; } },
  goniometro: { name: 'Goniometro', desc: 'Spari anche dietro', pools: ['treasure'], apply(p) { p.fx.back = true; } },
  mattone: { name: 'Mattone Pieno', desc: 'Respinta potente', pools: ['treasure', 'boss'], apply(p) { p.fx.knock = 2.5; p.mods.dmgUp += 0.5; } },
  lampada: { name: 'Lampada da Tavolo', desc: 'Vedi le stanze speciali', pools: ['shop', 'treasure'], apply(p) { p.fx.compass = true; p.mods.luck += 1; } },
  pianta: { name: "Pianta dell'Edificio", desc: 'Mappa completa', pools: ['shop', 'treasure', 'secret'], apply(p) { p.fx.map = true; } },
  calamaio: { name: 'Calamaio', desc: "+2 cuori d'inchiostro", pools: ['shop', 'treasure', 'secret'], apply(p) { p.addSoul(4); } },
  tiramisu: { name: 'Tiramisù', desc: 'Vita su x2', pools: ['boss'], apply(p) { p.addContainers(2); p.heal(4); p.mods.speed -= 0.08; } },
  portamine: { name: 'Portamine Doppio', desc: 'Tiro doppio', pools: ['treasure', 'pact'], apply(p) { p.fx.double = true; } },
  esplosivo: { name: 'Inchiostro Esplosivo', desc: 'Tiri esplosivi', pools: ['pact'], apply(p) { p.fx.explode = true; p.mods.tearMult *= 0.75; } },
  // ------------- attivi (si usano col tasto e si ricaricano completando le stanze)
  moka: { name: 'Moka', desc: 'Cura 2 cuori', pools: ['shop', 'treasure'], active: true, charge: 4 },
  timbro: { name: "Timbro dell'Accademia", desc: 'Colpisce tutti i nemici', pools: ['treasure', 'pact'], active: true, charge: 3 },
  plotter: { name: 'Plotter', desc: 'Raffica a 360°', pools: ['treasure', 'shop'], active: true, charge: 2 },
  detonatore: { name: 'Dinamite', desc: 'Bomba gratis', pools: ['treasure', 'shop'], active: true, charge: 2 },
  clessidra: { name: 'Clessidra', desc: 'Rallenta il tempo', pools: ['treasure', 'shop'], active: true, charge: 3 },
  biglietto: { name: "Biglietto dell'Autobus", desc: 'Teletrasporto', pools: ['treasure', 'secret'], active: true, charge: 2 },
  taccuino: { name: 'Taccuino degli Schizzi', desc: 'Crea bottini', pools: ['treasure', 'shop'], active: true, charge: 3 },
  fissativo: { name: 'Fissativo Spray', desc: 'Invulnerabile per poco', pools: ['treasure', 'pact'], active: true, charge: 4 },
};

// ------------------------------------------------------------------ NEMICI
// hp di base (cresce coi piani), r = raggio, fly = vola sopra ostacoli, spr = sprite
const ENEMIES = {
  gnat: { name: 'Moscerino', hp: 5, r: 5, fly: true, spr: 'gnat', h: 10 },
  blot: { name: 'Macchia', hp: 16, r: 8, spr: 'blot', h: 0 },
  blotS: { name: 'Macchiolina', hp: 6, r: 5, spr: 'blotS', h: 0 },
  spider: { name: 'Ragnetto', hp: 8, r: 6, spr: 'spider', h: 0 },
  eye: { name: 'Occhio', hp: 13, r: 7, fly: true, spr: 'eye', h: 8, still: true },
  beetle: { name: 'Scarafaggio', hp: 20, r: 7, spr: 'beetle', h: 0 },
  ghost: { name: 'Spettro', hp: 14, r: 7, fly: true, ghost: true, spr: 'ghost', h: 6 },
  bat: { name: 'Pipistrello', hp: 7, r: 6, fly: true, spr: 'bat', h: 12 },
  mushroom: { name: 'Fungo', hp: 16, r: 7, spr: 'mushroom', h: 0, still: true },
  skull: { name: 'Teschio', hp: 12, r: 6, fly: true, spr: 'skull', h: 6 },
};

const FLOOR_POOLS = [
  { common: ['gnat', 'gnat', 'blot', 'spider', 'spider', 'bat'], heavy: ['blot', 'spider'], turret: ['blot'] },
  { common: ['gnat', 'blot', 'spider', 'bat', 'skull'], heavy: ['beetle', 'blot'], turret: ['eye'] },
  { common: ['spider', 'bat', 'skull', 'blot', 'ghost', 'gnat'], heavy: ['beetle', 'ghost'], turret: ['eye', 'mushroom'] },
  { common: ['skull', 'ghost', 'bat', 'spider', 'gnat', 'blot'], heavy: ['beetle', 'ghost', 'blot'], turret: ['eye', 'mushroom'] },
  { common: ['skull', 'ghost', 'bat', 'spider', 'beetle', 'blot'], heavy: ['beetle', 'ghost'], turret: ['eye', 'mushroom'] },
];

const BOSSES = {
  re: { name: 'RE MACCHIA', hp: 190, r: 18 },
  sciame: { name: 'MADRE SCIAME', hp: 170, r: 15, fly: true },
  tessitrice: { name: 'LA TESSITRICE', hp: 240, r: 16 },
  colosso: { name: 'IL COLOSSO', hp: 320, r: 17 },
  ombra: { name: "L'OMBRA DI MATTIA", hp: 950, r: 14 },
};
const FLOOR_BOSSES = [['re', 'sciame'], ['sciame', 'tessitrice', 're'], ['tessitrice', 'colosso'], ['colosso', 'tessitrice', 're'], ['ombra']];
const FLOOR_HP = [1, 1.35, 1.75, 2.2, 2.6];
const BOSS_HP = [1, 1.45, 1.9, 2.4, 1];

// ------------------------------------------------------------------ STANZE
// 13x7. Legenda: . vuoto, r roccia, x roccia segnata, c cassa, m metallo, ^ spuntoni,
// o fossa, f braciere, e nemico, E nemico robusto, g sciame di moscerini, t torretta
const LAYOUTS = [
  [".............", "..e.......e..", "...r.....r...", ".............", "...r.....r...", "..e.......e..", "............."],
  ["rr.........rr", "r...e...e...r", ".............", ".............", ".............", "r...e...e...r", "rr.........rr"],
  [".............", ".oo..e.e..oo.", ".oo.......oo.", ".............", ".oo.......oo.", ".oo..e.e..oo.", "............."],
  ["c.....e.....c", "..c.......c..", ".............", "..e.......e..", ".............", "..c.......c..", "c.....e.....c"],
  [".............", "...r..g..r...", "..rr.....rr..", ".............", "..rr.....rr..", "...r.....r...", "............."],
  [".............", ".r.........r.", "....t...t....", ".............", "....e...e....", ".r.........r.", "............."],
  [".............", "..^^^...^^^..", "..^e^...^e^..", "..^^^...^^^..", ".............", ".....e.e.....", "............."],
  [".............", ".rrrrr.rrrrr.", ".....e.e.....", ".............", ".....e.e.....", ".rrrrr.rrrrr.", "............."],
  ["e.....e.....e", ".mm.......mm.", ".m.........m.", ".............", ".m.........m.", ".mm.......mm.", "......E......"],
  ["f...........f", ".............", "..e.......e..", ".............", "..e.......e..", ".............", "f...........f"],
  [".............", "....r...r....", "..E.......E..", ".............", "..r.......r..", ".............", "......e......"],
  [".............", "..oooo.oooo..", "..o...t...o..", ".............", "..o.......o..", "..oooo.oooo..", "......e......"],
  ["x...........r", ".r....e....r.", "..r.......r..", ".............", "..r.......r..", ".r....e....r.", "r...........r"],
  [".............", ".ccc.....ccc.", ".c.e.....e.c.", ".............", ".c.........c.", ".ccc..E..ccc.", "............."],
  ["....r...r....", "....r.g.r....", "....r...r....", ".............", "....r...r....", "....r.e.r....", "....r...r...."],
  [".............", ".e.^^^.^^^.e.", ".............", ".............", ".............", ".e.^^^.^^^.e.", "............."],
  ["oo.........oo", "o...........o", "...t.....t...", ".............", "...e.....e...", "o...........o", "oo.........oo"],
  [".............", "..mm.....mm..", "..m..E.E..m..", ".............", "..m.......m..", "..mm.....mm..", "............."],
  ["r.r.r...r.r.r", ".............", "r...e...e...r", ".............", "r...e...e...r", ".............", "r.r.r...r.r.r"],
  [".............", ".....rrr.....", "...g.rxr.g...", ".............", ".....rrr.....", ".............", "............."],
  ["c...........c", ".c....t....c.", ".............", ".e.........e.", ".............", ".c.........c.", "c....e.e....c"],
  [".............", ".^^.......^^.", ".^^.e...e.^^.", ".............", ".^^.......^^.", ".^^...E...^^.", "............."],
  ["......e......", ".rr.......rr.", ".r..ooooo..r.", ".....o.o.....", ".r..ooooo..r.", ".rr.......rr.", "......e......"],
  [".............", "..f.......f..", ".....eee.....", ".............", ".............", "..f.......f..", "............."],
  ["mmm.......mmm", "m...e...e...m", "m...........m", ".............", "m...........m", "m...t...t...m", "mmm.......mmm"],
  [".............", "..g.......g..", ".............", ".............", ".............", "..g.......g..", "............."],
  ["x.....r.....c", "..e.......e..", "....r...r....", ".............", "....r...r....", "..E.......e..", "c.....r.....x"],
  [".............", ".oooooooooo..", ".....e.......", ".............", ".......e.....", "..oooooooooo.", "............."],
];

// Stanza iniziale: suggerimenti sul pavimento nel primo piano
const TEXTS = {
  intro: [
    "MATTIA STUDIA ARCHITETTURA.",
    "LA NOTTE PRIMA DELLA CONSEGNA, LA SUA TAVOLA",
    "FINALE SCOMPARE NELLE SEGRETE SOTTO L'ACCADEMIA.",
    "",
    "ARMATO SOLO DEL SUO INCHIOSTRO DI CHINA,",
    "SCENDE NEL BUIO PER RIPRENDERSELA...",
  ],
  help: [
    "MUOVITI: STICK A SINISTRA DELLO SCHERMO",
    "SPARA: STICK A DESTRA (4 DIREZIONI)",
    "BOMBA E OGGETTO ATTIVO: TASTI A DESTRA",
    "",
    "SCONFIGGI I NEMICI PER APRIRE LE PORTE.",
    "OGNI PIANO HA UN BOSS: BATTILO E SCENDI",
    "NELLA BOTOLA. SONO 5 PIANI IN TUTTO.",
    "",
    "STANZA DEL TESORO (ORO): UN OGGETTO GRATIS.",
    "DAL 2° PIANO SERVE UNA CHIAVE.",
    "NEGOZIO (MONETA): SPENDI LE MONETE.",
    "STANZE SEGRETE: FAI ESPLODERE I MURI CREPATI.",
    "DOPO IL BOSS PUÒ APRIRSI LA STANZA DEL PATTO:",
    "OGGETTI POTENTI IN CAMBIO DI CUORI.",
    "",
    "LE ROCCE SEGNATE NASCONDONO SORPRESE.",
    "TASTIERA: WASD MUOVI, FRECCE SPARA,",
    "E BOMBA, SPAZIO OGGETTO, ESC PAUSA.",
  ],
  win: [
    "MATTIA HA SCONFITTO LA SUA OMBRA",
    "E HA RECUPERATO LA TAVOLA FINALE.",
    "",
    "LA CONSEGNA È SALVA!",
  ],
};
