package de.wertungssuche;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Baut {@code docs/data/personen.json} – den Suchindex der Website.
 *
 * <h2>Warum es diese Datei gibt</h2>
 *
 * <p>Die Wertungen sind nach dem ersten Buchstaben des <b>Nachnamens</b>
 * aufgeteilt. Für eine Suche nach „Müller" reicht das: die Website lädt
 * {@code m.json} und ist fertig. Für eine Suche nach „Jonas" reicht es
 * <b>nicht</b> – der Vorname sagt nichts darüber, in welcher Datei die Person
 * steht. Die Website müsste alle 25 Dateien laden, also 9 MB Wertungen.
 *
 * <p>Deshalb dieser Index: ein Eintrag pro Person mit Namen, Jahrgang,
 * Vereinen und einem Verweis auf die Datei mit den Einzelwertungen. Die
 * Website lädt ihn einmal beim Start (rund 90 KB komprimiert), sucht darin im
 * Browser und holt danach nur die Datei oder Dateien, in denen die Treffer
 * wirklich stehen.
 *
 * <p>Was eine Person ist, legt {@link Wertung#personSchluessel()} fest:
 * Nachname, Vorname und Jahrgang – <b>ohne</b> Verein. Dort steht auch, warum
 * der Verein nicht dazugehört. Weil eine Person dadurch mehrere
 * Vereinsangaben haben kann, sind {@link Person#vereine} und
 * {@link Person#nationen} Listen, sortiert nach dem letzten Auftritt.
 */
public class Personenindex {

    /** Ein Eintrag im Suchindex. Die Feldnamen landen genauso in der JSON-Datei. */
    public static class Person {
        public String vorname;
        public String nachname;
        public Integer jahrgang;
        /**
         * Alle Vereine, für die diese Person angetreten ist – neueste zuerst.
         * Mehrere Einträge entstehen durch Vereinswechsel und durch
         * unterschiedliche Schreibweisen desselben Vereins; siehe
         * {@link Wertung#personSchluessel()}.
         */
        public List<String> vereine = new ArrayList<>();
        /** Alle Nationen, unter denen diese Person angetreten ist. */
        public List<String> nationen = new ArrayList<>();
        /** Name der Datei mit den Einzelwertungen, ohne Endung – z. B. {@code m}. */
        public String datei;
        /** Anzahl der Wertungen, also der einzelnen Übungen. */
        public int anzahl;
        /**
         * Anzahl der verschiedenen Wettkämpfe. Die Website zeigt das in der
         * Trefferliste an, noch bevor die Wertungsdatei geladen ist.
         */
        public int wettkaempfe;
        /** Bester Gesamtwert einer einzelnen Übung. */
        public Double bestGesamt;
        /** Datum des jüngsten Wettkampfs, Format JJJJ-MM-TT. */
        public String letzterWettkampf;

        /** Verein beziehungsweise Nation -> Datum des letzten Auftritts. */
        private final transient Map<String, String> vereinZuletzt = new LinkedHashMap<>();
        private final transient Map<String, String> nationZuletzt = new LinkedHashMap<>();
        /** Die Event-IDs, an denen die Person teilgenommen hat – zum Zählen. */
        private final transient Set<String> eventIds = new LinkedHashSet<>();
    }

    private Personenindex() {
        // Hilfsklasse
    }

    /**
     * Fasst alle Wertungen zu Personen zusammen und schreibt den Index.
     *
     * @param alleWertungen alle Wertungen des Datenbestands
     * @param datei         Zielpfad, üblicherweise {@code docs/data/personen.json}
     * @return die Anzahl der Personen
     */
    public static int schreiben(List<Wertung> alleWertungen, Path datei)
            throws IOException {

        // LinkedHashMap, damit die Reihenfolge erst mal stabil bleibt;
        // sortiert wird danach ausdrücklich.
        Map<String, Person> personen = new LinkedHashMap<>();

        for (Wertung w : alleWertungen) {
            Person p = personen.computeIfAbsent(w.personSchluessel(), k -> {
                Person neu = new Person();
                neu.vorname = w.vorname;
                neu.nachname = w.nachname;
                neu.jahrgang = w.jahrgang;
                neu.datei = w.datei();
                return neu;
            });

            p.anzahl++;
            if (w.eventId != null) {
                p.eventIds.add(w.eventId);
            }

            // Je Verein merken, wann er zuletzt vorkam – danach wird sortiert.
            merken(p.vereinZuletzt, w.verein, w.datum);
            merken(p.nationZuletzt, w.nation, w.datum);

            if (w.gesamt != null && (p.bestGesamt == null || w.gesamt > p.bestGesamt)) {
                p.bestGesamt = w.gesamt;
            }
            if (w.datum != null
                    && (p.letzterWettkampf == null
                        || w.datum.compareTo(p.letzterWettkampf) > 0)) {
                p.letzterWettkampf = w.datum;
            }
        }

        // Vereine und Nationen nach dem letzten Auftritt sortieren,
        // neueste zuerst.
        for (Person p : personen.values()) {
            p.vereine = nachDatumSortiert(p.vereinZuletzt);
            p.nationen = nachDatumSortiert(p.nationZuletzt);
            p.wettkaempfe = p.eventIds.size();
        }

        // Nach Nachname, Vorname sortieren – dieselbe Reihenfolge wie in den
        // Wertungsdateien, damit die Git-Diffs klein bleiben.
        List<Person> liste = new ArrayList<>(personen.values());
        liste.sort(Comparator
                .comparing((Person p) -> Namen.normalisieren(p.nachname))
                .thenComparing(p -> Namen.normalisieren(p.vorname))
                .thenComparing(p -> p.jahrgang == null ? 0 : p.jahrgang));

        var gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .serializeNulls()
                .create();

        Files.createDirectories(datei.getParent());
        Files.writeString(datei, gson.toJson(liste) + "\n", StandardCharsets.UTF_8);

        return liste.size();
    }

    /**
     * Merkt sich zu einem Verein oder einer Nation das späteste Datum, an dem
     * die Person dafür angetreten ist.
     */
    private static void merken(Map<String, String> zuletzt, String wert, String datum) {
        if (wert == null || wert.isBlank()) {
            return;
        }
        String bisher = zuletzt.get(wert);
        if (bisher == null || (datum != null && datum.compareTo(bisher) > 0)) {
            zuletzt.put(wert, datum == null ? "" : datum);
        }
    }

    /** Die Schlüssel der Map, sortiert nach Datum absteigend. */
    private static List<String> nachDatumSortiert(Map<String, String> zuletzt) {
        List<String> namen = new ArrayList<>(zuletzt.keySet());
        namen.sort(Comparator
                // Neueste zuerst
                .comparing((String n) -> zuletzt.get(n), Comparator.reverseOrder())
                // Bei gleichem Datum alphabetisch, damit die Datei stabil bleibt
                .thenComparing(Namen::normalisieren));
        return namen;
    }
}
