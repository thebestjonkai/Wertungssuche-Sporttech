package de.wertungssuche;

import de.wertungssuche.api.EventDaten;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Das Herzstück: macht aus der verschachtelten Event-Antwort von sporttech.io
 * eine flache Liste von {@link Wertung}en.
 *
 * <p>Der Weg durch die Daten:
 * <pre>
 * für jeden Stage (Durchgang)
 *   → Competition dazu holen, Disziplin prüfen
 *   → für jede Group (Riege)
 *       → für jede Performance (Starter)
 *           → für die ersten PerfomanceFramesLimit Frames (Übungen)
 *               → nur wenn State == 3 (veröffentlicht)
 *                   → für jeden Athleten der Performance eine Zeile
 * </pre>
 *
 * <p>Wir gehen über {@code Stages} und nicht über {@code Frames}, weil wir die
 * Übungsnummer innerhalb des Durchgangs brauchen ("Qualifikation 1" / "2") und
 * weil nur der Stage weiß, wie viele Übungen überhaupt zählen.
 */
public class WertungsExtraktor {

    /**
     * Welche Disziplinen übernommen werden.
     *
     * <p>Laut Vorgabe nur Trampolin Einzel. Synchron, Doppel-Minitrampolin und
     * Tumbling werden übersprungen, obwohl sie in denselben Events stehen.
     * Die Umrechnung für Synchron ist in {@link #eNote} und {@link #flugzeit}
     * trotzdem schon korrekt umgesetzt – wenn Synchron später dazukommen soll,
     * reicht es, hier {@link Disziplin#SYNCHRON} aufzunehmen.
     */
    private static final Set<Disziplin> ERLAUBTE_DISZIPLINEN = Set.of(Disziplin.TRAMPOLIN);

    private final Laenderliste laender;
    private final Konfiguration konfig;

    /** Zähler fürs Protokoll. */
    private int uebersprungeneDisziplin = 0;
    private int uebersprungeneNichtVeroeffentlicht = 0;
    private int uebersprungeneFilter = 0;

    public WertungsExtraktor(Laenderliste laender, Konfiguration konfig) {
        this.laender = laender;
        this.konfig = konfig;
    }

    /**
     * Zieht alle Wertungen aus einem Event.
     *
     * @param daten die geparste API-Antwort
     * @return die gefundenen Wertungen, nie {@code null}
     */
    public List<Wertung> extrahieren(EventDaten daten) {
        List<Wertung> ergebnis = new ArrayList<>();

        if (daten == null || daten.Event == null || daten.Stages == null) {
            return ergebnis;
        }
        // Events, die ihre Ergebnisse ausdrücklich nicht öffentlich zeigen,
        // lassen wir aus.
        if (Boolean.TRUE.equals(daten.Event.HideOnlineResults)) {
            System.out.println("  Event versteckt seine Ergebnisse, wird übersprungen.");
            return ergebnis;
        }

        String eventId = daten.Event.UUID;
        String eventName = Namen.aufraeumen(daten.Event.Title);
        String eventDatum = datumAusZeitstempel(daten.Event.StartDate);
        // Hängt am Reglement des Events, gilt also für alle Durchgänge gleich.
        int eFaktor = Reglement.eFaktor(daten.Event.RulesCode);

        for (EventDaten.Stage stage : werte(daten.Stages)) {
            if (stage == null || stage.CompetitionID == null) {
                continue;
            }
            EventDaten.Competition wettkampf = hole(daten.Competitions, stage.CompetitionID);
            if (wettkampf == null) {
                continue;
            }

            Disziplin disziplin = Disziplin.vonNummer(wettkampf.Discipline);
            if (disziplin == null || !ERLAUBTE_DISZIPLINEN.contains(disziplin)) {
                uebersprungeneDisziplin++;
                continue;
            }

            // Wie viele Übungen zählen in diesem Durchgang?
            int anzahlUebungen = Zahlen.oderNull(stage.PerfomanceFramesLimit);
            if (anzahlUebungen <= 0) {
                continue;
            }

            String link = "https://sporttech.io/events/" + eventId
                    + "/ovs/stages/" + stage.ID + "/results";

            for (Integer groupId : liste(stage.Groups)) {
                EventDaten.Group riege = hole(daten.Groups, groupId);
                if (riege == null) {
                    continue;
                }
                for (Integer performanceId : liste(riege.Performances)) {
                    EventDaten.Performance starter = hole(daten.Performances, performanceId);
                    if (starter == null) {
                        continue;
                    }
                    ergebnis.addAll(zeilenFuerStarter(
                            daten, starter, stage, wettkampf, disziplin,
                            anzahlUebungen, eventId, eventName, eventDatum, link,
                            eFaktor));
                }
            }
        }
        return ergebnis;
    }

