# Quern TV

Native Android phone + Android TV client compatible with Stremio addons (protocol v3), Debrid services, and personal media servers. Package: `com.quern.app`.

## Stack

- Kotlin 2.1.0, AGP 8.7.3, KSP 2.1.0-1.0.29, JDK 17
- Jetpack Compose BOM 2024.12.01 + Material3 + TV Material 1.0.0, Navigation Compose 2.8.5
- Media3 (ExoPlayer, HLS, DASH, OkHttp datasource) 1.5.1
- Coil 2.7.0, Room 2.6.1, OkHttp 4.12.0, kotlinx.serialization 1.7.3, Coroutines 1.9.0, ZXing 3.5.3
- minSdk 24, targetSdk/compileSdk 35

Layout: `app/src/main/java/com/quern/app/` holds `data/` (addons, debrid, servers, metadata, sync, local), `domain/model/`, `player/`, `ui/` (`QuernApp.kt`, `screens/`, `components/`, `theme/`), and `di/AppContainer.kt`. `sync/supabase_schema.sql` is the optional schema users run in their own Supabase project.

## Hard rules

- The app is client-only. Never bundle, hardcode, or suggest any addon, source, or content.
- Never read, edit, or print `local.properties`, keystores, signing configs' secrets, or API keys.
- After every change, verify it with `./gradlew assembleDebug`. A step is not done until the build passes.
- Ask before deleting files or adding or upgrading dependencies.
- Don't commit or push unless the user asks.
- Use free tools only.
- Keep the repo private. Don't publish it or change its visibility until the user confirms the app works on the TV.
- Work one step at a time and stop after each step for review.

## Signing and CI

- Local release builds are signed with the owner's release keystore through the git-ignored `keystore.properties` at the repo root (`app/build.gradle.kts`). When that file is missing, release falls back to the debug key.
- Never read, print, or commit `keystore.properties` or the `.jks`. Check signing only with `apksigner verify --print-certs`.
- CI (`.github/workflows/build-apk.yml`, `gradle assembleRelease`) has no keystore, so it signs with its own debug key. Never install a CI APK over a locally built one: the signatures don't match.
- The user creates and holds the release keystore. Claude never creates one.

## TV testing (Google TV Streamer, Android 14, over adb)

- Install with `adb install -r` only. Never `adb uninstall` or `pm clear`: user data lives on the device.
- After every release install, run `adb shell cmd package compile -f -m speed-profile com.quern.app`.
- Don't change any TV system settings.
- For UI and focus checks, drive the app with `adb shell input keyevent ...` and capture with `adb exec-out screencap -p`.

## Performance measurement protocol (identical every time)

Run `.\tools\measure.ps1` from the repo root after installing the release build and compiling speed-profile. Don't change the script's path or timing between measurements; if it must change, take a new baseline first. The script does exactly this:

1. Cold start ×3: `am force-stop`, then `am start -W -n com.quern.app/.MainActivity`, and record `TotalTime`.
2. Waits 10 s for Home content to load.
3. Fixed start position: focus starts on rail Home, then RIGHT, DOWN, DOWN, which lands on the first card of row 2.
4. Each run: `dumpsys gfxinfo com.quern.app reset`, then 20× DPAD_RIGHT followed by 20× DPAD_LEFT (40 keys, ending where it started), with `sleep 0.12` between keys in one on-device shell loop. Because `input keyevent` takes about 150 ms to spawn, the real interval is about 265 ms, so a run takes about 10.6 s. It does 3 runs.
5. Discards runs with fewer than 100 frames.
6. Prints cold starts, per-run frames, janky %, p50/p90/p95/p99, the totals, and TOTAL PSS (`dumpsys meminfo`).

Report the raw table and compare against the latest baseline. Baselines taken with a different path or timing aren't comparable.

## Step reports

End every step with a short report that covers:
- what changed, with `file:line`
- build results
- the measurement table, when performance is involved
- what the user should test by hand on the TV

## Communication

Reply to the user in Arabic, using masculine address, directly and concisely. Write code, comments, commit messages, and prompts in English.
