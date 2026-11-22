# Frigate API reference (as used by Rumput Vision)

**Installed server:** Frigate `0.18.2-cfa08c5`, go2rtc `1.9.14`
(probed 2026-11-22).

**Source of truth:** the server's own OpenAPI document, saved at
[`reference/frigate-0.18.2-openapi.json`](reference/frigate-0.18.2-openapi.json)
(fetched from `GET /api/openapi.json`; 167 paths). The OpenAPI paths omit the
`/api` prefix that Frigate's Nginx adds; all HTTP paths below include it.

The status column means:
- **Verified**: called against the real server and the response shape was inspected.
- **Spec**: present in the OpenAPI document, not yet called.
- **Docs**: described in the official documentation only.

> Every response below was observed through the **unauthenticated** port, because
> 8971 is not exposed (see `SECURITY.md` S1). Responses through 8971 for a
> non-admin role may be filtered by `allowed_cameras`.

## Server

| Method & path | Use | Status | Notes |
|---|---|---|---|
| `GET /api/version` | Connection test, version | Verified | `text/plain`, e.g. `0.18.2-cfa08c5`. |
| `GET /api/stats` | Server health, detectors, per-camera FPS | Verified | See shape below. |
| `GET /api/config` | Camera discovery and metadata | Verified | ~40 KB. **Contains camera RTSP credentials** in `go2rtc.streams`. Parse only the fields listed below. Never log, cache or persist the raw body. |
| `GET /api/profile` | Current user and role | Verified | `{"username","role","allowed_cameras":[...]}` |
| `GET /api/auth/first_time_login` | Public | Verified | `{"admin_first_time_login":false}` |
| `GET /api/labels` | Label filter options | Verified | `["orang_terdeteksi"]` (array of strings). Optional `?camera=`. |

### `/api/stats` (fields used)

```jsonc
{
  "cameras": {
    "<camera>": {
      "camera_fps": 5.1, "process_fps": 5.1, "detection_fps": 0.0,
      "detection_enabled": false,
      "connection_quality": "poor",     // string; seen: "poor"
      "expected_fps": 5,
      "reconnects_last_hour": 9, "stalls_last_hour": 1
    }
  },
  "detectors": { "<name>": { "inference_speed": 10.0, "detection_start": 0.0 } },
  "service": {
    "uptime": 8328, "version": "0.18.2-cfa08c5", "latest_version": "0.18.3",
    "storage": { "/media/frigate/recordings": { "total": 468701.6, "used": 29268.1, "free": 415553.1 } },
    "last_updated": 1795325092
  }
}
```

Storage values are in MB. A camera is treated as **online** when it appears
in `stats.cameras` with `camera_fps > 0`. Realtime status also arrives on
`/ws` (`<camera>/status/<role>`).

### `/api/config` (fields used)

- `cameras.<name>.enabled` (bool), `.ui.order` (int), `.ui.dashboard` (bool)
- `cameras.<name>.live.streams` — map of display name to go2rtc stream name.
  On this server each camera has one entry, its `_sub` stream, e.g.
  `{"garasi_sub": "garasi_sub"}`.
- `cameras.<name>.detect.width|height` (aspect ratio)
- `cameras.<name>.record.enabled`, `.snapshots.enabled`, `.audio.enabled`
- `go2rtc.streams` — **only the keys** (stream names), never the values.
- `auth.cookie_name`, `auth.session_length`, `auth.refresh_time`
- `camera_groups`

Unknown keys must be ignored (`ignoreUnknownKeys = true`): the config is
large and changes between versions.

Implemented in `data/remote/dto/ConfigDto.kt`. `go2rtc.streams` values are
decoded as opaque JSON and never read, stored or logged.

**Display names:** Frigate 0.18 has no friendly-name field for cameras, so
the app derives one from the name (`ruang_tengah` → "Ruang Tengah").

**Snapshot cadence:** dashboard grid 360p every 10 s, Cameras list
180p every 30 s, camera detail 576p every 4 s (frames come from the 1024×576 detect stream since the
2026-11-22 server change). Each runs only while that
screen is visible and the server is connected. Intervals stay well above the
server's per-frame encode time.

