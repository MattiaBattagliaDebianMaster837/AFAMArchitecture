'use strict';
// Motore di gioco: piani, stanze, entità, nemici, boss.

const TILE = 24, COLS = 13, ROWS = 7, RW = 312, RH = 168, STEP = 1 / 60;
const T_EMPTY = 0, T_ROCK = 1, T_XROCK = 2, T_CRATE = 3, T_METAL = 4, T_SPIKES = 5, T_PIT = 6, T_FIRE = 7;
const DV = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] };
const OPP = { up: 'down', down: 'up', left: 'right', right: 'left' };
const DOOR_POS = { up: [156, 0], down: [156, 168], left: [0, 84], right: [312, 84] };
const ENTRY_POS = { up: [156, 16], down: [156, 154], left: [14, 84], right: [298, 84] };
const MAP_W = 9, MAP_H = 8;

const clamp = (v, a, b) => v < a ? a : v > b ? b : v;
const rnd = (a, b) => a + Math.random() * (b - a);
const rndi = (a, b) => a + Math.floor(Math.random() * (b - a + 1));
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

let run = null;      // partita in corso
let P = null;        // giocatore
const FX = { shake: 0, shakeMag: 0, slowT: 0 };

// ================================================================== GIOCATORE
function newPlayer() {
  const p = {
    x: RW / 2, y: RH / 2 + 24, vx: 0, vy: 0, hw: 5, hh: 4,
    maxHp: 6, hp: 6, soul: 0, coins: 0, bombs: 1, keys: 0,
    inv: 0, godT: 0, fireCd: 0, eye: 0, face: 'down', shootDir: 'down', shootT: 0, walkT: 0, moving: false,
    items: [], active: null,
    mods: { dmgUp: 0, dmgFlat: 0, dmgMult: 1, tears: 0, tearMult: 1, range: 0, shotSpeed: 0, speed: 0, luck: 0, size: 0 },
    fx: {}, familiars: [], shield: false, st: {}, holdT: 0, holdIcon: null, trail: [],
  };
  p.addContainers = (n) => { p.maxHp = Math.min(24, p.maxHp + n * 2); if (p.maxHp + p.soul > 24) p.soul = 24 - p.maxHp; };
  p.heal = (n) => { p.hp = Math.min(p.maxHp, p.hp + n); };
  p.addSoul = (n) => { p.soul = Math.min(24 - p.maxHp, p.soul + n); };
  recompute(p);
  return p;
}

function recompute(p) {
  const m = p.mods;
  p.st.damage = Math.max(0.5, (3.5 * Math.sqrt(Math.max(0.05, 1 + m.dmgUp * 1.2)) + m.dmgFlat) * m.dmgMult);
  p.st.rate = clamp((2.73 + m.tears * 0.55) * m.tearMult, 0.8, 9);
  p.st.range = Math.max(70, 165 + m.range);
  p.st.shotSpeed = Math.max(0.6, 1 + m.shotSpeed);
  p.st.speed = clamp(1 + m.speed, 0.6, 2);
  p.st.luck = m.luck;
  p.st.size = clamp(3 + m.size + (p.st.damage > 9 ? 1 : 0), 2, 8);
}

// ================================================================== GENERAZIONE PIANO
function genFloor(floorIdx) {
  const rng = run.rng;
  const target = floorIdx === 4 ? 10 : Math.min(17, Math.floor(3.33 * (floorIdx + 1)) + rng.int(5, 6));
  for (let attempt = 0; attempt < 400; attempt++) {
    const grid = Array.from({ length: MAP_H }, () => Array(MAP_W).fill(null));
    const start = { x: 4, y: 3 };
    grid[start.y][start.x] = 'start';
    const cells = [start], queue = [start];
    const nCount = (x, y) => [[1, 0], [-1, 0], [0, 1], [0, -1]].reduce((s, [dx, dy]) => {
      const xx = x + dx, yy = y + dy;
      return s + (xx >= 0 && yy >= 0 && xx < MAP_W && yy < MAP_H && grid[yy][xx] ? 1 : 0);
    }, 0);
    let guard = 0;
    while (cells.length < target && guard++ < 500) {
      if (!queue.length) queue.push(rng.pick(cells));
      const c = queue.shift();
      for (const [dx, dy] of rng.shuffle([[1, 0], [-1, 0], [0, 1], [0, -1]])) {
        if (cells.length >= target) break;
        const x = c.x + dx, y = c.y + dy;
        if (x < 0 || y < 0 || x >= MAP_W || y >= MAP_H || grid[y][x]) continue;
        if (nCount(x, y) > 1) continue;
        if (rng() < 0.5) continue;
        grid[y][x] = 'normal';
        const n = { x, y };
        cells.push(n); queue.push(n);
      }
    }
    if (cells.length < target) continue;
    const dead = cells.filter(c => c !== start && nCount(c.x, c.y) === 1);
    if (dead.length < 3) continue;
    // distanze dalla partenza
    const distOf = {};
    const bq = [start]; distOf[start.x + ',' + start.y] = 0;
    while (bq.length) {
      const c = bq.shift();
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const x = c.x + dx, y = c.y + dy, k = x + ',' + y;
        if (x < 0 || y < 0 || x >= MAP_W || y >= MAP_H || !grid[y][x] || k in distOf) continue;
        distOf[k] = distOf[c.x + ',' + c.y] + 1; bq.push({ x, y });
      }
    }
    dead.sort((a, b) => distOf[b.x + ',' + b.y] - distOf[a.x + ',' + a.y]);
    const boss = dead[0];
    if (distOf[boss.x + ',' + boss.y] < 2) continue;
    const rest = rng.shuffle(dead.slice(1));
    grid[boss.y][boss.x] = 'boss';
    grid[rest[0].y][rest[0].x] = 'treasure';
    grid[rest[1].y][rest[1].x] = 'shop';
    // stanza segreta: cella vuota con più vicini
    let best = null, bestN = 0;
    for (let y = 0; y < MAP_H; y++) for (let x = 0; x < MAP_W; x++) {
      if (grid[y][x]) continue;
      let n = 0, nearBoss = false;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= MAP_W || yy >= MAP_H) continue;
        const t = grid[yy][xx];
        if (t === 'boss') nearBoss = true;
        if (t && t !== 'boss') n++;
      }
      if (nearBoss || n === 0) continue;
      const score = n + rng() * 0.5;
      if (score > bestN) { bestN = score; best = { x, y }; }
    }
    if (best) grid[best.y][best.x] = 'secret';
    // stanza del patto (riservata accanto al boss)
    let pact = null;
    if (floorIdx < 4) {
      for (const [dx, dy] of rng.shuffle([[1, 0], [-1, 0], [0, 1], [0, -1]])) {
        const x = boss.x + dx, y = boss.y + dy;
        if (x < 0 || y < 0 || x >= MAP_W || y >= MAP_H || grid[y][x]) continue;
        let ok = true;
        for (const [ex, ey] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const xx = x + ex, yy = y + ey;
          if ((xx !== boss.x || yy !== boss.y) && xx >= 0 && yy >= 0 && xx < MAP_W && yy < MAP_H && grid[yy][xx]) ok = false;
        }
        if (ok) { pact = { x, y }; grid[y][x] = 'pact'; break; }
      }
    }
    return buildFloor(floorIdx, grid, start);
  }
  throw new Error('generazione piano fallita');
}

function buildFloor(floorIdx, grid, start) {
  const rng = run.rng;
  const fl = { idx: floorIdx, theme: THEMES[floorIdx], rooms: {}, list: [], grid };
  fl.tiles = buildTileSprites(fl.theme, (run.seed + floorIdx * 977) >>> 0);
  const usedLayouts = new Set();
  for (let y = 0; y < MAP_H; y++) for (let x = 0; x < MAP_W; x++) {
    const type = grid[y][x];
    if (!type) continue;
    const room = {
      gx: x, gy: y, type, key: x + ',' + y, seed: rng.int(1, 1e9),
      tiles: new Uint8Array(COLS * ROWS), tileHp: new Uint8Array(COLS * ROWS), tileVar: new Uint8Array(COLS * ROWS),
      spawns: [], pickups: [], pedestals: [], doors: {}, cleared: true, visited: false, seen: false,
      bg: null, decals: null, trapdoor: null, hints: false,
    };
    for (let i = 0; i < room.tileVar.length; i++) room.tileVar[i] = rng.int(0, 2);
    fl.rooms[room.key] = room; fl.list.push(room);
    if (type === 'normal') {
      let li;
      for (let t = 0; t < 10; t++) { li = rng.int(0, LAYOUTS.length - 1); if (!usedLayouts.has(li)) break; }
      usedLayouts.add(li);
      applyLayout(room, LAYOUTS[li], rng.chance(0.5), rng.chance(0.5), floorIdx);
      room.cleared = room.spawns.length === 0;
    } else if (type === 'start') {
      room.hints = floorIdx === 0;
    } else if (type === 'boss') {
      room.cleared = false;
      const pool = FLOOR_BOSSES[floorIdx].filter(b => !run.bossesSeen.includes(b));
      room.boss = rng.pick(pool.length ? pool : FLOOR_BOSSES[floorIdx]);
      run.bossesSeen.push(room.boss);
      if (room.boss !== 'ombra') for (const [c, r] of [[1, 1], [11, 1], [1, 5], [11, 5]]) if (rng.chance(0.6)) room.tiles[r * COLS + c] = T_ROCK;
    } else if (type === 'treasure') {
      room.pedestals.push(makePedestal(156, 84, drawItem('treasure')));
      if (rng.chance(0.5)) for (const [c, r] of [[4, 2], [8, 2], [4, 4], [8, 4]]) room.tiles[r * COLS + c] = T_ROCK;
    } else if (type === 'shop') {
      const xs = [84, 132, 180, 228];
      room.pedestals.push(makePedestal(xs[0], 90, drawItem('shop'), { price: 15 }));
      const goods = rng.shuffle(['heart', 'bomb', 'key', 'ink', 'heart', 'bomb', 'key']).slice(0, 3);
      goods.forEach((g, i) => room.pickups.push(makePickup(g, xs[i + 1], 92, { price: g === 'heart' ? 3 : 5, still: true })));
    } else if (type === 'secret') {
      const roll = rng();
      if (roll < 0.3) room.pedestals.push(makePedestal(156, 84, drawItem('secret')));
      else if (roll < 0.65) { for (let i = 0; i < rng.int(4, 7); i++) room.pickups.push(makePickup(rng.chance(0.15) ? 'coin5' : 'coin', 130 + rng.int(0, 52), 70 + rng.int(0, 30), { still: true })); room.pickups.push(makePickup('bomb', 120, 110, { still: true })); }
      else { room.pickups.push(makePickup('chestGold', 156, 84, { still: true })); room.pickups.push(makePickup('ink', 110, 84, { still: true })); room.pickups.push(makePickup('key', 200, 84, { still: true })); }
    } else if (type === 'pact') {
      const a = drawItem('pact'), b = drawItem('pact');
      room.pedestals.push(makePedestal(116, 84, a, { hprice: rng.chance(0.5) ? 1 : 2 }));
      room.pedestals.push(makePedestal(196, 84, b, { hprice: rng.chance(0.6) ? 1 : 2 }));
      for (const [c, r] of [[2, 1], [10, 1], [2, 5], [10, 5]]) room.tiles[r * COLS + c] = T_FIRE, room.tileHp[r * COLS + c] = 6;
    }
    if (type === 'start') fl.start = room;
    if (type === 'boss') fl.boss = room;
    if (type === 'pact') fl.pact = room;
  }
  // porte
  for (const room of fl.list) {
    for (const dir of ['right', 'down']) {
      const [dx, dy] = DV[dir];
      const other = fl.rooms[(room.gx + dx) + ',' + (room.gy + dy)];
      if (!other) continue;
      const types = [room.type, other.type];
      let kind = 'normal', hidden = false, locked = false;
      if (types.includes('pact')) { if (!types.includes('boss')) continue; kind = 'pact'; hidden = true; }
      else if (types.includes('secret')) { if (types.includes('boss')) continue; kind = 'secret'; hidden = true; }
      else if (types.includes('boss')) kind = 'boss';
      else if (types.includes('treasure')) { kind = 'treasure'; locked = floorIdx >= 1; }
      else if (types.includes('shop')) kind = 'shop';
      const door = { kind, hidden, locked, a: room, b: other, dirA: dir };
      room.doors[dir] = door; other.doors[OPP[dir]] = door;
    }
  }
  // la stanza segreta deve avere almeno una porta
  for (const room of fl.list) if (room.type === 'secret' && !Object.keys(room.doors).length) { delete fl.rooms[room.key]; fl.list.splice(fl.list.indexOf(room), 1); grid[room.gy][room.gx] = null; }
  return fl;
}

