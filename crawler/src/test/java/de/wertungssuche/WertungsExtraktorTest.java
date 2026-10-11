package de.wertungssuche;

import de.wertungssuche.api.EventDaten;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft das Herauslösen der Wertungen gegen die gespeicherten Beispieldateien.
 *
 * <p>Der wichtigste Test ist {@link #punkteformelGehtAuf()}: er rechnet jede
 * einzelne Wertung nach. Geht die Summe nicht auf, stimmt an der Umrechnung
 * etwas nicht – und genau dort steckten im Laufe der Entwicklung zwei Fehler
 * (der vergessene zusätzliche Abzug und die Sonderregel für die E-Note).
 */
class WertungsExtraktorTest {

    /** Toleranz beim Nachrechnen: die Werte sind auf 1 bis 3 Stellen gerundet. */
    private static final double TOLERANZ = 0.0021;

    // ---------------------------------------------------------------- Formel

    @Test
    @DisplayName("Die Punkteformel geht in jeder Wertung des CBI 2024 auf")
    void punkteformelGehtAuf() {
        List<Wertung> wertungen = Beispieldaten.extraktor()
                .extrahieren(Beispieldaten.cbi2024());

        assertFalse(wertungen.isEmpty(), "Es sollten Wertungen gefunden werden.");

        for (Wertung w : wertungen) {
            double errechnet = oderNull(w.d) + oderNull(w.e) + oderNull(w.t)
                    + oderNull(w.h) - oderNull(w.abzug);
            assertEquals(oderNull(w.gesamt), errechnet, TOLERANZ,
                    "Summe stimmt nicht bei " + w);
        }
    }

    @Test
    @DisplayName("Die Punkteformel geht auch im World Cup Cottbus 2024 auf")
    void punkteformelGehtAuchInCottbusAuf() {
        List<Wertung> wertungen = Beispieldaten.extraktor()
                .extrahieren(Beispieldaten.cottbus2024());

        assertFalse(wertungen.isEmpty());
        for (Wertung w : wertungen) {
            double errechnet = oderNull(w.d) + oderNull(w.e) + oderNull(w.t)
                    + oderNull(w.h) - oderNull(w.abzug);
            assertEquals(oderNull(w.gesamt), errechnet, TOLERANZ,
                    "Summe stimmt nicht bei " + w);
        }
    }

    @Test
    @DisplayName("Der zusätzliche Abzug DPenaltyT zählt mit")
    void zusaetzlicherAbzugZaehltMit() {
        // Frame 32 des CBI 2024: D 0,0 + E 15,8 - DPenalty 12,0 = 3,80.
        // Ohne DPenaltyT käme 15,80 heraus - so rechnet sporttechs eigene
        // Beispiel-Erweiterung vmixLivesportTRA.js, und so wäre es falsch.
        EventDaten daten = Beispieldaten.cbi2024();
        EventDaten.Frame frame = daten.Frames.get("32");

        assertNotNull(frame, "Frame 32 muss im Beispiel enthalten sein.");
        assertEquals(120, frame.DPenaltyT,
                "Frame 32 ist genau wegen dieses Abzugs im Beispiel.");

        Wertung w = Beispieldaten.extraktor().extrahieren(daten).stream()
                .filter(x -> x.gesamt != null && Math.abs(x.gesamt - 3.8) < 0.001)
                .findFirst()
                .orElse(null);

        // Tumbling wird nicht gesammelt, darum taucht Frame 32 nicht in den
        // Wertungen auf. Die Rechnung lässt sich aber direkt prüfen:
        assertNull(w, "Tumbling gehört nicht in die Daten.");
        double abzug = (Zahlen.oderNull(frame.PenaltyT)
                + Zahlen.oderNull(frame.DPenaltyT)) / 10.0;
        double errechnet = Zahlen.oderNull(frame.DifficultyT_G) / 10.0
                + Zahlen.oderNull(frame.ETotalT_G) / 10.0 - abzug;
        assertEquals(3.8, errechnet, 0.0005,
                "D + E - (Penalty + DPenalty) muss 3,80 ergeben.");
    }

    // ------------------------------------------------------------ Disziplinen

    @Test
    @DisplayName("Nur Trampolin Einzel wird übernommen")
    void nurTrampolinEinzel() {
        EventDaten daten = Beispieldaten.cbi2024();

        // Das Beispiel enthält absichtlich alle vier Disziplinen.
        Set<Integer> enthaltene = daten.Competitions.values().stream()
                .map(c -> c.Discipline)
                .collect(Collectors.toSet());
        assertEquals(Set.of(0, 1, 2, 3), enthaltene,
                "Das Beispiel soll alle vier Disziplinen enthalten.");

        List<Wertung> wertungen = Beispieldaten.extraktor().extrahieren(daten);

        assertFalse(wertungen.isEmpty());
        for (Wertung w : wertungen) {
            assertEquals("Trampolin", w.disziplin,
                    "Synchron, Doppel-Mini und Tumbling gehören nicht in die Daten.");
        }
    }

    // ------------------------------------------------------------ Frame-Status

    @Test
    @DisplayName("Nur veröffentlichte Übungen werden übernommen")
    void nurVeroeffentlichteUebungen() {
        EventDaten daten = Beispieldaten.cbi2024();

        long veroeffentlichtTRA = 0;
        for (EventDaten.Stage stage : daten.Stages.values()) {
            EventDaten.Competition c = daten.Competitions.get(
                    String.valueOf(stage.CompetitionID));
            if (c == null || c.Discipline == null || c.Discipline != 0) {
                continue;
            }
            int limit = Zahlen.oderNull(stage.PerfomanceFramesLimit);
            for (Integer gid : stage.Groups) {
                EventDaten.Group g = daten.Groups.get(String.valueOf(gid));
                for (Integer pid : g.Performances) {
                    EventDaten.Performance p =
                            daten.Performances.get(String.valueOf(pid));
                    List<Integer> frames = p.Frames;
                    for (int i = 0; i < Math.min(limit, frames.size()); i++) {
                        EventDaten.Frame f =
                                daten.Frames.get(String.valueOf(frames.get(i)));
                        if (f != null && f.State != null && f.State == 3) {
                            veroeffentlichtTRA += p.Athletes.size();
                        }
                    }
                }
            }
        }

        List<Wertung> wertungen = Beispieldaten.extraktor().extrahieren(daten);
        assertEquals(veroeffentlichtTRA, wertungen.size(),
                "Es darf genau eine Zeile je veröffentlichter Trampolin-Übung "
                + "und Athlet geben.");
    }

    @Test
    @DisplayName("Gescheiterte Übungen (DNS, FSF) kommen nicht in die Daten")
    void gescheiterteUebungenFehlen() {
        EventDaten daten = Beispieldaten.cbi2024();

        // Frame 453 ist FSF, Frame 324 ist DNS - beide absichtlich im Beispiel.
        EventDaten.Frame fsf = daten.Frames.get("453");
        assertNotNull(fsf);
        assertEquals(4, fsf.State, "Frame 453 soll den Status GESCHEITERT haben.");
        assertEquals("FSF", Frames.kuerzel(fsf));
        assertFalse(Frames.istVeroeffentlicht(fsf));

        // Die zugehörige Performance taucht nur mit ihren gültigen Übungen auf.
        List<Wertung> wertungen = Beispieldaten.extraktor().extrahieren(daten);
        for (Wertung w : wertungen) {
            assertNotNull(w.gesamt, "Eine Zeile ohne Gesamtwert darf es nicht geben.");
        }
    }

    // ------------------------------------------------------------ Durchgänge

    @Test
    @DisplayName("Eine Qualifikation mit zwei Übungen ergibt zwei Zeilen "
            + "mit demselben Durchgangsnamen")
    void zweiUebungenErgebenZweiZeilen() {
        List<Wertung> wertungen = Beispieldaten.extraktor()
                .extrahieren(Beispieldaten.cbi2024());

        Set<String> durchgaenge = wertungen.stream()
                .map(w -> w.durchgang)
                .collect(Collectors.toSet());

        assertTrue(durchgaenge.contains("Qualifikation"),
                "Erwartet: Qualifikation, gefunden " + durchgaenge);
        assertFalse(durchgaenge.contains("Qualifikation 1"),
                "Die Übungsnummer gehört nicht in den Durchgangsnamen.");
        assertFalse(durchgaenge.contains("Qualifikation 2"),
                "Die Übungsnummer gehört nicht in den Durchgangsnamen.");

        // Die zwei Übungen eines Vorkampfs: gleicher Durchgang, verschiedene
        // Übungsnummern, gleicher Platz und gleiche Durchgangssumme.
        Wertung erste = wertungen.get(0);
        List<Wertung> einePerson = wertungen.stream()
                .filter(w -> "Qualifikation".equals(w.durchgang))
                .filter(w -> w.nachname.equals(erste.nachname))
                .filter(w -> w.wettkampf.equals(erste.wettkampf))
                .toList();

        assertTrue(einePerson.size() >= 2,
                "Das Beispiel enthält eine Qualifikation aus zwei Übungen.");
        assertEquals(1, einePerson.get(0).uebung);
        assertEquals(2, einePerson.get(1).uebung);
        assertEquals(2, einePerson.get(0).uebungen);
        assertEquals(einePerson.get(0).platz, einePerson.get(1).platz,
                "Der Platz gehört zum Durchgang, nicht zur einzelnen Übung.");
        assertEquals(einePerson.get(0).gesamtDurchgang,
                einePerson.get(1).gesamtDurchgang,
                "Die Durchgangssumme ist für beide Übungen dieselbe.");

        // Und die beiden dürfen sich nicht gegenseitig als Duplikat verdrängen.
        assertFalse(einePerson.get(0).schluessel()
                        .equals(einePerson.get(1).schluessel()),
                "Übung 1 und Übung 2 brauchen verschiedene Schlüssel.");
    }

    @Test
    @DisplayName("Der Durchgangsname trägt keine Übungsnummer")
    void durchgangsnameOhneUebungsnummer() {
        assertEquals("Qualifikation", Durchgang.name(0));
        assertEquals("Viertelfinale", Durchgang.name(1));
        assertEquals("Halbfinale", Durchgang.name(2));
        assertEquals("Finale", Durchgang.name(3));
        assertEquals("Team-Halbfinale", Durchgang.name(4));
        assertEquals("Team-Finale", Durchgang.name(5));
        assertEquals("Qualifikation II", Durchgang.name(6));
        assertEquals("Finale 2", Durchgang.name(7));
        assertEquals("Finale 1", Durchgang.name(8));
        // Unbekannte Nummern dürfen nicht zum Absturz führen.
        assertEquals("Durchgang 99", Durchgang.name(99));
        assertEquals("Durchgang null", Durchgang.name(null));
    }

    @Test
    @DisplayName("Jede Zeile weiß, die wievielte Übung sie ist")
    void uebungsnummerGesetzt() {
        for (Wertung w : Beispieldaten.extraktor().extrahieren(Beispieldaten.cbi2024())) {
            assertNotNull(w.uebung, "Übungsnummer fehlt bei " + w);
            assertNotNull(w.uebungen);
            assertTrue(w.uebung >= 1 && w.uebung <= w.uebungen,
                    "Übung " + w.uebung + " von " + w.uebungen + " bei " + w);
        }
    }

    // -------------------------------------------------------- Verein / Nation

    @Test
    @DisplayName("Beim CBI steht im Representing ein Verein")
    void cbiHatVereine() {
        List<Wertung> wertungen = Beispieldaten.extraktor()
                .extrahieren(Beispieldaten.cbi2024());

        assertFalse(wertungen.isEmpty());
        for (Wertung w : wertungen) {
            assertNull(w.nation,
                    "CAP-SP und MTC-MG sind Vereine, keine Länderkürzel.");
            assertNotNull(w.verein);
        }
    }

    @Test
    @DisplayName("Beim World Cup steht im Representing eine Nation")
    void worldCupHatNationen() {
        List<Wertung> wertungen = Beispieldaten.extraktor()
                .extrahieren(Beispieldaten.cottbus2024());

        assertFalse(wertungen.isEmpty());
        for (Wertung w : wertungen) {
            assertNull(w.verein, "GER und JPN sind Länderkürzel, keine Vereine.");
            assertNotNull(w.nation);
            assertEquals(3, w.nation.length());
        }
    }

    // ------------------------------------------------------------- Jahrgang

    @Test
    @DisplayName("Fehlt das Geburtsdatum, bleibt der Jahrgang leer")
    void fehlenderJahrgangWirdNull() {
        // In Cottbus 2024 hat kein einziger Athlet ein DateOfBirth.
        EventDaten cottbus = Beispieldaten.cottbus2024();
        for (EventDaten.Athlete a : cottbus.Athletes.values()) {
            assertNull(a.DateOfBirth,
                    "Das Beispiel ist genau wegen der fehlenden Geburtsdaten dabei.");
        }

        for (Wertung w : Beispieldaten.extraktor().extrahieren(cottbus)) {
            assertNull(w.jahrgang);
        }

        // Beim CBI ist das Geburtsdatum da, aber nur das Jahr ist echt.
        for (Wertung w : Beispieldaten.extraktor().extrahieren(Beispieldaten.cbi2024())) {
            assertNotNull(w.jahrgang);
            assertTrue(w.jahrgang > 1950 && w.jahrgang < 2030,
                    "Unplausibler Jahrgang: " + w.jahrgang);
        }
    }

    @Test
    @DisplayName("Jahrgang wird aus dem Geburtsdatum gelesen, Platzhalter nicht")
    void jahrgangAusDatum() {
        assertEquals(2006, WertungsExtraktor.jahrgangAus("2006-01-01T00:00:00Z"));
        assertNull(WertungsExtraktor.jahrgangAus(null));
        assertNull(WertungsExtraktor.jahrgangAus(""));
        assertNull(WertungsExtraktor.jahrgangAus("0001-01-01T00:00:00Z"));
        assertNull(WertungsExtraktor.jahrgangAus("keine Zahl"));
    }

    // -------------------------------------------------------------- Datum

    @Test
    @DisplayName("Das Datum kommt aus dem Veröffentlichungszeitpunkt")
    void datumAusVeroeffentlichung() {
        assertEquals("2024-06-29",
                WertungsExtraktor.datumAusZeitstempel("2024-06-29T17:29:49.08-03:00"));
        assertNull(WertungsExtraktor.datumAusZeitstempel("0001-01-01T00:00:00Z"),
                "Der Platzhalter 0001-01-01 ist kein Datum.");
        assertNull(WertungsExtraktor.datumAusZeitstempel(null));
        assertNull(WertungsExtraktor.datumAusZeitstempel("kurz"));
    }

    // ------------------------------------------------------------ Link

    @Test
    @DisplayName("Jede Zeile verweist auf die Ergebnisseite ihres Durchgangs")
    void linkZeigtAufDieErgebnisseite() {
        for (Wertung w : Beispieldaten.extraktor().extrahieren(Beispieldaten.cbi2024())) {
            assertNotNull(w.link);
            assertTrue(w.link.startsWith("https://sporttech.io/events/" + w.eventId
                    + "/ovs/stages/"), "Unerwarteter Link: " + w.link);
            assertTrue(w.link.endsWith("/results"), "Unerwarteter Link: " + w.link);
        }
    }

    private static double oderNull(Double wert) {
        return wert == null ? 0.0 : wert;
    }
}
