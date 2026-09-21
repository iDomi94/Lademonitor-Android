# Lademonitor – Android

**Language:** English | [Deutsch](README.md)

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)

Android app (Kotlin + Jetpack Compose) for [Lademonitor](https://github.com/iDomi94/Lademonitor-Server) –
the Android counterpart to the iOS app. It ports the SwiftUI app's
architecture and features 1:1: an **offline-capable local store** (Room)
plus a **server mode with bidirectional synchronization**.

## Features (same as the iOS app)

- **Two modes:** "Local only on this device" (all data in a local Room
  database) or "Connect to your own server" (login/registration, token
  stored encrypted, ongoing synchronization with the server).
- **Dashboard** with metrics and charts (donut charts per provider, AC/DC
  split, cost/kWh/consumption per month) – computed locally so it isn't
  empty even offline.
- **Charging sessions**: view, create, edit, confirm (needs_review),
  delete (swipe gesture). Consumption display (kWh/100km) using the same
  fallback chain as the iOS app.
- **Map** (OpenStreetMap via osmdroid, no API key needed) with all charging
  locations (incl. matching radius) and charging sessions; tapping opens
  detail/edit.
- **Vehicles, providers, charging locations** management; address search
  (in local mode via OSM Nominatim, in server mode via the server proxy)
  and "current location".
- **Time-range filter** (presets + custom range), global across dashboard,
  charging sessions, and map.
- **Sign in with username or e-mail address**; an address can optionally be
  stored during registration.
- **Account settings:** store and confirm an e-mail address, change your own
  password, switch the server's notifications on/off (failed backup, MyŠkoda
  error, monthly report, digest of sessions that need a review), delete the
  account including all server data. Needs Lademonitor Server 0.14.0 or newer
  (account deletion: 0.16.0) – against an older server the section stays hidden.
- **"Forgot password":** the app requests the link, the new password is set
  through the link in the mail in the browser.
- **Question on sign-in:** if you sign in to an account this device has never
  synced with and data is already on the device, the app asks – upload or delete
  from the device. Nothing is ever deleted on the server.
- **Outside temperature** per charging session – the basis of the "consumption
  by outside temperature" analysis in the server dashboard (from
  Lademonitor-Server 0.23.0). It means the value **at the start of charging**,
  because a session's consumption comes from the drive before it. Works in
  local-only mode too.
- **Server-side deletions arrive**: charging sessions, vehicles, providers and
  charging locations deleted on the server also disappear from the app on the
  next sync, instead of lingering as "ghost rows". Requires
  Lademonitor-Server 0.22.0 or newer; against an older server the app behaves
  as before. The app still deliberately infers NOTHING from an entry merely
  missing in the server's response - an incomplete response would otherwise
  silently destroy local data.
- With server 0.22.0 or newer **all** charging sessions arrive; before that the
  server silently capped the list at the 200 most recent ones.

## Deployment options

