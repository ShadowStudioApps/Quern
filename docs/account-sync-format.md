# Quern account sync format (v1)

The shared contract between the Android app and the Quern website. Both read and write the same rows in the Supabase table `quern_sync_sections` (see `sync/supabase_schema.sql`). Any change to this format needs a new `formatVersion` and must stay readable by the previous app release.

## Accounts and keys

Users sign up with email + password. **The raw password is derived locally for direct sign-in and is never sent to Supabase. The separate local browser fallback is described below.** Both the app and the website derive two values from it:

| Value | How | Used for |
|---|---|---|
| `masterKey` (32 bytes) | PBKDF2-HMAC-SHA256(password, salt = `"quern-v1:" + lowercase(trim(email))`, 600 000 iterations) | Only to derive the two below. Kept in memory, never stored. |
| `authSecret` | base64url-no-padding(HMAC-SHA256(key = masterKey, message = `"quern-auth-v1"`)) | Sent to Supabase Auth **as the account password**. |
| `encKey` (AES-256) | HMAC-SHA256(key = masterKey, message = `"quern-enc-v1"`) | Encrypts the private sections. Stored on the device in the Android Keystore-backed `SecureVault`; held in session memory on the website. |

The password and the salt are UTF-8 bytes. Passwords need at least 8 characters. Reference: `app/src/main/java/com/quern/app/data/sync/AccountCrypto.kt`.

Consequences:
- Supabase (and Shadow Studio) can't read encrypted sections and never sees the real password.
- "Forgot password" creates a new password, so it **can't decrypt old encrypted sections**. Any signed-in TV still has the plaintext data locally and re-uploads it encrypted with the new key once the user signs in again with the new password. If no device has the data, those sections start empty. The UI must say this before a reset.
- Changing the password while signed in: decrypt with the old `encKey`, then re-encrypt everything with the new one, then update the Supabase password to the new `authSecret`.

### Encrypted payload format

`v1.<base64url(iv 12 bytes)>.<base64url(AES-256-GCM ciphertext + 16-byte tag)>`, plaintext UTF-8 JSON. The row is stored with `encrypted = true`.

## Sections

One row per `(user_id, section)`. `payload` is JSON (or an encrypted payload when `encrypted = true`). Every JSON payload has `"formatVersion": 1`.

| Section | Encrypted | Content |
|---|---|---|
| `addons` | yes | Installed addons, order, enabled flag, hidden catalogs. Manifest URLs often contain personal tokens. |
| `secrets` | yes | Debrid API keys, media servers (with passwords and tokens), TMDB key, Trakt tokens, profile PIN hashes. |
| `settings` | no | Shared entries of `AppSettings` across all devices (excludes `profiles`, `activeProfileId`, `hiddenCatalogKeys`, and the per-device-class keys in `settings_tv` / `settings_phone`). `hiddenCatalogKeys` travels in `addons`. |
| `settings_tv` | no | Per-device-class settings shared across Android TV devices only (`navigationStyle`, `cardSize`, `fontScale`, `bufferProfile`, `playerEngine`, `preferExternalPlayer`, `preferredExternalPlayerPackage`, `appIcon`, `uiModeOverride`, `screenScalePercent`). Read and written only by TV hardware. |
| `settings_phone` | no | Same keys and schema as `settings_tv`, shared across phones/tablets only. Read and written only by non-TV hardware so a phone never overwrites a TV's layout. |
| `profiles` | no | Profile ids, names, avatar colours (no PINs). `activeProfileId` stays local per device. |
| `favorites` | no | Library/favorites list, with deletions kept as tombstones. |
| `progress` | yes | Watch progress / Continue Watching. Encrypted because it reveals viewing history; `lastStreamUrl` is never synced (it may contain tokens). |
| `device_<id>` | no | One row per device: name, model, app version, last seen. `<id>` is 12 random lowercase letters, generated once per install. |

