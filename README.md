# Quern

A native media client for **Android TV / Google TV** and **Android phones**, built with Kotlin, Jetpack Compose and Media3 ExoPlayer. One APK runs on both, with a D-Pad-first TV interface and a touch interface for phones.

> **Quern ships empty.** It does not host, provide, bundle or recommend any content, catalog or addon. You connect your own Stremio-compatible addons, your own media servers and services you are authorised to use.

---

## Features

### TV-first interface
- Full D-Pad navigation with clear focus states. Focus returns to where you left it when you come back from the side rail.
- Three navigation styles: left sidebar, auto-hide sidebar, or top bar.
- Home with a hero spotlight that follows the focused title, Continue Watching, and configurable rows, card size, shape and corners.
- 11 colour themes (including a true-black Glossy Black for OLED) and 6 launcher icons and TV banners.

### Addons
- Stremio addon protocol v3: catalogs, metadata, streams and subtitles.
- Install by manifest URL or from your phone: scan the QR code on the TV and paste URLs, keys and server details in your phone's browser (same Wi-Fi), with no remote typing.
- Per-addon health checks, ordering and per-catalog visibility on Home.
- Streams appear as each addon answers, so one slow addon doesn't block the rest.

### Services and servers (all optional, nothing preconfigured)
- **Debrid:** TorBox, Real-Debrid, AllDebrid, Premiumize. Shows cache status and lets you browse your cloud library.
- **Personal servers:** Jellyfin, Emby, Plex, WebDAV.
- **Metadata and tracking:** TMDB and Trakt (device-code sign-in and scrobbling).

### Playback
- Media3 ExoPlayer with HLS and DASH. Preferred audio and subtitle language, subtitle size, colour and sync offset, playback speed, aspect ratio, and buffer presets for 4K remuxes.
- Resume from where you stopped, including the last-watched episode of a series.
- **Dolby Vision profile 7** (dual-layer UHD Blu-ray remuxes): on devices without a profile 7 decoder, Quern plays the HEVC base layer as HDR10 instead of failing. Other Dolby Vision profiles play natively when the device supports them.
- Optional external player: VLC, MX Player, Just Player, mpv and other installed players.

### Profiles, sync and backup
- Multiple profiles with an optional PIN lock. PINs are stored hashed, with lockout after repeated wrong attempts.
- Move your setup between TVs and phones with a portable sync code (`QUERN1:…`), or keep devices in sync through **your own** free Supabase project.
- Import addons, library and watch progress from a Stremio account or a JSON backup.

### Languages
- Follows the system language or a manual choice. Arabic (right-to-left) and English are fully translated. French, Spanish, German, Turkish, Russian and Portuguese cover the main screens; newer settings fall back to English.

---

## Privacy and security

- Debrid keys, server passwords and tokens are encrypted with AES-256-GCM through the Android Keystore and are never stored in plain text.
- Official service endpoints are HTTPS-only. Plain HTTP is allowed only for user-entered addresses such as LAN media servers and self-hosted addons.
- No analytics, no ads, no tracking. Quern only connects to the addons, servers and services you add.

---

## Install

1. Download the latest `Quern-vX.Y.Z-release.apk` from the [Releases](https://github.com/ShadowStudioApps/Quern/releases) page.
2. **Phone:** open the APK and allow installing from your browser or file manager.
3. **Android TV / Google TV:** use a sideloading app such as Downloader with the APK link, or install over adb: `adb install -r Quern-vX.Y.Z-release.apk`.

Requires Android 7.0 (API 24) or newer. Tested on a Google TV Streamer (Android 14).

### Signing

Official APKs on the Releases page are signed by **Shadow Studio**:

```
CN=SHADOW STUDIO, O=SHADOW STUDIO
SHA-256: efa7e9bcfd7171fbdd0f8a002b3f48d92936d6c477b792d0831f8f69f0f03b33
```

Check a download with `apksigner verify --print-certs <file>.apk`. Updates from the Releases page install over each other. An APK from any other source (including the CI test builds) has a different signature: Android will refuse to update over it, and uninstalling clears the app's data.

## Build from source

Needs JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Without your own `keystore.properties` it's signed with your local debug key.

GitHub Actions builds every push to `main` as a short-lived, debug-signed **test build**. Use the Releases page for real installs.

## Cloud sync (optional)

1. Create a free project at [supabase.com](https://supabase.com).
2. Run [`sync/supabase_schema.sql`](sync/supabase_schema.sql) in its SQL editor.
3. In Quern, open **Settings → Accounts & Sync → Account** and enter your project URL and anon key.

Your data stays in your own project.

## Tech stack

Kotlin 2.1 · Jetpack Compose (Material 3 + TV Material) · Media3 1.5 · Coil · Room · OkHttp · kotlinx.serialization

## License

[MIT](LICENSE)
