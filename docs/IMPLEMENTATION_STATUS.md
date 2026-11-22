# Implementation status

Legend: **Not started** · **In progress** · **Implemented** (code exists,
not automatically tested) · **Tested** (automated test executed and passing)
· **Blocked**

Last updated: 2026-11-22 (Phase 2). Test run: `./gradlew :app:testDebugUnitTest`.
96 tests, 0 failures, 1 skipped (the opt-in live test). The live test was also
run separately against the real server and passed. Lint: 0 errors.
Manual end-to-end check on the emulator (API 36.1) against the real Frigate
0.18.2 over the LAN.

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
| Encrypted credential storage | Tested (logic) | Store logic tested with a fake cipher. **The Keystore cipher itself is untested.** It needs an instrumented test on a device. |
| App preferences (DataStore) | Tested | |
| Log redaction | Tested | |
| Backup exclusion | Implemented | Manifest + rules; not tested. |

## Phase 2 — Frigate connection: done (with one blocked verification)

| Feature | Status | Notes |
|---|---|---|
| OkHttp/Retrofit client, DTOs from the real 0.18.2 responses | Tested | Sanitized recorded fixtures; live test against the real server passed. |
| Transport guard (no credentials/login over HTTP, no HTTP to public hosts, no redirects) | Tested | |
| Login, Bearer token, refresh via re-issued cookie, re-auth on 401, logout | Tested against an HTTPS test server | **Blocked** for real-server verification until port 8971 is reachable over HTTPS (SECURITY.md S1/S3). |
| Encrypted storage of token and optional password | Tested (logic) | Keystore cipher itself still needs an on-device test. |
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

## Phase 3 — Dashboard and cameras: in progress

| Feature | Status | Notes |
|---|---|---|
| Dashboard with server status and camera list | Implemented (Phase 2) | |
| Cameras tab list with status and refresh | Implemented (Phase 2) | |
| Snapshot previews (`latest.webp`), preview grid | Not started | |
| Camera detail screen | Not started | |

## Phase 4 — Live streaming: not started

MSE WebSocket data source, `CameraStreamPlayer`, player pool, grid. Needs a
physical device for verification.

## Phase 5 — Events and recordings: not started

## Phase 6 — Reliability and release: not started

## Notifications

| Feature | Status | Notes |
|---|---|---|
| Notification preferences | Tested | Stored in DataStore. The push toggle is visibly disabled. |
| In-app event updates | Not started | Depends on Phase 2 `/ws`. |
| Push via FCM relay | Not started | Separate service; MQTT is disabled on the server. |

## Known limitations

- Compose BOM is held at 2026.09.00 until SDK platform 37.1 is installed.
- Events and Recordings tabs still show "not available yet" placeholders.
- The app shows Frigate's `connection_quality` value as reported (e.g. "unusable"
  while the camera still delivers frames).
- The light theme is defined but not selectable.
