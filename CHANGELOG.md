# Changelog

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
