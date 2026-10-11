package de.wertungssuche;

/**
 * Die Disziplinen innerhalb eines Trampolin-Events.
 *
 * <p>Die Nummern stammen aus {@code TRA.shadow.CompetitionTypes} im
 * JavaScript-Bundle {@code https://sporttech.io/static/TRA/script.min.js} und
 * stehen in {@code Competition.Discipline}.
 *
 * <p><b>Achtung:</b> diese Nummern gelten nur innerhalb von Trampolin-Events.
 * Im Gerätturnen bedeutet {@code Discipline} etwas völlig anderes (1 = Boden,
 * 2 = Sprung und so weiter). Ob ein Event überhaupt Trampolin ist, entscheidet
 * allein das Feld {@code sport} aus der Eventliste.
 */
public enum Disziplin {

    /** Trampolin Einzel – das, was wir sammeln. */
    TRAMPOLIN(0, "Trampolin"),

    /** Synchronspringen. */
    SYNCHRON(1, "Synchron"),

    /** Doppel-Minitrampolin. */
    DOPPEL_MINI(2, "Doppel-Mini"),

    /** Tumbling. */
    TUMBLING(3, "Tumbling"),

    /** Team-Mehrkampf; die echte Disziplin steht dann in {@code Performance.Discipline}. */
    TEAM(4, "Team");

    private final int nummer;
    private final String anzeigename;

    Disziplin(int nummer, String anzeigename) {
        this.nummer = nummer;
        this.anzeigename = anzeigename;
    }

    public int nummer() {
        return nummer;
    }

    /** Der Name, der später auf der Website steht. */
    public String anzeigename() {
        return anzeigename;
    }

    /**
     * Sucht die Disziplin zur API-Nummer.
     *
     * @return die Disziplin, oder {@code null} bei unbekannter oder fehlender Nummer
     */
    public static Disziplin vonNummer(Integer nummer) {
        if (nummer == null) {
            return null;
        }
        for (Disziplin d : values()) {
            if (d.nummer == nummer) {
                return d;
            }
        }
        return null;
    }
}
