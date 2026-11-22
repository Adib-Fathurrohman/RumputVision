# Implementation status

Legend: **Not started** · **In progress** · **Implemented** (code exists,
not automatically tested) · **Tested** (automated test executed and passing)
· **Blocked**

Last updated: 2026-11-22 (Phase 6). Test run: `./gradlew :app:testDebugUnitTest`:
151 tests, 0 failures, 1 skipped (the opt-in live test). Instrumented:
`./gradlew :app:connectedDebugAndroidTest` on the emulator (API 36): 4 tests, 0 failures.
Lint: 0 errors (1 warning: the Compose BOM held back on purpose).
Manual end-to-end checks on the emulator (API 36.1) and an Infinix X6731B
(Android 14) against the real Frigate 0.18.2 over the LAN.

## Phase 0 — Audit and discovery: done

| Item | Status | Notes |
|---|---|---|
| Repository audit | Done | Repository was empty. |
| Toolchain audit | Done | See `DEVELOPMENT.md`. |
| Frigate version | Done | 0.18.2-cfa08c5, go2rtc 1.9.14. |
| API verification | Done | OpenAPI spec saved; key endpoints called. See `FRIGATE_API.md`. |
| Streaming investigation | Done | MSE WebSocket verified; see `STREAMING.md`. |
| Security audit | Done | Three critical findings; see `SECURITY.md`. |

## Phase 1 — Foundation: done

| Feature | Status | Notes |
|---|---|---|
| Gradle project, version catalog, wrapper | Tested | Debug + release (R8) builds pass; lint 0 errors. |
| Hilt DI | Tested | Full Hilt graph exercised by `AppNavigationTest`. |
| Navigation (5 tabs, onboarding, editor, about) | Tested | `AppNavigationTest`. Bottom bar on phones, rail on wide screens (rail not visually checked). |
| Design system (theme, tokens, state components, status indicators) | Implemented | Visually checked on emulator; no screenshot tests. |
| Server profile model | Tested | |
| URL and transport security policy | Tested | `ServerUrlPolicyTest`. |
| Profile create/edit/delete/activate | Tested | Use case, ViewModel, repository (real Room) tests. |
| Encrypted credential storage | Tested | Store logic with a fake cipher (JVM), and the real Keystore cipher plus store in the instrumented `KeystoreCredentialStoreTest` (emulator). StrongBox path not exercised. |
| App preferences (DataStore) | Tested | |
| Log redaction | Tested | |
| Backup exclusion | Implemented | Manifest + rules; not tested. |

## Phase 2 — Frigate connection: done (with one blocked verification)

| Feature | Status | Notes |
|---|---|---|
| OkHttp/Retrofit client, DTOs from the real 0.18.2 responses | Tested | Sanitized recorded fixtures; live test against the real server passed. |
| Transport guard (no credentials/login over HTTP, no HTTP to public hosts, no redirects) | Tested | |
| Login, Bearer token, refresh via re-issued cookie, re-auth on 401, logout | Tested against an HTTPS test server | **Blocked** for real-server verification until port 8971 is reachable over HTTPS (SECURITY.md S1/S3). |
| Encrypted storage of token and optional password | Tested | Including the real Keystore (instrumented test). |
| Endpoint selection: local/remote, Wi-Fi vs mobile order, debounced network changes | Tested | Verified on emulator: airplane mode → "No network", then automatic reconnect. **Remote/Tailscale path not tried on a real network** (no remote URL configured yet). |
| Connection state machine (connected, unauthorized reasons, unreachable reasons) | Tested | |
| Anonymous-admin (auth bypass) detection | Tested + verified on the real server | Shown on Home and in the connection test. |
| Connection test in profile editor | Tested + verified on the real server | |
| Sign-in card on Home, "Forget saved sign-in" | Tested (ViewModel/session) | UI not exercised against a real auth server (blocked as above). |
| Camera discovery (dynamic list), metadata, Room cache (DB v2 + migration) | Tested + verified on the real server | 4 cameras discovered; migration ran on the emulator's Phase 1 data. |
| Camera status (online/offline/disabled/unknown, last seen) | Tested + verified on the real server | Cached lists are labelled; stale data (>2 min) shows "unknown". |
| Server status (version, update, uptime, detectors, storage) | Tested + verified on the real server | |
| Refresh cadence | Tested | 30 s only while Home is visible and connected; nothing in the background. |
| Realtime `/ws` | Not started | Moved to the events phase (Phase 5). |