function applyLayout(room, layout, mx, my, floorIdx) {
  const pools = FLOOR_POOLS[floorIdx];
  const rng = run.rng;
  for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) {
    const ch = layout[my ? ROWS - 1 - r : r][mx ? COLS - 1 - c : c];
    const i = r * COLS + c;
    const map = { r: T_ROCK, x: T_XROCK, c: T_CRATE, m: T_METAL, '^': T_SPIKES, o: T_PIT, f: T_FIRE };
    if (map[ch]) { room.tiles[i] = map[ch]; room.tileHp[i] = ch === 'c' ? 3 : ch === 'f' ? 6 : 0; }
    else if (ch === 'e') room.spawns.push({ type: rng.pick(pools.common), c, r });
    else if (ch === 'E') room.spawns.push({ type: rng.pick(pools.heavy), c, r });
    else if (ch === 't') room.spawns.push({ type: rng.pick(pools.turret), c, r });
    else if (ch === 'g') for (let k = 0; k < 3 + (floorIdx >= 2 ? 1 : 0); k++) room.spawns.push({ type: 'gnat', c, r, ox: rng.int(-10, 10), oy: rng.int(-10, 10) });
  }
  // croce centrale sempre libera (porte collegate)
  for (let c = 0; c < COLS; c++) clearCross(room, 3 * COLS + c);
  for (let r = 0; r < ROWS; r++) clearCross(room, r * COLS + 6);
  // spazio libero davanti alle porte
  for (const i of [6, 6 + COLS, (ROWS - 1) * COLS + 6, (ROWS - 2) * COLS + 6, 3 * COLS, 3 * COLS + 1, 3 * COLS + 12, 3 * COLS + 11]) room.tiles[i] = T_EMPTY;
  for (const s of room.spawns) {
    if (run.rng.chance(floorIdx >= 1 ? 0.06 + floorIdx * 0.03 : 0)) s.champion = run.rng.pick(['#ff3030', '#ffd030', '#40e060']);
    room.tiles[s.r * COLS + s.c] = room.tiles[s.r * COLS + s.c] === T_SPIKES ? T_SPIKES : T_EMPTY;
  }
}
function clearCross(room, i) {
  const t = room.tiles[i];
  if (t !== T_EMPTY && t !== T_SPIKES) room.tiles[i] = T_EMPTY;
  if (t === T_SPIKES && (i % COLS === 6 || ((i / COLS) | 0) === 3)) room.tiles[i] = T_EMPTY;
}

// ------------------------------------------------------------------ pool oggetti
function initPools() {
  const pools = { treasure: [], shop: [], boss: [], pact: [], secret: [] };
  for (const id in ITEMS) for (const p of ITEMS[id].pools) pools[p].push(id);
  return pools;
}
function drawItem(pool) {
  const order = [pool, 'treasure', 'boss', 'shop'];
  for (const p of order) {
    const avail = run.pools[p].filter(id => !run.seenItems.has(id));
    if (avail.length) { const id = run.rng.pick(avail); run.seenItems.add(id); return id; }
  }
  return 'cornetto';
}

function makePedestal(x, y, item, opts = {}) {
  return { x, y, item, price: opts.price || 0, hprice: opts.hprice || 0, taken: false, cd: 0, charge: null };
}
function makePickup(type, x, y, opts = {}) {
  return { type, x, y, vx: opts.vx || 0, vy: opts.vy || 0, z: opts.z || 0, vz: opts.vz || 0, price: opts.price || 0, t: 0, still: !!opts.still, opened: false, hw: 4, hh: 3 };
}

// ================================================================== PARTITA
function newRun() {
  const seed = (Date.now() ^ (Math.random() * 1e9)) >>> 0;
  run = {
    seed, rng: RNG(seed), floorIdx: 0, floor: null, room: null,
    tears: [], eshots: [], enemies: [], bombs: [], parts: [], hazards: [],
    pools: null, seenItems: new Set(), bossesSeen: [],
    stats: { kills: 0, time: 0, rooms: 0 },
    banner: null, floorBanner: 0, roomDamaged: false, flow: null, flowKey: '', killer: '',
    bossRef: null, trans: null, fall: null, dying: 0, won: 0,
  };
  run.pools = initPools();
  P = newPlayer();
  startFloor(0);
}

function startFloor(idx) {
  run.floorIdx = idx;
  run.floor = genFloor(idx);
  run.floorBanner = 3;
  P.x = RW / 2; P.y = RH / 2 + 24; P.vx = P.vy = 0;
  enterRoom(run.floor.start, null);
  revealMap();
  Sound.playMusic('f' + idx);
}

function revealMap() {
  if (!run.floor) return;
  for (const r of run.floor.list) {
    if (P.fx.map && r.type !== 'secret' && r.type !== 'pact') r.seen = true;
    if (P.fx.compass && ['treasure', 'shop', 'boss'].includes(r.type)) r.seen = true;
  }
}
function roomAt(gx, gy) { return run.floor.rooms[gx + ',' + gy]; }

function enterRoom(room, viaDir) {
  run.room = room;
  if (!room.visited) run.stats.rooms++;
  room.visited = true; room.seen = true;
  for (const d in room.doors) { const door = room.doors[d]; if (!door.hidden) (door.a === room ? door.b : door.a).seen = true; }
  revealMap();
  run.tears.length = 0; run.eshots.length = 0; run.bombs.length = 0; run.parts.length = 0; run.hazards.length = 0;
  run.enemies = [];
  run.flowKey = '';
  run.roomDamaged = false;
  if (viaDir) { const [x, y] = ENTRY_POS[viaDir]; P.x = x; P.y = y; P.vx *= 0.3; P.vy *= 0.3; }
  for (const f of P.familiars) { f.x = P.x; f.y = P.y; }
  P.trail = [];
  if (P.fx.shieldItem) P.shield = true;
  if (!room.cleared) {
    if (room.type === 'boss') spawnBoss(room);
    else for (const s of room.spawns) {
      const x = s.c * TILE + 12 + (s.ox || 0), y = s.r * TILE + 12 + (s.oy || 0);
      if (Math.hypot(x - P.x, y - P.y) < 40) continue;
      spawnEnemy(s.type, x, y, { champion: s.champion });
    }
    if (!run.enemies.length) room.cleared = true;
  }
  if (room.type === 'boss' && !room.cleared) Sound.playMusic(room.boss === 'ombra' ? 'final' : 'boss');
  else Sound.playMusic('f' + run.floorIdx);
}

function doorOpen(room, dir) {
  const d = room.doors[dir];
  return d && !d.hidden && !d.locked && room.cleared;
}
function otherSide(door, room) { return door.a === room ? door.b : door.a; }

// ================================================================== COLLISIONI
function tileAt(c, r) {
  if (c < 0 || r < 0 || c >= COLS || r >= ROWS) return -1;
  return run.room.tiles[r * COLS + c];
}
function solidWalk(t) { return t === T_ROCK || t === T_XROCK || t === T_CRATE || t === T_METAL || t === T_PIT || t === T_FIRE; }
function solidShot(t) { return t === T_ROCK || t === T_XROCK || t === T_CRATE || t === T_METAL || t === T_FIRE; }

function moveBox(e, dx, dy, mode) {
  const steps = Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 4));
  const sx = dx / steps, sy = dy / steps;
  let hx = false, hy = false;
  for (let i = 0; i < steps; i++) {
    e.x += sx; if (collideAxis(e, 0, sx, mode)) hx = true;
    e.y += sy; if (collideAxis(e, 1, sy, mode)) hy = true;
  }
  return { hx, hy };
}

function collideAxis(e, axis, d, mode) {
  const hw = e.hw, hh = e.hh, room = run.room;
  let hit = false;
  if (axis === 0) {
    let minX = hw, maxX = RW - hw;
    if (mode === 'player') {
      if (doorOpen(room, 'left') && Math.abs(e.y - 84) <= 12 - hh) minX = -40;
      if (doorOpen(room, 'right') && Math.abs(e.y - 84) <= 12 - hh) maxX = RW + 40;
      if (e.y < hh || e.y > RH - hh) { minX = Math.max(minX, 144 + hw); maxX = Math.min(maxX, 168 - hw); }
    }
    if (e.x < minX) { e.x = minX; hit = true; } else if (e.x > maxX) { e.x = maxX; hit = true; }
  } else {
    let minY = hh, maxY = RH - hh;
    if (mode === 'player') {
      if (doorOpen(room, 'up') && Math.abs(e.x - 156) <= 12 - hw) minY = -40;
      if (doorOpen(room, 'down') && Math.abs(e.x - 156) <= 12 - hw) maxY = RH + 40;
      if (e.x < hw || e.x > RW - hw) { minY = Math.max(minY, 72 + hh); maxY = Math.min(maxY, 96 - hh); }
    }
    if (e.y < minY) { e.y = minY; hit = true; } else if (e.y > maxY) { e.y = maxY; hit = true; }
  }
  if (mode === 'fly' || d === 0) return hit;
  const c0 = Math.floor((e.x - hw) / TILE), c1 = Math.floor((e.x + hw - 0.01) / TILE);
  const r0 = Math.floor((e.y - hh) / TILE), r1 = Math.floor((e.y + hh - 0.01) / TILE);
  for (let r = r0; r <= r1; r++) for (let c = c0; c <= c1; c++) {
    const t = tileAt(c, r);
    if (t < 0 || !solidWalk(t)) continue;
    if (mode === 'pickup' && t === T_PIT) continue;
    if (axis === 0) { if (d > 0) e.x = c * TILE - hw; else e.x = (c + 1) * TILE + hw; }
    else { if (d > 0) e.y = r * TILE - hh; else e.y = (r + 1) * TILE + hh; }
    hit = true;
  }
  return hit;
}

