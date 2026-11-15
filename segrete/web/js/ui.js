'use strict';
// Rendering, HUD, menu, comandi touch/tastiera/gamepad e ciclo principale.

const cv = document.getElementById('game');
const ctx = cv.getContext('2d');
let LW = 600, LH = 270, SCALE = 4, OX = 0, OY = 0;
const SAFE = { l: 0, t: 0, r: 0, b: 0 };
let VIGNETTE = null, vignetteKey = '';
let TOUCH = ('ontouchstart' in window) || navigator.maxTouchPoints > 0;
let now = 0;

const G = { state: 'title', prev: 'title', menuSel: 0, confirmQuit: false, titleBg: null, introT: 0, endT: 0 };

// ------------------------------------------------------------------ opzioni e statistiche
const Opts = { music: true, sfx: true, vibra: true, aim: '4', swap: false };
const Stats = { runs: 0, wins: 0, best: 0, kills: 0 };
function loadSaved() {
  try { Object.assign(Opts, JSON.parse(localStorage.getItem('segrete_opts') || '{}')); } catch (e) { /* */ }
  try { Object.assign(Stats, JSON.parse(localStorage.getItem('segrete_stats') || '{}')); } catch (e) { /* */ }
}
function saveOpts() { try { localStorage.setItem('segrete_opts', JSON.stringify(Opts)); } catch (e) { /* */ } }
function saveStats() { try { localStorage.setItem('segrete_stats', JSON.stringify(Stats)); } catch (e) { /* */ } }

// ------------------------------------------------------------------ dimensioni schermo
function resize() {
  const dpr = window.devicePixelRatio || 1;
  const dw = Math.round(window.innerWidth * dpr), dh = Math.round(window.innerHeight * dpr);
  SCALE = Math.max(1, Math.floor(Math.min(dh / 232, dw / 380)));
  LW = Math.floor(dw / SCALE); LH = Math.floor(dh / SCALE);
  cv.width = LW; cv.height = LH;
  cv.style.width = (LW * SCALE / dpr) + 'px'; cv.style.height = (LH * SCALE / dpr) + 'px';
  cv.style.left = Math.floor((dw - LW * SCALE) / 2 / dpr) + 'px'; cv.style.top = Math.floor((dh - LH * SCALE) / 2 / dpr) + 'px';
  ctx.imageSmoothingEnabled = false;
  OX = Math.floor((LW - 360) / 2); OY = Math.floor((LH - 216) / 2) + (LH - 216 > 30 ? 4 : 0);
  try {
    if (window.Android && Android.insets) {
      const [l, t, r, b] = String(Android.insets()).split(',').map(Number);
      SAFE.l = Math.ceil(l / SCALE); SAFE.t = Math.ceil(t / SCALE); SAFE.r = Math.ceil(r / SCALE); SAFE.b = Math.ceil(b / SCALE);
    }
  } catch (e) { /* */ }
  vignetteKey = '';
}

function vignette() {
  const tint = run && run.floor ? run.floor.theme.light : null;
  const key = LW + 'x' + LH + tint;
  if (key !== vignetteKey) { VIGNETTE = renderVignette(LW, LH, tint); vignetteKey = key; }
  return VIGNETTE;
}

// ------------------------------------------------------------------ cache sprite vari
const CACHE = new Map();
function cached(key, fn) { let v = CACHE.get(key); if (!v) { v = fn(); CACHE.set(key, v); } return v; }
function shadowSprite(w) {
  w = Math.max(4, Math.round(w));
  return cached('sh' + w, () => { const P = PixelArt(w, Math.max(3, Math.round(w / 3))); P.ellipse(w >> 1, P.c.height >> 1, (w >> 1) - 1, Math.max(1, (P.c.height >> 1)), 'rgba(0,0,0,0.35)'); return P.c; });
}
function ringSprite(r, color, thick = 1) {
  return cached('ring' + r + color + thick, () => {
    const P = PixelArt(r * 2 + 3, r * 2 + 3);
    P.circle(r + 1, r + 1, r, color);
    P.ctx.globalCompositeOperation = 'destination-out';
    P.circle(r + 1, r + 1, r - thick, '#000');
    return P.c;
  });
}
function discSprite(r, color) { return cached('disc' + r + color, () => { const P = PixelArt(r * 2 + 3, r * 2 + 3); P.circle(r + 1, r + 1, r, color); return P.c; }); }
function drawCentered(c, img, x, y) { c.drawImage(img, Math.round(x - img.width / 2), Math.round(y - img.height / 2)); }

// ------------------------------------------------------------------ porte
const DOOR_COL = {
  normal: ['#7a6a5e', '#9a8a7e', '#4a3e36'],
  treasure: ['#e2b83c', '#fff0a0', '#9a7018'],
  shop: ['#c8803a', '#e8a050', '#7a4a1a'],
  boss: ['#d8d0c0', '#f4eee0', '#8a2028'],
  pact: ['#5a0a14', '#a01828', '#1a0408'],
  secret: ['#000', '#000', '#000'],
};
function doorSprite(kind, state, dir) {
  return cached('door' + kind + state + dir, () => {
    const P = PixelArt(36, 26);
    const [base, light, dark] = DOOR_COL[kind];
    if (kind === 'secret') {
      P.rect(6, 0, 24, 26, '#06040a');
      const rubble = ['#3a3030', '#2a2222', '#4a3e3a'];
      for (let i = 0; i < 26; i++) { P.px(5 + (i % 3 === 0 ? 1 : 0), i, rubble[i % 3]); P.px(30 - (i % 4 === 0 ? 1 : 0), i, rubble[(i + 1) % 3]); }
      for (let x = 6; x < 30; x += 2) P.px(x, 0 + (x % 4 === 0 ? 1 : 0), rubble[x % 3]);
    } else {
      // vano
      for (let y = 3; y < 26; y++) { const k = y / 26; P.rect(6, y, 24, 1, `rgb(${10 + k * 20 | 0},${6 + k * 14 | 0},${8 + k * 12 | 0})`); }
      if (state !== 'open') {
        const wood = kind === 'boss' ? ['#4a1418', '#6a2028', '#2a080c'] : kind === 'pact' ? ['#200810', '#3a1018', '#100408'] : kind === 'treasure' ? ['#7a5a22', '#9a7a32', '#4a3410'] : ['#5a3a22', '#7a5232', '#3a2412'];
        P.rect(7, 4, 22, 22, wood[0]);
        for (let x = 9; x < 29; x += 5) P.rect(x, 4, 1, 22, wood[2]);
        P.rect(7, 4, 22, 1, wood[1]); P.rect(17, 4, 2, 22, wood[2]);
        P.rect(7, 10, 22, 2, wood[2]); P.rect(7, 19, 22, 2, wood[2]);
        if (state === 'locked') {
          P.rect(14, 10, 8, 7, '#f2c94c'); P.rect(15, 7, 6, 4, '#b5861f'); P.rect(16, 8, 4, 3, wood[0]);
          P.rect(17, 12, 2, 3, '#3a2a10'); P.rect(14, 16, 8, 1, '#b5861f');
        }
      }
      // stipiti e architrave
      P.rect(0, 0, 6, 26, base); P.rect(30, 0, 6, 26, base); P.rect(0, 0, 36, 4, base);
      P.rect(0, 0, 36, 1, light); P.rect(0, 0, 1, 26, light); P.rect(30, 0, 1, 26, light);
      P.rect(5, 3, 1, 23, dark); P.rect(35, 0, 1, 26, dark); P.rect(6, 3, 24, 1, dark);
      if (kind === 'boss') {
        for (const x of [1, 31]) { P.rect(x + 1, 6, 3, 3, '#f4eee0'); P.px(x + 2, 7, '#140e10'); P.rect(x + 1, 14, 3, 2, '#c8343c'); P.rect(x + 1, 20, 3, 2, '#c8343c'); }
        P.rect(15, 0, 6, 3, '#c8343c'); P.px(16, 1, '#140e10'); P.px(19, 1, '#140e10');
      }
      if (kind === 'shop') { P.rect(15, 0, 6, 3, '#f2c94c'); P.px(17, 1, '#b5861f'); P.px(18, 1, '#b5861f'); }
      if (kind === 'treasure') { P.rect(14, 0, 8, 3, '#fff0a0'); P.px(15, 1, '#d83a3a'); P.px(20, 1, '#4a7fe0'); P.px(17, 1, '#5cc85c'); P.px(18, 1, '#5cc85c'); }
      if (kind === 'pact') { P.rect(16, 0, 4, 3, '#140e10'); P.px(17, 1, '#ff3030'); P.px(18, 1, '#ff3030'); }
      P.outline();
    }
    const times = { up: 0, right: 1, down: 2, left: 3 }[dir];
    return rot90(P.c, times);
  });
}
function crackSprite(dir) {
  return cached('crack' + dir, () => {
    const P = PixelArt(36, 26);
    const col = 'rgba(0,0,0,0.55)';
    P.line(12, 4, 16, 10, col); P.line(16, 10, 14, 15, col); P.line(16, 10, 22, 12, col); P.line(22, 12, 25, 19, col); P.line(20, 6, 22, 12, col);
    return rot90(P.c, { up: 0, right: 1, down: 2, left: 3 }[dir]);
  });
}
function drawDoor(c, room, dir, ox, oy) {
  const door = room.doors[dir];
  const pos = { up: [ox + 162, oy], down: [ox + 162, oy + 190], left: [ox, oy + 90], right: [ox + 334, oy + 90] }[dir];
  if (door.hidden) {
    if (door.kind === 'secret') c.drawImage(crackSprite(dir), pos[0], pos[1]);
    return;
  }
  // il tipo di porta è quello della stanza speciale
  const state = door.locked ? 'locked' : room.cleared ? 'open' : 'closed';
  c.drawImage(doorSprite(door.kind, state, dir), pos[0], pos[1]);
}

