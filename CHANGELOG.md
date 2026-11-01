# Changelog

## 1.7 (unveröffentlicht)

- **Einführung beim ersten Start**: ein paar Seiten zu den wichtigsten
  Funktionen (Ladevorgänge erfassen, Dashboard, Kosten und Erinnerungen rund
  ums Auto, Tools) und zum Schluss der Unterschied zwischen „Nur lokal“,
  eigenem Server und Lademonitor-Cloud – danach geht es wie bisher zur
  Moduswahl. Überspringen geht jederzeit, die Moduswahl führt über
  „Einführung ansehen“ wieder zurück.
- Unter Einstellungen → Hilfe → „Einführung erneut ansehen“ lässt sie sich
  jederzeit wieder öffnen; die letzte Seite markiert dann den Modus, den du
  gerade nutzt. Wer die App schon eingerichtet hat, sieht sie einmalig
  nach dem Update. Gleicher Inhalt wie in der iOS-App 1.14.0.
- **€/100 km je Ladevorgang**: in der Liste steht neben dem Verbrauch jetzt
  auch, was die Strecke seit dem vorherigen Ladevorgang je 100 km gekostet
  hat („17,8 kWh/100km · 6,23 €/100km“). Live gerechnet aus Verbrauch und
  effektivem Preis je kWh inklusive Grundgebühr-Anteil; ohne Preis oder
  Verbrauch bleibt der Wert weg. Wie in der iOS-App 1.15.0.

## 1.6 (2026-10-29)

- **Sonnenanteil beim Laden zuhause**: neues optionales Feld „Davon aus dem
  Netz (kWh)“ beim Ladevorgang (`grid_energy_kwh`), der Rest der geladenen
  Energie kam kostenlos von PV-Anlage oder Hausakku. Die Detailansicht zeigt
  die Aufteilung („☀ Sonne/Hausakku 78 % (12,4 kWh) · Netz 22 % (3,5 kWh)“),
  die Liste ein kleines „☀ 78 %“ neben AC/DC, das Dashboard den Sonnenanteil
  des Zeitraums unter „Gesamt kWh“. Der Netzbezug darf nicht größer als die
  geladene Energie sein.
- Automatisch gefüllt wird das Feld von Home Assistant (Integration ab 0.7.0)
  über Lademonitor-Server ab 0.41.0; `price_total` sind dann die gemessenen
  Kosten des Netzanteils. Im Server-Modus wird der Wert in beide Richtungen
  synchronisiert, im „Nur lokal"-Modus lokal gespeichert (Room 10 → 11, neue
  Spalte per Migration, keine Daten gehen verloren).

## 1.5 (2026-10-27)

- **Mehrverbrauch nach Tempo und Temperatur.** Neues Feld „Ø km/h seit
  Laden“ beim Ladevorgang (Durchschnittsgeschwindigkeit aus dem
  Bordcomputer), synchronisiert und auch im Local-Only-Modus. Im Server-Modus
  (ab Lademonitor-Server 0.47.0) zeigt die Detailansicht den Verbrauch
  aufgeteilt in dein Normal, Tempo, Temperatur und Rest. Room-Migration 9 → 10.
- **Ø Preis je Anbieter.** Neuer Abschnitt im Dashboard unter den
  Kuchendiagrammen: jeder Anbieter einzeln, der günstigste zuerst, mit
  Durchschnittspreis je kWh inklusive Grundgebühren, Anzahl der Vorgänge, kWh
  und Kosten. Lädt man bei einem Anbieter AC und DC, stehen beide Preise
  getrennt darunter. Vorgänge ohne Preis zählen wie im Ø Preis/kWh oben mit
  0 € und werden je Anbieter genannt. Folgt Zeitraum- und Fahrzeugfilter,
  funktioniert auch im Local-Only-Modus.

## 1.4 (2026-10-27)

- **Gesamtpreis beim Bearbeiten.** Ein neu eingetragener Gesamtpreis bleibt
  jetzt stehen, der Preis je kWh wird daraus berechnet. Bisher hat der alte
  kWh-Preis den Gesamtpreis beim Speichern wieder überschrieben. Im
  Server-Modus braucht es dafür zusätzlich Lademonitor-Server 0.46.1.