// Campo di flusso (BFS) verso il giocatore per i nemici che camminano
function updateFlow() {
  const pc = clamp(Math.floor(P.x / TILE), 0, COLS - 1), pr = clamp(Math.floor(P.y / TILE), 0, ROWS - 1);
  const key = pc + ',' + pr + ',' + run.room.tiles.join('');
  if (key === run.flowKey) return;
  run.flowKey = key;
  const D = new Int16Array(COLS * ROWS).fill(-1);
  const q = [pr * COLS + pc]; D[q[0]] = 0;
  while (q.length) {
    const i = q.shift(), c = i % COLS, r = (i / COLS) | 0;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const cc = c + dx, rr = r + dy;
      if (cc < 0 || rr < 0 || cc >= COLS || rr >= ROWS) continue;
      const j = rr * COLS + cc;
      if (D[j] >= 0 || solidWalk(run.room.tiles[j])) continue;
      D[j] = D[i] + 1; q.push(j);
    }
  }
  run.flow = D;
}
function flowDir(e) {
  const c = clamp(Math.floor(e.x / TILE), 0, COLS - 1), r = clamp(Math.floor(e.y / TILE), 0, ROWS - 1);
  const D = run.flow;
  const here = D ? D[r * COLS + c] : -1;
  let dx = P.x - e.x, dy = P.y - e.y;
  if (D && here > 1) {
    let best = here, bx = 0, by = 0;
    for (const [ox, oy] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]]) {
      const cc = c + ox, rr = r + oy;
      if (cc < 0 || rr < 0 || cc >= COLS || rr >= ROWS) continue;
      const v = D[rr * COLS + cc];
      if (v < 0 || v >= best) continue;
      if (ox && oy && (D[r * COLS + cc] < 0 || D[rr * COLS + c] < 0)) continue;
      best = v; bx = ox; by = oy;
    }
    if (bx || by) { dx = (c + bx) * TILE + 12 - e.x; dy = (r + by) * TILE + 12 - e.y; }
  }
  const d = Math.hypot(dx, dy) || 1;
  return [dx / d, dy / d];
}

// ================================================================== EFFETTI
function shake(mag, t) { FX.shakeMag = Math.max(FX.shakeMag, mag); FX.shake = Math.max(FX.shake, t); }
function vibrate(ms) {
  if (!Opts.vibra) return;
  try { if (window.Android && Android.vibrate) Android.vibrate(ms); else if (navigator.vibrate) navigator.vibrate(ms); } catch (e) { /* */ }
}
function part(x, y, vx, vy, life, color, size = 1, grav = 0, z = 0) {
  if (run.parts.length > 400) run.parts.shift();
  run.parts.push({ x, y, vx, vy, life, max: life, color, size, grav, z, vz: 0 });
}
function burst(x, y, n, color, speed = 60, life = 0.4, size = 1, z = 6) {
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2, s = speed * rnd(0.3, 1);
    const p = { x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s * 0.7, life: life * rnd(0.6, 1.2), max: life, color, size, grav: 220, z, vz: rnd(30, 90) };
    if (run.parts.length > 400) run.parts.shift();
    run.parts.push(p);
  }
}
function decal(x, y, r, color) {
  const room = run.room;
  if (!room.decals) room.decals = makeCanvas(RW, RH);
  const c = room.decals.getContext('2d');
  c.fillStyle = color;
  for (let i = 0; i < 6; i++) {
    const a = Math.random() * Math.PI * 2, d = Math.random() * r;
    const s = Math.max(1, Math.round(r * rnd(0.3, 0.7)));
    c.fillRect(Math.round(x + Math.cos(a) * d - s / 2), Math.round(y + Math.sin(a) * d * 0.7 - s / 2), s, Math.max(1, Math.round(s * 0.7)));
  }
}
function banner(title, sub, icon) { run.banner = { title, sub, icon, t: 2.6 }; }

// ================================================================== DANNO AL GIOCATORE
function hurtPlayer(amount, source) {
  if (P.inv > 0 || P.godT > 0 || run.dying || run.won) return;
  if (P.shield) {
    P.shield = false; P.inv = 0.8;
    burst(P.x, P.y, 14, '#f5a0b8', 70, 0.4, 1, 10);
    Sound.play('hit'); return;
  }
  let a = amount;
  if (P.soul > 0) { const t = Math.min(P.soul, a); P.soul -= t; a -= t; }
  P.hp -= a;
  P.inv = 1.0;
  run.roomDamaged = true;
  shake(3, 0.25); vibrate(70);
  Sound.play('hurt', 0.1);
  burst(P.x, P.y - 8, 10, '#a01828', 70, 0.5, 1, 10);
  if (P.hp <= 0 && P.soul <= 0) {
    P.hp = 0; run.killer = source || '';
    run.dying = 1.6;
    Sound.stopMusic(); Sound.play('death'); vibrate(300);
  }
}

// ================================================================== SPARO
function fireTears(dir) {
  const st = P.st;
  const [dx, dy] = dir;
  const speed = 230 * st.shotSpeed;
  P.eye = 1 - P.eye;
  const perpX = -dy, perpY = dx;
  const eyeOff = (P.eye ? 3 : -3);
  const angs = P.fx.triple ? [-0.2, 0, 0.2] : [0];
  const shots = [];
  for (const a of angs) {
    const ca = Math.cos(a), sa = Math.sin(a);
    const vx = dx * ca - dy * sa, vy = dx * sa + dy * ca;
    if (P.fx.double) { shots.push([vx, vy, 4]); shots.push([vx, vy, -4]); }
    else shots.push([vx, vy, angs.length > 1 ? 0 : eyeOff]);
  }
  if (P.fx.back) shots.push([-dx, -dy, 0]);
  for (const [vx, vy, off] of shots) {
    run.tears.push({
      x: P.x + perpX * off, y: P.y - 2 + perpY * off,
      vx: vx * speed + P.vx * 0.3, vy: vy * speed + P.vy * 0.3,
      dmg: st.damage, r: st.size, life: st.range / speed, z: 12, hit: new Set(), bounces: 0,
    });
  }
  P.shootT = 0.18;
  Sound.play('shoot', 0.05);
}

function tearSprite(t) {
  const r = clamp(Math.round(t.r), 2, 8);
  if (t.enemy) return t.dark ? GFX.shot.dark[r] : GFX.shot.enemy[r];
  if (P.fx.explode) return GFX.shot.fx.explode[r];
  if (P.fx.burn) return GFX.shot.fx.burn[r];
  if (P.fx.poison) return GFX.shot.fx.poison[r];
  if (P.fx.homing) return GFX.shot.fx.homing[r];
  if (P.fx.slow) return GFX.shot.fx.slow[r];
  return GFX.shot.tear[r];
}

function splash(t, color = '#2c3466') {
  burst(t.x, t.y, 5, color, 40, 0.25, 1, t.z);
  Sound.play('splash', 0.04);
  if (!t.enemy && P.fx.explode) smallBlast(t.x, t.y, t.dmg * 0.7);
}
function smallBlast(x, y, dmg) {
  burst(x, y, 10, '#ffcc40', 60, 0.3, 1, 6);
  for (const e of run.enemies) if (!e.dead && e.spawnT <= 0 && Math.hypot(e.x - x, e.y - y) < 22 + e.r) damageEnemy(e, dmg, null);
}

function updateTears(dt) {
  const tearsArr = run.tears;
  for (let i = tearsArr.length - 1; i >= 0; i--) {
    const t = tearsArr[i];
    t.life -= dt;
    if (P.fx.homing) {
      let best = null, bd = 130;
      for (const e of run.enemies) { if (e.dead || e.spawnT > 0) continue; const d = Math.hypot(e.x - t.x, e.y - t.y); if (d < bd) { bd = d; best = e; } }
      if (best) {
        const sp = Math.hypot(t.vx, t.vy);
        const want = Math.atan2(best.y - t.y, best.x - t.x), cur = Math.atan2(t.vy, t.vx);
        let da = want - cur; while (da > Math.PI) da -= Math.PI * 2; while (da < -Math.PI) da += Math.PI * 2;
        const na = cur + clamp(da, -5 * dt, 5 * dt);
        t.vx = Math.cos(na) * sp; t.vy = Math.sin(na) * sp;
      }
    }
    t.x += t.vx * dt; t.y += t.vy * dt;
    if (t.life < 0.12) t.z = Math.max(0, t.z - 100 * dt);
    let dead = t.life <= 0;
    // muri
    if (!dead && (t.x < 0 || t.x > RW || t.y < 0 || t.y > RH)) {
      if (P.fx.bounce && t.bounces < 3) {
        if (t.x < 0 || t.x > RW) t.vx = -t.vx;
        if (t.y < 0 || t.y > RH) t.vy = -t.vy;
        t.x = clamp(t.x, 0, RW); t.y = clamp(t.y, 0, RH); t.bounces++;
      } else dead = true;
    }
    // ostacoli
    if (!dead) {
      const c = Math.floor(t.x / TILE), r = Math.floor(t.y / TILE), tt = tileAt(c, r);
      if (tt > 0 && solidShot(tt)) {
        const i2 = r * COLS + c;
        if (tt === T_CRATE) { hitTile(i2, 1); dead = true; }
        else if (tt === T_FIRE) { hitTile(i2, t.dmg > 4 ? 2 : 1); dead = true; }
        else if (!P.fx.spectral) {
          if (P.fx.bounce && t.bounces < 3) {
            const pc = Math.floor((t.x - t.vx * dt) / TILE), pr = Math.floor((t.y - t.vy * dt) / TILE);
            if (pc !== c) t.vx = -t.vx; if (pr !== r) t.vy = -t.vy;
            t.x -= t.vx * dt * 0; t.bounces++;
          } else dead = true;
        }
      }
    }
    // nemici
    if (!dead) {
      for (const e of run.enemies) {
        if (e.dead || e.spawnT > 0 || e.untouchable) continue;
        if (t.hit.has(e)) continue;
        const hz = e.hitZ || 0;
        if (Math.hypot(e.x - t.x, e.y - hz - t.y) < e.r + t.r) {
          damageEnemy(e, t.dmg, t);
          if (P.fx.pierce) t.hit.add(e); else { dead = true; break; }
        }
      }
    }
    if (dead) { splash(t); tearsArr.splice(i, 1); }
  }
}