// ------------------------------------------------------------------ disegno del mondo
function drawWorld(c, ox, oy) {
  const room = run.room, fl = run.floor;
  if (!room.bg) room.bg = renderRoomBackground(fl.theme, room.seed, room.type);
  c.drawImage(room.bg, ox, oy);
  const ix = ox + 24, iy = oy + 24;
  if (room.decals) c.drawImage(room.decals, ix, iy);
  if (room.hints) drawHints(c, ix, iy);
  for (const dir in room.doors) drawDoor(c, room, dir, ox, oy);
  if (room.trapdoor) c.drawImage(GFX.trapdoor, ix + room.trapdoor.x - 14, iy + room.trapdoor.y - 12);
  // ostacoli
  const TS = fl.tiles;
  for (let i = 0; i < COLS * ROWS; i++) {
    const t = room.tiles[i];
    const x = ix + (i % COLS) * TILE, y = iy + ((i / COLS) | 0) * TILE;
    if (t === T_PIT) c.drawImage(TS.pit, x, y);
    else if (t === T_SPIKES) c.drawImage(TS.spikes, x, y);
  }
  if (room.fireOut) for (const i of room.fireOut) c.drawImage(TS.fireOut, ix + (i % COLS) * TILE, iy + ((i / COLS) | 0) * TILE - 6);
  for (let i = 0; i < COLS * ROWS; i++) {
    const t = room.tiles[i];
    const x = ix + (i % COLS) * TILE, y = iy + ((i / COLS) | 0) * TILE;
    if (t === T_ROCK) c.drawImage(TS.rock[room.tileVar[i] % 3], x, y);
    else if (t === T_XROCK) c.drawImage(TS.xrock, x, y);
    else if (t === T_CRATE) c.drawImage(TS.crate[clamp(3 - room.tileHp[i], 0, 2)], x, y);
    else if (t === T_METAL) c.drawImage(TS.metal, x, y);
    else if (t === T_FIRE) c.drawImage(TS.fire[Math.floor(now * 8 + i) % 3], x, y - 6);
  }
  // pericoli (massi che cadono)
  for (const h of run.hazards) {
    const k = clamp(1 - h.t / 1.2, 0.2, 1);
    c.globalAlpha = 0.3 + 0.4 * k; drawCentered(c, shadowSprite(h.r * 2 * k + 4), ix + h.x, iy + h.y); c.globalAlpha = 1;
    if (h.t < 0.35) drawCentered(c, TS.rock[0], ix + h.x, iy + h.y - h.t * 300 - 6);
  }
  // ombre
  const ents = [];
  for (const p of room.pickups) ents.push({ y: p.y, k: 'p', o: p });
  for (const pd of room.pedestals) ents.push({ y: pd.y, k: 'd', o: pd });
  for (const b of run.bombs) ents.push({ y: b.y, k: 'b', o: b });
  for (const e of run.enemies) ents.push({ y: e.y, k: 'e', o: e });
  for (const f of P.familiars) if (f.x != null) ents.push({ y: f.y, k: 'f', o: f });
  if (!run.fall || run.fall.t < 0.6) ents.push({ y: P.y, k: 'P', o: P });
  for (const en of ents) {
    const o = en.o;
    if (en.k === 'e') { const w = o.boss ? o.r * 2.4 / (1 + (o.z || 0) / 60) : o.r * 1.8; drawCentered(c, shadowSprite(w), ix + o.x, iy + o.y + 1); }
    else if (en.k === 'P') drawCentered(c, shadowSprite(12), ix + o.x, iy + o.y + 1);
    else if (en.k === 'p' || en.k === 'b') drawCentered(c, shadowSprite(8), ix + o.x, iy + o.y + 1);
  }
  ents.sort((a, b) => a.y - b.y);
  for (const en of ents) {
    const o = en.o;
    switch (en.k) {
      case 'p': drawPickup(c, o, ix, iy); break;
      case 'd': drawPedestal(c, o, ix, iy); break;
      case 'b': drawBomb(c, o, ix, iy); break;
      case 'e': if (o.boss) drawBoss(c, o, ix, iy); else drawEnemy(c, o, ix, iy); break;
      case 'f': drawFamiliar(c, o, ix, iy); break;
      case 'P': drawPlayer(c, ix, iy); break;
    }
  }
  // proiettili
  for (const t of run.tears) { const s = tearSprite(t); if (t.z > 3) drawCentered(c, shadowSprite(t.r * 2), ix + t.x, iy + t.y + 1); drawCentered(c, s, ix + t.x, iy + t.y - t.z); }
  for (const t of run.eshots) { const s = tearSprite(t); drawCentered(c, shadowSprite(t.r * 2), ix + t.x, iy + t.y + 1); drawCentered(c, s, ix + t.x, iy + t.y - t.z); }
  // particelle
  for (const p of run.parts) {
    c.globalAlpha = clamp(p.life / p.max * 1.5, 0, 1);
    c.fillStyle = p.color;
    c.fillRect(Math.round(ix + p.x - p.size / 2), Math.round(iy + p.y - p.z - p.size / 2), p.size, p.size);
  }
  c.globalAlpha = 1;
}

function drawHints(c, ix, iy) {
  const col = 'rgba(240,230,210,0.30)';
  if (TOUCH) {
    Font.draw(c, 'MUOVITI', ix + 52, iy + 120, col, { align: 'center' });
    Font.draw(c, 'SPARA', ix + 260, iy + 120, col, { align: 'center' });
    c.globalAlpha = 0.3;
    drawCentered(c, ringSprite(14, '#f0e6d2', 1), ix + 52, iy + 145);
    drawCentered(c, ringSprite(14, '#f0e6d2', 1), ix + 260, iy + 145);
    c.globalAlpha = 1;
  } else {
    Font.draw(c, 'WASD: MUOVITI', ix + 60, iy + 126, col, { align: 'center' });
    Font.draw(c, 'FRECCE: SPARA', ix + 252, iy + 126, col, { align: 'center' });
    Font.draw(c, 'E: BOMBA   SPAZIO: OGGETTO', ix + 156, iy + 148, col, { align: 'center' });
  }
  Font.draw(c, 'TROVA IL BOSS DEL PIANO', ix + 156, iy + 20, col, { align: 'center' });
}

