# Movie max Android app

Plain Java WebView app (no AndroidX) that opens the active website from Firebase `app_settings/active_site`.

Release an update:
1. Raise `versionCode` and `versionName` in `app/build.gradle`.
2. Build: `gradle assembleRelease` (needs JDK 17 + Android SDK 34). Keep `app/moviemax.keystore` — updates must be signed with it.
3. Copy `app/build/outputs/apk/release/app-release.apk` to the website's `public/downloads/MovieMax.apk`.
4. Set the same `versionCode`/`versionName` in the website's `public/app-version.json` and publish the website.
Installed apps see the new version, download it automatically and show "tap to install".