function hitTile(i, dmg) {
  const room = run.room;
  const t = room.tiles[i];
  const x = (i % COLS) * TILE + 12, y = ((i / COLS) | 0) * TILE + 12;
  if (t === T_CRATE) {
    room.tileHp[i] = Math.max(0, room.tileHp[i] - dmg);
    burst(x, y, 4, '#8e5a34', 50, 0.3, 1, 8);
    if (room.tileHp[i] <= 0) destroyTile(i);
    else Sound.play('hit', 0.05);
  } else if (t === T_FIRE) {
    room.tileHp[i] = Math.max(0, room.tileHp[i] - dmg);
    burst(x, y - 6, 3, '#ff9a30', 40, 0.3, 1, 10);
    if (room.tileHp[i] <= 0) {
      room.tiles[i] = T_EMPTY; room.fireOut = room.fireOut || new Set(); room.fireOut.add(i);
      burst(x, y - 6, 10, '#777', 40, 0.6, 1, 10); Sound.play('splash');
      if (Math.random() < 0.25) dropRandom(x, y);
    }
  }
}

function destroyTile(i) {
  const room = run.room;
  const t = room.tiles[i];
  const x = (i % COLS) * TILE + 12, y = ((i / COLS) | 0) * TILE + 12;
  if (t === T_METAL || t === T_EMPTY || t === T_SPIKES || t === T_PIT) return;
  room.tiles[i] = T_EMPTY;
  if (t === T_FIRE) { room.fireOut = room.fireOut || new Set(); room.fireOut.add(i); }
  const col = t === T_CRATE ? '#8e5a34' : run.floor.theme.rock[0];
  burst(x, y, 12, col, 80, 0.6, 2, 6);
  decal(x, y, 8, 'rgba(0,0,0,0.25)');
  if (t === T_CRATE && Math.random() < 0.3) dropRandom(x, y);
  if (t === T_XROCK) {
    const roll = Math.random();
    if (roll < 0.4) spawnPickup('ink', x, y);
    else if (roll < 0.6) { spawnPickup('bomb', x, y); spawnPickup('bomb', x, y); }
    else if (roll < 0.8) spawnPickup('key', x, y);
    else { spawnPickup('coin5', x, y); spawnPickup('coin', x, y); }
  }
  run.flowKey = '';
}

// ================================================================== PICKUP
function randomPickupType(luck = 0) {
  const r = Math.random();
  if (r < 0.34) return Math.random() < 0.07 + luck * 0.02 ? 'coin5' : 'coin';
  if (r < 0.55) return Math.random() < 0.3 ? 'heartHalf' : 'heart';
  if (r < 0.69) return 'key';
  if (r < 0.83) return 'bomb';
  if (r < 0.9) return 'ink';
  if (r < 0.96) return 'chest';
  return 'chestGold';
}
function spawnPickup(type, x, y, still) {
  const a = Math.random() * Math.PI * 2, s = still ? 0 : rnd(30, 70);
  const p = makePickup(type, x, y, { vx: Math.cos(a) * s, vy: Math.sin(a) * s, vz: still ? 0 : 70, z: still ? 0 : 1 });
  run.room.pickups.push(p);
  return p;
}
function dropRandom(x, y) { spawnPickup(randomPickupType(P.st.luck), x, y); }

function freeSpotNear(x, y) {
  let best = [x, y], bd = 1e9;
  for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) {
    if (run.room.tiles[r * COLS + c] !== T_EMPTY) continue;
    const cx = c * TILE + 12, cy = r * TILE + 12;
    const d = Math.hypot(cx - x, cy - y);
    if (d < bd) { bd = d; best = [cx, cy]; }
  }
  return best;
}

function updatePickups(dt) {
  const room = run.room;
  for (let i = room.pickups.length - 1; i >= 0; i--) {
    const p = room.pickups[i];
    p.t += dt;
    if (p.z > 0 || p.vz > 0) { p.vz -= 300 * dt; p.z += p.vz * dt; if (p.z <= 0) { p.z = 0; p.vz = p.vz < -40 ? -p.vz * 0.35 : 0; } }
    if (P.fx.magnet && !p.price && p.t > 0.4 && ['coin', 'coin5', 'key', 'bomb', 'heart', 'heartHalf', 'ink'].includes(p.type)) {
      const d = Math.hypot(P.x - p.x, P.y - p.y);
      if (d < 90 && d > 2) { p.vx += (P.x - p.x) / d * 220 * dt; p.vy += (P.y - p.y) / d * 220 * dt; }
    }
    if (p.vx || p.vy) {
      moveBox(p, p.vx * dt, p.vy * dt, 'pickup');
      const f = Math.pow(0.02, dt); p.vx *= f; p.vy *= f;
      if (Math.abs(p.vx) < 1) p.vx = 0; if (Math.abs(p.vy) < 1) p.vy = 0;
    }
    if (run.dying) continue;
    const d = Math.hypot(P.x - p.x, P.y - 2 - p.y);
    if (d < 11 && p.z < 4 && p.t > 0.15) {
      if (collectPickup(p)) room.pickups.splice(i, 1);
    }
  }
}

function collectPickup(p) {
  const room = run.room;
  if (p.opened) return false;
  if (p.price) {
    if (P.coins < p.price) { if (!p.denyT || p.t - p.denyT > 1) { Sound.play('deny'); p.denyT = p.t; } return false; }
    if ((p.type === 'heart' || p.type === 'heartHalf') && P.hp >= P.maxHp) return false;
    if (p.type === 'ink' && P.maxHp + P.soul >= 24) return false;
    P.coins -= p.price; p.price = 0; Sound.play('buy');
  }
  switch (p.type) {
    case 'coin': P.coins = Math.min(99, P.coins + 1); Sound.play('coin'); break;
    case 'coin5': P.coins = Math.min(99, P.coins + 5); Sound.play('coin'); break;
    case 'heart': case 'heartHalf':
      if (P.hp >= P.maxHp) { pushAway(p); return false; }
      P.heal(p.type === 'heart' ? 2 : 1); Sound.play('heart'); break;
    case 'ink':
      if (P.maxHp + P.soul >= 24) { pushAway(p); return false; }
      P.addSoul(2); Sound.play('heart'); break;
    case 'bomb': P.bombs = Math.min(99, P.bombs + 1); Sound.play('bombPick'); break;
    case 'key': P.keys = Math.min(99, P.keys + 1); Sound.play('key'); break;
    case 'chest': case 'chestGold': {
      if (p.type === 'chestGold') {
        if (P.keys <= 0) { if (!p.denyT || p.t - p.denyT > 1) { Sound.play('deny'); p.denyT = p.t; } pushAway(p, 30); return false; }
        P.keys--; Sound.play('unlock');
      }
      const gold = p.type === 'chestGold';
      p.opened = true; p.type = 'chestOpen'; Sound.play('chest');
      const n = rndi(2, 3) + (gold ? 1 : 0);
      if (Math.random() < (gold ? 0.2 : 0.06)) room.pedestals.push(makePedestal(p.x, p.y > 40 ? p.y - 22 : p.y + 22, drawItem('treasure')));
      for (let i = 0; i < n; i++) spawnPickup(pick(['coin', 'coin', 'coin', 'heart', 'key', 'bomb', 'ink']), p.x, p.y);
      return false;
    }
    default: return false;
  }
  burst(p.x, p.y, 6, '#fff6c0', 40, 0.25, 1, 4);
  return true;
}
function pushAway(p, s = 60) {
  const d = Math.hypot(p.x - P.x, p.y - P.y) || 1;
  p.vx = (p.x - P.x) / d * s; p.vy = (p.y - P.y) / d * s;
}

function updatePedestals(dt) {
  for (const pd of run.room.pedestals) {
    pd.cd = Math.max(0, pd.cd - dt);
    if (pd.taken || !pd.item || run.dying) continue;
    if (Math.hypot(P.x - pd.x, P.y - pd.y) < 13 && pd.cd <= 0) {
      if (pd.price && P.coins < pd.price) { Sound.play('deny'); pd.cd = 1; continue; }
      if (pd.hprice) {
        const canContainers = P.maxHp / 2 > pd.hprice;
        const canSoul = P.soul >= pd.hprice * 6;
        if (!canContainers && !canSoul) { Sound.play('deny'); pd.cd = 1; banner('NON HAI ABBASTANZA CUORI', ''); continue; }
        if (canContainers) { P.maxHp -= pd.hprice * 2; P.hp = Math.min(P.hp, P.maxHp); if (P.hp <= 0) P.hp = 1; }
        else P.soul -= pd.hprice * 6;
      }
      if (pd.price) { P.coins -= pd.price; pd.price = 0; Sound.play('buy'); }
      const id = pd.item;
      const def = ITEMS[id];
      if (def.active) {
        const old = P.active;
        P.active = { id, charge: pd.charge != null ? pd.charge : def.charge, max: def.charge };
        if (old) { pd.item = old.id; pd.charge = old.charge; pd.cd = 1.2; }
        else { pd.item = null; pd.taken = true; }
      } else {
        P.items.push(id);
        def.apply(P);
        recompute(P);
        revealMap();
        pd.item = null; pd.taken = true;
      }
      P.holdT = 1.0; P.holdIcon = id;
      banner(def.name.toUpperCase(), def.desc.toUpperCase(), id);
      Sound.play('item'); vibrate(40);
      burst(pd.x, pd.y - 20, 16, '#fff2a0', 60, 0.6, 1, 10);
    }
  }
}