## Phase 3 — Dashboard and cameras: done

| Feature | Status | Notes |
|---|---|---|
| Authenticated image loading (Coil via session client, memory-only cache) | Tested | Token sent only to the connected server (`FrigateCameraMediaTest`). |
| `CameraSnapshot`, `CameraPreviewCard` components | Implemented + verified on the real server | Flicker-free refresh; non-live frames dimmed and labelled. No screenshot tests. |
| Dashboard: preview grid (adaptive columns), connection header, server section | Tested (ViewModel, navigation) + verified on the real server | 2 columns on phone portrait, 4 in landscape. |
| Lifecycle-aware snapshot refresh (grid 10 s, list 30 s, detail 4 s) | Tested + verified on the real server | Request log showed exact cadence, and zero requests in the background. |
| Cameras tab: thumbnails, status, tap to detail | Tested (navigation) + verified on the real server | |
| Camera detail: large frame, health (fps, quality, reconnects, stalls, last seen), configuration, streams | Tested (ViewModel, navigation) + verified on the real server | The Live view button is visible but disabled until Phase 4. |
| Navigation rail for short/wide windows | Implemented + verified on emulator (landscape) | |

## Phase 4 — Live streaming: done (remote/Tailscale and latency not yet measured)

Verified on an Infinix X6731B (Android 14) against the real server. Details in `STREAMING.md`.

| Feature | Status | Notes |
|---|---|---|
| MSE WebSocket data source | Tested | Real WebSocket server tests: handshake, data, errors, 401, stall, fall-behind, abort, close. |
| Stream discovery (sub / main restream) | Tested | |
| Media3 player: buffering, catch-up, retry/backoff, rebuffer detection | Tested (logic) + verified on device | |
| Audio: video-only while muted; fallback when FLAC fails | Verified on device | Unit tests cover codec offers; the fallback path is device-verified only. |
| Single-camera live view (quality, mute, switch, fullscreen, landscape) | Tested (ViewModel, navigation) + verified on device | Main 1440p H.264 and sub streams played; Main rebuffers on this phone's Wi-Fi, and the "Switch to Sub" hint appeared. |
| Stream stops on background/leave | Verified on device | App ~1 s; go2rtc drops the consumer ~5 s later. |
| 2×2 live grid with stream limit and still fallback | Tested (ViewModel) + verified on device | All four tiles live, 3.22 Mbit/s delivered, 4 stalls in 21 s with the SMOOTH buffer profile on the phone's congested 2.4 GHz link. |
| Low-latency WifiLock | Removed | A/B on the device showed no benefit. |
| Tailscale remote streaming | Not verified | |

## Phase 5 — Events and recordings: done (export not implemented)

Verified on the emulator against the real server (Frigate 0.18.2), 2026-11-22.

| Feature | Status | Notes |
|---|---|---|
| Event feed: day-grouped timeline, thumbnails, paging | Tested + verified on server | Pages of 50 via `before`. |
| Filters: camera, label (shown when > 1 label), Today / 24 h / 7 days / a picked day | Tested | |
| Offline: cached events (Room v4), labelled | Tested | Metadata only; no images are written to disk. |
| Event detail: snapshot, metadata, play recording, open live view | Tested + verified on server | |
| Event clip playback (VOD window with ±5 s padding) | Tested + verified on server | |
| Recordings: camera → day → hour, recorded time per hour | Tested + verified on server | |
| Playback: HLS VOD, play/pause, seek, ±10 s, wall-clock time, segment strip | Tested + verified on server | Wall clock matched the camera overlay to the second. |
| Expired / missing recordings | Implemented | A 404 shows a clear message; verified with curl, not yet seen in the app. |
| Playback opened offline or while connecting | Tested + verified on emulator | Fixed in Phase 6: it used to stay black. Now it shows progress or "cannot be reached" with Retry, and loads by itself once connected. |
| Realtime `/ws` (backoff, dedupe, only while observed) | Tested + verified on server | Server log shows the app's `/ws` opening and closing with screen use. |
| Recent events on Home | Implemented + verified on server | |
| Download / export clips | Not started | |

