package de.wertungssuche.api;

import java.util.List;
import java.util.Map;

/**
 * Die Antwort von
 * {@code GET /events/<id>/ovs/api/event?fetch_event_competitions=true&...}.
 *
 * <p>Die Hierarchie steht nicht verschachtelt im JSON, sondern als fünf flache
 * Maps, die über ID-Listen verknüpft sind:
 *
 * <pre>
 * Event
 *  └─ Competitions[]          Wettkampfklasse ("AK 13/14 (weiblich)")
 *      └─ Stages[]            Durchgangsart (Qualifikation / Finale / ...)
 *          └─ Groups[]        Riegen – nur eine Einteilung
 *              └─ Performances[]   ein Starter in diesem Durchgang
 *                  ├─ Athletes[]   1 Athlet, bei Synchron 2
 *                  └─ Frames[]     die einzelnen Übungen mit den Wertungen
 * </pre>
 *
 * <p><b>Wichtig:</b> die Schlüssel der Maps sind die IDs als <i>Text</i>, also
 * {@code "0"}, {@code "1"} und so weiter. Darum sind alle Maps
 * {@code Map<String, ...>}.
 *
 * <p><b>Ebenfalls wichtig:</b> alle Zahlenfelder sind {@code Integer} und nicht
 * {@code int}. Die API lässt Felder in einzelnen Objekten weg – im Deutschland
 * Cup 2025 fehlt zum Beispiel bei zwei von 1372 Übungen das Feld
 * {@code DPenaltyT}. Mit {@code int} würde dort stillschweigend eine 0 stehen,
 * mit {@code Integer} steht {@code null}, und {@link de.wertungssuche.Zahlen}
 * entscheidet bewusst, was daraus wird.
 */
public class EventDaten {

    public Event Event;
    public Map<String, Competition> Competitions;
    public Map<String, Stage> Stages;
    public Map<String, Group> Groups;
    public Map<String, Performance> Performances;
    public Map<String, Frame> Frames;
    public Map<String, Athlete> Athletes;

    /** Das Event selbst. */
    public static class Event {
        public String UUID;
        /** Eventname, z. B. {@code Deutschland Cup 2025}. */
        public String Title;
        /** Untertitel, meist Ort und Zeitraum als Freitext. */
        public String Subtitle;
        /** {@code 2024-06-26T00:00:00Z} */
        public String StartDate;
        public String EndDate;
        /**
         * Reglement, z. B. {@code TRA.FIG.2022.2024-06-26}.
         * Der dritte Teil entscheidet über die Umrechnung der E-Note –
         * siehe {@link de.wertungssuche.Reglement}.
         */
        public String RulesCode;
        /** Steht auf {@code false}, wenn in {@code DateOfBirth} nur das Jahr echt ist. */
        public Boolean UseFullDOB;
        /** Wenn {@code true}, sind die Ergebnisse nicht öffentlich. */
        public Boolean HideOnlineResults;
        public List<Integer> Competitions;
    }

    /** Ein Wettkampf, z. B. "AK 13/14 (weiblich)". */
    public static class Competition {
        public Integer ID;
        public String Title;
        /**
         * Disziplin innerhalb von Trampolin:
         * 0 = TRA (Einzel), 1 = SYN (Synchron), 2 = DMT, 3 = TUM, 4 = TEAM.
         * Siehe {@link de.wertungssuche.Disziplin}.
         */
        public Integer Discipline;
        public String AgeGroup;
        public String SexCategory;
        public List<Integer> Stages;
    }

    /** Ein Durchgang, z. B. die Qualifikation. Hat <b>keinen</b> Titel. */
    public static class Stage {
        public Integer ID;
        public Integer CompetitionID;
        /** Art des Durchgangs, siehe {@link de.wertungssuche.Durchgang}. */
        public Integer Kind;
        /**
         * Anzahl der Übungen pro Starter, die in diesem Durchgang zählen.
         * Der Tippfehler "Perfomance" steckt so im Original-JSON.
         */
        public Integer PerfomanceFramesLimit;
        /**
         * Rechenoptionen dieses Durchgangs. Für uns zählen nur drei davon,
         * weil sie den Maßstab der E-Note ändern – siehe
         * {@link de.wertungssuche.Reglement#eTeiler}.
         */
        public List<Integer> CalcOptions;
        public List<Integer> Groups;
    }

    /** Eine Riege innerhalb eines Durchgangs – nur eine Einteilung. */
    public static class Group {
        public Integer ID;
        public Integer StageID;
        public List<Integer> Performances;
    }

    /** Ein Starter (oder ein Synchronpaar) in einem Durchgang. */
    public static class Performance {
        public Integer ID;
        public Integer GroupID;
        /** Ein Athlet, bei Synchron zwei. */
        public List<Integer> Athletes;
        /**
         * Die Übungen. Nur die ersten {@link Stage#PerfomanceFramesLimit}
         * zählen, der Rest sind nie benutzte Platzhalter.
         */
        public List<Integer> Frames;
        /** Ergebnis des gesamten Durchgangs, mal 1000. */
        public Integer MarkTTT_G;
        /** Platz in diesem Durchgang. */
        public Integer Rank_G;
        /** {@code false} bei Gaststartern, die nicht gewertet werden. */
        public Boolean Ranked;
    }

    /** Eine einzelne Übung. Hier stehen die Einzelwertungen. */
    public static class Frame {
        public Integer ID;
        public Integer PerformanceID;
        /** Nur {@code 3} = veröffentlicht zählt. Siehe {@link de.wertungssuche.Frames}. */
        public Integer State;
        /** Bei {@code State == 4}: 1 = DNS, 3 = FSF, 4 = DNF, 5 = DSQ, 6 = DQB. */
        public Integer SubState;

        /** Gesamtergebnis der Übung, mal 1000. */
        public Integer MarkTTT_G;
        /** D-Note (Schwierigkeit), mal 10. */
        public Integer DifficultyT_G;
        /** E-Note (Ausführung), mal 10 – bei Synchron mal 1000. */
        public Integer ETotalT_G;
        /** Flugzeit in Sekunden, mal 1000 – bei Synchron anders, siehe Doku. */
        public Integer TimeTMS_G;
        /** Horizontale Verlagerung (H bzw. HD), mal 100. */
        public Integer Displacement_G;
        /** Abzug, mal 10. */
        public Integer PenaltyT;
        /** Zusätzlicher Abzug, mal 10. Darf nicht vergessen werden! */
        public Integer DPenaltyT;
        /** Bonus, mal 10. */
        public Integer BonusT;

        /** Zeitpunkt der Veröffentlichung, unser Datum für diese Wertung. */
        public String LastPublishTime_G;
    }

    /** Eine Person. Die ID ist nur innerhalb dieses Events eindeutig. */
    public static class Athlete {
        public Integer ID;
        public String GivenName;
        public String Surname;
        /**
         * Geburtsdatum als {@code 2006-01-01T00:00:00Z}. <b>Kann ganz fehlen</b>
         * und ist bei {@code Event.UseFullDOB == false} nur im Jahr echt.
         */
        public String DateOfBirth;
        /**
         * Verein <b>oder</b> Nation – je nach Veranstalter. Beim Deutschland Cup
         * steht hier {@code TG Münster 1862}, beim World Cup {@code GER}.
         * {@link de.wertungssuche.Laenderliste} unterscheidet die beiden Fälle.
         */
        public String Representing;
        public String Representing2;
        public String Sex;
        public String Level;
    }
}
