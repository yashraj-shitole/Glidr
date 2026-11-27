/* The working remote: a drawn phone whose trackpad, wheel, buttons and shortcut keys drive this page.
   Glidr mode is the pad alone. Screen mode adds a live miniature of the page that can be tapped directly.
   Nothing here is required to read or use the site. */
(function () {
  'use strict';
  var G = window.Glidr, $ = G.$, $$ = G.$$;
  var body = document.body;
  var remote = $('#remote'), pad = $('#pad'), wheel = $('#wheel'), cursor = $('#cursor'), mirror = $('#mirror');
  var ctx = $('#ctx'), toast = $('#toast'), remoteBtn = $('#remoteBtn');
  var wide = window.matchMedia('(min-width: 1180px)');
  var HOVERABLE = 'a[href], button, summary, input, .win.drag > .bar';

  var cur = { x: 0, y: 0, placed: false }, hovered = null, mode = 'pad', grab = null, clip = '';

  /* ---------------------------------------------------------------- the five tricks */
  var done = {}, total = 5;
  var tips = { move: 'drag on the pad', click: 'tap to click', scroll: 'use the wheel', keys: 'press a shortcut', see: 'try Screen mode' };
  function learn(name) {
    if (done[name]) return;
    done[name] = true;
    $$('[data-m="' + name + '"]').forEach(function (el) { el.classList.add('done'); });
    var n = Object.keys(done).length;
    var next = Object.keys(tips).filter(function (k) { return !done[k]; })[0];
    $('#progressText').textContent = n === total ? 'All 5 tricks learned' : n + '/' + total + ' · next: ' + tips[next];
    if (n === total) { toast.hidden = false; }
  }
  $('#toastClose').addEventListener('click', function () { toast.hidden = true; });
  $('a', toast).addEventListener('click', function () { toast.hidden = true; });

  /* ---------------------------------------------------------------- open, close, mode */
  function bounds() {                   // the part of the window the remote does not cover
    var w = window.innerWidth, h = window.innerHeight;
    if (!remote.hidden) {
      if (wide.matches) w -= remote.offsetWidth + 30;
      else h -= remote.offsetHeight + 66;
    }
    return { w: w, h: Math.max(h, 120) };
  }
  function open(m, remember) {
    remote.hidden = false; cursor.hidden = false;
    body.classList.add('remote-open');
    remoteBtn.setAttribute('aria-expanded', 'true');
    if (m) setMode(m);
    if (!cur.placed) { var b = bounds(); move(b.w * 0.55, b.h * 0.5, true); cur.placed = true; }
    else move(cur.x, cur.y, true);
    if (remember) G.store('glidr-remote', 'open');
    draw();
  }
  function close() {
    remote.hidden = true; cursor.hidden = true; ctx.hidden = true;
    body.classList.remove('remote-open', 'show-desktop');
    remoteBtn.setAttribute('aria-expanded', 'false');
    hover(null);
    G.store('glidr-remote', 'closed');
  }
  function setMode(m) {
    mode = m;
    mirror.hidden = m !== 'screen';
    $$('[data-mode]').forEach(function (b) { b.setAttribute('aria-pressed', b.dataset.mode === m); });
    if (m === 'screen') learn('see');
    draw();
  }
  remoteBtn.addEventListener('click', function () { if (remote.hidden) open(null, true); else close(); });
  $('#remoteClose').addEventListener('click', function () { close(); remoteBtn.focus(); });
  $$('[data-open-remote]').forEach(function (b) {
    b.addEventListener('click', function () { open(b.getAttribute('data-open-remote') || null, true); pad.focus({ preventScroll: true });
      if (!G.reduce && remote.firstElementChild.animate) remote.firstElementChild.animate({ rotate: ['0deg', '-4deg', '3deg', '-2deg', '0deg'] }, { duration: 520 });
    });
  });
  $$('[data-mode]').forEach(function (b) { b.addEventListener('click', function () { setMode(b.dataset.mode); }); });

  /* ---------------------------------------------------------------- the pointer */
  function under() {
    var el = document.elementFromPoint(cur.x, cur.y);
    return el && !remote.contains(el) ? el : null;
  }
  function hover(el) {
    if (hovered === el) return;
    if (hovered) hovered.classList.remove('vhover');
    hovered = el;
    if (el) el.classList.add('vhover');
  }
  function move(x, y, absolute) {
    var b = bounds();
    var nx = absolute ? x : cur.x + x, ny = absolute ? y : cur.y + y;
    nx = Math.max(4, Math.min(b.w - 6, nx));
    // below the top or bottom edge the page scrolls, as if the desktop were taller than the screen
    var floor = wide.matches || remote.hidden ? window.innerHeight : b.h;
    var over = ny < 8 ? ny - 8 : (ny > floor - 8 ? ny - floor + 8 : 0);
    if (!absolute && over) scroll(over);
    ny = Math.max(4, Math.min(floor - 6, ny));
    var dx = nx - cur.x, dy = ny - cur.y;
    cur.x = nx; cur.y = ny;
    cursor.style.transform = 'translate(' + nx.toFixed(1) + 'px,' + ny.toFixed(1) + 'px)';
    if (grab) {
      if (grab.kind === 'win') grab.drag.by(dx, dy);
      else if (grab.kind === 'range') setRange(grab.el);
    }
    var el = under();
    hover(el ? el.closest(HOVERABLE) : null);
    draw();
  }
  function setRange(input) {
    var r = input.getBoundingClientRect();
    var p = Math.max(0, Math.min(1, (cur.x - r.left) / r.width));
    input.value = Math.round(+input.min + p * (input.max - input.min));
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
  function ring() {
    var r = document.createElement('div'); r.className = 'click-ring';
    r.style.left = cur.x + 'px'; r.style.top = cur.y + 'px';
    body.appendChild(r); setTimeout(function () { r.remove(); }, 600);
  }
  function click() {
    ring(); learn('click');
    var el = under();
    if (!ctx.hidden && !(el && ctx.contains(el))) { ctx.hidden = true; return; }
    if (!el) return;
    var t = el.closest(HOVERABLE);
    if (!t) return;
    if (t.tagName === 'INPUT') { setRange(t); t.focus({ preventScroll: true }); return; }
    t.classList.add('vpress'); setTimeout(function () { t.classList.remove('vpress'); }, 140);
    if (typeof t.click === 'function') t.click();
    setTimeout(draw, 60);
  }
  function rightClick() {
    ring(); learn('click');
    ctx.hidden = false;
    var b = bounds();
    ctx.style.left = Math.max(6, Math.min(cur.x, b.w - ctx.offsetWidth - 6)) + 'px';
    ctx.style.top = Math.max(6, Math.min(cur.y, b.h - ctx.offsetHeight - 6)) + 'px';
  }
  function press() {                    // button held down: grab a title bar or the slider
    cursor.classList.add('down');
    var el = under();
    if (!el) return;
    var bar = el.closest('.win.drag > .bar');
    if (bar && !el.closest('button')) grab = { kind: 'win', drag: G.dragger(bar.parentNode) };
    else if (el.tagName === 'INPUT' && el.type === 'range') grab = { kind: 'range', el: el };
  }
  function release() {
    cursor.classList.remove('down');
    if (grab && grab.kind === 'win') grab.drag.end();
    grab = null;
  }

  var travelled = 0, scrolled = 0;
  function scroll(dy) {
    window.scrollBy({ top: dy, behavior: 'instant' });
    scrolled += Math.abs(dy);
    if (scrolled > 220) learn('scroll');
    var roll = (parseFloat(wheel.style.getPropertyValue('--roll')) || 0) - dy / 4;
    wheel.style.setProperty('--roll', (roll % 13) + 'px');
  }

  /* ---------------------------------------------------------------- trackpad gestures */
  var fingers = new Map(), gesture = null, lastTap = 0;
  function mark(f) {
    var r = pad.getBoundingClientRect();
    f.dot.style.left = (f.x - r.left) + 'px'; f.dot.style.top = (f.y - r.top) + 'px';
  }
  pad.addEventListener('pointerdown', function (e) {
    if (e.pointerType === 'mouse' && e.button === 2) return;
    e.preventDefault();
    try { pad.setPointerCapture(e.pointerId); } catch (err) {}
    var f = { x: e.clientX, y: e.clientY, t: e.timeStamp, dot: document.createElement('i') };
    f.dot.className = 'touch-dot'; pad.appendChild(f.dot); mark(f);
    fingers.set(e.pointerId, f);
    pad.classList.add('active', 'used');
    if (fingers.size === 1) {
      gesture = { start: e.timeStamp, moved: 0, max: 1, drag: false };
      if (e.timeStamp - lastTap < 280) { gesture.drag = true; press(); }   // tap, then drag
    } else if (gesture) {
      gesture.max = Math.max(gesture.max, fingers.size);
      if (gesture.drag) { gesture.drag = false; release(); }
    }
  });
  pad.addEventListener('pointermove', function (e) {
    var f = fingers.get(e.pointerId);
    if (!f || !gesture) return;
    var dx = e.clientX - f.x, dy = e.clientY - f.y, dt = Math.max(e.timeStamp - f.t, 8);
    f.x = e.clientX; f.y = e.clientY; f.t = e.timeStamp; mark(f);
    var dist = Math.hypot(dx, dy);
    gesture.moved += dist;
    if (fingers.size >= 2) { scroll(-dy * 2.2 / fingers.size * 2); return; }
    if (gesture.max > 1) return;
    var gain = 1.5 + Math.min(dist / dt, 2) * 1.4;                           // faster swipes travel further
    if (wide.matches && e.pointerType === 'mouse') gain *= 1.15;
    move(dx * gain, dy * gain);
    travelled += dist;
    if (travelled > 90) learn('move');
  });
  function lift(e) {
    var f = fingers.get(e.pointerId);
    if (!f) return;
    f.dot.remove(); fingers.delete(e.pointerId);
    if (fingers.size) return;
    pad.classList.remove('active');
    if (!gesture) return;
    var tap = e.type === 'pointerup' && gesture.moved < 9 && e.timeStamp - gesture.start < 350;
    release();
    if (tap) {
      var r = pad.getBoundingClientRect(), rip = document.createElement('i'); rip.className = 'rip';
      rip.style.left = (e.clientX - r.left) + 'px'; rip.style.top = (e.clientY - r.top) + 'px';
      pad.appendChild(rip); setTimeout(function () { rip.remove(); }, 650);
      if (gesture.max >= 2) rightClick();
      else { click(); lastTap = e.timeStamp; }
    }
    gesture = null;
  }
  pad.addEventListener('pointerup', lift);
  pad.addEventListener('pointercancel', lift);
  pad.addEventListener('contextmenu', function (e) { e.preventDefault(); rightClick(); });
  pad.addEventListener('keydown', function (e) {
    var step = e.shiftKey ? 90 : 28;
    var map = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
    if (map[e.key]) { e.preventDefault(); pad.classList.add('used'); move(map[e.key][0], map[e.key][1]); learn('move'); }
    else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); click(); }
    else if (e.key === 'PageDown' || e.key === 'PageUp') { e.preventDefault(); scroll((e.key === 'PageDown' ? 1 : -1) * 320); }
  });

  /* ---------------------------------------------------------------- scroll wheel (with a little momentum) */
  (function () {
    var id = null, y = 0, v = 0, spin = 0;
    function coast() { v *= 0.93; if (Math.abs(v) < 0.8) return; scroll(v); spin = requestAnimationFrame(coast); }
    wheel.addEventListener('pointerdown', function (e) { e.preventDefault(); cancelAnimationFrame(spin); id = e.pointerId; y = e.clientY; v = 0; try { wheel.setPointerCapture(id); } catch (err) {} });
    wheel.addEventListener('pointermove', function (e) { if (e.pointerId !== id) return; v = (y - e.clientY) * -4; y = e.clientY; scroll(v); });
    function end(e) { if (e.pointerId !== id) return; id = null; if (!G.reduce) spin = requestAnimationFrame(coast); }
    wheel.addEventListener('pointerup', end); wheel.addEventListener('pointercancel', end);
    wheel.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); scroll((e.key === 'ArrowDown' ? 1 : -1) * 160); }
    });
  })();

  /* ---------------------------------------------------------------- mouse buttons */
  (function () {
    var left = $('#btnL'), down = 0, moved = false, x = 0, y = 0;
    left.addEventListener('pointerdown', function (e) {
      e.preventDefault(); down = e.timeStamp; moved = false; x = cur.x; y = cur.y;
      left.classList.add('held'); press();
    });
    function up(e) {
      if (!down) return;
      moved = Math.hypot(cur.x - x, cur.y - y) > 6;
      var quick = e.timeStamp - down < 500;
      down = 0; left.classList.remove('held'); release();
      if (e.type === 'pointerup' && quick && !moved) click();
    }
    left.addEventListener('pointerup', up); left.addEventListener('pointercancel', up);
    left.addEventListener('click', function (e) { if (e.detail === 0) click(); });   // keyboard activation
    $('#btnR').addEventListener('click', rightClick);
  })();

  /* ---------------------------------------------------------------- shortcut keys */
  function nextScene() {
    var i = G.scenes.indexOf(G.chapter());
    var n = G.scenes[(i + 1) % G.scenes.length];
    n.scrollIntoView({ behavior: G.reduce ? 'auto' : 'smooth', block: 'start' });
  }
  var keys = {
    desk: function () { body.classList.toggle('show-desktop'); },
    next: function () { body.classList.remove('show-desktop'); nextScene(); },
    copy: function () {                 // copies inside this demo only; the real clipboard is left alone
      var el = under(), src = el && el.closest('h1, h2, h3, li, summary, .btn, p, .dicon');
      if (!src) src = $('h2, h1', G.chapter());
      clip = src.textContent.replace(/\s+/g, ' ').trim().slice(0, 90);
      src.animate && src.animate([{ background: 'var(--ac)', color: '#0b0c0a' }, { background: 'var(--ac)', color: '#0b0c0a', offset: 0.7 }, {}], { duration: 700 });
    },
    paste: function () {
      $$('.paste').slice(0, -5).forEach(function (p) { p.remove(); });
      var note = document.createElement('div'); note.className = 'paste';
      note.textContent = clip || 'Copy something first: point at it, press Copy.';
      var x = document.createElement('button'); x.type = 'button'; x.textContent = '✕'; x.setAttribute('aria-label', 'Remove note');
      x.addEventListener('click', function () { note.remove(); });
      note.appendChild(x);
      note.style.left = Math.max(8, Math.min(cur.x, bounds().w - 232)) + window.scrollX + 'px';
      note.style.top = cur.y + window.scrollY + 'px';
      body.appendChild(note);
    }
  };
  $$('[data-key]').forEach(function (b) {
    b.addEventListener('click', function () {
      ctx.hidden = true;
      b.classList.add('fire'); setTimeout(function () { b.classList.remove('fire'); }, 240);
      keys[b.dataset.key](); learn('keys'); setTimeout(draw, 80);
    });
  });
  $('a', ctx).addEventListener('click', function () { ctx.hidden = true; });
  window.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;
    ctx.hidden = true; body.classList.remove('show-desktop');
  });
  $$('.taskbar a').forEach(function (a) { a.addEventListener('click', function () { body.classList.remove('show-desktop'); }); });

  /* ---------------------------------------------------------------- Screen mode: a live miniature of the page */
  var pending = false, boxes = [], pointerEl = document.createElement('u');
  mirror.appendChild(pointerEl);
  function draw() {
    if (pending || mode !== 'screen' || remote.hidden) return;
    pending = true;
    requestAnimationFrame(paint);
  }
  function paint() {
    pending = false;
    if (mode !== 'screen' || remote.hidden) return;
    var b = bounds(), mw = mirror.clientWidth, mh = mirror.clientHeight, k = Math.min(mw / b.w, mh / b.h);
    var ox = (mw - b.w * k) / 2, oy = (mh - b.h * k) / 2, n = 0;
    if (!body.classList.contains('show-desktop')) {
      $$('[data-mirror]').forEach(function (el) {
        var r = el.getBoundingClientRect();
        if (r.bottom < 0 || r.top > b.h || !r.width) return;
        var box = boxes[n] || (boxes[n] = mirror.insertBefore(document.createElement('i'), pointerEl));
        var head = /^(H1|H2|P)$/.test(el.tagName) || el.classList.contains('desk-icons');
        var label = $('.ttl', el);
        box.className = head ? 'head' : '';
        box.textContent = el.classList.contains('desk-icons') ? '▣ ▣ ▣ ▣' : head ? el.textContent.trim() : (label ? label.textContent : '');
        box.style.cssText = 'left:' + (ox + r.left * k) + 'px;top:' + (oy + r.top * k) + 'px;width:' + (r.width * k) + 'px;height:' + (r.height * k) + 'px';
        n++;
      });
    }
    for (var i = n; i < boxes.length; i++) boxes[i].style.display = 'none';
    pointerEl.style.left = (ox + cur.x * k) + 'px'; pointerEl.style.top = (oy + cur.y * k) + 'px';
    mirror._map = { k: k, ox: ox, oy: oy };
  }
  mirror.addEventListener('pointerdown', function (e) {          // direct touch: tap the picture, click that spot
    var m = mirror._map;
    if (!m) return;
    e.preventDefault();
    var r = mirror.getBoundingClientRect();
    move((e.clientX - r.left - mirror.clientLeft - m.ox) / m.k, (e.clientY - r.top - mirror.clientTop - m.oy) / m.k, true);
    click();
  });

  window.addEventListener('scroll', function () {
    ctx.hidden = true;
    var el = remote.hidden ? null : under();
    hover(el ? el.closest(HOVERABLE) : null);
    var max = document.documentElement.scrollHeight - window.innerHeight;
    wheel.setAttribute('aria-valuenow', max > 0 ? Math.round(window.scrollY / max * 100) : 0);
    draw();
  }, { passive: true });
  window.addEventListener('resize', function () { if (!remote.hidden) move(cur.x, cur.y, true); });
  G.onChange(function () { draw(); setTimeout(draw, 480); });

  /* on a wide screen the remote is part of the desk from the start, unless this visitor put it down before */
  if (wide.matches && G.store('glidr-remote') !== 'closed') setTimeout(function () { open(null, false); }, 700);
})();