// ================================================================== BOMBE
function placeBomb(free, big) {
  if (!free) { if (P.bombs <= 0) { Sound.play('deny'); return; } P.bombs--; }
  run.bombs.push({ x: P.x, y: P.y, vx: 0, vy: 0, t: 1.6, big: !!big, hw: 4, hh: 4 });
  Sound.play('place');
}
function updateBombs(dt) {
  for (let i = run.bombs.length - 1; i >= 0; i--) {
    const b = run.bombs[i];
    b.t -= dt;
    const d = Math.hypot(P.x - b.x, P.y - b.y);
    if (d < 10 && b.t < 1.45) { const s = 90; b.vx += (b.x - P.x) / (d || 1) * s * dt * 10; b.vy += (b.y - P.y) / (d || 1) * s * dt * 10; }
    if (b.vx || b.vy) { moveBox(b, b.vx * dt, b.vy * dt, 'walk'); const f = Math.pow(0.05, dt); b.vx *= f; b.vy *= f; }
    if (Math.random() < 0.3) part(b.x + 3, b.y - 12, rnd(-10, 10), rnd(-30, -10), 0.3, '#ffcc40', 1);
    if (b.t <= 0) { run.bombs.splice(i, 1); explode(b.x, b.y, b.big ? 62 : 44, b.big ? 60 : 40); }
  }
}
function explode(x, y, rad, dmg) {
  Sound.play('boom', 0.05); shake(5, 0.4); vibrate(120);
  burst(x, y, 30, '#ffb030', 140, 0.5, 2, 4);
  burst(x, y, 20, '#ff5a20', 100, 0.6, 2, 8);
  burst(x, y, 16, '#555', 60, 0.9, 2, 10);
  decal(x, y, rad * 0.45, 'rgba(10,6,4,0.55)');
  for (const e of run.enemies) if (!e.dead && Math.hypot(e.x - x, e.y - y) < rad + e.r && !(e.z > 20)) damageEnemy(e, dmg, { vx: e.x - x, vy: e.y - y, boom: true });
  if (Math.hypot(P.x - x, P.y - y) < rad * 0.85 + 5) hurtPlayer(2, 'UNA BOMBA');
  for (let i = 0; i < COLS * ROWS; i++) {
    const cx = (i % COLS) * TILE + 12, cy = ((i / COLS) | 0) * TILE + 12;
    if (Math.hypot(cx - x, cy - y) < rad + 8) {
      const t = run.room.tiles[i];
      if (t === T_ROCK || t === T_XROCK || t === T_CRATE || t === T_FIRE) destroyTile(i);
    }
  }
  for (const dir in run.room.doors) {
    const door = run.room.doors[dir];
    const [dx, dy] = DOOR_POS[dir];
    if (Math.hypot(dx - x, dy - y) < rad + 18) {
      if (door.hidden && door.kind === 'secret') { door.hidden = false; Sound.play('unlock'); burst(dx, dy, 20, run.floor.theme.wall[0], 90, 0.7, 2, 6); banner('PASSAGGIO SEGRETO!', ''); otherSide(door, run.room).seen = true; }
    }
  }
  for (const b of run.bombs) if (Math.hypot(b.x - x, b.y - y) < rad) b.t = Math.min(b.t, 0.08);
  for (const p of run.room.pickups) if (!p.price && !p.still) { const d = Math.hypot(p.x - x, p.y - y); if (d < rad + 20) { p.vx += (p.x - x) / (d || 1) * 120; p.vy += (p.y - y) / (d || 1) * 120; } }
}

// ================================================================== NEMICI
function spawnEnemy(type, x, y, opts = {}) {
  const def = ENEMIES[type];
  const mult = FLOOR_HP[run.floorIdx];
  const e = {
    type, def, x, y, vx: 0, vy: 0, kx: 0, ky: 0, r: def.r, hw: def.r * 0.75, hh: def.r * 0.6,
    hp: def.hp * mult, maxHp: def.hp * mult, fly: !!def.fly, dead: false, spawnT: opts.noGrace ? 0 : 0.7,
    t: Math.random() * 3, timer: rnd(0.5, 1.5), cd: rnd(1, 2), flash: 0, anim: Math.random() * 2,
    poison: 0, burn: 0, slow: 0, dot: 0, z: 0, st: 'idle', dir: pick(['up', 'down', 'left', 'right']),
    champion: opts.champion || null, h: def.h || 0,
  };
  if (e.champion) { e.hp *= 2; e.maxHp *= 2; }
  if (type === 'skull') { const a = pick([0.785, 2.356, 3.927, 5.498]); e.vx = Math.cos(a) * 62; e.vy = Math.sin(a) * 62; }
  run.enemies.push(e);
  return e;
}

function spawnBoss(room) {
  const def = BOSSES[room.boss];
  const hp = def.hp * BOSS_HP[run.floorIdx];
  const e = {
    type: 'boss', bk: room.boss, boss: true, def, x: 156, y: 70, vx: 0, vy: 0, kx: 0, ky: 0, r: def.r, hw: def.r * 0.8, hh: def.r * 0.6,
    hp, maxHp: hp, fly: !!def.fly, dead: false, spawnT: 0.4, t: 0, timer: 1.2, cd: 2, flash: 0, anim: 0,
    poison: 0, burn: 0, slow: 0, dot: 0, z: 0, st: 'idle', count: 0, squash: 0, phase: 1, alpha: 1, name: def.name,
  };
  if (room.boss === 'ombra') e.y = 50;
  run.enemies.push(e);
  run.bossRef = e;
  run.bossIntro = 1.8;
  Sound.play('roar');
}

function enemyShot(x, y, ang, speed, opts = {}) {
  run.eshots.push({ x, y, vx: Math.cos(ang) * speed, vy: Math.sin(ang) * speed, r: opts.r || 3, life: opts.life || 4, z: opts.z == null ? 8 : opts.z, enemy: true, dark: !!opts.dark, burst: opts.burst || 0, bt: opts.bt || 0, grav: opts.grav || 0, vz: opts.vz || 0, dmg: opts.dmg || 1, name: opts.name || '' });
}
function aimAng(e) { return Math.atan2(P.y - 4 - e.y, P.x - e.x); }
function ring(e, n, speed, offset = 0, opts) { for (let i = 0; i < n; i++) enemyShot(e.x, e.y, offset + (i / n) * Math.PI * 2, speed, opts); }
function spread(e, n, speed, gap, opts) { const a = aimAng(e); for (let i = 0; i < n; i++) enemyShot(e.x, e.y, a + (i - (n - 1) / 2) * gap, speed, opts); }

function damageEnemy(e, dmg, src) {
  if (e.dead) return;
  e.hp -= dmg; e.flash = 0.08;
  Sound.play('hit', 0.05);
  if (src && (src.vx || src.vy)) {
    const sp = Math.hypot(src.vx, src.vy) || 1;
    const kb = (src.boom ? 160 : 55) * (P.fx.knock || 1) * (e.boss ? 0.15 : 1) * (e.def.still ? 0.2 : 1);
    e.kx += src.vx / sp * kb; e.ky += src.vy / sp * kb;
  }
  if (src && !src.enemy && !src.boom) {
    if (P.fx.poison) e.poison = 3;
    if (P.fx.burn) e.burn = 2.5;
    if (P.fx.slow) e.slow = 2;
  }
  if (e.hp <= 0) killEnemy(e);
}

function killEnemy(e) {
  e.dead = true;
  run.stats.kills++;
  Sound.play('kill', 0.04);
  const ink = e.type === 'blot' || e.type === 'blotS';
  burst(e.x, e.y, e.boss ? 60 : 14, ink ? '#232846' : '#7a1020', e.boss ? 150 : 80, 0.6, e.boss ? 2 : 1, 8);
  decal(e.x, e.y, e.boss ? 20 : e.r, ink ? 'rgba(20,24,52,0.7)' : 'rgba(90,10,20,0.55)');
  if (e.type === 'blot') for (let i = 0; i < 2; i++) { const c = spawnEnemy('blotS', e.x + (i ? 6 : -6), e.y, { noGrace: true }); c.kx = (i ? 60 : -60); c.spawnT = 0.15; }
  if (e.type === 'mushroom') ring(e, 8, 80, Math.PI / 8);
  if (e.champion) dropRandom(e.x, e.y);
  else if (!e.boss && Math.random() < 0.035 + P.st.luck * 0.01) dropRandom(e.x, e.y);
  if (e.boss) bossDeath(e);
}

function bossDeath(e) {
  shake(6, 0.8); vibrate(250);
  Sound.play('boom');
  for (let i = 0; i < 4; i++) burst(e.x + rnd(-16, 16), e.y + rnd(-16, 16), 20, pick(['#ffb030', '#ff5a20', '#fff']), 120, 0.8, 2, 10);
  for (const o of run.enemies) if (!o.dead && !o.boss) { o.dead = true; burst(o.x, o.y, 10, '#7a1020', 70, 0.5, 1, 8); }
  run.eshots.length = 0;
  run.bossRef = null;
  const room = run.room;
  if (e.bk === 'ombra') { run.won = 3.0; Sound.stopMusic(); Sound.play('win'); return; }
  setTimeout(() => { if (run && run.room === room) Sound.playMusic('f' + run.floorIdx); }, 1500);
  room.pedestals.push(makePedestal(156, 56, drawItem('boss')));
  room.trapdoor = { x: 156, y: 118 };
  spawnPickup('heart', 120, 100); if (Math.random() < 0.5) spawnPickup('heart', 190, 100);
  const pactChance = 0.3 + (run.roomDamaged ? 0 : 0.35);
  if (run.floor.pact && Math.random() < pactChance) {
    for (const dir in room.doors) { const d = room.doors[dir]; if (d.kind === 'pact') { d.hidden = false; run.floor.pact.seen = true; } }
    banner('SI È APERTA UNA PORTA OSCURA...', 'LA STANZA DEL PATTO');
  }
}

