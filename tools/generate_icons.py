#!/usr/bin/env python3
"""Erzeugt den Android-Icon-Satz aus dem Logo-Generator des Server-Repos.

    python3 tools/generate_icons.py --server ../Lademonitor-Server

Die Zeichnung selbst liegt bewusst NICHT hier, sondern nur in
`design/logo/` von iDomi94/Lademonitor-Server (Variante 17
„Angeschnitten"). Dieses Skript importiert den dortigen Generator und
rastert daraus die Android-Formate - genauso, wie `icons.py --ios` den
Asset-Katalog der iOS-App bedient. Zwei Kopien derselben Zeichnung würden
früher oder später auseinanderlaufen.

Was erzeugt wird
----------------
* **Adaptive Icon** (`mipmap-*dpi/ic_launcher_foreground.png` +
  `drawable/ic_launcher_background.xml`). Android legt eine vom Hersteller
  gewählte Maske über das Icon: von den 108 dp sind je nach Gerät nur die
  mittleren ~72 dp garantiert sichtbar. Die Zeichnung sitzt deshalb auf
  78 % der Kantenlänge - groß genug, dass die angeschnittene Ladesäule
  weiterhin über den sichtbaren Rand hinausläuft (genau davon lebt
  Variante 17), klein genug, dass das Fahrzeug in der Sicherheitszone
  bleibt. Der Hintergrund ist ein eigener Verlauf als VectorDrawable, weil
  die Maske ihn bis in die Ecken braucht.
* **PNG statt VectorDrawable** für den Vordergrund: Androids
  VectorDrawable kennt kein `stroke-dasharray` - und genau daraus besteht
  das Kabel. Dieselbe Begründung wie bei iOS (dort scheitert Xcodes
  SVG-Import daran), und ein stiller Ausfall genau des Elements, das die
  Marke ausmacht, ist das Risiko nicht wert.
* **`drawable-nodpi/logo_mark.png`** - die gerundete Kachel für den
  Erststart-Screen. Bewusst die Kachel und nicht das quere Zeichen aus der
  Web-Kopfleiste: der Screen folgt dem Hell/Dunkel-Modus des Systems, und
  die graue Silhouette mit grünem Kabel ist für dunklen Grund gezeichnet.
  Die Kachel bringt ihren eigenen Grund mit und sitzt in beiden Modi
  richtig. `nodpi`, weil sie in genau einer Größe gezeigt wird.
* **`ic_launcher-playstore.png`** - 512 px, quadratisch und OHNE
  Alphakanal, wie die Play Console es für den Store-Eintrag verlangt.

Gerastert wird über das headless Chromium aus der Playwright-Installation
(kein cairosvg/rsvg im Bild), einmal groß und dann mit LANCZOS
heruntergerechnet - direkt in Zielgröße zu rendern ergibt sichtbar rauhere
Kanten.
"""
import argparse
import os
import subprocess
import sys
import tempfile

from PIL import Image

ANDROID_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ANDROID_ROOT, "app", "src", "main", "res")

CHROME = os.environ.get(
    "LADEMONITOR_CHROME",
    "/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell",
)
RENDER_PX = 1024

# Dichtestufen des adaptiven Icons: 108 dp in px je Stufe.
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# Anteil der Kantenlänge, den die Zeichnung im 108-dp-Feld einnimmt (siehe
# Modulkommentar). 0.78 hält das Fahrzeug innerhalb der 72-dp-Zone (es belegt
# rund 83 % der Zeichnungsbreite) und lässt die Säule weiter über den
# sichtbaren Rand hinauslaufen.
FOREGROUND_SCALE = 0.78

# Rechter Rand der Sicherheitszone in dp: dorthin wird die Zeichnung
# ausgerichtet, statt sie einfach zu zentrieren. Variante 17 ist bewusst
# asymmetrisch - die Säule läuft links aus dem Bild, das Fahrzeug steht
# rechts. Zentriert man stur, schiebt genau diese Asymmetrie die Schnauze
# über die Zone hinaus und eine runde Maske schneidet sie an.
SAFE_ZONE_RIGHT_DP = 90.0


def rasterise(svg_markup, px=RENDER_PX):
    """SVG -> RGBA-Bild, Ecken transparent."""
    with tempfile.TemporaryDirectory() as tmp:
        html = os.path.join(tmp, "r.html")
        png = os.path.join(tmp, "r.png")
        with open(html, "w") as fh:
            fh.write('<!doctype html><meta charset="utf-8"><style>html,body{margin:0;'
                     'padding:0;background:transparent}svg{display:block;width:%dpx;'
                     'height:%dpx}</style>%s' % (px, px, svg_markup))
        subprocess.run(
            [CHROME, "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
             "--default-background-color=00000000", "--force-device-scale-factor=1",
             f"--screenshot={png}", f"--window-size={px},{px}", "file://" + html],
            capture_output=True, check=True)
        return Image.open(png).convert("RGBA").copy()


