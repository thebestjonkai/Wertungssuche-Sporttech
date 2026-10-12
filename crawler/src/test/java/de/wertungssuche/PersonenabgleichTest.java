package de.wertungssuche;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft das Zusammenführen von Personen.
 *
 * <p>Die Fälle sind <b>nicht erfunden</b>, sie stammen alle aus dem echten
 * Datenbestand – Emely Schmal, Michael Gladjuk und die Starterliste des
 * SV Barnstorf. Deshalb stehen die Originalwerte in den Testnamen: wenn
 * sporttech.io seine Daten irgendwann korrigiert, sieht man hier, womit
 * gerechnet wurde.
 */
class PersonenabgleichTest {

    /** Baut eine Wertung mit den Feldern, auf die es hier ankommt. */
    private static Wertung w(String vorname, String nachname, Integer jahrgang,
                             String verein, String eventId) {
        Wertung w = new Wertung();
        w.vorname = vorname;
        w.nachname = nachname;
        w.jahrgang = jahrgang;
        w.verein = verein;
        w.eventId = eventId;
        w.datum = "2026-01-01";
        w.wettkampf = "AK 13/14";
        w.durchgang = "Qualifikation";
        w.uebung = 1;
        return w;
    }

    private static List<Wertung> liste(Wertung... wertungen) {
        return new ArrayList<>(List.of(wertungen));
    }

    /** Wie viele verschiedene Personen stecken in der Liste? */
    private static long personen(List<Wertung> alle) {
        return alle.stream().map(Wertung::personSchluessel).distinct().count();
    }

    @Nested
    @DisplayName("Vor- und Nachname vertauscht")
    class Vertauscht {

