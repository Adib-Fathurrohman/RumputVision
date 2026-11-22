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

The Keystore cipher (`KeystoreSecretCipher`) needs a real Android Keystore
and is replaced by `FakeSecretCipher` in JVM tests. It should get an
instrumented test (`androidTest`) once a device is part of the test routine.

No test needs the home server. A mock Frigate API (OkHttp `MockWebServer`
with recorded responses from `docs/reference/`) arrives with Phase 2.

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

## Conventions

- Dependencies are declared only in `gradle/libs.versions.toml`.
- UI strings live in `res/values/strings.xml`.
- Never log secrets. Use `AppLogger`, which redacts and is disabled in release.
- Do not mark a feature **Tested** in `IMPLEMENTATION_STATUS.md` unless a test
  actually ran.
