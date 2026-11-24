# Glidr feature ideas

Brainstormed by four Sonnet agents (input and gestures, PC control and productivity, connectivity and platform,
personalisation and polish) and merged here. Effort: S under a day, M a few days, L a week or more.
Nothing below is built or verified. Anything that touches Bluetooth needs a real PC to test.

Duplicates across the four reports are merged. The number in brackets is how many agents proposed the idea.

## Tier 1: quick wins (no descriptor change, no new permission)

| # | Feature | Effort | Impact | Notes |
|---|---------|--------|--------|-------|
| 1 | **Lock-screen remote**: Play/Pause, Prev/Next, Vol-/Vol+ actions on the ongoing notification while connected [2] | S | High | Today the actions exist only during keep awake. Use plain actions, not MediaStyle. |
| 2 | **Inertial fling scroll** after two-finger and strip scrolls | S-M | High | Whole detents only. Tune on a real PC. A tap cancels it. |
| 3 | **Presenter mode**: big Next/Prev, elapsed timer with haptic cues, F5 / Esc / B / W, hardware volume keys as slide advance [3] | S-M | High | Volume keys work only while the app is in the foreground. |
| 4 | **Meeting panel**: mute, camera, raise hand, leave, share, per app preset (Teams, Zoom, Meet) | S | High | Shortcuts differ by app and version. |
| 5 | **Home-theatre remote**: d-pad, Back, fullscreen, subtitles, seek, per player presets (VLC, YouTube, Kodi) | S-M | High | Key bindings differ per player. |
| 6 | **Pad modifier bar and edit bar**: Ctrl/Shift/Alt latch above the mouse buttons, Select all / Copy / Paste / Undo, swipe on Space to move the caret | S | Medium | Auto-release after one click so a modifier is never stuck. |
| 7 | **Precision and accessibility basics**: drag lock, adjustable tap and hold timing, slow-down key, tremor filter, high-contrast theme [2] | S-M | Medium-high | Hot path must stay allocation-free. Hide extras under Advanced. |
| 8 | **Quick Settings tile and launcher shortcuts** (Connect, Keep awake, Media) [2] | S-M | High | Tile state must follow the transport state. |
| 9 | **Window switcher, HID fallback**: Win+1..9, Win+Ctrl+Arrow desktops, Alt+Tab hold and tap | S | Medium | A window list with icons needs the companion (Tier 3). |

## Tier 2: medium projects

| # | Feature | Effort | Impact | Notes |
|---|---------|--------|--------|-------|
| 10 | **Custom shortcut and macro deck** with profiles of tiles, sequences with delays, Win+R run entries, text snippets, JSON export [3] | M | High | The 18 built-ins become presets. Win+R timing must be editable. |
| 11 | **Typing box**: use Gboard (swipe, autocorrect) and type on Send; "Type my clipboard"; Android share target [2] | S-M | High | Characters outside the layout are skipped, with a count shown. |
| 12 | **Live dictation and voice commands**: stream stable words, "new line", "delete that", "press enter" [2] | M | High | Correcting partials sends backspaces, so commit only stable words. |
| 13 | **Connection doctor**: count failures and say why (Bluetooth off, unpaired, Windows forgot the phone, HID slot taken), with a one-tap fix | M | High | Today three failed connects silently return to Ready. Needs a real PC to see real failures. |
| 14 | **Background survival**: persist the connection wish, sticky service while wanted, OEM battery prompt, slow retry back-off | M | Medium | Hard to test without long idle runs. |
| 15 | **Profiles** (Desk, Couch, Presenting) bundling sensitivity, scroll, handedness, haptics, keep-awake | M | High | Factor the profile fields out of the flat `Settings` first. |
| 16 | **Multiple PCs** with nicknames and one-tap switching from the status pill [2] | M | Medium-high | Each PC can remember its profile. Needs two PCs to test. |
| 17 | **More gestures and a remap screen**: browser back and forward, four-finger virtual desktops, three-finger tap, double-tap-drag | M | Medium-high | Needs the same decide-early approach as the scroll and pinch split. |
| 18 | **Interactive practice mode** on a sandbox pad with ticks per gesture | M | Medium-high | Reuses the Settings try-it pad. Works on an emulator. |
| 19 | **Localisation**: German, Hindi, Spanish UI, per-app language on API 33+ | M | Medium-high | Check long German strings against the equal-height tabs. |
| 20 | **Feedback and diagnostics export**, manual and opt-in, shown before sending | S-M | Medium | Must not break the no-analytics promise. |

## Tier 3: large or risky

| # | Feature | Effort | Impact | Notes |
|---|---------|--------|--------|-------|
| 21 | **Two-way companion channel**: clipboard bridge (type Unicode and Devanagari), PC actions (lock, sleep, volume %), window list, PC status in the notification [3] | L | High | Companion currently ignores bytes from the phone. Needs an allowlist, the paired phone only, no arbitrary shell. Untestable without a real PC. |
| 22 | **Auto-connect when the PC wakes** via Companion Device Manager | M-L | High | New permission and an association dialog. Varies by OEM. |
| 23 | **Other targets**: Mac, iPad, Chromebook, Android TV, Linux; TV remote mode | S-M | Medium | Wording changes from "PC"; each target needs its own hardware test. |
| 24 | **Air mouse** with the gyroscope | M | Medium | Relative HID, so drift and no absolute aim. |
| 25 | **LAN transport** through the companion | L | Medium | Needs network permission, encryption and a pairing secret. Conflicts with the "internet for updates only" stance. |
| 26 | **Absolute touch or Windows Precision Touchpad descriptor** | L | High if it works | Descriptor change forces re-pairing, so CLAUDE.md says ask first. `BluetoothHidDevice` may not support PTP feature reports. Spike only. |
| 27 | **Delight layer**: key sounds (off by default), pairing confetti, custom accent picker | S | Low-medium | Honour reduce motion. |
| 28 | **Store readiness**: rate and share, screenshot tests, privacy policy page | S | Medium | A Play review library would be a new dependency, so ask first. |

## Suggested order

1. **Next release:** 1, 2, 3, 8, 6 (small, visible, no risk to pairing).
2. **After that:** 13 and 14 (reliability), then 10 and 15 (make it yours).
3. **Before a Play launch:** 18, 19, 20, 28.
4. **Needs a decision first:** 21, 22, 25, 26, and any new library.

## Caveats

- The agents read the code, but their claims about it are unchecked. Examples: the companion discarding phone-to-PC bytes, `wantConnected` living only in memory, and which device classes `bondedHosts()` filters out.
- Their claims about what users want ("most requested", "biggest gap") are opinions, not data.
