# Development

## Toolchain (verified 2026-11-22 on macOS, Apple Silicon)

| Component | Version | Notes |
|---|---|---|
| JDK | 25 (Homebrew OpenJDK) | Runs Gradle. Bytecode target is 17. |
| Gradle | 9.9.2 (wrapper, SHA-256 pinned) | |
| Android Gradle Plugin | 9.5.2 | Built-in Kotlin, so no `kotlin-android` plugin. |
| Kotlin | 2.4.21 | Compose and serialization compiler plugins. |
| KSP | 2.3.14 | Hilt and Room processors. |
| Android SDK | platform `android-37.0`, build-tools 37.0.0 | `compileSdk = 37`, `targetSdk = 37`, `minSdk = 29`. |
| Compose BOM | 2026.09.00 | **Held back:** 2026.11.01 (Foundation 1.13) requires compileSdk 37.1, which is not installed. Upgrade both together. |
| Android Studio | not installed | The project builds entirely from the command line. Open it in a recent Android Studio when available. |

`local.properties` (untracked) must contain `sdk.dir=/Users/<you>/Library/Android/sdk`.

## Common commands

Build the debug APK (output: `app/build/outputs/apk/debug/app-debug.apk`):

```bash
./gradlew :app:assembleDebug
```

Run all JVM unit tests, including the Robolectric Room and Compose navigation tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run lint:

```bash
./gradlew :app:lintDebug
```

Install on a running emulator or connected device:

```bash
./gradlew :app:installDebug
```

The debug build uses application ID `com.rumput.vision.debug`, so it can be
installed next to a release build.

### Emulator

