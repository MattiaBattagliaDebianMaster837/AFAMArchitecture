'use strict';
// Grafica: palette, sprite pixel-art disegnati a mano (griglie) e procedurali.

const PAL = {
  '.': null,
  // contorni e neutri
  k: '#140e10', K: '#1b1f3a', w: '#ffffff', g: '#b8bcc4', G: '#5c6064', W: '#e2e2e2',
  // Mattia (colori presi dalla foto)
  h: '#2c1d14', H: '#4a3324', L: '#6e5038', M: '#8a6a4c',
  s: '#c99470', S: '#a8714f', l: '#e0b490', b: '#23160f', p: '#140c08',
  m: '#9c5a52', z: '#8f6d55', r: '#6b2836', R: '#4a1823', q: '#8a3a4a',
  n: '#2a2833', f: '#151515',
};

// Colori per icone e oggetti
const IPAL = {
  '.': null, k: '#140e10', w: '#ffffff', g: '#b8bcc4', G: '#6a6e78', y: '#f2c94c', Y: '#b5861f',
  o: '#e8833a', r: '#d83a3a', R: '#8e1f2a', b: '#4a7fe0', B: '#23408f', c: '#f0e2c0',
  n: '#9a6038', N: '#5e3820', v: '#5cc85c', V: '#2d7a35', u: '#9a5ad8', U: '#5a2d8a',
  K: '#1b1f3a', p: '#f5a0b8', P: '#c0607e', s: '#d9a07a', t: '#79d0e0', T: '#3a8aa0',
  e: '#3a2a2e', f: '#ff6a2a', i: '#7d8fd8', x: '#2a2a34', d: '#8f6d55', h: '#4a3324', H: '#6e5038',
};

function makeCanvas(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.max(1, w | 0); c.height = Math.max(1, h | 0);
  const ctx = c.getContext('2d');
  ctx.imageSmoothingEnabled = false;
  return c;
}

function spriteFromGrid(grid, pal = PAL, overrides) {
  const h = grid.length, w = Math.max(...grid.map(r => r.length));
  const c = makeCanvas(w, h), ctx = c.getContext('2d');
  for (let y = 0; y < h; y++) for (let x = 0; x < grid[y].length; x++) {
    const ch = grid[y][x];
    const col = (overrides && ch in overrides) ? overrides[ch] : pal[ch];
    if (col) { ctx.fillStyle = col; ctx.fillRect(x, y, 1, 1); }
  }
  return c;
}

function flipH(src) {
  const c = makeCanvas(src.width, src.height), ctx = c.getContext('2d');
  ctx.translate(src.width, 0); ctx.scale(-1, 1); ctx.drawImage(src, 0, 0);
  return c;
}
function rot90(src, times) {
  times = ((times % 4) + 4) % 4;
  if (!times) return src;
  const sw = src.width, sh = src.height;
  const c = makeCanvas(times % 2 ? sh : sw, times % 2 ? sw : sh), ctx = c.getContext('2d');
  ctx.translate(c.width / 2, c.height / 2); ctx.rotate(times * Math.PI / 2);
  ctx.drawImage(src, -sw / 2, -sh / 2);
  return c;
}
function silhouette(src, color) {
  const c = makeCanvas(src.width, src.height), ctx = c.getContext('2d');
  ctx.drawImage(src, 0, 0);
  ctx.globalCompositeOperation = 'source-in';
  ctx.fillStyle = color; ctx.fillRect(0, 0, c.width, c.height);
  return c;
}
function tinted(src, color, alpha) {
  const c = makeCanvas(src.width, src.height), ctx = c.getContext('2d');
  ctx.drawImage(src, 0, 0);
  ctx.globalCompositeOperation = 'source-atop';
  ctx.globalAlpha = alpha; ctx.fillStyle = color; ctx.fillRect(0, 0, c.width, c.height);
  return c;
}
function scaled(src, s) {
  const c = makeCanvas(src.width * s, src.height * s), ctx = c.getContext('2d');
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(src, 0, 0, c.width, c.height);
  return c;
}

// Disegno pixel procedurale con contorno automatico
function PixelArt(w, h) {
  const c = makeCanvas(w, h), ctx = c.getContext('2d');
  const P = {
    c, ctx,
    px(x, y, col) { ctx.fillStyle = col; ctx.fillRect(x | 0, y | 0, 1, 1); },
    rect(x, y, ww, hh, col) { ctx.fillStyle = col; ctx.fillRect(x | 0, y | 0, ww | 0, hh | 0); },
    ellipse(cx, cy, rx, ry, col) {
      ctx.fillStyle = col;
      for (let y = -ry; y <= ry; y++) {
        const t = 1 - (y * y) / (ry * ry + 0.0001);
        if (t < 0) continue;
        const half = Math.round(rx * Math.sqrt(t) - 0.15);
        ctx.fillRect(Math.round(cx - half), Math.round(cy + y), half * 2 + 1, 1);
      }
    },
    circle(cx, cy, r, col) { P.ellipse(cx, cy, r, r, col); },
    line(x0, y0, x1, y1, col) {
      x0 |= 0; y0 |= 0; x1 |= 0; y1 |= 0;
      const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
      let err = dx + dy;
      ctx.fillStyle = col;
      for (;;) {
        ctx.fillRect(x0, y0, 1, 1);
        if (x0 === x1 && y0 === y1) break;
        const e2 = 2 * err;
        if (e2 >= dy) { err += dy; x0 += sx; }
        if (e2 <= dx) { err += dx; y0 += sy; }
      }
    },
    outline(col = '#140e10') {
      const img = ctx.getImageData(0, 0, w, h), d = img.data;
      const out = new Uint8Array(w * h);
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        if (d[(y * w + x) * 4 + 3] > 0) continue;
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const xx = x + dx, yy = y + dy;
          if (xx >= 0 && yy >= 0 && xx < w && yy < h && d[(yy * w + xx) * 4 + 3] > 0) { out[y * w + x] = 1; break; }
        }
      }
      ctx.fillStyle = col;
      for (let i = 0; i < w * h; i++) if (out[i]) ctx.fillRect(i % w, (i / w) | 0, 1, 1);
    },
  };
  return P;
}

// Generatore pseudo-casuale con seme (mulberry32)
function RNG(seed) {
  let a = seed >>> 0;
  const f = () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  f.int = (lo, hi) => lo + Math.floor(f() * (hi - lo + 1));
  f.pick = (arr) => arr[Math.floor(f() * arr.length)];
  f.chance = (p) => f() < p;
  f.shuffle = (arr) => { for (let i = arr.length - 1; i > 0; i--) { const j = Math.floor(f() * (i + 1)); [arr[i], arr[j]] = [arr[j], arr[i]]; } return arr; };
  return f;
}

