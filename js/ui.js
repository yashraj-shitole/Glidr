/* Page behaviour: theme + accent, reveals, 3D card tilt, magnetic buttons, live demos.
   Shares state with scene.js through window.Glidr. */
(function () {
  'use strict';
  var doc = document.documentElement;
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var coarse = window.matchMedia('(pointer: coarse)').matches;
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

  var listeners = [];
  var G = window.Glidr = {
    mouse: { x: 0, y: 0 },           // -1..1 across the viewport
    reduce: reduce,
    onChange: function (fn) { listeners.push(fn); },
    accent: function () { return getComputedStyle(doc).getPropertyValue('--ac').trim() || '#d4ff3f'; },
    light: function () { return doc.getAttribute('data-theme') === 'light'; }
  };
  function emit() { listeners.forEach(function (f) { f(); }); }

  /* ---------- theme + accent (remembered per viewer, never required) ---------- */
  function store(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { return null; } }
  function setTheme(t) {
    doc.setAttribute('data-theme', t);
    $$('[data-theme-set]').forEach(function (b) { b.classList.toggle('on', b.dataset.themeSet === t); });
    var m = $('meta[name=theme-color]'); if (m) m.content = t === 'light' ? '#f1f2f4' : '#000000';
    store('glidr-theme', t); emit();
  }
  function setAccent(a) {
    doc.setAttribute('data-accent', a);
    $$('[data-accent-set]').forEach(function (b) { b.classList.toggle('on', b.dataset.accentSet === a); });
    store('glidr-accent', a); emit();
  }
  var st = store('glidr-theme'), sa = store('glidr-accent');
  if (st === 'light' || st === 'dark') setTheme(st);
  if (sa && /^(volt|ember|sky|rose)$/.test(sa)) setAccent(sa);
  $('#themeBtn').addEventListener('click', function () { setTheme(G.light() ? 'dark' : 'light'); });
  $$('[data-theme-set]').forEach(function (b) { b.addEventListener('click', function () { setTheme(b.dataset.themeSet); }); });
  $$('[data-accent-set]').forEach(function (b) { b.addEventListener('click', function () { setAccent(b.dataset.accentSet); }); });

  /* ---------- hero headline: split into letters for the spring pop ---------- */
  var idx = 0;
  $$('h1.split .line').forEach(function (line) {
    (function walk(node) {
      Array.prototype.slice.call(node.childNodes).forEach(function (n) {
        if (n.nodeType === 3) {
          var frag = document.createDocumentFragment();
          n.textContent.split(/(\s+)/).forEach(function (w) {
            if (!w) return;
            if (/^\s+$/.test(w)) { frag.appendChild(document.createTextNode(' ')); return; }
            var word = document.createElement('span'); word.style.whiteSpace = 'nowrap'; word.style.display = 'inline-block';
            w.split('').forEach(function (c) { var s = document.createElement('span'); s.className = 'ch'; s.style.setProperty('--i', idx++); s.textContent = c; word.appendChild(s); });
            frag.appendChild(word);
          });
          n.parentNode.replaceChild(frag, n);
        } else if (n.nodeType === 1) walk(n);
      });
    })(line);
  });
  $$('.hero .copy > :not(h1)').forEach(function (el, i) { el.style.setProperty('--d', i); });
  requestAnimationFrame(function () { requestAnimationFrame(function () { doc.classList.add('ready'); }); });

  /* ---------- scroll reveal ---------- */
  var groups = new Map();
  $$('.reveal').forEach(function (el) {
    var p = el.parentElement; var n = groups.get(p) || 0; groups.set(p, n + 1);
    el.style.setProperty('--rd', Math.min(n, 6) * 70 + 'ms');
  });
  if ('IntersectionObserver' in window && !reduce) {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
    $$('.reveal').forEach(function (el) { io.observe(el); });
  } else { $$('.reveal').forEach(function (el) { el.classList.add('in'); }); }

  /* ---------- pointer: spotlight, scene parallax, magnetic buttons, card tilt ---------- */
  var spot = $('.spot'), tx = innerWidth / 2, ty = innerHeight * 0.3, sx = tx, sy = ty;
  window.addEventListener('pointermove', function (e) {
    tx = e.clientX; ty = e.clientY;
    G.mouse.x = (e.clientX / innerWidth) * 2 - 1; G.mouse.y = (e.clientY / innerHeight) * 2 - 1;
  }, { passive: true });
  (function loop() {
    sx += (tx - sx) * 0.12; sy += (ty - sy) * 0.12;
    spot.style.setProperty('--mx', sx.toFixed(1) + 'px'); spot.style.setProperty('--my', sy.toFixed(1) + 'px');
    requestAnimationFrame(loop);
  })();

  if (!coarse && !reduce) {
    $$('.magnetic').forEach(function (b) {
      b.addEventListener('pointermove', function (e) {
        var r = b.getBoundingClientRect();
        b.style.transform = 'translate(' + ((e.clientX - r.left - r.width / 2) * 0.18).toFixed(1) + 'px,' + ((e.clientY - r.top - r.height / 2) * 0.28).toFixed(1) + 'px)';
      });
      b.addEventListener('pointerleave', function () { b.style.transform = ''; });
    });
    $$('.card.tilt').forEach(function (c) {
      c.addEventListener('pointermove', function (e) {
        var r = c.getBoundingClientRect(), px = (e.clientX - r.left) / r.width, py = (e.clientY - r.top) / r.height;
        c.classList.add('moving');
        c.style.setProperty('--ry', ((px - 0.5) * 9).toFixed(2) + 'deg');
        c.style.setProperty('--rx', ((0.5 - py) * 9).toFixed(2) + 'deg');
        c.style.setProperty('--gx', (px * 100).toFixed(1) + '%'); c.style.setProperty('--gy', (py * 100).toFixed(1) + '%');
        c.style.setProperty('--glare', 1);
      });
      c.addEventListener('pointerleave', function () {
        c.classList.remove('moving');
        c.style.setProperty('--rx', '0deg'); c.style.setProperty('--ry', '0deg'); c.style.setProperty('--glare', 0);
      });
    });
  }

  /* ---------- nav hides while scrolling down, returns on the way up ---------- */
  var nav = $('#nav'), lastY = 0;
  window.addEventListener('scroll', function () {
    var y = window.scrollY;
    nav.classList.toggle('hide', y > lastY && y > 240);
    lastY = y;
  }, { passive: true });

  /* ---------- demo: the pad ---------- */
  var pad = $('#demoPad');
  if (pad) {
    var glow = document.createElement('div'); glow.className = 'glow'; pad.appendChild(glow);
    pad.addEventListener('pointermove', function (e) {
      var r = pad.getBoundingClientRect(); glow.style.left = (e.clientX - r.left) + 'px'; glow.style.top = (e.clientY - r.top) + 'px';
    });
    pad.addEventListener('pointerdown', function (e) {
      var r = pad.getBoundingClientRect(), rip = document.createElement('div'); rip.className = 'rip';
      rip.style.left = (e.clientX - r.left) + 'px'; rip.style.top = (e.clientY - r.top) + 'px';
      pad.appendChild(rip); setTimeout(function () { rip.remove(); }, 800);
    });
  }

  /* ---------- demo: shortcuts flash like a real press ---------- */
  $$('.sc').forEach(function (b) {
    b.addEventListener('click', function () { b.classList.add('fire'); setTimeout(function () { b.classList.remove('fire'); }, 260); });
  });

  /* ---------- demo: typing in any language ---------- */
  var typeEl = $('#typeText');
  if (typeEl) {
    var phrases = ['Hello from my phone', 'नमस्ते, मेरे फ़ोन से', 'こんにちは、スマホから', 'مرحبا من هاتفي', 'Привет с телефона', 'Hola desde mi móvil'];
    var pi = 0, ci = 0, dir = 1, wait = 0;
    if (reduce) typeEl.textContent = phrases[0];
    else setInterval(function () {
      if (wait > 0) { wait--; return; }
      var chars = Array.from(phrases[pi]);
      ci += dir;
      typeEl.textContent = chars.slice(0, ci).join('');
      if (dir === 1 && ci >= chars.length) { dir = -1; wait = 22; }
      else if (dir === -1 && ci <= 0) { dir = 1; pi = (pi + 1) % phrases.length; wait = 4; }
    }, 70);
  }

  /* ---------- demo: monitors ---------- */
  var labels = ['VIEWING DISPLAY 1 · 1920 × 1080', 'VIEWING DISPLAY 2 · 2560 × 1440', 'VIEWING DISPLAY 3 · 1280 × 1024'];
  $$('#monDemo .mon').forEach(function (b) {
    b.addEventListener('click', function () {
      $$('#monDemo .mon').forEach(function (o) { o.classList.toggle('on', o === b); });
      $('#monLabel').textContent = labels[+b.dataset.i];
    });
  });

  /* ---------- demo: live fps trace ---------- */
  var sp = $('#sparkPath');
  if (sp) {
    var pts = []; for (var i = 0; i < 60; i++) pts.push(24);
    var tick = 0;
    var upd = function () {
      tick++;
      var v = 24 - (Math.random() < 0.12 ? Math.random() * 3 : Math.random() * 0.6);
      pts.push(v); pts.shift();
      var d = pts.map(function (p, i) { return (i ? 'L' : 'M') + (i * 300 / 59).toFixed(1) + ' ' + (10 + (26 - p) * 12).toFixed(1); }).join('');
      sp.setAttribute('d', d);
      if (tick % 6 === 0) { $('#fpsNum').textContent = Math.round(v); $('#pingNum').textContent = 12 + Math.round(Math.random() * 6); }
    };
    upd(); if (!reduce) setInterval(upd, 140);
  }

  /* ---------- decorative QR-style pattern (not a real code) ---------- */
  var qr = $('#qr');
  if (qr) {
    var seed = 7, rnd = function () { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
    var finder = function (x, y) { return '<path d="M' + x + ' ' + y + 'h7v7h-7zM' + (x + 1) + ' ' + (y + 1) + 'v5h5v-5z" fill-rule="evenodd"/><rect x="' + (x + 2) + '" y="' + (y + 2) + '" width="3" height="3"/>'; };
    var out = finder(0, 0) + finder(14, 0) + finder(0, 14);
    for (var y = 0; y < 21; y++) for (var x = 0; x < 21; x++) {
      var inF = (x < 8 && y < 8) || (x > 12 && y < 8) || (x < 8 && y > 12);
      if (!inF && rnd() > 0.52) out += '<rect x="' + x + '" y="' + y + '" width="1" height="1"/>';
    }
    qr.innerHTML = out; qr.setAttribute('fill', 'currentColor');
  }
})();
