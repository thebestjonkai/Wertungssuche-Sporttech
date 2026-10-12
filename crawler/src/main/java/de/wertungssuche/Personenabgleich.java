package de.wertungssuche;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Räumt die Personenangaben auf, bevor die Dateien geschrieben werden.
 *
 * <h2>Warum es diese Klasse gibt</h2>
 *
 * <p>Bei sporttech.io trägt <b>jeder Veranstalter die Starterliste selbst
 * ein</b>. Es gibt keine zentrale Athletendatenbank – das ist nachgeprüft:
 * das Feld {@code Athletes[].ID} zählt innerhalb eines Events einfach durch
 * (dieselbe Turnerin hatte 72, 120, 38 und 89), {@code GUID} ist meistens leer
 * und {@code ExternalID} immer 0. Es gibt also <b>keine stabile Kennung</b>,
 * an der man eine Person über Wettkämpfe hinweg wiedererkennen könnte. Bleibt
 * nur der Name – und der kommt in drei Varianten kaputt an:
 *
 * <ol>
 *   <li><b>Vor- und Nachname vertauscht.</b> Der Barmstedt Cup liefert
 *       {@code GivenName: "Schmal", Surname: "Emely"}. Das sind keine
 *       Einzelfälle: 572 Zeilen in zwei Events.</li>
 *   <li><b>Ganzer Name in einem Feld.</b> {@code GivenName: ""},
 *       {@code Surname: "Celina Ainger"}.</li>
 *   <li><b>Jahrgang fehlt.</b> Manche Events schicken gar kein
 *       {@code DateOfBirth} – die WAGC-Qualifikation zum Beispiel.</li>
 * </ol>
 *
 * <p>Ohne Aufräumen hat Emely Schmal drei Karten auf der Website statt einer,
 * Michael Gladjuk ebenfalls. Schlimmer noch: eine vertauschte Zeile landet nach
 * {@link Namen#datei(String)} in {@code e.json} statt in {@code s.json}, der
 * Suchindex zeigt aber auf {@code s} – diese Wertungen wären auf der Website
 * gar nicht auffindbar.
 *
 * <h2>Wie zusammengeführt wird</h2>
 *
 * <p><b>Schritt 1 – Name aus einem Feld aufteilen.</b> Ist ein Feld leer und
 * im anderen steht ein Leerzeichen, wird am letzten Leerzeichen getrennt.
 * "Celina Ainger" wird zu "Celina" + "Ainger", "Anna Maria Schmidt-Weber" zu
 * "Anna Maria" + "Schmidt-Weber".
 *
 * <p><b>Schritt 2 – Reihenfolge ignorieren.</b> Der Gruppenschlüssel ist
 * <em>Vorname und Nachname alphabetisch sortiert</em>. "Emely Schmal" und
 * "Schmal Emely" ergeben beide {@code emely|schmal} und gehören damit
 * zusammen. Nachgemessen am Datenbestand legt das 223 Gruppen zusammen – und
 * bei 197 davon steht auf beiden Seiten derselbe Verein, es ist also
 * nachweislich dieselbe Person. Von den übrigen 26 wurde jede einzelne
 * angesehen; es war keine Fehlzuordnung darunter, die Vereine lauteten nur
 * einmal "TSVE Bielefeld" und einmal "Westfälischer Turnerbund", also Verein
 * gegen Landesturnverband.
 *
 * <p><b>Schritt 3 – Jahrgänge zusammenfassen.</b> Innerhalb einer Namensgruppe
 * wird nach Jahrgang unterteilt, und diese Teile werden wieder vereinigt, wenn
 * sich die <b>Vereinsangaben überschneiden</b>. Michael Gladjuk steht bei
 * sporttech mal mit 2005 und mal mit 2006, beide Male bei "TV Nellingen" –
 * also eine Person. Von 21 solchen Widersprüchen im Datenbestand lösen sich 14
 * über den Verein auf. Die restlichen 7 bleiben getrennt, und das ist
 * Absicht: bei "Emma Leonhardt 2010 Pfungstadt" gegen "Emma Leonhardt 2011
 * Mannheim" lässt sich schlicht nicht entscheiden, ob das ein Vereinswechsel
 * oder zwei Turnerinnen sind. Lieber zwei Karten als zwei
 * zusammengeworfene Personen.
 *
 * <p><b>Schritt 4 – Zeilen ohne Jahrgang anhängen.</b> Ein fehlender Jahrgang
 * darf <b>nie</b> eine neue Person erzeugen; das betrifft 167 Personen. Die
 * Zeilen werden an die eine passende Person angehängt – gibt es nach Schritt 3
 * nur eine, dann an diese, sonst an die mit überschneidendem Verein. Bleibt es
 * mehrdeutig, bleiben sie getrennt.
 *
 * <p><b>Schritt 5 – eine Schreibweise für alle Zeilen.</b> Pro Person
 * gewinnt die Schreibweise, die in den <b>meisten Wettkämpfen</b> vorkommt.
 * Emely Schmal tritt 16 Mal als "Emely Schmal" und 2 Mal als "Schmal Emely"
 * an – die 16 gewinnen. Nebeneffekt: auch "SCHMAL" aus dem Nissen Cup wird
 * dabei zu "Schmal". Bei Gleichstand entscheidet, wie häufig der Name im
 * ganzen Datenbestand als Vorname beziehungsweise als Nachname auftaucht.
 *
 * <h2>Was diese Klasse nicht kann</h2>
 *
 * <p>Kaputte Umlaute bleiben stehen. Einige Events liefern Namen mit dem
 * Unicode-Ersatzzeichen, zum Beispiel {@code F�rster} statt "Förster" –
 * das steht schon so in sporttechs Datenbank, die API gibt die Bytes
 * {@code EF BF BD} heraus. Welcher Umlaut es war, ist daraus nicht
 * wiederherstellbar, und Raten würde Namen erfinden. Siehe
 * {@link #ERSATZZEICHEN}.
 */
public final class Personenabgleich {

    /**
     * Das Unicode-Ersatzzeichen U+FFFD. Taucht in Namen auf, bei denen schon
     * sporttech die Zeichenkodierung verloren hat. Wird nur gezählt, nicht
     * verändert.
     */
    public static final char ERSATZZEICHEN = '�';

    /**
     * Wörter, die in Vereinsnamen nichts über den Verein aussagen:
     * Rechtsformen, Vereinsgattungen, Länderkürzel. Sie fliegen vor dem
     * Vergleich raus, damit "TV Nellingen 1893 e.V." und "Turnverein
     * Nellingen" sich über "nellingen" treffen.
     */
    private static final Set<String> VEREIN_FUELLWOERTER = Set.of(
            "ev", "e", "v", "verein", "sportverein", "turnverein", "turnerschaft",
            "tv", "tsv", "sv", "mtv", "tsg", "vt", "sg", "tus", "vfl", "vfb",
            "tg", "sc", "esv", "post", "spvgg", "stb", "turnen", "turner",
            "bund", "verband", "turnverband", "turnerbund", "ger", "und",
            "der", "die", "das", "von", "am", "im");

    /** Kürzer als das zählt nicht als aussagekräftiges Vereinswort. */
    private static final int VEREIN_MINDESTLAENGE = 4;

    /**
     * Ab diesem Abstand gilt die Rollenstatistik eines Namens als belastbar und
     * schlägt die Mehrheit der Wettkämpfe – siehe
     * {@link Schreibweise#bestimmen}.
     *
     * <p>Die Zahl ist gemessen, nicht geraten. Im Datenbestand widersprechen
     * sich "meiste Wettkämpfe" und "Rollenstatistik" in 55 von 309 Fällen mit
     * mehreren Schreibweisen. Sortiert man diese 55 nach dem Abstand, liegt
     * dazwischen eine saubere Lücke: oberhalb von 10 hatte die Statistik jedes
     * Mal recht, unterhalb kam der Abstand aus ein oder zwei fremden Zeilen und
     * war wertlos.
     */
    private static final int SCHWELLE = 10;

    /** Was der Abgleich geändert hat – nur für die Ausgabe im Protokoll. */
    public record Bericht(
            int namenGeteilt,
            int namenGedreht,
            int schreibweiseAngeglichen,
            int jahrgangErgaenzt,
            int jahrgangKorrigiert,
            int personenVorher,
            int personenNachher,
            int namenMitErsatzzeichen,
            /** Wettkämpfe, bei denen die Namensfelder durchgehend getauscht waren. */
            int vertauschteEvents) {

        /** Hat sich überhaupt etwas geändert? */
        public boolean etwasGeaendert() {
            return namenGeteilt + namenGedreht + schreibweiseAngeglichen
                    + jahrgangErgaenzt + jahrgangKorrigiert > 0;
        }
    }

    private Personenabgleich() {
        // Hilfsklasse
    }

    /**
     * Gleicht die Personenangaben aller Wertungen ab. Die Objekte in der Liste
     * werden <b>an Ort und Stelle geändert</b>.
     *
     * <p>Achtung für den Aufrufer: dabei kann sich {@link Wertung#datei()}
     * ändern, weil der Nachname ein anderer wird. Die Wertungen müssen danach
     * neu auf die Dateien verteilt werden – {@link Datenbestand#namenAbgleichen()}
     * erledigt das.
     *
     * @param alle alle Wertungen des Datenbestands
     * @return was geändert wurde
     */
    public static Bericht abgleichen(List<Wertung> alle) {
        int personenVorher = (int) alle.stream()
                .map(Wertung::personSchluessel).distinct().count();

        int geteilt = namenTeilen(alle);

        // Erst die Wettkämpfe, die komplett verdreht sind. Das muss vor allem
        // anderen laufen: danach ist die Statistik sauberer, und die
        // Einzelfallentscheidung weiter unten hat weniger zu tun.
        Ereignisbefund befund = vertauschteEventsDrehen(alle);

        Haeufigkeiten haeufig = Haeufigkeiten.zaehlen(alle);

        int gedreht = befund.zeilen();
        int schreibweise = 0;
        int jahrgangErgaenzt = 0;
        int jahrgangKorrigiert = 0;

        for (List<Wertung> namensgruppe : nachNamenGruppieren(alle).values()) {
            for (List<Wertung> person : personenTrennen(namensgruppe)) {

                Schreibweise gewinner = Schreibweise.bestimmen(person, haeufig);
                Integer jahrgang = jahrgangBestimmen(person);

                for (Wertung w : person) {
                    boolean warGedreht = Namen.normalisieren(w.vorname)
                            .equals(Namen.normalisieren(gewinner.nachname()))
                            && !Namen.normalisieren(w.vorname)
                                    .equals(Namen.normalisieren(w.nachname));

                    if (!gewinner.passtAuf(w)) {
                        if (warGedreht) {
                            gedreht++;
                        } else {
                            schreibweise++;
                        }
                        w.vorname = gewinner.vorname();
                        w.nachname = gewinner.nachname();
                    }

                    if (!java.util.Objects.equals(w.jahrgang, jahrgang)) {
                        if (w.jahrgang == null) {
                            jahrgangErgaenzt++;
                        } else {
                            jahrgangKorrigiert++;
                        }
                        w.jahrgang = jahrgang;
                    }
                }
            }
        }

        int personenNachher = (int) alle.stream()
                .map(Wertung::personSchluessel).distinct().count();

        return new Bericht(geteilt, gedreht, schreibweise, jahrgangErgaenzt,
                jahrgangKorrigiert, personenVorher, personenNachher,
                mitErsatzzeichen(alle), befund.events());
    }

    // ---------------------------------------------------------------
    // Schritt 2: ganze Wettkämpfe, die verdreht eingetragen wurden
    // ---------------------------------------------------------------

    /** Wie viele Wettkämpfe komplett gedreht wurden und wie viele Zeilen das war. */
    private record Ereignisbefund(int events, int zeilen) { }

    /**
     * Ab diesem Anteil verdrehter Namen gilt ein ganzer Wettkampf als falsch
     * eingetragen.
     *
     * <p>Auch das ist gemessen. Zählt man je Event nur die Zeilen, bei denen
     * die Rollenstatistik eindeutig ist, ergibt sich eine Lücke, die kaum
     * deutlicher sein könnte:
     *
     * <pre>
     * 24. Barmstedt Cup 2025                   386 von 439 verdreht   88 %
     * 25. Barmstedt Cup 2026                   375 von 432 verdreht   87 %
     * Vereinsmeisterschaften TSV Mannheim        2 von  40 verdreht    5 %
     * Wölfe Open 13.09.2025                      6 von 222 verdreht    3 %
     * ... alle weiteren                                          0 bis 2 %
     * </pre>
     *
     * <p>Zwischen 87 % und 5 % liegt nichts. Die paar Prozent bei den übrigen
     * Wettkämpfen sind keine Fehler des Veranstalters, sondern Grenzfälle der
     * Statistik – etwa eine Turnerin, die wirklich "König" mit Vornamen heißt.
     */
    private static final double EVENT_ANTEIL = 0.5;

    /**
     * Wie viele eindeutige Zeilen ein Event mindestens haben muss, damit über
     * den ganzen Wettkampf entschieden wird. Bei einer Handvoll Starter wäre
     * der Anteil Zufall.
     */
    private static final int EVENT_MINDESTZEILEN = 10;

    /**
     * Findet Wettkämpfe, bei denen der Veranstalter Vor- und Nachname
     * durchgehend in die falschen Felder eingetragen hat, und dreht sie
     * komplett.
     *
     * <p>Beim Barmstedt Cup liefert sporttech.io {@code GivenName: "Schmal"}
     * und {@code Surname: "Emely"} – und zwar für fast die ganze Starterliste.
     * Das Event als Ganzes zu erkennen ist deutlich verlässlicher, als jede
     * Person einzeln zu betrachten: wer nur ein- oder zweimal gestartet ist und
     * dabei nur bei diesem Wettkampf, hat für sich allein keine Mehrheit, die
     * man befragen könnte. "Freda Bauböck" zum Beispiel kommt im übrigen
     * Datenbestand überhaupt nicht vor – über den Wettkampf ist der Fall
     * trotzdem klar.
     *
     * <p>Entschieden wird nur anhand der Zeilen, bei denen die Rollenstatistik
     * eindeutig ist. Gedreht werden dann <b>alle</b> Zeilen des Events, auch
     * die unentscheidbaren.
     */
    private static Ereignisbefund vertauschteEventsDrehen(List<Wertung> alle) {
        Haeufigkeiten haeufig = Haeufigkeiten.zaehlen(alle);

        // Je Event zählen: wie viele eindeutige Namen stehen richtig, wie viele
        // verdreht?
        Map<String, int[]> bilanz = new LinkedHashMap<>();
        for (Wertung w : alle) {
            if (leer(w.vorname) || leer(w.nachname)) {
                continue;
            }
            long punkte = haeufig.bewerten(new Schreibweise(w.vorname, w.nachname));
            if (Math.abs(punkte) < SCHWELLE) {
                continue;
            }
            int[] zaehler = bilanz.computeIfAbsent(String.valueOf(w.eventId),
                    k -> new int[2]);
            zaehler[punkte > 0 ? 0 : 1]++;
        }

        Set<String> verdrehteEvents = new HashSet<>();
        for (Map.Entry<String, int[]> e : bilanz.entrySet()) {
            int richtig = e.getValue()[0];
            int verdreht = e.getValue()[1];
            int gesamt = richtig + verdreht;
            if (gesamt >= EVENT_MINDESTZEILEN
                    && (double) verdreht / gesamt > EVENT_ANTEIL) {
                verdrehteEvents.add(e.getKey());
            }
        }
        if (verdrehteEvents.isEmpty()) {
            return new Ereignisbefund(0, 0);
        }

        int zeilen = 0;
        for (Wertung w : alle) {
            if (verdrehteEvents.contains(String.valueOf(w.eventId))
                    && !leer(w.vorname) && !leer(w.nachname)) {
                String merk = w.vorname;
                w.vorname = w.nachname;
                w.nachname = merk;
                zeilen++;
            }
        }
        return new Ereignisbefund(verdrehteEvents.size(), zeilen);
    }

    // ---------------------------------------------------------------
    // Schritt 1: ganzer Name in einem Feld
    // ---------------------------------------------------------------

    /**
     * Teilt Namen auf, bei denen ein Feld leer ist und im anderen der ganze
     * Name steht. Getrennt wird am <b>letzten</b> Leerzeichen, weil deutsche
     * Doppelvornamen häufiger sind als Doppelnachnamen ohne Bindestrich:
     * "Anna Maria Weber" wird zu "Anna Maria" + "Weber".
     *
     * @return Anzahl der geänderten Zeilen
     */
    private static int namenTeilen(List<Wertung> alle) {
        int geaendert = 0;
        for (Wertung w : alle) {
            boolean vornameLeer = leer(w.vorname);
            boolean nachnameLeer = leer(w.nachname);

            // Nur wenn genau eines der beiden Felder gefüllt ist.
            if (vornameLeer == nachnameLeer) {
                continue;
            }
            String ganz = Namen.aufraeumen(vornameLeer ? w.nachname : w.vorname);
            if (ganz == null) {
                continue;
            }
            int schnitt = ganz.lastIndexOf(' ');
            if (schnitt <= 0) {
                // Ein einzelnes Wort lässt sich nicht aufteilen – das ist dann
                // eben nur ein Nachname.
                continue;
            }
            w.vorname = ganz.substring(0, schnitt);
            w.nachname = ganz.substring(schnitt + 1);
            geaendert++;
        }
        return geaendert;
    }

    // ---------------------------------------------------------------
    // Schritt 2: Gruppen bilden, Reihenfolge ignorieren
    // ---------------------------------------------------------------

    /**
     * Gruppiert alle Wertungen nach dem Namen, <b>ohne</b> auf die Reihenfolge
     * von Vor- und Nachname zu achten: die beiden Vergleichsformen werden
     * alphabetisch sortiert und aneinandergehängt.
     */
    private static Map<String, List<Wertung>> nachNamenGruppieren(List<Wertung> alle) {
        Map<String, List<Wertung>> gruppen = new LinkedHashMap<>();
        for (Wertung w : alle) {
            gruppen.computeIfAbsent(namensgruppe(w), k -> new ArrayList<>()).add(w);
        }
        return gruppen;
    }

    /** Der reihenfolgeunabhängige Namensschlüssel einer Wertung. */
    static String namensgruppe(Wertung w) {
        String a = Namen.normalisieren(w.vorname);
        String b = Namen.normalisieren(w.nachname);
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    // ---------------------------------------------------------------
    // Schritt 3 und 4: Jahrgänge zusammenfassen
    // ---------------------------------------------------------------

    /**
     * Zerlegt eine Namensgruppe in die Personen, die wirklich darin stecken.
     *
     * <p>Grundlage ist der Jahrgang. Zwei Jahrgänge werden wieder vereinigt,
     * wenn sich die Vereinsangaben überschneiden – dann ist es derselbe Mensch,
     * nur hat ein Veranstalter sich beim Geburtsjahr vertippt. Zeilen ohne
     * Jahrgang kommen zuletzt dazu.
     */
    private static List<List<Wertung>> personenTrennen(List<Wertung> namensgruppe) {
        // Nach Jahrgang unterteilen; null bekommt einen eigenen Topf.
        Map<Integer, List<Wertung>> nachJahrgang = new LinkedHashMap<>();
        for (Wertung w : namensgruppe) {
            nachJahrgang.computeIfAbsent(w.jahrgang, k -> new ArrayList<>()).add(w);
        }

        List<Wertung> ohneJahrgang = nachJahrgang.remove(null);

        if (nachJahrgang.isEmpty()) {
            // Niemand in dieser Gruppe hat einen Jahrgang – dann ist der Name
            // alles, was wir haben, und das ist eine Person.
            return List.of(namensgruppe);
        }

        // Jahrgänge mit überschneidenden Vereinen zusammenlegen.
        List<List<Wertung>> personen = new ArrayList<>();
        List<Set<String>> vereine = new ArrayList<>();

        for (List<Wertung> topf : nachJahrgang.values()) {
            Set<String> meine = vereinsWoerter(topf);
            int treffer = -1;
            for (int i = 0; i < personen.size() && treffer < 0; i++) {
                if (ueberschneidetSich(vereine.get(i), meine)) {
                    treffer = i;
                }
            }
            if (treffer >= 0) {
                personen.get(treffer).addAll(topf);
                vereine.get(treffer).addAll(meine);
            } else {
                personen.add(new ArrayList<>(topf));
                vereine.add(new HashSet<>(meine));
            }
        }

        // Zeilen ohne Jahrgang anhängen. Ein fehlendes Geburtsjahr darf keine
        // neue Person erzeugen, solange klar ist, wohin die Zeile gehört.
        if (ohneJahrgang != null) {
            if (personen.size() == 1) {
                personen.get(0).addAll(ohneJahrgang);
            } else {
                anhaengenOderTrennen(ohneJahrgang, personen, vereine);
            }
        }
        return personen;
    }

    /**
     * Verteilt die Zeilen ohne Jahrgang auf die bereits gefundenen Personen.
     * Passt der Verein zu genau einer, kommen sie dorthin. Passt er zu keiner
     * oder zu mehreren, bleiben sie eine eigene Person – lieber eine Karte zu
     * viel als eine falsche Zusammenlegung.
     */
    private static void anhaengenOderTrennen(List<Wertung> ohneJahrgang,
            List<List<Wertung>> personen, List<Set<String>> vereine) {

        // Nach Verein unterteilen, damit nicht alle jahrgangslosen Zeilen
        // gemeinsam entscheiden müssen.
        Map<String, List<Wertung>> nachVerein = new LinkedHashMap<>();
        for (Wertung w : ohneJahrgang) {
            String schluessel = String.join(",", vereinsWoerter(List.of(w)));
            nachVerein.computeIfAbsent(schluessel, k -> new ArrayList<>()).add(w);
        }

        for (List<Wertung> teil : nachVerein.values()) {
            Set<String> meine = vereinsWoerter(teil);
            int treffer = -1;
            int anzahlTreffer = 0;
            for (int i = 0; i < personen.size(); i++) {
                if (ueberschneidetSich(vereine.get(i), meine)) {
                    treffer = i;
                    anzahlTreffer++;
                }
            }
            if (anzahlTreffer == 1) {
                personen.get(treffer).addAll(teil);
            } else {
                personen.add(new ArrayList<>(teil));
                vereine.add(new HashSet<>(meine));
            }
        }
    }

    /** Die aussagekräftigen Wörter aller Vereinsangaben dieser Wertungen. */
    private static Set<String> vereinsWoerter(List<Wertung> wertungen) {
        Set<String> woerter = new LinkedHashSet<>();
        for (Wertung w : wertungen) {
            if (leer(w.verein)) {
                continue;
            }
            for (String teil : Namen.normalisieren(w.verein).split("[^a-z0-9]+")) {
                if (teil.length() >= VEREIN_MINDESTLAENGE
                        && !VEREIN_FUELLWOERTER.contains(teil)
                        && !teil.chars().allMatch(Character::isDigit)) {
                    woerter.add(teil);
                }
            }
        }
        return woerter;
    }

    /**
     * Haben die beiden Vereinsangaben ein Wort gemeinsam? Zwei leere Mengen
     * gelten <b>nicht</b> als Treffer – ohne Vereinsangabe wissen wir nichts.
     */
    private static boolean ueberschneidetSich(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        for (String wort : b) {
            if (a.contains(wort)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Der Jahrgang, der für alle Zeilen dieser Person gilt: der, der in den
     * meisten Wettkämpfen steht. Bei Gleichstand der kleinere, damit das
     * Ergebnis bei jedem Lauf dasselbe ist.
     */
    private static Integer jahrgangBestimmen(List<Wertung> person) {
        // Je Jahrgang die Events zählen, nicht die Zeilen: ein Wettkampf mit
        // sechs Übungen soll nicht sechsfach wiegen.
        Map<Integer, Set<String>> events = new HashMap<>();
        for (Wertung w : person) {
            if (w.jahrgang != null) {
                events.computeIfAbsent(w.jahrgang, k -> new HashSet<>())
                      .add(String.valueOf(w.eventId));
            }
        }
        return events.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Integer, Set<String>> e) -> e.getValue().size())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------
    // Schritt 5: eine Schreibweise für die ganze Person
    // ---------------------------------------------------------------

    /** Eine konkrete Schreibweise eines Namens. */
    private record Schreibweise(String vorname, String nachname) {

        /** Steht diese Schreibweise schon genau so in der Wertung? */
        boolean passtAuf(Wertung w) {
            return java.util.Objects.equals(w.vorname, vorname)
                    && java.util.Objects.equals(w.nachname, nachname);
        }

        /**
         * Die Schreibweise, die für diese Person gelten soll.
         *
         * <p>Es gibt zwei Anhaltspunkte, und <b>beide</b> werden gebraucht:
         *
         * <ol>
         *   <li><b>Die Mehrheit der Wettkämpfe.</b> Naheliegend, reicht aber
         *       nicht: Alina Warmuth steht zweimal beim Barmstedt Cup (also
         *       vertauscht) und nur einmal richtig. Die Mehrheit würde hier den
         *       Fehler zur Wahrheit machen.</li>
         *   <li><b>Die Rolle des Namens im übrigen Datenbestand.</b> "Lina"
         *       steht bei anderen Turnerinnen 198 Mal als Vorname und
         *       praktisch nie als Nachname – dann ist "Lina Schmidt" richtig
         *       und "Schmidt Lina" falsch, ganz egal wie die Wettkämpfe
         *       stehen. Die eigenen Zeilen zählen dabei nicht mit, sonst würde
         *       sich der Fehler selbst bestätigen.</li>
         * </ol>
         *
         * <p>Welcher Anhaltspunkt entscheidet, hängt davon ab, wie deutlich
         * die Statistik ist. Nachgemessen am Datenbestand widersprechen sich
         * die beiden in 55 von 309 Fällen, und die Trennung ist erfreulich
         * scharf: ab einem Abstand von etwa 10 lag die Statistik in jedem
         * nachgeprüften Fall richtig ("Mia Seidel" 126, "Marie Wilhelms" 114,
         * "Amelie Masch" 120). Darunter ist sie Zufallsrauschen aus ein oder
         * zwei fremden Zeilen, und dann gewinnt die Mehrheit der Wettkämpfe –
         * sonst würde aus "Matti König" mit 14 Wettkämpfen wegen eines
         * Abstands von 3 plötzlich "König Matti".
         *
         * @see Personenabgleich#SCHWELLE
         */
        static Schreibweise bestimmen(List<Wertung> person, Haeufigkeiten haeufig) {
            Map<Schreibweise, Set<String>> events = new LinkedHashMap<>();
            for (Wertung w : person) {
                events.computeIfAbsent(new Schreibweise(w.vorname, w.nachname),
                                k -> new HashSet<>())
                      .add(String.valueOf(w.eventId));
            }
            if (events.size() == 1) {
                return events.keySet().iterator().next();
            }

            // Die eigenen Zeilen aus der Statistik herausrechnen.
            Haeufigkeiten ohneMich = haeufig.ohne(person);

            // Ist die Statistik deutlich, bekommt sie den Vortritt.
            long bester = events.keySet().stream()
                    .mapToLong(ohneMich::bewerten)
                    .map(Math::abs)
                    .max()
                    .orElse(0);
            boolean statistikEntscheidet = bester >= SCHWELLE;

            Comparator<Map.Entry<Schreibweise, Set<String>>> nachEvents =
                    Comparator.comparingInt(
                            (Map.Entry<Schreibweise, Set<String>> e) ->
                                    e.getValue().size())
                            .reversed();
            Comparator<Map.Entry<Schreibweise, Set<String>>> nachStatistik =
                    Comparator.comparingLong(
                            (Map.Entry<Schreibweise, Set<String>> e) ->
                                    ohneMich.bewerten(e.getKey()))
                            .reversed();

            Comparator<Map.Entry<Schreibweise, Set<String>>> reihenfolge =
                    statistikEntscheidet
                            ? nachStatistik.thenComparing(nachEvents)
                            : nachEvents.thenComparing(nachStatistik);

            return events.entrySet().stream()
                    // Zuletzt alphabetisch, damit jeder Lauf dasselbe ergibt.
                    .sorted(reihenfolge.thenComparing(e ->
                            e.getKey().vorname() + "|" + e.getKey().nachname()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseGet(() -> new Schreibweise(null, null));
        }
    }

    /**
     * Zählt im ganzen Datenbestand, wie oft ein Name als Vorname und wie oft er
     * als Nachname auftaucht. Damit entscheidet
     * {@link Schreibweise#bestimmen}, ob Vor- und Nachname vertauscht sind.
     */
    private static final class Haeufigkeiten {
        private final Map<String, Integer> alsVorname;
        private final Map<String, Integer> alsNachname;

        private Haeufigkeiten(Map<String, Integer> alsVorname,
                              Map<String, Integer> alsNachname) {
            this.alsVorname = alsVorname;
            this.alsNachname = alsNachname;
        }

        static Haeufigkeiten zaehlen(List<Wertung> alle) {
            Haeufigkeiten h = new Haeufigkeiten(new HashMap<>(), new HashMap<>());
            for (Wertung w : alle) {
                h.alsVorname.merge(Namen.normalisieren(w.vorname), 1, Integer::sum);
                h.alsNachname.merge(Namen.normalisieren(w.nachname), 1, Integer::sum);
            }
            return h;
        }

        /**
         * Dieselbe Statistik, aber ohne die Zeilen dieser einen Person.
         *
         * <p>Das ist der Kern der Sache: wenn die Frage lautet "heißt sie Alina
         * Warmuth oder Warmuth Alina?", dürfen ihre eigenen – möglicherweise
         * vertauschten – Zeilen nicht mitstimmen. Sonst bestätigt der Fehler
         * sich selbst. Gefragt wird also: wie halten es die <i>anderen</i>
         * 17 000 Zeilen mit dem Namen "Alina"?
         */
        Haeufigkeiten ohne(List<Wertung> person) {
            Map<String, Integer> v = new HashMap<>(alsVorname);
            Map<String, Integer> n = new HashMap<>(alsNachname);
            for (Wertung w : person) {
                v.merge(Namen.normalisieren(w.vorname), -1, Integer::sum);
                n.merge(Namen.normalisieren(w.nachname), -1, Integer::sum);
            }
            return new Haeufigkeiten(v, n);
        }

        /**
         * Wie gut passt diese Schreibweise zum Rest des Datenbestands?
         *
         * <p>Gezählt wird, wie oft der Vorname woanders Vorname und der
         * Nachname woanders Nachname ist – minus dasselbe für die gedrehte
         * Variante. Positiv heißt "passt", negativ heißt "steht wohl verkehrt".
         * Der Betrag sagt, wie belastbar die Aussage ist; ab
         * {@link Personenabgleich#SCHWELLE} wird sie als sicher behandelt.
         */
        long bewerten(Schreibweise s) {
            String v = Namen.normalisieren(s.vorname());
            String n = Namen.normalisieren(s.nachname());
            return (long) alsVorname.getOrDefault(v, 0) + alsNachname.getOrDefault(n, 0)
                    - alsNachname.getOrDefault(v, 0) - alsVorname.getOrDefault(n, 0);
        }
    }

    // ---------------------------------------------------------------
    // Kleinkram
    // ---------------------------------------------------------------

    /**
     * Zählt die Personen, deren Name das Unicode-Ersatzzeichen enthält. Diese
     * Namen kommen schon kaputt von sporttech.io und werden nicht verändert –
     * der Crawler weist im Protokoll nur darauf hin.
     */
    private static int mitErsatzzeichen(List<Wertung> alle) {
        Set<String> betroffen = new HashSet<>();
        for (Wertung w : alle) {
            String ganz = (w.vorname == null ? "" : w.vorname)
                    + " " + (w.nachname == null ? "" : w.nachname);
            if (ganz.indexOf(ERSATZZEICHEN) >= 0) {
                betroffen.add(ganz.toLowerCase(Locale.GERMAN));
            }
        }
        return betroffen.size();
    }

    private static boolean leer(String s) {
        return s == null || s.isBlank();
    }
}
