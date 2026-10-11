# Glidr

Turn your Android phone into a Bluetooth mouse, keyboard, media remote and voice-to-text input for a Windows PC. It uses the Bluetooth HID Device profile, so the PC needs no software for basic control.

## Download (v1.0.0)
- **Phone app:** [Glidr-1.0.0.apk](https://github.com/yashraj-shitole/Glidr/releases/download/v1.0.0/Glidr-1.0.0.apk). Android 10 or newer; allow installing from your browser or file manager.
- **PC app (optional):** [GlidrCompanion.exe](https://github.com/yashraj-shitole/Glidr/releases/download/v1.0.0/GlidrCompanion.exe). Shows what is playing on the PC in Glidr's Media screen. Needs the .NET 8 Desktop Runtime. You can also install it from the phone: Settings, Sync media, Install PC app.

All downloads are on the [Releases page](https://github.com/yashraj-shitole/Glidr/releases). The APK is signed with a debug key.

## Build
`gradlew.bat assembleDebug` for the phone app; see [windows/README.md](windows/README.md) for the PC app.
