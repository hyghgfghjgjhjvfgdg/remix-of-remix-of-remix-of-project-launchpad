# Android app
- Android app lives in `mobile-app/` (plain Java WebView, no AndroidX; see its README): it reads the live domain from Firestore `app_settings/active_site` (auto-saved by admins' dashboard visits) and self-updates from the site's `public/app-version.json` + `public/downloads/MovieMax.apk`. Why: domain changes and releases must not need a Play Store.
