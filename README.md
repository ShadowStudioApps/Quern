# Quern

A native media client for **Android TV / Google TV** and **Android phones**, built with Kotlin, Jetpack Compose and Media3 ExoPlayer. One APK runs on both, with a D-Pad-first TV interface and a touch interface for phones.

[Download Quern v1.8.0](https://github.com/ShadowStudioApps/Quern/releases/download/v1.8.0/Quern-v1.8.0-release.apk) · [Release notes and screenshots](https://github.com/ShadowStudioApps/Quern/releases/tag/v1.8.0)

> **Quern ships empty.** It does not host, provide, bundle or recommend any content, catalog or addon. You connect your own Stremio-compatible addons, your own media servers and services you are authorised to use.

---

## Features

### TV-first interface
- Full D-Pad navigation with clear focus states. Focus returns to where you left it when you come back from the side rail.
- Three navigation styles: left sidebar, auto-hide sidebar, or top bar.
- Home with a hero spotlight that follows the focused title, Continue Watching, and configurable rows, card size, shape and corners.
- TV, wide-screen and compact phone layouts, with interface size adjustable from 75% to 100%.
- 11 colour themes, including true-black Glossy Black for OLED, and six Glass Prism Q launcher icons with matching TV banners. Icon changes apply after leaving Quern.

### Addons
- Stremio addon protocol v3: catalogs, metadata, streams and subtitles.
- Install by manifest URL or from your phone: scan the QR code on the TV and paste URLs, keys and server details in your phone's browser (same Wi-Fi), with no remote typing.
- Per-addon health checks, ordering and per-catalog visibility on Home.
- Home loads all eligible selected catalogs in order. Use **Refresh Addons** to update installed manifests on demand.
- Streams appear as each addon answers, so one slow addon doesn't block the rest.

### Services and servers (all optional, nothing preconfigured)
- **Debrid:** TorBox, Real-Debrid, AllDebrid, Premiumize. Shows cache status and lets you browse your cloud library.
- **Personal servers:** Jellyfin, Emby, Plex, WebDAV.
- **Metadata and tracking:** TMDB and Trakt (device-code sign-in and scrobbling).

### Playback
- Media3 ExoPlayer with HLS and DASH. Preferred audio and subtitle language, subtitle size, colour and sync offset, playback speed, aspect ratio, and buffer presets for 4K remuxes.
- Resume from where you stopped, including the last-watched episode of a series.
- **Play / Resume** selects a source directly. Early failures can switch sources automatically, and manual source switching keeps your playback position. Source results stay on the device.
- TV frame-rate matching restores the prior display mode when you exit. **Playback info** shows video, audio, refresh rate, buffer and dropped frames.
- **Dolby Vision profile 7** (dual-layer UHD Blu-ray remuxes): on devices without a profile 7 decoder, Quern plays the HEVC base layer as HDR10 instead of failing. Other Dolby Vision profiles play natively when the device supports them.
- Optional external player: VLC, MX Player, Just Player, mpv and other installed players.

### Profiles, sync and backup
- Multiple profiles with an optional PIN lock. PINs are stored hashed, with lockout after repeated wrong attempts.
- Choose illustrated avatars, a custom avatar URL, and a background for each profile.
- A Quern account syncs addons, supported service settings, profiles, favorites and watch progress. TV and phone settings sync separately.
- Move a subset of your settings and enabled addon URLs with a portable sync code (`QUERN1:…`).
- Import addons, library and watch progress from a Stremio account or a JSON backup.

### Languages
- Follows the system language or a manual choice. Arabic (right-to-left) and English are fully translated. French, Spanish, German, Turkish, Russian and Portuguese cover the main screens; newer settings fall back to English.

---

## Privacy and security

- Debrid keys, server passwords and tokens are encrypted with AES-256-GCM through the Android Keystore and are never stored in plain text.
- Official service endpoints are HTTPS-only. Plain HTTP is allowed only for user-entered addresses such as LAN media servers and self-hosted addons.
- Quern includes no ads or analytics. It checks GitHub for app updates and uses the Quern account service when you sign in.

---

## Install

1. Download the latest `Quern-vX.Y.Z-release.apk` from the [Releases](https://github.com/ShadowStudioApps/Quern/releases) page.
2. **Phone:** open the APK and allow installing from your browser or file manager.
3. **Android TV / Google TV:** use a sideloading app such as Downloader with the APK link, or install over adb: `adb install -r Quern-vX.Y.Z-release.apk`.

Requires Android 7.0 (API 24) or newer. Tested on a Google TV Streamer (Android 14). Existing users can check **Settings → System** for updates and release notes.

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

## Quern account sync (optional)

1. Open **Settings → Accounts & Sync → Account** and create or sign in to a Quern account.
2. On a signed-in phone, choose **Link a TV** and scan the account QR shown on the TV, or select a TV discovered on your local network. Confirm the code displayed on the TV.

When you sign out, Quern offers to sync pending changes before clearing the account data from that device. Signing back in restores the synced data. See the [account sync documentation](docs/account-sync-format.md) for the data format.

## Tech stack

Kotlin 2.1 · Jetpack Compose (Material 3 + TV Material) · Media3 1.5 · Coil · Room · OkHttp · kotlinx.serialization

## License

[MIT](LICENSE)