// ---------------------------------------------------------------- MATTIA
const MATTIA = {
  headD: [
    "..........kk......",
    "........kkMLk.....",
    "...kk..kHkkk.kk...",
    "..kLHkkLHHHkkLHk..",
    ".kHLMHHLHHHHLMHHk.",
    "kHHLHHhHLMHHhLHHHk",
    "kLHHHhLHHHHLHHhHLk",
    "kHHhHLHHhHMHLHHhHk",
    "kLHHHsHLsHHsHLsHHk",
    "khHHslsssHsslssHhk",
    "kHhsbbbsssssbbbshk",
    "khSsspwssssspwssSk",
    "khSssppsssssppssSk",
    ".kSsssssSSsssssSk.",
    ".kzSssssssssssSzk.",
    "..kzsssmmmmsssSk..",
    "...kzzzsssszzzk...",
    "....kkkkkkkkkk....",
  ],
  headU: [
    "..........kk......",
    "........kkMLk.....",
    "...kk..kHkkk.kk...",
    "..kLHkkLHHHkkLHk..",
    ".kHLMHHLHHHHLMHHk.",
    "kHHLHHhHLMHHhLHHHk",
    "kLHHHhLHHHHLHHhHLk",
    "kHHhHLHHhHMHLHHhHk",
    "kLHHMHHLHHHHHLHHHk",
    "khHLHHhHHLMHHhHHhk",
    "kHhHHMHHhHHHLHHHhk",
    "khHhHHHLHHhHHMHhhk",
    ".khHhLHHhHHLHHhhk.",
    ".kShhhHhhhHhhhhSk.",
    "..kSShhhhhhhhSSk..",
    "...kSSsssssSSSk...",
    "....kkkkkkkkkk....",
  ],
  headL: [
    "...........kk.....",
    ".........kkMLk....",
    "....kk..kHkkk.kk..",
    "...kLHkkLHHHkkLHk.",
    "..kHLMHHLHHHHLMHHk",
    ".kHHLHHhHLMHHhLHHk",
    "kHLHHhLHHHHLHHhHLk",
    "kHHsHLHHhHMHLHHhHk",
    "kLsHsHHLHHHHLHHHHk",
    "kslsssHHLHHhHHhHhk",
    "kbbbsssSHHHLHHhhHk",
    "kpwssssSHHhHHhhhHk",
    "kppssssSShHHhhhhk.",
    "sssssssssShhhhhk..",
    "kSssssssssShhhk...",
    ".kmsssssssSShk....",
    "..kzzzzssSSkk.....",
    "...kkkkkkkk.......",
  ],
  bodyD: [
    ["....kkkkkk....", "...kRRssRRk...", "..krrrRRrrrk..", ".krqrrrrrrqrk.", ".krqrrrrrrqrk.", ".ksrrrrrrrrsk.", ".kkgWgGgWgGkk.", "..kGgGgGgGgk..", "..knnk..knnk..", "..kffk..kffk.."],
    ["....kkkkkk....", "...kRRssRRk...", "..krrrRRrrrk..", ".krqrrrrrrqrk.", ".krqrrrrrrqrk.", ".ksrrrrrrrrsk.", ".kkgWgGgWgGkk.", "..kGgGgGgGgk..", "..knnk...knk..", "..kffk...kfk.."],
    ["....kkkkkk....", "...kRRssRRk...", "..krrrRRrrrk..", ".krqrrrrrrqrk.", ".krqrrrrrrqrk.", ".ksrrrrrrrrsk.", ".kkgWgGgWgGkk.", "..kGgGgGgGgk..", "..knk...knnk..", "..kfk...kffk.."],
  ],
  bodyU: [
    ["....kkkkkk....", "...kRRRRRRk...", "..krrrrrrrrk..", ".krqrrrrrrqrk.", ".krqrrrrrrqrk.", ".ksrrrrrrrrsk.", ".kkgWgGgWgGkk.", "..kGgGgGgGgk..", "..knnk..knnk..", "..kffk..kffk.."],
    ["....kkkkkk....", "...kRRRRRRk...", "..krrrrrrrrk..", ".krqrrrrrrqrk.", ".krqrrrrrrqrk.", ".ksrrrrrrrrsk.", ".kkgWgGgWgGkk.", "..kGgGgGgGgk..", "..knnk...knk..", "..kffk...kfk.."],
    ["....kkkkkk....", "...kRRRRRRk...", "..krrrrrrrrk..", ".krqrrrrrrqrk.", ".krqrrrrrrqrk.", ".ksrrrrrrrrsk.", ".kkgWgGgWgGkk.", "..kGgGgGgGgk..", "..knk...knnk..", "..kfk...kffk.."],
  ],
  bodyL: [
    ["....kkkkkk....", "...kRRRssRk...", "...krrrrrrk...", "...krqqrrrk...", "...krrqrrrk...", "...krrsrrrk...", "...kgWgGgWk...", "...kGgGgGgk...", "....knnnk.....", "....kfffk....."],
    ["....kkkkkk....", "...kRRRssRk...", "...krrrrrrk...", "...krqqrrrk...", "...krrqrrrk...", "...krrsrrrk...", "...kgWgGgWk...", "...kGgGgGgk...", "...knkknk.....", "...kfk.kfk...."],
    ["....kkkkkk....", "...kRRRssRk...", "...krrrrrrk...", "...krqqrrrk...", "...krrqrrrk...", "...krrsrrrk...", "...kgWgGgWk...", "...kGgGgGgk...", "....knnnk.....", "....kfffk....."],
  ],
};

// Variante "occhi socchiusi" quando spara
function squint(grid) {
  return grid.map(r => r.replace(/pw/g, 'bb').replace(/wp/g, 'bb').replace(/pp/g, 'SS').replace(/kpw/g, 'kbb'));
}

const GFX = {};

function buildPlayerSprites(target, palOverride) {
  const mk = (g) => spriteFromGrid(g, PAL, palOverride);
  const hD = mk(MATTIA.headD), hU = mk(MATTIA.headU), hL = mk(MATTIA.headL);
  const hDs = mk(squint(MATTIA.headD)), hLs = mk(squint(MATTIA.headL));
  target.head = {
    down: hD, up: hU, left: hL, right: flipH(hL),
    downS: hDs, upS: hU, leftS: hLs, rightS: flipH(hLs),
  };
  target.body = {
    down: MATTIA.bodyD.map(mk), up: MATTIA.bodyU.map(mk),
    left: MATTIA.bodyL.map(mk), right: MATTIA.bodyL.map(g => flipH(mk(g))),
  };
}

// ---------------------------------------------------------------- PICKUP / HUD
const PICK_GRIDS = {
  heart: [".kk...kk.", "kwrk.krrk", "krrrkrrrk", "krrrrrrRk", ".krrrrRk.", "..krrRk..", "...kRk...", "....k...."],
  heartHalf: [".kk...kk.", "kwrk.keek", "krrrkeeek", "krrrkeeek", ".krrkeek.", "..krkek..", "...kkk...", "....k...."],
  heartEmpty: [".kk...kk.", "keek.keek", "keeekeeek", "keeeeeeek", ".keeeeek.", "..keeek..", "...kek...", "....k...."],
  ink: [".kk...kk.", "kibk.kbbk", "kbbbkbbbk", "kbbbbbbBk", ".kbbbbBk.", "..kbbBk..", "...kBk...", "....k...."],
  inkHalf: [".kk......", "kibk.....", "kbbbk....", "kbbbk....", ".kbbk....", "..kbk....", "...kk....", "....k...."],
  coin: ["..kkk..", ".kyyyk.", "kywyyYk", "kyyyyYk", "kyyyyYk", ".kYYYk.", "..kkk.."],
  coin5: ["..kkkk..", ".kggggk.", "kgwgggGk", "kggGgggk", "kggGgggk", "kgggggGk", ".kGGGGk.", "..kkkk.."],
  bomb: ["......kk.", ".....k.f.", "...kkkk..", "..kxxxxk.", ".kxwxxxxk", ".kxxxxxxk", ".kxxxxxxk", "..kxxxxk.", "...kkkk.."],
  key: [".kkk.......", "kywykkkkkkk", "kykykyyyyyk", "kyyykkkykyk", ".kkk...k.k."],
  chest: [".kkkkkkkkkkkk.", "knnnnnnnnnnnnk", "knNNNNNNNNNNnk", "knnnnnnnnnnnnk", "kkkkkkyykkkkkk", "knnnnnyYnnnnnk", "knNNNNNNNNNNnk", "knnnnnnnnnnnnk", "knNNNNNNNNNNnk", "knnnnnnnnnnnnk", ".kkkkkkkkkkkk."],
  chestGold: [".kkkkkkkkkkkk.", "kyyyyyyyyyyyyk", "kyYYYYYYYYYYyk", "kyyyyyyyyyyyyk", "kkkkkkxxkkkkkk", "kyyyyyxGyyyyyk", "kyYYYYYYYYYYyk", "kyyyyyyyyyyyyk", "kyYYYYYYYYYYyk", "kyyyyyyyyyyyyk", ".kkkkkkkkkkkk."],
  chestOpen: [".kkkkkkkkkkkk.", "kNNNNNNNNNNNNk", "kNxxxxxxxxxxNk", "kkkkkkkkkkkkkk", "knnnnnnnnnnnnk", "knNNNNNNNNNNnk", "knnnnnnnnnnnnk", "knNNNNNNNNNNnk", "knnnnnnnnnnnnk", ".kkkkkkkkkkkk."],
  pill: ["..kkk..", ".kwwrk.", "kwwwrrk", "kwwrrrk", ".krrrk.", "..kkk.."],
};