function drawPickup(c, p, ix, iy) {
  let img = GFX.pick[p.type];
  if (p.type === 'heartHalf') img = cached('halfpick', () => { const k = makeCanvas(5, 8); k.getContext('2d').drawImage(GFX.pick.heart, 0, 0); return k; });
  if (!img) return;
  const bob = p.type === 'coin' || p.type === 'coin5' ? 0 : 0;
  const x = ix + p.x, y = iy + p.y - p.z - img.height / 2 - bob;
  let draw = img;
  if ((p.type === 'coin' || p.type === 'coin5') && Math.floor(now * 3 + p.x) % 4 === 0) draw = cached('shine' + p.type, () => tinted(img, '#ffffff', 0.35));
  drawCentered(c, draw, x, y);
  if (p.price) drawPrice(c, ix + p.x, iy + p.y + 6, p.price);
}
function drawPrice(c, x, y, price) {
  const w = Font.width(String(price)) + 9;
  c.drawImage(GFX.pick.coin, Math.round(x - w / 2), y);
  Font.draw(c, String(price), x - w / 2 + 9, y, P.coins >= price ? '#fff' : '#ff6060', { shadow: '#000' });
}
function drawPedestal(c, pd, ix, iy) {
  const x = ix + pd.x, y = iy + pd.y;
  drawCentered(c, GFX.pedestal, x, y + 2);
  if (pd.item) {
    const bob = Math.round(Math.sin(now * 3 + pd.x) * 1.5);
    const icon = GFX.items[pd.item];
    drawCentered(c, shadowSprite(10), x, y - 6);
    if (Math.floor(now * 2) % 7 === 0) drawCentered(c, cached('glow' + pd.item, () => tinted(icon, '#ffffff', 0.4)), x, y - 16 + bob);
    else drawCentered(c, icon, x, y - 16 + bob);
  }
  if (pd.item && pd.price) drawPrice(c, x, y + 9, pd.price);
  if (pd.item && pd.hprice) {
    for (let i = 0; i < pd.hprice; i++) c.drawImage(GFX.pick.heart, Math.round(x - pd.hprice * 5 + i * 10), y + 9);
  }
}
function drawBomb(c, b, ix, iy) {
  const img = (b.t < 0.6 && Math.floor(b.t * 20) % 2) ? flashOf(GFX.pick.bomb) : GFX.pick.bomb;
  const s = b.big ? 2 : 1;
  const w = img.width * s, h = img.height * s;
  const puff = 1 + Math.sin(b.t * 30) * 0.06 * (b.t < 0.6 ? 2 : 1);
  c.drawImage(img, Math.round(ix + b.x - w * puff / 2), Math.round(iy + b.y - h * puff), Math.round(w * puff), Math.round(h * puff));
}
function drawFamiliar(c, f, ix, iy) {
  if (f.kind === 'mini') drawCentered(c, GFX.items.modellino, ix + f.x, iy + f.y - 8 + Math.round(Math.sin(now * 6) * 1));
  else drawCentered(c, GFX.items.cupola, ix + f.x, iy + f.y - 8);
}

function drawPlayer(c, ix, iy) {
  if (P.inv > 0 && !run.dying && Math.floor(P.inv * 16) % 2 === 0) return;
  const sx = Math.round(ix + P.x), sy = Math.round(iy + P.y);
  let bodyDir = P.moving ? P.face : (P.shootT > 0 ? P.shootDir : P.face);
  const headDir = P.shootT > 0 ? P.shootDir : bodyDir;
  const frame = P.moving ? [1, 0, 2, 0][Math.floor(P.walkT * 8) % 4] : 0;
  const body = GFX.player.body[bodyDir][frame];
  const squint = P.shootT > 0.1;
  let head = GFX.player.head[headDir + (squint ? 'S' : '')];
  let bodyImg = body;
  if (run.dying) { head = flashOf(head); bodyImg = flashOf(body); }
  const fall = run.fall ? clamp(1 - run.fall.t / 0.6, 0, 1) : 1;
  if (fall < 1) {
    c.save(); c.translate(sx, sy); c.scale(fall, fall); c.translate(-sx, -sy);
  }
  const by = sy - 9;
  const bob = frame ? 1 : 0;
  c.drawImage(bodyImg, sx - 7, by);
  const hy = by - head.height + 1 + bob;
  c.drawImage(head, sx - 9, hy);
  if (P.godT > 0 && Math.floor(now * 10) % 2) { c.globalAlpha = 0.5; c.drawImage(cached('gold_' + headDir, () => silhouette(head, '#ffe680')), sx - 9, hy); c.globalAlpha = 1; }
  if (P.shield) { c.globalAlpha = 0.35 + Math.sin(now * 6) * 0.1; drawCentered(c, ringSprite(13, '#f5a0b8', 1), sx, sy - 12); c.globalAlpha = 1; }
  if (P.holdT > 0 && P.holdIcon) drawCentered(c, GFX.items[P.holdIcon], sx, hy - 8);
  if (fall < 1) c.restore();
}

function enemyFrame(e) {
  const frames = GFX.enemy[e.def.spr];
  const fps = { gnat: 14, bat: 8, spider: e.st === 'run' ? 12 : 3, beetle: e.st === 'charge' ? 14 : 5, ghost: 3, mushroom: 1, blot: 1, blotS: 1, skull: 1, eye: 1 }[e.type] || 4;
  let img = frames[Math.floor(e.anim * fps) % frames.length];
  if (e.type === 'mushroom') img = frames[e.squash > 0 ? 1 : 0];
  if (e.type === 'beetle') {
    const times = { down: 0, left: 1, up: 2, right: 3 }[e.dir];
    const key = 'beetle' + frames.indexOf(img) + times;
    img = cached(key, () => rot90(img, times));
  }
  return img;
}
function drawEnemy(c, e, ix, iy) {
  let img = enemyFrame(e);
  if (e.shadow) img = tintCached(img, '#100818', 0.75);
  if (e.champion) img = tintCached(img, e.champion, 0.45);
  if (e.flash > 0) img = flashOf(img);
  let alpha = e.type === 'ghost' ? 0.82 : 1;
  if (e.spawnT > 0) alpha *= clamp(1 - e.spawnT / 0.7, 0, 1);
  const hover = e.h ? e.h + Math.round(Math.sin(e.anim * 3) * 2) : 0;
  let w = img.width, h = img.height;
  let sq = 0;
  if (e.squash > 0) sq = e.squash * 1.2;
  if (e.type === 'blot' || e.type === 'blotS') { if (e.hopT > 0) sq = -0.15; }
  w = Math.round(w * (1 + sq)); h = Math.round(h * (1 - sq));
  const x = Math.round(ix + e.x - w / 2), y = Math.round(iy + e.y - h + 3 - hover - (e.z || 0));
  c.globalAlpha = alpha;
  c.drawImage(img, x, y, w, h);
  if (e.type === 'eye' && e.flash <= 0) {
    const a = Math.atan2(P.y - e.y, P.x - e.x);
    const cx = x + 8 + Math.round(Math.cos(a) * 2.5), cy = y + 8 + Math.round(Math.sin(a) * 2.5);
    c.fillStyle = '#c8343c'; c.fillRect(cx - 2, cy - 2, 4, 4);
    c.fillStyle = '#140e10'; c.fillRect(cx - 1, cy - 1, 2, 2);
  }
  c.globalAlpha = 1;
  if (e.st === 'prep' && Math.floor(now * 20) % 2) { c.fillStyle = '#fff'; c.fillRect(Math.round(ix + e.x) - 1, y - 6, 2, 4); }
}

function drawBoss(c, e, ix, iy) {
  const B = GFX.boss;
  c.globalAlpha = e.spawnT > 0 ? clamp(1 - e.spawnT / 0.4, 0, 1) : (e.alpha == null ? 1 : e.alpha);
  if (e.bk === 'ombra') {
    // Mattia d'ombra, doppia dimensione
    const dx = P.x - e.x, dy = P.y - e.y;
    const dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');
    const moving = e.phase === 1;
    const fr = moving ? [1, 0, 2, 0][Math.floor(e.anim * 6) % 4] : 0;
    let body = B.ombra.body[dir][fr], head = B.ombra.head[dir];
    if (e.flash > 0) { body = flashOf(body); head = flashOf(head); }
    const sx = Math.round(ix + e.x), sy = Math.round(iy + e.y);
    const float = Math.round(Math.sin(now * 2) * 2);
    c.drawImage(body, sx - 14, sy - 18 + float, 28, 20);
    c.drawImage(head, sx - 18, sy - 18 - head.height * 2 + 2 + float, 36, head.height * 2);
    if (Math.random() < 0.5) part(e.x + rnd(-14, 14), e.y - rnd(0, 50), 0, -15, 0.6, '#2a1840', 2);
    c.globalAlpha = 1;
    return;
  }
  const frames = B[e.bk];
  let img = frames[Math.floor(e.anim * (e.bk === 'sciame' ? 12 : e.bk === 'tessitrice' ? (e.st === 'dash' ? 12 : 3) : 3)) % frames.length];
  if (e.flash > 0) img = flashOf(img);
  let w = img.width, h = img.height;
  const sq = (e.squash > 0 ? e.squash : 0) * 0.8 + (e.st === 'hop' ? -0.08 : 0) + (e.st === 'raise' ? -0.06 : 0);
  w = Math.round(w * (1 + sq)); h = Math.round(h * (1 - sq));
  const hover = e.bk === 'sciame' ? 14 + Math.round(Math.sin(now * 4) * 2) : 0;
  let shakeX = (e.st === 'prep' || e.st === 'raise') ? Math.round(rnd(-1, 1)) : 0;
  c.drawImage(img, Math.round(ix + e.x - w / 2) + shakeX, Math.round(iy + e.y - h + 6 - hover - (e.z || 0)), w, h);
  c.globalAlpha = 1;
}

