# Streaming architecture

## Facts from the installed server (verified 2026-11-22)

- go2rtc `1.9.14`, embedded in Frigate 0.18.2.
- Main streams are **H.264 High**. Sub streams (used for live view on every
  camera, `live.streams`) are **H.265/HEVC**. Camera audio is PCMA (G.711
  A-law) 8 kHz.
- Frigate's Nginx exposes go2rtc **only over WebSockets**:
  - `/live/mse/api/ws?src=<stream>` (MSE)
  - `/live/webrtc/api/ws?src=<stream>` (WebRTC signalling)
  - HTTP `stream.mp4` / `stream.m3u8` live endpoints are **not** proxied
    (they return the SPA HTML).
- Port 8554 (RTSP restream, unauthenticated) and 8555 (WebRTC media) are open
  on the LAN; 1984 (go2rtc API) is closed.
- Recordings are available as fMP4 **HLS VOD** at
  `/vod/<camera>/start/<s>/end/<e>/index.m3u8`.

### MSE protocol test

Using a minimal WebSocket client through port 80:

1. Client → `{"type":"mse","value":"avc1.640029,avc1.64002A,avc1.640033,hvc1.1.6.L153.B0,mp4a.40.2,mp4a.40.5,flac,opus"}`
2. Server → `{"type":"mse","value":"video/mp4; codecs=\"hvc1.1.6.L153.B0,flac\""}` for
   `garasi_sub`, and `codecs="avc1.640029,flac"` for `garasi_main`.
3. Server → binary frames: an fMP4 init segment (`ftyp` + `moov`) followed
   by `moof`/`mdat` fragments. In 3 s: ~350 KB (sub), ~790 KB (main).

go2rtc transcodes the PCMA audio to FLAC because FLAC was offered. If the
client offers only video codecs, the stream is video-only.

## Options evaluated

| Transport | Verdict | Reasoning |
|---|---|---|
| **MSE over WebSocket → Media3** | **Selected (primary)** | Verified on this server. Works through Frigate's authenticated Nginx on 8971 and any reverse proxy, with the same JWT as the REST API. It is a single TCP/TLS connection, so it works over Tailscale without ICE/STUN. It carries both H.264 and H.265, and Android has hardware HEVC decoders on effectively all API 29+ devices. Media3 already parses fMP4 (`FragmentedMp4Extractor`), decodes FLAC (API 27+), and handles surfaces and lifecycle. The only custom piece is a `DataSource` that turns WebSocket binary frames into a byte stream. |
| WebRTC (`/live/webrtc/api/ws`) | Deferred | Lowest latency, and two-way audio is possible. But Google's libwebrtc on Android does not reliably decode H.265, and every sub stream here is H.265. It needs port 8555 reachable plus correct `webrtc.candidates` for LAN and Tailscale IPs (not configured on this server). It also adds a large native dependency and needs its own renderer. Revisit for H.264 main streams or two-way talk once MSE is in place. |
| RTSP (`rtsp://host:8554/<stream>`) via `media3-exoplayer-rtsp` | Optional, LAN only, later | Media3 RTSP supports H.264/H.265 and G.711. But the restream is **unauthenticated** (SECURITY.md S4), bypasses Frigate roles, needs 8554 reachable (also over Tailscale), and has no TLS. It will be offered only as an explicit opt-in transport after the server sets go2rtc RTSP credentials. |
| HLS live | Not available | Not proxied by Frigate's Nginx (verified). |
| jsmpeg | Rejected | Low quality, MPEG-1 transcoding on the server, no audio, and `/live/jsmpeg/<cam>` returned 500. |
| Snapshot refresh (`/api/<cam>/latest.webp?height=`) | Fallback | ~45 KB per frame at 360p. Used for grid cells that exceed the concurrent-stream limit, for background or metered situations, and while a stream is connecting. |

**Recordings:** Media3 HLS (`media3-exoplayer-hls`) on Frigate's VOD
playlists. These are seekable VOD playlists with fMP4 segments. Event clips
use progressive `clip.mp4`.

## Design

```
feature/liveview (Compose)
   │ observes PlayerUiState, renders PlayerSurface
   ▼
core/streaming
   CameraStreamPlayer (interface)          ← UI-facing contract
     ├─ MseCameraStreamPlayer              ← Media3 ExoPlayer + MseWebSocketDataSource
     ├─ SnapshotCameraStreamPlayer         ← polls latest.webp (fallback)
     └─ (later) RtspCameraStreamPlayer
   StreamDiscovery                         ← maps camera → go2rtc stream names + codecs
   StreamPlayerPool                        ← limits concurrent players; reuses per-camera instances
   CodecCapabilities                       ← MediaCodecList → codec list offered to go2rtc
```

### `CameraStreamPlayer` contract

```kotlin
interface CameraStreamPlayer {
    val state: StateFlow<StreamState>   // Idle, Connecting, Buffering, Playing, Error(reason, retryable), Released
    fun prepare(source: StreamSource)    // camera + stream name + endpoint + auth
    fun play()
    fun stop()                           // closes the socket, keeps the decoder
    fun retry()
    fun setMuted(muted: Boolean)
    fun release()                        // idempotent
}
```

- Players are created by `StreamPlayerPool`, never from inside a composable.
  The composable only gets a handle via a `remember`ed key (camera +
  quality). Recomposition never creates a player.
- Lifecycle: `ON_STOP` → `stop()` (socket closed, no background streaming).
  `ON_START` → `play()`. Leaving the screen → `release()`.
- Grid: at most `maxGridStreams` (preference, default 4) live players. Grid
  uses the sub stream and fullscreen uses the main stream (both
  configurable). Cells over the limit use the snapshot player.

### MSE data source details

- `MseWebSocketDataSource` implements Media3 `DataSource`: `open()` connects
  the OkHttp WebSocket (with `Authorization: Bearer`) and sends the `mse`
  request with codecs from `CodecCapabilities`. `hvc1` is offered only if a
  hardware HEVC decoder exists. It waits for the `mse` reply, then `read()`
  drains a bounded buffer of binary frames. Length is `C.LENGTH_UNSET` and
  the source is not seekable.
- Back-pressure: if the bounded buffer is full, the oldest fragments are
  dropped and playback resyncs at the next keyframe fragment. Live view should
  never drift behind real time.
- `LoadControl` tuned for live: small min/max buffer (~500 ms / 2 s) and fast
  start (~250 ms).
- Errors map to `StreamState.Error` with a reason: `Unauthorized` (HTTP 401
  during upgrade), `StreamNotFound`, `UnsupportedCodec`, `NetworkLost`,
  `ServerClosed`. Retries use exponential backoff with jitter, capped, and
  happen only while the screen is visible.

### To verify in Phase 4 (on a physical device)

- [ ] H.265 sub stream decodes via MediaCodec on the test device.
- [ ] FLAC audio in fMP4 plays; mute toggle.
- [ ] Latency glass-to-glass (target < 1.5 s LAN).
- [ ] Through HTTPS + JWT on 8971 (after SECURITY.md S1/S3).
- [ ] Over Tailscale on mobile data.
- [ ] Four simultaneous sub streams: CPU, memory, battery, bandwidth.
- [ ] Recovery after Wi-Fi ↔ mobile handover.
