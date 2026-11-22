# Security

This document covers two things: (1) the audit of the current home deployment,
and (2) the security design of the Rumput Vision app.

Audit date: 2026-11-22, performed from a LAN client (macOS) against
`192.168.100.22` / `frigate.home.arpa`. All probes were read-only `GET`
requests or WebSocket handshakes. No configuration was changed.

---

## 1. Deployment audit findings

Severity: **Critical** = must be fixed before the app is given real
credentials or remote access is relied on. **High** = fix soon.
**Info** = recommendation.

### S1 — Critical: Frigate API is reachable as an anonymous admin

| Endpoint | Result |
|---|---|
| `GET http://192.168.100.22/api/profile` (host Nginx, port 80) | `{"username":"anonymous","role":"admin",...}` |
| `GET http://192.168.100.22:5000/api/profile` (Frigate internal port) | same |
| `GET http://192.168.100.22:8971/` (Frigate authenticated port) | connection refused (not published) |

Frigate's own config has `auth.enabled: true`, but authentication is only
enforced on port **8971**. Port **5000** is documented as *"Internal
unauthenticated UI and API access. Access to this port should be limited."*
The host Nginx on port 80 forwards to port 5000, so **authentication is
bypassed for every client that can reach port 80 or 5000** — the whole LAN and,
via Tailscale subnet routing or a Tailscale node on the server, the tailnet.
An anonymous client has admin rights: it can delete recordings and events,
change config, and restart Frigate.

**Fix**

1. Stop publishing port 5000 to the LAN. In `docker-compose.yml`, remove the
   `5000:5000` mapping, or bind it to loopback only: `127.0.0.1:5000:5000`.
2. Publish 8971 instead (e.g. `127.0.0.1:8971:8971` if only the host Nginx
   uses it).
3. Point the host Nginx at the authenticated port:
   `proxy_pass https://127.0.0.1:8971;` — Frigate's built-in TLS on 8971 uses
   a self-signed certificate by default, so either set
   `proxy_ssl_verify off;` **only for this loopback upstream**, or set
   `tls.enabled: false` in Frigate and use `proxy_pass http://127.0.0.1:8971;`
   (traffic never leaves the host).
4. Keep WebSocket upgrade headers on the proxy (`/ws`, `/live/mse/api/ws`,
   `/live/webrtc/api/ws` all need them — they work today through port 80).
5. Add the host Nginx address to `auth.trusted_proxies` so Frigate's
   `X-Forwarded-For` handling and login rate limiting see real client IPs.
6. Set `auth.failed_login_rate_limit`, e.g. `"1/second;5/minute;20/hour"`.
7. Verify: `curl -s https://<host>/api/profile` must return **401** without a
   token.

### S2 — Critical: camera RTSP credentials are exposed to unauthenticated clients

`GET /api/config` and `GET /api/go2rtc/streams` return the go2rtc stream
sources, which contain the cameras' RTSP URLs **including the camera
usernames and passwords** (`rtsp://<user>:<password>@192.168.100.2xx:554/...`).
Combined with S1, anyone on the network can read them.

**Fix**

1. Fix S1 first.
2. Move camera credentials out of the config file into Frigate environment
   variables (`{FRIGATE_RTSP_PASSWORD}` substitution) so they are not stored
   in plain config, and restrict `/api/config` to admins (Frigate's 8971
   role enforcement does this).
3. **Rotate every camera password** after S1 is fixed. Treat the current ones
   as disclosed.
4. Put the cameras on an isolated VLAN/IoT network that only the Frigate host
   can reach.

The app never stores or logs the raw `/api/config` body. It parses only the
fields it needs (see `FRIGATE_API.md`).

### S3 — Critical: no HTTPS anywhere

- Port 443 is closed; Nginx serves plain HTTP on port 80.
- `auth.cookie_secure: false`.

Sending a Frigate password or JWT over plain HTTP on Wi-Fi exposes it to any
device on that network. **The app refuses to send credentials over HTTP**
(see §2.3), so login stays unavailable until HTTPS is set up.

**Fix (recommended order)**

1. **Tailscale HTTPS (simplest, works locally and remotely).** Enable MagicDNS
   and HTTPS certificates in the Tailscale admin console, then on the server
   run `tailscale cert <machine>.<tailnet>.ts.net`, and configure Nginx with
   the issued certificate/key (or use `tailscale serve --https=443
   http://127.0.0.1:8971`). The `*.ts.net` certificate is issued by Let's
   Encrypt and is trusted by Android without any app changes. Renew it
   periodically (`tailscale cert` again, or `tailscale serve`, which renews
   automatically).
2. **A real domain with DNS-01 Let's Encrypt** (e.g. `frigate.example.com`
   resolved by AdGuard to `192.168.100.22` on the LAN). Also trusted by
   Android without app changes.
3. **Private CA.** Android apps targeting API 24+ do not trust user-installed
   CAs by default. Rumput Vision deliberately does not opt in to user CAs
   in release builds. A private CA is therefore not supported for release
   builds.

`.home.arpa` names cannot get a publicly trusted certificate. With option 1
the app can use the same `https://<machine>.<tailnet>.ts.net` URL on both LAN
and mobile data, provided Tailscale is running on the phone.

Once HTTPS works, set `auth.cookie_secure: true`.

### S4 — High: RTSP restream (8554) is unauthenticated on the LAN

Port 8554 is open. Frigate documents that restream RTSP is unauthenticated by
default. Anyone on the LAN or tailnet can pull every camera's main stream.