**How the app uses these (Phase 2):**
- Connection probe: `GET /api/version` (unauthenticated; a 401 also counts as
  "server present, sign-in required"), then `GET /api/profile`.
- Camera discovery and health: `GET /api/config` + `GET /api/stats` in
  parallel, every 30 s **only while Home is visible and connected**, and on
  demand from the Cameras tab.
- Camera status: online if `stats.cameras.<name>.camera_fps > 0`, offline
  otherwise, disabled if `enabled: false`, and unknown if the data is older than
  2 minutes or the server is unreachable.

## Authentication (Spec + Docs; implemented, not yet verified against 8971, which is closed)

| Method & path | Notes |
|---|---|
| `POST /api/login` | Body `{"user": string, "password": string}` (`AppPostLoginBody`). Returns the JWT in a cookie named `auth.cookie_name` (`frigate_token` here). |
| `GET /api/auth` | Used by Nginx `auth_request`; refreshes the token when needed. |
| `GET /api/logout` | Clears the cookie (observed 303 redirect on 5000). |
| `GET /api/profile` | Validates a token. |

Docs say the JWT can also be sent as `Authorization: Bearer <token>`.
Rate limiting: `auth.failed_login_rate_limit` (unset here).

## Cameras and media

| Method & path | Status | Notes |
|---|---|---|
| `GET /api/{camera}/latest.webp?height=N` | Verified | Current frame, scaled server-side. Measured on this server: 13 KB at 180p, ~45 KB at 360p, 184 KB at 720p; **0.4–1.2 s per request** (encoded on demand); `cache-control: no-store`; unknown camera → `404 {"success":false,"message":"Camera not found"}`. Optional `quality`. `.jpg` is much larger. |
| `GET /api/{camera}/recordings/{frame_time}/snapshot.{format}` | Spec | Frame from a recording. |
| `GET /api/{camera}?fps=&height=` | Spec | MJPEG debug stream. Docs say not to use it continuously, so the app does not use it. |

## Events (tracked objects)

| Method & path | Status | Notes |
|---|---|---|
| `GET /api/events` | Verified | Query: `cameras`, `labels`, `zones`, `after`, `before` (epoch seconds), `limit`, `offset`, `has_clip`, `has_snapshot`, `in_progress`, `sort`, … (comma-separated lists). |
| `GET /api/events/{id}` | Spec | `EventResponse` |
| `GET /api/events/{id}/snapshot.jpg` | Verified | Query: `bbox`, `crop`, `height`, `quality`, `timestamp`. |
| `GET /api/events/{id}/thumbnail.webp` | Verified | Small (~10 KB). |
| `GET /api/events/{id}/clip.mp4` | Verified | `video/mp4`, progressive. Optional `padding`. |
| `GET /vod/event/{id}` | Spec | HLS for an event. |

`EventResponse` (observed):

```json
{"id":"1795325071.105106-a63k1v","camera":"ruang_tengah","label":"orang_terdeteksi",
 "sub_label":null,"zones":[],"start_time":1795325066.105106,"end_time":1795325101.105106,
 "has_clip":true,"has_snapshot":true,"retain_indefinitely":false,"plus_id":null,
 "top_score":null,"false_positive":null,"box":null,"thumbnail":null,
 "data":{"type":"api","score":0,"top_score":0}}
```

`end_time` is `null` while an event is in progress. On this server the events
are created through the API (`data.type: "api"`, label `orang_terdeteksi`),
and Frigate's own object detection is disabled on all cameras
(`detect.enabled: false`, detector `dummy`). The app must not assume the
COCO label set.

## Review items (alerts and detections)

| Method & path | Status | Notes |
|---|---|---|
| `GET /api/review` | Verified | Query: `cameras`, `labels`, `zones`, `reviewed` (0/1), `limit`, `severity` (`alert`/`detection`), `before`, `after`. |
| `GET /api/review/summary` | Verified | Map keyed by `last24Hours` and `YYYY-MM-DD` with `total_alert`, `reviewed_alert`, `total_detection`, `reviewed_detection`. Optional `timezone`. |
| `GET /api/review/{id}` | Spec | |
| `GET /api/review/{id}/preview?format=gif` | Verified | Animated GIF preview. |
| `GET /api/review/{id}/clip.mp4` | Spec | |
| `POST /api/reviews/viewed` | Spec | Body `ReviewModifyMultipleBody`. |

