# Glidr Companion (Windows)

Optional tray app that shows what is playing on your PC inside Glidr's Media screen: cover art, title, artist,
source app and a live progress bar. Mouse, keyboard and media keys work without it.

It reads Windows' own media session (the one the volume flyout shows, so Spotify, browsers, Media Player and so on)
and sends it to the phone over Bluetooth. There is no account. It goes online for two things only: sharper cover
art (below) and the update check.

## Use
1. Pair the phone with the PC and connect in Glidr as usual.
2. Run `GlidrCompanion.exe`. A Glidr icon appears in the tray; its menu shows the link status, "Start with Windows",
   "Check for updates", "Open log" and Quit.
3. Open the Media tab in Glidr.

Needs Windows 10 (2004) or newer and the .NET 8 Desktop Runtime.

## Cover art quality
Windows gives browsers only a 120-256px thumbnail, which looks soft on a phone. So the companion looks the track up
for sharper art: first the active browser tab's YouTube address, then a YouTube search accepted only when both title
and channel match, then Apple's music catalogue for songs (up to 1400x1400). YouTube thumbnails arrive at up to
1280x720 and are shown in their own shape.

Only the track title and artist are sent. It runs on the PC, never on the
phone, and you can switch it off with "High-quality cover art" in the tray menu (the phone then gets what Windows
provides). Spotify, Apple Music and most desktop players already supply large covers.

## Updates
At start (after 15 s) and once a day the companion asks GitHub for the latest Glidr release (`api.github.com`,
repo yashraj-shitole/Glidr). When it is newer, a balloon appears and the tray menu offers "Download update"; nothing is
installed automatically. The same release (it carries the APK too) is passed to the phone over Bluetooth, so the phone
learns about its own update even when it cannot check itself. Publish both files on one release tagged `vX.Y.Z`, and
bump `<Version>` in the .csproj and `versionName` in the app to match.

## Build
Needs the .NET 8 SDK (`winget install Microsoft.DotNet.SDK.8`).

```
cd windows\GlidrCompanion
dotnet publish -c Release -r win-x64 --self-contained false -p:PublishSingleFile=true -o ..\dist
```

For a copy that runs on PCs without .NET, use `--self-contained true` (about 150 MB).

## Protocol
The phone runs a Bluetooth serial (RFCOMM) server with service id `7b2f3c10-6a1e-4c8e-9d52-2f6b1a9c4e01`
(`CompanionLink.kt` in the app, `PhoneLink.cs` here). The PC connects to a paired phone and sends one JSON object per
line: `{"t":"np","title","artist","album","app","playing","pos","dur","art"?}` with times in milliseconds and
`art` a base64 JPEG sent only when the track changes, or `{"t":"none"}` when nothing is playing.
`{"t":"release","v","apk","page"}` reports the latest GitHub release (links must start with `https://github.com/`).
