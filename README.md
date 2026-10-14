# Glidr + Glidr Screen: launch page

Landing page for the joint launch of [Glidr](https://github.com/yashraj-shitole/Glidr) (phone as a Bluetooth mouse,
keyboard and remote) and [Glidr Screen](https://github.com/yashraj-shitole/GlidrScreen) (your Windows PC on your phone).

Plain HTML, CSS and JavaScript. No build step, no framework. Open `index.html` in a browser, or serve the folder with any
static host (GitHub Pages, Vercel, Netlify).

## What is in it

* A scroll-driven 3D story (three.js r128, vendored in `js/three.min.js`): a phone, a monitor, the Glidr cursor, Bluetooth
  range rings, a data stream and a globe. The phone and PC screens are live canvas textures drawn from the apps' own palette.
* Same design system as the apps: Midnight / Paper themes, four accents (Volt, Ember, Sky, Rose), Geist and Geist Mono,
  dot grid, spring motion. Theme and accent can be switched on the page and the 3D scene follows.
* Interactive demos: trackpad glow, shortcut presses, typing in any language, monitor picker, fps trace.
* Honors `prefers-reduced-motion` (no scroll choreography, no continuous animation) and falls back to a calm gradient when
  WebGL is unavailable.

## Files

| Path | What |
|------|------|
| `index.html` | content and section order (each `data-stage` section is one pose of the 3D scene) |
| `css/style.css` | tokens, layout, components |
| `js/ui.js` | theme and accent, reveals, card tilt, demos |
| `js/scene.js` | the 3D scene, textures, stage poses (`S` array) |
| `fonts/`, `img/` | Geist fonts (OFL) and the two app marks |

## Download links

The buttons point straight at the v1.0.0 release files, so the browser downloads them directly (same tab):

* Glidr: `Glidr-1.0.0.apk` and `GlidrCompanion.exe`
* Glidr Screen: `GlidrScreen-1.0.0.apk` and `GlidrScreen-1.0.0-win-x64.zip`

Links that open a GitHub page (repos, release lists) and the portfolio open in a new tab. When you publish a new version,
search `index.html` for `v1.0.0` and update the four file names.

Built by [Yashraj Shitole](https://yashrajshitole.in).
