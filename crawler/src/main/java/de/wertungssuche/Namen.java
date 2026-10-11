package de.wertungssuche;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalisiert Namen – für die Dateiaufteilung und später für die Suche.
 *
 * <p>Die Website muss "Müller" auch bei der Eingabe "muller" oder "MUELLER"
 * finden. Dafür wird jeder Name auf eine Vergleichsform gebracht:
 * kleingeschrieben, Umlaute aufgelöst, Akzente entfernt.
 *
 * <p><b>Diese Regeln müssen in {@code docs/app.js} genauso implementiert
 * sein</b> – sonst sucht die Website in der falschen Datei. Die JUnit-Tests
 * prüfen die Java-Seite, der Kommentar in der app.js verweist hierher.
 */
public final class Namen {

    /** Dateiname für alles, was nicht mit a–z beginnt. */
    public static final String SAMMELDATEI = "other";

    private Namen() {
        // Hilfsklasse
    }

    /**
     * Bringt einen Namen auf die Vergleichsform.
     *
     * <p>Reihenfolge der Schritte:
     * <ol>
     *   <li>kleinschreiben</li>
     *   <li>deutsche Umlaute ersetzen: ä→a, ö→o, ü→u, ß→ss</li>
     *   <li>alle übrigen Akzente entfernen (é→e, ñ→n, č→c, å→a)</li>
     *   <li>mehrere Leerzeichen zu einem zusammenziehen</li>
     * </ol>
     *
     * <p>Schritt 2 muss <b>vor</b> Schritt 3 kommen: die Unicode-Zerlegung
     * würde aus "ü" sonst einfach "u" machen. Für die Suche ist das zwar auch
     * brauchbar, aber die Aufgabenstellung verlangt ausdrücklich ü→u und nicht
     * ü→ue, also ist die Reihenfolge hier bewusst so gewählt und "ß"→"ss"
     * bleibt der einzige Fall, der länger wird.
     */
    public static String normalisieren(String name) {
        if (name == null) {
            return "";
        }
        String s = name.toLowerCase(Locale.GERMAN);

        s = s.replace("ä", "a")
             .replace("ö", "o")
             .replace("ü", "u")
             .replace("ß", "ss");

        // Zerlegt "é" in "e" + Akzent und wirft dann alle Akzentzeichen weg.
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                      .replaceAll("\\p{M}+", "");

        return s.trim().replaceAll("\\s+", " ");
    }

    /**
     * Der Dateiname, in dem die Wertungen dieses Nachnamens landen:
     * der erste Buchstabe der Vergleichsform, oder {@code other}.
     *
     * <p>Beispiele: "Müller" → {@code m}, "Ärnström" → {@code a},
     * "Čech" → {@code c}, "" → {@code other}, "1. FC" → {@code other}.
     */
    public static String datei(String nachname) {
        String norm = normalisieren(nachname);
        if (norm.isEmpty()) {
            return SAMMELDATEI;
        }
        char erster = norm.charAt(0);
        if (erster >= 'a' && erster <= 'z') {
            return String.valueOf(erster);
        }
        return SAMMELDATEI;
    }

    /**
     * Macht aus einem Namen die Form, die wir speichern: Leerzeichen am Rand
     * weg, mehrfache Leerzeichen zusammengezogen, leer wird {@code null}.
     */
    public static String aufraeumen(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }
}