// ---------------------------------------------------------------- IA
const AI = {
  gnat(e, dt, sp) {
    const dx = P.x - e.x, dy = P.y - 6 - e.y, d = Math.hypot(dx, dy) || 1;
    const v = 42 * sp;
    e.vx += ((dx / d) * v - e.vx) * 2 * dt + rnd(-1, 1) * 240 * dt;
    e.vy += ((dy / d) * v - e.vy) * 2 * dt + rnd(-1, 1) * 240 * dt;
    moveBox(e, e.vx * dt, e.vy * dt, 'fly');
  },
  blot(e, dt, sp) {
    const small = e.type === 'blotS';
    if (e.hopT > 0) {
      e.hopT -= dt;
      moveBox(e, e.vx * dt, e.vy * dt, 'walk');
      e.z = Math.sin(Math.PI * (1 - e.hopT / 0.38)) * 6;
      if (e.hopT <= 0) { e.z = 0; e.squash = 0.15; if (!small && run.floorIdx >= 2 && Math.random() < 0.3) { for (let i = 0; i < 4; i++) enemyShot(e.x, e.y, i * Math.PI / 2, 80); Sound.play('eshot'); } }
    } else {
      e.timer -= dt;
      if (e.timer <= 0) {
        const [fx, fy] = flowDir(e);
        const v = (small ? 100 : 82) * sp;
        e.vx = fx * v; e.vy = fy * v; e.hopT = 0.38; e.timer = rnd(0.6, 1.2) / sp;
      }
    }
  },
  spider(e, dt, sp) {
    e.timer -= dt;
    if (e.timer <= 0) {
      if (e.st === 'run') { e.st = 'idle'; e.vx = e.vy = 0; e.timer = rnd(0.25, 0.7); }
      else {
        const [fx, fy] = flowDir(e);
        const a = Math.atan2(fy, fx) + rnd(-1.1, 1.1), v = 110 * sp;
        e.vx = Math.cos(a) * v; e.vy = Math.sin(a) * v; e.st = 'run'; e.timer = rnd(0.25, 0.5);
      }
    }
    moveBox(e, e.vx * dt, e.vy * dt, 'walk');
  },
  eye(e, dt, sp) {
    e.cd -= dt * sp;
    if (e.cd <= 0) {
      e.cd = Math.max(1.2, 2.1 - run.floorIdx * 0.12);
      if (Math.hypot(P.x - e.x, P.y - e.y) > 28) {
        if (run.floorIdx >= 2) spread(e, 3, 105, 0.22); else spread(e, 1, 100, 0);
        Sound.play('eshot');
      }
    }
  },
  beetle(e, dt, sp) {
    if (e.st === 'charge') {
      const [dx, dy] = DV[e.dir];
      const h = moveBox(e, dx * 175 * sp * dt, dy * 175 * sp * dt, 'walk');
      if (Math.random() < 0.4) part(e.x, e.y + 4, rnd(-10, 10), rnd(-10, 10), 0.3, '#5a4a3a', 1);
      if (h.hx || h.hy) { e.st = 'stun'; e.timer = 0.7; shake(1.5, 0.1); Sound.play('door', 0.1); }
      return;
    }
    if (e.st === 'prep') { e.timer -= dt; if (e.timer <= 0) e.st = 'charge'; return; }
    if (e.st === 'stun') { e.timer -= dt; if (e.timer <= 0) e.st = 'walk'; return; }
    e.timer -= dt;
    if (e.timer <= 0) { e.dir = pick(['up', 'down', 'left', 'right']); e.timer = rnd(0.8, 1.8); }
    const [dx, dy] = DV[e.dir];
    const h = moveBox(e, dx * 34 * sp * dt, dy * 34 * sp * dt, 'walk');
    if (h.hx || h.hy) e.timer = 0;
    // allineato col giocatore?
    const ax = Math.abs(P.x - e.x), ay = Math.abs(P.y - e.y);
    let cd = null;
    if (ax < 9) cd = P.y > e.y ? 'down' : 'up';
    else if (ay < 9) cd = P.x > e.x ? 'right' : 'left';
    if (cd && clearLine(e, P)) { e.dir = cd; e.st = 'prep'; e.timer = 0.25; }
  },
  ghost(e, dt, sp) {
    e.t += dt;
    const dx = P.x - e.x, dy = P.y - e.y, d = Math.hypot(dx, dy) || 1;
    const v = 24 * sp;
    e.vx = dx / d * v + Math.cos(e.t * 2) * 18; e.vy = dy / d * v + Math.sin(e.t * 1.6) * 18;
    moveBox(e, e.vx * dt, e.vy * dt, 'fly');
    e.cd -= dt * sp;
    if (e.cd <= 0) { e.cd = 2.8; spread(e, 3, 92, 0.3); Sound.play('eshot'); }
  },
  bat(e, dt, sp) {
    if (e.st === 'dive') {
      e.timer -= dt;
      moveBox(e, e.vx * dt, e.vy * dt, 'fly');
      if (e.timer <= 0) { e.st = 'orbit'; e.cd = rnd(2, 3.5); }
      return;
    }
    e.ang = (e.ang || Math.random() * 6) + dt * 1.4 * sp;
    const tx = P.x + Math.cos(e.ang) * 66, ty = P.y + Math.sin(e.ang) * 50;
    const dx = tx - e.x, dy = ty - e.y, d = Math.hypot(dx, dy) || 1;
    const v = Math.min(80 * sp, d * 3);
    moveBox(e, dx / d * v * dt, dy / d * v * dt, 'fly');
    e.cd -= dt;
    if (e.cd <= 0) { const a = aimAng(e); e.vx = Math.cos(a) * 150 * sp; e.vy = Math.sin(a) * 150 * sp; e.st = 'dive'; e.timer = 0.55; }
  },
  mushroom(e, dt, sp) {
    e.cd -= dt * sp;
    if (e.cd <= 0) {
      e.cd = 2.4; e.alt = !e.alt;
      for (let i = 0; i < 4; i++) enemyShot(e.x, e.y - 4, i * Math.PI / 2 + (e.alt ? Math.PI / 4 : 0), 82);
      e.squash = 0.2; Sound.play('eshot');
    }
  },
  skull(e, dt, sp) {
    e.x += e.vx * sp * dt; e.y += e.vy * sp * dt;
    if (e.x < e.hw) { e.x = e.hw; e.vx = Math.abs(e.vx); } if (e.x > RW - e.hw) { e.x = RW - e.hw; e.vx = -Math.abs(e.vx); }
    if (e.y < e.hh) { e.y = e.hh; e.vy = Math.abs(e.vy); } if (e.y > RH - e.hh) { e.y = RH - e.hh; e.vy = -Math.abs(e.vy); }
    if (Math.random() < 0.2) part(e.x + rnd(-3, 3), e.y - 8, 0, -20, 0.3, '#b46cff', 1);
  },
};
AI.blotS = AI.blot;

function clearLine(a, b) {
  const steps = Math.ceil(Math.hypot(b.x - a.x, b.y - a.y) / 8);
  for (let i = 1; i < steps; i++) {
    const x = a.x + (b.x - a.x) * i / steps, y = a.y + (b.y - a.y) * i / steps;
    const t = tileAt(Math.floor(x / TILE), Math.floor(y / TILE));
    if (t > 0 && solidWalk(t)) return false;
  }
  return true;
}

