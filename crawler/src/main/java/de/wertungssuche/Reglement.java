package de.wertungssuche;

import de.wertungssuche.api.EventDaten;

import java.util.List;

/**
 * Die Umrechnung der E-Note – der einzige Wert, der nicht einfach durch einen
 * festen Faktor geteilt wird.
 *
 * <h2>Warum das nötig ist</h2>
 *
 * <p>Bei den meisten Events gilt {@code E = ETotalT_G / 10}. Bei einem Teil der
 * deutschen Nachwuchswettkämpfe stimmt das aber nicht. Beispiel: Offene Kölner
 * Pokalwettkämpfe 2026, Wettkampf WK13, erste Übung von Elia Altinova:
 *
 * <pre>
 * MarkTTT_G      = 28100   ->  Gesamt   28,100
 * DifficultyT_G  =     0   ->  D         0,0
 * TimeTMS_G      =  8600   ->  T         8,600
 * Displacement_G =     0   ->  H         0,00
 * ETotalT_G      =    65   ->  E = ?
 * </pre>
 *
 * <p>Damit die Summe aufgeht, muss E = 19,5 sein – also {@code 65 × 3 / 10} und
 * nicht {@code 65 / 10}. Mit dem einfachen Faktor 10 wären 200 von 15 258
 * Zeilen falsch gewesen.
 *
 * <h2>Die maßgebliche Regel</h2>
 *
 * <p>Nicht geraten, sondern aus sporttechs eigener Anzeige-Logik übernommen.
 * Im Bundle {@code https://sporttech.io/static/TRA/script.min.js} steht:
 *
 * <pre>
 * M.filter("EScore", function(f, m) {
 *     var s = &lt;Stage des Frames&gt;;
 *     var subtype = common.filters.getSubtype(m.Event.RulesCode);
 *     if (subtype != "2017" &amp;&amp; subtype != "2022") {
 *         return ETotalFilter(EMultipliedFilter(f), s);   // EMultiplied = ETotalT_G * 3
 *     }
 *     if (f.Discipline_G != SYN) {
 *         return ETotalFilter(f.ETotalT_G, s);
 *     }
 *     return SynETotalFilter(f.ETotalT_G);
 * });
 *
 * M.filter("ETotal", function(n, s) {
 *     if (hasCalcOption(s, CalcCroppedAvg) || hasCalcOption(s, CalcAvgX2)
 *             || hasCalcOption(s, CalcAvgX3)) {
 *         return formatDDD(n);     // n / 1000
 *     }
 *     return formatD(n);           // n / 10
 * });
 *
 * getSubtype = function(rulesCode) {
 *     var parts = rulesCode.split(".");
 *     if (parts.length != 4) { return; }
 *     return parts[2];
 * };
 * </pre>
 *
 * <p>Zusammengefasst: aus {@code TRA.FIG.2022.2024-06-26} wird der Subtyp
 * {@code "2022"}. Nur bei {@code "2017"} und {@code "2022"} wird die E-Note
 * unverändert genommen, bei jedem anderen Reglement wird sie vorher mit 3
 * multipliziert. Geteilt wird dann durch 10 – außer der Durchgang benutzt eine
 * Durchschnittsrechnung, dann durch 1000.
 */
public final class Reglement {

    /** {@code CalcKinds.CalcCroppedAvg} aus dem TRA-Bundle. */
    private static final int CALC_CROPPED_AVG = 3;
    /** {@code CalcKinds.CalcAvgX2}. */
    private static final int CALC_AVG_X2 = 16;
    /** {@code CalcKinds.CalcAvgX3}. */
    private static final int CALC_AVG_X3 = 17;

    private Reglement() {
        // Hilfsklasse
    }

    /**
     * Der Subtyp aus dem {@code RulesCode}, also der dritte von vier
     * punktgetrennten Teilen.
     *
     * @return z. B. {@code "2022"}, oder {@code null} wenn der Code nicht aus
     *         genau vier Teilen besteht
     */
    public static String subtyp(String rulesCode) {
        if (rulesCode == null) {
            return null;
        }
        String[] teile = rulesCode.split("\\.");
        if (teile.length != 4) {
            return null;
        }
        return teile[2];
    }

    /**
     * Mit welchem Faktor die E-Note vor dem Teilen multipliziert wird: 1 bei
     * den Reglements 2017 und 2022, sonst 3.
     */
    public static int eFaktor(String rulesCode) {
        String subtyp = subtyp(rulesCode);
        if ("2017".equals(subtyp) || "2022".equals(subtyp)) {
            return 1;
        }
        return 3;
    }

    /**
     * Durch welchen Wert die E-Note geteilt wird: 1000, wenn der Durchgang mit
     * Durchschnitten rechnet, sonst 10.
     */
    public static int eTeiler(EventDaten.Stage stage) {
        if (stage == null) {
            return 10;
        }
        List<Integer> optionen = stage.CalcOptions;
        if (optionen == null) {
            return 10;
        }
        if (optionen.contains(CALC_CROPPED_AVG)
                || optionen.contains(CALC_AVG_X2)
                || optionen.contains(CALC_AVG_X3)) {
            return 1000;
        }
        return 10;
    }
}
