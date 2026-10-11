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
- **Tools**-Reiter mit dem Rechner **„Lohnt sich der Tarif?“**: effektiver
  Preis pro kWh mit Grundgebühr, ob das günstiger ist als ohne Tarif und ab wie
  viel km im Monat es sich lohnt. Tarifpreis, Grundgebühr, km im Monat und
  Anteil beim Tarif sind Schieberegler, das Ergebnis rechnet beim Schieben live
  mit. Vorbelegt aus den eigenen Ladevorgängen; der Preis ohne Tarif wahlweise
  automatisch aus dem, was an Anbietern mit Ladeort „Unterwegs“ bezahlt wurde
  (einstellbar beim Anbieter). Rechnet komplett auf dem Gerät
- **Vergleich mit Verbrenner** (Tools): Umschalter oben zwischen „Zeitraum“
  (was die gefahrenen km mit Benzin/Diesel gekostet hätten), „Pro 100 km“
  (Kosten und CO₂ je 100 km) und „Lebenszyklus“ (CO₂ inklusive Herstellung von
  Fahrzeug und Akku sowie Kraftstoff-Vorkette, mit dem Kilometerstand, ab dem
  der Herstellungs-Rucksack eingeholt ist). Rechnet auf dem Gerät.
- **Akku und Ladeverluste** (Tools, nur im Server-Modus, Server ab 0.28.0):
  Akku-Index je Quartal und Mehrbedarf beim Laden nach AC/DC und Anbieter.
- **Reichweitenrechner** (Tools): wie weit man von X auf Y % bei einer
  bestimmten Außentemperatur kommt. Ladestand, Temperatur, Verbrauch und (falls
  beim Fahrzeug nicht hinterlegt) Akkukapazität sind Schieberegler. Im
  Server-Modus (Server ab 0.34.0) folgt der Verbrauch der Temperaturkurve aus
  den eigenen Fahrten (ab Akku, Ladeverlust herausgerechnet), sonst dem
  Jahresdurchschnitt. Dazu auf dem Dashboard die Kachel **„Reale Reichweite
  (100 → 0 %)“** je Temperatur (nur Server-Modus, genau ein Fahrzeug).
- **Homescreen-Widget** mit Kosten und kWh des laufenden Monats, ab mittlerer
  Breite zusätzlich mit dem letzten Ladevorgang.
- **Kurzbefehle** (langes Drücken aufs App-Symbol): „Ladevorgang erfassen“
  öffnet direkt das Formular, dazu „Dashboard“ und – sobald ein Fahrzeug als
  Dienstwagen markiert ist – „Erstattungsbericht“.
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
- **Außentemperatur** je Ladevorgang erfassen und ansehen – Grundlage der
  Auswertung „Verbrauch nach Außentemperatur" im Server-Dashboard (ab
  Lademonitor-Server 0.23.0). Gemeint ist der Wert **beim Ladebeginn**, weil
  der Verbrauch eines Ladevorgangs von der Fahrt davor stammt. Funktioniert
  auch im „Nur lokal"-Modus.
- **Reifen** (Einstellungen → Reifen): jeden Reifenwechsel mit Art, Datum,
  Kilometerstand, Größe (bei Mischbereifung vorne und hinten getrennt), Marke
  und Modell eintragen. Dazu die Übersicht, wie lange ein Satz
  aufgezogen war, wie viele Kilometer und Fahrten auf ihm liegen und wie alt er
  seit der ersten Montage ist, plus der temperaturbereinigte Vergleich Winter
  gegen Sommer. Braucht Lademonitor-Server 0.26.1 oder neuer und ist **nur im
  Server-Modus** verfügbar: die Zuordnung der Fahrten und die Bereinigung
  rechnet der Server, damit App und Web nicht unterschiedliche Zahlen zeigen.
- **Fahrzeugkosten und Gesamtkosten:** am Fahrzeug Kauf (Kaufpreis, Kaufdatum,
  Erstzulassung, km-Stand beim Kauf) oder Leasing (Rate, Laufzeit,
  Sonderzahlung) hinterlegen – die App schätzt den heutigen Wert und schlägt
  einen monatlichen Wertverlust vor (fester Betrag möglich). Unter
  Einstellungen → Fahrzeuge → Fahrzeug → Kosten sonstige Ausgaben wie
  Versicherung, Steuer, Wartung oder Reparaturen erfassen, einmalig oder
  wiederkehrend, mit Übersicht je Jahr. Das Dashboard zeigt daraus den
  Abschnitt „Gesamtkosten“ (inkl. Kosten je km und Anteil Laden). Funktioniert
  in beiden Modi; mit Server ab 0.32.0 wird alles synchronisiert.