**Spec mismatch:** the OpenAPI schema declares `start_time`/`end_time` as
`string`, but the server returns JSON numbers (`1795325006.108921`). Decode
them as numbers and accept numeric strings too.

```json
{"id":"1795325006.108921-mg0e44","camera":"ruang_belakang","start_time":1795325006.108921,
 "end_time":1795325036.108921,"severity":"alert","thumb_path":"/media/frigate/clips/review/thumb-….webp",
 "has_been_reviewed":false,
 "data":{"detections":["…"],"objects":["orang_terdeteksi"],"verified_objects":[],"sub_labels":[],"zones":[],"audio":[]}}
```

`thumb_path` is a server filesystem path. Strip the `/media/frigate` prefix
to get the URL: `/clips/review/thumb-<camera>-<id>.webp` (verified,
`200 image/webp`).

## Recordings

| Method & path | Status | Notes |
|---|---|---|
| `GET /api/recordings/summary?cameras=&timezone=` | Verified | `{"2026-11-15": true, …}`: days that have recordings. |
| `GET /api/{camera}/recordings/summary?timezone=` | Verified | `[{"day","events","hours":[{"hour":"04","events","motion","objects","duration"}]}]` |
| `GET /api/{camera}/recordings?after=&before=` | Verified | Segments: `id`, `start_time`, `end_time`, `duration`, `segment_size` (MB), `motion`, `objects`. |
| `GET /api/recordings/unavailable?cameras=&after=&before=&scale=` | Verified | Gaps: `[{"start_time","end_time"}]`. |
| `GET /vod/{camera}/start/{start}/end/{end}/index.m3u8` | Verified | **HLS VOD** with fMP4 (`#EXT-X-MAP init-v1.mp4`, `seg-N-v1.m4s`), `#EXT-X-PLAYLIST-TYPE:VOD`. Seekable. Used for recording playback. |
| `GET /api/{camera}/start/{start}/end/{end}/clip.mp4` | Spec | Export as MP4 (download). |
| `GET /api/preview/{camera}/start/{start}/end/{end}` | Spec | Low-res preview files for timeline scrubbing. |

Timestamps are Unix epoch seconds (UTC). Day buckets use the `timezone`
query parameter (IANA name). The app passes the device time zone.

## go2rtc (through Frigate's Nginx)

| Path | Status | Notes |
|---|---|---|
| `GET /api/go2rtc/streams` | Verified | Stream names, producers and consumers, codecs. **Contains camera credentials** in producer URLs, so the app reads only stream names and codecs. |
| `WS /live/mse/api/ws?src={stream}` | Verified | MSE protocol. See `STREAMING.md`. |
| `WS /live/webrtc/api/ws?src={stream}` | Verified (handshake only) | WebRTC signalling. Not used yet. |
| `/live/mse/api/stream.mp4`, `/live/mse/api/stream.m3u8` | Verified **not available** | Return the SPA `index.html`. Frigate's Nginx proxies only the WebSocket routes. |
| `/api/go2rtc/stream.mp4`, `/api/go2rtc/webrtc` (GET) | Verified **not available** | 404 / 403. |

Stream codecs on this server (from `/api/go2rtc/streams`):

| Stream | Video | Audio |
|---|---|---|
| `<cam>_main` | H.264 High (level 4.0 / 5.0) | PCMA 8 kHz (+ backchannel) |
| `<cam>_sub` | **H.265 / HEVC** | PCMA 8 kHz |

`ruang_belakang_sub` had no active producer when probed.

## Realtime WebSocket

`WS /ws` — Verified. Messages are JSON `{"topic": string, "payload": any}`.
Topics mirror the MQTT topics without the `frigate/` prefix (MQTT itself is
disabled on this server). Observed:

```json
{"topic": "garasi/status/detect", "payload": "online"}
```

Not used yet. Planned use (later phase; verify each topic before relying on it): camera
status (`<camera>/status/<role>`), `events`, `reviews`, `stats`,
`camera_activity`. Reconnect with exponential backoff and de-duplicate by
event or review `id` plus `type`.
