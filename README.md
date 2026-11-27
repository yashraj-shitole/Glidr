# Glidr + Glidr Screen: launch page

Landing page for the joint launch of [Glidr](https://github.com/yashraj-shitole/Glidr) (phone as a Bluetooth mouse,
keyboard and remote) and [Glidr Screen](https://github.com/yashraj-shitole/GlidrScreen) (your Windows PC on your phone).

Plain HTML, CSS and JavaScript. No build step, no framework. Open `index.html` in a browser, or serve the folder with any
static host (GitHub Pages, Vercel, Netlify).

## The idea: this page has a remote

The page is laid out as a desktop (flat wallpaper per chapter, draggable windows, a taskbar for navigation) with a drawn
phone that really works. Visitors try both apps by using them on the page itself.

* **Glidr mode:** the trackpad moves a cursor over the page; tap clicks, two fingers scroll, two-finger tap opens a menu,
  tap-then-drag or holding *Left* drags windows. Scroll wheel and shortcut keys (Copy, Paste, Switch, Desktop) act on the page.
* **Screen mode:** a live miniature of the page on the phone; tapping it clicks that spot (Direct touch).
* **Five tricks** (move, click, scroll, shortcut, Screen) are tracked and end in a download prompt. Nothing is gated by them.
* **Range test:** a distance slider showing where Bluetooth stops and Glidr Screen carries on.
* From 1180 px wide the remote is open beside the page; below that it is a bottom sheet opened from the taskbar.
* The remote is optional: the page reads, scrolls and downloads normally without it. The trackpad also takes arrow keys,
  Enter and Page Up / Down. `prefers-reduced-motion` turns animation off.
* Paper / Midnight windows and the apps' four accents; Geist and Geist Mono.

## Files

| Path | What |
|------|------|
| `index.html` | content; each `.scene` section is one chapter (`data-wall` picks its wallpaper) |
| `css/style.css` | tokens, wallpapers, windows, taskbar, remote, responsive rules |
| `js/ui.js` | theme and accent, chapters, window dragging, demos, film, download links |
| `js/remote.js` | the working remote: gestures, pointer, shortcut keys, Screen mirror |
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
