# Rumput Vision

A native Android client for a self-hosted [Frigate NVR](https://frigate.video).
Built with Kotlin, Jetpack Compose and Material 3.

**Status:** all six planned phases are complete (version 0.1.0). See
[docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) for what is tested, what was
verified on real hardware, and what is still open.

## Download

Get the latest APK from the
[Releases page](https://github.com/Adib-Fathurrohman/RumputVision/releases/latest).

1. Download `RumputVision-<version>.apk` on your phone.
2. Open it and allow installing from this source when Android asks.
3. Updates from the Releases page install over the previous version and keep your settings.

Each release lists the APK's SHA-256 checksum, so you can check the download:

```bash
shasum -a 256 RumputVision-0.1.0.apk
```

**Requirements:** Android 10 (API 29) or newer, and a Frigate server. The app was developed
against Frigate 0.18.2 with its built-in go2rtc.

## Features

- **Servers:** one or more server profiles, each with a local URL (home network) and a
  remote URL (for example a Tailscale HTTPS address). The app picks the right one for the
  current network and switches when the network changes. It includes a connection test.
- **Home:** connection status, live camera previews, server health (version, uptime,
  detectors, storage) and recent events.
- **Cameras:** camera list with status, and a detail page with health and configuration.
- **Live view:** low-latency streaming through go2rtc (MSE over WebSocket), with sub and
  main stream choice, mute and full screen. A live grid shows up to four cameras.
- **Events:** a timeline filtered by camera, label and day, with snapshots and clip playback.
  Saved events stay visible offline.
- **Recordings:** browse by camera, day and hour, and play with seeking and the real
  wall-clock time.
- **About and diagnostics:** connection details, recent warnings, and a copyable report
  with passwords, tokens, the username and server addresses left out.

## Security

- Passwords are only ever sent over HTTPS with normal certificate checks. They are stored
  encrypted with a key from the Android Keystore and are never backed up.
- Plain HTTP is allowed only to private network addresses, and without credentials.
- Nothing is streamed or polled in the background. Images and video are not written to disk.

A Frigate server that answers without a login gives admin access to anyone who can reach it,
and the app warns about this. Read [docs/SECURITY.md](docs/SECURITY.md) before exposing a
server beyond your home network.

## Known limitations

- No push notifications yet (they need a separate relay service).
- No clip download or export.
- Signing in to Frigate's authenticated port (8971) is tested only against a test server.
- Streaming over Tailscale on mobile data has not been verified yet.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Frigate API reference](docs/FRIGATE_API.md), verified against Frigate 0.18.2
- [Streaming design and measurements](docs/STREAMING.md)
- [Security audit and app security design](docs/SECURITY.md)
- [Development, tests and release builds](docs/DEVELOPMENT.md)

## Build from source

Requires JDK 25 and the Android SDK (platform 37). See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Release builds are signed with a key that is not in this repository; see
"Release build" in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