        @Test
        @DisplayName("Barmstedt Cup liefert 'Schmal Emely' – wird zu 'Emely Schmal'")
        void emelySchmal() {
            // 3 Wettkämpfe richtig, 1 Wettkampf vertauscht: die Mehrheit gewinnt.
            List<Wertung> alle = liste(
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-filder"),
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-ltv"),
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-djm"),
                    w("Schmal", "Emely", 2011, "TSVE Bielefeld", "ev-barmstedt"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertEquals(1, personen(alle), "muss eine Person sein");
            assertEquals(1, b.namenGedreht());
            // Vorher zwei Personen, nicht vier: die drei richtigen Zeilen haben
            // schon denselben Personenschlüssel, nur die gedrehte fällt heraus.
            assertEquals(2, b.personenVorher());
            assertEquals(1, b.personenNachher());
            for (Wertung x : alle) {
                assertEquals("Emely", x.vorname);
                assertEquals("Schmal", x.nachname);
            }
        }

        @Test
        @DisplayName("Die gedrehte Zeile landet danach in der richtigen Datei")
        void dateiWirdKorrigiert() {
            // Der eigentliche Schaden: mit nachname='Emely' liegt die Wertung in
            // e.json, der Suchindex zeigt aber auf s – sie wäre unfindbar.
            Wertung gedreht = w("Schmal", "Emely", 2011, "TSVE Bielefeld", "ev-barmstedt");
            assertEquals("e", gedreht.datei(), "vorher: falsche Datei");

            List<Wertung> alle = liste(
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-filder"),
                    gedreht);
            Personenabgleich.abgleichen(alle);

            assertEquals("s", gedreht.datei(), "nachher: richtige Datei");
        }

        @Test
        @DisplayName("Bei Gleichstand entscheidet die Rolle im übrigen Datenbestand")
        void gleichstandUeberHaeufigkeit() {
            // Ein Wettkampf so, ein Wettkampf so. "Schmal" ist im Datenbestand
            // sonst nur Nachname, "Emely" nur Vorname – das muss den Ausschlag
            // geben.
            List<Wertung> alle = liste(
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-a"),
                    w("Schmal", "Emely", 2011, "TSVE Bielefeld", "ev-b"),
                    // Hintergrundrauschen: andere Turnerinnen mit Nachnamen Schmal
                    w("Lena", "Schmal", 2009, "TSVE Bielefeld", "ev-a"),
                    w("Emely", "Krause", 2010, "TSVE Bielefeld", "ev-a"));

            Personenabgleich.abgleichen(alle);

            Wertung erste = alle.get(0);
            assertEquals("Emely", erste.vorname);
            assertEquals("Schmal", erste.nachname);
        }

        @Test
        @DisplayName("Mehrheit verliert gegen eine deutliche Rollenstatistik")
        void alinaWarmuth() {
            // Der Fall, der die erste Fassung entlarvt hat: Alina Warmuth steht
            // zweimal vertauscht (Barmstedt) und nur einmal richtig. Nach
            // Wettkämpfen gezählt würde "Warmuth Alina" gewinnen. "Alina" ist
            // im übrigen Datenbestand aber eindeutig ein Vorname, und das muss
            // schwerer wiegen.
            List<Wertung> alle = liste(
                    w("Alina", "Warmuth", 2012, "TSV Dielmissen", "ev-gut"),
                    w("Warmuth", "Alina", 2012, "TSV Dielmissen", "ev-barmstedt-25"),
                    w("Warmuth", "Alina", 2012, "TSV Dielmissen", "ev-barmstedt-26"));
            // Genug andere Turnerinnen namens Alina, damit die Statistik greift.
            for (int i = 0; i < 12; i++) {
                alle.add(w("Alina", "Nachname" + i, 2010 + i, "TV Irgendwo", "ev-x"));
            }

            Personenabgleich.abgleichen(alle);

            assertEquals("Alina", alle.get(0).vorname);
            assertEquals("Warmuth", alle.get(0).nachname);
            assertEquals("Alina", alle.get(1).vorname, "die Mehrheit darf hier nicht gewinnen");
            assertEquals("Warmuth", alle.get(1).nachname);
        }

        @Test
        @DisplayName("Schwaches Statistik-Rauschen schlägt keine klare Mehrheit")
        void mattiKoenig() {
            // "Matti König" steht in vielen Wettkämpfen richtig. Eine einzelne
            // fremde Zeile mit Vornamen "König" darf das nicht umdrehen.
            List<Wertung> alle = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                alle.add(w("Matti", "König", 2009, "TV Beispiel", "ev-" + i));
            }
            alle.add(w("König", "Matti", 2009, "TV Beispiel", "ev-barmstedt"));
            // Das Rauschen: eine Person, die wirklich König mit Vornamen heißt.
            alle.add(w("König", "Sonstwer", 2005, "TV Anders", "ev-y"));

            Personenabgleich.abgleichen(alle);

            assertEquals("Matti", alle.get(0).vorname);
            assertEquals("König", alle.get(0).nachname);
            assertEquals("Matti", alle.get(8).vorname);
        }

        @Test
        @DisplayName("GROSSSCHREIBUNG wird an die Mehrheit angeglichen")
        void grossschreibung() {
            List<Wertung> alle = liste(
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-a"),
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-b"),
                    w("Emely", "SCHMAL", 2011, null, "ev-nissen"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertEquals(1, personen(alle));
            assertEquals(1, b.schreibweiseAngeglichen());
            assertEquals("Schmal", alle.get(2).nachname);
        }
    }

    @Nested
    @DisplayName("Ganzer Name in einem Feld")
    class NameInEinemFeld {

        @Test
        @DisplayName("SV Barnstorf liefert nachname='Celina Ainger', vorname=leer")
        void wirdGeteilt() {
            List<Wertung> alle = liste(
                    w(null, "Celina Ainger", null, "SV Barnstorf", "ev-a"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertEquals(1, b.namenGeteilt());
            assertEquals("Celina", alle.get(0).vorname);
            assertEquals("Ainger", alle.get(0).nachname);
            assertEquals("a", alle.get(0).datei());
        }

        @Test
        @DisplayName("Doppelvorname: getrennt wird am letzten Leerzeichen")
        void doppelvorname() {
            List<Wertung> alle = liste(
                    w("", "Anna Maria Weber", 2012, "TV Beispiel", "ev-a"));

            Personenabgleich.abgleichen(alle);

            assertEquals("Anna Maria", alle.get(0).vorname);
            assertEquals("Weber", alle.get(0).nachname);
        }

        @Test
        @DisplayName("Ein einzelnes Wort bleibt unangetastet")
        void einzelwort() {
            List<Wertung> alle = liste(w(null, "Ainger", null, "SV Barnstorf", "ev-a"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertEquals(0, b.namenGeteilt());
            assertNull(alle.get(0).vorname);
            assertEquals("Ainger", alle.get(0).nachname);
        }

        @Test
        @DisplayName("Sind beide Felder gefüllt, wird nichts geteilt")
        void beideGefuellt() {
            // "Joana Elia" + "Kabak" ist korrekt und darf nicht angefasst werden.
            List<Wertung> alle = liste(
                    w("Joana Elia", "Kabak", 2016, "TSG Bodenwerder", "ev-a"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertEquals(0, b.namenGeteilt());
            assertEquals("Joana Elia", alle.get(0).vorname);
            assertEquals("Kabak", alle.get(0).nachname);
        }
    }

    @Nested
    @DisplayName("Jahrgang")
    class Jahrgang {

        @Test
        @DisplayName("Fehlender Jahrgang erzeugt keine zweite Person")
        void fehlenderJahrgangWirdErgaenzt() {
            // Die WAGC-Qualifikation schickt kein DateOfBirth.
            List<Wertung> alle = liste(
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-a"),
                    w("Emely", "Schmal", null, "TSVE 1890 Bielefeld e.V.", "ev-wagc"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertEquals(1, personen(alle));
            assertEquals(1, b.jahrgangErgaenzt());
            assertEquals(2011, alle.get(1).jahrgang);
        }

        @Test
        @DisplayName("Widersprüchlicher Jahrgang: gleicher Verein heißt gleiche Person")
        void gladjuk() {
            // sporttech hat Michael Gladjuk mal mit 2005, mal mit 2006 – beide
            // Male bei TV Nellingen. 2005 steht in mehr Wettkämpfen.
            List<Wertung> alle = liste(
                    w("Michael", "Gladjuk", 2006, "TV Nellingen 1893 e.V.", "ev-a"),
                    w("Michael", "Gladjuk", 2005, "GER, TV Nellingen e.V.", "ev-b"),
                    w("Michael", "Gladjuk", 2005, "MTV Stuttgart", "ev-c"),
                    w("Michael", "Gladjuk", null, "MTV Stuttgart", "ev-wagc"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertEquals(1, personen(alle), "muss eine Person sein");
            assertEquals(1, b.jahrgangKorrigiert());
            assertEquals(1, b.jahrgangErgaenzt());
            for (Wertung x : alle) {
                assertEquals(2005, x.jahrgang, "der häufigere Jahrgang gewinnt");
            }
        }

        @Test
        @DisplayName("Verschiedene Vereine bleiben getrennt – lieber zwei Karten")
        void emmaLeonhardt() {
            // Pfungstadt 2010 gegen Mannheim 2011: Vereinswechsel oder zwei
            // Turnerinnen? Nicht entscheidbar, also nicht zusammenlegen.
            List<Wertung> alle = liste(
                    w("Emma", "Leonhardt", 2010, "TV Pfungstadt", "ev-a"),
                    w("Emma", "Leonhardt", 2011, "MTG Mannheim", "ev-b"));

            Personenabgleich.abgleichen(alle);

            assertEquals(2, personen(alle));
            assertEquals(2010, alle.get(0).jahrgang);
            assertEquals(2011, alle.get(1).jahrgang);
        }

        @Test
        @DisplayName("Landesturnverband zählt als Vereinstreffer, wenn der Ort passt")
        void turnverband() {
            // "ESV Lokomotive Zwickau" und "Sächsischer Turnverband Zwickau"
            // treffen sich über "zwickau".
            List<Wertung> alle = liste(
                    w("Felizitas", "Nehrke", 2013, "ESV Lokomotive Zwickau", "ev-a"),
                    w("Felizitas", "Nehrke", 2015,
                            "SN, Sächsischer Turn-Verband Zwickau", "ev-b"));

            Personenabgleich.abgleichen(alle);

            assertEquals(1, personen(alle));
        }

        @Test
        @DisplayName("Ohne jede Vereinsangabe bleiben verschiedene Jahrgänge getrennt")
        void ohneVerein() {
            // "Sofia Almeida" steht zweimal ohne Verein drin, mit 2010 und 2014.
            List<Wertung> alle = liste(
                    w("Sofia", "Almeida", 2010, null, "ev-a"),
                    w("Sofia", "Almeida", 2014, null, "ev-b"));

            Personenabgleich.abgleichen(alle);

            assertEquals(2, personen(alle));
        }

        @Test
        @DisplayName("Hat niemand einen Jahrgang, ist der Name die Person")
        void niemandHatJahrgang() {
            List<Wertung> alle = liste(
                    w("Helene", "Rudolf", null, "SV Barnstorf", "ev-a"),
                    w("Helene", "Rudolf", null, "SV Barnstorf", "ev-b"));

            Personenabgleich.abgleichen(alle);

            assertEquals(1, personen(alle));
            assertNull(alle.get(0).jahrgang);
        }

        @Test
        @DisplayName("Jahrgangslose Zeile bei zwei möglichen Personen bleibt eigenständig")
        void mehrdeutigBleibtGetrennt() {
            List<Wertung> alle = liste(
                    w("Emma", "Leonhardt", 2010, "TV Pfungstadt", "ev-a"),
                    w("Emma", "Leonhardt", 2011, "MTG Mannheim", "ev-b"),
                    w("Emma", "Leonhardt", null, "TG Irgendwo", "ev-c"));

            Personenabgleich.abgleichen(alle);

            assertEquals(3, personen(alle), "nicht raten, wenn es mehrdeutig ist");
            assertNull(alle.get(2).jahrgang);
        }
    }

    @Nested
    @DisplayName("Keine falschen Zusammenlegungen")
    class KeineUebertreibung {

        @Test
        @DisplayName("Verschiedene Personen mit gleichem Nachnamen bleiben getrennt")
        void geschwister() {
            List<Wertung> alle = liste(
                    w("Phil", "Uhle", 2014, "SV Barnstorf", "ev-a"),
                    w("Feli", "Uhle", 2018, "SV Barnstorf", "ev-a"));

            Personenabgleich.abgleichen(alle);

            assertEquals(2, personen(alle));
            assertEquals("Phil", alle.get(0).vorname);
            assertEquals("Feli", alle.get(1).vorname);
        }

        @Test
        @DisplayName("Ein Lauf über schon saubere Daten ändert nichts")
        void sauberBleibtSauber() {
            List<Wertung> alle = liste(
                    w("Joana Elia", "Kabak", 2016, "TSG Bodenwerder", "ev-a"),
                    w("Michael", "Gladjuk", 2005, "MTV Stuttgart", "ev-a"),
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-a"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertTrue(b.etwasGeaendert() == false, "nichts zu tun");
            assertEquals(3, b.personenVorher());
            assertEquals(3, b.personenNachher());
        }

        @Test
        @DisplayName("Zweimal laufen lassen ändert beim zweiten Mal nichts mehr")
        void zweiterLaufIstStabil() {
            List<Wertung> alle = liste(
                    w("Emely", "Schmal", 2011, "TSVE Bielefeld", "ev-a"),
                    w("Schmal", "Emely", 2011, "TSVE Bielefeld", "ev-b"),
                    w("Emely", "Schmal", null, "TSVE 1890 Bielefeld e.V.", "ev-c"));

            Personenabgleich.abgleichen(alle);
            Personenabgleich.Bericht zweiter = Personenabgleich.abgleichen(alle);

            assertTrue(zweiter.etwasGeaendert() == false,
                    "der Abgleich muss nach einem Durchlauf fertig sein");
        }
    }

    @Nested
    @DisplayName("Kaputte Umlaute")
    class Ersatzzeichen {

        @Test
        @DisplayName("Werden gezählt, aber nicht verändert")
        void bleibenStehen() {
            // So kommt es von sporttech.io: die API liefert die Bytes EF BF BD.
            // Welcher Umlaut es war, lässt sich nicht zurückrechnen.
            String kaputt = "F" + Personenabgleich.ERSATZZEICHEN + "rster";
            List<Wertung> alle = liste(w("Lisa", kaputt, 2012, "MTV Köln", "ev-a"));

            Personenabgleich.Bericht b = Personenabgleich.abgleichen(alle);

            assertEquals(1, b.namenMitErsatzzeichen());
            assertEquals(kaputt, alle.get(0).nachname, "darf nicht geraten werden");
            assertNotEquals("Förster", alle.get(0).nachname);
        }
    }
}
