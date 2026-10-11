package de.wertungssuche;

/**
 * Die Arten von Durchgängen und ihre deutschen Namen.
 *
 * <p>Ein {@code Stage} in der API hat <b>keinen Titel</b> – nur das Feld
 * {@code Kind}. Die Nummern stammen aus {@code TRA.shadow.StageKinds} im Bundle
 * {@code https://sporttech.io/static/TRA/script.min.js}.
 */
public enum Durchgang {

    QUALIFIKATION(0, "Qualifikation"),
    VIERTELFINALE(1, "Viertelfinale"),
    HALBFINALE(2, "Halbfinale"),
    FINALE(3, "Finale"),
    TEAM_HALBFINALE(4, "Team-Halbfinale"),
    TEAM_FINALE(5, "Team-Finale"),
    QUALIFIKATION_2(6, "Qualifikation II"),
    FINALE_2(7, "Finale 2"),
    FINALE_1(8, "Finale 1");

    private final int nummer;
    private final String anzeigename;

    Durchgang(int nummer, String anzeigename) {
        this.nummer = nummer;
        this.anzeigename = anzeigename;
    }

    public int nummer() {
        return nummer;
    }

    public String anzeigename() {
        return anzeigename;
    }

    public static Durchgang vonNummer(Integer nummer) {
        if (nummer == null) {
            return null;
        }
        for (Durchgang d : values()) {
            if (d.nummer == nummer) {
                return d;
            }
        }
        return null;
    }

    /**
     * Baut den Namen, der später in der Spalte "Durchgang" steht.
     *
     * <p>Besteht ein Durchgang aus mehreren Übungen, wird die Übungsnummer
     * angehängt – aus {@code Kind == 0} mit zwei Übungen wird also
     * "Qualifikation 1" und "Qualifikation 2". Bei nur einer Übung bleibt es
     * bei "Finale".
     *
     * @param kind          {@code Stage.Kind}
     * @param uebungNr      1-basierte Nummer der Übung innerhalb des Durchgangs
     * @param anzahlUebungen {@code Stage.PerfomanceFramesLimit}
     */
    public static String name(Integer kind, int uebungNr, int anzahlUebungen) {
        Durchgang d = vonNummer(kind);
        String basis = (d != null) ? d.anzeigename() : ("Durchgang " + kind);
        if (anzahlUebungen > 1) {
            return basis + " " + uebungNr;
        }
        return basis;
    }
}
