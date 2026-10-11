package de.wertungssuche;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rechnet die Ganzzahlen von sporttech.io in echte Punktzahlen um.
 *
 * <p>sporttech.io speichert alle Wertungen als Ganzzahlen mit festem Faktor:
 * eine D-Note von 18,0 steht als {@code 180} in der API, eine Flugzeit von
 * 15,955 s als {@code 15955}. Hier wird daraus wieder eine Kommazahl.
 *
 * <p>Zwei Dinge sind dabei wichtig:
 * <ul>
 *   <li>Ein fehlendes Feld ({@code null}) ist nicht dasselbe wie eine 0.
 *       {@link #teilen} gibt bei {@code null} auch {@code null} zurück.</li>
 *   <li>Gerundet wird über {@link BigDecimal}, nicht über {@code double}.
 *       Mit {@code double} käme bei 180/10 schon mal 17.999999999999996 heraus,
 *       und das würde dann so in der JSON-Datei landen.</li>
 * </ul>
 */
public final class Zahlen {

    private Zahlen() {
        // Hilfsklasse, wird nicht erzeugt
    }

    /**
     * Teilt einen API-Wert durch einen Faktor und rundet auf so viele
     * Nachkommastellen, wie der Faktor Stellen hat.
     *
     * @param wert   der Rohwert aus der API, darf {@code null} sein
     * @param faktor 10, 100 oder 1000
     * @return die Punktzahl, oder {@code null} wenn {@code wert} null war
     */
    public static Double teilen(Integer wert, int faktor) {
        if (wert == null) {
            return null;
        }
        int stellen = String.valueOf(faktor).length() - 1;
        return BigDecimal.valueOf(wert)
                .divide(BigDecimal.valueOf(faktor), stellen, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Wie {@link #teilen}, aber gibt {@code null} zurück, wenn der Wert 0 oder
     * negativ ist.
     *
     * <p>Gebraucht für Flugzeit und Verlagerung: bei Doppel-Minitrampolin und
     * Tumbling gibt es diese Wertungen gar nicht, und die API liefert stur 0.
     * Dasselbe passiert bei Wettkämpfen, die keine Flugzeit messen – die
     * Deutschen Hochschulmeisterschaften 2024 zum Beispiel haben in sechs
     * Wettkämpfen durchgehend {@code TimeTMS_G == 0}. In unseren Daten soll
     * dann {@code null} stehen, damit auf der Website kein "0,00" erscheint,
     * wo es nichts zu zeigen gibt.
     */
    public static Double teilenNullWennNull(Integer wert, int faktor) {
        if (wert == null || wert <= 0) {
            return null;
        }
        return teilen(wert, faktor);
    }

    /**
     * Wie {@link #teilen}, aber gibt {@code null} zurück, wenn der Wert negativ
     * ist. Eine echte 0 bleibt eine 0.
     *
     * <p>sporttech.io benutzt <b>−1 als Marker für "nicht gesetzt"</b> – das
     * sieht man auch an Feldern wie {@code DefaultNElements} oder
     * {@code JudgesPanelID}. Bei der D-Note kommt das wirklich vor: Frame 1016
     * des 3. Wölfe Open 2024 hat {@code DifficultyT_G == -1}, und das
     * Gesamtergebnis rechnet mit 0. Eine D-Note von echten 0,0 gibt es
     * daneben auch (Anfängerübungen), die darf nicht verschwinden.
     */
    public static Double teilenNullWennNegativ(Integer wert, int faktor) {
        if (wert == null || wert < 0) {
            return null;
        }
        return teilen(wert, faktor);
    }

    /** Behandelt ein fehlendes Feld wie eine 0 – für Summen von Abzügen. */
    public static int oderNull(Integer wert) {
        return wert == null ? 0 : wert;
    }

    /** Wie {@link #oderNull}, behandelt zusätzlich negative Werte als 0. */
    public static int oderNullNichtNegativ(Integer wert) {
        if (wert == null || wert < 0) {
            return 0;
        }
        return wert;
    }

    /** Rundet eine Kommazahl sauber auf die angegebenen Nachkommastellen. */
    public static Double runden(double wert, int stellen) {
        return BigDecimal.valueOf(wert)
                .setScale(stellen, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