- **Erinnerungen:** unter Einstellungen → Fahrzeuge → Fahrzeug → Erinnerungen
  HU, Inspektion, Bremsflüssigkeit, Reifenwechsel, Versicherung, Kfz-Steuer,
  THG-Quote oder Eigenes – fällig nach Datum und/oder km-Stand, mit
  Wiederholung und Vorlauf. Der km-Stand kommt aus den Ladevorgängen, daraus
  auch die Prognose „bei deiner Fahrleistung etwa am …“. „Erledigt“ schreibt
  die nächste Fälligkeit fort und bietet an, die Kosten gleich einzutragen.
  Bald Fälliges steht oben im Dashboard, dazu lokale Mitteilungen (abschaltbar).
  Funktioniert in beiden Modi; mit Server ab 0.33.0 wird alles synchronisiert.
- **Erstattungsbericht Dienstwagen** (Tools → Erstattungsbericht): ein Fahrzeug
  als Dienstwagen markieren, Erstattungsart wählen (pauschal je kWh mit Sätzen
  „gültig ab“ oder tatsächliche Kosten, optional inkl. Grundgebühr) und Name,
  Personalnummer und Kennzeichen hinterlegen. Der Bericht listet je Monat oder
  frei gewähltem Zeitraum alle Heimladungen (Anbieter „Zuhause“) mit kWh,
  Herkunft der kWh (Wallbox, Fahrzeug oder geschätzt), Betrag und
  Monatssummen – teilbar als PDF oder CSV. Rechnet in beiden Modi lokal; mit
  Server ab 0.35.0 werden Einstellungen und Sätze synchronisiert, dort ist
  auch eine Monatsmail möglich.
- **Jahres- und Monatsrückblick** (Tools → Rückblick): ein Kalenderjahr oder
  -monat auf einen Blick – kWh, Kosten (inkl. Grundgebühren), km,
  Ladevorgänge, Ø Preis und Ø Verbrauch mit ± % zur Vorperiode, die Ersparnis
  in Euro und CO₂ gegenüber einem Verbrenner, Rekorde (günstigste/teuerste und
  größte Ladung, längste Strecke, kältester/wärmster Ladetag, meistgenutzter
  Ladeort und Anbieter, Monat mit dem geringsten/höchsten Verbrauch),
  Verteilung Zuhause/Arbeit/unterwegs und AC/DC sowie kWh je Monat.
  Fahrzeugfilter wie im Dashboard. Rechnet lokal nach denselben Regeln wie der
  Server, also auch im Local-Only-Modus.
- **Vergleichsfahrzeug** (Einstellungen): Kraftstoff, Verbrauch, Preis und
  Strommix für die Ersparnis im Rückblick; das Verbrenner-Tool startet damit und
  kann seine Werte als Vergleichsfahrzeug speichern. Mit Server ab 0.36.0 wird
  es synchronisiert, dort gibt es auch den Schalter „Jahresrückblick per Mail“.
- **Serverseitige Löschungen kommen an**: auf dem Server gelöschte Ladevorgänge,
  Fahrzeuge, Anbieter und Ladeorte verschwinden beim nächsten Abgleich auch aus
  der App, statt als „Geisterzeilen" stehenzubleiben. Braucht Lademonitor-Server
  0.22.0 oder neuer; gegen einen älteren Server verhält sich die App wie bisher.
  Aus dem bloßen Fehlen eines Eintrags in der Server-Antwort leitet die App
  weiterhin bewusst NICHTS ab – eine unvollständige Antwort würde sonst still
  lokale Daten vernichten.
- Mit Server 0.22.0 oder neuer kommen **alle** Ladevorgänge an; vorher deckelte
  der Server die Liste stillschweigend auf die 200 neuesten.

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
tools/
  generate_icons.py - erzeugt App-Icon und Logo aus dem Generator im Server-Repo
```

## App-Icon & Logo

App-Icon und das Zeichen im Erststart-Screen zeigen dieselbe Marke wie
Server- und iOS-App (Variante 17 „Angeschnitten"). Die Zeichnung liegt
bewusst nur an einer Stelle – in `design/logo/` des
[Server-Repos](https://github.com/iDomi94/Lademonitor-Server) –, zwei Kopien
würden früher oder später auseinanderlaufen. Neu erzeugen:

```bash
python3 tools/generate_icons.py --server ../Lademonitor-Server
```

Das schreibt das adaptive Icon (`mipmap-*dpi/ic_launcher_foreground.png` plus
`drawable/ic_launcher_background.xml`), die Kachel für den Erststart-Screen
(`drawable-nodpi/logo_mark.png`) und das Store-Bild
(`app/src/main/ic_launcher-playstore.png`, 512 px ohne Alphakanal).
Voraussetzungen: Pillow und ein Chromium/Chrome zum Rastern (Pfad notfalls
über `LADEMONITOR_CHROME`).

Der Vordergrund ist ein PNG und kein VectorDrawable: Androids
VectorDrawable kennt kein `stroke-dasharray` – und genau daraus besteht das
Kabel. Die Zeichnung sitzt auf 78 % der Kantenlänge und am rechten Rand der
Sicherheitszone ausgerichtet, damit die Gerätemaske die angeschnittene
Ladesäule links weiter anschneiden darf, ohne dem Fahrzeug die Schnauze
abzuschneiden.

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