// ---------------------------------------------------------------- NEMICI
const ENEMY_GRIDS = {
  gnat: [
    [".tt...tt.", "tttt.tttt", ".ttkkktt.", "..kUUUk..", "..kRURk..", "..kUUUk..", "...kkk..."],
    [".........", ".........", "ttkkkkktt", "tttUUUttt", ".tkRURkt.", "..kUUUk..", "...kkk..."],
  ],
  blot: [
    [".....kkkkkk.....", "...kkKKKKKKkk...", "..kKKiKKKKKKKk..", ".kKKiiKKKKKKKKk.", ".kKKKKKKKKKKKKk.", "kKKKwwKKKKwwKKKk", "kKKKwkKKKKwkKKKk", "kKKKKKKKKKKKKKKk", "kKKKKKKKKKKKKKKk", "kKKKKKKkkKKKKKKk", ".kKKKKKKKKKKKKk.", "..kKKKKKKKKKKk..", "...kkkkkkkkkk..."],
  ],
  blotS: [
    ["...kkkk...", ".kkKiKKkk.", "kKiKKKKKKk", "kKwkKKwkKk", "kKKKKKKKKk", "kKKKkkKKKk", ".kKKKKKKk.", "..kkkkkk.."],
  ],
  spider: [
    ["k...........k", ".k..kkkkk..k.", "..kkNnnnNkk..", "k..kNRnRNk..k", ".kkkNnnnNkkk.", "k..kNNNNNk..k", "..kkkNNNkkk..", ".k...kkk...k.", "k...........k"],
    [".............", "kk..kkkkk..kk", "..kkNnnnNkk..", ".k.kNRnRNk.k.", "k.kkNnnnNkk.k", ".k.kNNNNNk.k.", "..kkkNNNkkk..", "kk...kkk...kk", "............."],
  ],
  beetle: [
    ["....kkkk....", "..kknnnnkk..", ".knnNnnNnnk.", "kknnNnnNnnkk", ".knnNnnNnnk.", "kknnNnnNnnkk", ".knnNnnNnnk.", "kknnNnnNnnkk", ".kknNnnNnkk.", "..kkkkkkkk..", "...kNNNNk...", "..kNRNNRNk..", "..k.kNNk.k..", ".k...kk...k."],
    ["....kkkk....", "..kknnnnkk..", "kknnNnnNnnkk", ".knnNnnNnnk.", "kknnNnnNnnkk", ".knnNnnNnnk.", "kknnNnnNnnkk", ".knnNnnNnnk.", ".kknNnnNnkk.", "..kkkkkkkk..", "...kNNNNk...", "..kNRNNRNk..", ".k..kNNk..k.", "k....kk....k"],
  ],
  ghost: [
    ["....kkkkkk....", "..kkccccccck..", ".kcccccccccck.", ".kcccccccccck.", "kcckkccccckkck", "kcckUccccckUck", "kccccccccccccck".slice(0, 14), "kcccccckkcccck", "kccccckxxkccck", "kcccccckkcccck", "kcccccccccccck", "kcccccccccccck", "kcckccckcccckk", "kck.kck.kck.k.", ".k...k...k...."],
    ["....kkkkkk....", "..kkccccccck..", ".kcccccccccck.", ".kcccccccccck.", "kcckkccccckkck", "kcckUccccckUck", "kcccccccccccck", "kcccccckkcccck", "kccccckxxkccck", "kcccccckkcccck", "kcccccccccccck", "kcccccccccccck", "kkccccckccckck", ".kck.kck.kck.k", "..k...k...k..."],
  ],
  bat: [
    ["k..............k", "kk............kk", "kUk...k..k...kUk", "kUUk..kkkk..kUUk", "kUUUkkUUUUkkUUUk", ".kUUUUUrrUUUUUk.", "..kkUUUUUUUUkk..", "....kkUkkUkk....", "......k..k......"],
    ["................", "................", "......k..k......", "......kkkk......", "..kkkkUUUUkkkk..", ".kUUUUUrrUUUUUk.", "kUUUkkUUUUkkUUUk", "kUUk..kkkk..kUUk", "kk....k..k....kk"],
  ],
  mushroom: [
    ["....kkkkkk....", "..kkrrwrrrkk..", ".krrwwrrrrwrk.", "krrrrrrwwrrrrk", "krwwrrrwwrrwwk", "krwwrrrrrrrwwk", ".kkkkkkkkkkkk.", "...kccccccck..", "...kckkckkck..", "...kccccccck..", "...kcccRccck..", "...kccccccck..", "....kkkkkkk..."],
    ["..............", "....kkkkkk....", "..kkrrwrrrkk..", ".krrwwrrrrwrk.", "krrrrrrwwrrrrk", "krwwrrrwwrrwwk", "krwwrrrrrrrwwk", ".kkkkkkkkkkkk.", "...kckkckkck..", "...kccccccck..", "...kcccRccck..", "...kccccccck..", "....kkkkkkk..."],
  ],
  skull: [
    ["..kkkkkkkk..", ".kccccccccck".slice(0, 12), "kccccccccccck".slice(0, 12), "kcccccccccck", "kckkkcckkkck", "kckukcckukck", "kckkkcckkkck", "kcccckkcccck", ".kcccccccck.", "..kckckckk..", "..kckckck...", "...kkkkk...."],
  ],
};
const ENEMY_PAL = Object.assign({}, IPAL, {
  t: 'rgba(200,220,240,0.75)', U: '#3a2a4a', R: '#e03838', K: '#232846', i: '#5a6ab0',
  n: '#6b3d24', N: '#3e2214', c: '#dfe8f0', x: '#2a1a2a', r: '#c8343c', u: '#b46cff',
});

function buildEnemySprites() {
  const E = {};
  for (const k in ENEMY_GRIDS) E[k] = ENEMY_GRIDS[k].map(g => spriteFromGrid(g, ENEMY_PAL));
  // teschio: ossa color avorio
  E.skull = ENEMY_GRIDS.skull.map(g => spriteFromGrid(g, Object.assign({}, ENEMY_PAL, { c: '#e8e0c8' })));
  // occhio fluttuante (procedurale)
  const eye = PixelArt(16, 16);
  eye.circle(8, 8, 6, '#f2e6e0');
  eye.ellipse(8, 10, 5, 3, '#e8cfc8');
  eye.circle(6, 5, 2, '#ffffff');
  eye.line(2, 8, 4, 7, '#c03040'); eye.line(13, 9, 11, 8, '#c03040'); eye.line(9, 14, 9, 12, '#c03040');
  eye.outline();
  E.eye = [eye.c];
  return E;
}

