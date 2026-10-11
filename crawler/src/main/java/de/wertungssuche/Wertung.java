package de.wertungssuche;

import java.util.Objects;

/**
 * Eine einzelne Wertung im flachen Zielformat – genau so, wie sie in den
 * JSON-Dateien unter {@code docs/data/} landet und von der Website gelesen wird.
 *
 * <p><b>Eine Zeile ist eine Übung</b>, nicht ein ganzer Durchgang. Eine
 * Qualifikation mit zwei Übungen erzeugt also zwei Zeilen
 * ("Qualifikation 1" und "Qualifikation 2") mit je eigenen D/E/T/H-Werten.
 * {@link #platz} und {@link #gesamtDurchgang} gehören zum ganzen Durchgang und
 * wiederholen sich auf beiden Zeilen.
 *
 * <p>Werte, die es nicht gibt, sind {@code null} und nicht 0 – bei
 * Doppel-Minitrampolin gibt es zum Beispiel keine Flugzeit.
 *
 * <p>Die Feldnamen sind absichtlich kleingeschrieben und deutsch: Gson schreibt
 * sie genau so in die JSON-Datei, und die Website liest sie genau so.
 */
public class Wertung {

    // --- Person ---
    public String vorname;
    public String nachname;
    /** Geburtsjahr, oder {@code null} wenn das Event es nicht veröffentlicht. */
    public Integer jahrgang;
    /** Verein, wenn {@code Representing} kein Länderkürzel ist. */
    public String verein;
    /** Nation als IOC-Kürzel, wenn {@code Representing} eines ist. */
    public String nation;

    // --- Wo und wann ---
    public String event;
    public String eventId;
    /** Tag der Veröffentlichung dieser Wertung, Format JJJJ-MM-TT. */
    public String datum;

    // --- Was ---
    /** {@code Trampolin}, {@code Synchron}, {@code Doppel-Mini} oder {@code Tumbling}. */
    public String disziplin;
    /** Name der Wettkampfklasse, z. B. {@code AK 13/14 (weiblich)}. */
    public String wettkampf;
    /** z. B. {@code Qualifikation 1}, {@code Finale}. */
    public String durchgang;
    /** Platz im gesamten Durchgang. */
    public Integer platz;

    // --- Die Wertungen dieser einen Übung ---
    /** D – Schwierigkeit. */
    public Double d;
    /** E – Ausführung. */
    public Double e;
    /** H – horizontale Verlagerung. {@code null} bei DMT und Tumbling. */
    public Double h;
    /** T – Flugzeit in Sekunden. {@code null} bei DMT und Tumbling. */
    public Double t;
    /** Abzüge, Summe aus {@code PenaltyT} und {@code DPenaltyT}. */
    public Double abzug;
    /** Gesamtergebnis dieser Übung. */
    public Double gesamt;
    /** Ergebnis des gesamten Durchgangs, zu dem auch {@link #platz} gehört. */
    public Double gesamtDurchgang;

    /** Ergebnisseite dieses Durchgangs auf sporttech.io. */
    public String link;

    /**
     * Der eindeutige Schlüssel dieser Wertung – gegen Duplikate.
     *
     * <p>Zusammengesetzt aus Event, Durchgang und Person. Die Person steckt mit
     * drin, weil ein Durchgang mehrere Starter hat; der Durchgangsname enthält
     * die Übungsnummer, so dass Übung 1 und Übung 2 auseinandergehalten werden.
     *
     * <p>Der Name wird normalisiert, damit eine Korrektur der Schreibweise
     * ("Mueller" → "Müller") in einem erneut geladenen Event nicht als zweite
     * Wertung durchgeht.
     */
    public String schluessel() {
        return eventId
                + "|" + durchgang
                + "|" + wettkampf
                + "|" + Namen.normalisieren(nachname)
                + "|" + Namen.normalisieren(vorname)
                + "|" + (jahrgang == null ? "" : jahrgang);
    }

    /**
     * Der Schlüssel der <b>Person</b> – danach gruppiert die Website die
     * Ergebnisse: Nachname, Vorname und Jahrgang.
     *
     * <p>Der <b>Verein gehört bewusst nicht dazu</b>, obwohl das zuerst
     * naheliegt. Die Vereinsangabe ist bei sporttech.io nicht stabil: jeder
     * Veranstalter trägt sie neu ein. Gemessen am fertigen Datenbestand
     * betrifft das <b>696 von 2 932 Personen</b> – sie hätten sonst mehrere
     * Karten statt einer. Beispiele aus den echten Daten:
     *
     * <pre>
     * Tristan Abild (2003)   "TST Satrup"        / "TST Satrup e.V."
     * Joel Achille (2013)    "GER, TG Münster"   / "TG Münster" / "TG Münster 1862"
     * Edda Ahrens (2013)     "VfL Grasdorf"      / "VFL Grasdorf 1896 e.V."
     * Ella Alberternst (2012) "TV 1843 Dillenburg" / "HE, Hessischer Turnverband"
     * </pre>
     *
     * <p>Dazu kommen echte Vereinswechsel, die eine Turnerin ja nicht zu einer
     * anderen Person machen. Die Website zeigt deshalb <b>alle</b> Vereine
     * einer Person an, neueste zuerst.
     *
     * <p>Der Preis: zwei verschiedene Personen mit gleichem Namen <i>und</i>
     * gleichem Jahrgang landen auf einer Karte. Das ist selten, und weil dann
     * beide Vereine nebeneinander stehen, fällt es auf.
     */
    public String personSchluessel() {
        return Namen.normalisieren(nachname)
                + "|" + Namen.normalisieren(vorname)
                + "|" + (jahrgang == null ? "" : jahrgang);
    }

    /** In welche Datei unter {@code docs/data/} gehört diese Wertung? */
    public String datei() {
        return Namen.datei(nachname);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Wertung andere)) {
            return false;
        }
        return schluessel().equals(andere.schluessel());
    }

    @Override
    public int hashCode() {
        return Objects.hash(schluessel());
    }

    @Override
    public String toString() {
        return vorname + " " + nachname + " (" + jahrgang + ") – "
                + event + ", " + wettkampf + ", " + durchgang
                + ": " + gesamt;
    }
}