function snapshotRoom() {
  const s = makeCanvas(360, 216);
  drawWorld(s.getContext('2d'), 0, 0);
  return s;
}

// ------------------------------------------------------------------ HUD
function drawHUD() {
  const hx = Math.max(SAFE.l, 6) + 4, hy = 7;
  // cuori
  let k = 0;
  const containers = P.maxHp / 2;
  for (let i = 0; i < containers; i++, k++) {
    const img = P.hp >= (i + 1) * 2 ? GFX.pick.heart : P.hp === i * 2 + 1 ? GFX.pick.heartHalf : GFX.pick.heartEmpty;
    ctx.drawImage(img, hx + (k % 6) * 10, hy + Math.floor(k / 6) * 9);
  }
  for (let i = 0; i < Math.ceil(P.soul / 2); i++, k++) {
    const img = P.soul >= (i + 1) * 2 ? GFX.pick.ink : GFX.pick.inkHalf;
    ctx.drawImage(img, hx + (k % 6) * 10, hy + Math.floor(k / 6) * 9);
  }
  if (P.shield) { ctx.globalAlpha = 0.8; ctx.drawImage(GFX.items.gomma, hx + (k % 6) * 10 - 1, hy + Math.floor(k / 6) * 9 - 2); ctx.globalAlpha = 1; }
  const cy = hy + (k > 6 ? 22 : 13);
  const pad = (n) => (n < 10 ? '0' : '') + n;
  ctx.drawImage(GFX.pick.coin, hx + 1, cy + 1); Font.draw(ctx, pad(P.coins), hx + 12, cy + 1, '#fff', { shadow: '#000' });
  ctx.drawImage(GFX.pick.bomb, hx, cy + 10); Font.draw(ctx, pad(P.bombs), hx + 12, cy + 12, '#fff', { shadow: '#000' });
  ctx.drawImage(GFX.pick.key, hx - 1, cy + 23); Font.draw(ctx, pad(P.keys), hx + 12, cy + 22, '#fff', { shadow: '#000' });
  // attivo (desktop: mostrato a sinistra)
  if (!TOUCH && P.active) drawActiveIcon(hx + 6, cy + 46);
  // minimappa
  drawMinimap(LW - Math.max(SAFE.r, 6) - 4 - MAP_W * 8, 6, 8, 6, 0.85);
  // barra del boss
  const b = run.bossRef;
  if (b && !b.dead && run.room.type === 'boss') {
    const w = 150, x = Math.round(LW / 2 - w / 2), y = OY + 216 - 13;
    ctx.fillStyle = '#140e10'; ctx.fillRect(x - 2, y - 2, w + 4, 8);
    ctx.fillStyle = '#4a1018'; ctx.fillRect(x, y, w, 4);
    ctx.fillStyle = '#e03040'; ctx.fillRect(x, y, Math.round(w * clamp(b.hp / b.maxHp, 0, 1)), 4);
    ctx.fillStyle = '#ff8a8a'; ctx.fillRect(x, y, Math.round(w * clamp(b.hp / b.maxHp, 0, 1)), 1);
    Font.draw(ctx, b.name, LW / 2, y - 11, '#f0e0d0', { align: 'center', shadow: '#000' });
  }
  // stendardi
  if (run.floorBanner > 0 && !run.bossIntro) {
    const a = clamp(Math.min(run.floorBanner, 3 - run.floorBanner) * 2, 0, 1);
    ctx.globalAlpha = a;
    ctx.fillStyle = 'rgba(0,0,0,0.6)'; ctx.fillRect(0, OY + 70, LW, 44);
    Font.draw(ctx, 'PIANO ' + (run.floorIdx + 1), LW / 2, OY + 76, '#c8b8a8', { align: 'center' });
    Font.draw(ctx, run.floor.theme.name, LW / 2, OY + 90, '#fff', { align: 'center', scale: 2, shadow: '#000' });
    ctx.globalAlpha = 1;
  }
  if (run.banner) {
    const bn = run.banner, a = clamp(Math.min(bn.t, 2.6 - bn.t) * 3, 0, 1);
    ctx.globalAlpha = a;
    const sc = Font.width(bn.title, 2) < 340 ? 2 : 1;
    ctx.fillStyle = 'rgba(0,0,0,0.6)'; ctx.fillRect(OX + 24, OY + 30, 312, sc === 2 ? 34 : 26);
    Font.draw(ctx, bn.title, LW / 2, OY + 34, '#fff6d0', { align: 'center', scale: sc, shadow: '#000' });
    if (bn.sub) Font.draw(ctx, bn.sub, LW / 2, OY + 34 + (sc === 2 ? 18 : 11), '#d0c8c0', { align: 'center' });
    ctx.globalAlpha = 1;
  }
  if (FX.slowT > 0) Font.draw(ctx, 'TEMPO RALLENTATO', LW / 2, OY - 10 > 2 ? OY - 10 : OY + 4, '#9ad0ff', { align: 'center' });
  if (TOUCH) drawTouchControls();
  else Font.draw(ctx, 'ESC: PAUSA', LW - Math.max(SAFE.r, 6) - 4, LH - 12, 'rgba(255,255,255,0.4)', { align: 'right' });
}

function drawActiveIcon(x, y) {
  const a = P.active;
  const icon = GFX.items[a.id];
  const full = a.charge >= a.max;
  if (full && Math.floor(now * 4) % 2) drawCentered(ctx, cached('glow' + a.id, () => tinted(icon, '#ffffff', 0.4)), x, y);
  else drawCentered(ctx, icon, x, y);
  const bh = 16, bx = x + 9, by = y - 8;
  ctx.fillStyle = '#140e10'; ctx.fillRect(bx, by, 4, bh + 2);
  const seg = bh / a.max;
  for (let i = 0; i < a.max; i++) {
    ctx.fillStyle = i < a.charge ? (full ? '#f2c94c' : '#5cc85c') : '#3a3034';
    ctx.fillRect(bx + 1, Math.round(by + 1 + bh - (i + 1) * seg), 2, Math.max(1, Math.round(seg) - 1));
  }
}

function drawMinimap(mx, my, cw, ch, alpha) {
  const fl = run.floor;
  ctx.globalAlpha = alpha;
  ctx.fillStyle = 'rgba(10,6,8,0.55)';
  // riquadro solo attorno alle stanze note
  let x0 = 99, y0 = 99, x1 = -1, y1 = -1;
  for (const r of fl.list) if (r.seen || r.visited) { x0 = Math.min(x0, r.gx); y0 = Math.min(y0, r.gy); x1 = Math.max(x1, r.gx); y1 = Math.max(y1, r.gy); }
  if (x1 >= 0) ctx.fillRect(mx + x0 * cw - 3, my + y0 * ch - 3, (x1 - x0 + 1) * cw + 5, (y1 - y0 + 1) * ch + 5);
  for (const r of fl.list) {
    if (!r.seen && !r.visited) continue;
    const x = mx + r.gx * cw, y = my + r.gy * ch;
    ctx.fillStyle = r === run.room ? '#ffffff' : r.visited ? '#a89c94' : '#4a4246';
    ctx.fillRect(x, y, cw - 1, ch - 1);
    const ic = { treasure: '#f2c94c', shop: '#e0903a', boss: '#e03040', secret: '#9a9aa8', pact: '#7a1020' }[r.type];
    if (ic) { ctx.fillStyle = r === run.room ? '#140e10' : ic; ctx.fillRect(x + (cw >> 1) - 2, y + (ch >> 1) - 1, 3, 2); if (cw > 9) ctx.fillRect(x + (cw >> 1) - 3, y + (ch >> 1) - 2, 5, 4); }
  }
  ctx.globalAlpha = 1;
}

