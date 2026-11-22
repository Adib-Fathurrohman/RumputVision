# Implementation status

Legend: **Not started** · **In progress** · **Implemented** (code exists,
not automatically tested) · **Tested** (automated test executed and passing)
· **Blocked**

Last updated: 2026-11-22. Test run: `./gradlew :app:testDebugUnitTest`,
47 tests, 0 failures. Manual check on emulator (API 36.1).

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

## Phase 2 — Frigate connection: not started

| Feature | Status | Notes |
|---|---|---|
| Retrofit/OkHttp client, DTOs | Not started | |
| Request-time cleartext and credential guard | Not started | |
| Login / token / refresh / logout | **Blocked** for real verification | Needs SECURITY.md S1 + S3 (port 8971 behind HTTPS). Can be built against the spec and MockWebServer. |
| Connection test, endpoint selection (local/remote) | Not started | |
| Camera discovery (`/api/config`, `/api/stats`) | Not started | |
| Realtime `/ws` with backoff | Not started | |
| Mock Frigate API for tests | Not started | |

## Phase 3 — Dashboard and cameras: not started

Dashboard data, camera list, snapshots, camera detail. Screens currently show
an explicit "not available yet" state.

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
- No connection to Frigate yet. Screens other than Settings show placeholders
  that say so.
- The light theme is defined but not selectable.