An AVD named `Medium_Phone_API_36.1` exists on this machine:

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1
```

With the software GPU, system apps sometimes show "isn't responding" dialogs
right after boot. Tap **Wait**; this is the emulator, not the app.

Emulators cannot verify streaming performance or HEVC hardware decoding. Use
a physical device for Phase 4.

## Tests

| Suite | Kind | What it covers |
|---|---|---|
| `ServerUrlPolicyTest` | JVM | URL parsing, host classification, HTTPS/cleartext policy, warnings |
| `SaveServerProfileUseCaseTest` | JVM | Validation, create/edit, password/credential update rules |
| `ServerProfileEditViewModelTest` | JVM | Editor state, live warnings, save/delete, password not leaked |
| `RedactorTest` | JVM | Secrets removed from log text |
| `EncryptedCredentialStoreTest` | JVM + real DataStore | Encryption round-trip, AAD binding, fail-closed |
| `AppPreferencesDataSourceTest` | JVM + real DataStore | Defaults, updates, clamping |
| `DefaultServerProfileRepositoryTest` | Robolectric + in-memory Room | Real SQL: active profile rules, delete, credentials |
| `AppNavigationTest` | Robolectric + Hilt + Compose | Onboarding → add server → all tabs; HTTPS rejection message |
| `FrigateApiSerializationTest` | MockWebServer + recorded fixtures | Real 0.18.2 config/stats/profile/version parse |
| `TransportGuardInterceptorTest` | MockWebServer | No credentials or login over HTTP, no HTTP to public hosts, no redirects |
| `SessionTokenTest`, `FrigateLoginServiceTest` | HTTPS MockWebServer | Cookie token, expiry, invalid/rate-limited, TLS never bypassed |
| `FrigateSessionTest` | HTTPS MockWebServer | Local/remote fallback, cellular order, offline/background, login, re-auth, token refresh, sign in/out |
| `FrigateConnectionTesterTest` | MockWebServer | Anonymous-admin detection, sign-in check, redirects, non-Frigate, policy |
| `FrigateServerRepositoryTest` | Robolectric + Room + MockWebServer | Discovery, status, stale/cached handling, removed cameras |
| `MigrationTest` | Robolectric + SQLite | Real v1 database (from exported schema) → v2 |
| `DashboardViewModelTest` | coroutines-test | 30 s refresh only while observed and connected; sign-in |
| `LiveFrigateServerTest` | opt-in, real server | Real stack against `FRIGATE_LIVE_URL` (skipped when unset) |
| `MseWebSocketDataSourceTest` | Robolectric + WebSocket server | go2rtc MSE protocol, errors, stall, abort, close |
| `StreamingLogicTest`, `LiveViewModelsTest` | JVM | Retry/backoff, codecs, error mapping, stream selection; live view and grid ViewModels |
| `FrigateEventRepositoryTest` | Robolectric + Room + test server | Events parse/cache/paging/filters, offline detail, realtime keys |
| `FrigateRealtimeTest` | WebSocket server | Messages, reconnect with backoff, no socket while disconnected |
| `RecordingTimelineTest` | JVM | Playlist ↔ wall clock, including Frigate's untrimmed start (real numbers) |
| `EventsViewModelsTest`, `RecordingsAndPlaybackTest` | coroutines-test | Paging, filters, offline, realtime merge; hour windows; playback, including opened offline |
| `DiagnosticsReportTest` | JVM | Diagnostics report leaves out addresses, username and secrets; log masking; ring buffer |
| `KeystoreCredentialStoreTest` | **Instrumented** (`androidTest`) | Real Android Keystore: AES-256-GCM round trip, random IV, profile binding, tamper rejection, non-exportable key, nothing secret on disk |

Recorded fixtures live in `app/src/test/resources/frigate/0.18.2/`. They
were captured from the real server and **sanitized**: go2rtc stream sources
were replaced and secret-named fields masked. Re-check for credentials
before adding new fixtures.

Run the opt-in live test against an anonymous-access server:

```bash
FRIGATE_LIVE_URL=http://192.168.100.22 ./gradlew :app:testDebugUnitTest --tests '*LiveFrigateServerTest*'
```

The Keystore cipher (`KeystoreSecretCipher`) needs a real Android Keystore,
so JVM tests use `FakeSecretCipher` and the real cipher is covered by the
instrumented `KeystoreCredentialStoreTest`. Run it with an emulator or device
attached (it installs and then **uninstalls** the debug app, which deletes its
data):

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

No test needs the home server: `testing/FrigateTestServer.kt` is a scriptable
Frigate stand-in (HTTP or HTTPS with a test CA, optional 8971-style auth).
Run lint separately from tests (`./gradlew :app:lintDebug`). Running both in
one invocation occasionally fails `lintAnalyzeDebugUnitTest`.

## Release build

The release build has R8 minification and resource shrinking enabled.
Without signing configuration, it produces
`app/build/outputs/apk/release/app-release-unsigned.apk`.

To sign:

1. Create a keystore **outside** the repository (once):

   ```bash
   keytool -genkeypair -v -keystore ~/keys/rumput-vision.jks -alias rumput -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `keystore.properties` in the project root. It is git-ignored; never
   commit it.

   ```properties
   storeFile=/Users/<you>/keys/rumput-vision.jks
   storePassword=...
   keyAlias=rumput
   keyPassword=...
   ```

3. Build:

   ```bash
   ./gradlew :app:assembleRelease
   ```

Back up the keystore. Losing it means future updates cannot be installed
over existing installs.

### Release checklist

1. `./gradlew :app:testDebugUnitTest`, then `./gradlew :app:lintDebug` (separately, see above).
2. `./gradlew :app:connectedDebugAndroidTest` on an emulator or device.
3. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
4. `./gradlew :app:assembleRelease` with `keystore.properties` present.
5. Install the release APK and walk through: add server and test connection, Home, Cameras,
   camera detail, live view (Sub and Main), live grid, Events, event detail, Play recording,
   Recordings, Settings, About and diagnostics. Repeat the walk in airplane mode.
6. Check there is no app output in logcat (`adb logcat | grep RumputVision` prints nothing for a
   release build) and that the crash buffer is empty (`adb logcat -b crash -d`).

## Conventions

- Dependencies are declared only in `gradle/libs.versions.toml`.
- UI strings live in `res/values/strings.xml`.
- Never log secrets. Use `AppLogger`, which redacts and is disabled in release. Its warnings and
  errors also go to the in-memory `DiagnosticsLog` (all builds) for the diagnostics screen.
- Do not mark a feature **Tested** in `IMPLEMENTATION_STATUS.md` unless a test
  actually ran.