// ---------------------------------------------------------------- BOSS IA
const BOSS_AI = {
  re(e, dt) {
    const hard = e.hp < e.maxHp * 0.5;
    if (e.squash > 0) e.squash -= dt;
    switch (e.st) {
      case 'idle':
        e.timer -= dt;
        if (e.timer <= 0) {
          e.count++;
          if (e.count % 3 === 0) { e.st = 'rise'; e.timer = 0.5; Sound.play('jump'); }
          else {
            e.st = 'hop'; e.hopT = 0; e.hopDur = 0.62;
            const dx = P.x - e.x, dy = P.y - e.y, d = Math.hypot(dx, dy) || 1, v = Math.min(140, d / e.hopDur);
            e.vx = dx / d * v; e.vy = dy / d * v; Sound.play('jump');
          }
        }
        break;
      case 'hop':
        e.hopT += dt;
        moveBox(e, e.vx * dt, e.vy * dt, 'walk');
        e.z = Math.sin(Math.PI * Math.min(1, e.hopT / e.hopDur)) * 20;
        if (e.hopT >= e.hopDur) {
          e.z = 0; e.st = 'idle'; e.timer = hard ? 0.35 : 0.6; e.squash = 0.25;
          ring(e, hard ? 10 : 8, 88, Math.random()); Sound.play('slam'); shake(2, 0.15);
        }
        break;
      case 'rise':
        e.timer -= dt; e.z += 420 * dt;
        if (e.timer <= 0) { e.st = 'hover'; e.timer = 1.0; e.untouchable = true; }
        break;
      case 'hover': {
        e.timer -= dt;
        const dx = P.x - e.x, dy = P.y - e.y, d = Math.hypot(dx, dy) || 1;
        moveBox(e, dx / d * Math.min(130, d * 4) * dt, dy / d * Math.min(130, d * 4) * dt, 'walk');
        if (e.timer <= 0) { e.st = 'fall'; }
        break;
      }
      case 'fall':
        e.z -= 600 * dt;
        if (e.z <= 0) {
          e.z = 0; e.untouchable = false; e.st = 'idle'; e.timer = 0.8; e.squash = 0.35;
          ring(e, hard ? 14 : 12, 95, 0); shake(5, 0.3); Sound.play('slam'); vibrate(60);
          const smalls = run.enemies.filter(o => !o.dead && o.type === 'blotS').length;
          if (smalls < 4) for (let i = 0; i < 2; i++) spawnEnemy('blotS', e.x + (i ? 20 : -20), e.y + 6, { noGrace: true });
        }
        break;
    }
    e.hitZ = 0; e.noContact = e.z > 10;
  },
  sciame(e, dt) {
    const hard = e.hp < e.maxHp * 0.4;
    e.t += dt;
    const dx = P.x - e.x, dy = P.y - 30 - e.y, d = Math.hypot(dx, dy) || 1;
    const v = hard ? 42 : 28;
    moveBox(e, (dx / d * v + Math.cos(e.t * 1.3) * 30) * dt, (dy / d * v + Math.sin(e.t * 2.1) * 20) * dt, 'fly');
    e.cd -= dt;
    if (e.cd <= 0) {
      e.cd = hard ? 1.5 : 2.2; e.count++;
      const m = e.count % 3;
      if (m === 0) { const n = run.enemies.filter(o => !o.dead && o.type === 'gnat').length; for (let i = 0; i < (hard ? 3 : 2) && n + i < 6; i++) spawnEnemy('gnat', e.x + rnd(-10, 10), e.y + 10, { noGrace: true }); Sound.play('roar', 1); }
      else if (m === 1) { spread(e, 5, 105, 0.22); Sound.play('eshot'); }
      else { ring(e, hard ? 12 : 8, 85, e.t); Sound.play('eshot'); }
    }
    e.hitZ = 10;
  },
  tessitrice(e, dt) {
    const hard = e.hp < e.maxHp * 0.5;
    switch (e.st) {
      case 'idle':
        e.timer -= dt;
        if (e.timer <= 0) {
          e.st = 'dash'; e.timer = 0.6;
          const tx = P.x + rnd(-30, 30), ty = P.y + rnd(-20, 20), dx = tx - e.x, dy = ty - e.y, d = Math.hypot(dx, dy) || 1;
          e.vx = dx / d * 215; e.vy = dy / d * 215;
        }
        break;
      case 'dash': {
        e.timer -= dt;
        const h = moveBox(e, e.vx * dt, e.vy * dt, 'walk');
        if (Math.random() < 0.5) part(e.x, e.y + 6, rnd(-15, 15), rnd(-10, 0), 0.3, '#5a4a3a', 1);
        if (e.timer <= 0 || h.hx || h.hy) {
          e.st = 'idle'; e.timer = hard ? 0.45 : 0.75; e.count++;
          spread(e, 3, 78, 0.35, { r: 4 }); Sound.play('eshot');
          if (hard) ring(e, 8, 70, e.count);
          if (e.count % 3 === 0) { const n = run.enemies.filter(o => !o.dead && o.type === 'spider').length; for (let i = 0; i < 2 && n + i < 4; i++) spawnEnemy('spider', e.x + (i ? 14 : -14), e.y, { noGrace: true }); }
        }
        break;
      }
    }
  },
  colosso(e, dt) {
    const hard = e.hp < e.maxHp * 0.5;
    switch (e.st) {
      case 'idle': {
        const [fx, fy] = flowDir(e);
        moveBox(e, fx * 28 * dt, fy * 28 * dt, 'walk');
        e.anim += dt;
        e.timer -= dt;
        if (e.timer <= 0) {
          const d = dist(e, P);
          if (hard && Math.random() < 0.35) { e.st = 'throw'; e.timer = 0.5; }
          else if (d < 95) { e.st = 'raise'; e.timer = 0.6; }
          else { e.st = 'prep'; e.timer = 0.5; }
        }
        break;
      }
      case 'raise':
        e.timer -= dt; e.z = (0.6 - e.timer) * 18;
        if (e.timer <= 0) {
          e.z = 0; e.st = 'idle'; e.timer = hard ? 1.6 : 2.2;
          ring(e, hard ? 16 : 14, 85, Math.random()); shake(5, 0.35); Sound.play('slam'); vibrate(60);
          burst(e.x, e.y + 8, 20, run.floor.theme.rock[0], 90, 0.6, 2, 2);
        }
        break;
      case 'prep':
        e.timer -= dt;
        if (e.timer <= 0) { const a = aimAng(e); e.vx = Math.cos(a) * 185; e.vy = Math.sin(a) * 185; e.st = 'charge'; e.timer = 1.6; }
        break;
      case 'charge': {
        e.timer -= dt;
        const h = moveBox(e, e.vx * dt, e.vy * dt, 'walk');
        if (Math.random() < 0.6) part(e.x, e.y + 8, rnd(-15, 15), rnd(-10, 0), 0.4, '#6a6058', 2);
        if (h.hx || h.hy || e.timer <= 0) {
          e.st = 'stun'; e.timer = 1.1; shake(6, 0.4); Sound.play('slam'); vibrate(80);
          for (let i = 0; i < (hard ? 8 : 6); i++) run.hazards.push({ x: rnd(20, RW - 20), y: rnd(20, RH - 20), t: rnd(0.7, 1.3), r: 11 });
          run.hazards.push({ x: P.x, y: P.y, t: 1.0, r: 11 });
        }
        break;
      }
      case 'stun': e.timer -= dt; if (e.timer <= 0) { e.st = 'idle'; e.timer = 1.5; } break;
      case 'throw':
        e.timer -= dt;
        if (e.timer <= 0) { enemyShot(e.x, e.y - 10, aimAng(e), 115, { r: 6, burst: 6, bt: 0.75 }); e.st = 'idle'; e.timer = 1.4; Sound.play('eshot'); }
        break;
    }
    e.hitZ = 6;
  },
  ombra(e, dt) {
    const f = e.hp / e.maxHp;
    e.phase = f > 0.66 ? 1 : f > 0.33 ? 2 : 3;
    e.t += dt; e.anim += dt;
    const dark = { dark: true, r: 3 };
    if (e.phase === 1) {
      // si tiene a distanza e spara come Mattia
      const dx = e.x - P.x, dy = e.y - P.y, d = Math.hypot(dx, dy) || 1;
      const want = 105;
      const vx = dx / d * (want - d) * 1.2 + (-dy / d) * 40, vy = dy / d * (want - d) * 1.2 + (dx / d) * 40;
      const sp = Math.hypot(vx, vy) || 1, cap = 90;
      moveBox(e, vx / sp * Math.min(cap, sp) * dt, vy / sp * Math.min(cap, sp) * dt, 'walk');
      e.vx = vx; e.vy = vy;
      e.cd -= dt;
      if (e.cd <= 0) { e.cd = 0.85; e.burstN = 3; e.burstT = 0; }
      if (e.burstN > 0) { e.burstT -= dt; if (e.burstT <= 0) { e.burstT = 0.12; e.burstN--; enemyShot(e.x, e.y - 10, aimAng(e) + rnd(-0.08, 0.08), 150, dark); Sound.play('eshot'); } }
    } else if (e.phase === 2) {
      e.timer -= dt;
      if (e.st === 'out') { e.alpha = Math.max(0, e.alpha - dt * 4); e.untouchable = true; if (e.alpha <= 0) { e.x = rnd(40, RW - 40); e.y = rnd(30, RH - 30); if (Math.hypot(e.x - P.x, e.y - P.y) < 70) { e.x = RW - P.x; e.y = RH - P.y; } e.st = 'in'; } }
      else if (e.st === 'in') { e.alpha = Math.min(1, e.alpha + dt * 4); if (e.alpha >= 1) { e.untouchable = false; e.st = 'spiral'; e.timer = 1.3; e.spin = 0; } }
      else if (e.st === 'spiral') { e.spin += dt; e.cd -= dt; if (e.cd <= 0) { e.cd = 0.06; enemyShot(e.x, e.y - 10, e.spin * 7, 88, dark); enemyShot(e.x, e.y - 10, e.spin * 7 + Math.PI, 88, dark); } if (e.timer <= 0) { e.st = 'rest'; e.timer = 1.0; } }
      else { if (e.timer <= 0) { e.st = 'out'; Sound.play('jump'); } else if (Math.random() < dt * 1.5) { spread(e, 3, 120, 0.2, dark); Sound.play('eshot'); } }
    } else {
      // fase finale: spirale continua + raffiche mirate
      const dx = 156 - e.x, dy = 70 - e.y;
      moveBox(e, dx * dt, dy * dt, 'walk');
      e.alpha = Math.min(1, e.alpha + dt * 3); e.untouchable = false;
      e.cd -= dt;
      if (e.cd <= 0) { e.cd = 0.11; const a = e.t * 2.2; enemyShot(e.x, e.y - 10, a, 80, dark); enemyShot(e.x, e.y - 10, -a + Math.PI, 80, dark); }
      e.timer -= dt;
      if (e.timer <= 0) {
        e.timer = 2.0; spread(e, 5, 125, 0.18, dark); Sound.play('eshot');
        e.count++;
        if (e.count % 4 === 0 && run.enemies.filter(o => !o.dead && !o.boss).length < 3) { const s = spawnEnemy('blot', e.x + rnd(-30, 30), e.y + 30, { noGrace: true }); s.shadow = true; }
      }
    }
    e.hitZ = 14;
  },
};

function updateEnemies(dt) {
  updateFlow();
  const slowAll = FX.slowT > 0 ? 0.5 : 1;
  for (const e of run.enemies) {
    if (e.dead) continue;
    if (e.flash > 0) e.flash -= dt;
    if (e.spawnT > 0) { e.spawnT -= dt; continue; }
    // stati alterati
    if (e.poison > 0 || e.burn > 0) {
      e.poison -= dt; e.burn -= dt; e.dot -= dt;
      if (e.dot <= 0) { e.dot = 0.5; damageEnemy(e, e.burn > 0 ? 2.5 : 1.5 + P.st.damage * 0.2, null); if (e.dead) continue; part(e.x + rnd(-4, 4), e.y - 8, 0, -20, 0.4, e.burn > 0 ? '#ff7a2a' : '#3ec84a', 1); }
    }
    if (e.slow > 0) e.slow -= dt;
    const sp = (e.slow > 0 ? 0.5 : 1) * slowAll * (e.champion === '#ffd030' ? 1.25 : 1);
    // respinta
    if (e.kx || e.ky) {
      moveBox(e, e.kx * dt, e.ky * dt, e.fly ? 'fly' : 'walk');
      const f = Math.pow(0.0005, dt); e.kx *= f; e.ky *= f;
      if (Math.abs(e.kx) < 2) e.kx = 0; if (Math.abs(e.ky) < 2) e.ky = 0;
    }
    e.anim += dt * sp;
    if (e.squash > 0 && !e.boss) e.squash -= dt;
    if (e.boss) BOSS_AI[e.bk](e, dt * slowAll);
    else AI[e.type](e, dt, sp);
    // danno da contatto
    if (!e.noContact && !e.untouchable && !run.dying) {
      const hz = e.boss ? 0 : 0;
      if (Math.hypot(P.x - e.x, P.y - 4 - (e.y - hz)) < e.r + 5) hurtPlayer(e.boss && run.floorIdx >= 2 ? 2 : 1, e.boss ? e.name : (e.def.name || '').toUpperCase());
    }
  }
  run.enemies = run.enemies.filter(e => !e.dead);
}

function updateEShots(dt) {
  const k = FX.slowT > 0 ? 0.5 : 1;
  const arr = run.eshots;
  for (let i = arr.length - 1; i >= 0; i--) {
    const s = arr[i];
    s.x += s.vx * dt * k; s.y += s.vy * dt * k; s.life -= dt * k;
    let dead = s.life <= 0;
    if (s.burst) { s.bt -= dt * k; if (s.bt <= 0) { for (let j = 0; j < s.burst; j++) enemyShot(s.x, s.y, j / s.burst * Math.PI * 2, 85); burst(s.x, s.y, 8, run.floor.theme.rock[0], 60, 0.4, 2, 6); dead = true; } }
    if (s.x < -4 || s.y < -4 || s.x > RW + 4 || s.y > RH + 4) dead = true;
    if (!dead) { const t = tileAt(Math.floor(s.x / TILE), Math.floor(s.y / TILE)); if (t > 0 && solidShot(t) && t !== T_FIRE) dead = true; }
    if (!dead) {
      for (const f of P.familiars) if (f.kind === 'orbit' && Math.hypot(f.x - s.x, f.y - s.y) < 8 + s.r) { dead = true; break; }
    }
    if (!dead && Math.hypot(P.x - s.x, P.y - 5 - s.y) < 5 + s.r && !run.dying) {
      if (P.inv <= 0 && P.godT <= 0) { hurtPlayer(s.dmg, run.bossRef ? run.bossRef.name : 'UN PROIETTILE'); dead = true; }
    }
    if (dead) { burst(s.x, s.y, 4, s.dark ? '#3a2050' : '#a01828', 40, 0.25, 1, s.z); arr.splice(i, 1); }
  }
}

function updateHazards(dt) {
  for (let i = run.hazards.length - 1; i >= 0; i--) {
    const h = run.hazards[i];
    h.t -= dt;
    if (h.t <= 0) {
      burst(h.x, h.y, 12, run.floor.theme.rock[0], 70, 0.5, 2, 4); Sound.play('slam', 0.08);
      decal(h.x, h.y, 6, 'rgba(0,0,0,0.3)');
      if (Math.hypot(P.x - h.x, P.y - h.y) < h.r + 4) hurtPlayer(1, 'UN MASSO');
      run.hazards.splice(i, 1);
    }
  }
}

