# Quern — Open-Source Android TV & Phone Media Aggregator

**Quern** is a high-performance, open-source native Android & Android TV media client built with **Kotlin, Jetpack Compose, and Media3 ExoPlayer**. One universal ~3.3 MB R8-optimized APK runs natively on both **Android Phones** and **Android TV / Google TV** with True AMOLED `#000000` support.

> **Disclaimer:** Quern is a clean, empty media interface and player. It does not host, provide, or distribute any media content. Users connect their own addons, personal servers, and authorized services.

---

## Key Features

- **Universal Phone + Android TV / Google TV UI:**
  - Full D-Pad remote navigation with visible focus halos & `BackHandler`.
  - **3 Navigation Styles:** Left Sidebar, Auto-Hide Slim Sidebar, or **Top Bar (Desktop / TV)** full-width cinema mode.
  - **True AMOLED Pure Black (`#000000`)** + 6 distinct themes including **Light Dark Blue (`#345372`)** and **Light Gray (`#D3D3D3`)**.
  - **Dynamic External App Launcher Icons** (Slate Blue, Light Gray, Pure AMOLED White, Ocean Blue) for both Phone & TV banners.
- **Multi-Language + Auto System Language:**
  - Automatically follows your device language (`System Default`), plus manual switching for **Arabic (RTL), English, French, Spanish, German, Turkish, Russian, and Portuguese**.
- **Stremio Addon Protocol v3 + QR Phone Companion:**
  - Scan the built-in **QR Code** in **Addons** or **Settings → Account** from your phone browser (same Wi-Fi) to paste `manifest.json` URLs, Debrid keys, and server credentials straight to your TV—zero remote typing.
- **Linked Servers & Cloud Services (AES-256 Keystore Encrypted):**
  - **Debrid:** TorBox, Real-Debrid, AllDebrid, Premiumize (instant cache check, hash resolution, cloud library browser).
  - **Personal Servers:** Jellyfin, Emby, Plex, and WebDAV / Custom HTTP Servers.
  - **Metadata & Tracking:** TMDB (Arabic/English) and Trakt.tv (TV-friendly OAuth Device Code + scrobbling).
- **Multi-Screen Account Sync:**
  - Sync Addons and Settings across multiple TVs and phones via your own free **Supabase** project (`sync/supabase_schema.sql`) **or** instant **Portable Sync Codes (`QUERN1:...`)**.
- **Native Media3 ExoPlayer:**
  - Preferred Subtitle Language (10+ languages incl. Arabic), Preferred Audio Language, Subtitle size/color styling, playback speed, aspect ratio, Continue Watching, and optional External Player launch (VLC / Nova / MX Player).

---

## Build Locally or via GitHub Actions

```bash
./gradlew assembleRelease
```

Output APK: `app/build/outputs/apk/release/app-release.apk`

To enable free Cloud Account Sync across multiple TVs/phones, run [`sync/supabase_schema.sql`](sync/supabase_schema.sql) in a free Supabase project and enter your Project URL + Anon Key in **Settings → You → Account & Cloud Sync**.