def write_png(img, size, path, drop_alpha=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out = img.resize((size, size), Image.LANCZOS)
    if drop_alpha:
        out = out.convert("RGB")
    out.save(path, "PNG", optimize=True)
    print(f"  {os.path.relpath(path, ANDROID_ROOT):<56}{size}x{size}"
          + (", ohne Alpha" if drop_alpha else ""))


def foreground_tile(scene, size=RENDER_PX):
    """Die Zeichnung auf [FOREGROUND_SCALE] verkleinert und eingepasst.

    Waagerecht wird an der Sicherheitszone ausgerichtet (siehe
    [SAFE_ZONE_RIGHT_DP]), senkrecht mittig - dort ist die Zeichnung
    symmetrisch genug, dass Zentrieren richtig sitzt. Bezug ist jeweils die
    tatsächlich gezeichnete Fläche, nicht die Kachelkante: die Kachel hat
    oben und unten reichlich Luft, und daran auszurichten hieße, die
    Zeichnung kleiner zu machen als nötig.
    """
    inner = int(round(size * FOREGROUND_SCALE))
    drawing = scene.resize((inner, inner), Image.LANCZOS)
    # Alpha > 40 blendet den weichen Schein um das Kabel aus - der zählt
    # nicht als Zeichnung und würde die Einpassung sonst verzerren.
    box = drawing.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox()
    if box is None:
        raise SystemExit("leere Zeichnung - Rasterung fehlgeschlagen?")
    left, top, right, bottom = box

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dx = int(round(SAFE_ZONE_RIGHT_DP / 108 * size)) - right
    dy = (size - (top + bottom)) // 2
    canvas.paste(drawing, (dx, dy))
    return canvas


def write_background_vector(build, path):
    """Hintergrund des adaptiven Icons als VectorDrawable.

    Derselbe Verlauf wie `parts.bg_gradient()` (dort in Anteilen der
    Bounding-Box, hier in den 108 Einheiten des Viewports).
    """
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Erzeugt von tools/generate_icons.py - nicht von Hand ändern.\n'
            '     Derselbe Verlauf wie im Logo-Generator (parts.bg_gradient). -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n'
            '    android:height="108dp"\n'
            '    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n'
            '    <path android:pathData="M0,0h108v108h-108z">\n'
            '        <aapt:attr xmlns:aapt="http://schemas.android.com/aapt"\n'
            '            name="android:fillColor">\n'
            '            <gradient\n'
            '                android:type="linear"\n'
            '                android:startX="0"\n'
            '                android:startY="0"\n'
            '                android:endX="37.8"\n'
            '                android:endY="108">\n'
            f'                <item android:offset="0" android:color="{build.BG_SOFT}" />\n'
            f'                <item android:offset="1" android:color="{build.BG_DEEP}" />\n'
            '            </gradient>\n'
            '        </aapt:attr>\n'
            '    </path>\n'
            '</vector>\n'
        )
    print(f"  {os.path.relpath(path, ANDROID_ROOT):<56}Verlauf "
          f"{build.BG_SOFT} -> {build.BG_DEEP}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", required=True,
                        help="Pfad zu einem Checkout von iDomi94/Lademonitor-Server")
    args = parser.parse_args()

    logo_dir = os.path.join(os.path.abspath(args.server), "design", "logo")
    if not os.path.isdir(logo_dir):
        raise SystemExit(f"Logo-Generator nicht gefunden: {logo_dir}")
    sys.path.insert(0, logo_dir)
    import build  # noqa: E402  (liegt erst nach dem sys.path-Eintrag)

    if not os.path.exists(CHROME):
        raise SystemExit(
            f"Chromium nicht gefunden: {CHROME}\n"
            "Pfad über LADEMONITOR_CHROME setzen (headless_shell aus der "
            "Playwright-Installation oder ein beliebiges Chrome/Chromium)."
        )

    print("rastere Variante 17 ...")
    tile = rasterise(build.v17_angeschnitten())        # gerundete Kachel
    square = rasterise(build.v17_quadratisch())        # randlos, mit Grund
    # Dieselbe randlose Fassung ohne den Hintergrund: der Vordergrund des
    # adaptiven Icons darf ihn nicht mitbringen, die Maske braucht ihn bis in
    # die Ecken und holt ihn sich aus dem eigenen Hintergrund-Drawable.
    bg_rect = (f'<rect x="0" y="0" width="{build.SIZE}" height="{build.SIZE}" '
               f'fill="url(#bg)"/>')
    markup = build.v17_quadratisch()
    if bg_rect not in markup:
        raise SystemExit("Hintergrund-Rechteck nicht gefunden - hat sich "
                         "build._v17() geändert?")
    scene = rasterise(markup.replace(bg_rect, ""))

    print("\nAdaptives Icon:")
    foreground = foreground_tile(scene)
    for density, px in DENSITIES.items():
        write_png(foreground, px,
                  os.path.join(RES, f"mipmap-{density}", "ic_launcher_foreground.png"))
    write_background_vector(build, os.path.join(RES, "drawable", "ic_launcher_background.xml"))

    print("\nErststart-Screen und Store:")
    write_png(tile, 512, os.path.join(RES, "drawable-nodpi", "logo_mark.png"))
    write_png(square, 512,
              os.path.join(ANDROID_ROOT, "app", "src", "main", "ic_launcher-playstore.png"),
              drop_alpha=True)

    print("\nfertig.")


if __name__ == "__main__":
    main()