// ---------------------------------------------------------------- BOSS
function buildBossSprites() {
  const B = {};
  // Re Macchia: grande goccia d'inchiostro con corona
  {
    const P = PixelArt(48, 40);
    P.ellipse(24, 26, 21, 13, '#232846');
    P.ellipse(24, 22, 16, 13, '#232846');
    P.ellipse(17, 17, 5, 3, '#3a4680');
    P.ellipse(15, 16, 2, 1, '#6a7ac8');
    // occhi
    P.ellipse(17, 25, 3, 3, '#ffffff'); P.ellipse(31, 25, 3, 3, '#ffffff');
    P.rect(17, 25, 2, 2, '#140e10'); P.rect(31, 25, 2, 2, '#140e10');
    P.rect(14, 21, 6, 1, '#140e10'); P.rect(28, 21, 6, 1, '#140e10');
    // bocca
    P.rect(19, 31, 10, 2, '#140e10'); P.rect(20, 33, 2, 1, '#e8e0d0'); P.rect(26, 33, 2, 1, '#e8e0d0');
    // corona
    P.rect(16, 6, 16, 4, '#f2c94c');
    P.rect(16, 2, 3, 4, '#f2c94c'); P.rect(22, 0, 4, 6, '#f2c94c'); P.rect(29, 2, 3, 4, '#f2c94c');
    P.rect(17, 7, 14, 1, '#b5861f'); P.px(23, 3, '#d83a3a'); P.px(24, 3, '#d83a3a'); P.px(17, 4, '#4a7fe0'); P.px(30, 4, '#4a7fe0');
    P.outline();
    B.re = [P.c];
  }
  // Madre Sciame: grande mosca regina
  {
    const mk = (up) => {
      const P = PixelArt(44, 36);
      const wc = 'rgba(200,220,240,0.7)';
      if (up) { P.ellipse(10, 9, 9, 6, wc); P.ellipse(34, 9, 9, 6, wc); }
      else { P.ellipse(7, 20, 7, 8, wc); P.ellipse(37, 20, 7, 8, wc); }
      P.ellipse(22, 22, 12, 11, '#3a2a4a');
      P.ellipse(22, 27, 9, 6, '#2a1d38');
      for (let i = 0; i < 3; i++) P.rect(14, 25 + i * 3, 17, 1, '#5a3d6a');
      P.ellipse(15, 15, 5, 5, '#c8343c'); P.ellipse(29, 15, 5, 5, '#c8343c');
      P.rect(13, 13, 2, 2, '#ff9a9a'); P.rect(27, 13, 2, 2, '#ff9a9a');
      P.rect(19, 7, 7, 3, '#f2c94c'); P.px(19, 6, '#f2c94c'); P.px(22, 5, '#f2c94c'); P.px(25, 6, '#f2c94c');
      P.outline();
      return P.c;
    };
    B.sciame = [mk(true), mk(false)];
  }
  // Tessitrice: ragno gigante
  {
    const mk = (f) => {
      const P = PixelArt(56, 40);
      const leg = '#24140c';
      for (let i = 0; i < 4; i++) {
        const y = 12 + i * 5, s = (i % 2 === f ? 2 : -2);
        P.line(20, y, 8, y - 6 + s, leg); P.line(8, y - 6 + s, 2, y + 6 + s, leg);
        P.line(36, y, 48, y - 6 - s, leg); P.line(48, y - 6 - s, 54, y + 6 - s, leg);
      }
      P.ellipse(28, 26, 12, 11, '#3e2214');
      P.ellipse(28, 26, 8, 7, '#5a3420');
      P.rect(26, 21, 4, 10, '#c8343c'); P.rect(24, 24, 8, 3, '#c8343c');
      P.ellipse(28, 12, 8, 6, '#3e2214');
      for (const [x, y] of [[24, 11], [32, 11], [26, 9], [30, 9]]) P.rect(x - 1, y - 1, 2, 2, '#ff4040');
      P.rect(25, 17, 2, 3, '#e8e0d0'); P.rect(30, 17, 2, 3, '#e8e0d0');
      P.outline();
      return P.c;
    };
    B.tessitrice = [mk(0), mk(1)];
  }
  // Colosso: golem di pietra e mattoni
  {
    const mk = (f) => {
      const P = PixelArt(44, 48);
      const st = '#7a7068', sd = '#5a524c', sl = '#9a9088';
      P.ellipse(8, 26 + f, 7, 9, st); P.ellipse(36, 26 - f, 7, 9, st);
      P.ellipse(8, 34 + f, 6, 5, sd); P.ellipse(36, 34 - f, 6, 5, sd);
      P.ellipse(22, 28, 14, 14, st);
      P.ellipse(22, 31, 11, 9, sd);
      P.rect(14, 38, 6, 8, sd); P.rect(24, 38, 6, 8, sd);
      P.ellipse(22, 12, 10, 9, st);
      P.ellipse(20, 9, 5, 3, sl);
      P.rect(15, 12, 5, 2, '#ffb030'); P.rect(24, 12, 5, 2, '#ffb030');
      P.rect(18, 17, 8, 1, '#3a3430');
      // mattoni / crepe
      P.line(12, 24, 18, 28, '#4a423c'); P.line(28, 22, 32, 30, '#4a423c'); P.line(20, 34, 26, 34, '#4a423c');
      P.rect(17, 24, 4, 2, '#a05a3a'); P.rect(25, 30, 4, 2, '#a05a3a'); P.rect(10, 30, 3, 2, '#a05a3a');
      P.outline();
      return P.c;
    };
    B.colosso = [mk(0), mk(1)];
  }
  // Ombra di Mattia: Mattia in versione d'ombra, gigante
  {
    const sh = { h: '#0c0a14', H: '#16122a', L: '#221c3c', M: '#2e2650', s: '#2a2440', S: '#1e1a30', l: '#3a3258',
      b: '#0a0810', p: '#ff2a3a', w: '#ffd0d0', m: '#ff2a3a', z: '#1e1a30', r: '#1a1426', R: '#100c18', q: '#2a2040',
      g: '#2a2a3a', G: '#141420', W: '#3a3a50', n: '#0a0a10', f: '#000000', k: '#000000' };
    const tmp = {};
    buildPlayerSprites(tmp, sh);
    B.ombra = tmp;
  }
  return B;
}

