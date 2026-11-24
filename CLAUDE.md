# Quern TV

Native Android phone + Android TV client compatible with Stremio addons (protocol v3), Debrid services and personal media servers. Package `com.quern.app`, version 1.7.0 (code 15).

## Repository

- GitHub: `ShadowStudioApps/Quern`, **public**, MIT license. `main` is the only branch on the remote.
- CI: `.github/workflows/build-apk.yml` runs `gradle assembleRelease` on every push to `main` and uploads a debug-signed **test build** artifact (kept 7 days).
- Releases: official APKs go to GitHub Releases as `Quern-vX.Y.Z-release.apk`, built locally and signed with the Shadow Studio keystore. Publish one only when the user asks. Bump `versionCode`/`versionName` for each new release.
- `.gitignore` excludes `local.properties`, `keystore.properties`, `*.jks`/`*.keystore`, `apk/`, `build/` and `*.ps1`. `tools/measure.ps1` is force-added on purpose.

## Stack

- Kotlin 2.1.0, AGP 8.7.3, KSP 2.1.0-1.0.29, JDK 17
- Jetpack Compose BOM 2024.12.01 (UI 1.7.6) + Material3 + TV Material 1.0.0, Navigation Compose 2.8.5
- Media3 1.5.1 (ExoPlayer, HLS, DASH, OkHttp datasource)
- Coil 2.7.0, Room 2.6.1, OkHttp 4.12.0, kotlinx.serialization 1.7.3, Coroutines 1.9.0, ZXing 3.5.3, CameraX 1.5.3 (phone QR scanner)
- minSdk 24, targetSdk/compileSdk 35

Layout: `app/src/main/java/com/quern/app/` holds `data/` (addons, debrid, servers, metadata, sync, local, companion), `domain/model/`, `player/`, `ui/` (`QuernApp.kt`, `QuernViewModel.kt`, `screens/`, `components/`, `theme/`) and `di/AppContainer.kt`. `sync/supabase_schema.sql` is the optional schema users run in their own Supabase project.

## Hard rules

- The app is client-only. Never bundle, hardcode or suggest any addon, source or content, not even by name in code comments.
- The repo is public. Before every push, scan the diff for secrets, private IPs and personal data. Placeholders such as `192.168.1.10` or `you@example.com` are fine.
- Never read, edit or print `local.properties`, `keystore.properties`, keystores or API keys.
- After every change, verify it with `./gradlew assembleDebug` (and `assembleRelease` before installing on the TV). A step isn't done until the build passes.
- Ask before deleting files or adding or upgrading dependencies.
- Don't commit or push unless the user asks. Work on a branch, and merge into `main` only when asked.
- Use free tools only.
- Work one step at a time and stop after each step for review.

## Signing

- Local release builds are signed with the owner's keystore through `keystore.properties` at the repo root (read in `app/build.gradle.kts`). Without that file, release falls back to the debug key.
- Release certificate: `CN=SHADOW STUDIO, O=SHADOW STUDIO`, SHA-256 `efa7e9bcfd7171fbdd0f8a002b3f48d92936d6c477b792d0831f8f69f0f03b33`. Check signing only with `apksigner verify --print-certs`. The certificate must never contain personal names or locations.
- CI has no keystore and signs with its own debug key. Never install a CI APK on the TV.
- The user creates and holds the keystore. Claude never creates one, never asks for its password and never handles it.

## TV testing (Google TV Streamer "kirkwood", Android 14, adb over Wi-Fi)

- Install with `adb install -r` only. Never `adb uninstall` or `pm clear` without the user's explicit approval for that one time: user data lives on the device.
- The TV has two profiles (user 0 and user 10). A package removed from one profile is still installed in the other.
- If the adb server restarts, reconnect with `adb connect`. The TV may show an "Allow USB debugging?" prompt that the user must approve.
- After every release install, run `adb shell cmd package compile -f -m speed-profile com.quern.app`.
- Don't change any TV system settings.
- Drive the UI with `adb shell input keyevent …` and capture with `adb exec-out screencap -p`. Keycodes: 19 up, 20 down, 21 left, 22 right, 23 OK, 4 back, 224 wake.
- Inside Settings, never send OK (23) blind: take a screenshot first and confirm what has focus. A blind OK once applied the "TV" device preset and changed the user's navigation style.
- The TV can fall asleep between steps (`dumpsys power` → `mWakefulness=Asleep`, screenshots come back black). Send 224 before continuing.

## Performance measurement protocol (identical every time)

Run `.\tools\measure.ps1` from the repo root after installing the release build and compiling speed-profile. Don't change its path or timing; if it must change, take a new baseline first.

