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

## Publishing (GitHub Pages)

The live site is served from the **Glidr** repo, branch `gh-pages`: https://yashraj-shitole.github.io/Glidr/

This repo is the source. To publish a change, commit here, then push the same commits to that branch (the app's `main` is never touched):

```
git push origin main
git push https://github.com/yashraj-shitole/Glidr.git main:gh-pages
```

Everything uses relative paths, so it works under the `/Glidr/` sub-path and when opened straight from disk. `media/` holds the
launch film (faststart MP4, 22 MB, loads only when played) and its poster.

## Download links

Download buttons always give the newest release, with nothing to edit when you publish one:

* Each button starts as the repo's `releases/latest` page (opens in a new tab), so it is correct even without JavaScript.
* On load, `js/ui.js` asks the GitHub API for the latest release of each repo and turns the button into the direct file link
  (same tab), then shows the version tag. Phone: the `.apk`. PC: an `.exe` if the release has one, otherwise the `win` `.zip`.
* The answer is cached for 15 minutes per visitor (the unauthenticated API allows 60 requests an hour per IP). If the lookup
  fails, the buttons stay on `releases/latest`.

To add a button, give the link `data-repo="owner/name"` and `data-asset="apk"` or `"pc"`. Links to GitHub pages and the
portfolio open in a new tab.

Built by [Yashraj Shitole](https://yashrajshitole.in).
