# Streaming architecture

## Facts from the installed server (verified 2026-11-22)

- go2rtc `1.9.14`, embedded in Frigate 0.18.2.
- Main streams are **H.264 High** (2560×1440 / 1920×1080, ~25 fps, ~6 Mbit/s). Sub streams
  (`live.streams`) were **H.265/HEVC** at first; the owner switched them to **H.264 High 704×576
  25 fps** on 2026-11-22 (also used for Frigate's detect role since then). Camera audio is PCMA (G.711
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

## Implementation (Phase 4)

`core/streaming/`:

| Class | Role |
|---|---|
| `MseWebSocketDataSource` | Media3 `DataSource` over go2rtc's MSE WebSocket. Sends the codec request, waits for the `mse` reply, then turns each binary message into bytes for `FragmentedMp4Extractor`. Fails with typed `StreamFailure`s: `UNAUTHORIZED` (401/403 upgrade), `SOURCE_ERROR` (go2rtc `error` message), `NETWORK` (stall > 10 s, drop), `FELL_BEHIND` (data queued > 4 s). `abort()` closes the socket from any thread and wakes the reader. |
| `StreamCodecs` | Codec list from `MediaCodecList`: H.264 always, H.265 only if a decoder exists, audio codecs only while unmuted. |
| `Media3CameraStreamPlayer` | `CameraStreamPlayer` on ExoPlayer: buffering per `BufferProfile` (`LOW_LATENCY` for single live view, `SMOOTH` for the grid), catch-up speed, retry/backoff (`RetryPolicy`), audio fallback, rebuffer detection. |
| `CameraStreamPlayerFactory` | Creates players (an interface, so ViewModels are tested with fakes). |

Stream selection (`Camera.streamFor`): **Sub** = Frigate's `live.streams` entry, and **Main** = the go2rtc
restream used by the record input (`rtsp://127.0.0.1:8554/<name>`, parsed from the config; other input
paths, which may contain credentials, are never stored). Each falls back to the other.

Screens: **Live view** (`feature/liveview/LiveViewScreen`) for one camera, with quality, mute, camera
switching and fullscreen, plus immersive mode in landscape. The video area uses the camera's detect
aspect ratio, which corrects anamorphic 704×576 sub streams. **Live grid** (`LiveGridScreen`) is 2×2,
pages for more cameras, at most `maxConcurrentGridStreams` live players on the grid quality, always muted;
other cells show refreshing stills.

## Findings from the physical device (Infinix X6731B, Helio G99, Android 14, 2026-11-22)

1. **Hardware decoding works.** `c2.mtk.avc.decoder` decoded H.264 2560×1440 main streams, and
   `c2.mtk.hevc.decoder` decoded the (then) H.265 sub streams. Four simultaneous H.264 sub-stream
   decoders initialised without errors.
2. **go2rtc's FLAC audio breaks Android's FLAC decoder.** For PCMA cameras go2rtc transcodes audio to
   FLAC, the only MSE audio it offers them (offering only AAC/Opus gives video only). Android's
   `c2.android.flac.decoder` fails with `CodecException 0xe` every ~6 s. FFmpeg decodes the same data
   fine, but the stream contains irregular block sizes (mostly 320 samples, occasionally 311 and a 9-byte
   frame). Handling:
   - **Muted streams request no audio**, which is the default. This also saves bandwidth and the server's
     FLAC encoding CPU.
   - **Unmuting reconnects with audio.** If the audio renderer then fails, the player reconnects video-only
     and shows "Audio … could not be played". Verified on the device.
3. **First frame time is the camera's keyframe interval.** It was 4.5 s on the main stream and
   1.4–3.2 s on the H.264 sub streams.
4. **Buffers.** A 300 ms resume threshold re-stalled immediately on 2.4 GHz Wi-Fi with 6 Mbit/s main
   streams. Settings now: min 1 s, max 2.5 s, start after 200 ms, resume after 1 s, and speed up 1.15×
   above 2 s buffered.
5. **The phone's 2.4 GHz link is the bottleneck.** The server is wired (100 Mbps; it should be
   gigabit, so check the cable or switch port), and cameras and gateway answer the server in < 30 ms.
   The phone, on 2.4 GHz channel 9 (single-stream 802.11n, the channel reported 80 % busy), shows
   15 % ping loss even when idle. With the 4-stream grid (3.2 Mbit/s) its pings rise to 0.5–4 s,
   i.e. data arrives at full average rate but in bursts seconds late. An earlier run at −71 dBm got only
   ~1.4 Mbit/s.
   - **WifiLock A/B (same conditions, near the router at −55 dBm):** with `WIFI_MODE_FULL_LOW_LATENCY`
     2.82 / 2.80 Mbit/s and 12 / 8 stalls; without it 2.81 Mbit/s and 11 stalls. There was no
     measurable benefit, so it was **removed** (also dropping the `WAKE_LOCK` permission).
   - **Grid `SMOOTH` profile:** buffer 2.5–5 s, resume after 2 s. Result: all four tiles live,
     3.22 Mbit/s delivered, **4 stalls in 21 s** (vs 8–12 with the low-latency profile).
   - **Stalled tiles keep the last video frame** instead of being covered by a still that may not
     load on a congested link either.
   - **"Video keeps pausing" hint** offers Sub when Main rebuffers ≥ 3 times in 30 s.
   - **Best fix is the network:** put the phone on 5 GHz if the router offers it, or move the cameras off
     the 2.4 GHz channel if they use Wi-Fi.