1. Cold start ×3: `am force-stop`, then `am start -W -n com.quern.app/.MainActivity`, and record `TotalTime`.
2. If the "Who's watching?" picker is showing (checked with `uiautomator dump`), presses OK to keep the active profile. Then waits 10 s for Home to load.
3. Start position: rail Home, then RIGHT, DOWN, DOWN, which lands on the first card of row 2.
4. Each run: `dumpsys gfxinfo reset`, then 20× DPAD_RIGHT and 20× DPAD_LEFT with `sleep 0.12` in one on-device loop. The real interval is about 265 ms because `input keyevent` takes about 150 ms to spawn. It does 3 runs of about 10.6 s each.
5. Discards runs with fewer than 100 frames.
6. Prints cold starts, per-run frames, janky %, p50/p90/p95/p99, totals and TOTAL PSS.

Latest baseline (after the glass theme + floating rail, Shadow profile, same path): janky about 6–6.4%, p50 40–48 ms, p90 48–61 ms, PSS about 83–88 MB. The commit before it measured 3%, p50 34–38 ms on the same content, so the translucent design costs a few ms per frame. Home content (synced addons) changes these numbers, so compare builds on the same day. Older: after STEP 4 janky about 37–38%, p50 61–65 ms. Earlier B1 (after SB): janky 56.4%, p50 73–77 ms, PSS 88.8 MB. Cold start varies by about 100 ms between sessions; treat it as noise unless the change is larger. The first run after launch often has a p99 spike.

## How key pieces work (don't regress)

