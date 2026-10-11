# Changelog

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
