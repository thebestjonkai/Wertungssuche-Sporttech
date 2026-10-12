package de.wertungssuche;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Prüft, dass eine Event-ID auch aus einer kompletten sporttech.io-Adresse
 * erkannt wird.
 *
 * <p>Das ist keine Spielerei: wer ein nicht gelistetes Event aufnehmen will,
 * hat die Adresse aus der Browser-Zeile in der Zwischenablage, nicht die
 * nackte UUID.
 */
class EventIdTest {

    private static final String ID = "d613f43d-fb1c-4beb-4770-9e684e2e0a8e";

    @ParameterizedTest(name = "{0}")
    @DisplayName("Die ID wird aus jeder Form der Adresse gelesen")
    @ValueSource(strings = {
        ID,
        "  " + ID + "  ",
        "https://sporttech.io/events/" + ID + "/ovs/",
        "https://sporttech.io/events/" + ID + "/ovs/scoreboards/",
        "https://sporttech.io/events/" + ID + "/ovs/stages/5/results",
        "https://sporttech.io/events/" + ID + "/ovs/event/TRA",
        "sporttech.io/events/" + ID,
        "https://sporttech.io/api/events/" + ID + "?fetch_organizations=true"
    })
    void idAusAdresse(String eingabe) {
        assertEquals(ID, Main.eventIdAus(eingabe));
    }

    @Test
    @DisplayName("Großschreibung in der ID stört nicht")
    void grossschreibung() {
        assertEquals(ID, Main.eventIdAus(ID.toUpperCase()));
    }

    @ParameterizedTest
    @DisplayName("Ohne ID kommt null zurück, kein Absturz")
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "https://sporttech.io/explore",
        "Deutschland Cup 2025",
        "1234",
        // Zu kurz, um eine ID zu sein
        "d613f43d-fb1c-4beb-4770-9e684e2e0a8"
    })
    void keineId(String eingabe) {
        assertNull(Main.eventIdAus(eingabe));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("sporttech.io benutzt auch IDs, die keine gültige UUID sind")
    @CsvSource({
        // Vierte Gruppe beginnt mit 4 oder 7 - bei einer echten UUID
        // kodiert diese Stelle die Variante und müsste 8, 9, a oder b sein.
        // Geprüft wird deshalb nur das Muster 8-4-4-4-12.
        "'https://sporttech.io/events/0aaa0cc5-bc38-4ce6-6dd0-eff5a170a7ed/ovs/', 0aaa0cc5-bc38-4ce6-6dd0-eff5a170a7ed",
        "'https://sporttech.io/events/91f733c2-17e1-402f-74d8-60b7dfbf28b7/ovs/', 91f733c2-17e1-402f-74d8-60b7dfbf28b7"
    })
    void echteIdsVonSporttech(String adresse, String erwartet) {
        assertEquals(erwartet, Main.eventIdAus(adresse));
    }
}
