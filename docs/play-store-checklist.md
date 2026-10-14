# Play Store readiness

What the code already satisfies, and what only you can do in Play Console.

## Done in the project
- `targetSdk` 37, `compileSdk` 37 (above Play's current target requirement), `minSdk` 29.
- Release builds are shrunk with R8; build the upload bundle with `gradlew.bat bundleRelease` (from `android/`).
- Only permissions the app uses: `BLUETOOTH_CONNECT`, legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` (maxSdk 30), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS` (Android 16 Live Updates), `RECORD_AUDIO` (Voice tab only), `VIBRATE`, `INTERNET`.
  No location, contacts, storage or camera permission. `INTERNET` is used only by the update check, which asks
  GitHub for the latest release; it is switched off automatically when Google Play installed the app (Play delivers
  updates and does not allow pointing users elsewhere).
- Runtime permissions are requested in context (Bluetooth when the user taps the connection pill, notifications when they turn keep awake on, microphone when they tap the mic in the Voice tab), and the app still works when notifications are denied.
- The foreground service is `connectedDevice`, started only by a user tap, with an ongoing notification and a Stop action.
- Edge-to-edge, system bar icons follow the theme, RTL supported, touch targets 48dp or larger, content descriptions on icon buttons.
- No ads, analytics, accounts or network access, so nothing leaves the device except HID input to the PC the user picks.
- `<uses-feature>` for Bluetooth and touchscreen so incompatible devices are filtered out.
- Fonts: Geist and Geist Mono (SIL Open Font License).

## You must do in Play Console
1. **Signing**: create an upload keystore and enrol in Play App Signing. Never commit the keystore.
2. **Real-device test**: HID Device does not work on emulators and some phone makers disable it. Test on the phones you care about; the listing should say "requires Bluetooth HID support".
3. **Privacy policy URL** (required because the app uses Bluetooth permissions and posts notifications). It can state: no data collected, shared or sold; input goes only to the user's chosen PC.
4. **Data safety form**: Glidr itself collects and shares nothing. Its only network request is the update check to GitHub (no personal data sent, off for Play installs). Voice uses the phone's own speech recogniser (`SpeechRecognizer`, set to prefer on-device); audio is handled by that system service, not by Glidr. Declare no data collected or shared by the app, and say so in the privacy policy. Settings stay on the device (DataStore, included in Android backup).
5. **Foreground service declaration**: type `connectedDevice`. Justification: keeps the Bluetooth link to the user's PC alive and runs the user-started Keep awake timer, with a visible notification and Stop control.
6. **Permissions declaration**: `BLUETOOTH_CONNECT` is used to connect to the user's paired PC; no scanning, so no `BLUETOOTH_SCAN` or location. `RECORD_AUDIO` is used only for the user-started Voice dictation and is optional (`microphone` feature is not required).
7. **Content rating questionnaire**, **target audience** (not designed for children), **app category** (Tools), **ads: none**.
8. **Store listing**: 512px icon, 1024x500 feature graphic, at least two phone screenshots, short and full description. Do not use "Microsoft" or "Windows" as part of the app name; saying "works with Windows PCs" in the description is fine.
9. **Launcher icon**: done (your glidr-icon set: adaptive and monochrome vectors). The 512px store icon is `docs/store/glidr-play-store-512.png`.
10. **Pre-launch report / internal testing track** first, then closed testing (new personal developer accounts need 12 testers for 14 days before production).

## Companion app
The Windows companion (`windows/`) is optional and separate from the Play listing; host the .exe yourself (for example a
GitHub release) and link to it in the description. The phone app only listens on a Bluetooth serial service for devices
already paired with it, so the Data safety answers do not change. The companion looks up cover art online (track title
and artist only, switchable in its tray menu); say so in its page and your privacy policy.

## Known limits to state in the listing
- Windows caches the HID descriptor at pairing; after an app update that changes it, forget the phone in Windows and pair again.
- Typing sends key codes through the layout chosen in Settings (US, UK, DE); Unicode text such as Devanagari cannot be typed over plain HID.
- Only one app can be the HID device at a time.
- Voice text is typed through the same key tables, so characters the chosen layout cannot produce are skipped (the app tells the user how many).
