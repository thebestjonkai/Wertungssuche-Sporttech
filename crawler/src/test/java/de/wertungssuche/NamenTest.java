package de.wertungssuche;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Prüft die Namensnormalisierung und die Aufteilung auf die Dateien.
 *
 * <p>Diese Regeln stecken doppelt im Projekt: hier in {@link Namen} und in
 * {@code docs/app.js}. Wenn die beiden auseinanderlaufen, sucht die Website
 * in der falschen Datei und findet nichts. Die Fälle hier sind deshalb
 * dieselben, die auch die Website können muss.
 */
class NamenTest {

    @ParameterizedTest(name = "\"{0}\" wird zu \"{1}\"")
    @DisplayName("Umlaute, Akzente und Großschreibung werden vereinheitlicht")
    @CsvSource({
        "Müller,       muller",
        "MÜLLER,       muller",
        "müller,       muller",
        "Mueller,      mueller",
        "Schäfer,      schafer",
        "Kröger,       kroger",
        "Strauß,       strauss",
        "Ärnström,     arnstrom",
        "Čech,         cech",
        "Désirée,      desiree",
        "Łukasz,       łukasz",
        "'  Lena  ',   lena",
        "'Anna  Lena', anna lena",
        "Anna-Lena,    anna-lena"
    })
    void normalisieren(String eingabe, String erwartet) {
        assertEquals(erwartet, Namen.normalisieren(eingabe));
    }

    @Test
    @DisplayName("Null und Leerstring stürzen nicht ab")
    void leereEingaben() {
        assertEquals("", Namen.normalisieren(null));
        assertEquals("", Namen.normalisieren(""));
        assertEquals("", Namen.normalisieren("   "));
    }

    @ParameterizedTest(name = "\"{0}\" gehört nach {1}.json")
    @DisplayName("Die Datei ergibt sich aus dem ersten Buchstaben")
    @CsvSource({
        "Müller,     m",
        "Ärnström,   a",
        "Öztürk,     o",
        "Überle,     u",
        "Čech,       c",
        "Schmidt,    s",
        "Zimmermann, z",
        "'',         other",
        "'123',      other",
        "'-Test',    other"
    })
    void datei(String nachname, String erwartet) {
        assertEquals(erwartet, Namen.datei(nachname));
    }

    @Test
    @DisplayName("Kyrillische Namen landen in other.json")
    void kyrillischNachOther() {
        // Kommt wirklich vor: manuell nachgeladene Events aus Russland.
        assertEquals("other", Namen.datei("Иванов"));
        assertEquals("other", Namen.datei(null));
    }

    @Test
    @DisplayName("Aufräumen entfernt überflüssige Leerzeichen")
    void aufraeumen() {
        assertEquals("TG Münster 1862", Namen.aufraeumen("  TG  Münster   1862 "));
        assertEquals("Lena", Namen.aufraeumen("Lena"));
        assertNull(Namen.aufraeumen("   "));
        assertNull(Namen.aufraeumen(""));
        assertNull(Namen.aufraeumen(null));
    }

    @Test
    @DisplayName("Aufräumen verändert die Schreibweise nicht")
    void aufraeumenLaesstSchreibweise() {
        // Anders als normalisieren: das hier ist die Form, die gespeichert
        // und angezeigt wird.
        assertEquals("Müller", Namen.aufraeumen("Müller"));
        assertEquals("MÜLLER", Namen.aufraeumen("MÜLLER"));
    }
}
