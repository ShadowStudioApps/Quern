# Quern account sync format (v1)

The shared contract between the Android app and the Quern website. Both read and write the same rows in the Supabase table `quern_sync_sections` (see `sync/supabase_schema.sql`). Any change to this format needs a new `formatVersion` and must stay readable by the previous app release.

## Accounts and keys

Users sign up with email + password. **The raw password never leaves the device.** Both the app and the website derive two values from it:

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
| `settings` | no | All entries of `AppSettings` except the per-device ones: `profiles`, `activeProfileId`, `appIcon`, `uiModeOverride`, `navigationStyle`, `cardSize`, `fontScale`, `bufferProfile`, `playerEngine`, `preferExternalPlayer`, `preferredExternalPlayerPackage`. `hiddenCatalogKeys` travels in `addons`. |
| `profiles` | no | Profile ids, names, avatar colours (no PINs). |
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
Enum values use their Kotlin `name`. Unknown keys are ignored and unknown enum values fall back to the default, so older apps survive newer websites.

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
- **First link** (a device signs in to an account that already has data): `addons`, `profiles` and `secrets` are merged (server items first or winning, then items only this device had), `settings` takes the server copy. Afterwards it's last writer wins as below.
- `secrets` only accepts real PIN hashes (`PinHasher` format) in `profilePins`; anything else is ignored. A profile missing from `profilePins` has no PIN.
- If an encrypted section can't be decrypted with this device's key:
  - the row was last written **before** this device signed in (server clock, `user.last_sign_in_at`): the password changed and this device has the newer key, so it re-uploads its own copy;
  - otherwise this device holds an old key: it stops uploading encrypted sections and asks the user to sign in again. It never overwrites data it couldn't read with an old key.
- **Merging:**
  - `favorites` and `progress`: per item, the newest `updatedAt` wins (deletions included).
  - `addons`, `settings`, `profiles` and `secrets`: the whole section, last writer wins. The website and the TV both edit these deliberately, so the newest edit is the intended one.
- Each device keeps its own `device_<id>` row up to date and never writes another device's row.
- **Sign-out** keeps local data and stops syncing. "Delete account" calls `rpc/quern_delete_my_account`, which removes the user and every row.
