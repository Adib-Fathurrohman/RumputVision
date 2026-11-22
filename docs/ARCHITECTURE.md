# Architecture

## Summary

- Single Gradle module `:app`, organised by feature package.
- MVVM with unidirectional data flow. ViewModels expose an immutable
  `StateFlow<UiState>`, and screens send events as method calls.
- Clean-architecture layering inside the module:
  `feature/*` → `domain` ← `data` → `core/*`.
  `domain` has no Android, Retrofit, Room or Compose imports.
- Hilt for DI, Kotlin Coroutines and Flow for async work, Room for cached
  metadata, DataStore for preferences, and Android Keystore for secrets.

## Packages

```
com.rumput.vision
├── core
│   ├── common      dispatchers, Clock, small shared types
│   ├── database    Room DB, entities, DAOs (Hilt module)
│   ├── datastore   DataStore-backed preference source
│   ├── design      theme tokens + reusable components
│   ├── logging     AppLogger + Redactor (no secrets in logs)
│   ├── network     OkHttp/Retrofit, auth interceptor, transport policy (Phase 2)
│   ├── security    Keystore cipher, encrypted credential store
│   └── streaming   CameraStreamPlayer, MSE data source, player pool (Phase 4)
├── data
│   ├── remote      Frigate API service + DTOs (Phase 2)
│   ├── local       (Room/DataStore adapters live in core; feature caches go here)
│   ├── mapper      DTO/entity ↔ domain
│   ├── model       data-layer-only models
│   └── repository  repository implementations + Hilt bindings
├── domain
│   ├── model       ServerProfile, AppPreferences, ConnectionState, CameraStatus, …
│   ├── repository  repository interfaces
│   └── usecase     ServerUrlPolicy, SaveServerProfileUseCase, …
├── feature
│   ├── onboarding, dashboard, cameras, liveview, events, recordings,
│   └── notifications, settings
└── navigation      type-safe routes, NavHost, adaptive navigation scaffold
```

## Key decisions

| Decision | Choice | Why |
|---|---|---|
| Min SDK | **29** (Android 10) | Hardware HEVC decoding is near-universal (needed for the H.265 sub streams), FLAC MediaCodec (API 27+), TLS 1.3 by default, and a mature Keystore with StrongBox (API 28+). Covers the large majority of active devices. |
| Target/compile SDK | **37** | Latest stable platform installed (`platforms/android-37.0`). |
| Toolchain | AGP 9.5.2, Gradle 9.9.2, Kotlin 2.4.21 (AGP built-in Kotlin), KSP 2.3.14, JDK 25 | Latest stable versions at project start. AGP 9's built-in Kotlin replaces the `kotlin-android` plugin. |
| Navigation | Navigation Compose with `@Serializable` type-safe routes; `NavigationSuiteScaffold` | Bottom bar on phones, rail on wide screens, from one code path. |
| Secrets | Custom Keystore AES-GCM + DataStore | `security-crypto` is deprecated. See `SECURITY.md`. |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Standard. `ignoreUnknownKeys` because Frigate's config is large and changes between versions. |
| Live video | go2rtc MSE over WebSocket → Media3 | See `STREAMING.md`. |
| Recording playback | Media3 HLS on Frigate VOD | Native seeking. |
| Icons | `material-icons-extended` 1.7.8 | No longer updated, but stable and complete. R8 strips unused icons. It can be replaced later by Material Symbols vector drawables. |
| Theme | Dark-first custom M3 color scheme | NVR use case; light scheme is defined but not exposed yet. |

## Data flow example (server profiles)

```
SettingsScreen ──events──▶ ServerProfileEditViewModel ──▶ SaveServerProfileUseCase
       ▲                          │ StateFlow<UiState>            │ ServerUrlPolicy (validation)
       └──────── collectAsStateWithLifecycle ◀───────────────────┘
                                                                  ▼
                                               ServerProfileRepository (domain interface)
                                                                  ▼
                                    DefaultServerProfileRepository (data)
                                     ├─ ServerProfileDao (Room)        – metadata
                                     └─ CredentialStore (Keystore+DS)  – password / token
```

## Connectivity model (Phase 2)

Each profile has an optional **local URL** and an optional **remote URL**,
plus an endpoint preference (`AUTOMATIC`, `LOCAL_ONLY`, `REMOTE_ONLY`).
`AUTOMATIC` probes `GET /api/version` on the local URL with a short timeout
when the device is on an unmetered network, then falls back to the remote
URL. The probe re-runs on `ConnectivityManager` network changes, not on a
timer. `.home.arpa` names are never assumed to resolve on mobile data.

Cached data (Room) is always tagged with `fetchedAt`. The UI shows it as
"last known" and never shows a cached camera as online.

## Notifications (future)

```
Frigate ──MQTT (frigate/reviews)──▶ Notification relay (separate service, holds FCM credentials)
                                        └─▶ FCM ──▶ Rumput Vision (FirebaseMessagingService)
```

- The relay is a separate project. The APK contains only the public
  `google-services.json` client config, never the Admin credentials.
- MQTT is currently **disabled** on the server (`mqtt.enabled: false`) and
  must be enabled for the relay.
- Initial release: in-app updates via `/ws` while the app is in the
  foreground, plus notification preferences stored locally.