// ---------------------------------------------------------------- ICONE OGGETTI (12x12)
const ITEM_ICONS = {
  caffe: ["....w..w....", "...w..w.....", "....w..w....", ".kkkkkkkk...", ".kNNNNNNkkk.", ".kwwwwwwk.k.", ".kwwwwwwkkk.", ".kgwwwwwk...", "..kgggwk....", "kkkkkkkkkkk.", "kgggggggggk.", ".kkkkkkkkk.."],
  cornetto: ["............", "....kkkk....", "..kkoyyokk..", ".koyyoyyyok.", "koyokkkkoyok", "kyok....kyok", "kok......kok", ".k........k.", "............", "............", "............", "............"],
  pizza: ["kkkkkkkkkkkk", "kNnNnnNnnnNk", "kyryyyyryyyk", ".kyyyryyyyk.", ".kyryyyyvyk.", "..kyyyryyk..", "..kyvyyyyk..", "...kyyryk...", "...kyyyyk...", "....kyrk....", "....kyyk....", ".....kk....."],
  china: ["....kkkk....", "....kNNk....", "....kNNk....", "...kkkkkk...", "..kKKKKKKk..", ".kKiKKKKKKk.", ".kKiKKKKKKk.", ".kKwwwwwwKk.", ".kKwkkkkwKk.", ".kKwwwwwwKk.", ".kKKKKKKKKk.", "..kkkkkkkk.."],
  rapido: ["..........kk", ".........kgk", "........kgk.", ".......kGk..", "......kKk...", ".....kKk....", "....kKk.....", "...kKk......", "..kkk.......", "..kk........", ".kk.........", "k..........."],
  squadra: ["k...........", "kk..........", "kgk.........", "kggk........", "kgkgk.......", "kgk.gk......", "kgk..gk.....", "kgk...gk....", "kgk....gk...", "kgkkkkkkgk..", "kggggggggk..", "kkkkkkkkkkk."],
  compasso: [".....kk.....", ".....kyk....", "....kyyk....", "....kGGk....", "...kGkkGk...", "...kGkkGk...", "..kGk..kGk..", "..kGk..kGk..", ".kGk....kGk.", ".kGk....kKk.", ".kgk.....kk.", "..k.........", ],
  scalimetro: ["..........kk", ".........kck", "........kcck", ".......kcckk", "......kcckk.", ".....kcckk..", "....kcckk...", "...kcckk....", "..kcckk.....", ".kcckk......", "kcckk.......", "kkkk........"],
  cutter: ["............", "........kk..", ".......kgwk.", "......kgwk..", ".....kkkk...", "....kyyyk...", "...kyyyk....", "..kyyYk.....", ".kyyYk......", "kyyYk.......", "kYYk........", ".kk........."],
  matita: ["..........kk", ".........kpk", "........kGk.", ".......kyyk.", "......kyYk..", ".....kyYk...", "....kyYk....", "...kyYk.....", "..kccck.....", "..kcck......", ".kKk........", "kk.........."],
  lucido: ["..kkkkkkkk..", "..kwwwwwwk..", "..kwgwwgwkk.", "..kwwwwwwkwk", "..kwgggwwkwk", "..kwwwwwwkwk", "..kwgwwgwkwk", "..kwwwwwwkwk", "..kkkkkkkkwk", "...kwwwwwwwk", "...kkkkkkkkk", "............"],
  specchio: ["...kkkkkk...", "..kttwwttk..", ".ktwwttttk..", ".kttttttTk..", ".kttttttTk..", ".kTttttTTk..", "..kTTTTTk...", "...kkkkk....", "....kNk.....", "....kNk.....", "....kNk.....", "....kkk....."],
  pennello: ["...........k", "..........kn", ".........knk", "........knk.", ".......knk..", "......knk...", ".....kgk....", "....kVvk....", "...kVvvk....", "..kVvvk.....", ".kvvVk......", ".kkkk......."],
  colla: ["....kkkk....", "....kook....", "....kook....", "...kkkkkk...", "..kwwwwwwk..", "..kwwwwwwk..", "..kwbbbbwk..", "..kwbwwbwk..", "..kwbbbbwk..", "..kwwwwwwk..", "..kgwwwwgk..", "...kkkkkk..."],
  accendino: ["....k.......", "...kfk......", "...kfyk.....", "..kfyyk.....", "...kyk......", "..kkkkkk....", "..kgGgGk....", "..kkkkkk....", "..krrrrk....", "..krRrrk....", "..krRrrk....", "..kkkkkk...."],
  tavola: ["kkkkkkkkkkkk", "kbbbbbbbbbbk", "kbwwwbbwbbbk", "kbwbwbbwbbbk", "kbwwwbbwwwbk", "kbbbbbbbbwbk", "kbwwwwwbbwbk", "kbwbbbwbbwbk", "kbwbbbwwwwbk", "kbwwwwwbbbbk", "kbbbbbbbbbbk", "kkkkkkkkkkkk"],
  lattina: ["...kkkkkk...", "..kgggggGk..", "..kkkkkkkk..", "..kvvvvvVk..", "..kvwvvvVk..", "..kvvkkvVk..", "..kvkykvVk..", "..kvvkkvVk..", "..kvwvvvVk..", "..kvvvvvVk..", "..kkkkkkkk..", "...kkkkkk..."],
  nottebianca: ["....kkkk....", "..kkyyyk....", ".kyyyk......", ".kyyk.....w.", "kyyk......k.", "kyyk..w.....", "kyyk........", "kyyyk.......", ".kyyykk..kk.", ".kYyyyykkyk.", "..kkYyyyyk..", "....kkkkk..."],
  modulor: [".....kk.....", "....kssk....", "....kssk....", "..kkkkkkkk..", ".kr.krrk.rk.", ".k..krrk..k.", "....krrk....", "....kBBk....", "...kBkkBk...", "...kBk.kBk..", "..kBk...kBk.", "..kk.....kk."],
  sezione: ["kkkkkkkkkkkk", "kyyyyyykyyyk", "kyyyyyykyyyk", "kyyyyyykyyyk", "kyyyyyykkkkk", "kyyyyyykykyk", "kyyyyyykyykk", "kkkkkkkkkkkk", "kyyyykyyyyyk", "kyyyykyyyyyk", "kyyyykyyyyyk", "kkkkkkkkkkkk"],
  colonna: ["kkkkkkkkkkkk", "kccccccccccK", ".kkkkkkkkkk.", "..kcgcgcgk..", "..kcgcgcgk..", "..kcgcgcgk..", "..kcgcgcgk..", "..kcgcgcgk..", "..kcgcgcgk..", ".kkkkkkkkkk.", "kccccccccccK", "kkkkkkkkkkkk"],
  cupola: [".....kk.....", ".....kk.....", "....kggk....", "..kkggggkk..", ".kgwggggggk.", ".kgwgggggGk.", "kggggggggGGk", "kkkkkkkkkkkk", "kcckcckcckck", "kcckcckcckck", "kcckcckcckck", "kkkkkkkkkkkk"],
  modellino: ["............", "....kkkk....", "..kkHHHHkk..", ".kHHhHHhHHk.", ".kHssssssHk.", ".kskpsspkks.", ".kssssssssk.", "..kssmmssk..", "...kkkkkk...", "...krrrrk...", "...kgggGk...", "...kk..kk..."],
  gomma: ["............", "............", "....kkkkkkk.", "...kpppppPk.", "..kpppppPPk.", ".kkkkkkkkPk.", ".kpppppkPPk.", ".kpppppkPk..", ".kbbbbbkk...", ".kBBBBBk....", ".kkkkkkk....", "............"],
  borsa: ["....kkk.....", "...kNnNk....", "....kkk.....", "...knnnk....", "..knnnnnk...", ".knnykynnk..", ".knnnyynnk..", ".knnykynnk..", ".knnnynnnk..", ".kNnnnnnNk..", "..kkkkkkk...", "............"],
  cartella: ["....kkkk....", "...kk..kk...", "kkkkkkkkkkkk", "kxxxxxxxxxxk", "kxGxxxxxxGxk", "kxxxxxxxxxxk", "kxxxkkkxxxxk", "kxxxkykxxxxk", "kxxxkkkxxxxk", "kxxxxxxxxxxk", "kxxxxxxxxxxk", "kkkkkkkkkkkk"],
  chiavi: ["...kkk......", "..kgGgk.....", "..kG.Gk.....", "..kgGgk.....", "...kyk.kkk..", "...kyk.kyk..", "...kykkyk...", "..kyykkyk...", "...kyk.kyyk.", "..kyyk.kyk..", "...kk..kyyk.", ".......kk..."],
  calamita: ["............", ".kkkk..kkkk.", ".krrk..kggk.", ".krrk..kggk.", ".krrk..kggk.", ".krrk..kggk.", ".krrkkkkggk.", ".krrrrrgggk.", "..krrrrggk..", "...kkkkkk...", "............", "............"],
  goniometro: ["............", "............", "....kkkk....", "..kkcccckk..", ".kccgccgcck.", ".kcgccccgck.", "kcgcckkccgck", "kccckwwkccck", "kckkkwwkkkck", "kkkkkkkkkkkk", "............", "............"],
  mattone: ["............", "............", "..kkkkkkkkk.", ".korroorrok.", "kkkkkkkkkok.", "korrorrorkk.", "kRRRRRRRRk..", "korrorrorkk.", "kRRRRRRRRk..", "kkkkkkkkkk..", "............", "............"],
  lampada: ["............", "...kkkkk....", "..kyyyyyk...", ".kyyyyyyyk..", "kkkkkkkkkkk.", "...wkw......", "..w.k.w.....", "....k.......", "....k.......", "....k.......", "..kkkkk.....", ".kGGGGGk...."],
  pianta: ["kkkkkkkkkkkk", "kbbbbbbbbbbk", "kbwwwwwwwwbk", "kbwbbbwbbwbk", "kbwbbbwbbwbk", "kbwwbwwbwwbk", "kbwbbbbbbwbk", "kbwbbbwwwwbk", "kbwbbbwbbwbk", "kbwwwwwbbwbk", "kbbbbbbbbbbk", "kkkkkkkkkkkk"],
  calamaio: ["......kk....", ".....kwk....", "....kwk.....", "...kwwk.....", "...kwk......", ".kkkkkkkk...", ".kbbbbbbk...", "kbibbbbbbk..", "kbbkbbkbbk..", "kbbbkkbbbk..", "kBbbbbbbBk..", ".kkkkkkkk..."],
  tiramisu: ["............", "............", "..kkkkkkkk..", ".knNnNnNnNk.", ".kccccccccck".slice(0, 12), ".kNNNNNNNNk.", ".kccccccccck".slice(0, 12), ".kNNNNNNNNk.", ".kccccccccck".slice(0, 12), ".kkkkkkkkkk.", "............", "............"],
  portamine: ["...........k", "..........kk", "...k.....kgk", "..kk....kbk.", "..kgk..kbk..", "...kbkkbk...", "....kbbk....", "...kbkkbk...", "..kbk..kgk..", ".kbk....kk..", "kgk.....k...", "kk.........."],
  esplosivo: ["......k.f...", ".....k.fyf..", ".....k..f...", "....kkkk....", "....kNNk....", "...kkkkkk...", "..kKKKKKKk..", ".kKiKKKKKKk.", ".kKKKrrKKKk.", ".kKKrrrrKKk.", ".kKKKrrKKKk.", "..kkkkkkkk.."],
  // attivi
  moka: ["....kkkk....", "...kgggGk...", "..kkkkkkkk..", "...kgggGk...", "...kgggGkkk.", "..kgggggGk.k", "..kkkkkkkk.k", "..kgggggGkkk", "..kgggggGk..", "..kgggggGk..", ".kgggggggGk.", ".kkkkkkkkkk."],
  timbro: ["....kkkk....", "...knnnnk...", "...knNNnk...", "....knnk....", "....knnk....", "....knnk....", "..kkkkkkkk..", ".kNnnnnnnNk.", ".kkkkkkkkkk.", ".krrrrrrrrk.", ".kkkkkkkkkk.", "............"],
  plotter: ["............", "..kkkkkkkk..", ".kwwwwwwwwk.", "kkkkkkkkkkkk", "kgggggggggGk", "kgkkkkkkgvGk", "kggggggggGGk", "kkkkkkkkkkkk", ".kwbwbwbwwk.", ".kwwwwwwwwk.", ".kkkkkkkkkk.", "............"],
  detonatore: [".........kf.", "........k.yf", ".......k..f.", "..kkkkkkk...", ".krrrrrrrk..", ".krwrrrrrk..", ".krrrrrrrk..", ".kRRRRRRRk..", ".kkkkkkkkk..", "............", "............", "............"],
  clessidra: ["kkkkkkkkkkkk", "knnnnnnnnnnk", ".kcyyyyyyck.", "..kcyyyyck..", "...kcyyck...", "....kcyk....", "....kcck....", "...kcccck...", "..kccyyccc..", ".kcyyyyyyck.", "knnnnnnnnnnk", "kkkkkkkkkkkk"],
  biglietto: ["............", "............", "kkkkkkkkkkkk", "kyyyyyyk.yyk", "kykkkkyyyyyk", "kyyyyyyk.yyk", "kykkkyyyyyyk", "kyyyyyyk.yyk", "kkkkkkkkkkkk", "............", "............", "............"],
  taccuino: ["kkkkkkkkkk..", "kwkcccccccck".slice(0, 12), "kwkcGGGGcck.", "kkkccccccck.", "kwkcGGGcGck.", "kwkcccccccck".slice(0, 12), "kkkcGGcGGck.", "kwkccccccck.", "kwkcGGGGcck.", "kkkccccccck.", "kkkkkkkkkkk.", "............"],
  fissativo: ["....kk......", "...kgGk..w..", "...kkkk.w.w.", "..kbbbbk.w..", "..kbwbbk....", "..kbwbbk....", "..kbbbbk....", "..kyyyyk....", "..kbbbbk....", "..kbbbbk....", "..kBBBBk....", "...kkkk....."],
};

