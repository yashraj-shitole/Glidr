# Glidr

Turn your Android phone into a Bluetooth mouse, keyboard, media remote and voice-to-text input for a Windows PC.
It uses the Bluetooth HID Device profile, so the PC needs no software for basic control.

| Part | Folder | Tech |
|------|--------|------|
| Phone app | `android/` | Kotlin, Compose, Bluetooth HID Device (`BluetoothHidDevice`); single module `:app` |
| PC companion (tray, optional) | `windows/` | .NET 8 tray app, Windows media session, Bluetooth RFCOMM |
| Docs and design | `docs/` | [Play Store checklist](docs/play-store-checklist.md), store icons, [design mockup](docs/design/) |

## Download (v2.0.0)
- **Phone app:** [Glidr-2.0.0.apk](https://github.com/yashraj-shitole/Glidr/releases/download/v2.0.0/Glidr-2.0.0.apk). Android 10 or newer; allow installing from your browser or file manager.
- **PC app (optional):** [GlidrCompanion.exe](https://github.com/yashraj-shitole/Glidr/releases/download/v2.0.0/GlidrCompanion.exe). Shows what is playing on the PC in Glidr's Media screen. Needs the .NET 8 Desktop Runtime. You can also install it from the phone: Settings, Sync media, Install PC app.

All downloads are on the [Releases page](https://github.com/yashraj-shitole/Glidr/releases). The APK is signed with a debug key.

## Use it

1. **Phone:** install the app (or build it: `cd android && gradlew.bat installDebug`).
2. Tap the connection pill and pair the phone with the PC from Windows Bluetooth settings.
3. Use the Pad, the keyboard sheet and the Media screen.
4. **PC (optional):** run `GlidrCompanion.exe` for now-playing info on the phone (see [windows/README.md](windows/README.md)).

## Features

### Phone app
* **Trackpad:** one finger moves the pointer, tap clicks, two-finger tap right-clicks, two fingers scroll,
  press and hold to drag. Scroll strip on the right edge, left and right mouse buttons under the pad.
* Pointer speed, acceleration curve (Off / Low / Med / High), scroll speed and smoothing.
* **Keyboard sheet:** Keys with a 123 symbols layer, Windows shortcuts and Voice dictation (the phone's speech
  recogniser types for you). US, UK and DE keyboard layouts.
* **Media:** media keys, a horizontal volume dial and, with the companion, a live now-playing card with cover art.
* **Keep awake:** nudges the mouse a few pixels on a timer (15 s to 2 min, optional auto-stop) so the PC does not sleep.
* Runs as a foreground service with an ongoing notification (Pause / Nudge now / Stop), promoted to an Android 16
  Live Update. Auto-reconnect, forget PC.
* Midnight (dark) and Paper (light) themes, four accents, haptics with adjustable intensity, Reduce motion.
* Update banner when a newer release is on GitHub (off when installed from Google Play).

### PC companion (Windows tray, optional)
* Reads the Windows media session (Spotify, browsers, Media Player...) and sends the playing track to the phone over Bluetooth.
* Sharper cover art from YouTube and Apple's catalogue (switchable), start with Windows, open log.
* Checks GitHub for updates and passes the latest release on to the phone.

## Updates

The phone (Settings > Updates) and the companion ask GitHub for the latest release of **yashraj-shitole/Glidr**.
A newer version shows a banner or tray balloon with a Download link; nothing installs itself.

To publish: bump `versionName`/`versionCode` in `android/app/build.gradle.kts` and `<Version>` in
`windows/GlidrCompanion/GlidrCompanion.csproj`, then create a GitHub release tagged `vX.Y.Z` with the `.apk` and
`GlidrCompanion.exe` attached.

## Build and test

```
android:  gradlew.bat assembleDebug               phone app
          gradlew.bat testDebugUnitTest           unit tests
          gradlew.bat connectedDebugAndroidTest   Compose UI tests (needs a device or emulator)
windows:  see windows/README.md (dotnet publish)
```

Open the `android/` folder in Android Studio.

## Honest limits

* Bluetooth HID only works on a real phone, and some phone makers disable the HID Device profile.
* Windows caches the HID descriptor at pairing; after an update that changes it, remove the phone in Windows and pair again.
* Typing sends key codes through the chosen layout (US, UK, DE), so Unicode text such as Devanagari cannot be typed.
* Only one app can be the HID device at a time.