// ------------------------------------------------------------------ comandi touch
const Touch = { move: null, shoot: null, btn: new Map() };
const STICK_R = 22;
function ctrlLayout() {
  const rx = LW - Math.max(SAFE.r, 4) - 24;
  return {
    pause: { x: LW / 2, y: Math.max(10, Math.min(OY - 2, 16)), r: 11 },
    active: { x: rx, y: Math.round(LH * 0.40), r: 17 },
    bomb: { x: rx - (P && P.active ? 4 : 0), y: Math.round(LH * 0.40) + (P && P.active ? 40 : 0), r: 15 },
    moveHome: { x: Math.max(SAFE.l, 6) + 44, y: LH - 44 },
    shootHome: { x: LW - Math.max(SAFE.r, 6) - 44, y: LH - 44 },
  };
}
function drawTouchControls() {
  const L = ctrlLayout();
  // pausa
  ctx.globalAlpha = 0.7;
  drawCentered(ctx, discSprite(8, 'rgba(20,14,16,0.6)'), L.pause.x, L.pause.y);
  ctx.fillStyle = '#e0d8d0'; ctx.fillRect(Math.round(L.pause.x) - 3, L.pause.y - 3, 2, 7); ctx.fillRect(Math.round(L.pause.x) + 1, L.pause.y - 3, 2, 7);
  // bomba
  ctx.globalAlpha = P.bombs > 0 ? 0.85 : 0.35;
  drawCentered(ctx, discSprite(L.bomb.r, 'rgba(20,14,16,0.55)'), L.bomb.x, L.bomb.y);
  drawCentered(ctx, ringSprite(L.bomb.r, '#e0d8d0', 1), L.bomb.x, L.bomb.y);
  drawCentered(ctx, GFX.pick.bomb, L.bomb.x, L.bomb.y - 2);
  Font.draw(ctx, String(P.bombs), L.bomb.x + 7, L.bomb.y + 3, '#fff', { shadow: '#000' });
  // attivo
  if (P.active) {
    const full = P.active.charge >= P.active.max;
    ctx.globalAlpha = full ? 0.95 : 0.6;
    drawCentered(ctx, discSprite(L.active.r, 'rgba(20,14,16,0.55)'), L.active.x, L.active.y);
    drawCentered(ctx, ringSprite(L.active.r, full ? '#f2c94c' : '#e0d8d0', 1), L.active.x, L.active.y);
    ctx.globalAlpha = 1;
    drawActiveIcon(L.active.x - 3, L.active.y);
  }
  ctx.globalAlpha = 1;
  // stick
  const drawStick = (s, home) => {
    if (s) {
      ctx.globalAlpha = 0.5;
      drawCentered(ctx, ringSprite(STICK_R, '#f0e6d2', 1), s.bx, s.by);
      ctx.globalAlpha = 0.7;
      drawCentered(ctx, discSprite(9, '#f0e6d2'), s.x, s.y);
      ctx.globalAlpha = 1;
    } else {
      ctx.globalAlpha = 0.16;
      drawCentered(ctx, ringSprite(STICK_R, '#f0e6d2', 1), home.x, home.y);
      drawCentered(ctx, discSprite(9, '#f0e6d2'), home.x, home.y);
      ctx.globalAlpha = 1;
    }
  };
  drawStick(Touch.move, Opts.swap ? L.shootHome : L.moveHome);
  drawStick(Touch.shoot, Opts.swap ? L.moveHome : L.shootHome);
}
function stickVec(s) {
  if (!s) return [0, 0];
  let dx = (s.x - s.bx) / STICK_R, dy = (s.y - s.by) / STICK_R;
  const l = Math.hypot(dx, dy);
  if (l > 1) { dx /= l; dy /= l; }
  return [dx, dy];
}

// ------------------------------------------------------------------ input
const Keys = {};
const Edge = { bomb: false, active: false, tap: false, pause: false };
let arrowOrder = [];
let UIB = [];
function toLogical(e) {
  const r = cv.getBoundingClientRect();
  return [(e.clientX - r.left) / r.width * LW, (e.clientY - r.top) / r.height * LH];
}
function onPointerDown(e) {
  e.preventDefault();
  if (e.pointerType === 'touch' && !TOUCH) TOUCH = true;
  Sound.init();
  const [x, y] = toLogical(e);
  try { cv.setPointerCapture(e.pointerId); } catch (er) { /* */ }
  if (G.state === 'play' && run && !run.dying && !run.won) {
    Edge.tap = true;
    const L = ctrlLayout();
    const hit = (b) => Math.hypot(x - b.x, y - b.y) <= b.r + 5;
    if (Math.abs(x - L.pause.x) < 16 && y < L.pause.y + 14) { pauseGame(); return; }
    if (hit(L.bomb)) { Edge.bomb = true; Touch.btn.set(e.pointerId, 'bomb'); return; }
    if (P.active && hit(L.active)) { Edge.active = true; Touch.btn.set(e.pointerId, 'active'); return; }
    const left = x < LW / 2;
    const isMove = Opts.swap ? !left : left;
    const s = { id: e.pointerId, bx: x, by: y, x, y };
    if (isMove && !Touch.move) Touch.move = s;
    else if (!isMove && !Touch.shoot) Touch.shoot = s;
    return;
  }
  uiClick(x, y);
}
function onPointerMove(e) {
  const [x, y] = toLogical(e);
  for (const s of [Touch.move, Touch.shoot]) {
    if (!s || s.id !== e.pointerId) continue;
    s.x = x; s.y = y;
    const dx = x - s.bx, dy = y - s.by, d = Math.hypot(dx, dy), max = STICK_R * 1.25;
    if (d > max) { s.bx = x - dx / d * max; s.by = y - dy / d * max; }
  }
}
function onPointerUp(e) {
  if (Touch.move && Touch.move.id === e.pointerId) Touch.move = null;
  if (Touch.shoot && Touch.shoot.id === e.pointerId) Touch.shoot = null;
  Touch.btn.delete(e.pointerId);
}
cv.addEventListener('pointerdown', onPointerDown, { passive: false });
cv.addEventListener('pointermove', onPointerMove, { passive: false });
cv.addEventListener('pointerup', onPointerUp);
cv.addEventListener('pointercancel', onPointerUp);
cv.addEventListener('contextmenu', (e) => e.preventDefault());

window.addEventListener('keydown', (e) => {
  Sound.init();
  if (Keys[e.code]) return;
  Keys[e.code] = true;
  if (e.code.startsWith('Arrow')) { arrowOrder = arrowOrder.filter(k => k !== e.code); arrowOrder.push(e.code); }
  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Space'].includes(e.code)) e.preventDefault();
  if (G.state === 'play') {
    if (e.code === 'KeyE' || e.code === 'KeyQ' || e.code === 'ShiftLeft') Edge.bomb = true;
    if (e.code === 'Space') Edge.active = true;
    if (e.code === 'Escape' || e.code === 'KeyP') pauseGame();
    Edge.tap = true;
  } else uiKey(e.code);
});
window.addEventListener('keyup', (e) => { Keys[e.code] = false; arrowOrder = arrowOrder.filter(k => k !== e.code); });
window.addEventListener('blur', () => { for (const k in Keys) Keys[k] = false; arrowOrder = []; Touch.move = Touch.shoot = null; });

