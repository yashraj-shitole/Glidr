/* The 3D story behind the page (three.js r128, global build).
   One fixed canvas. Each [data-stage] section has a target pose; the scene springs between them as you scroll.
   Phone and PC screens are live canvas textures drawn from the real apps' design tokens. */
(function () {
  'use strict';
  var T = window.THREE, G = window.Glidr;
  var canvas = document.getElementById('stage');
  var root = document.documentElement;
  if (!T || !canvas || !G) { root.classList.add('no-webgl'); return; }

  var renderer;
  try {
    renderer = new T.WebGLRenderer({ canvas: canvas, antialias: true, alpha: true, powerPreference: 'high-performance' });
  } catch (e) { root.classList.add('no-webgl'); return; }
  if (!renderer.getContext()) { root.classList.add('no-webgl'); return; }

  var reduce = G.reduce;
  var DPR = Math.min(window.devicePixelRatio || 1, 2);
  var small = Math.min(innerWidth, innerHeight) < 700;
  renderer.setPixelRatio(DPR);
  renderer.outputEncoding = T.sRGBEncoding;
  renderer.toneMapping = T.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;

  var scene = new T.Scene();
  var camera = new T.PerspectiveCamera(32, 1, 0.1, 100);
  camera.position.set(0, 0, 11);
  var world = new T.Group();           // parallax root
  scene.add(world);

  /* ---------------------------------------------------------------- helpers */
  var clamp = function (v, a, b) { return Math.max(a, Math.min(b, v)); };
  var sm = function (x) { x = clamp(x, 0, 1); return x * x * (3 - 2 * x); };
  var lerp = function (a, b, t) { return a + (b - a) * t; };
  var hex = function (c) { return new T.Color(c); };

  function roundedShape(w, h, r) {
    var s = new T.Shape(), x = -w / 2, y = -h / 2;
    s.moveTo(x + r, y); s.lineTo(x + w - r, y); s.absarc(x + w - r, y + r, r, -Math.PI / 2, 0, false);
    s.lineTo(x + w, y + h - r); s.absarc(x + w - r, y + h - r, r, 0, Math.PI / 2, false);
    s.lineTo(x + r, y + h); s.absarc(x + r, y + h - r, r, Math.PI / 2, Math.PI, false);
    s.lineTo(x, y + r); s.absarc(x + r, y + r, r, Math.PI, Math.PI * 1.5, false);
    return s;
  }
  function roundedPlane(w, h, r) {
    var g = new T.ShapeGeometry(roundedShape(w, h, r), 14), p = g.attributes.position, uv = g.attributes.uv;
    for (var i = 0; i < p.count; i++) uv.setXY(i, (p.getX(i) + w / 2) / w, (p.getY(i) + h / 2) / h);
    return g;
  }
  function roundedSlab(w, h, r, depth, bevel) {
    var g = new T.ExtrudeGeometry(roundedShape(w, h, r), { depth: depth, bevelEnabled: true, bevelThickness: bevel, bevelSize: bevel, bevelSegments: 5, curveSegments: 14 });
    g.translate(0, 0, -depth / 2);
    return g;
  }
  function canvasTex(w, h) {
    var c = document.createElement('canvas'); c.width = w; c.height = h;
    var t = new T.CanvasTexture(c);
    t.encoding = T.sRGBEncoding; t.generateMipmaps = false; t.minFilter = T.LinearFilter; t.magFilter = T.LinearFilter;
    t.anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy());
    return { c: c, ctx: c.getContext('2d'), tex: t };
  }
  function glowTexture() {
    var c = document.createElement('canvas'); c.width = c.height = 128; var x = c.getContext('2d');
    var g = x.createRadialGradient(64, 64, 0, 64, 64, 64);
    g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(0.25, 'rgba(255,255,255,.35)'); g.addColorStop(1, 'rgba(255,255,255,0)');
    x.fillStyle = g; x.fillRect(0, 0, 128, 128);
    return new T.CanvasTexture(c);
  }

  /* fx materials follow the accent and the theme (additive glow on Midnight, plain ink-like on Paper) */
  var fx = [];
  function fxReg(m, kind) { fx.push({ m: m, kind: kind || 'ac', add: m.blending === T.AdditiveBlending, op: m.opacity }); return m; }
  var acHex = '#d4ff3f', acCol = hex(acHex), deepCol = hex('#4a6000'), light = false;
  var DEEP = { '#d4ff3f': '#4a6000', '#ff8a5b': '#a23a0c', '#7db2ff': '#1d4fb0', '#ff7fb0': '#a8265b' };

  /* ---------------------------------------------------------------- environment (soft boxes for reflections) */
  (function () {
    var env = new T.Scene();
    env.add(new T.Mesh(new T.BoxGeometry(30, 30, 30), new T.MeshBasicMaterial({ color: 0x08090a, side: T.BackSide })));
    var panel = function (w, h, x, y, z, c, k) {
      var m = new T.Mesh(new T.PlaneGeometry(w, h), new T.MeshBasicMaterial({ color: new T.Color(c).multiplyScalar(k), side: T.DoubleSide }));
      m.position.set(x, y, z); m.lookAt(0, 0, 0); env.add(m);
    };
    panel(14, 5, 0, 12, 4, 0xffffff, 5);
    panel(3, 14, -12, 1, 3, 0xffffff, 3);
    panel(3, 12, 12, 0, -2, 0xd4ff3f, 3.5);
    panel(10, 2, 0, -11, 5, 0xffffff, 1.4);
    var pm = new T.PMREMGenerator(renderer);
    scene.environment = pm.fromScene(env, 0.03).texture;
    pm.dispose();
  })();
  var key = new T.DirectionalLight(0xffffff, 0.9); key.position.set(3, 5, 6); scene.add(key);
  var rim = new T.PointLight(0xd4ff3f, 14, 16, 2); rim.position.set(-4, 2, -3); scene.add(rim);

  /* ---------------------------------------------------------------- palette + 2D drawing (phone UI and PC desktop) */
  function pal() {
    return light
      ? { bg: '#f1f2f4', s1: '#ffffff', s2: '#e7e8eb', s3: '#d8dadf', tx: '#141517', dim: '#565963', line: 'rgba(0,0,0,.09)', dot: 'rgba(0,0,0,.10)' }
      : { bg: '#000000', s1: '#0c0d0f', s2: '#16171a', s3: '#222328', tx: '#f3f2ee', dim: '#9a9ca6', line: 'rgba(255,255,255,.08)', dot: 'rgba(255,255,255,.075)' };
  }
  var SANS = 'Geist, system-ui, "Segoe UI", sans-serif', MONO = '"Geist Mono", ui-monospace, Menlo, monospace';
  var font = function (w, s, mono) { return w + ' ' + s + 'px ' + (mono ? MONO : SANS); };
  function rr(c, x, y, w, h, r) {
    r = Math.min(r, w / 2, h / 2);
    c.beginPath(); c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r); c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath();
  }
  function rgba(c, a) { var k = hex(c); return 'rgba(' + Math.round(k.r * 255) + ',' + Math.round(k.g * 255) + ',' + Math.round(k.b * 255) + ',' + a + ')'; }
  function text(c, s, x, y, f, col, align, ls) {
    c.font = f; c.fillStyle = col; c.textAlign = align || 'left'; c.textBaseline = 'middle';
    if (ls !== undefined && 'letterSpacing' in c) c.letterSpacing = ls + 'px';
    c.fillText(s, x, y);
    if ('letterSpacing' in c) c.letterSpacing = '0px';
  }
  function cursorArrow(c, x, y, s) {
    c.save(); c.translate(x, y); c.scale(s, s);
    c.beginPath(); c.moveTo(0, 0); c.lineTo(0, 19); c.lineTo(5, 14.5); c.lineTo(8.5, 22); c.lineTo(12, 20.5); c.lineTo(8.6, 13); c.lineTo(15, 12.4); c.closePath();
    c.fillStyle = '#fff'; c.fill(); c.lineWidth = 1.3; c.strokeStyle = '#0b0c0a'; c.stroke(); c.restore();
  }
  /* the finger on the pad, and the PC pointer it drives, are the same path */
  function finger(t) { return { u: 0.5 + 0.3 * Math.sin(t * 1.4), v: 0.5 + 0.32 * Math.sin(t * 1.9 + 0.6) }; }

  function drawDesk(c, x, y, w, h, t, f, radius) {
    c.save(); c.translate(x, y); if (radius) { rr(c, 0, 0, w, h, radius); c.clip(); }
    c.scale(w / 960, h / 540);
    var g = c.createRadialGradient(180, 54, 0, 180, 54, 780); g.addColorStop(0, '#1b2740'); g.addColorStop(0.55, '#0d1220'); g.addColorStop(1, '#07080d');
    c.fillStyle = g; c.fillRect(0, 0, 960, 540);
    g = c.createRadialGradient(805, 470, 0, 805, 470, 380); g.addColorStop(0, rgba(acHex, 0.2)); g.addColorStop(1, rgba(acHex, 0));
    c.fillStyle = g; c.fillRect(0, 0, 960, 540);
    // dashboard window
    c.fillStyle = '#12141a'; rr(c, 56, 36, 640, 384, 14); c.fill(); c.strokeStyle = 'rgba(255,255,255,.08)'; c.lineWidth = 1.5; c.stroke();
    c.fillStyle = '#1b1d24'; rr(c, 56, 36, 640, 38, 14); c.fill(); c.fillRect(56, 56, 640, 18);
    c.fillStyle = acHex; rr(c, 72, 48, 14, 14, 4); c.fill();
    text(c, 'Launch day · Dashboard', 96, 55, font(600, 15), '#c9cad1');
    text(c, 'ACTIVE SESSIONS', 84, 112, font(600, 13, 1), '#7d808b', 'left', 2);
    text(c, String(12480 + Math.floor(t * 37) % 900).replace(/\B(?=(\d{3})+(?!\d))/g, ','), 84, 166, font(700, 58), '#f3f2ee', 'left', -2);
    text(c, '▲ 12.4%', 84, 214, font(600, 17, 1), acHex);
    for (var i = 0; i < 15; i++) {
      var bh = 36 + (Math.sin(t * 2.1 + i * 0.7) * 0.5 + 0.5) * 120;
      c.fillStyle = i > 10 ? acHex : '#2a2d37'; rr(c, 84 + i * 37, 392 - bh, 24, bh, 6); c.fill();
    }
    c.fillStyle = '#171920'; rr(c, 480, 92, 190, 124, 12); c.fill();
    c.beginPath(); for (i = 0; i < 24; i++) { var px = 486 + i * 7.4, py = 190 - (Math.sin(t * 1.6 + i * 0.5) * 0.5 + 0.5) * 60 - i * 1.4; i ? c.lineTo(px, py) : c.moveTo(px, py); }
    c.strokeStyle = acHex; c.lineWidth = 3; c.lineJoin = 'round'; c.stroke();
    // notes window
    c.fillStyle = '#1e2027'; rr(c, 560, 200, 340, 232, 14); c.fill(); c.strokeStyle = 'rgba(255,255,255,.1)'; c.stroke();
    c.fillStyle = '#272a33'; rr(c, 560, 200, 340, 36, 14); c.fill(); c.fillRect(560, 222, 340, 14);
    text(c, 'Notes', 580, 218, font(600, 14), '#c9cad1');
    [300, 238, 270, 190].forEach(function (lw, k) { c.fillStyle = k === 3 ? rgba(acHex, 0.8) : '#3a3d47'; rr(c, 584, 262 + k * 30, lw, 10, 5); c.fill(); });
    // taskbar
    c.fillStyle = 'rgba(16,17,22,.94)'; c.fillRect(0, 498, 960, 42); c.fillStyle = 'rgba(255,255,255,.06)'; c.fillRect(0, 498, 960, 1.5);
    ['#3b82f6', '#3a3d47', acHex, '#3a3d47', '#3a3d47'].forEach(function (col, k) { c.fillStyle = col; rr(c, 390 + k * 40, 508, 22, 22, 6); c.fill(); });
    text(c, '9:41', 936, 519, font(500, 15), '#cfd1d8', 'right');
    if (f) cursorArrow(c, (0.1 + 0.8 * f.u) * 960, (0.12 + 0.7 * f.v) * 540, 1.9);
    c.restore();
  }

  function padArea(c, P, x, y, w, h, t, f, hint) {
    c.save(); rr(c, x, y, w, h, 32); c.fillStyle = P.s1; c.fill(); c.clip();
    c.fillStyle = P.dot; for (var gx = x + 12; gx < x + w; gx += 22) for (var gy = y + 12; gy < y + h; gy += 22) c.fillRect(gx, gy, 2, 2);
    var fx_ = x + 24 + (w - 48) * f.u, fy = y + 24 + (h - 48) * f.v;
    for (var i = 7; i >= 1; i--) { var ff = finger(t - i * 0.035); c.fillStyle = rgba(acHex, 0.05 + 0.025 * (8 - i)); c.beginPath(); c.arc(x + 24 + (w - 48) * ff.u, y + 24 + (h - 48) * ff.v, 22 - i * 1.6, 0, 7); c.fill(); }
    var g = c.createRadialGradient(fx_, fy, 0, fx_, fy, 110); g.addColorStop(0, rgba(acHex, 0.34)); g.addColorStop(0.4, rgba(acHex, 0.11)); g.addColorStop(1, rgba(acHex, 0));
    c.fillStyle = g; c.fillRect(x, y, w, h);
    c.strokeStyle = acHex; c.lineWidth = 2; c.beginPath(); c.arc(fx_, fy, 20 + 3 * Math.sin(t * 5), 0, 7); c.stroke();
    // tap ripple
    var rp = (t * 0.7) % 1; c.strokeStyle = rgba(acHex, 0.55 * (1 - rp)); c.beginPath(); c.arc(fx_, fy, 20 + 70 * rp, 0, 7); c.stroke();
    // scroll strip
    c.fillStyle = P.s2; rr(c, x + w - 44, y + h * 0.2, 30, h * 0.52, 15); c.fill();
    c.fillStyle = P.dim; for (var k = 0; k < 15; k++) c.fillRect(x + w - 34, y + h * 0.2 + 14 + k * ((h * 0.52 - 28) / 14), 10, 1.5);
    c.restore();
    c.strokeStyle = P.line; c.lineWidth = 1.5; rr(c, x, y, w, h, 32); c.stroke();
    if (hint) text(c, 'TAP CLICK · 2 FINGERS SCROLL · 3 FINGERS SWITCH', x + w / 2 - 8, y + h - 20, font(500, 9, 1), P.dim, 'center', 0.8);
  }
  function statusBar(c, P) {
    text(c, '9:41', 28, 30, font(700, 15, 1), P.tx);
    c.fillStyle = P.tx; rr(c, 330, 24, 26, 13, 4); c.fill(); c.fillStyle = P.bg; rr(c, 332, 26, 16, 9, 2); c.fill();
    c.fillStyle = '#000'; rr(c, 145, 14, 100, 28, 14); c.fill();
  }
  function topPill(c, P) {
    c.fillStyle = P.s2; rr(c, 16, 54, 196, 44, 22); c.fill(); c.strokeStyle = P.line; c.lineWidth = 1.5; c.stroke();
    c.fillStyle = acHex; c.beginPath(); c.arc(38, 76, 4, 0, 7); c.fill();
    text(c, 'DESKTOP-YASH', 52, 76, font(700, 13), P.tx); text(c, '12 ms', 158, 76, font(500, 11.5, 1), P.dim);
    [306, 352].forEach(function (cx, k) {
      c.fillStyle = P.s2; c.beginPath(); c.arc(cx + 8, 76, 22, 0, 7); c.fill(); c.stroke();
      c.strokeStyle = P.tx; c.lineWidth = 2; c.lineCap = 'round';
      c.beginPath(); if (k) { c.moveTo(cx - 2, 70); c.lineTo(cx + 18, 70); c.moveTo(cx - 2, 82); c.lineTo(cx + 18, 82); } else { c.arc(cx + 8, 78, 7, 0.2, Math.PI * 1.9); }
      c.stroke();
    });
    c.strokeStyle = P.line; c.lineWidth = 1.5;
  }
  function dock(c, P, y) {
    c.fillStyle = P.s2; rr(c, 16, y, 262, 64, 32); c.fill(); c.strokeStyle = P.line; c.lineWidth = 1.5; c.stroke();
    c.fillStyle = acHex; rr(c, 22, y + 6, 124, 52, 26); c.fill();
    text(c, 'Pad', 96, y + 32, font(700, 15), '#0b0c0a', 'center'); text(c, 'Media', 212, y + 32, font(600, 15), P.dim, 'center');
    c.fillStyle = P.tx; c.beginPath(); c.arc(334, y + 32, 32, 0, 7); c.fill();
    c.strokeStyle = P.bg; c.lineWidth = 2.4; rr(c, 320, y + 24, 28, 17, 4); c.stroke();
    c.fillStyle = P.bg; for (var i = 0; i < 4; i++) c.fillRect(325 + i * 6, y + 29, 3, 3); c.fillRect(327, y + 35, 20, 2.5);
  }
  function mouseButtons(c, P, y) {
    [[16, 152, 'LEFT'], [226, 148, 'RIGHT']].forEach(function (b) { c.fillStyle = P.s2; rr(c, b[0], y, b[1], 56, 20); c.fill(); c.strokeStyle = P.line; c.lineWidth = 1.5; c.stroke(); text(c, b[2], b[0] + b[1] / 2, y + 28, font(700, 13), P.tx, 'center', 0.6); });
    c.fillStyle = P.s2; rr(c, 174, y, 46, 56, 20); c.fill(); c.stroke();
    c.strokeStyle = P.tx; c.lineWidth = 1.8; rr(c, 191, y + 16, 12, 24, 6); c.stroke(); c.fillStyle = P.tx; c.fillRect(196.2, y + 21, 1.8, 5);
  }
  function drawPad(c, P, t) {
    statusBar(c, P); topPill(c, P);
    padArea(c, P, 16, 110, 358, 408, t, finger(t), true);
    text(c, '1.6×', 52, 134, font(600, 11, 1), P.dim, 'center');
    mouseButtons(c, P, 534);
    var tiles = [['Copy', 'Ctrl', 'C'], ['Paste', 'Ctrl', 'V'], ['Switch', 'Alt', 'Tab'], ['Desktop', 'Win', 'D']];
    var press = Math.floor(t * 1.2) % 4, k = (t * 1.2) % 1 < 0.35;
    tiles.forEach(function (tl, i) {
      var x = 16 + i * 91, on = k && i === press;
      c.fillStyle = on ? acHex : P.s2; rr(c, x, 612, 83, 92, 18); c.fill(); c.strokeStyle = P.line; c.lineWidth = 1.5; c.stroke();
      text(c, tl[0], x + 12, 634, font(700, 12.5), on ? '#0b0c0a' : P.tx);
      [tl[1], tl[2]].forEach(function (kk, j) { var kx = x + 12 + (j ? (tl[1].length > 3 ? 40 : 34) : 0), kw = 11 + kk.length * 6.5; c.fillStyle = on ? 'rgba(0,0,0,.16)' : P.s3; rr(c, kx, 668, kw, 22, 7); c.fill(); text(c, kk, kx + kw / 2, 679, font(600, 10.5, 1), on ? '#0b0c0a' : P.tx, 'center'); });
    });
    dock(c, P, 748);
  }
  function drawViewer(c, P, t) {
    statusBar(c, P);
    var f = finger(t);
    c.save(); rr(c, 12, 54, 366, 206, 18); c.clip(); drawDesk(c, 12, 54, 366, 206, t, f, 0); c.restore();
    c.strokeStyle = P.line; c.lineWidth = 1.5; rr(c, 12, 54, 366, 206, 18); c.stroke();
    [0, 1].forEach(function (i) { c.fillStyle = i ? 'rgba(20,21,24,.7)' : acHex; rr(c, 24 + i * 38, 66, 32, 26, 13); c.fill(); text(c, String(i + 1), 40 + i * 38, 79, font(700, 12, 1), i ? '#f3f2ee' : '#0b0c0a', 'center'); });
    text(c, '24 fps · 1080p · 14 ms · direct', 195, 282, font(500, 10.5, 1), P.dim, 'center', 0.3);
    padArea(c, P, 16, 304, 358, 312, t, f, true);
    mouseButtons(c, P, 634);
    dock(c, P, 718);
  }
  function drawDisplays(c, P, t) {
    c.fillStyle = 'rgba(0,0,0,.6)'; c.fillRect(0, 0, 390, 844);
    c.fillStyle = P.s1; c.beginPath(); c.moveTo(0, 844); c.lineTo(0, 352); c.arcTo(0, 332, 28, 332, 28); c.lineTo(362, 332); c.arcTo(390, 332, 390, 360, 28); c.lineTo(390, 844); c.fill();
    c.fillStyle = P.s3; rr(c, 163, 342, 64, 4, 2); c.fill();
    text(c, 'Displays', 28, 386, font(700, 24), P.tx, 'left', -0.6);
    text(c, 'Tap one to view and control it', 28, 414, font(500, 13.5), P.dim);
    var act = Math.floor(t / 2.4) % 2;
    var mons = [[28, 448, 214, 134], [256, 476, 106, 80]];
    mons.forEach(function (m, i) {
      c.save(); rr(c, m[0], m[1], m[2], m[3], 12); c.clip(); drawDesk(c, m[0], m[1], m[2], m[3], t + i * 2, i === act ? finger(t) : null, 0); c.restore();
      c.lineWidth = i === act ? 3 : 1.5; c.strokeStyle = i === act ? acHex : P.line; rr(c, m[0], m[1], m[2], m[3], 12); c.stroke();
      c.fillStyle = i === act ? acHex : P.s2; rr(c, m[0] + 8, m[1] + 8, 26, 22, 11); c.fill(); text(c, String(i + 1), m[0] + 21, m[1] + 19, font(700, 12, 1), i === act ? '#0b0c0a' : P.tx, 'center');
    });
    text(c, act ? 'DISPLAY 2 · 1280 × 720' : 'DISPLAY 1 · 1920 × 1080', 28, 612, font(600, 11, 1), P.dim, 'left', 1.2);
    c.fillStyle = acHex; rr(c, 28, 650, 334, 52, 26); c.fill(); text(c, 'Next display', 195, 676, font(700, 15), '#0b0c0a', 'center');
    c.fillStyle = P.s2; rr(c, 28, 714, 334, 52, 26); c.fill(); c.strokeStyle = P.line; c.lineWidth = 1.5; c.stroke(); text(c, 'Close', 195, 740, font(600, 15), P.tx, 'center');
  }
  function drawPhone(o, mode, t) {
    var c = o.ctx, P = pal(); c.save(); c.setTransform(o.c.width / 390, 0, 0, o.c.height / 844, 0, 0);
    c.fillStyle = P.bg; c.fillRect(0, 0, 390, 844);
    drawPad(c, P, t);
    if (mode > 0.001) { c.globalAlpha = clamp(mode, 0, 1); c.fillStyle = P.bg; c.fillRect(0, 0, 390, 844); drawViewer(c, P, t); }
    if (mode > 1.001) { c.globalAlpha = clamp(mode - 1, 0, 1); drawDisplays(c, P, t); }
    c.globalAlpha = 1;
    c.restore();
    o.tex.needsUpdate = true;
  }

  /* ---------------------------------------------------------------- phone */
  var phone = new T.Group(), phoneTex = canvasTex(560, 1212);
  (function () {
    var W = 1.58, H = 3.4, D = 0.13, B = 0.035;
    var bodyMat = new T.MeshStandardMaterial({ color: 0x15161a, metalness: 0.92, roughness: 0.26, envMapIntensity: 1.5 });
    phone.add(new T.Mesh(roundedSlab(W, H, 0.27, D, B), bodyMat));
    var scr = new T.Mesh(roundedPlane(W - 0.1, H - 0.1, 0.22), new T.MeshBasicMaterial({ map: phoneTex.tex, toneMapped: false }));
    scr.position.z = D / 2 + B + 0.002; phone.add(scr);
    var gc = document.createElement('canvas'); gc.width = 256; gc.height = 4; var gx = gc.getContext('2d');
    var gg = gx.createLinearGradient(0, 0, 256, 0); gg.addColorStop(0, 'rgba(255,255,255,0)'); gg.addColorStop(0.42, 'rgba(255,255,255,0)'); gg.addColorStop(0.5, 'rgba(255,255,255,.55)'); gg.addColorStop(0.58, 'rgba(255,255,255,0)'); gg.addColorStop(1, 'rgba(255,255,255,0)');
    gx.fillStyle = gg; gx.fillRect(0, 0, 256, 4);
    var gt = new T.CanvasTexture(gc); gt.wrapS = T.RepeatWrapping; gt.repeat.set(0.7, 1);
    phone.userData.glare = gt;
    var glare = new T.Mesh(roundedPlane(W - 0.1, H - 0.1, 0.22), new T.MeshBasicMaterial({ map: gt, transparent: true, opacity: 0.11, blending: T.AdditiveBlending, depthWrite: false, toneMapped: false }));
    glare.position.z = D / 2 + B + 0.004; phone.add(glare);
    [[0.6, 0.45], [0.05, 0.26], [-0.35, 0.26]].forEach(function (b, i) {
      var m = new T.Mesh(new T.BoxGeometry(0.035, i ? 0.26 : 0.4, 0.07), bodyMat); m.position.set(W / 2 + B + 0.005, b[0], 0); phone.add(m);
    });
  })();
  world.add(phone);

  /* ---------------------------------------------------------------- monitor */
  var mon = new T.Group(), monTex = canvasTex(1024, 576), monMats = [];
  (function () {
    var W = 4.5, H = 2.56;
    var metal = new T.MeshStandardMaterial({ color: 0x17181c, metalness: 0.9, roughness: 0.3, envMapIntensity: 1.4, transparent: true });
    var screen = new T.MeshBasicMaterial({ map: monTex.tex, toneMapped: false, transparent: true });
    monMats.push(metal, screen);
    mon.add(new T.Mesh(roundedSlab(W + 0.1, H + 0.1, 0.1, 0.08, 0.02), metal));
    var s = new T.Mesh(roundedPlane(W, H, 0.05), screen); s.position.z = 0.062; mon.add(s);
    var neck = new T.Mesh(new T.BoxGeometry(0.26, 0.7, 0.07), metal); neck.position.set(0, -H / 2 - 0.32, -0.08); mon.add(neck);
    var base = new T.Mesh(new T.CylinderGeometry(0.62, 0.7, 0.05, 56), metal); base.position.set(0, -H / 2 - 0.68, 0.0); mon.add(base);
  })();
  world.add(mon);

  /* ---------------------------------------------------------------- cursor (the Glidr mark, extruded) */
  var cursor = new T.Group(), cursorMat;
  (function () {
    var pts = [[34, 34], [74, 47], [53, 54], [46, 75]].map(function (p) { return new T.Vector2((p[0] - 52) / 22, -(p[1] - 54) / 22); });
    var sh = new T.Shape(pts);
    var geo = new T.ExtrudeGeometry(sh, { depth: 0.18, bevelEnabled: true, bevelThickness: 0.14, bevelSize: 0.17, bevelSegments: 8, curveSegments: 10 });
    geo.translate(0, 0, -0.09);
    cursorMat = new T.MeshStandardMaterial({ color: 0xd4ff3f, emissive: 0xd4ff3f, emissiveIntensity: 0.22, metalness: 0.1, roughness: 0.3, envMapIntensity: 0.55, toneMapped: false });
    cursor.add(new T.Mesh(geo, cursorMat));
    var tr = new T.MeshBasicMaterial({ color: 0xd4ff3f, transparent: true, opacity: 0.55, depthWrite: false });
    var tr2 = new T.MeshBasicMaterial({ color: 0xd4ff3f, transparent: true, opacity: 0.26, depthWrite: false });
    fxReg(tr); fxReg(tr2);
    var bar = function (l, rad, mat, x, y) { var m = new T.Mesh(roundedSlab(rad * 2, l + rad * 2, rad, 0.04, 0.01), mat); m.rotation.z = Math.PI / 4; m.position.set(x, y, -0.05); cursor.add(m); };
    bar(0.72, 0.1, tr, 1.28, -1.55); bar(0.34, 0.085, tr2, 1.9, -0.95);
  })();
  var cursorGlow = new T.Sprite(fxReg(new T.SpriteMaterial({ map: glowTexture(), color: 0xd4ff3f, transparent: true, opacity: 0.5, blending: T.AdditiveBlending, depthWrite: false })));
  cursorGlow.scale.set(5, 5, 1); cursor.add(cursorGlow);
  world.add(cursor);

  /* ---------------------------------------------------------------- particles (dust + data stream) */
  var particleVS = 'attribute float aAlpha; attribute float aSize; uniform float uPx; varying float vA;' +
    'void main(){ vA=aAlpha; vec4 mv=modelViewMatrix*vec4(position,1.); gl_PointSize=aSize*uPx*(9.0/-mv.z); gl_Position=projectionMatrix*mv; }';
  var particleFS = 'uniform vec3 uColor; uniform float uOpacity; varying float vA;' +
    'void main(){ vec2 c=gl_PointCoord-.5; float d=length(c); if(d>.5) discard; gl_FragColor=vec4(uColor, vA*uOpacity*smoothstep(.5,.05,d)); }';
  function makePoints(n, add) {
    var g = new T.BufferGeometry(), pos = new Float32Array(n * 3), al = new Float32Array(n), sz = new Float32Array(n);
    g.setAttribute('position', new T.BufferAttribute(pos, 3)); g.setAttribute('aAlpha', new T.BufferAttribute(al, 1)); g.setAttribute('aSize', new T.BufferAttribute(sz, 1));
    var m = new T.ShaderMaterial({ vertexShader: particleVS, fragmentShader: particleFS, transparent: true, depthWrite: false, blending: add ? T.AdditiveBlending : T.NormalBlending, uniforms: { uPx: { value: DPR * 2 }, uColor: { value: new T.Color(acHex) }, uOpacity: { value: 1 } } });
    m.opacity = 1; fx.push({ m: m, kind: 'ac', add: true, shader: true, op: 1 });
    var p = new T.Points(g, m); p.frustumCulled = false; return { p: p, pos: pos, al: al, sz: sz, g: g, m: m };
  }
  var dust = makePoints(small ? 120 : 260, true), dustSeed = [];
  for (var i = 0; i < dust.al.length; i++) { dustSeed.push([Math.random() * 18 - 9, Math.random() * 11 - 5.5, -Math.random() * 7 + 1, Math.random(), Math.random()]); dust.sz[i] = 0.8 + Math.random() * 1.6; }
  world.add(dust.p);
  var NS = small ? 60 : 110, stream = makePoints(NS, true), streamSeed = [];
  for (i = 0; i < NS; i++) { streamSeed.push([Math.random(), 0.12 + Math.random() * 0.2, (Math.random() - 0.5) * 0.18, (Math.random() - 0.5) * 0.18]); }
  world.add(stream.p);
  var curveGeo = new T.BufferGeometry(); curveGeo.setAttribute('position', new T.BufferAttribute(new Float32Array(61 * 3), 3));
  var curveMat = fxReg(new T.LineBasicMaterial({ color: 0xd4ff3f, transparent: true, opacity: 0.4, blending: T.AdditiveBlending, depthWrite: false }));
  var curveLine = new T.Line(curveGeo, curveMat); curveLine.frustumCulled = false; world.add(curveLine);

  /* ---------------------------------------------------------------- bluetooth rings */
  var rings = [];
  for (i = 0; i < 3; i++) {
    var rm = fxReg(new T.MeshBasicMaterial({ color: 0xd4ff3f, transparent: true, opacity: 0.5, blending: T.AdditiveBlending, depthWrite: false, side: T.DoubleSide }));
    var ring = new T.Mesh(new T.RingGeometry(0.985, 1, 160), rm); world.add(ring); rings.push(ring);
  }

  /* ---------------------------------------------------------------- globe */
  var globe = new T.Group(), globeMat;
  (function () {
    var N = small ? 1100 : 2200, pos = new Float32Array(N * 3), seed = new Float32Array(N), ga = Math.PI * (3 - Math.sqrt(5));
    for (var k = 0; k < N; k++) {
      var y = 1 - (k / (N - 1)) * 2, r = Math.sqrt(1 - y * y), th = ga * k;
      pos[k * 3] = Math.cos(th) * r; pos[k * 3 + 1] = y; pos[k * 3 + 2] = Math.sin(th) * r; seed[k] = Math.random();
    }
    var g = new T.BufferGeometry(); g.setAttribute('position', new T.BufferAttribute(pos, 3)); g.setAttribute('aSeed', new T.BufferAttribute(seed, 1));
    globeMat = new T.ShaderMaterial({
      transparent: true, depthWrite: false, blending: T.AdditiveBlending,
      uniforms: { uTime: { value: 0 }, uSize: { value: DPR * 2.1 }, uColor: { value: new T.Color(acHex) }, uOpacity: { value: 0 } },
      vertexShader: 'attribute float aSeed; uniform float uTime; uniform float uSize; varying float vA;' +
        'void main(){ vec3 n=normalize(normalMatrix*position); float f=n.z; float a=smoothstep(-.3,.9,f); float rim=pow(1.-abs(f),3.);' +
        'float w=.5+.5*sin(uTime*1.3+position.y*3.2+aSeed*5.0); float hot=step(.9,aSeed);' +
        'vA=(a*.7+rim*.55)*(.5+.5*w*(.4+hot)); vec4 mv=modelViewMatrix*vec4(position,1.); gl_PointSize=uSize*(1.+hot*1.3)*(10./-mv.z)*1.0; gl_Position=projectionMatrix*mv; }',
      fragmentShader: 'uniform vec3 uColor; uniform float uOpacity; varying float vA; void main(){ vec2 c=gl_PointCoord-.5; float d=length(c); if(d>.5) discard; gl_FragColor=vec4(uColor, clamp(vA,0.,1.)*uOpacity*smoothstep(.5,.1,d)); }'
    });
    fx.push({ m: globeMat, kind: 'ac', add: true, shader: true, op: 1 });
    globe.add(new T.Points(g, globeMat));
    var ringMat = fxReg(new T.LineBasicMaterial({ color: 0xd4ff3f, transparent: true, opacity: 0.16, blending: T.AdditiveBlending, depthWrite: false }));
    [0, 0.6, -0.6].forEach(function (lat) {
      var pts = [], rr_ = Math.sqrt(1 - lat * lat);
      for (var q = 0; q <= 120; q++) { var a = q / 120 * Math.PI * 2; pts.push(new T.Vector3(Math.cos(a) * rr_ * 1.004, lat, Math.sin(a) * rr_ * 1.004)); }
      globe.add(new T.LineLoop(new T.BufferGeometry().setFromPoints(pts), ringMat));
    });
    var halo = new T.Sprite(fxReg(new T.SpriteMaterial({ map: glowTexture(), color: 0xd4ff3f, transparent: true, opacity: 0.18, blending: T.AdditiveBlending, depthWrite: false })));
    halo.scale.set(3.6, 3.6, 1); halo.userData.halo = true; globe.userData.halo = halo; globe.add(halo);
  })();
  world.add(globe);

  /* ---------------------------------------------------------------- theme + accent */
  function applyLook() {
    acHex = G.accent(); acCol = hex(acHex); light = G.light(); deepCol = hex(DEEP[acHex.toLowerCase()] || '#4a6000');
    var col = light ? deepCol : acCol;
    fx.forEach(function (f) {
      var add = !light;
      if (f.shader) { f.m.uniforms.uColor.value.copy(col); f.m.blending = add ? T.AdditiveBlending : T.NormalBlending; f.m.needsUpdate = true; }
      else { f.m.color.copy(f.kind === 'ac' ? col : f.m.color); if (f.m.blending !== undefined) { f.m.blending = add ? T.AdditiveBlending : T.NormalBlending; f.m.needsUpdate = true; } }
    });
    cursorMat.color.copy(acCol); cursorMat.emissive.copy(acCol); cursorMat.emissiveIntensity = light ? 0.08 : 0.22;
    rim.color.copy(acCol);
    renderer.toneMappingExposure = light ? 1.0 : 1.05;
    dirty = true;
  }
  var dirty = true;
  G.onChange(applyLook);

  /* ---------------------------------------------------------------- stage poses */
  var D = { px: 0, py: 0, pz: 0, pry: 0, prx: 0, prz: 0, ps: 1, mx: 3, my: 0, mz: -1, mry: -0.3, ms: 1, mo: 0, cx: 0, cy: 0, cs: 1, co: 0, go: 0, gs: 2, gx: 0, gy: 0, ro: 0, rmax: 2.5, so: 0, reach: 1, lift: 1.6, mode: 0, vis: 1, shift: 1 };
  function pose(o) { return Object.assign({}, D, o); }
  var S = [
    /* 0 hero */    pose({ px: 3.6, py: -0.85, pz: 1.1, pry: -0.38, prx: 0.05, prz: 0.05, ps: 0.72, mx: 2.2, my: 0.55, mz: -0.8, mry: -0.3, ms: 0.86, mo: 1, cx: 0.55, cy: -1.95, cs: 0.4, co: 1, so: 1, lift: 0.7 }),
    /* 1 glidr */   pose({ px: -3.1, py: -0.1, pz: 0.6, pry: 0.4, prx: 0.03, prz: -0.04, ps: 1.22, mx: 5, my: 0.5, mz: -3, mry: -0.4, ms: 0.7, mo: 0, cx: -0.9, cy: 2.0, cs: 0.5, co: 0, ro: 1, rmax: 2.6 }),
    /* 2 bridge */  pose({ px: -4.0, py: -1.7, shift: -1, pz: 0, pry: 0.5, ps: 0.68, mx: 4.5, my: -1.5, mz: -1.0, mry: -0.45, ms: 0.55, mo: 0.5, ro: 1, rmax: 3.4, so: 0.8, reach: 0.4, lift: 0.9 }),
    /* 3 screen */  pose({ px: 1.0, py: -0.5, pz: 1.7, pry: 0.3, ps: 0.82, mx: 4.1, my: 0.9, mz: 0.2, mry: -0.45, ms: 0.7, mo: 1, go: 1, gs: 2.05, gx: 2.6, gy: 0, so: 1, lift: 1.3, mode: 1 }),
    /* 4 see */     pose({ px: -3, py: -0.1, pz: 0.6, pry: 0.38, prz: -0.03, ps: 1.28, mx: -1.15, my: 2.15, mz: -3.2, mry: 0.3, ms: 0.5, mo: 0.6, go: 0.4, gs: 2.6, gx: -1.6, gy: -0.2, so: 1, lift: 0.7, mode: 1 }),
    /* 5 control */ pose({ px: 3.1, py: -0.1, pz: 0.6, pry: -0.38, prz: 0.03, ps: 1.28, mx: 1.15, my: 2.15, mz: -3.2, mry: -0.3, ms: 0.5, mo: 0.6, go: 0.35, gs: 2.6, gx: 1.6, gy: -0.2, so: 1, lift: 0.7, mode: 1 }),
    /* 6 monitors */pose({ px: -3, py: -0.1, pz: 0.6, pry: 0.38, prz: -0.03, ps: 1.28, mx: -1.15, my: 2.15, mz: -3.2, mry: 0.3, ms: 0.5, mo: 0.6, go: 0.25, gs: 2.6, gx: -1.6, gy: -0.2, so: 1, lift: 0.7, mode: 2 })
  ];
  var secs = Array.prototype.slice.call(document.querySelectorAll('[data-stage]'));
  var hidePose = pose(S[6]); hidePose.vis = 0;
  while (S.length < secs.length - 1) S.push(Object.assign({}, hidePose));
  S.push(pose({ vis: 0.55, go: 1, gs: 3.7, gx: 0, gy: -0.3, ps: 0.01, mo: 0, so: 0, co: 0, ro: 0, mode: 0, mx: 4, mz: -3 }));

  var keys = Object.keys(D), cur = {}, vel = {}, tgt = {};
  keys.forEach(function (k) { cur[k] = S[0][k]; vel[k] = 0; tgt[k] = S[0][k]; });
  var aspect = 1.6, tops = [], stageF = 0;
  function measure() {
    aspect = innerWidth / innerHeight; camera.aspect = aspect;
    camera.fov = aspect < 0.8 ? 32 + (0.8 - aspect) * 22 : 32;      // wider framing on tall screens
    camera.updateProjectionMatrix();
    renderer.setSize(innerWidth, innerHeight, false);
    tops = secs.map(function (s) { return s.getBoundingClientRect().top + window.scrollY; });
    tops.push(document.querySelector('main').getBoundingClientRect().bottom + window.scrollY);
    dirty = true;
  }
  function stageAt(y) {
    var c = y + innerHeight * 0.5, i = 0;
    for (var k = 0; k < secs.length; k++) if (c >= tops[k]) i = k;
    var span = tops[i + 1] - tops[i];
    return i + clamp((c - tops[i]) / Math.max(span, 1), 0, 0.999);
  }
  function blend(a, b, t) { var o = {}; keys.forEach(function (k) { o[k] = lerp(a[k], b[k], t); }); return o; }
  function targetPose(stage) {
    var i = Math.min(Math.floor(stage), S.length - 1), f = stage - i;
    if (f < 0.5) return blend(S[Math.max(i - 1, 0)], S[i], sm(f / 0.4));
    return blend(S[i], S[Math.min(i + 1, S.length - 1)], sm((f - 0.6) / 0.4));
  }
  function fit(p) {                       // adapt a landscape pose to the current aspect ratio
    var k = clamp(aspect / 1.6, 0.22, 1), q = Object.assign({}, p), fs = 0.62 + 0.38 * k;
    ['px', 'mx', 'cx', 'gx'].forEach(function (a) { q[a] = p[a] * k * (a === 'mx' && k < 0.7 ? 0.8 : 1); });
    ['py', 'my', 'cy', 'gy'].forEach(function (a) { q[a] = p[a] + (1 - k) * 1.5 * p.shift; });
    ['ps', 'ms', 'cs', 'gs'].forEach(function (a) { q[a] = p[a] * fs; });
    if (k < 0.7) q.ms *= 0.8;
    return q;
  }

  /* ---------------------------------------------------------------- frame */
  var fontsReady = false;
  var t0 = performance.now(), last = t0, intro = reduce ? 1 : 0, introV = 0, par = { x: 0, y: 0, vx: 0, vy: 0 };
  var bez = new T.Vector3(), A = new T.Vector3(), B = new T.Vector3(), Cc = new T.Vector3();
  function bezier(u, out) {
    var a = 1 - u; return out.set(a * a * A.x + 2 * a * u * Cc.x + u * u * B.x, a * a * A.y + 2 * a * u * Cc.y + u * u * B.y, a * a * A.z + 2 * a * u * Cc.z + u * u * B.z);
  }
  var acc = 0, texTick = 0;
  function frame(now) {
    var dt = clamp((now - last) / 1000, 0, 0.05); last = now; var t = Math.max(0, (now - t0) / 1000);
    var tp = fit(targetPose(stageF));
    keys.forEach(function (k) { tgt[k] = tp[k]; });
    if (reduce) { keys.forEach(function (k) { cur[k] = tgt[k]; }); intro = 1; }
    else {
      var steps = Math.max(1, Math.ceil(dt / (1 / 90))), h = dt / steps;
      for (var s = 0; s < steps; s++) {
        keys.forEach(function (k) { var a = 90 * (tgt[k] - cur[k]) - 17.5 * vel[k]; vel[k] += a * h; cur[k] += vel[k] * h; });
        var ia = 55 * (1 - intro) - 12 * introV; introV += ia * h; intro += introV * h;
      }
      par.vx += (60 * (G.mouse.x - par.x) - 12 * par.vx) * dt; par.x += par.vx * dt;
      par.vy += (60 * (G.mouse.y - par.y) - 12 * par.vy) * dt; par.y += par.vy * dt;
    }
    var vis = clamp(cur.vis, 0, 1) * clamp(intro, 0, 1);
    canvas.style.opacity = vis.toFixed(3);
    if (vis < 0.01) return;

    var I = clamp(intro, 0, 1.08);
    world.rotation.y = par.x * 0.09; world.rotation.x = par.y * 0.05;
    var bob = reduce ? 0 : Math.sin(t * 0.9);

    // phone
    phone.position.set(cur.px, cur.py + bob * 0.06, cur.pz);
    phone.rotation.set(cur.prx + (reduce ? 0 : Math.sin(t * 0.7) * 0.025), cur.pry + (reduce ? 0 : Math.sin(t * 0.55) * 0.05), cur.prz);
    phone.scale.setScalar(Math.max(cur.ps * I, 0.001)); phone.visible = cur.ps > 0.05;
    phone.userData.glare.offset.x = -cur.pry * 0.9 + t * 0.02;
    // monitor
    var mo = clamp(cur.mo, 0, 1) * clamp(intro, 0, 1);
    mon.visible = mo > 0.01;
    mon.position.set(cur.mx, cur.my - bob * 0.05, cur.mz); mon.rotation.set(0, cur.mry + (reduce ? 0 : Math.sin(t * 0.5) * 0.03), 0); mon.scale.setScalar(Math.max(cur.ms * I, 0.001));
    monMats.forEach(function (m) { m.opacity = mo; });
    // cursor
    cursor.visible = cur.co > 0.02;
    cursor.position.set(cur.cx, cur.cy + (reduce ? 0 : Math.sin(t * 1.3) * 0.12), 0.8);
    cursor.rotation.set(par.y * 0.25, (reduce ? 0 : Math.sin(t * 0.8) * 0.55) + par.x * 0.4, -0.12);
    cursor.scale.setScalar(Math.max(cur.cs * cur.co * I, 0.001));

    // textures (throttled; phone UI and desktop are alive)
    texTick += dt;
    if (texTick > 1 / 30 || dirty || reduce) {
      texTick = 0;
      var tt = reduce ? 1.2 : t;
      if (phone.visible) drawPhone(phoneTex, clamp(cur.mode, 0, 2), tt);
      if (mon.visible) { var c = monTex.ctx; c.save(); c.setTransform(1, 0, 0, 1, 0, 0); drawDesk(c, 0, 0, 1024, 576, tt, finger(tt), 0); c.restore(); monTex.tex.needsUpdate = true; }
    }

    // rings (bluetooth range) centred on the phone
    rings.forEach(function (r, k) {
      var u = reduce ? (k + 1) / 3 : (t * 0.34 + k / 3) % 1, rad = lerp(0.95, cur.rmax, u) * cur.ps;
      r.visible = cur.ro > 0.02; r.position.set(phone.position.x, phone.position.y, phone.position.z - 0.12);
      r.scale.set(rad, rad, 1); r.material.opacity = Math.pow(1 - u, 1.6) * 0.5 * clamp(cur.ro, 0, 1) * (light ? 1.4 : 1);
    });

    // link between phone and monitor: curve + packets
    var so = clamp(cur.so, 0, 1) * (mo > 0.01 ? 1 : 0);
    stream.p.visible = curveLine.visible = so > 0.02;
    if (so > 0.02) {
      A.set(phone.position.x, phone.position.y + 1.62 * cur.ps, phone.position.z);
      B.set(mon.position.x, mon.position.y + 1.2 * cur.ms, mon.position.z);
      Cc.set((A.x + B.x) / 2, Math.max(A.y, B.y) + cur.lift, Math.max(A.z, B.z) + 0.9);
      var cp = curveGeo.attributes.position;
      for (var q = 0; q <= 60; q++) { bezier(q / 60, bez); cp.setXYZ(q, bez.x, bez.y, bez.z); }
      cp.needsUpdate = true;
      curveMat.opacity = 0.34 * so;
      for (q = 0; q < NS; q++) {
        var sd = streamSeed[q], u = reduce ? sd[0] : (sd[0] + t * sd[1]) % 1;
        bezier(u, bez); stream.pos[q * 3] = bez.x + sd[2]; stream.pos[q * 3 + 1] = bez.y + sd[3]; stream.pos[q * 3 + 2] = bez.z;
        stream.al[q] = Math.pow(Math.sin(u * Math.PI), 0.6) * so * (1 - sm((u - (cur.reach - 0.12)) / 0.12));
        stream.sz[q] = 1.4 + sd[1] * 9;
      }
      stream.g.attributes.position.needsUpdate = true; stream.g.attributes.aAlpha.needsUpdate = true; stream.g.attributes.aSize.needsUpdate = true;
    }

    // globe
    var go = clamp(cur.go, 0, 1);
    globe.visible = go > 0.01;
    globe.position.set(cur.gx, cur.gy, -0.6); globe.scale.setScalar(Math.max(cur.gs * I, 0.001));
    globe.rotation.y = reduce ? 0.6 : t * 0.1; globe.rotation.x = 0.35;
    globeMat.uniforms.uTime.value = t; globeMat.uniforms.uOpacity.value = go * (light ? 1.3 : 1);
    globe.userData.halo.material.opacity = 0.17 * go * (light ? 0.5 : 1);

    // dust
    for (q = 0; q < dustSeed.length; q++) {
      var d = dustSeed[q], ph = reduce ? 0 : t * (0.04 + d[3] * 0.06);
      dust.pos[q * 3] = d[0] + Math.sin(ph + d[4] * 6) * 0.4; dust.pos[q * 3 + 1] = ((d[1] + ph * 2 + 5.5) % 11) - 5.5; dust.pos[q * 3 + 2] = d[2];
      dust.al[q] = 0.2 + 0.5 * d[3];
    }
    dust.g.attributes.position.needsUpdate = true; dust.g.attributes.aAlpha.needsUpdate = true; dust.g.attributes.aSize.needsUpdate = true;
    dust.m.uniforms.uOpacity.value = light ? 0.55 : 0.7;

    cursorGlow.material.opacity = (light ? 0.14 : 0.5) * clamp(cur.co, 0, 1);
    renderer.render(scene, camera);
    dirty = false;
  }

  /* ---------------------------------------------------------------- loop */
  function onScroll() { stageF = stageAt(window.scrollY); if (reduce) { requestAnimationFrame(frame); } }
  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', function () { measure(); onScroll(); });
  window.addEventListener('load', function () { measure(); onScroll(); });
  if (window.ResizeObserver) new ResizeObserver(function () { var h = document.body.scrollHeight; if (Math.abs(h - (measure.h || 0)) > 4) { measure.h = h; measure(); onScroll(); } }).observe(document.body);
  document.addEventListener('visibilitychange', function () { last = performance.now(); });
  if (document.fonts && document.fonts.load) {
    Promise.all([document.fonts.load('700 20px Geist'), document.fonts.load('500 12px "Geist Mono"')]).then(function () { fontsReady = true; dirty = true; if (reduce) requestAnimationFrame(frame); });
  }
  applyLook(); measure(); onScroll();
  if (reduce) { requestAnimationFrame(frame); }
  else (function loop(now) { if (!document.hidden) frame(now); requestAnimationFrame(loop); })(performance.now());
  void fontsReady;
})();