- **Standalone local:** runs entirely offline on the device, no server needed.
- **Self-hosted:** your own
  [Lademonitor Server](https://github.com/iDomi94/Lademonitor-Server), open
  source and run via Docker – full control over your own data.
- **Lademonitor Cloud** (`lademonitor.cloud`): no server operation of your own,
  updates and backups are handled for you. Selectable in the app right next to
  the server address via the hosting picker.

Server mode (self-hosted or cloud) additionally brings automatic charging-session
detection via Home Assistant and access from the web UI, with bidirectional
synchronization between app, web UI and further devices.

## Requirement

For server mode, a running
[Lademonitor-Server](https://github.com/iDomi94/Lademonitor-Server) – or the
Lademonitor Cloud. On first launch, enter the server address (domain, `https://`
is added automatically) or pick "Lademonitor Cloud", plus username or e-mail
address and the password. "Local only" mode works without a server.

**Note:** if the password is reset or changed, the server signs out all devices.
On a change in the account settings this app stays signed in (the server ships
the new access along, from server 0.14.1); after a reset via the mail link a new
sign-in is needed.

## Build & run

1. Open the `Lademonitor-Android/` folder in **Android Studio**
   (Giraffe/Koala or newer, with Android SDK 35).
2. Wait for the Gradle sync (downloads AGP 8.6, Kotlin 2.0, Compose BOM,
   Room, osmdroid, …). If Android Studio suggests newer plugin versions,
   you can accept them.
3. Run on a device/emulator (Android 8.0 / API 26 or newer) (Run ▶).

Alternatively via the command line (Android SDK required; `local.properties`
with `sdk.dir=` is created automatically by Android Studio):

```bash
./gradlew assembleDebug
```

The resulting APK is then located under `app/build/outputs/apk/debug/`.

## Project structure

```
app/src/main/java/com/dominiqueherbrigpersonalteam/lademonitor/
  data/
    model/       - DTOs, enums, payloads (counterpart to Models.swift)
    remote/      - ApiClient (OkHttp), Moshi setup, server date format
    local/       - Room entities, DAOs, database (counterpart to LocalModels/LocalStore)
    settings/    - AppSettings (mode/server URL), TokenStore (encrypted)
    session/     - SessionManager (auth state)
    net/         - NetworkMonitor (reachability)
    location/    - CurrentLocationProvider (GPS without Google Play Services)
    repo/        - AppRepository, LocalDataStore, SyncService,
                   LocalStats-/LocalConsumptionCalculator, LocalGeocoder
  ui/
    theme/       - Compose theme (light/dark)
    auth/        - Mode selection, login/registration
    dashboard/   - Dashboard + custom-drawn charts
    sessions/    - List, create/edit, detail
    map/         - osmdroid map + mini map in detail view
    settings/    - Settings, vehicles/providers/locations, connection,
                   account (e-mail, password, notifications)
    filter/      - Global time-range filter
    common/      - Formatting, reusable building blocks
tools/
  generate_icons.py - builds app icon and logo from the generator in the server repo
```

## App icon & logo

The app icon and the mark on the first-launch screen show the same brand as
the server and iOS apps (variant 17 "Angeschnitten"). The drawing lives in
exactly one place on purpose – `design/logo/` of the
[server repo](https://github.com/iDomi94/Lademonitor-Server) – since two
copies would drift apart sooner or later. To regenerate:

```bash
python3 tools/generate_icons.py --server ../Lademonitor-Server
```

This writes the adaptive icon (`mipmap-*dpi/ic_launcher_foreground.png` plus
`drawable/ic_launcher_background.xml`), the tile for the first-launch screen
(`drawable-nodpi/logo_mark.png`) and the store image
(`app/src/main/ic_launcher-playstore.png`, 512 px without an alpha channel).
Requirements: Pillow and a Chromium/Chrome for rasterizing (path via
`LADEMONITOR_CHROME` if needed).

The foreground is a PNG rather than a VectorDrawable: Android's
VectorDrawable has no `stroke-dasharray` – and that is exactly what the cable
is made of. The drawing sits at 78 % of the edge length, aligned to the right
edge of the safe zone, so the device mask may keep cropping the charging
station on the left without cutting off the car's nose.

## Differences from the iOS app (intentional)

- **Map:** OpenStreetMap (osmdroid) instead of Apple Maps – no API key
  needed. Charging sessions are shown as individual markers; the iOS app's
  map clustering is not (yet) ported.
- **Local address search:** instead of Apple `MKLocalSearch`, local mode
  queries OSM Nominatim directly (the same source the server proxies).
- **Token storage:** Android `EncryptedSharedPreferences` instead of the
  iOS Keychain.
- **Tablet layout:** the iPad master-detail view of the iOS app is not ported;
  on Android it stays list → detail.

## License

AGPL-3.0 – matching the server and iOS repos.
