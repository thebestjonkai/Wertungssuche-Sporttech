package de.wertungssuche;

import de.wertungssuche.api.EventListe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft, welche Events der Crawler überhaupt anfasst: den Trampolin-Filter
 * und die Entscheidung, ob ein bekanntes Event erneut geladen werden muss.
 */
class EventAuswahlTest {

    // ------------------------------------------------------ Trampolin-Filter

    @Test
    @DisplayName("Aus der Beispiel-Eventliste bleiben nur die Trampolin-Events übrig")
    void nurTrampolinEventsBleiben() {
        EventListe liste = Beispieldaten.eventListe();

        long trampolin = liste.event.values().stream()
                .filter(Main::istTrampolin)
                .count();

        assertEquals(5, liste.event.size(),
                "Das Beispiel soll fünf Events enthalten.");
        assertEquals(3, trampolin,
                "Drei davon sind Trampolin, zwei sind Gerätturnen.");
    }

    @Test
    @DisplayName("Beide Felder müssen passen, sport und rules")
    void beideFelderMuessenPassen() {
        assertTrue(Main.istTrampolin(event("Trampoline", "TRA FIG 2022")));
        assertTrue(Main.istTrampolin(event("Trampoline", "TRA FIG 2017")));
        assertTrue(Main.istTrampolin(event("Trampoline", "TRA DEN")));
        assertTrue(Main.istTrampolin(event("trampoline", " tra fig ")),
                "Groß- und Kleinschreibung darf keine Rolle spielen.");

        assertFalse(Main.istTrampolin(event("Artistic gymnastics", "AG DEF")));
        assertFalse(Main.istTrampolin(event("Rhythmic gymnastics", "RG FIG 2022")));
        assertFalse(Main.istTrampolin(event("Trampoline", "AG DEF")),
                "Sportart passt, Reglement nicht - dann lieber auslassen.");
        assertFalse(Main.istTrampolin(event(null, "TRA FIG 2022")));
        assertFalse(Main.istTrampolin(event("Trampoline", null)));
    }

    // ------------------------------------------------- Erneut laden oder nicht

    @Test
    @DisplayName("Ein unbekanntes Event wird geladen")
    void unbekanntesEventWirdGeladen() {
        EventStatus status = new EventStatus();
        assertTrue(status.mussGeladenWerden("neu", null, LocalDate.of(2026, 10, 11), 14));
        assertFalse(status.istBekannt("neu"));
    }

    @Test
    @DisplayName("Ein lange abgeschlossenes Event wird nicht erneut geladen")
    void abgeschlossenesEventWirdUebersprungen() {
        EventStatus status = new EventStatus();
        status.events.put("alt", eintrag("2024-06-30", EventStatus.ABGESCHLOSSEN,
                "2024-07-05T10:00:00Z"));

        assertFalse(status.mussGeladenWerden("alt", "2024-07-05T10:00:00Z",
                LocalDate.of(2026, 10, 11), 14));
        assertTrue(status.istBekannt("alt"));
    }

    @Test
    @DisplayName("Ein gerade beendetes Event wird erneut geladen")
    void kuerzlichBeendetesEventWirdNeuGeladen() {
        // Kampfgerichte korrigieren Wertungen noch Tage nach dem Wettkampf.
        EventStatus status = new EventStatus();
        status.events.put("frisch", eintrag("2026-10-05", EventStatus.ABGESCHLOSSEN,
                "2026-10-05T18:00:00Z"));

        assertTrue(status.mussGeladenWerden("frisch", "2026-10-05T18:00:00Z",
                LocalDate.of(2026, 10, 11), 14),
                "Enddatum liegt sechs Tage zurück, die Grenze sind 14 Tage.");
    }

    @Test
    @DisplayName("Ein Event mit Fehler wird beim nächsten Lauf wieder versucht")
    void fehlerhaftesEventWirdWiederVersucht() {
        EventStatus status = new EventStatus();
        status.events.put("kaputt", eintrag("2024-01-10", EventStatus.FEHLER,
                "2024-01-15T10:00:00Z"));

        assertTrue(status.mussGeladenWerden("kaputt", "2024-01-15T10:00:00Z",
                LocalDate.of(2026, 10, 11), 14));
    }

    @Test
    @DisplayName("Ein auf sporttech.io geändertes Event wird erneut geladen")
    void geaendertesEventWirdNeuGeladen() {
        EventStatus status = new EventStatus();
        status.events.put("geaendert", eintrag("2024-06-30",
                EventStatus.ABGESCHLOSSEN, "2024-07-05T10:00:00Z"));

        assertTrue(status.mussGeladenWerden("geaendert", "2026-09-01T12:00:00Z",
                LocalDate.of(2026, 10, 11), 14),
                "updated_at ist neuer als unser letzter Stand.");
    }

    @Test
    @DisplayName("Ein Event ohne Enddatum wird vorsichtshalber geladen")
    void eventOhneEnddatumWirdGeladen() {
        EventStatus status = new EventStatus();
        status.events.put("ohne", eintrag(null, EventStatus.OFFEN, null));

        assertTrue(status.mussGeladenWerden("ohne", null,
                LocalDate.of(2026, 10, 11), 14));
    }

    @Test
    @DisplayName("Von Hand aufgenommene Events laufen in Folgeläufen automatisch mit")
    void manuelleEventsLaufenMit() {
        EventStatus status = new EventStatus();

        EventStatus.Eintrag manuell = eintrag("2024-06-30",
                EventStatus.ABGESCHLOSSEN, "2024-07-05T10:00:00Z");
        manuell.manuell = true;
        status.events.put("ausland", manuell);

        status.events.put("normal", eintrag("2024-06-30",
                EventStatus.ABGESCHLOSSEN, "2024-07-05T10:00:00Z"));

        assertEquals(List.of("ausland"),
                List.copyOf(status.manuelleEvents().keySet()),
                "Nur das von Hand aufgenommene Event darf hier auftauchen.");
    }

    @Test
    @DisplayName("Datumsangaben werden robust gelesen")
    void datumRobustLesen() {
        assertEquals(LocalDate.of(2024, 6, 30),
                EventStatus.datumOderNull("2024-06-30"));
        assertEquals(LocalDate.of(2024, 6, 30),
                EventStatus.datumOderNull("2024-06-30T12:00:00Z"));
        assertEquals(null, EventStatus.datumOderNull(null));
        assertEquals(null, EventStatus.datumOderNull("kurz"));
        assertEquals(null, EventStatus.datumOderNull("2024-13-45"));
    }

    private static EventListe.Event event(String sport, String rules) {
        EventListe.Event e = new EventListe.Event();
        e.sport = sport;
        e.rules = rules;
        return e;
    }

    private static EventStatus.Eintrag eintrag(String ende, String status,
                                               String standSporttech) {
        EventStatus.Eintrag e = new EventStatus.Eintrag();
        e.ende = ende;
        e.status = status;
        e.standSporttech = standSporttech;
        return e;
    }
}