function buildItemIcons() {
  const I = {};
  for (const k in ITEM_ICONS) I[k] = spriteFromGrid(ITEM_ICONS[k], IPAL);
  return I;
}

// ---------------------------------------------------------------- TEMI DEI PIANI
const THEMES = [
  { name: 'LE CANTINE', floor: ['#4a3b33', '#41332c', '#55453b'], grout: '#2e231e', wall: ['#5e4a3e', '#4a3a30', '#74604f'], rock: ['#7d7268', '#5f564e', '#9c9288'], dark: '#1e1612', light: 'rgba(255,190,120,0.10)' },
  { name: "L'ARCHIVIO SOMMERSO", floor: ['#323c48', '#2b343f', '#3a4552'], grout: '#1c232b', wall: ['#44505e', '#36404b', '#5a6878'], rock: ['#6f7884', '#535b66', '#8e98a4'], dark: '#10161c', light: 'rgba(140,190,255,0.10)' },
  { name: 'LE FONDAMENTA', floor: ['#38402e', '#30372a', '#434c37'], grout: '#20261a', wall: ['#4c5640', '#3d4533', '#647052'], rock: ['#76786a', '#5a5c50', '#94968a'], dark: '#12160e', light: 'rgba(170,255,140,0.08)' },
  { name: 'LE CATACOMBE', floor: ['#3e3442', '#352c39', '#4a3e4f'], grout: '#221b25', wall: ['#584a5c', '#46394a', '#6e5e72'], rock: ['#8a8070', '#6a6254', '#aca290'], dark: '#140e16', light: 'rgba(220,170,255,0.08)' },
  { name: "IL CUORE DELL'ACCADEMIA", floor: ['#442a2a', '#3a2323', '#523232'], grout: '#241212', wall: ['#5e3232', '#4a2626', '#7a4444'], rock: ['#6e5e5e', '#524444', '#8c7a7a'], dark: '#180a0a', light: 'rgba(255,120,100,0.10)' },
];

