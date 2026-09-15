# Lademonitor – Android

**Sprache:** Deutsch | [English](README.en.md)

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)

Android-App (Kotlin + Jetpack Compose) für [Lademonitor](https://github.com/iDomi94/Lademonitor-Server) –
das Android-Pendant zur iOS-App. Portiert 1:1 die Architektur und Funktionen der
SwiftUI-App: ein **offline-fähiger lokaler Speicher** (Room) plus ein
**Server-Modus mit bidirektionaler Synchronisierung**.

## Features (wie in der iOS-App)

- **Zwei Modi:** „Nur lokal auf diesem Gerät“ (alle Daten in einer lokalen
  Room-Datenbank) oder „Mit eigenem Server verbinden“ (Login/Registrierung,
  Token verschlüsselt gespeichert, laufende Synchronisierung mit dem Server).
- **Dashboard** mit Kennzahlen und Diagrammen (Donut-Diagramme pro Anbieter,
  AC/DC-Aufteilung, Kosten/kWh/Verbrauch pro Monat) – lokal berechnet, damit es
  auch offline nicht leer bleibt.
- **Ladevorgänge** ansehen, anlegen, bearbeiten, bestätigen (needs_review),
  löschen (Wischgeste). Verbrauchsanzeige (kWh/100km) mit derselben
  Fallback-Kette wie die iOS-App.
- **Karte** (OpenStreetMap via osmdroid, kein API-Key nötig) mit allen Ladeorten
  (inkl. Matching-Radius) und Ladevorgängen; Antippen öffnet Detail/Bearbeiten.
- **Fahrzeuge, Anbieter, Ladeorte** verwalten; Adresssuche (im lokalen Modus per
  OSM-Nominatim, im Server-Modus über den Server-Proxy) und „Aktueller Standort“.
- **Zeitraum-Filter** (Presets + eigener Zeitraum), global für Dashboard,
  Ladevorgänge und Karte.
- **Anmelden mit Nutzername oder E-Mail-Adresse**; bei der Registrierung kann
  optional eine Adresse hinterlegt werden.
- **Konto-Einstellungen:** E-Mail-Adresse hinterlegen und bestätigen, eigenes
  Passwort ändern, Benachrichtigungen des Servers ein-/ausschalten
  (fehlgeschlagenes Backup, MyŠkoda-Fehler, Monatsbericht, Sammelmeldung über zu
  prüfende Ladevorgänge), Konto samt aller Server-Daten löschen. Braucht
  Lademonitor-Server 0.14.0 oder neuer (Konto löschen: 0.16.0) – gegen einen
  älteren Server bleibt der Bereich ausgeblendet.
- **„Passwort vergessen“:** die App fordert den Link an, gesetzt wird das neue
  Passwort über den Link in der Mail im Browser.
- **Nachfrage beim Anmelden:** meldest du dich an einem Konto an, mit dem dieses
  Gerät noch nie synchronisiert hat, und liegen schon Daten auf dem Gerät, fragt
  die App nach – hochladen oder vom Gerät löschen. Auf dem Server wird dabei nie
  etwas gelöscht.

## Einsatzmöglichkeiten

- **Standalone lokal:** läuft komplett offline auf dem Gerät, kein Server nötig.
- **Selbst gehostet:** eigener
  [Lademonitor-Server](https://github.com/iDomi94/Lademonitor-Server), Open
  Source und per Docker betrieben – volle Kontrolle über die eigenen Daten.
- **Lademonitor-Cloud** (`lademonitor.cloud`): kein eigener Serverbetrieb nötig,
  Updates und Backups übernimmt der Betreiber. In der App direkt über die
  Hosting-Auswahl neben der Server-Adresse wählbar.

Server-Modus (selbst gehostet oder Cloud) bringt zusätzlich automatische
Ladevorgangs-Erkennung über Home Assistant sowie Zugriff vom Web-UI aus, mit
bidirektionaler Synchronisierung zwischen App, Web-UI und weiteren Geräten.

## Voraussetzung

Für den Server-Modus ein laufender
[Lademonitor-Server](https://github.com/iDomi94/Lademonitor-Server) – oder die
Lademonitor-Cloud. Beim ersten Start die Server-Adresse (Domain, `https://` wird
automatisch ergänzt) bzw. „Lademonitor-Cloud“ wählen und Nutzername oder
E-Mail-Adresse samt Passwort eingeben. Der „Nur lokal“-Modus funktioniert ohne
Server.

**Hinweis:** Wird das Passwort zurückgesetzt oder geändert, meldet der Server
alle Geräte ab. Bei einer Änderung in den Konto-Einstellungen bleibt diese App
angemeldet (der Server liefert den neuen Zugang direkt mit, ab Server 0.14.1);
nach einem Zurücksetzen über den Mail-Link ist eine neue Anmeldung nötig.

## Bauen & Ausführen

1. Ordner `Lademonitor-Android/` in **Android Studio** öffnen
   (Giraffe/Koala oder neuer, mit Android SDK 35).
2. Gradle-Sync abwarten (lädt AGP 8.6, Kotlin 2.0, Compose BOM, Room, osmdroid …).
   Sollte Android Studio neuere Plugin-Versionen vorschlagen, kann man sie
   übernehmen.
3. Auf Gerät/Emulator (Android 8.0 / API 26 oder neuer) ausführen (Run ▶).

Alternativ per Kommandozeile (Android SDK vorausgesetzt, `local.properties`
mit `sdk.dir=` wird von Android Studio automatisch angelegt):

```bash
./gradlew assembleDebug
```

Die fertige APK liegt danach unter `app/build/outputs/apk/debug/`.

## Projektstruktur

```
app/src/main/java/com/dominiqueherbrigpersonalteam/lademonitor/
  data/
    model/       - DTOs, Enums, Payloads (Pendant zu Models.swift)
    remote/      - ApiClient (OkHttp), Moshi-Setup, Server-Datumsformat
    local/       - Room-Entities, DAOs, Datenbank (Pendant zu LocalModels/LocalStore)
    settings/    - AppSettings (Modus/Server-URL), TokenStore (verschlüsselt)
    session/     - SessionManager (Auth-Zustand)
    net/         - NetworkMonitor (Erreichbarkeit)
    location/    - CurrentLocationProvider (GPS ohne Google Play Services)
    repo/        - AppRepository, LocalDataStore, SyncService,
                   LocalStats-/LocalConsumptionCalculator, LocalGeocoder
  ui/
    theme/       - Compose-Theme (hell/dunkel)
    auth/        - Modus-Auswahl, Login/Registrierung
    dashboard/   - Dashboard + selbstgezeichnete Diagramme
    sessions/    - Liste, Anlegen/Bearbeiten, Detail
    map/         - osmdroid-Karte + Mini-Karte im Detail
    settings/    - Einstellungen, Fahrzeuge/Anbieter/Ladeorte, Verbindung,
                   Konto (E-Mail, Passwort, Benachrichtigungen)
    filter/      - Globaler Zeitraum-Filter
    common/      - Formatierung, wiederverwendbare Bausteine
```

## Unterschiede zur iOS-App (bewusst)

- **Karte:** OpenStreetMap (osmdroid) statt Apple Maps – kein API-Key nötig.
  Ladevorgänge werden als Einzel-Marker gezeigt; das Karten-Clustering der
  iOS-App ist (noch) nicht portiert.
- **Lokale Adresssuche:** statt Apple `MKLocalSearch` wird im lokalen Modus
  direkt OSM-Nominatim angefragt (dieselbe Quelle, die der Server proxied).
- **Token-Speicher:** Android `EncryptedSharedPreferences` statt iOS-Keychain.
- **Tablet-Layout:** die iPad-Master-Detail-Ansicht der iOS-App ist nicht
  portiert; auf Android bleibt es bei Liste → Detail.

## Lizenz

AGPL-3.0 – passend zum Server- und iOS-Repo.
