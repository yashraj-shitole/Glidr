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

## Links to update on release

Download buttons point at GitHub Releases: `Glidr-1.0.0.apk` and `GlidrCompanion.exe` on the Glidr repo, and
`releases/latest` on the Glidr Screen repo (it has no release yet, so those buttons open an empty releases page until one
is published).

Built by [Yashraj Shitole](https://yashrajshitole.in).