function buildTileSprites(theme, seed) {
  const R = RNG(seed);
  const T = 24;
  const S = {};
  // Rocce (3 varianti)
  S.rock = [0, 1, 2].map(v => {
    const P = PixelArt(T, T);
    const [base, dark, light] = theme.rock;
    P.ellipse(12, 14, 10, 8, dark);
    P.ellipse(12, 12, 10, 8, base);
    P.ellipse(9 + v, 9, 5, 3, light);
    if (v === 1) { P.ellipse(17, 15, 4, 3, dark); P.line(6, 14, 11, 17, dark); }
    if (v === 2) { P.line(14, 6, 17, 12, dark); P.ellipse(7, 16, 3, 2, dark); }
    if (v === 0) { P.line(5, 13, 9, 15, dark); P.px(16, 9, light); }
    P.outline();
    return P.c;
  });
  // Roccia segnata
  {
    const P = PixelArt(T, T);
    const [base, dark, light] = theme.rock;
    P.ellipse(12, 14, 10, 8, dark); P.ellipse(12, 12, 10, 8, base); P.ellipse(9, 9, 5, 3, light);
    P.line(8, 8, 15, 15, '#e0b040'); P.line(15, 8, 8, 15, '#e0b040'); P.line(9, 8, 16, 15, '#e0b040'); P.line(16, 8, 9, 15, '#e0b040');
    P.outline();
    S.xrock = P.c;
  }
  // Casse di legno (3 stati di danno)
  S.crate = [0, 1, 2].map(dmg => {
    const P = PixelArt(T, T);
    P.rect(2, 3, 20, 19, '#6e4428'); P.rect(2, 3, 20, 2, '#8e5a34'); P.rect(2, 20, 20, 2, '#4a2c18');
    P.rect(2, 3, 2, 19, '#8e5a34'); P.rect(20, 3, 2, 19, '#4a2c18');
    P.line(4, 5, 19, 19, '#4a2c18'); P.line(4, 6, 19, 20, '#8e5a34');
    P.rect(4, 11, 16, 2, '#4a2c18');
    if (dmg >= 1) { P.line(14, 4, 11, 9, '#2a180c'); P.rect(15, 15, 3, 2, '#2a180c'); }
    if (dmg >= 2) { P.line(6, 14, 10, 20, '#2a180c'); P.rect(5, 6, 3, 3, '#2a180c'); P.line(17, 6, 20, 10, '#2a180c'); }
    P.outline();
    return P.c;
  });
  // Blocco di metallo
  {
    const P = PixelArt(T, T);
    P.rect(1, 1, 22, 22, '#5a5e68'); P.rect(1, 1, 22, 2, '#8a8e98'); P.rect(1, 1, 2, 22, '#8a8e98');
    P.rect(1, 21, 22, 2, '#3a3e48'); P.rect(21, 1, 2, 22, '#3a3e48');
    for (const [x, y] of [[5, 5], [17, 5], [5, 17], [17, 17]]) { P.rect(x, y, 2, 2, '#2a2e38'); P.px(x, y, '#aab'); }
    P.rect(8, 8, 8, 8, '#4a4e58'); P.rect(8, 8, 8, 1, '#3a3e48');
    P.outline();
    S.metal = P.c;
  }
  // Spuntoni
  {
    const P = PixelArt(T, T);
    P.rect(2, 2, 20, 20, 'rgba(0,0,0,0.25)');
    for (const [x, y] of [[6, 6], [16, 6], [11, 11], [6, 16], [16, 16]]) {
      P.rect(x - 2, y + 1, 5, 2, '#3a3a40');
      P.rect(x - 1, y - 2, 3, 3, '#b0b4bc'); P.px(x, y - 4, '#dfe2e8'); P.px(x, y - 3, '#dfe2e8'); P.px(x + 1, y - 1, '#7a7e86');
    }
    S.spikes = P.c;
  }
  // Fossa
  {
    const P = PixelArt(T, T);
    P.rect(0, 0, T, T, '#07050a'); P.rect(0, 0, T, 4, '#1a1418'); P.rect(0, 4, T, 2, '#100c10');
    S.pit = P.c;
  }
  // Braciere (2 fotogrammi di fiamma)
  S.fire = [0, 1, 2].map(f => {
    const P = PixelArt(T, T + 6);
    P.ellipse(12, 24, 9, 4, '#3a3430'); P.ellipse(12, 23, 9, 3, '#5a524c'); P.rect(5, 20, 14, 3, '#5a524c');
    P.ellipse(12, 20, 7, 2, '#2a1810');
    const off = [0, 1, -1][f];
    P.ellipse(12 + off, 13, 6, 8, '#e84a20');
    P.ellipse(12 - off, 15, 4, 6, '#ff9a30');
    P.ellipse(12, 17, 2, 3, '#ffe080');
    P.px(10 + off, 4, '#e84a20'); P.px(15 - off, 6, '#ff9a30');
    P.outline('#2a0c04');
    return P.c;
  });
  S.fireOut = (() => {
    const P = PixelArt(T, T + 6);
    P.ellipse(12, 24, 9, 4, '#3a3430'); P.ellipse(12, 23, 9, 3, '#5a524c'); P.rect(5, 20, 14, 3, '#5a524c');
    P.ellipse(12, 20, 7, 2, '#1a1410'); P.px(10, 19, '#555'); P.px(13, 19, '#444');
    P.outline();
    return P.c;
  })();
  return S;
}

// Sfondo della stanza (muri + pavimento) per un tema
function renderRoomBackground(theme, seed, kind) {
  const T = 24, W = 360, H = 216;
  const c = makeCanvas(W, H), ctx = c.getContext('2d');
  const R = RNG(seed);
  // pavimento
  for (let ty = 0; ty < 7; ty++) for (let tx = 0; tx < 13; tx++) {
    const x = 24 + tx * T, y = 24 + ty * T;
    ctx.fillStyle = R.pick(theme.floor); ctx.fillRect(x, y, T, T);
    for (let i = 0; i < 10; i++) { ctx.fillStyle = R.chance(0.5) ? theme.floor[1] : theme.floor[2]; ctx.fillRect(x + R.int(1, 21), y + R.int(1, 21), R.int(1, 3), 1); }
    ctx.fillStyle = theme.grout; ctx.fillRect(x, y, T, 1); ctx.fillRect(x, y, 1, T);
    if (R.chance(0.12)) { ctx.fillStyle = theme.grout; let px = x + R.int(4, 18), py = y + R.int(4, 18); for (let i = 0; i < 6; i++) { ctx.fillRect(px, py, 1, 1); px += R.int(-1, 1); py += 1; } }
  }
  // fogli di progetto sparsi (tocco "architettura")
  const sheets = kind === 'boss' ? 1 : R.int(0, 2);
  for (let i = 0; i < sheets; i++) {
    const x = 30 + R.int(0, 280), y = 30 + R.int(0, 140), w = R.int(9, 14), h = R.int(7, 10);
    ctx.fillStyle = R.chance(0.5) ? 'rgba(70,110,190,0.55)' : 'rgba(225,215,190,0.45)';
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = 'rgba(255,255,255,0.45)';
    ctx.fillRect(x + 2, y + 2, w - 4, 1); ctx.fillRect(x + 2, y + 2, 1, h - 4); ctx.fillRect(x + w - 4, y + 3, 1, h - 5); ctx.fillRect(x + 2, y + h - 3, w - 5, 1);
  }
  // ombra interna dei muri
  for (let i = 0; i < 6; i++) {
    ctx.fillStyle = `rgba(0,0,0,${0.32 - i * 0.05})`;
    ctx.fillRect(24, 24 + i, 312, 1); ctx.fillRect(24 + i, 24, 1, 168);
    ctx.fillRect(24, 191 - i, 312, 1); ctx.fillRect(335 - i, 24, 1, 168);
  }
  // muri: mattoni
  const [wb, wd, wl] = theme.wall;
  ctx.fillStyle = wd; ctx.fillRect(0, 0, W, 24); ctx.fillRect(0, H - 24, W, 24); ctx.fillRect(0, 0, 24, H); ctx.fillRect(W - 24, 0, 24, H);
  const brick = (x, y, w, h) => {
    ctx.fillStyle = R.chance(0.15) ? wl : wb; ctx.fillRect(x + 1, y + 1, w - 1, h - 1);
    ctx.fillStyle = wl; ctx.fillRect(x + 1, y + 1, w - 1, 1);
    ctx.fillStyle = theme.dark; ctx.fillRect(x + 1, y + h - 1, w - 1, 1);
  };
  for (let row = 0; row < 4; row++) {
    const off = row % 2 ? 6 : 0;
    for (let x = -off; x < W; x += 12) { brick(x, row * 6, 12, 6); brick(x, H - 24 + row * 6, 12, 6); }
  }
  for (let col = 0; col < 4; col++) {
    const off = col % 2 ? 6 : 0;
    for (let y = 24 - off; y < H - 24; y += 12) { brick(col * 6, y, 6, 12); brick(W - 24 + col * 6, y, 6, 12); }
  }
  // bordo interno
  ctx.fillStyle = theme.dark;
  ctx.fillRect(22, 22, 316, 2); ctx.fillRect(22, 192, 316, 2); ctx.fillRect(22, 22, 2, 172); ctx.fillRect(336, 22, 2, 172);
  ctx.fillStyle = 'rgba(0,0,0,0.35)'; ctx.fillRect(0, 0, W, 2); ctx.fillRect(0, H - 2, W, 2); ctx.fillRect(0, 0, 2, H); ctx.fillRect(W - 2, 0, 2, H);
  // ragnatele negli angoli
  ctx.fillStyle = 'rgba(220,220,220,0.25)';
  for (let i = 0; i < 8; i++) { ctx.fillRect(24 + i, 24 + 7 - i, 1, 1); ctx.fillRect(24, 24 + i * 1, 1, 1); ctx.fillRect(24 + i, 24, 1, 1); }
  for (let i = 0; i < 6; i++) { ctx.fillRect(335 - i, 24 + 5 - i, 1, 1); }
  if (kind === 'pact') { ctx.fillStyle = 'rgba(120,0,20,0.35)'; ctx.fillRect(24, 24, 312, 168); }
  if (kind === 'shop') { ctx.fillStyle = 'rgba(150,90,40,0.18)'; ctx.fillRect(24, 24, 312, 168); }
  if (kind === 'secret') { ctx.fillStyle = 'rgba(0,0,0,0.3)'; ctx.fillRect(24, 24, 312, 168); }
  return c;
}