const Pad = { prev: {} };
function pollPad() {
  const pads = navigator.getGamepads ? navigator.getGamepads() : [];
  const gp = pads && [...pads].find(p => p && p.connected);
  if (!gp) return null;
  const b = (i) => gp.buttons[i] && gp.buttons[i].pressed;
  const edge = (i) => { const v = b(i), was = Pad.prev[i]; Pad.prev[i] = v; return v && !was; };
  const out = { mx: gp.axes[0] || 0, my: gp.axes[1] || 0, sx: gp.axes[2] || 0, sy: gp.axes[3] || 0 };
  if (Math.hypot(out.mx, out.my) < 0.2) out.mx = out.my = 0;
  if (Math.hypot(out.sx, out.sy) < 0.4) out.sx = out.sy = 0;
  if (b(12)) out.sy = -1; if (b(13)) out.sy = 1; if (b(14)) out.sx = -1; if (b(15)) out.sx = 1;
  if (b(3)) out.sy = -1; if (b(0)) out.sy = 1; if (b(2)) out.sx = -1; if (b(1)) out.sx = 1;
  out.bomb = edge(4) || edge(6); out.active = edge(5) || edge(7); out.pause = edge(9);
  return out;
}

function gatherInput() {
  let mx = 0, my = 0, sx = 0, sy = 0;
  if (Keys.KeyA) mx -= 1; if (Keys.KeyD) mx += 1; if (Keys.KeyW) my -= 1; if (Keys.KeyS) my += 1;
  const ak = arrowOrder[arrowOrder.length - 1];
  if (ak === 'ArrowLeft') sx = -1; else if (ak === 'ArrowRight') sx = 1; else if (ak === 'ArrowUp') sy = -1; else if (ak === 'ArrowDown') sy = 1;
  if (Touch.move) {
    const [vx, vy] = stickVec(Touch.move);
    const l = Math.hypot(vx, vy);
    if (l > 0.18) { const k = Math.min(1, (l - 0.18) / 0.55 + 0.35) / l; mx += vx * k; my += vy * k; }
  }
  if (Touch.shoot) {
    const [vx, vy] = stickVec(Touch.shoot);
    if (Math.hypot(vx, vy) > 0.35) {
      if (Opts.aim === '4') { if (Math.abs(vx) > Math.abs(vy)) { sx = Math.sign(vx); sy = 0; } else { sy = Math.sign(vy); sx = 0; } }
      else { sx = vx; sy = vy; }
    }
  }
  const pad = pollPad();
  if (pad) {
    mx += pad.mx; my += pad.my;
    if (pad.sx || pad.sy) { if (Opts.aim === '4') { if (Math.abs(pad.sx) > Math.abs(pad.sy)) { sx = Math.sign(pad.sx); sy = 0; } else { sy = Math.sign(pad.sy); sx = 0; } } else { sx = pad.sx; sy = pad.sy; } }
    if (pad.bomb) Edge.bomb = true; if (pad.active) Edge.active = true;
    if (pad.pause) pauseGame();
  }
  const out = { mx, my, sx, sy, bomb: Edge.bomb, active: Edge.active, anyTap: Edge.tap };
  Edge.bomb = Edge.active = Edge.tap = false;
  return out;
}

// ------------------------------------------------------------------ stati
function pauseGame() {
  if (G.state !== 'play' || !run || run.dying || run.won) return;
  G.state = 'pause'; G.menuSel = 0; G.confirmQuit = false;
  Touch.move = Touch.shoot = null;
  Sound.play('select');
}
function resumeGame() { G.state = 'play'; Touch.move = Touch.shoot = null; }
function startNewRun() {
  newRun();
  Stats.runs++; saveStats();
  G.state = 'play';
  Touch.move = Touch.shoot = null;
}
function onDeath() {
  G.state = 'dead'; G.endT = 0; G.menuSel = 0;
  Stats.best = Math.max(Stats.best, run.floorIdx + 1); Stats.kills += run.stats.kills; saveStats();
}
function onWin() {
  G.state = 'win'; G.endT = 0; G.menuSel = 0;
  Stats.wins++; Stats.best = 6; Stats.kills += run.stats.kills; saveStats();
  Sound.playMusic('menu');
}
function goTitle() { G.state = 'title'; G.menuSel = 0; run = null; Sound.playMusic('menu'); }

window.onAndroidBack = () => {
  Sound.init();
  switch (G.state) {
    case 'play': pauseGame(); break;
    case 'pause': resumeGame(); break;
    case 'options': case 'help': G.state = G.prev; G.menuSel = 0; break;
    case 'intro': G.state = 'title'; break;
    case 'dead': case 'win': goTitle(); break;
    default: try { Android.exit(); } catch (e) { /* */ }
  }
};
window.onAndroidPause = () => { if (G.state === 'play') pauseGame(); Sound.suspend(); };
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { if (G.state === 'play') pauseGame(); Sound.suspend(); }
  else Sound.resume();
});

// ------------------------------------------------------------------ GUI immediata (menu)
function button(label, x, y, w, h, cb, opts = {}) {
  const idx = UIB.length;
  UIB.push({ x, y, w, h, cb });
  const sel = !TOUCH && G.menuSel === idx;
  ctx.fillStyle = opts.bg || 'rgba(20,14,16,0.82)';
  ctx.fillRect(x, y, w, h);
  ctx.fillStyle = sel ? '#f2c94c' : (opts.border || '#8a7a70');
  ctx.fillRect(x, y, w, 1); ctx.fillRect(x, y + h - 1, w, 1); ctx.fillRect(x, y, 1, h); ctx.fillRect(x + w - 1, y, 1, h);
  const sc = opts.scale || 1;
  Font.draw(ctx, label, x + w / 2, y + Math.round((h - 7 * sc) / 2), sel ? '#f2c94c' : (opts.color || '#f0e6d2'), { align: 'center', scale: sc, shadow: '#000' });
}
function uiClick(x, y) {
  for (const b of UIB) if (x >= b.x && y >= b.y && x <= b.x + b.w && y <= b.y + b.h) { Sound.play('select'); b.cb(); return; }
  if (G.state === 'intro') { startNewRun(); return; }
  if (G.state === 'play' && run && run.bossIntro > 0) run.bossIntro = 0;
}
function uiKey(code) {
  if (code === 'ArrowDown' || code === 'ArrowRight' || code === 'KeyS') { G.menuSel = (G.menuSel + 1) % Math.max(1, UIB.length); Sound.play('tick'); }
  else if (code === 'ArrowUp' || code === 'ArrowLeft' || code === 'KeyW') { G.menuSel = (G.menuSel - 1 + UIB.length) % Math.max(1, UIB.length); Sound.play('tick'); }
  else if (code === 'Enter' || code === 'Space') { if (G.state === 'intro') startNewRun(); else { const b = UIB[G.menuSel]; if (b) { Sound.play('select'); b.cb(); } } }
  else if (code === 'Escape') window.onAndroidBack();
}

// ------------------------------------------------------------------ schermate
function drawBackdrop(themeIdx, dark = 0.55) {
  if (!G.titleBg || G.titleBgIdx !== themeIdx) { G.titleBg = renderRoomBackground(THEMES[themeIdx], 1234 + themeIdx, 'normal'); G.titleBgIdx = themeIdx; }
  ctx.fillStyle = '#0b0809'; ctx.fillRect(0, 0, LW, LH);
  ctx.drawImage(G.titleBg, OX, OY);
  ctx.fillStyle = `rgba(8,5,6,${dark})`; ctx.fillRect(0, 0, LW, LH);
  ctx.drawImage(vignette(), 0, 0);
}

function drawTitle() {
  drawBackdrop(0, 0.45);
  const cx = LW / 2;
  const portraitH = Math.min(LH - 60, 126), ps = portraitH / 63;
  const px = Math.max(SAFE.l + 8, Math.round(cx - 200)), py = Math.round(LH / 2 - portraitH / 2) + 8;
  if (GFX.portrait.complete && GFX.portrait.naturalWidth) {
    ctx.drawImage(GFX.portrait, px, py, Math.round(48 * ps), Math.round(63 * ps));
  }
  const tx = Math.round(Math.min(cx + 50, px + 48 * ps + (LW - (px + 48 * ps)) / 2));
  const ty = Math.round(LH * 0.14);
  Font.draw(ctx, 'SEGRETE', tx, ty, '#2a1e24', { align: 'center', scale: 4 });
  Font.draw(ctx, 'SEGRETE', tx - 2, ty - 2, '#f0e0c8', { align: 'center', scale: 4 });
  Font.draw(ctx, 'DI MATTIA', tx, ty + 34, '#c83a4a', { align: 'center', scale: 2, shadow: '#140e10' });
  // Mattia che cammina
  const fr = [1, 0, 2, 0][Math.floor(now * 6) % 4];
  const body = GFX.player.body.down[fr], head = GFX.player.head.down;
  const mx = tx, my = ty + 72;
  ctx.drawImage(shadowSprite(12), mx - 6, my + 1);
  ctx.drawImage(body, mx - 7, my - 9); ctx.drawImage(head, mx - 9, my - 9 - head.height + 1 + (fr ? 1 : 0));
  UIB = [];
  const bw = 120, bh = 18, by = my + 12;
  button('GIOCA', tx - bw / 2, by, bw, bh, () => { G.state = 'intro'; G.introT = 0; }, { scale: 2, border: '#c83a4a' });
  button('COME SI GIOCA', tx - bw / 2, by + 23, bw, 14, () => { G.prev = 'title'; G.state = 'help'; G.menuSel = 0; });
  button('OPZIONI', tx - bw / 2, by + 41, bw, 14, () => { G.prev = 'title'; G.state = 'options'; G.menuSel = 0; });
  const st = `PARTITE ${Stats.runs}   VITTORIE ${Stats.wins}   RECORD: ${Stats.best >= 6 ? 'COMPLETATO' : Stats.best ? 'PIANO ' + Stats.best : '-'}`;
  Font.draw(ctx, st, tx, LH - 12, 'rgba(240,230,210,0.55)', { align: 'center' });
}