// ================================================================== FAMIGLI
function updateFamiliars(dt, shootDir) {
  P.trail.unshift([P.x, P.y]);
  if (P.trail.length > 40) P.trail.pop();
  let mini = 0, orb = 0;
  const orbCount = P.familiars.filter(f => f.kind === 'orbit').length;
  for (const f of P.familiars) {
    if (f.kind === 'mini') {
      const tp = P.trail[Math.min(P.trail.length - 1, 12 + mini * 10)] || [P.x, P.y];
      f.x = f.x == null ? P.x : f.x + (tp[0] - f.x) * Math.min(1, dt * 10);
      f.y = f.y == null ? P.y : f.y + (tp[1] - f.y) * Math.min(1, dt * 10);
      f.cd -= dt;
      if (shootDir && f.cd <= 0) {
        f.cd = 0.55;
        run.tears.push({ x: f.x, y: f.y - 2, vx: shootDir[0] * 210, vy: shootDir[1] * 210, dmg: 3.5, r: 2, life: 0.75, z: 8, hit: new Set(), bounces: 0, mini: true });
      }
      mini++;
    } else if (f.kind === 'orbit') {
      f.a += dt * 2.6;
      const a = f.a + (orb / Math.max(1, orbCount)) * Math.PI * 2;
      f.x = P.x + Math.cos(a) * 24; f.y = P.y - 4 + Math.sin(a) * 18;
      f.cd = (f.cd || 0) - dt;
      if (f.cd <= 0) for (const e of run.enemies) if (!e.dead && e.spawnT <= 0 && Math.hypot(e.x - f.x, e.y - f.y) < e.r + 7) { damageEnemy(e, 3, null); f.cd = 0.25; break; }
      orb++;
    }
  }
}

// ================================================================== OGGETTO ATTIVO
function useActive() {
  const a = P.active;
  if (!a || run.dying) return;
  if (a.charge < a.max) { Sound.play('deny'); return; }
  let used = true;
  switch (a.id) {
    case 'moka': if (P.hp >= P.maxHp) { used = false; break; } P.heal(4); Sound.play('heart'); burst(P.x, P.y - 10, 12, '#ff6a8a', 50, 0.6, 1, 10); break;
    case 'timbro': shake(6, 0.4); Sound.play('slam'); vibrate(100); for (const e of run.enemies) if (!e.dead) { damageEnemy(e, 40, null); } decal(P.x, P.y, 14, 'rgba(160,20,30,0.4)'); break;
    case 'plotter': for (let i = 0; i < 16; i++) { const an = i / 16 * Math.PI * 2; run.tears.push({ x: P.x, y: P.y - 2, vx: Math.cos(an) * 220, vy: Math.sin(an) * 220, dmg: P.st.damage, r: P.st.size, life: 0.9, z: 12, hit: new Set(), bounces: 0 }); } Sound.play('shoot'); break;
    case 'detonatore': placeBomb(true, true); break;
    case 'clessidra': FX.slowT = 8; Sound.play('power'); break;
    case 'biglietto': {
      const cands = run.floor.list.filter(r => r !== run.room && r.type !== 'boss' && r.type !== 'secret' && r.type !== 'pact');
      const unv = cands.filter(r => !r.visited);
      const dest = pick(unv.length ? unv : cands);
      if (!dest) { used = false; break; }
      Sound.play('fall');
      P.x = RW / 2; P.y = RH / 2 + 20;
      startTransition(null, () => enterRoom(dest, null));
      break;
    }
    case 'taccuino': for (let i = 0; i < 2; i++) dropRandom(P.x, P.y); Sound.play('power'); break;
    case 'fissativo': P.godT = 6; Sound.play('power'); break;
  }
  if (used) { a.charge = 0; vibrate(30); }
}

// ================================================================== TRANSIZIONI
function startTransition(dir, after) {
  run.trans = { dir, t: 0, dur: dir ? 0.3 : 0.45, after, done: false, snap: snapshotRoom() };
  if (dir) { run.trans.done = true; after(); }
}

function checkDoors() {
  const room = run.room;
  if (P.y < -8 && room.doors.up) return 'up';
  if (P.y > RH + 8 && room.doors.down) return 'down';
  if (P.x < -8 && room.doors.left) return 'left';
  if (P.x > RW + 8 && room.doors.right) return 'right';
  return null;
}

function tryUnlockDoors() {
  const room = run.room;
  if (!room.cleared) return;
  for (const dir in room.doors) {
    const door = room.doors[dir];
    if (!door.locked || door.hidden) continue;
    const [dx, dy] = DOOR_POS[dir];
    if (Math.hypot(P.x - dx, P.y - dy) < 22) {
      if (P.keys > 0) { P.keys--; door.locked = false; Sound.play('unlock'); burst(dx, dy, 10, '#f2c94c', 50, 0.4, 1, 6); }
      else if (!door.denyT || run.stats.time - door.denyT > 1.2) { door.denyT = run.stats.time; Sound.play('deny'); }
    }
  }
}

// ================================================================== AGGIORNAMENTO PRINCIPALE
function updateGame(dt, input) {
  if (run.trans) {
    const tr = run.trans;
    tr.t += dt;
    if (!tr.done && tr.t >= tr.dur / 2) { tr.done = true; tr.after(); }
    if (tr.t >= tr.dur) run.trans = null;
    return;
  }
  if (run.fall) {
    run.fall.t += dt;
    if (run.fall.t > 1.2 && !run.fall.done) { run.fall.done = true; startFloor(run.floorIdx + 1); }
    if (run.fall.t > 1.6) run.fall = null;
    return;
  }
  if (run.bossIntro > 0) { run.bossIntro -= dt; if (input.anyTap) run.bossIntro = 0; return; }
  run.stats.time += dt;
  if (run.dying) {
    run.dying -= dt;
    updateParticles(dt);
    if (run.dying <= 0) { run.dying = 0; onDeath(); }
    return;
  }
  if (run.won) {
    run.won -= dt;
    updateParticles(dt);
    if (Math.random() < 0.3) burst(rnd(20, RW - 20), rnd(20, RH - 20), 10, pick(['#f2c94c', '#ff6a8a', '#79d0e0', '#fff']), 80, 0.8, 1, 20);
    if (run.won <= 0) { run.won = 0; onWin(); }
    return;
  }
  FX.slowT = Math.max(0, FX.slowT - dt);
  if (run.floorBanner > 0) run.floorBanner -= dt;
  if (run.banner) { run.banner.t -= dt; if (run.banner.t <= 0) run.banner = null; }

  // --- movimento
  let mx = input.mx, my = input.my;
  const ml = Math.hypot(mx, my);
  if (ml > 1) { mx /= ml; my /= ml; }
  const speed = 112 * P.st.speed;
  const acc = Math.min(1, dt * 14);
  P.vx += (mx * speed - P.vx) * acc;
  P.vy += (my * speed - P.vy) * acc;
  // aiuto per infilare le porte
  const room = run.room;
  if (my < -0.5 && P.y < 20 && doorOpen(room, 'up') && Math.abs(P.x - 156) < 16) P.vx += (156 - P.x) * 8 * dt * 10;
  if (my > 0.5 && P.y > RH - 20 && doorOpen(room, 'down') && Math.abs(P.x - 156) < 16) P.vx += (156 - P.x) * 8 * dt * 10;
  if (mx < -0.5 && P.x < 20 && doorOpen(room, 'left') && Math.abs(P.y - 84) < 16) P.vy += (84 - P.y) * 8 * dt * 10;
  if (mx > 0.5 && P.x > RW - 20 && doorOpen(room, 'right') && Math.abs(P.y - 84) < 16) P.vy += (84 - P.y) * 8 * dt * 10;
  moveBox(P, P.vx * dt, P.vy * dt, 'player');
  P.moving = ml > 0.15;
  if (P.moving) {
    P.walkT += dt * (0.6 + Math.hypot(P.vx, P.vy) / 120);
    P.face = Math.abs(mx) > Math.abs(my) ? (mx > 0 ? 'right' : 'left') : (my > 0 ? 'down' : 'up');
  }
  if (P.inv > 0) P.inv -= dt;
  if (P.godT > 0) P.godT -= dt;
  if (P.holdT > 0) P.holdT -= dt;
  if (P.shootT > 0) P.shootT -= dt;

  // spuntoni
  const pt = tileAt(Math.floor(P.x / TILE), Math.floor(P.y / TILE));
  if (pt === T_SPIKES) hurtPlayer(1, 'GLI SPUNTONI');
  // braciere acceso a contatto
  for (const [ox, oy] of [[0, 0], [P.hw, 0], [-P.hw, 0], [0, P.hh + 2], [0, -P.hh - 2]]) {
    if (tileAt(Math.floor((P.x + ox) / TILE), Math.floor((P.y + oy) / TILE)) === T_FIRE) { hurtPlayer(1, 'IL FUOCO'); break; }
  }

  // --- sparo
  P.fireCd -= dt;
  let sdir = null;
  if (input.sx || input.sy) {
    const l = Math.hypot(input.sx, input.sy);
    sdir = [input.sx / l, input.sy / l];
    P.shootDir = Math.abs(sdir[0]) > Math.abs(sdir[1]) ? (sdir[0] > 0 ? 'right' : 'left') : (sdir[1] > 0 ? 'down' : 'up');
    P.shootT = Math.max(P.shootT, 0.05);
    if (P.fireCd <= 0) { fireTears(sdir); P.fireCd = 1 / P.st.rate; }
  }
  if (input.bomb) placeBomb(false, false);
  if (input.active) useActive();

  updateFamiliars(dt, sdir);
  updateTears(dt);
  updateEnemies(dt);
  updateEShots(dt);
  updateHazards(dt);
  updateBombs(dt);
  updatePickups(dt);
  updatePedestals(dt);
  updateParticles(dt);
  tryUnlockDoors();

  // stanza completata
  if (!room.cleared && !run.enemies.length) {
    room.cleared = true;
    Sound.play('door');
    if (room.type === 'normal') {
      if (Math.random() < Math.min(0.85, 0.42 + P.st.luck * 0.04)) { const [x, y] = freeSpotNear(156, 84); spawnPickup(randomPickupType(P.st.luck), x, y); }
      Sound.play('clear');
    }
    if (P.active && P.active.charge < P.active.max) { P.active.charge++; if (P.active.charge === P.active.max) Sound.play('power'); }
  }

  // botola
  if (room.trapdoor && room.cleared && Math.hypot(P.x - room.trapdoor.x, P.y - room.trapdoor.y) < 9 && P.holdT <= 0) {
    run.fall = { t: 0, done: false };
    P.x = room.trapdoor.x; P.y = room.trapdoor.y;
    Sound.play('fall');
    return;
  }

  // porte
  const dir = checkDoors();
  if (dir) {
    const next = otherSide(room.doors[dir], room);
    startTransition(dir, () => enterRoom(next, OPP[dir]));
  }
}

function updateParticles(dt) {
  const arr = run.parts;
  for (let i = arr.length - 1; i >= 0; i--) {
    const p = arr[i];
    p.life -= dt;
    if (p.life <= 0) { arr.splice(i, 1); continue; }
    p.x += p.vx * dt; p.y += p.vy * dt;
    if (p.grav) { p.vz -= p.grav * dt; p.z += p.vz * dt; if (p.z < 0) { p.z = 0; p.vz = 0; p.vx *= 0.5; p.vy *= 0.5; } }
    else { p.vx *= 0.96; p.vy *= 0.96; }
  }
}