// Vignettatura (luce della stanza)
function renderVignette(w, h, tint) {
  const c = makeCanvas(w, h), ctx = c.getContext('2d');
  const g = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.25, w / 2, h / 2, Math.max(w, h) * 0.62);
  g.addColorStop(0, 'rgba(0,0,0,0)');
  g.addColorStop(1, 'rgba(0,0,0,0.55)');
  ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
  if (tint) {
    const g2 = ctx.createRadialGradient(w / 2, h / 2, 10, w / 2, h / 2, Math.min(w, h) * 0.6);
    g2.addColorStop(0, tint); g2.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g2; ctx.fillRect(0, 0, w, h);
  }
  return c;
}

// Proiettili
function buildShotSprites() {
  const S = { tear: {}, enemy: {} };
  for (let r = 2; r <= 8; r++) {
    const sz = r * 2 + 3;
    let P = PixelArt(sz, sz);
    P.circle(sz >> 1, sz >> 1, r, '#2b3f9e');
    P.circle((sz >> 1) - 1, (sz >> 1) - 1, Math.max(1, r - 2), '#4a66d8');
    P.rect((sz >> 1) - Math.ceil(r / 2), (sz >> 1) - Math.ceil(r / 2), Math.max(1, r >> 1), Math.max(1, r >> 1), '#dfe8ff');
    P.outline('#0a0c20');
    S.tear[r] = P.c;
    P = PixelArt(sz, sz);
    P.circle(sz >> 1, sz >> 1, r, '#9a1c24');
    P.circle((sz >> 1) - 1, (sz >> 1) - 1, Math.max(1, r - 2), '#d83a3a');
    P.rect((sz >> 1) - Math.ceil(r / 2), (sz >> 1) - Math.ceil(r / 2), Math.max(1, r >> 1), Math.max(1, r >> 1), '#ffb0a0');
    P.outline('#2a0606');
    S.enemy[r] = P.c;
  }
  // varianti colorate per effetti
  const fx = { poison: '#3ec84a', slow: '#d8d8e0', burn: '#ff7a2a', homing: '#c070ff', explode: '#ffcc40' };
  S.dark = {};
  for (let r = 2; r <= 8; r++) S.dark[r] = tinted(S.tear[r], '#8a1a5a', 0.45);
  S.fx = {};
  for (const k in fx) { S.fx[k] = {}; for (let r = 2; r <= 8; r++) S.fx[k][r] = tinted(S.tear[r], fx[k], 0.55); }
  return S;
}

function loadGfx() {
  buildPlayerSprites(GFX.player = {});
  GFX.pick = {};
  for (const k in PICK_GRIDS) GFX.pick[k] = spriteFromGrid(PICK_GRIDS[k], IPAL, { e: '#3a2a2e' });
  GFX.pick.ink = spriteFromGrid(PICK_GRIDS.ink, IPAL, { b: '#3a5ad0', B: '#22348a', i: '#9ab0ff' });
  GFX.pick.inkHalf = spriteFromGrid(PICK_GRIDS.inkHalf, IPAL, { b: '#3a5ad0', B: '#22348a', i: '#9ab0ff' });
  GFX.enemy = buildEnemySprites();
  GFX.boss = buildBossSprites();
  GFX.items = buildItemIcons();
  GFX.shot = buildShotSprites();
  GFX.flash = new Map();
  // piedistallo
  {
    const P = PixelArt(20, 14);
    P.rect(2, 2, 16, 4, '#8a8278'); P.rect(2, 2, 16, 1, '#aaa298'); P.rect(4, 6, 12, 6, '#6a625a'); P.rect(2, 11, 16, 3, '#8a8278');
    P.rect(5, 7, 1, 4, '#7a7268'); P.rect(14, 7, 1, 4, '#5a524a');
    P.outline();
    GFX.pedestal = P.c;
  }
  // botola
  {
    const P = PixelArt(28, 24);
    P.rect(0, 0, 28, 24, '#3a2618'); P.rect(2, 2, 24, 20, '#050307');
    P.rect(2, 2, 24, 3, '#120a10'); for (let i = 0; i < 4; i++) P.rect(4, 6 + i * 4, 20, 1, 'rgba(60,40,30,0.35)');
    P.rect(0, 0, 28, 1, '#5a3a24'); P.rect(0, 23, 28, 1, '#20140c');
    GFX.trapdoor = P.c;
  }
  // ritratto (dalla foto)
  GFX.portrait = new Image();
  GFX.portrait.src = 'img/ritratto.png';
}

// Sprite "lampeggiante" bianco (danno) con cache
function tintCached(src, color, alpha) {
  if (!GFX.tints) GFX.tints = new Map();
  let m = GFX.tints.get(src);
  if (!m) { m = new Map(); GFX.tints.set(src, m); }
  const k = color + alpha;
  let t = m.get(k);
  if (!t) { t = tinted(src, color, alpha); m.set(k, t); }
  return t;
}
function flashOf(src) {
  let f = GFX.flash.get(src);
  if (!f) { f = silhouette(src, '#ffffff'); GFX.flash.set(src, f); }
  return f;
}