function drawIntro() {
  drawBackdrop(0, 0.75);
  G.introT += 1 / 60;
  const lines = TEXTS.intro;
  const y0 = Math.round(LH / 2 - lines.length * 7);
  lines.forEach((l, i) => {
    const a = clamp((G.introT - i * 0.5) * 2, 0, 1);
    ctx.globalAlpha = a;
    Font.draw(ctx, l, LW / 2, y0 + i * 13, '#f0e6d2', { align: 'center', shadow: '#000' });
  });
  ctx.globalAlpha = 0.5 + Math.sin(now * 4) * 0.3;
  Font.draw(ctx, 'TOCCA PER INIZIARE', LW / 2, LH - 24, '#f2c94c', { align: 'center' });
  ctx.globalAlpha = 1;
  UIB = [];
}

function drawHelp() {
  drawBackdrop(1, 0.8);
  Font.draw(ctx, 'COME SI GIOCA', LW / 2, 12, '#f2c94c', { align: 'center', scale: 2, shadow: '#000' });
  const lines = TEXTS.help;
  const lh = Math.min(11, Math.floor((LH - 70) / lines.length));
  lines.forEach((l, i) => Font.draw(ctx, l, LW / 2, 34 + i * lh, '#f0e6d2', { align: 'center', shadow: '#000' }));
  UIB = [];
  button('INDIETRO', LW / 2 - 50, LH - 22, 100, 15, () => { G.state = G.prev; G.menuSel = 0; });
}

function drawOptions() {
  if (G.prev === 'pause' && run) { drawPlayScene(); ctx.fillStyle = 'rgba(8,5,6,0.8)'; ctx.fillRect(0, 0, LW, LH); }
  else drawBackdrop(2, 0.75);
  Font.draw(ctx, 'OPZIONI', LW / 2, 16, '#f2c94c', { align: 'center', scale: 2, shadow: '#000' });
  UIB = [];
  const w = 200, x = LW / 2 - w / 2;
  let y = 44;
  const row = (label, val, cb) => { button(label + ': ' + val, x, y, w, 16, cb); y += 21; };
  row('MUSICA', Opts.music ? 'SÌ' : 'NO', () => { Opts.music = !Opts.music; Sound.setMusic(Opts.music); saveOpts(); });
  row('EFFETTI SONORI', Opts.sfx ? 'SÌ' : 'NO', () => { Opts.sfx = !Opts.sfx; Sound.setSfx(Opts.sfx); saveOpts(); });
  row('VIBRAZIONE', Opts.vibra ? 'SÌ' : 'NO', () => { Opts.vibra = !Opts.vibra; saveOpts(); if (Opts.vibra) vibrate(40); });
  row('MIRA', Opts.aim === '4' ? '4 DIREZIONI' : 'LIBERA', () => { Opts.aim = Opts.aim === '4' ? 'free' : '4'; saveOpts(); });
  row('COMANDI', Opts.swap ? 'INVERTITI (MANCINI)' : 'STANDARD', () => { Opts.swap = !Opts.swap; saveOpts(); });
  button('INDIETRO', LW / 2 - 50, y + 6, 100, 15, () => { G.state = G.prev; G.menuSel = 0; });
}

function statLines() {
  const st = P.st;
  return [
    ['DANNO', st.damage.toFixed(2)],
    ['CADENZA', st.rate.toFixed(2)],
    ['GITTATA', (st.range / 24).toFixed(1)],
    ['VEL. TIRO', st.shotSpeed.toFixed(2)],
    ['VELOCITÀ', st.speed.toFixed(2)],
    ['FORTUNA', String(st.luck)],
  ];
}
function fmtTime(t) { const m = Math.floor(t / 60), s = Math.floor(t % 60); return m + ':' + (s < 10 ? '0' : '') + s; }

function drawItemsGrid(x, y, perRow, maxRows) {
  const ids = P.items.slice();
  ids.forEach((id, i) => {
    if (i >= perRow * maxRows) return;
    ctx.drawImage(GFX.items[id], x + (i % perRow) * 14, y + Math.floor(i / perRow) * 14);
  });
}

function drawPause() {
  drawPlayScene();
  ctx.fillStyle = 'rgba(8,5,6,0.78)'; ctx.fillRect(0, 0, LW, LH);
  Font.draw(ctx, 'PAUSA', LW / 2, 10, '#f2c94c', { align: 'center', scale: 3, shadow: '#000' });
  Font.draw(ctx, 'PIANO ' + (run.floorIdx + 1) + ' - ' + run.floor.theme.name + '   ' + fmtTime(run.stats.time), LW / 2, 36, '#c8b8a8', { align: 'center' });
  const colW = Math.min(150, (LW - 40) / 3);
  const x1 = Math.round(LW / 2 - colW * 1.5), x2 = Math.round(LW / 2 - colW / 2), x3 = Math.round(LW / 2 + colW / 2);
  let y = 54;
  Font.draw(ctx, 'STATISTICHE', x1 + 6, y, '#8a7a70');
  statLines().forEach(([k, v], i) => { Font.draw(ctx, k, x1 + 6, y + 13 + i * 11, '#f0e6d2'); Font.draw(ctx, v, x1 + colW - 14, y + 13 + i * 11, '#fff', { align: 'right' }); });
  Font.draw(ctx, 'OGGETTI', x2 + 6, y, '#8a7a70');
  if (P.items.length) drawItemsGrid(x2 + 6, y + 12, Math.floor((colW - 10) / 14), 5);
  else Font.draw(ctx, 'NESSUNO', x2 + 6, y + 13, '#5a504c');
  if (P.active) { Font.draw(ctx, 'ATTIVO: ' + ITEMS[P.active.id].name.toUpperCase(), x2 + 6, y + 88, '#c8b8a8'); }
  Font.draw(ctx, 'MAPPA', x3 + 6, y, '#8a7a70');
  drawMinimap(x3 + 6, y + 12, 12, 8, 1);
  UIB = [];
  const bw = 96, by = LH - 26;
  if (!G.confirmQuit) {
    button('RIPRENDI', LW / 2 - bw * 1.5 - 8, by, bw, 16, resumeGame, { border: '#5cc85c' });
    button('OPZIONI', LW / 2 - bw / 2, by, bw, 16, () => { G.prev = 'pause'; G.state = 'options'; G.menuSel = 0; });
    button('ABBANDONA', LW / 2 + bw / 2 + 8, by, bw, 16, () => { G.confirmQuit = true; G.menuSel = 0; });
  } else {
    Font.draw(ctx, 'VUOI DAVVERO ABBANDONARE LA PARTITA?', LW / 2, by - 14, '#ff8080', { align: 'center' });
    button('NO, CONTINUA', LW / 2 - bw - 4, by, bw, 16, () => { G.confirmQuit = false; });
    button('SÌ, ESCI', LW / 2 + 4, by, bw, 16, () => { Stats.best = Math.max(Stats.best, run.floorIdx + 1); saveStats(); goTitle(); }, { border: '#c83a4a' });
  }
}

