package de.wertungssuche;

import de.wertungssuche.api.EventDaten;

/**
 * Die Zustände einer Übung ({@code Frame.State} und {@code Frame.SubState}).
 *
 * <p>Quelle: {@code model/constants/frameStates.js} im offiziellen
 * Beispielprojekt {@code sporttech/sporttechio-ovs-api-ext}.
 *
 * <p>Von den 900 Übungen des CBI 2024 haben nur 342 den Zustand
 * {@link #VEROEFFENTLICHT}. Der Rest sind größtenteils nie benutzte
 * Platzhalter: jede Performance hat vier Frame-Slots, auch wenn nur zwei
 * Übungen geturnt werden.
 */
public final class Frames {

    /** Noch nicht gestartet – meist ein leerer Platzhalter. */
    public static final int NICHT_GESTARTET = 0;
    /** Läuft gerade. */
    public static final int GESTARTET = 1;
    /** Geturnt, aber noch nicht freigegeben. */
    public static final int BEENDET = 2;
    /** Freigegeben – <b>nur diese Übungen übernehmen wir</b>. */
    public static final int VEROEFFENTLICHT = 3;
    /** Nicht zustande gekommen; Grund steht im {@code SubState}. */
    public static final int GESCHEITERT = 4;
    /** Wird gerade nachkorrigiert. */
    public static final int IN_BEARBEITUNG = 5;

    // Gründe bei GESCHEITERT
    public static final int SUB_DNS = 1;
    public static final int SUB_BLOCKED = 2;
    public static final int SUB_FSF = 3;
    public static final int SUB_DNF = 4;
    public static final int SUB_DSQ = 5;
    public static final int SUB_DQB = 6;
    public static final int SUB_DNC = 7;

    private Frames() {
        // Hilfsklasse
    }

    /** Hat diese Übung eine gültige, veröffentlichte Wertung? */
    public static boolean istVeroeffentlicht(EventDaten.Frame frame) {
        return frame != null
                && frame.State != null
                && frame.State == VEROEFFENTLICHT;
    }

    /**
     * Das Kürzel für eine gescheiterte Übung, sonst {@code null}.
     *
     * <p>Wird derzeit nicht in die Daten geschrieben, weil wir nur
     * veröffentlichte Übungen übernehmen. Steht hier, damit die Bedeutung der
     * Nummern dokumentiert ist und man die Fälle später leicht aufnehmen kann.
     */
    public static String kuerzel(EventDaten.Frame frame) {
        if (frame == null || frame.State == null || frame.State != GESCHEITERT) {
            return null;
        }
        if (frame.SubState == null) {
            return null;
        }
        return switch (frame.SubState) {
            case SUB_DNS -> "DNS";
            case SUB_FSF -> "FSF";
            case SUB_DNF -> "DNF";
            case SUB_DSQ -> "DSQ";
            case SUB_DQB -> "DQB";
            case SUB_DNC -> "DNC";
            case SUB_BLOCKED -> "BLOCKED";
            default -> null;
        };
    }
}
