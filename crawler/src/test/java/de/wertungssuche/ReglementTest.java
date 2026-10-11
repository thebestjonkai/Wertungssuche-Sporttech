package de.wertungssuche;

import de.wertungssuche.api.EventDaten;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Prüft die Sonderregel für die E-Note und die Platzhalter in den Zahlen.
 *
 * <p>Beides sind Fallen, in die man ohne diese Tests wieder hineinläuft:
 * <ul>
 *   <li>Die E-Note ist nicht immer {@code ETotalT_G / 10} – bei anderen
 *       Reglements als 2017 und 2022 wird vorher mit 3 multipliziert.</li>
 *   <li>Eine {@code -1} bedeutet "nicht gesetzt" und nicht "minus 0,1".</li>
 * </ul>
 */
class ReglementTest {

    // --------------------------------------------------------------- Subtyp

    @Test
    @DisplayName("Der Subtyp ist der dritte von genau vier Teilen des RulesCode")
    void subtypLesen() {
        assertEquals("2022", Reglement.subtyp("TRA.FIG.2022.2024-06-26"));
        assertEquals("2017", Reglement.subtyp("TRA.FIG.2017.2020-10-03"));
        // Vier Teile, dritter leer - genau so steht es beim Kölner Event.
        assertEquals("", Reglement.subtyp("TRA.FIG..2026-06-28"));
        // Nicht vier Teile: kein Subtyp.
        assertNull(Reglement.subtyp("TRA.FIG.2022"));
        assertNull(Reglement.subtyp("TRA FIG 2022"));
        assertNull(Reglement.subtyp(null));
    }

    @Test
    @DisplayName("Nur die Reglements 2017 und 2022 lassen die E-Note unverändert")
    void eFaktor() {
        assertEquals(1, Reglement.eFaktor("TRA.FIG.2022.2024-06-26"));
        assertEquals(1, Reglement.eFaktor("TRA.FIG.2017.2020-10-03"));

        // Offene Kölner Pokalwettkämpfe 2026: leerer Subtyp -> Faktor 3.
        assertEquals(3, Reglement.eFaktor("TRA.FIG..2026-06-28"));
        assertEquals(3, Reglement.eFaktor("TRA.DEN.irgendwas.2025-01-01"));
        assertEquals(3, Reglement.eFaktor(null));
    }

    @Test
    @DisplayName("Durchgänge mit Durchschnittsrechnung teilen durch 1000")
    void eTeiler() {
        EventDaten.Stage ohne = new EventDaten.Stage();
        ohne.CalcOptions = List.of(5, 39);
        assertEquals(10, Reglement.eTeiler(ohne));

        EventDaten.Stage ohneAngabe = new EventDaten.Stage();
        assertEquals(10, Reglement.eTeiler(ohneAngabe));
        assertEquals(10, Reglement.eTeiler(null));

        for (int option : new int[] { 3, 16, 17 }) {
            EventDaten.Stage mit = new EventDaten.Stage();
            mit.CalcOptions = List.of(4, option, 22);
            assertEquals(1000, Reglement.eTeiler(mit),
                    "CalcOption " + option + " muss den Teiler 1000 ergeben.");
        }
    }

    @Test
    @DisplayName("Die Rechnung des Kölner Beispiels geht mit Faktor 3 auf")
    void koelnerBeispiel() {
        // Offene Kölner Pokalwettkämpfe 2026, Frame 312:
        //   MarkTTT_G 28100, DifficultyT_G 0, TimeTMS_G 8600,
        //   Displacement_G 0, ETotalT_G 65
        // Mit Faktor 1 käme E = 6,5 heraus und die Summe wäre 15,1 statt 28,1.
        int eFaktor = Reglement.eFaktor("TRA.FIG..2026-06-28");
        double e = 65 * eFaktor / 10.0;
        double summe = 0.0 + e + 8600 / 1000.0 + 0.0;

        assertEquals(19.5, e, 0.0005, "E muss 19,5 sein, nicht 6,5.");
        assertEquals(28.1, summe, 0.0005);
    }

    // ------------------------------------------------------- Zahlen und -1

    @Test
    @DisplayName("Minus eins heißt nicht gesetzt, nicht minus 0,1")
    void minusEinsIstKeinWert() {
        assertNull(Zahlen.teilenNullWennNegativ(-1, 10));
        assertEquals(0.0, Zahlen.teilenNullWennNegativ(0, 10),
                "Eine echte D-Note von 0,0 gibt es und muss erhalten bleiben.");
        assertEquals(18.0, Zahlen.teilenNullWennNegativ(180, 10));
        assertNull(Zahlen.teilenNullWennNegativ(null, 10));
    }

    @Test
    @DisplayName("Eine Null bei Flugzeit und Verlagerung bedeutet: nicht gemessen")
    void nullBedeutetNichtGemessen() {
        assertNull(Zahlen.teilenNullWennNull(0, 1000));
        assertNull(Zahlen.teilenNullWennNull(null, 1000));
        assertEquals(15.955, Zahlen.teilenNullWennNull(15955, 1000));
        assertEquals(9.3, Zahlen.teilenNullWennNull(930, 100));
    }

    @Test
    @DisplayName("Geteilt wird ohne Rundungsfehler aus der Gleitkommarechnung")
    void sauberesTeilen() {
        // Mit double käme hier 17.999999999999996 heraus, und genau das
        // würde dann in der JSON-Datei stehen.
        assertEquals(18.0, Zahlen.teilen(180, 10));
        assertEquals(58.055, Zahlen.teilen(58055, 1000));
        assertEquals(9.3, Zahlen.teilen(930, 100));
        assertEquals(7.05, Zahlen.teilen(7050, 1000));
        assertNull(Zahlen.teilen(null, 10));
    }

    @Test
    @DisplayName("Fehlende Felder zählen bei Abzügen wie eine Null")
    void fehlendeFelderAlsNull() {
        assertEquals(0, Zahlen.oderNull(null));
        assertEquals(120, Zahlen.oderNull(120));
        assertEquals(0, Zahlen.oderNullNichtNegativ(-5));
        assertEquals(0, Zahlen.oderNullNichtNegativ(null));
        assertEquals(60, Zahlen.oderNullNichtNegativ(60));
    }
}
