#!/usr/bin/env python3
"""Prueft die JSON-Dateien unter docs/data auf Gueltigkeit.

Laeuft im Workflow "Build und Tests" bei jedem Push. Der Sinn: kaputtes JSON
in docs/data wuerde die Website stillschweigend leer lassen - hier faellt es
sofort auf.

Geprueft wird:
  * jede .json-Datei ist gueltiges JSON
  * die Wertungsdateien (a.json ... other.json) sind Listen von Objekten mit
    den erwarteten Feldern
  * die Punkteformel geht in jeder Zeile auf:
    gesamt = d + e + t + h - abzug
  * meta.json und events.json haben die erwarteten Felder
  * jede Wertung steht in der Datei, in die ihr Nachname gehoert
  * es gibt keine doppelten Wertungen

Lokal startbar mit:
    python .github/scripts/daten_pruefen.py
"""

import json
import pathlib
import sys
import unicodedata

ORDNER = pathlib.Path("docs/data")

# Felder, die jede Wertung haben muss
PFLICHTFELDER = [
    "vorname", "nachname", "jahrgang", "verein", "nation",
    "event", "eventId", "datum", "disziplin", "wettkampf",
    "durchgang", "platz", "d", "e", "h", "t", "abzug",
    "gesamt", "gesamtDurchgang", "link",
]

# Toleranz beim Nachrechnen der Punkteformel. Die Werte sind auf eine bis drei
# Nachkommastellen gerundet, da darf sich etwas Rundung ansammeln.
TOLERANZ = 0.0021


def normalisieren(name):
    """Muss dieselben Regeln haben wie Namen.java und app.js."""
    if not name:
        return ""
    s = name.lower()
    for alt, neu in (("ä", "a"), ("ö", "o"), ("ü", "u"), ("ß", "ss")):
        s = s.replace(alt, neu)
    s = "".join(z for z in unicodedata.normalize("NFD", s)
                if unicodedata.category(z) != "Mn")
    return " ".join(s.split())


def erwartete_datei(nachname):
    norm = normalisieren(nachname)
    if norm and "a" <= norm[0] <= "z":
        return norm[0]
    return "other"


def main():
    if not ORDNER.is_dir():
        print(f"{ORDNER} gibt es noch nicht - nichts zu pruefen.")
        return 0

    fehler = []
    zeilen_gesamt = 0
    schluessel = {}

    wertungsdateien = sorted(
        d for d in ORDNER.glob("*.json")
        if d.stem not in ("meta", "events")
    )

    for datei in wertungsdateien:
        try:
            inhalt = json.loads(datei.read_text(encoding="utf-8"))
        except Exception as e:
            fehler.append(f"{datei}: ungueltiges JSON - {e}")
            continue

        if not isinstance(inhalt, list):
            fehler.append(f"{datei}: erwartet wird eine Liste, gefunden "
                          f"{type(inhalt).__name__}")
            continue

        zeilen_gesamt += len(inhalt)

        for nr, zeile in enumerate(inhalt):
            ort = f"{datei.name} Zeile {nr}"

            if not isinstance(zeile, dict):
                fehler.append(f"{ort}: erwartet wird ein Objekt")
                continue

            fehlend = [f for f in PFLICHTFELDER if f not in zeile]
            if fehlend:
                fehler.append(f"{ort}: Felder fehlen: {', '.join(fehlend)}")
                continue

            # Steht die Zeile in der richtigen Datei?
            soll = erwartete_datei(zeile["nachname"])
            if soll != datei.stem:
                fehler.append(f"{ort}: '{zeile['nachname']}' gehoert nach "
                              f"{soll}.json, steht aber in {datei.name}")

            # Punkteformel nachrechnen
            def wert(feld):
                v = zeile[feld]
                return 0.0 if v is None else float(v)

            summe = (wert("d") + wert("e") + wert("t") + wert("h")
                     - wert("abzug"))
            if abs(summe - wert("gesamt")) > TOLERANZ:
                fehler.append(
                    f"{ort}: Punkteformel geht nicht auf. "
                    f"d={zeile['d']} e={zeile['e']} t={zeile['t']} "
                    f"h={zeile['h']} abzug={zeile['abzug']} "
                    f"-> errechnet {summe:.3f}, gespeichert {zeile['gesamt']}")

            # Duplikate
            s = "|".join(str(zeile[f]) for f in
                         ("eventId", "durchgang", "wettkampf", "nachname",
                          "vorname", "jahrgang"))
            if s in schluessel:
                fehler.append(f"{ort}: Duplikat von {schluessel[s]}")
            else:
                schluessel[s] = ort

        print(f"  {datei.name}: {len(inhalt)} Wertungen")

    # meta.json
    meta_datei = ORDNER / "meta.json"
    if meta_datei.exists():
        try:
            meta = json.loads(meta_datei.read_text(encoding="utf-8"))
            for feld in ("aktualisiert", "anzahlWertungen", "anzahlPersonen",
                         "anzahlEvents", "quelle"):
                if feld not in meta:
                    fehler.append(f"meta.json: Feld '{feld}' fehlt")
            if meta.get("anzahlWertungen") != zeilen_gesamt:
                fehler.append(
                    f"meta.json: anzahlWertungen ist "
                    f"{meta.get('anzahlWertungen')}, gezaehlt wurden "
                    f"{zeilen_gesamt}")
            print(f"  meta.json: ok (Stand {meta.get('aktualisiert')})")
        except Exception as e:
            fehler.append(f"meta.json: ungueltiges JSON - {e}")

    # events.json
    events_datei = ORDNER / "events.json"
    if events_datei.exists():
        try:
            daten = json.loads(events_datei.read_text(encoding="utf-8"))
            if "events" not in daten:
                fehler.append("events.json: Feld 'events' fehlt")
            else:
                print(f"  events.json: {len(daten['events'])} Events")
        except Exception as e:
            fehler.append(f"events.json: ungueltiges JSON - {e}")

    print()
    print(f"Insgesamt {zeilen_gesamt} Wertungen in "
          f"{len(wertungsdateien)} Dateien.")

    if fehler:
        print()
        print(f"FEHLER ({len(fehler)}):")
        # Nicht alle ausgeben - bei einem systematischen Fehler waeren das
        # Tausende Zeilen Protokoll.
        for f in fehler[:30]:
            print("  " + f)
        if len(fehler) > 30:
            print(f"  ... und {len(fehler) - 30} weitere")
        return 1

    print("Alles in Ordnung.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
