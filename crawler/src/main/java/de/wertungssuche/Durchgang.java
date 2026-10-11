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
     * Der Name des Durchgangs – <b>ohne</b> Übungsnummer.
     *
     * <p>Die Nummer der Übung gehört bewusst nicht hierher, sondern in das
     * eigene Feld {@link Wertung#uebung}. Früher stand hier "Qualifikation 1"
     * und "Qualifikation 2" für die zwei Übungen eines Vorkampfs – das war
     * gleich doppelt missverständlich:
     *
     * <ul>
     *   <li>Im Trampolinturnen heißt "Qualifikation 2" üblicherweise der
     *       <b>zweite Durchgang</b>, also das Halbfinale – nicht die zweite
     *       Übung des Vorkampfs. Beim FIG World Cup Cottbus 2024 (Männer)
     *       sieht man den Unterschied an den Starterzahlen: Qualifikation
     *       89 Starter in <i>beiden</i> Übungen, danach ein eigener
     *       Durchgang mit 24 Startern, dann 8 im Finale.</li>
     *   <li>Es kollidierte mit echten Durchgangsnamen: {@link #FINALE_1}
     *       heißt "Finale 1" – und ein Finale aus drei Übungen hätte
     *       ebenfalls "Finale 1", "Finale 2", "Finale 3" ergeben. Genau das
     *       kommt im Wölfe Open 13.09.2025 vor.</li>
     * </ul>
     *
     * @param kind {@code Stage.Kind}
     */
    public static String name(Integer kind) {
        Durchgang d = vonNummer(kind);
        return (d != null) ? d.anzeigename() : ("Durchgang " + kind);
    }
}