6. **Leaving the screen closes the stream.** The app aborts its WebSockets at once; the player was idle
   ~1 s after Home. go2rtc keeps listing the consumer for ~5 s more (nginx detects the close lazily).
7. **OkHttp limit.** Live WebSockets hold a dispatcher slot for their lifetime, so they now use a
   dedicated dispatcher. Otherwise four streams would leave one of OkHttp's 5 per-host slots for every
   API call and snapshot.

## Measurements (Phase 6, emulator, 2026-11-22)

Emulator `Medium_Phone_API_36.1` (arm64, software video decoding) on the same LAN, against the real server.
The phone was not attached, so these are not phone numbers.

| Measure | Result | Method |
|---|---|---|
| Glass-to-glass latency, Sub (704×576) | **≈ 1.5 s** | Four screenshots of Garasi; the device clock read just before and after each capture, against the camera's on-screen clock. The intervals intersect at 1.44–1.6 s. Assumes the camera clock is right (it agreed with the device to within the stream delay). |
| Glass-to-glass latency, Main (2560×1440) | 2.0–3.6 s | Same method, three samples. Includes software decoding of 1440p on the emulator, so a phone with a hardware decoder should do better. |
| Time to first frame, Main | 3.8 s from opening live view | Media3 `EventLogger` (debug build): `renderedFirstFrame` eventTime. Dominated by the keyframe interval (see finding 3). |
| Time to first frame, Sub | 1.8 s after switching from Main | Same. |
| Release cold start (`am start -W` TotalTime) | 300–415 ms after ART compiled the app; 260–1,280 ms straight after install | Six runs each, signed R8 build, profile configured. Time to first activity frame, not to server data. |

### Still to verify on a device

- [x] H.264 and H.265 hardware decoding.
- [x] Audio fallback; video-only when muted.
- [x] Stream closed when leaving or backgrounding (app side ~1 s).
- [x] Four simultaneous decoders start.
- [x] 4-stream grid plays live (with the SMOOTH profile; occasional stalls remain on this 2.4 GHz link).
- [x] WifiLock effect (none measured; removed).
- [x] Glass-to-glass latency on the emulator (≈ 1.5 s Sub). Repeat on the phone.
- [ ] Over Tailscale on mobile data.
- [ ] Battery and thermal behaviour over a long session.
