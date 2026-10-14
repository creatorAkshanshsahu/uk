# AKTV v2 — by Akshansh

Native Android TV IPTV app (Kotlin + Jetpack Compose + Media3 ExoPlayer).
No WebView, so it runs the same on Android 9 and Android 13 TVs (minSdk 21).

Playlist: https://raw.githubusercontent.com/Indiblog/india-iptv/main/output/india_iptv.m3u

## Build the APK (no Android Studio needed)
1. Create a new GitHub repo (e.g. `aktv-v2`) and upload everything in this folder.
2. (Optional) Settings → Secrets and variables → Actions → add
   - `CLOUDFLARE_WORKER_URL` = `https://iptv-proxy.YOUR-NAME.workers.dev`
   - `PROXY_KEY` = any password you also set as `PROXY_KEY` in the Worker
3. Actions → "Build AKTV v2 APK" → Run workflow → download the `AKTV-v2-apk` artifact.

## Install on the TV
- Easiest: put the APK on Google Drive / a GitHub release and open the link with the "Downloader" app on the TV.
- Or: `adb connect <tv-ip>` then `adb install app-release.apk`
- Enable "Install unknown apps" for the installer app on the TV.

## Remote controls
- Home: ◀ ▶ ▲ ▼ browse, OK play, Menu = refresh playlist
- Player: ▲ / ▼ (or CH+/CH-) next/previous channel in that row, OK = show info, OK again = add/remove favourite, Back = exit

## Cloudflare Worker
`worker/cloudflare_worker.js` — see the chat instructions. If playback fails, the app automatically retries through the Worker.