## 1.3 (2026-10-23)

- **„Lädt gerade“.** Ein laufender Ladevorgang erscheint ganz oben im
  Dashboard und als grün markierter Eintrag über der Ladevorgangsliste – mit
  Beginn, Start-SoC und aktuellem SoC samt Ladebalken, bisher geladenen kWh
  (gemessen oder geschätzt), AC/DC, km-Stand, Außentemperatur und dem
  Zeitpunkt des letzten Berichts. Nur im Server-Modus und ab
  Lademonitor-Server 0.45.0; Quellen sind MyŠkoda, VW-Datenportal, Renault
  und Home Assistant (ab Lademonitor-HA 0.9.0).
- **Kein ungewolltes Abmelden mehr.** Der verschlüsselte Speicher für die
  Anmeldung ließ sich direkt nach einem Neustart oder einem App-Update (und auf
  manchen Geräten zufällig) kurz nicht öffnen. Die App las dann keine Anmeldung
  und zeigte den Login, obwohl sie noch gültig war. Jetzt wird erneut versucht,
  die Anmeldung bleibt im Speicher, und beim Zurückholen der App in den
  Vordergrund wird sie wieder aufgenommen.
- Eine Server-Antwort „nicht angemeldet“ auf eine noch laufende Anfrage mit
  einer älteren Anmeldung (z. B. direkt nach einem Passwortwechsel) meldet
  nicht mehr ab.

## 1.2 (2026-10-14)

Angleichung an die iOS-App:

- **Karte**: Ladevorgänge werden je nach Zoom zu Zahlen-Clustern
  zusammengefasst (dieselbe Rechnung wie in iOS und im Web), zu prüfende
  orange markiert. Ein Tipp zoomt in das Cluster hinein; liegen die Vorgänge
  praktisch am selben Punkt (dieselbe Wallbox), öffnet sich stattdessen eine
  Liste. Neuer Knopf „Auf aktuellen Standort zoomen“ mit blauem Standortpunkt.
- **Einstellungen** gegliedert wie in iOS: die Verwaltung steht oben und
  enthält jetzt auch **Kosten** und **Erinnerungen** (mit mehreren Fahrzeugen
  erst die Auswahl). Modus, Konto und Server-Adresse liegen gebündelt unter
  „Server & Verbindung“ und sind auch im lokalen Modus erreichbar.
- **Fällige Erinnerungen** im Dashboard öffnen die Liste aller Fahrzeuge statt
  nur des einen; neue Erinnerungen fragen dort zuerst nach dem Fahrzeug.
- **Kurzbefehl „Fällige Erinnerungen“** am App-Symbol (sobald es Erinnerungen
  gibt) – Gegenstück zum gleichnamigen Siri-Kurzbefehl der iOS-App.
- Der Code wird jetzt bei jedem Pull Request auf GitHub gebaut.

## 1.1 (2026-10-11)

- **Rückblick** (Tools): Jahres- und Monatsrückblick mit Umschalter Jahr/Monat,
  Fahrzeugfilter, großen Zahlen mit ± % zur Vorperiode, Ersparnis gegenüber
  dem Verbrenner (Euro und CO₂), Rekorden, Verteilung Zuhause/Arbeit/unterwegs
  und AC/DC sowie kWh je Monat. Lokal gerechnet nach den Regeln von
  `GET /api/stats/review` (Server 0.36.0), auch im Local-Only-Modus.
- **Vergleichsfahrzeug** in den Einstellungen (Kraftstoff, l/100 km, €/l,
  Strommix). Das Verbrenner-Tool startet mit diesen Werten und bietet
  „Als Vergleichsfahrzeug speichern“ an. Im Server-Modus über
  `/api/combustion-vehicle` synchronisiert; ältere Server werden still
  übersprungen.
- **Jahresrückblick per Mail**: neuer Schalter neben dem Monatsbericht in den
  Benachrichtigungen (Server ab 0.36.0, Standard an).