    /** Alle Zeilen, die ein einzelner Starter in einem Durchgang erzeugt. */
    private List<Wertung> zeilenFuerStarter(
            EventDaten daten,
            EventDaten.Performance starter,
            EventDaten.Stage stage,
            EventDaten.Competition wettkampf,
            Disziplin disziplin,
            int anzahlUebungen,
            String eventId,
            String eventName,
            String eventDatum,
            String link,
            int eFaktor) {

        List<Wertung> zeilen = new ArrayList<>();
        List<Integer> frameIds = liste(starter.Frames);

        // Nur die ersten anzahlUebungen Frames zählen – die weiteren sind
        // Platzhalter, die sporttech.io immer anlegt.
        int bisWohin = Math.min(anzahlUebungen, frameIds.size());

        for (int index = 0; index < bisWohin; index++) {
            EventDaten.Frame uebung = hole(daten.Frames, frameIds.get(index));
            if (!Frames.istVeroeffentlicht(uebung)) {
                uebersprungeneNichtVeroeffentlicht++;
                continue;
            }

            // Eine Synchron-Performance hat zwei Athleten; beide bekommen eine
            // eigene Zeile mit derselben Wertung.
            for (Integer athletId : liste(starter.Athletes)) {
                EventDaten.Athlete person = hole(daten.Athletes, athletId);
                if (person == null) {
                    continue;
                }
                Wertung w = baueWertung(
                        person, starter, stage, wettkampf, uebung, disziplin,
                        index + 1, anzahlUebungen,
                        eventId, eventName, eventDatum, link, eFaktor);
                if (w == null) {
                    continue;
                }
                if (!passtZumFilter(w)) {
                    uebersprungeneFilter++;
                    continue;
                }
                zeilen.add(w);
            }
        }
        return zeilen;
    }

    /** Baut eine einzelne Zeile. Gibt {@code null} zurück, wenn kein Name da ist. */
    private Wertung baueWertung(
            EventDaten.Athlete person,
            EventDaten.Performance starter,
            EventDaten.Stage stage,
            EventDaten.Competition wettkampf,
            EventDaten.Frame uebung,
            Disziplin disziplin,
            int uebungNr,
            int anzahlUebungen,
            String eventId,
            String eventName,
            String eventDatum,
            String link,
            int eFaktor) {

        Wertung w = new Wertung();

        w.vorname = Namen.aufraeumen(person.GivenName);
        w.nachname = Namen.aufraeumen(person.Surname);
        if (w.nachname == null && w.vorname == null) {
            // Ohne Namen ist die Zeile für eine Namenssuche wertlos.
            return null;
        }
        w.jahrgang = jahrgangAus(person.DateOfBirth);

        // Verein oder Nation? Siehe Laenderliste.
        String representing = Namen.aufraeumen(person.Representing);
        if (representing != null && laender.istNation(representing)) {
            w.nation = representing.toUpperCase(Locale.ROOT);
            w.verein = null;
        } else {
            w.verein = representing;
            w.nation = null;
        }

        w.event = eventName;
        w.eventId = eventId;
        // Pro Übung das Veröffentlichungsdatum, sonst der Eventbeginn.
        String uebungsDatum = datumAusZeitstempel(uebung.LastPublishTime_G);
        w.datum = (uebungsDatum != null) ? uebungsDatum : eventDatum;

        w.disziplin = disziplin.anzeigename();
        w.wettkampf = Namen.aufraeumen(wettkampf.Title);
        w.durchgang = Durchgang.name(stage.Kind, uebungNr, anzahlUebungen);

        // Gaststarter laufen ohne Platzierung mit; 0 und negative Werte sind
        // Platzhalter und keine echten Plätze.
        w.platz = platz(starter);

        boolean synchron = disziplin == Disziplin.SYNCHRON;
        // Bei der D-Note ist −1 der Marker "nicht gesetzt", eine echte 0 gibt
        // es aber auch (Anfängerübungen) – siehe Zahlen.teilenNullWennNegativ.
        w.d = Zahlen.teilenNullWennNegativ(uebung.DifficultyT_G, 10);
        w.e = eNote(uebung, stage, synchron, eFaktor);
        w.t = flugzeit(uebung, synchron);
        w.h = Zahlen.teilenNullWennNull(uebung.Displacement_G, 100);
        w.abzug = abzug(uebung);
        w.gesamt = Zahlen.teilen(uebung.MarkTTT_G, 1000);
        w.gesamtDurchgang = Zahlen.teilen(starter.MarkTTT_G, 1000);
        w.link = link;

        return w;
    }

    /**
     * Der Platz im Durchgang, oder {@code null}.
     *
     * <p>Gaststarter laufen mit {@code Ranked == false} ohne Platzierung mit.
     * Außerdem sind 0 und negative Werte bei sporttech.io Platzhalter und keine
     * echten Plätze.
     */
    private static Integer platz(EventDaten.Performance starter) {
        if (Boolean.FALSE.equals(starter.Ranked)) {
            return null;
        }
        if (starter.Rank_G == null || starter.Rank_G <= 0) {
            return null;
        }
        return starter.Rank_G;
    }

