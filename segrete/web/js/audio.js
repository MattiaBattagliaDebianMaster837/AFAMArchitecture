'use strict';
// Audio sintetizzato con WebAudio: effetti e musica chiptune procedurale.
const Sound = (() => {
  let ctx = null, master, sfxGain, musGain, noiseBuf;
  let sfxOn = true, musOn = true;
  let music = null; // {timer, step, song, next}
  const last = {};

  function init() {
    if (ctx) { if (ctx.state === 'suspended') ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    ctx = new AC();
    master = ctx.createGain(); master.gain.value = 0.8; master.connect(ctx.destination);
    const comp = ctx.createDynamicsCompressor(); comp.connect(master);
    sfxGain = ctx.createGain(); sfxGain.gain.value = sfxOn ? 0.55 : 0; sfxGain.connect(comp);
    musGain = ctx.createGain(); musGain.gain.value = musOn ? 0.22 : 0; musGain.connect(comp);
    noiseBuf = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
    const d = noiseBuf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
  }
  function setSfx(on) { sfxOn = on; if (sfxGain) sfxGain.gain.value = on ? 0.55 : 0; }
  function setMusic(on) { musOn = on; if (musGain) musGain.gain.value = on ? 0.22 : 0; }
  function suspend() { if (ctx && ctx.state === 'running') ctx.suspend(); }
  function resume() { if (ctx && ctx.state === 'suspended') ctx.resume(); }

  function tone(type, f0, f1, dur, vol, when = 0, dest = sfxGain) {
    const t = ctx.currentTime + when;
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.type = type; o.frequency.setValueAtTime(f0, t);
    if (f1 !== f0) o.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t + dur);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(vol, t + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(dest);
    o.start(t); o.stop(t + dur + 0.02);
  }
  function noise(dur, vol, fFrom, fTo, when = 0, type = 'lowpass', dest = sfxGain) {
    const t = ctx.currentTime + when;
    const s = ctx.createBufferSource(); s.buffer = noiseBuf;
    s.playbackRate.value = 1;
    const f = ctx.createBiquadFilter(); f.type = type; f.frequency.setValueAtTime(fFrom, t);
    f.frequency.exponentialRampToValueAtTime(Math.max(30, fTo), t + dur);
    const g = ctx.createGain();
    g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    s.connect(f); f.connect(g); g.connect(dest);
    s.start(t, Math.random() * 0.5); s.stop(t + dur + 0.02);
  }

  const SFX = {
    shoot() { tone('sine', 520 + Math.random() * 60, 260, 0.09, 0.25); noise(0.04, 0.08, 3000, 800); },
    splash() { noise(0.08, 0.12, 1800, 300); },
    hit() { tone('square', 240, 120, 0.06, 0.12); },
    kill() { noise(0.22, 0.3, 1200, 120); tone('sawtooth', 200, 50, 0.2, 0.12); },
    hurt() { tone('sawtooth', 320, 70, 0.3, 0.3); noise(0.2, 0.25, 900, 150); },
    coin() { tone('square', 988, 988, 0.07, 0.12); tone('square', 1319, 1319, 0.16, 0.12, 0.06); },
    heart() { tone('sine', 523, 523, 0.1, 0.2); tone('sine', 784, 784, 0.18, 0.2, 0.08); },
    key() { tone('triangle', 1500, 1500, 0.06, 0.2); tone('triangle', 2000, 2000, 0.12, 0.15, 0.05); },
    bombPick() { tone('square', 200, 300, 0.1, 0.15); },
    place() { tone('square', 140, 90, 0.08, 0.2); },
    fuse() { noise(0.05, 0.05, 6000, 3000, 0, 'highpass'); },
    boom() { noise(0.7, 0.8, 1400, 60); tone('sine', 90, 30, 0.6, 0.6); },
    door() { tone('square', 110, 70, 0.18, 0.15); noise(0.15, 0.15, 500, 100); },
    unlock() { tone('triangle', 900, 1200, 0.08, 0.2); tone('triangle', 1200, 1600, 0.12, 0.2, 0.08); },
    item() { [523, 659, 784, 1047].forEach((f, i) => tone('square', f, f, 0.14, 0.12, i * 0.09)); tone('triangle', 1047, 1047, 0.5, 0.15, 0.36); },
    power() { [392, 523, 659].forEach((f, i) => tone('triangle', f, f * 1.5, 0.2, 0.2, i * 0.06)); },
    roar() { tone('sawtooth', 110, 55, 0.9, 0.35); tone('sawtooth', 116, 50, 0.9, 0.25); noise(0.8, 0.2, 600, 80); },
    clear() { tone('triangle', 660, 660, 0.1, 0.15); tone('triangle', 880, 880, 0.2, 0.15, 0.1); },
    eshot() { tone('square', 300, 200, 0.08, 0.08); },
    slam() { noise(0.4, 0.6, 800, 50); tone('sine', 70, 35, 0.4, 0.5); },
    jump() { tone('sine', 200, 500, 0.15, 0.15); },
    select() { tone('square', 660, 660, 0.05, 0.12); },
    back() { tone('square', 440, 330, 0.07, 0.12); },
    buy() { tone('square', 880, 880, 0.06, 0.12); tone('square', 1175, 1175, 0.06, 0.12, 0.06); tone('square', 1568, 1568, 0.12, 0.12, 0.12); },
    deny() { tone('square', 180, 160, 0.15, 0.15); },
    fall() { tone('sine', 600, 80, 0.8, 0.25); },
    chest() { tone('triangle', 400, 600, 0.12, 0.2); noise(0.1, 0.1, 2000, 500); },
    death() { [392, 330, 262, 196].forEach((f, i) => tone('triangle', f, f, 0.35, 0.22, i * 0.25)); },
    win() { [523, 659, 784, 1047, 784, 1047, 1319].forEach((f, i) => tone('square', f, f, 0.2, 0.12, i * 0.13)); },
    tick() { tone('sine', 1200, 1200, 0.03, 0.08); },
  };

  function play(name, minGap = 0.03) {
    if (!ctx || !sfxOn) return;
    const now = ctx.currentTime;
    if (last[name] && now - last[name] < minGap) return;
    last[name] = now;
    try { SFX[name] && SFX[name](); } catch (e) { /* ignora */ }
  }

  // ------------------------------------------------ musica
  const N = (n) => 440 * Math.pow(2, (n - 69) / 12);
  // progressioni (note MIDI radice) e scale per ogni piano
  const SONGS = {
    menu: { bpm: 84, roots: [57, 53, 55, 52], arp: [0, 3, 7, 12, 7, 3], lead: [69, -1, 72, -1, 71, -1, 67, -1, 69, -1, -1, -1, 64, -1, -1, -1], wave: 'triangle' },
    f0: { bpm: 96, roots: [45, 41, 43, 40], arp: [0, 7, 12, 7], lead: [-1, -1, 69, -1, 72, -1, 71, 69, -1, -1, 67, -1, 64, -1, -1, -1], wave: 'square' },
    f1: { bpm: 92, roots: [43, 39, 41, 38], arp: [0, 3, 7, 10], lead: [67, -1, -1, 70, -1, 72, -1, -1, 74, -1, 72, -1, 70, -1, -1, -1], wave: 'triangle' },
    f2: { bpm: 100, roots: [40, 43, 45, 38], arp: [0, 7, 3, 12], lead: [-1, 64, -1, 67, -1, 69, -1, 71, -1, 72, -1, 71, 69, -1, -1, -1], wave: 'square' },
    f3: { bpm: 88, roots: [42, 38, 40, 37], arp: [0, 3, 6, 12], lead: [66, -1, -1, -1, 69, -1, 68, -1, 66, -1, -1, -1, 61, -1, 62, -1], wave: 'triangle' },
    f4: { bpm: 104, roots: [38, 34, 36, 33], arp: [0, 3, 7, 12, 15, 12], lead: [62, -1, 65, -1, 69, -1, 68, -1, 65, -1, 62, -1, 61, -1, -1, -1], wave: 'square' },
    boss: { bpm: 138, roots: [40, 40, 41, 39], arp: [0, 12, 7, 12], lead: [64, 64, -1, 67, -1, 64, 70, -1, 69, -1, 67, -1, 65, -1, 64, -1], wave: 'sawtooth', drums: true },
    final: { bpm: 146, roots: [38, 38, 39, 37], arp: [0, 12, 6, 12, 7, 12], lead: [62, -1, 65, 62, 68, -1, 67, -1, 65, 62, -1, 61, 62, -1, -1, -1], wave: 'sawtooth', drums: true },
  };

  function playMusic(name) {
    if (!ctx) return;
    if (music && music.name === name) return;
    stopMusic();
    const song = SONGS[name]; if (!song) return;
    music = { name, song, step: 0, next: ctx.currentTime + 0.1 };
    music.timer = setInterval(schedule, 30);
  }
  function stopMusic() {
    if (music) { clearInterval(music.timer); music = null; }
  }
  function note(type, freq, t, dur, vol) {
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.type = type; o.frequency.setValueAtTime(freq, t);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(vol, t + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(musGain);
    o.start(t); o.stop(t + dur + 0.05);
  }
  function drum(t, kind) {
    const s = ctx.createBufferSource(); s.buffer = noiseBuf;
    const f = ctx.createBiquadFilter(), g = ctx.createGain();
    if (kind === 'k') { const o = ctx.createOscillator(), og = ctx.createGain(); o.frequency.setValueAtTime(120, t); o.frequency.exponentialRampToValueAtTime(40, t + 0.15); og.gain.setValueAtTime(0.5, t); og.gain.exponentialRampToValueAtTime(0.001, t + 0.18); o.connect(og); og.connect(musGain); o.start(t); o.stop(t + 0.2); return; }
    f.type = kind === 'h' ? 'highpass' : 'bandpass'; f.frequency.value = kind === 'h' ? 7000 : 1800;
    g.gain.setValueAtTime(kind === 'h' ? 0.08 : 0.25, t); g.gain.exponentialRampToValueAtTime(0.001, t + (kind === 'h' ? 0.04 : 0.12));
    s.connect(f); f.connect(g); g.connect(musGain); s.start(t, Math.random() * 0.5); s.stop(t + 0.15);
  }
  function schedule() {
    if (!music || !ctx || ctx.state !== 'running') return;
    const { song } = music;
    const stepDur = 60 / song.bpm / 4;
    while (music.next < ctx.currentTime + 0.15) {
      const st = music.step, t = music.next;
      const bar = Math.floor(st / 16) % song.roots.length, s16 = st % 16;
      const root = song.roots[bar];
      if (s16 % 4 === 0) note('triangle', N(root), t, stepDur * 3.5, 0.35);
      if (s16 % 4 === 2) note('triangle', N(root + 12), t, stepDur * 1.5, 0.18);
      if (s16 % 2 === 0) { const a = song.arp[(st / 2) % song.arp.length | 0]; note(song.wave === 'sawtooth' ? 'square' : song.wave, N(root + 24 + a), t, stepDur * 1.6, 0.05); }
      const ln = song.lead[s16];
      if (ln > 0 && (Math.floor(st / 64) % 2 === 1 || song.drums)) note(song.wave, N(ln + (bar === 3 ? -2 : 0)), t, stepDur * 3, song.wave === 'sawtooth' ? 0.045 : 0.07);
      if (song.drums) { if (s16 % 8 === 0) drum(t, 'k'); if (s16 % 8 === 4) drum(t, 's'); if (s16 % 2 === 1) drum(t, 'h'); }
      else if (s16 % 4 === 2) drum(t, 'h');
      music.step++; music.next += stepDur;
    }
  }

  return { init, play, playMusic, stopMusic, setSfx, setMusic, suspend, resume, get ready() { return !!ctx; } };
})();
