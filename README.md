# Rumput Vision

A native Android client for a self-hosted [Frigate NVR](https://frigate.video).
Built with Kotlin, Jetpack Compose and Material 3.

**Status:** Phase 1 (foundation) is complete. See
[docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Frigate API reference](docs/FRIGATE_API.md), verified against Frigate 0.18.2
- [Streaming design](docs/STREAMING.md)
- [Security audit and app security design](docs/SECURITY.md): read this before using real credentials
- [Development and release builds](docs/DEVELOPMENT.md)

## Quick start

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```