    /**
     * Die E-Note – der einzige Wert mit einer nicht ganz offensichtlichen
     * Umrechnung. Die Regel steht ausführlich erklärt in {@link Reglement}.
     *
     * <p>Kurz: beim Synchronspringen gilt {@code ETotalT_G / 1000}. Sonst wird
     * der Wert erst mit {@code eFaktor} multipliziert (1 bei den Reglements
     * 2017 und 2022, sonst 3) und dann durch 10 geteilt – bei Durchgängen mit
     * Durchschnittsrechnung durch 1000.
     */
    private static Double eNote(EventDaten.Frame uebung, EventDaten.Stage stage,
                                boolean synchron, int eFaktor) {
        if (uebung.ETotalT_G == null || uebung.ETotalT_G < 0) {
            return null;
        }
        if (synchron) {
            return Zahlen.teilen(uebung.ETotalT_G, 1000);
        }
        return Zahlen.teilen(uebung.ETotalT_G * eFaktor, Reglement.eTeiler(stage));
    }

    /**
     * Die Flugzeit. Beim Synchronspringen gilt {@code 2 × Wert / 100} statt
     * {@code Wert / 1000}. Bei Doppel-Minitrampolin und Tumbling gibt es keine
     * Flugzeit, dort liefert die API stur 0 – daraus wird {@code null}.
     */
    private static Double flugzeit(EventDaten.Frame uebung, boolean synchron) {
        if (uebung.TimeTMS_G == null || uebung.TimeTMS_G == 0) {
            return null;
        }
        if (synchron) {
            return Zahlen.runden(2.0 * uebung.TimeTMS_G / 100.0, 2);
        }
        return Zahlen.teilen(uebung.TimeTMS_G, 1000);
    }

    /**
     * Die Abzüge – Summe aus {@code PenaltyT} und {@code DPenaltyT}.
     *
     * <p>{@code DPenaltyT} ist der zusätzliche Abzug und wird gern vergessen;
     * sporttechs eigene Beispiel-Erweiterung wertet ihn nicht aus. Ohne ihn
     * stimmt die Punkteformel nicht: Frame 32 des CBI 2024 hat D 0,0, E 15,8
     * und ein Gesamtergebnis von 3,80 – die Differenz sind genau die 12,0
     * Punkte aus {@code DPenaltyT}.
     */
    private static Double abzug(EventDaten.Frame uebung) {
        int summe = Zahlen.oderNullNichtNegativ(uebung.PenaltyT)
                + Zahlen.oderNullNichtNegativ(uebung.DPenaltyT);
        return Zahlen.teilen(summe, 10);
    }

    /** Macht aus {@code 2006-01-01T00:00:00Z} das Jahr {@code 2006}. */
    static Integer jahrgangAus(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.length() < 4) {
            return null;
        }
        try {
            int jahr = Integer.parseInt(dateOfBirth.substring(0, 4));
            // Platzhalterjahre wie 0001 aussortieren.
            if (jahr < 1900 || jahr > 2100) {
                return null;
            }
            return jahr;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Macht aus {@code 2024-06-29T17:29:49.08-03:00} das Datum
     * {@code 2024-06-29}. Der Platzhalter {@code 0001-01-01T00:00:00Z} wird zu
     * {@code null}.
     */
    static String datumAusZeitstempel(String zeitstempel) {
        if (zeitstempel == null || zeitstempel.length() < 10) {
            return null;
        }
        String datum = zeitstempel.substring(0, 10);
        if (datum.startsWith("0001") || datum.compareTo("1900-01-01") < 0) {
            return null;
        }
        return datum;
    }

    /** Prüft die optionalen Filter nach Nation und Verein aus der config.json. */
    private boolean passtZumFilter(Wertung w) {
        if (!konfig.nationFilter.isEmpty()) {
            if (w.nation == null) {
                return false;
            }
            boolean treffer = konfig.nationFilter.stream()
                    .anyMatch(n -> n.equalsIgnoreCase(w.nation));
            if (!treffer) {
                return false;
            }
        }
        if (!konfig.vereinFilter.isEmpty()) {
            if (w.verein == null) {
                return false;
            }
            String vereinNorm = Namen.normalisieren(w.verein);
            boolean treffer = konfig.vereinFilter.stream()
                    .anyMatch(v -> vereinNorm.contains(Namen.normalisieren(v)));
            if (!treffer) {
                return false;
            }
        }
        return true;
    }

    // --- kleine Helfer, damit oben keine null-Prüfungen den Blick verstellen ---

    private static <T> T hole(Map<String, T> map, Integer id) {
        if (map == null || id == null) {
            return null;
        }
        return map.get(String.valueOf(id));
    }

    private static <T> Iterable<T> werte(Map<String, T> map) {
        return map == null ? List.of() : map.values();
    }

    private static List<Integer> liste(List<Integer> maybe) {
        return maybe == null ? Collections.emptyList() : maybe;
    }

    public int getUebersprungeneDisziplin() {
        return uebersprungeneDisziplin;
    }

    public int getUebersprungeneNichtVeroeffentlicht() {
        return uebersprungeneNichtVeroeffentlicht;
    }

    public int getUebersprungeneFilter() {
        return uebersprungeneFilter;
    }
}
