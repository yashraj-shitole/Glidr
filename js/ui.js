/* Page behaviour that works with or without the remote: theme + accent, wallpaper chapters, taskbar,
   window dragging, small demos, the launch film and the download links. */
(function () {
  'use strict';
  var doc = document.documentElement, body = document.body;
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

  var listeners = [];
  var G = window.Glidr = {
    reduce: reduce, $: $, $$: $$,
    onChange: function (fn) { listeners.push(fn); },
    changed: function () { listeners.forEach(function (f) { f(); }); }
  };

  /* ---------- theme + accent (remembered per viewer, never required) ---------- */
  function store(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { return null; } }
  G.store = store;
  function setTheme(t) { doc.setAttribute('data-theme', t); store('glidr-theme', t); G.changed(); }
  function setAccent(a) {
    doc.setAttribute('data-accent', a);
    $$('[data-accent-set]').forEach(function (b) { var on = b.dataset.accentSet === a; b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
    store('glidr-accent', a); G.changed();
  }
  var st = store('glidr-theme'), sa = store('glidr-accent');
  if (st === 'light' || st === 'dark') setTheme(st);
  setAccent(/^(volt|ember|sky|rose)$/.test(sa) ? sa : 'volt');
  $('#themeBtn').addEventListener('click', function () { setTheme(doc.getAttribute('data-theme') === 'dark' ? 'light' : 'dark'); });
  $$('[data-accent-set]').forEach(function (b) { b.addEventListener('click', function () { setAccent(b.dataset.accentSet); }); });

  /* ---------- the wallpaper and the taskbar follow the chapter in the middle of the screen ---------- */
  var scenes = $$('.scene'), current = null;
  G.scenes = scenes;
  var meta = $('meta[name=theme-color]');
  function chapter() {
    var mid = window.innerHeight * 0.5, pick = scenes[0];
    scenes.forEach(function (s) { if (s.getBoundingClientRect().top <= mid) pick = s; });
    if (pick === current) return;
    current = pick;
    body.setAttribute('data-wall', pick.dataset.wall);
    $$('.tasks a').forEach(function (a) {
      var on = a.dataset.task === pick.id || (a.dataset.task === 'screen' && pick.id === 'setup');
      a.classList.toggle('on', on);
      if (on) { a.setAttribute('aria-current', 'true'); var p = a.parentNode; p.scrollLeft = a.offsetLeft - p.offsetLeft - (p.clientWidth - a.offsetWidth) / 2; }
      else a.removeAttribute('aria-current');
    });
    if (meta) setTimeout(function () { meta.content = getComputedStyle(body).backgroundColor; }, 600);
    G.changed();
  }
  G.chapter = function () { return current; };
  window.addEventListener('scroll', chapter, { passive: true });
  window.addEventListener('resize', chapter);
  chapter();

  /* ---------- clock ---------- */
  var clock = $('#clock');
  (function tick() { var d = new Date(); clock.textContent = ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2); setTimeout(tick, 15000); })();

  /* ---------- windows: minimise, and drag by the title bar (mouse and pen; touch keeps scrolling) ---------- */
  $$('.min').forEach(function (b) {
    var win = b.closest('.win');
    b.setAttribute('aria-expanded', 'true');
    b.addEventListener('click', function () {
      var off = win.classList.toggle('minimised');
      b.setAttribute('aria-expanded', !off); G.changed();
    });
  });
  var z = 1;
  G.dragger = function (win) {          // returns a function that moves a window by a delta
    var x = parseFloat(win.style.getPropertyValue('--dx')) || 0, y = parseFloat(win.style.getPropertyValue('--dy')) || 0;
    win.classList.add('dragging'); win.style.zIndex = ++z;
    return {
      by: function (dx, dy) { x += dx; y += dy; win.style.setProperty('--dx', x + 'px'); win.style.setProperty('--dy', y + 'px'); G.changed(); },
      end: function () { win.classList.remove('dragging'); }
    };
  };
  $$('.win.drag > .bar').forEach(function (bar) {
    var win = bar.parentNode;
    bar.addEventListener('pointerdown', function (e) {
      if (e.pointerType === 'touch' || e.button !== 0 || e.target.closest('button')) return;
      e.preventDefault();
      var d = G.dragger(win), px = e.clientX, py = e.clientY;
      function move(ev) { d.by(ev.clientX - px, ev.clientY - py); px = ev.clientX; py = ev.clientY; }
      function up() { d.end(); window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', up); }
      window.addEventListener('pointermove', move); window.addEventListener('pointerup', up); window.addEventListener('pointercancel', up);
    });
    bar.addEventListener('dblclick', function () { win.style.setProperty('--dx', '0px'); win.style.setProperty('--dy', '0px'); G.changed(); });
  });

  /* ---------- range test ---------- */
  var dist = $('#dist');
  if (dist) {
    var walker = $('#walker'), out = $('#distOut'), vg = $('#vg'), vs = $('#vs');
    var gRow = $('[data-v="g"]'), sRow = $('[data-v="s"]');
    var update = function () {
      var p = dist.value / 100;
      // 0..0.33 of the slider covers 0..10 m, the rest stretches to "across town"
      var m = p <= 0.33 ? p / 0.33 * 10 : 10 + Math.pow((p - 0.33) / 0.67, 3) * 4990;
      var label = m < 1000 ? Math.round(m) + ' m' : (m / 1000).toFixed(1) + ' km';
      walker.parentNode.style.setProperty('--p', p);
      out.textContent = label; dist.setAttribute('aria-valuetext', label);
      var far = m > 10;
      gRow.classList.toggle('off', far);
      vg.textContent = far ? 'Out of Bluetooth range' : 'Connected over Bluetooth';
      vs.textContent = m <= 30 ? 'Connected over Wi-Fi' : 'Connected over the internet';
      sRow.classList.remove('off');
    };
    dist.addEventListener('input', update); update();
  }

  /* ---------- demo: typing in any language ---------- */
  var typeEl = $('#typeText');
  if (typeEl) {
    var phrases = ['Hello from my phone', 'नमस्ते, मेरे फ़ोन से', 'こんにちは、スマホから', 'مرحبا من هاتفي', 'Привет с телефона', 'Hola desde mi móvil'];
    var pi = 0, ci = 0, dir = 1, wait = 0;
    if (reduce) typeEl.textContent = phrases[0];
    else setInterval(function () {
      if (document.hidden) return;
      if (wait > 0) { wait--; return; }
      var chars = Array.from(phrases[pi]);
      ci += dir;
      typeEl.textContent = chars.slice(0, ci).join('');
      if (dir === 1 && ci >= chars.length) { dir = -1; wait = 22; }
      else if (dir === -1 && ci <= 0) { dir = 1; pi = (pi + 1) % phrases.length; wait = 4; }
    }, 70);
  }

  /* ---------- demo: monitors ---------- */
  var labels = ['Viewing display 1 · 1920 × 1080', 'Viewing display 2 · 2560 × 1440', 'Viewing display 3 · 1280 × 1024'];
  $$('#monDemo .mon').forEach(function (b) {
    b.setAttribute('aria-pressed', b.classList.contains('on'));
    b.addEventListener('click', function () {
      $$('#monDemo .mon').forEach(function (o) { o.classList.toggle('on', o === b); o.setAttribute('aria-pressed', o === b); });
      $('#monLabel').textContent = labels[+b.dataset.i];
    });
  });

  /* ---------- demo: fps trace (illustrative) ---------- */
  var sp = $('#sparkPath');
  if (sp) {
    var pts = []; for (var i = 0; i < 50; i++) pts.push(30);
    var tick = 0;
    var upd = function () {
      if (document.hidden) return;
      tick++;
      pts.push(30 - (Math.random() < 0.12 ? Math.random() * 4 : Math.random() * 0.8)); pts.shift();
      sp.setAttribute('d', pts.map(function (p, i) { return (i ? 'L' : 'M') + (i * 300 / 49).toFixed(1) + ' ' + (8 + (31 - p) * 10).toFixed(1); }).join(''));
      if (tick % 6 === 0) { $('#fpsNum').textContent = Math.round(pts[pts.length - 1]); $('#pingNum').textContent = 12 + Math.round(Math.random() * 6); }
    };
    upd(); if (!reduce) setInterval(upd, 160);
  }

  /* ---------- launch film: starts on click (so it can have sound), controls appear once it plays ---------- */
  (function () {
    var v = $('#film'), btn = $('#playBtn'), box = $('#player');
    if (!v || !btn) return;
    btn.addEventListener('click', function () { v.controls = true; var p = v.play(); if (p && p.catch) p.catch(function () { v.controls = true; box.classList.add('playing'); }); });
    v.addEventListener('play', function () { box.classList.add('playing'); });
    v.addEventListener('ended', function () { box.classList.remove('playing'); v.controls = false; v.currentTime = 0; });
  })();

  /* ---------- downloads: always the newest release ----------
     Every [data-asset] link starts as the repo's releases/latest page (correct with no JavaScript, opens in a new tab).
     Once GitHub answers, it becomes the direct file URL of the newest release. The answer is cached for 15 minutes to stay
     inside the unauthenticated API limit; if the lookup fails the links simply stay on releases/latest. */
  (function () {
    var links = $$('a[data-asset]');
    if (!links.length || !window.fetch) return;
    var repos = {};
    links.forEach(function (a) { repos[a.dataset.repo] = true; });
    var pick = {
      apk: function (n) { return /\.apk$/i.test(n); },
      // the PC build: an installer .exe if there is one, otherwise the Windows .zip
      pc: function (n) { return /\.exe$/i.test(n) || /win.*\.zip$/i.test(n); }
    };
    function apply(repo, rel) {
      var assets = rel.assets || [];
      links.filter(function (a) { return a.dataset.repo === repo; }).forEach(function (a) {
        var hit = assets.filter(function (x) { return pick[a.dataset.asset](x.name); })
          .sort(function (x, y) { return (/\.exe$/i.test(y.name) ? 1 : 0) - (/\.exe$/i.test(x.name) ? 1 : 0); })[0];
        if (!hit) return;                                  // leave the releases page as the target
        a.href = hit.browser_download_url; a.removeAttribute('target'); a.removeAttribute('rel');
        a.title = hit.name;
      });
      $$('[data-ver="' + repo + '"]').forEach(function (v) { v.textContent = rel.tag_name; });
    }
    Object.keys(repos).forEach(function (repo) {
      var key = 'glidr-rel-' + repo, hit = null;
      try { var c = JSON.parse(sessionStorage.getItem(key) || 'null'); if (c && Date.now() - c.t < 15 * 60 * 1000) hit = c.rel; } catch (e) {}
      if (hit) return apply(repo, hit);
      fetch('https://api.github.com/repos/' + repo + '/releases/latest', { headers: { Accept: 'application/vnd.github+json' } })
        .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
        .then(function (rel) {
          var slim = { tag_name: rel.tag_name, assets: (rel.assets || []).map(function (x) { return { name: x.name, browser_download_url: x.browser_download_url }; }) };
          try { sessionStorage.setItem(key, JSON.stringify({ t: Date.now(), rel: slim })); } catch (e) {}
          apply(repo, slim);
        })
        .catch(function () { /* keep the releases/latest fallback */ });
    });
  })();
})();