### `addons`
```json
{
  "formatVersion": 1,
  "addons": [
    { "manifestUrl": "https://…/manifest.json", "name": "…", "enabled": true, "order": 0 }
  ],
  "hiddenCatalogKeys": ["<addonId>:<type>:<catalogId>"]
}
```
`manifestJson` isn't synced: each device fetches the manifest itself.

### `secrets`
```json
{
  "formatVersion": 1,
  "debrid": { "TORBOX": "…", "REAL_DEBRID": "…", "ALL_DEBRID": "…", "PREMIUMIZE": "…" },
  "servers": [
    { "id": "…", "name": "…", "type": "JELLYFIN", "baseUrl": "http://…", "username": "…", "userId": "…", "password": "…", "token": "…", "enabled": true }
  ],
  "tmdb": { "apiKey": "…", "language": "ar-SA" },
  "trakt": { "clientId": "…", "clientSecret": "…", "accessToken": "…", "refreshToken": "…", "username": "…" },
  "profilePins": { "<profileId>": "<PinHasher output>" }
}
```

### `settings`
```json
{ "formatVersion": 1, "values": { "theme": "GLOSSY_BLACK", "language": "ARABIC", "subtitleLanguage": "ARABIC", "...": "..." } }
```
Enum values use their Kotlin `name`. Unknown keys are ignored and unknown enum values fall back to the current/default value, so older apps survive newer websites or newer app releases.

### `settings_tv` and `settings_phone`
```json
{
  "formatVersion": 1,
  "values": {
    "navigationStyle": "SIDEBAR_LEFT",
    "cardSize": "STANDARD",
    "fontScale": "STANDARD",
    "bufferProfile": "BALANCED",
    "playerEngine": "MEDIA3_EXOPLAYER",
    "preferExternalPlayer": false,
    "preferredExternalPlayerPackage": "",
    "appIcon": "SLATE_BLUE",
    "uiModeOverride": "AUTO",
    "screenScalePercent": 100
  }
}
```
Device-class routing uses Android hardware TV detection (`UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION` or `Configuration.UI_MODE_TYPE_TELEVISION`, independent of `uiModeOverride`):
- Android TV devices read and write only `settings_tv` and never read or write `settings_phone`.
- Phones and tablets read and write only `settings_phone` and never read or write `settings_tv`.
- Unknown keys in `values` are ignored and unknown enum values keep the device's current value. `screenScalePercent` (`75`–`100`, default `100`) scales root `LocalDensity` edge-to-edge so more posters and rows fit on screen without black borders. `activeProfileId` is never synced so each device still asks "Who's watching?".

### `profiles`
```json
{ "formatVersion": 1, "profiles": [ { "id": "main", "name": "…", "avatarColorHex": 4282339954, "avatarKey": "rocket" } ] }
```
`avatarKey` is one of the built-in picture keys in `ui/components/ProfileAvatars.kt` (`paw`, `rocket`, `gamer`, …) or `null` for initials on `avatarColorHex`. Unknown keys fall back to initials. PIN hashes travel only in the encrypted `secrets` section.

### `favorites` and `progress`
```json
{ "formatVersion": 1, "items": [ { "profileId": "main", "contentId": "tt…", "title": "…", "mediaType": "movie", "posterUrl": "…", "updatedAt": 1763880000000, "deleted": false } ] }
```
Items are per profile: `(profileId, contentId)` is the key. `favorites` items also carry `backdropUrl`, `year`, `rating`, `origin` and `sourceId` (`updatedAt` is when it was added). `progress` items also carry `backdropUrl`, `positionMs`, `durationMs`, `episodeId` and `episodeLabel`. A deleted item is `{ "profileId", "contentId", "updatedAt": <deletion time>, "deleted": true }`; devices keep deletions for 60 days (table `sync_tombstones`, Room v3), then drop them.

