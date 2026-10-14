# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Glidr

Android app (Kotlin, Jetpack Compose) that turns the phone into a Bluetooth mouse, keyboard, media remote and voice-to-text input for a Windows PC, using the Bluetooth HID Device profile (`BluetoothHidDevice`). No PC-side software in v1.

## Current state
Implemented: Compose theme (Midnight/Paper, four accents, Geist fonts), HID layer (descriptor, encoders, US/UK/DE key tables, `HidEngine`, `BluetoothHidTransport`), motion pipeline, Pad screen (gestures, scroll strip, mouse buttons), keyboard sheet (Keys with a 123 symbols layer, Shortcuts, Voice), Media, Settings (incl. acceleration curve, haptics toggles, auto-reconnect, forget PC), `GlidrService` (foreground service, type `connectedDevice`: keeps the connection alive, runs keep-awake, ongoing notification with progress and Pause / Nudge now / Stop, requests Android 16 Live Update promotion), `SttEngine` with the Android `SpeechRecognizer` engine, DataStore settings, launcher icon. Tests: unit tests in `app/src/test`, Compose UI tests in `app/src/androidTest` (pass on the emulator and a real Android 16 phone). Optional Windows companion (`windows/GlidrCompanion`, .NET 8 tray app, see `windows/README.md`) sends the playing track to the phone over a Bluetooth RFCOMM server in `CompanionLink`; the Media screen shows it live with a horizontal volume dial. The phone app has the internet permission for one thing only: asking GitHub (`api.github.com`, repo yashraj-shitole/Glidr) whether a newer release exists (`update/Updates.kt`, Settings > Updates). The companion also checks and forwards the latest release to the phone over Bluetooth (`{"t":"release"}`). Both only notify and open the download; when installed from Google Play the check is off. The companion additionally goes online for sharper cover art. Not done: on-device Whisper engine, hand-built in-app island and lock-screen mock surfaces (the notification / Live Update replaces them), and real-PC verification of Bluetooth input.

Build config: single module `:app`, package `com.yashraj.glidr`, version catalog in `gradle/libs.versions.toml`, AGP 9.4.1 with built-in Kotlin 2.2.10 plus the Compose compiler plugin, compileSdk/targetSdk 37, minSdk 29 (above the 28 floor, so ask before lowering). UI uses compose foundation only (no material3). Play Store steps are in `docs/play-store-checklist.md`. Commit as you go. Remote: github.com/yashraj-shitole/Glidr (public); APK and PC exe are published as GitHub release assets, not committed.

## Commands
- Build: `gradlew.bat assembleDebug` (Windows) / `./gradlew assembleDebug`
- Unit tests: `gradlew.bat testDebugUnitTest`
- UI tests (needs a device or emulator): `gradlew.bat connectedDebugAndroidTest` (runs on every attached device, including a real phone)
- Single unit test: `gradlew.bat testDebugUnitTest --tests "com.yashraj.glidr.SomeTest.someMethod"`
- Lint: `gradlew.bat lintDebug`
- Install on the connected phone: `gradlew.bat installDebug`
- Device logs: `adb logcat -s Glidr` (use `Log.d("Glidr", ...)` tags)

## Hard constraints (do not "fix" these)
- minSdk must be 28 or higher: `BluetoothHidDevice` does not exist below API 28.
- HID Device only works on a physical phone. An emulator or unit test cannot prove Bluetooth behaviour. Never say Bluetooth works unless I report a real-device result; instead tell me exactly what to test.
- Windows caches the HID report descriptor at pairing. After ANY descriptor change, tell me to remove the device in Windows Bluetooth settings and pair again.
- HID sends key codes, not characters. Typing goes through per-layout tables (US, UK, DE). Unicode/Devanagari cannot be typed over plain HID (v2: companion agent + clipboard).
- Only one app can be registered as the HID device at a time.
- Mouse hot path must be allocation-free: reuse ByteArrays, batch reports at ~125 Hz, accumulate fractional deltas so slow movement is not lost.

## Architecture (single module)
- `ui/`: Compose screens (Pad, Media, Settings) and components. ViewModel + StateFlow, unidirectional data flow.
- `input/`: gesture recognizer, then motion pipeline (sensitivity, acceleration curve, smoothing).
- `hid/`: `HidTransport` interface (`sendMouse`, `sendKeyboard`, `sendConsumer`), `BluetoothHidTransport`, report descriptor, report encoders. All HID access goes through the interface so the engine is testable and a Wi-Fi transport can be added later.
- `service/`: foreground service (types `connectedDevice` and `microphone`) owns the HID connection and the keep-awake scheduler. Ongoing notification with Pause / Nudge now / Stop. Promote to an Android 16 Live Update when available.
- `data/`: DataStore settings.
- `voice/`: `SttEngine` interface; Android `SpeechRecognizer` first, on-device Whisper later.
- Connection states: Unsupported, BluetoothOff, Registering, Ready, Connecting, Connected, Reconnecting. Every state is shown in the status pill.

## Keep awake
A "nudge" is a tiny relative mouse move and back (a few px). It is skipped while the user is touching the pad. Runs from the foreground service, interval 15 s / 30 s / 1 min / 2 min, optional auto-stop. The whole feature (button, notification, island) can be switched off in Settings.

## Design
Mockup screens: Trackpad, Keep awake on, Keyboard, Windows shortcuts, Media, Settings, Paper (light) theme, Notification shade, Lock-screen island. Reference screenshots live in `docs/design/` when present; match them. The interactive mockup is `Phone Mouse UI – 1 · Trackpad.html` in the repo root: a bundled React page covering Pad, Media, Settings, keep-awake island and notification cards. Its template can be unpacked from the `__bundler/template` and `__bundler/manifest` script tags, and it holds the exact tokens, copy, spacing and behaviour (default sensitivity 1.6×, scroll speed 1.2×, nudge intervals 15/30/60/120 s, acceleration Off/Low/Med/High).
- Default theme Midnight: bg `#000000`, surface 1/2/3 `#0C0D0F` / `#16171A` / `#222328`, text `#F3F2EE`, dim text `#9A9CA6`.
- Paper theme: bg `#F1F2F4`, surface 1/2/3 `#FFFFFF` / `#E7E8EB` / `#D8DADF`, text `#141517`, dim `#565963`.
- Accent Volt `#D4FF3F` with ink `#0B0C0A` on top; on Paper use `#4A6000` for accent text. Other accents: Ember `#FF8A5B`, Sky `#7DB2FF`, Rose `#FF7FB0`.
- Fonts: Geist and Geist Mono in `res/font`.
- Motion: springs, not tweens. Honour the system "remove animations" setting and the in-app Reduce motion switch.
- Haptic vocabulary: click (taps, keys), thud (right click), tick (scroll detents, modifier latch), confirm (connected, sent), reject (errors). Use `VibrationEffect.Composition` when supported (API 30+), otherwise predefined effects (API 29+) or `performHapticFeedback`. Always respect the haptics intensity setting.

## Permissions
`BLUETOOTH_CONNECT` (runtime, API 31+), `BLUETOOTH` / `BLUETOOTH_ADMIN` (maxSdk 30), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS` (runtime, API 33+), `POST_PROMOTED_NOTIFICATIONS`, `RECORD_AUDIO` (runtime, Voice tab only), `VIBRATE`, `INTERNET` (update check only). No microphone foreground service type: dictation runs while the app is open.

## Workflow
- Small steps. After each, run `assembleDebug` and the unit tests, and show me the output.
- Commit after each working step with a clear message.
- Do not add libraries without asking. Prefer androidx.
- Ask before changing the package name, minSdk or the HID descriptor.
