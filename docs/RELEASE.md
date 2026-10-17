# Android-App aufs Gerät bringen

Android braucht anders als iOS keinen Store und kein Entwicklerkonto: eine
signierte APK lässt sich direkt installieren. Der Workflow
`.github/workflows/release.yml` ist das Gegenstück zum TestFlight-Workflow der
iOS-App: ein Tag baut die App, signiert sie und hängt die APK an ein
GitHub-Release.

## Kurz: was gebraucht wird

| | iOS (TestFlight) | Android (GitHub-Release) |
|---|---|---|
| Konto | Apple Developer Program (99 €/Jahr) | keins |
| Secrets | `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` | `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` |
| Auslöser | Tag `vX.Y.Z` | Tag `vX.Y` oder `vX.Y.Z` |
| Auf dem Gerät | TestFlight-App | APK aus dem Release öffnen |

## 1. Signaturschlüssel einmalig anlegen

Android erkennt Updates an der Signatur: jede neue Version muss mit **demselben
Schlüssel** signiert sein, sonst lehnt das Gerät das Update ab. Der Schlüssel
wird einmal erzeugt und dann für immer verwendet.

Auf dem Mac im Terminal (`keytool` gehört zu Java bzw. Android Studio):

```bash
keytool -genkeypair -v \
  -keystore lademonitor-release.jks \
  -alias lademonitor \
  -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` fragt nach einem Passwort und ein paar Angaben (Name usw., beliebig).
Wird nur ein Passwort abgefragt, ist es für Keystore und Schlüssel dasselbe.

**Die Datei `lademonitor-release.jks` und das Passwort sicher aufheben**
(z. B. im Passwortmanager). Ohne sie kann keine weitere Version mehr als
Update installiert werden, nur noch nach Deinstallation. Die Datei gehört
**nicht** ins Repository.

Falls `keytool` nicht gefunden wird: Android Studio bringt es mit, z. B.
`"/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"`.

## 2. Secrets in GitHub eintragen

Lademonitor-Android → Settings → Secrets and variables → Actions → New
repository secret:

| Secret | Inhalt |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Ausgabe von `base64 -i lademonitor-release.jks \| pbcopy` (landet in der Zwischenablage) |
| `ANDROID_KEYSTORE_PASSWORD` | das Keystore-Passwort |
| `ANDROID_KEY_ALIAS` | `lademonitor` (der Alias aus Schritt 1) |
| `ANDROID_KEY_PASSWORD` | das Schlüssel-Passwort (meist dasselbe wie oben) |

## 3. Release auslösen

```bash
git tag v1.2 && git push origin v1.2
```

Oder auf GitHub: Releases → „Draft a new release“ → neuen Tag `v1.2` anlegen →
veröffentlichen. Achtung: dann legt GitHub das Release schon selbst an, der
Workflow hängt die APK nur noch daran.

Der Workflow (Actions → „Release“) setzt die Versionsnummer aus dem Tag
(`v1.2` → 1.2, interner `versionCode` 10200), übernimmt den passenden
Abschnitt aus `CHANGELOG.md` plus die Liste der gemergten Pull Requests als
Release-Text und hängt `lademonitor-1.2.apk` an (dazu `lademonitor-1.2.aab`
für einen späteren Weg über Google Play). Fehlt ein Secret, bricht er mit einer klaren Meldung
ab.

## 4. Auf dem Handy installieren

1. Auf dem Handy die Release-Seite öffnen:
   https://github.com/iDomi94/Lademonitor-Android/releases
2. Unter „Assets“ die `lademonitor-X.Y.apk` antippen und herunterladen.
3. Die Datei öffnen. Beim ersten Mal fragt Android, ob der Browser „unbekannte
   Apps installieren“ darf – erlauben.
4. Installieren. Google Play Protect warnt eventuell, weil die App nicht aus
   dem Play Store kommt – „Trotzdem installieren“.

Updates: dieselben Schritte mit der neuen APK. Daten bleiben erhalten, solange
der Schlüssel derselbe ist.

**Bequemer:** die App [Obtainium](https://obtainium.imranr.dev/) (kostenlos,
F-Droid/GitHub) beobachtet die Releases dieses Repos, meldet neue Versionen
und installiert sie mit einem Tipp – am ehesten das, was TestFlight auf iOS
macht.

**Repository privat?** Dann sind die Releases nur angemeldet erreichbar: auf
dem Handy in der GitHub-App oder im Browser anmelden, oder die APK am Mac
herunterladen und per Mail, Cloud oder USB aufs Handy bringen. Obtainium braucht dann einen GitHub-Token.

## Zum schnellen Ausprobieren: Debug-APK aus jedem Build

Jeder Pull Request und jeder Push auf `main` baut eine Debug-APK (Actions →
Lauf → „Artifacts“ → `lademonitor-debug-apk`, 14 Tage aufbewahrt, als ZIP
verpackt). Die lässt sich genauso installieren, aber: jeder Lauf signiert mit
einem anderen Debug-Schlüssel, eine spätere Version installiert sich also
**nicht** als Update darüber. Gedacht zum Ausprobieren, nicht für den Alltag.

**Wechsel von einer Debug-Version zur Release-Version:** wegen des anderen
Schlüssels muss die Debug-Version zuerst deinstalliert werden. Im Server-Modus
holt die App danach alles wieder vom Server. Im Modus „Nur lokal“ gehen die
Daten dabei verloren.

## Später: Google Play (das echte Gegenstück zu TestFlight)

Nicht eingerichtet, nur falls es einmal über den eigenen Gebrauch hinausgehen
soll:

- Google-Play-Developer-Konto (einmalig 25 $), App in der Play Console anlegen.
- Erstes Paket von Hand hochladen (das `.aab` aus dem GitHub-Release) und „Play App Signing“ aktivieren; der Schlüssel aus Schritt 1 wird
  dann zum Upload-Schlüssel.
- Für die automatische Übergabe ein Dienstkonto in der Google Cloud mit
  Zugriff auf die Play Console, dessen JSON-Schlüssel als weiteres Secret.
- „Interner Test“ funktioniert sofort für bis zu 100 Tester. Für eine
  öffentliche Veröffentlichung verlangt Google bei neuen privaten Konten
  vorher einen geschlossenen Test mit 12 Testern über 14 Tage.

### Lokaler Play-Upload (halvar100)

Für Google Play wird lokal gebaut und das `.aab` von Hand hochgeladen. Dafür
liegt im Projektordner eine git-ignorierte `keystore.properties`:

```properties
storeFile=upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
applicationId=com.herbrigpersonalteam.lademonitor
```

Ist sie vorhanden, signiert der Release-Build mit diesem Upload-Schlüssel und
nutzt die Play-`applicationId`; ohne sie bleibt alles wie oben (GitHub-Secrets,
`com.dominiqueherbrigpersonalteam.lademonitor`). Bauen mit demselben
Versionsschema wie der Workflow:

```bash
./gradlew bundleRelease -PversionCode=10200 -PversionName=1.2
# → app/build/outputs/bundle/release/app-release.aab
```