### `device_<id>`
```json
{ "formatVersion": 1, "name": "Living room TV", "model": "Google TV Streamer", "platform": "android-tv", "appVersion": "1.8.0", "lastSeen": 1763880000000 }
```

## Sync rules

- **Pull** on sign-in, on app start, when returning to the foreground, and every 10 minutes while open. Only sections whose server `revision` is newer than the last one this device saw get downloaded.
- **Push** a section a few seconds after it changes locally (debounced; `progress` waits until it has been quiet for 30 s, since playback saves it every few seconds), using an optimistic check: `PATCH …?user_id=eq.<uid>&section=eq.<s>&revision=eq.<lastSeen>`. If no row was updated, someone else changed it first: pull, merge, push again. A section that doesn't exist yet is created with `POST` (a `409` means another device created it first: pull).
- Each device remembers, per section, the last revision it saw and a hash of its local data right after the last pull or push. It uploads only when that hash changes, so data that came from the server is never echoed back.
- **First link** (a device signs in to an account that already has data): `addons`, `profiles` and `secrets` are merged (server items first or winning, then items only this device had), `settings` and the device's own `settings_tv` / `settings_phone` section take the server copy. Afterwards it's last writer wins as below.
- **Profile picker after first sync**: if a freshly linked device starts with one local profile and the first sync pulls multiple profiles, the app opens "Who's watching?" once `profiles` (and any accompanying `secrets` PINs) have been applied, provided the picker has not already been shown during this launch.
- `secrets` only accepts real PIN hashes (`PinHasher` format) in `profilePins`; anything else is ignored. A profile missing from `profilePins` has no PIN.
- If an encrypted section can't be decrypted with this device's key:
  - the row was last written **before** this device signed in (server clock, `user.last_sign_in_at`): the password changed and this device has the newer key, so it re-uploads its own copy;
  - otherwise this device holds an old key: it stops uploading encrypted sections and asks the user to sign in again. It never overwrites data it couldn't read with an old key.
- **Merging:**
  - `favorites` and `progress`: per item, the newest `updatedAt` wins (deletions included).
  - `addons`, `settings`, `settings_tv`, `settings_phone`, `profiles` and `secrets`: the whole section, last writer wins. The website and the TV both edit these deliberately, so the newest edit is the intended one.
- Each device keeps its own `device_<id>` row up to date and never writes another device's row.
- **Sign-out** on Android asks for confirmation, then flushes pending changes across every section this device owns. If the server is unreachable, the credentials are stale, or a changed section conflicts, the user can cancel or confirm losing unsynced edits on this device. After confirmation and any save, Android clears its local account data, including the current device class's settings (including interface size), storage for all profiles, secrets, tombstones, and sync revisions and hashes. It keeps the installation's device ID. The next sign-in restores server sections without merging in the default placeholder profile. "Delete account" calls `rpc/quern_delete_my_account`, which removes the user and every row, then clears the same local data.

## Device linking

Signing in on an Android TV without typing an email or password uses an ephemeral, end-to-end encrypted LAN handshake between a signed-in phone (or, later, a signed-in `quern.pages.dev` browser session) and the TV's local companion server.

### Goals and security properties

- In the native-app handshake, the password and derived credentials never travel in plaintext over the LAN, and the confirmation code is only entered in the phone UI. No credentials or confirmation code appear in URLs or logs.
- A photo of the TV's QR code contains **nothing secret** (only the TV's ephemeral public key, random session id, and device name).
- Each device keeps its own Supabase session (own rotating refresh token): the phone sends `{ email, authSecret, encKey }` inside the encrypted envelope, and the TV calls Supabase `POST /auth/v1/token?grant_type=password` with `authSecret` to obtain its own independent session.
- On the phone, `authSecret` is stored alongside `encKey` in Android Keystore-backed `SecureVault` (AES-256-GCM) and cleared on sign-out.

### Handshake steps