function drawEnd(win) {
  drawPlayScene();
  G.endT += 1 / 60;
  ctx.fillStyle = win ? 'rgba(10,8,4,0.8)' : 'rgba(30,4,8,0.8)'; ctx.fillRect(0, 0, LW, LH);
  let y = 16;
  if (win) {
    Font.draw(ctx, 'CONSEGNA SALVATA!', LW / 2, y, '#f2c94c', { align: 'center', scale: 3, shadow: '#000' }); y += 32;
    TEXTS.win.forEach((l, i) => Font.draw(ctx, l, LW / 2, y + i * 11, '#f0e6d2', { align: 'center' })); y += TEXTS.win.length * 11 + 6;
  } else {
    Font.draw(ctx, 'MATTIA È CADUTO', LW / 2, y, '#ff5a5a', { align: 'center', scale: 3, shadow: '#000' }); y += 32;
    if (run.killer) { Font.draw(ctx, 'SCONFITTO DA: ' + run.killer, LW / 2, y, '#f0e6d2', { align: 'center' }); y += 13; }
  }
  Font.draw(ctx, 'PIANO ' + (run.floorIdx + 1) + ': ' + run.floor.theme.name, LW / 2, y, '#c8b8a8', { align: 'center' }); y += 11;
  Font.draw(ctx, 'TEMPO ' + fmtTime(run.stats.time) + '   NEMICI ' + run.stats.kills + '   STANZE ' + run.stats.rooms, LW / 2, y, '#c8b8a8', { align: 'center' }); y += 16;
  const per = Math.min(16, P.items.length || 1);
  drawItemsGrid(Math.round(LW / 2 - per * 7), y, per, 2); y += P.items.length > 16 ? 30 : 16;
  if (GFX.portrait.complete && GFX.portrait.naturalWidth && LH > 250) {
    ctx.globalAlpha = win ? 1 : 0.5;
    ctx.drawImage(GFX.portrait, Math.max(SAFE.l + 8, 10), LH - 70, 48, 63);
    ctx.globalAlpha = 1;
  }
  UIB = [];
  if (G.endT > 0.8) {
    const bw = 100, by = LH - 26;
    button(win ? 'GIOCA ANCORA' : 'RIPROVA', LW / 2 - bw - 4, by, bw, 16, () => { G.state = 'intro'; G.introT = 3; }, { border: '#5cc85c' });
    button('MENU', LW / 2 + 4, by, bw, 16, goTitle);
  }
}

function drawBossIntro() {
  const b = run.bossRef; if (!b) return;
  const t = 1.8 - run.bossIntro;
  const slide = clamp(t * 4, 0, 1);
  ctx.fillStyle = 'rgba(0,0,0,0.65)'; ctx.fillRect(0, 0, LW, LH);
  const bandY = Math.round(LH / 2 - 40);
  ctx.fillStyle = '#2a0a10'; ctx.fillRect(0, bandY, LW, 80);
  ctx.fillStyle = '#c83a4a'; ctx.fillRect(0, bandY, LW, 2); ctx.fillRect(0, bandY + 78, LW, 2);
  if (GFX.portrait.complete && GFX.portrait.naturalWidth) ctx.drawImage(GFX.portrait, Math.round(LW / 2 - 190 - (1 - slide) * 200), bandY - 14, 72, 94);
  const img = b.bk === 'ombra' ? GFX.boss.ombra.head.down : GFX.boss[b.bk][0];
  const s = b.bk === 'ombra' ? 4 : 2;
  ctx.drawImage(img, Math.round(LW / 2 + 160 - img.width * s / 2 + (1 - slide) * 200), Math.round(bandY + 40 - img.height * s / 2), img.width * s, img.height * s);
  Font.draw(ctx, 'VS', LW / 2, bandY + 12, '#fff', { align: 'center', scale: 3, shadow: '#c83a4a' });
  Font.draw(ctx, b.name, LW / 2, bandY + 48, '#f2c94c', { align: 'center', scale: 2, shadow: '#000' });
}

function drawPlayScene() {
  ctx.fillStyle = '#0b0809'; ctx.fillRect(0, 0, LW, LH);
  let sx = 0, sy = 0;
  if (FX.shake > 0 && G.state === 'play') { sx = Math.round(rnd(-1, 1) * FX.shakeMag); sy = Math.round(rnd(-1, 1) * FX.shakeMag); }
  const tr = run.trans;
  if (tr && tr.dir && tr.snap) {
    const k = clamp(tr.t / tr.dur, 0, 1), e = k * k * (3 - 2 * k);
    const [dx, dy] = DV[tr.dir];
    const ox = dx * 360, oy = dy * 216;
    drawWorld(ctx, OX + Math.round(ox * (1 - e)), OY + Math.round(oy * (1 - e)));
    ctx.drawImage(tr.snap, OX - Math.round(ox * e), OY - Math.round(oy * e));
  } else drawWorld(ctx, OX + sx, OY + sy);
  ctx.drawImage(vignette(), 0, 0);
  if (P.hp + P.soul <= 1 && !run.dying && G.state === 'play') { ctx.globalAlpha = 0.12 + Math.sin(now * 6) * 0.08; ctx.fillStyle = '#ff0000'; ctx.fillRect(0, 0, LW, LH); ctx.globalAlpha = 1; }
  if (run.dying) { ctx.fillStyle = `rgba(0,0,0,${clamp(1 - run.dying / 1.6, 0, 0.7)})`; ctx.fillRect(0, 0, LW, LH); }
  if (tr && !tr.dir) { const k = clamp(tr.t / tr.dur, 0, 1); ctx.fillStyle = `rgba(0,0,0,${1 - Math.abs(k * 2 - 1)})`; ctx.fillRect(0, 0, LW, LH); }
  if (run.fall) { const k = run.fall.t; const a = k < 1.2 ? clamp((k - 0.4) / 0.8, 0, 1) : clamp(1 - (k - 1.2) / 0.4, 0, 1); ctx.fillStyle = `rgba(0,0,0,${a})`; ctx.fillRect(0, 0, LW, LH); }
}

// ------------------------------------------------------------------ ciclo principale
let acc = 0, lastT = 0;
function frame(ts) {
  const t = ts / 1000;
  let dt = lastT ? t - lastT : STEP;
  lastT = t;
  dt = Math.min(dt, 0.1);
  now += dt;
  if (G.state === 'play' && run) {
    acc += dt;
    let n = 0;
    while (acc >= STEP && n < 5) {
      const input = gatherInput();
      updateGame(STEP, input);
      if (FX.shake > 0) { FX.shake -= STEP; if (FX.shake <= 0) FX.shakeMag = 0; }
      acc -= STEP; n++;
      if (G.state !== 'play') break;
    }
    if (n >= 5) acc = 0;
  } else acc = 0;
  render();
  requestAnimationFrame(frame);
}

function render() {
  ctx.imageSmoothingEnabled = false;
  if (LW < 330) {
    ctx.fillStyle = '#0b0809'; ctx.fillRect(0, 0, LW, LH);
    Font.draw(ctx, 'RUOTA IL TELEFONO', LW / 2, LH / 2 - 4, '#f0e6d2', { align: 'center' });
    UIB = [];
    return;
  }
  switch (G.state) {
    case 'title': drawTitle(); break;
    case 'intro': drawIntro(); break;
    case 'help': drawHelp(); break;
    case 'options': drawOptions(); break;
    case 'pause': drawPause(); break;
    case 'dead': drawEnd(false); break;
    case 'win': drawEnd(true); break;
    case 'play':
      drawPlayScene();
      UIB = [];
      if (!run.trans && !run.fall) drawHUD();
      if (run.bossIntro > 0) drawBossIntro();
      break;
  }
}

// ------------------------------------------------------------------ avvio
function boot() {
  loadSaved();
  Sound.setMusic(Opts.music); Sound.setSfx(Opts.sfx);
  loadGfx();
  resize();
  window.addEventListener('resize', resize);
  window.addEventListener('orientationchange', () => setTimeout(resize, 200));
  const unlock = () => { Sound.init(); Sound.setMusic(Opts.music); Sound.setSfx(Opts.sfx); if (G.state === 'title' || G.state === 'intro') Sound.playMusic('menu'); window.removeEventListener('pointerdown', unlock); };
  window.addEventListener('pointerdown', unlock);
  window.addEventListener('keydown', unlock, { once: true });
  requestAnimationFrame(frame);
}
boot();