**Fix:** set credentials in the go2rtc section:

```yaml
go2rtc:
  rtsp:
    username: "<user>"
    password: "<long random password>"
```

Frigate notes this does not apply to localhost consumers, so Frigate's own
recording pipeline is unaffected. If no LAN client needs RTSP, stop
publishing 8554.

### S5 — Info: other exposed services

| Port | Service | Note |
|---|---|---|
| 22 | SSH | Ensure key-only auth and `PermitRootLogin no`. |
| 8123 | Home Assistant | Out of scope; make sure HA has its own auth. |
| 8555 | go2rtc WebRTC | Fine on LAN. Never port-forward it to the internet. Rumput Vision does not use WebRTC yet (see `STREAMING.md`). |
| 1984 | go2rtc admin UI | Closed — good. Keep it closed. |
| 8971 | Frigate authenticated | Closed — needs to be used by Nginx (S1). |

### S6 — Info: Tailscale

- Use Tailscale ACLs so only your own devices can reach the server's
  port 443, not shared or tagged nodes.
- Do not use Tailscale Funnel for Frigate (that would publish it to the
  internet).
- Do not rely on `.home.arpa` from mobile data: AdGuard is only reachable
  on the LAN or via Tailscale DNS settings.

### S7 — Info: Frigate version

Installed `0.18.2-cfa08c5`. `/api/stats` reports `latest_version: 0.18.3`.
Update after reading the release notes.

### Things that were *not* verified

- Login, token refresh and roles on port 8971 could not be tested because
  8971 is not reachable. They are documented from the official docs and
  OpenAPI spec and must be re-verified once S1 is fixed (see `FRIGATE_API.md`).

---

## 2. App security design

### 2.1 Secrets at rest

| Data | Storage |
|---|---|
| Server profile metadata (name, URLs, username, auth mode) | Room `server_profiles` table. Not secret. |
| Password (optional, only if the user chooses "remember password") and JWT | Encrypted blob per profile in a dedicated DataStore file `secure_credentials.preferences_pb`. |
| App preferences | DataStore `app_preferences`. Not secret. |

Encryption: AES-256-GCM using a non-exportable key in the Android Keystore
(`core/security/KeystoreSecretCipher.kt`). StrongBox is used when the device
has it; otherwise the TEE-backed Keystore. Every blob has a format-version
byte, a random 12-byte IV, and uses the profile ID as GCM associated data,
so a blob copied to another profile fails authentication. If the key is lost
or invalidated (e.g. device restore), decryption fails closed: the credential
is discarded and the user is asked to sign in again.

`androidx.security:security-crypto` (EncryptedSharedPreferences) is
deprecated and is not used.

Backups: `android:allowBackup="false"` and data extraction rules exclude
all app data from cloud backup and device transfer. Keystore keys cannot be
restored, so backed-up ciphertext would be useless anyway.

### 2.2 Secrets in transit and logs

- No secrets in source code or in Gradle files.
- HTTP logging is off in release builds. Debug builds log only method, URL
  path and status code. `Authorization`, `Cookie` and `Set-Cookie` headers
  are redacted (`core/logging/Redactor.kt`, unit-tested).
- Passwords are never written to logs, crash messages or UI state that
  survives process death (`SavedStateHandle` never holds the password).

### 2.3 Transport policy

Implemented in `domain/usecase/ServerUrlPolicy.kt` (unit-tested). It runs at
profile-save time and will be enforced again at request time in Phase 2.

1. Only `http` and `https` URLs; no user-info, query or fragment.
2. **Credentials (Frigate login) require HTTPS on every configured URL.**
3. Plain HTTP is allowed only for profiles with **no authentication**, and
   only to private-network hosts: RFC 1918 IPv4, loopback, link-local, IPv6
   ULA, Tailscale CGNAT `100.64.0.0/10`, and the names `*.home.arpa`,
   `*.local`, `*.lan`, `*.internal`, single-label hostnames and `*.ts.net`.
   The UI shows a persistent "unencrypted" warning for these profiles.
4. Port 5000 triggers a warning that it is Frigate's unauthenticated internal
   port.
5. TLS validation is never disabled. There is no "trust all certificates"
   option, and the app does not trust user-installed CAs in release
   builds (`res/xml/network_security_config.xml`).

Android's network security config cannot list arbitrary IP addresses, so the
cleartext permission in the manifest is broad and the restriction above is
enforced by the app itself. In Phase 2 an OkHttp interceptor rejects any
cleartext request that would carry an `Authorization` header or cookie,
regardless of profile settings.

### 2.4 Authentication flow (Phase 2, based on docs; to verify on 8971)

- `POST /api/login` with `{"user": ..., "password": ...}`; the JWT arrives in
  the `frigate_token` cookie (cookie name from `auth.cookie_name` in
  `/api/config`).
- The app sends `Authorization: Bearer <jwt>` (documented as supported)
  instead of keeping a cookie jar.
- Token refresh: Frigate re-issues the cookie when the token is older than
  `auth.refresh_time`. The app captures any `Set-Cookie: frigate_token=` on
  responses and replaces the stored token.
- On `401`: if a remembered password exists, log in again once, otherwise
  move the profile to the "sign-in required" state. Never retry in a loop.
- Logout: `GET /api/logout`, then delete the token (and password if the user
  asks).

### 2.5 Notifications

No Firebase credentials ship in the APK. Push will come through a separate
relay service (see `ARCHITECTURE.md`), which holds the FCM service account.
