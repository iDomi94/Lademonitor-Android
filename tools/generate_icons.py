#!/usr/bin/env python3
"""Erzeugt den Android-Icon-Satz aus dem Logo-Generator des Server-Repos.

    python3 tools/generate_icons.py --server ../Lademonitor-Server

Die Zeichnung („Säule lädt Auto“) und der Generator liegen bewusst NICHT
hier, sondern nur in `design/logo/` von iDomi94/Lademonitor-Server - zwei
Kopien derselben Zeichnung würden früher oder später auseinanderlaufen. Dieses
Skript ruft dort `icons.py --android` für dieses Repo auf.

Was erzeugt wird (Begründungen im Docstring von icons.py):

* Adaptive Icon: `mipmap-*dpi/ic_launcher_foreground.png` (Motiv in der
  66-dp-Sicherheitszone), `drawable/ic_launcher_background.xml` (Verlauf) und
  `mipmap-*dpi/ic_launcher_monochrome.png` für die Themensymbole ab Android 13.
* `drawable-nodpi/logo_mark.png` - gerundete Kachel für den Erststart-Screen.
* `ic_launcher-playstore.png` - 512 px, ohne Alphakanal (Play Console).

Chromium wird automatisch in der Playwright-Installation gesucht, sonst über
LADEMONITOR_CHROME angeben.
"""
import argparse
import os
import subprocess
import sys

ANDROID_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", required=True,
                        help="Pfad zu einem Checkout von iDomi94/Lademonitor-Server")
    args = parser.parse_args()
    script = os.path.join(os.path.abspath(args.server), "design", "logo", "icons.py")
    if not os.path.exists(script):
        raise SystemExit(f"Logo-Generator nicht gefunden: {script}")
    subprocess.run([sys.executable, script, "--no-server", "--android", ANDROID_ROOT],
                   check=True)


if __name__ == "__main__":
    main()