- **Rail focus** (`ui/QuernApp.kt`, `QuernTvNavigationRail`): one persistent `Row` per tab, never `if (expanded) Row else Box`. The outer rail `Box` wraps both the tab column and the bottom profile button in a single `focusGroup()` with `focusProperties { enter = selected tab's requester }`, so LEFT from any content row (including lower rows) always enters on the selected tab; the profile button is reached via DOWN from the last tab (and UP returns to it). Pressing BACK from another tab switches to Home and re-syncs rail focus to the Home tab (`focusSyncSignal`).
- **Floating rail:** the rail is drawn over full-bleed content (no solid column). Screens pad their TV content by `LocalTvRailInset`. The content focus group starts at `railInset` (`padding(start = railInset)` before `restoreLastContentFocus`) and a `layout` uses `placeRelative(-insetPx, 0)` so content runs edge-to-edge behind the rail in both LTR and Arabic RTL.
- **Backgrounds:** `SoftGlowBackground` (1/8-size bitmap, drawn once) sits behind every page as the fallback; pages and the Scaffold are transparent. On Home and Detail, `AmbientBlurredBackdrop` (`ui/components/SoftGlowBackground.kt`) decodes the active title's artwork at 48×27 px (`Precision.EXACT`, memory-cached), bakes the dark theme veil (darker on Glossy/monochrome) + radial vignette once off-thread (`AmbientVeilTransformation`), stretches it full-screen with hardware bilinear filtering (`FilterQuality.Low`), and crossfades for ~380 ms only on item change. Never animate it, add `Modifier.blur`, or draw per-frame full-screen radial gradients (measured 72–79% janky on this TV).
- **RIGHT back to the same item:** `@Composable Modifier.tvClickable` (`ui/components/AdaptiveComponents.kt`) attaches a remembered `FocusRequester` whenever `LocalContentFocusMemory.current != null`, records it on `isFocused`, and clears it only on `DisposableEffect.onDispose` (cards keep a stable modifier chain with focus rings drawn in `drawWithCache` so losing focus never detaches the requester). Pressing `DPAD_RIGHT` (`DPAD_LEFT` in RTL) inside `QuernTvNavigationRail` invokes `onPreviewKeyEvent` → `ContentFocusMemory.restore()`, which requests focus directly on that exact remembered `FocusRequester` (falling back to the Home Hero primary CTA on fresh launch, or normal geometric search on tabs without a remembered item). Don't use `focusRestorer` or custom `FocusRequesterModifierNode.requestFocus()` on `.clickable()` cards (Compose 1.7.6 `DelegatingNode` quirks caused focus to fall through).
- **Hero banner & Home spacing:** On TV, the living hero (~72% of screen height) is item 0 of the TV `LazyColumn` so Continue Watching (16:9 overlay cards) peeks at the bottom of the first screen and the hero scrolls away smoothly as you browse down with zero horizontal clip line and zero `CompositingStrategy.Offscreen` FBO (an offscreen top fading edge measured 93.2% janky). Focus across both Continue Watching and poster rows goes through a `MutableStateFlow` with a 220 ms `collectLatest` debounce feeding `AmbientBlurredBackdrop` and `HomeHeroSection` without recomposing `HomeScreen` or rows; auto-slide runs only while nothing is focused. `LumaHeroBanner` shows the title's meta logo (Stremio meta `logo` or TMDB `/images`, falling back to large bold text), a single metadata line, a 2-line synopsis, a smart Resume/View Details CTA, and a top-right `HH:mm` clock on TV. `buildHeroBackdropRequest` (`720×405`, `HeroMeltTransformation`) is shared between the hero and the visibility-aware next-2-cards preloader so Coil caches only one transformed bitmap per title. `QuernHomeSpacing` defines shared gaps and rail-aligned insets; `HomeSkeletonScreen` matches the final sizes while loading.
- **Home catalogs & manifest refresh** (`data/addons/AddonRepository.kt`, `data/QuernRepository.kt`): Home shows all enabled Home-eligible catalogs with no per-addon or total cap, ordered strictly by addon priority (`Settings > Addons`) then manifest catalog order (no `catalogSortWeight` re-sorting), with Continue Watching first. Catalogs requiring an extra stay off Home unless every required extra (excluding `search`) declares non-empty `options`, in which case the first option is fetched and titled `<Catalog> · <option>` (unless hidden in `hiddenCatalogKeys`). To keep start-up fast, the first 8 addon catalogs load in the initial batch and remaining catalogs stream in background batches of 6, appending to `LazyColumn` without moving focus or scroll. Installed addon manifests re-fetch automatically on app start at most once every 6h and manually via `Settings > Addons > Refresh Addons`, updating `manifestJson`/`name`/`baseUrl` in place while keeping `hiddenCatalogKeys` valid for catalogs that still exist.
- **Poster cards:** Coil `crossfade(false)`, one 150 ms tween read only in `graphicsLayer` (6% lift) and `drawWithCache` (soft accent glow + focus border via `drawOutline`), genre and rating overlays rendered on focus only (zero idle badge nodes).
- **Rejected:** `allowRgb565` for posters. It needs `allowHardware(false)`, which measured +24 MB PSS and no jank gain.
- **Dolby Vision** (`player/DolbyVisionFallback.kt`, `QuernRenderersFactory`): when no DV decoder supports profile 7 (`dvhe.07`), the track is presented as HEVC and the base layer plays as HDR10. Verified on the TV with `c2.mtk.hevc.decoder`. The TV's DV decoders cover profiles 4, 5, 8, 9 and 10 only.
- **Data safety:** Room no longer uses `fallbackToDestructiveMigration`, so any schema change needs a version bump plus a migration. `SecureVault` stores only AES-GCM (`ENC:`); legacy `RAW:` entries migrate on read.
- **Account sync** (`data/sync/QuernSyncEngine.kt`, `docs/account-sync-format.md`): per-device-class settings live in `settings_tv` (TV hardware) and `settings_phone` (non-TV hardware), routed via `isTvHardware(context)` (`AdaptiveComponents.kt`) so phones never overwrite TVs. `activeProfileId` stays local per device. On a new device that starts with one profile, `QuernApp` watches `profilesAppliedCount` (incremented after `pullAll()` finishes `profiles` + `secrets`) and opens "Who's watching?" once if `profiles.size > 1` and it has not been shown this launch.
- **Account sign-out:** confirm, flush pending sync sections, then clear the Android device's account data and reset settings (including interface size). If offline or a save fails, offer Cancel or Sign out anyway. Delete account clears the same data after server deletion. Keep the device ID and restore from the server on the next sign-in without uploading a placeholder profile.
- **TV QR sign-in & Phone "Link a TV":** the signed-out TV account page shows the device-link QR + 6-digit code + LAN base URL, and advertises `_quern-link._tcp` via Android NSD (TXT: `v`, `n`, `s`, `pk` — nothing secret) ONLY while the QR is on screen. A signed-in phone has **Link a TV** (`PhoneLinkTvDialog`) in its Account card: it discovers TVs via NSD, offers **Scan QR** (isolated `PhoneQrScannerHost` with CameraX 1.5.3 + ZXing Y-plane in-RAM decoding, shown only when `PackageManager.FEATURE_CAMERA_ANY` is true, requesting `CAMERA` permission only on tap, releasing the camera immediately on close, never loaded on TV), and reveals a manual `http://<ip>:8787` field after ~8s (`GET /api/link/discover`). All three paths feed `DeviceLinkApproveDialog` (6-digit code, single-use ECDH + HKDF-SHA256 + AES-256-GCM envelope, 5-attempt lockout, LAN-only hosts). The QR URL also supports `quern://link` and browser email fallback. Companion connection failures are isolated per socket. Account session credentials are encrypted and committed together before notifying the UI.
- **Hardware feature gating** (`ui/components/AdaptiveComponents.kt`, `isTvHardware(context)`): always decide which device-specific features appear by real hardware (`isTvHardware(context)`), **never** `settings.uiModeOverride` (which only overrides layout). Phone-only features (`Link a TV`, LAN TV discovery, in-app QR scanner, and future touch gestures/downloads) must never appear anywhere on a TV — not even in Settings or Settings search — and camera features additionally check `PackageManager.FEATURE_CAMERA_ANY`. TV-only features (the "sign in this TV" QR, Phone Companion QR, rail/remote hints) must never appear on a phone or tablet.
- **App updates** (`data/update/AppUpdateManager.kt`): checks `api.github.com/repos/ShadowStudioApps/Quern/releases` at most once every 12h automatically (silent on rate limits/offline) and on manual request, respects `AppSettings.updateChannel` (STABLE skips prereleases, BETA includes them), compares semantic versions, accepts only `Quern-vX.Y.Z-release.apk`, downloads to `cacheDir/updates/`, and opens the system installer via `FileProvider` (never auto-installs; explains unknown-sources block on Android 8+ TV). Shown as a dot on Settings in the rail/dock, a card at the top of Settings > System, and a one-time non-blocking banner on Home ("Later" hides until the next version).
- **Window layouts** (`ui/components/AdaptiveComponents.kt`, `QuernLayoutClass`): `QuernApp` provides `LocalQuernLayout` from `currentQuernLayout(settings.uiModeOverride)` (`AUTO` = real hardware via `isTvHardware(context)` -> `TV`, non-TV `screenWidthDp >= 600` -> `WIDE`, `< 600` -> `COMPACT`; `TV`/`PHONE` override layout only). Rotation and fold/unfold reclassify independently of `Interface size`. Hardware-only features (TV QR sign-in, Phone Companion QR, `settings_tv` vs `settings_phone`) remain strictly gated by `isTvHardware(context)`, never `uiModeOverride`. On non-TV, `QuernFloatingPhoneDock` keeps `navigationBarsPadding()` above system bars and every non-TV scrollable screen reserves bottom padding for dock + nav bar. Don't force blanket `sizeIn(48.dp)` in `Modifier.tvClickable` (breaks small pills/badges); apply `>= 48 dp` touch targets explicitly on compact interactive buttons, tabs, pills, and rows.
- **Interface size** (`ui/QuernApp.kt`, `Settings > Appearance`): steps 75/80/85/90/95/100% (`AppSettings.screenScalePercent`, synced per device class in `settings_tv` / `settings_phone`). Implemented at the root of `QuernApp` via `CompositionLocalProvider(LocalDensity provides Density(density * scale, fontScale))` across the full edge-to-edge screen (no shrinking frame, no black borders, never `graphicsLayer`) so layouts re-measure to full screen and fit more posters/rows with crisp text and exact focus rects; covers Home, Settings, profile picker, and player, and `QuernAlertDialog` re-injects `LocalQuernScaledDensity` into dialog windows. Poster and Continue Watching Coil decode sizes follow `LocalDensity`.
- **Sync code** (`QUERN1:`): holds only enabled addon URLs and a few settings. It doesn't include Debrid keys, servers, profiles or watch history.

## Open items

- Full JSON backup export (history, favorites, servers, profiles, optional keys) so users can move devices without losing data.
- French, Spanish, German, Turkish, Russian and Portuguese lack the new settings strings (only `values` and `values-ar` exist).
- All theme presets now have a transparent `borderTint` (design choice from the settings redesign).
- v1.7.0 is the first public release. The next release needs a `versionCode` bump (currently 15).
- Fixed in PART 1: "QUERN" logo wrap (`maxLines = 1, softWrap = false`), race-free `firstRowFocusRequester` targeting Continue Watching (or first catalog row) on DOWN from "View Details", rail focus syncing back to Home on BACK from another tab, LEFT from lower rows entering the rail on the selected tab instead of the profile button, and removal of the legacy BYO Supabase sign-in/push/pull path from `CloudSyncManager` (keeping Stremio import, JSON/backup import, and `QUERN1:` portable sync code).

## Step reports

End every step with a short report that covers:
- what changed, with `file:line`
- build results
- the measurement table, when performance is involved
- what the user should test by hand on the TV

## Communication

Reply to the user in Arabic, using masculine address, directly and concisely. Write code, comments, commit messages and prompts in English.
