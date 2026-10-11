package de.wertungssuche;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft, dass keine Wertung doppelt in den Daten landet.
 *
 * <p>Zwei Mechanismen sichern das ab, und beide werden hier getestet:
 * <ol>
 *   <li>der eindeutige Schlüssel einer Wertung
 *       ({@link Wertung#schluessel()})</li>
 *   <li>das Ersetzen statt Ergänzen beim erneuten Laden eines Events
 *       ({@link Datenbestand#eventEntfernen(String)})</li>
 * </ol>
 */
class DuplikatTest {

    // --------------------------------------------------- Schlüssel einer Zeile

    @Test
    @DisplayName("Dieselbe Wertung zweimal ergibt denselben Schlüssel")
    void gleicheWertungGleicherSchluessel() {
        Wertung a = beispiel("Müller", "Lena", 2008, "Qualifikation");
        Wertung b = beispiel("Müller", "Lena", 2008, "Qualifikation");

        assertEquals(a.schluessel(), b.schluessel());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("Die zweite Übung desselben Durchgangs ist eine eigene Wertung")
    void zweiteUebungIstEigeneWertung() {
        // Beide heißen "Qualifikation" - unterschieden wird über die
        // Übungsnummer. Fehlte sie im Schlüssel, würde eine der beiden
        // Übungen als Duplikat verschwinden.
        Wertung erste = beispiel("Müller", "Lena", 2008, "Qualifikation");
        erste.uebung = 1;
        Wertung zweite = beispiel("Müller", "Lena", 2008, "Qualifikation");
        zweite.uebung = 2;

        assertFalse(erste.schluessel().equals(zweite.schluessel()),
                "Übung 1 und Übung 2 dürfen nicht zusammenfallen.");
    }

    @Test
    @DisplayName("Groß- und Kleinschreibung erzeugt kein Duplikat")
    void schreibweiseErzeugtKeinDuplikat() {
        // Wird in einem Event die Schreibweise nachträglich berichtigt, darf
        // daraus keine zweite Wertung werden.
        Wertung alt = beispiel("MÜLLER", "LENA", 2008, "Qualifikation");
        Wertung neu = beispiel("Müller", "Lena", 2008, "Qualifikation");

        assertEquals(alt.schluessel(), neu.schluessel(),
                "MÜLLER und Müller sind dieselbe Person.");
    }

    @Test
    @DisplayName("Mueller und Müller gelten als verschiedene Personen")
    void ueUndUmlautSindVerschieden() {
        // Das ist Absicht. Würde die Normalisierung "ue" zu "u" zusammenziehen,
        // damit "Mueller" und "Müller" zusammenfallen, dann würde aus "Neuer"
        // ein "Ner" und aus "Bauer" ein "Bar" - und völlig verschiedene
        // Personen landeten auf einer Karte.
        //
        // Für die Suche löst die Website das anders herum: sie merkt sich zu
        // jedem gespeicherten Namen zusätzlich die ausgeschriebene Form
        // (Müller -> mueller). Die Eingabe "mueller" findet damit "Müller",
        // ohne dass die Identität darunter leidet. Siehe suchformen() in
        // docs/app.js.
        Wertung mitUmlaut = beispiel("Müller", "Lena", 2008, "Qualifikation");
        Wertung ausgeschrieben = beispiel("Mueller", "Lena", 2008, "Qualifikation");

        assertFalse(mitUmlaut.schluessel().equals(ausgeschrieben.schluessel()));
        assertEquals("muller", Namen.normalisieren("Müller"));
        assertEquals("mueller", Namen.normalisieren("Mueller"));
        assertEquals("neuer", Namen.normalisieren("Neuer"),
                "Hier darf nichts zusammengezogen werden.");
    }

    @Test
    @DisplayName("Namensgleiche mit verschiedenem Jahrgang bleiben getrennt")
    void namensgleicheBleibenGetrennt() {
        Wertung eine = beispiel("Müller", "Lena", 2008, "Qualifikation");
        Wertung andere = beispiel("Müller", "Lena", 2011, "Qualifikation");

        assertFalse(eine.schluessel().equals(andere.schluessel()));
        assertFalse(eine.personSchluessel().equals(andere.personSchluessel()));
    }

    @Test
    @DisplayName("Dieselbe Person mit anders geschriebenem Verein bleibt eine Person")
    void vereinsschreibweiseTrenntNicht() {
        // Genau dieser Fall steckt in den echten Daten: "TV Voerde" und
        // "TV Voerde 1920 e.V." sind derselbe Verein, und ein Vereinswechsel
        // macht eine Turnerin auch nicht zu einer anderen Person.
        Wertung a = beispiel("Dierks", "Julie", 2013, "Qualifikation");
        a.verein = "TV Voerde";
        Wertung b = beispiel("Dierks", "Julie", 2013, "Qualifikation");
        b.verein = "TV Voerde 1920 e.V.";

        assertEquals(a.personSchluessel(), b.personSchluessel());
    }

    // ------------------------------------------------- Zusammenspiel im Bestand

    @Test
    @DisplayName("Dieselbe Wertung zweimal eingefügt wird nur einmal gespeichert")
    void duplikatWirdUebersprungen(@TempDir Path ordner) {
        Datenbestand bestand = new Datenbestand(ordner);

        Wertung a = beispiel("Müller", "Lena", 2008, "Qualifikation");
        Wertung b = beispiel("Müller", "Lena", 2008, "Qualifikation");

        bestand.hinzufuegen(List.of(a, b));

        assertEquals(1, bestand.anzahlWertungen());
        assertEquals(1, bestand.getHinzugefuegt());
        assertEquals(1, bestand.getDuplikate());
    }

    @Test
    @DisplayName("Beim erneuten Laden eines Events werden die alten Zeilen ersetzt")
    void erneutesLadenErsetztStattZuErgaenzen(@TempDir Path ordner) {
        Datenbestand bestand = new Datenbestand(ordner);

        // Erster Lauf: zwei Übungen.
        Wertung alt1 = beispiel("Müller", "Lena", 2008, "Qualifikation");
        alt1.uebung = 1;
        Wertung alt2 = beispiel("Müller", "Lena", 2008, "Qualifikation");
        alt2.uebung = 2;
        bestand.hinzufuegen(List.of(alt1, alt2));
        assertEquals(2, bestand.anzahlWertungen());

        // Zweiter Lauf desselben Events: das Kampfgericht hat die zweite Übung
        // zurückgezogen und die erste korrigiert.
        Wertung neu1 = beispiel("Müller", "Lena", 2008, "Qualifikation");
        neu1.uebung = 1;
        neu1.gesamt = 44.44;

        bestand.eventEntfernen("event-1");
        bestand.hinzufuegen(List.of(neu1));

        assertEquals(1, bestand.anzahlWertungen(),
                "Die zurückgezogene Übung muss verschwinden, nicht als "
                + "Geisterzeile stehen bleiben.");

        Wertung uebrig = bestand.alleWertungen().get(0);
        assertEquals(44.44, uebrig.gesamt,
                "Die korrigierte Wertung muss die alte ersetzen.");
    }

    @Test
    @DisplayName("Ein zweites Event bleibt beim Entfernen unangetastet")
    void anderesEventBleibtErhalten(@TempDir Path ordner) {
        Datenbestand bestand = new Datenbestand(ordner);

        Wertung ausEventEins = beispiel("Müller", "Lena", 2008, "Qualifikation");
        Wertung ausEventZwei = beispiel("Müller", "Lena", 2008, "Qualifikation");
        ausEventZwei.eventId = "event-2";
        ausEventZwei.event = "Zweites Event";

        bestand.hinzufuegen(List.of(ausEventEins, ausEventZwei));
        assertEquals(2, bestand.anzahlWertungen());

        bestand.eventEntfernen("event-1");

        assertEquals(1, bestand.anzahlWertungen());
        assertEquals("event-2", bestand.alleWertungen().get(0).eventId);
    }

    // ------------------------------------------------- Schreiben und Einlesen

    @Test
    @DisplayName("Geschriebene Dateien lassen sich ohne Verlust wieder einlesen")
    void schreibenUndWiederEinlesen(@TempDir Path ordner) throws IOException {
        Datenbestand erster = new Datenbestand(ordner);
        List<Wertung> echte = Beispieldaten.extraktor()
                .extrahieren(Beispieldaten.cbi2024());
        assertFalse(echte.isEmpty());

        erster.hinzufuegen(echte);
        erster.speichern();
        int vorher = erster.anzahlWertungen();

        Datenbestand zweiter = new Datenbestand(ordner);
        zweiter.laden();

        assertEquals(vorher, zweiter.anzahlWertungen(),
                "Beim Einlesen darf keine Wertung verloren gehen.");

        // Und ein zweites Einlesen derselben Daten erzeugt keine Duplikate.
        zweiter.hinzufuegen(echte);
        assertEquals(vorher, zweiter.anzahlWertungen());
        assertEquals(echte.size(), zweiter.getDuplikate());
    }

    @Test
    @DisplayName("Jede Wertung landet in der Datei ihres Anfangsbuchstabens")
    void jedeWertungInDerRichtigenDatei(@TempDir Path ordner) throws IOException {
        Datenbestand bestand = new Datenbestand(ordner);
        bestand.hinzufuegen(Beispieldaten.extraktor()
                .extrahieren(Beispieldaten.cbi2024()));
        bestand.speichern();

        for (var eintrag : bestand.getDateien().entrySet()) {
            for (Wertung w : eintrag.getValue()) {
                assertEquals(eintrag.getKey(), Namen.datei(w.nachname),
                        w.nachname + " gehört nicht in " + eintrag.getKey() + ".json");
            }
        }

        // Nur Dateien, die auch Inhalt haben, dürfen auf der Platte liegen.
        try (var dateien = Files.list(ordner)) {
            Set<String> geschrieben = new HashSet<>();
            dateien.forEach(p -> geschrieben.add(
                    p.getFileName().toString().replace(".json", "")));
            for (String name : geschrieben) {
                assertTrue(bestand.getDateien().containsKey(name)
                                && !bestand.getDateien().get(name).isEmpty(),
                        "Leere Datei " + name + ".json sollte gelöscht sein.");
            }
        }
    }

    @Test
    @DisplayName("Der Personenindex zählt jede Wertung genau einmal")
    void personenindexZaehltRichtig(@TempDir Path ordner) throws IOException {
        List<Wertung> echte = new ArrayList<>(Beispieldaten.extraktor()
                .extrahieren(Beispieldaten.cbi2024()));
        echte.addAll(Beispieldaten.extraktor().extrahieren(Beispieldaten.cottbus2024()));

        Path datei = ordner.resolve("personen.json");
        int personen = Personenindex.schreiben(echte, datei);

        assertTrue(Files.exists(datei));
        assertTrue(personen > 0);

        Set<String> erwartet = new HashSet<>();
        for (Wertung w : echte) {
            erwartet.add(w.personSchluessel());
        }
        assertEquals(erwartet.size(), personen,
                "Der Index muss genau so viele Personen haben wie es "
                + "verschiedene Personen-Schlüssel gibt.");
    }

    /** Baut eine Wertung mit festen Werten, nur Name, Jahrgang und Durchgang variieren. */
    private static Wertung beispiel(String nachname, String vorname,
                                    Integer jahrgang, String durchgang) {
        Wertung w = new Wertung();
        w.nachname = nachname;
        w.vorname = vorname;
        w.jahrgang = jahrgang;
        w.verein = "TG Beispiel";
        w.nation = null;
        w.event = "Beispiel-Cup 2026";
        w.eventId = "event-1";
        w.datum = "2026-05-01";
        w.disziplin = "Trampolin";
        w.wettkampf = "AK 15/16 (weiblich)";
        w.durchgang = durchgang;
        w.uebung = 1;
        w.uebungen = 2;
        w.platz = 3;
        w.d = 13.5;
        w.e = 16.2;
        w.h = 9.3;
        w.t = 17.1;
        w.abzug = 0.0;
        w.gesamt = 56.1;
        w.gesamtDurchgang = 110.0;
        w.link = "https://sporttech.io/events/event-1/ovs/stages/1/results";
        return w;
    }
}