## Phase 6 — Reliability and release: done (phone and Tailscale re-checks pending)

| Item | Status | Notes |
|---|---|---|
| Signed R8 release build | Verified on emulator | 5.3 MB. Full walkthrough (add server, test connection, Home, live grid, live view, event, recording playback) with zero crashes and zero app log lines. Signed with a throwaway key; the real key is the owner's (see `DEVELOPMENT.md`). |
| Keystore instrumented test | Tested | `KeystoreCredentialStoreTest`, 4 tests on the emulator. |
| About and diagnostics screen | Tested (report) + verified on emulator | Connection, network, Frigate version, cameras online, recent warnings, **Copy diagnostics** with addresses, username and secrets left out (`DiagnosticsReportTest`). |
| In-memory warning log | Tested | Last 50 warnings/errors, redacted; also in release, never on disk or logcat. Connection failures and offline now record a warning. |
| Offline walkthrough (airplane mode) | Verified on emulator | All five tabs, camera detail, event detail and playback show cached data or a clear message; no crash. Two fixes: playback waits for the server (above) and camera detail shows a single offline notice and no "refreshed every few seconds" caption while offline. |
| Startup and live latency | Measured on emulator | Cold start 300–415 ms; Sub ≈ 1.5 s glass-to-glass; details in `STREAMING.md`. Not yet repeated on the phone. |
| "In-app event updates" switch | Tested + verified on server | Was ignored before; see Notifications. |
| Security review | Done | `SECURITY.md` "Phase 6 release review". Removed the unused `WAKE_LOCK` permission merged in by media3. |
| Tailscale / mobile data | Not verified | Needs the phone on mobile data with Tailscale on. |
| Battery and thermal over a long session | Not verified | |

## Notifications

| Feature | Status | Notes |
|---|---|---|
| Notification preferences | Tested | Stored in DataStore. The push toggle is visibly disabled. |
| In-app event updates | Tested + verified on server | Realtime `/ws` (Phase 5). Fixed in Phase 6: the Settings switch was not read; now "off" means no `/ws` socket at all (server log showed none while Events was open). |
| Push via FCM relay | Not started | Separate service; MQTT is disabled on the server. |

## Known limitations

- The Garasi camera's clock was one hour ahead earlier on 2026-11-22 (overlay 11:08 for footage
  recorded at 10:08 WIB). In the Phase 6 check at 18:57 its live overlay matched the device clock, so it
  appears fixed; recording times in the app come from Frigate either way.
- Home Assistant creates alarms very often (22 in 6 minutes on 2026-11-22), so the event list grows
  quickly; consider tuning the automations.

- Camera audio (PCMA, transcoded to FLAC by go2rtc) fails on Android's FLAC decoder, so live view
  falls back to video only. Fix on the server: give go2rtc an AAC audio source, e.g.
  `ffmpeg:<camera>_main#audio=aac`.
- Cameras on this server report frequent reconnects and stalls (e.g. Garasi:
  43 reconnects and 13 stalls in an hour, quality "unusable"). This is a
  camera/network issue on the server side, shown as reported.

- Compose BOM is held at 2026.09.00 until SDK platform 37.1 is installed.
- Downloading or exporting clips is not implemented.
- Push notifications are not implemented (needs a relay; MQTT is disabled on the server).
- Login against Frigate's authenticated port (8971) is tested only against a test server, because the
  real server exposes no HTTPS (SECURITY.md S1/S3).
- The diagnostics warning list lives in memory, so it starts empty after the app process restarts.
- The app shows Frigate's `connection_quality` value as reported (e.g. "unusable"
  while the camera still delivers frames).
- The light theme is defined but not selectable.