1. **TV opens the QR** (while signed out on Settings → Accounts & Sync):
   - Generates an ephemeral EC P-256 key pair (`secp256r1`), a 128-bit random `sessionId` (base64url, 22 chars), and a random 6-digit confirmation code `code6` (`000000`–`999999`).
   - Displays the QR encoding:
     ```text
     http://<tv-ip>:8787/link#pk=<tvPublicKeySpkiB64Url>&s=<sessionId>&n=<urlEncodedDeviceName>
     ```
     and shows `code6` next to the QR (`XXX XXX`).
   - Session TTL is **5 minutes** and auto-refreshes when expired. The `/api/link/*` endpoints only accept requests while the QR is actually on screen; closing the card wipes the ephemeral private key immediately.
2. **Phone scans the QR**:
   - The TV's `GET /link` page reads the URL fragment in JS and offers **"Open in Quern"** via an Android `intent://link?host=...&pk=...&s=...&n=...#Intent;scheme=quern;package=com.quern.app;S.browser_fallback_url=...;end` link (handled by `MainActivity`'s `quern://link` intent filter).
   - If the app does not open, the page suggests opening it in Chrome and offers a browser email + password + 6-digit code form. The form sends those fields to the TV over local HTTP without transport encryption (`POST /link-fallback`, enforcing the same 5-minute TTL, active-QR gate, single-use rule, and 5-attempt lockout).
3. **Phone confirms and encrypts**:
   - The app validates that `host` is a private LAN origin (`10/8`, `172.16/12`, `192.168/16`, `169.254/16`, `127/8`, `localhost`, or `*.local`), queries `GET /api/link/info?s=<sessionId>`, and shows `"Sign in TV '<device name>'?"` asking for the 6-digit code shown on the TV.
   - On **Allow**, the phone generates its own ephemeral EC P-256 key pair, computes `sharedSecret = ECDH(phonePriv, tvPub)`, and derives a 32-byte AES-256-GCM key with HKDF-SHA256 (RFC 5869):
     - `salt = UTF-8("quern-link-v1:" + sessionId + ":" + code6)`
     - `info = UTF-8("quern-link-aes256gcm-v1:" + tvPublicKeyB64 + ":" + phonePublicKeyB64)`
     - `aad  = UTF-8("quern-link-v1:" + sessionId)`
   - Encrypts `{ "v": 1, "email": "...", "authSecret": "...", "encKey": "<base64url 32 bytes>" }` with AES-256-GCM (random 12-byte IV, 128-bit tag) and POSTs to `POST http://<tv-ip>:8787/api/link/complete`:
     ```json
     {
       "s": "<sessionId>",
       "pk": "<phonePublicKeySpkiB64Url>",
       "iv": "<base64url 12 bytes>",
       "ct": "<base64url ciphertext + 16-byte tag>"
     }
     ```
4. **TV verifies, signs in, and wipes**:
   - Checks the session is active, unexpired, unlocked, unused, and matches `sessionId` (constant-time).
   - Derives the same HKDF-SHA256 key using its ephemeral private key and displayed `code6`, and attempts AES-256-GCM decryption.
   - A wrong 6-digit code fails AEAD authentication (`403 wrong_code`); **5 wrong attempts lock the session** (`423 locked`) and wipe the ephemeral key.
   - On success, the TV **immediately marks the session completed and wipes the ephemeral key** (single-use; any replay returns `409 already_used`), calls Supabase password grant with `authSecret` for its own session, stores `encKey` and `authSecret` in `SecureVault`, closes the QR, and starts the initial sync (showing "Who's watching?" if multiple profiles are pulled).

Reference: `app/src/main/java/com/quern/app/data/sync/DeviceLinkCrypto.kt`, `DeviceLinkClient.kt`, and `data/companion/TvWebCompanionServer.kt`. All primitives (`P-256` SPKI, `ECDH`, `HKDF-SHA256`, `AES-256-GCM`) map 1:1 to browser WebCrypto for the future `quern.pages.dev` phone-browser flow.
