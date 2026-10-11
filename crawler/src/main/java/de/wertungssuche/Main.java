package de.wertungssuche;

import de.wertungssuche.api.EventDaten;
import de.wertungssuche.api.EventListe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Startpunkt des Crawlers.
 *
 * <p>Ablauf eines Laufs:
 * <ol>
 *   <li>Konfiguration und bisherigen Stand laden</li>
 *   <li>Eventliste von sporttech.io holen</li>
 *   <li>Events auswählen: Trampolin, richtiges Land, ab dem Stichtag – plus
 *       alle von Hand aufgenommenen Events</li>
 *   <li>für jedes ausgewählte Event die Wertungen holen und umwandeln;
 *       ein Fehler bei einem Event bricht den Lauf nicht ab</li>
 *   <li>Dateien unter {@code docs/data/} schreiben</li>
 * </ol>
 *
 * <p>Aufruf (aus dem Projektordner):
 * <pre>
 * java -jar crawler/target/crawler.jar
 * java -jar crawler/target/crawler.jar --event 91f733c2-17e1-402f-74d8-60b7dfbf28b7
 * java -jar crawler/target/crawler.jar --nur-events --event &lt;id1&gt;,&lt;id2&gt;
 * java -jar crawler/target/crawler.jar --trockenlauf
 * </pre>
 */
public class Main {

    private static final DateTimeFormatter ZEITSTEMPEL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    public static void main(String[] args) {
        try {
            int fehler = ausfuehren(args);
            System.exit(fehler);
        } catch (Exception e) {
            System.err.println("Der Lauf ist abgebrochen: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int ausfuehren(String[] args) throws IOException {
        Argumente arg = Argumente.lesen(args);
        if (arg.hilfe) {
            Argumente.hilfeAusgeben();
            return 0;
        }

        Path wurzel = projektWurzelFinden();
        Path konfigDatei = (arg.konfigPfad != null)
                ? Paths.get(arg.konfigPfad)
                : wurzel.resolve("crawler").resolve("config.json");
        Path datenOrdner = (arg.ausgabePfad != null)
                ? Paths.get(arg.ausgabePfad)
                : wurzel.resolve("docs").resolve("data");

        System.out.println("=== Trampolin-Wertungssuche – Crawler ===");
        System.out.println("Projektordner: " + wurzel);
        System.out.println("Konfiguration: " + konfigDatei);
        System.out.println("Ausgabe:       " + datenOrdner);

        Konfiguration konfig = Konfiguration.laden(konfigDatei);
        if (arg.maxNeueEvents != null) {
            konfig.maxNeueEventsProLauf = arg.maxNeueEvents;
        }
        System.out.println(konfig);
        System.out.println();

        HttpLader lader = new HttpLader(konfig);
        SporttechApi api = new SporttechApi(lader);
        Laenderliste laender = Laenderliste.laden(lader);

        Path statusDatei = datenOrdner.resolve("events.json");
        EventStatus status = EventStatus.laden(statusDatei);
        System.out.println("Bisher bekannte Events: " + status.events.size());

        Datenbestand bestand = new Datenbestand(datenOrdner);
        bestand.laden();
        System.out.println();

        // --- Schritt 1: entscheiden, was geladen wird ---
        LocalDate heute = LocalDate.now(ZoneOffset.UTC);
        Auswahl auswahl = auswaehlen(api, konfig, status, arg, heute);

        if (auswahl.zuLaden.isEmpty()) {
            System.out.println("Nichts zu tun – alle Events sind aktuell.");
            // Trotzdem meta.json und den Suchindex aktualisieren, damit der
            // Zeitstempel stimmt und ein fehlender Index nachgezogen wird.
            if (!arg.trockenlauf) {
                status.aktualisiert = ZEITSTEMPEL.format(Instant.now());
                status.speichern(statusDatei);
                Personenindex.schreiben(bestand.alleWertungen(),
                        datenOrdner.resolve("personen.json"));
                bestand.metaSpeichern(datenOrdner.resolve("meta.json"),
                        status.aktualisiert, status.events.size());
            }
            return 0;
        }

        System.out.println();
        System.out.println("=== " + auswahl.zuLaden.size() + " Events werden geladen ===");

        // --- Schritt 2: Events laden und auswerten ---
        WertungsExtraktor extraktor = new WertungsExtraktor(laender, konfig);
        int nummer = 0;
        int erfolgreich = 0;
        int fehlgeschlagen = 0;

        for (Map.Entry<String, Kandidat> eintrag : auswahl.zuLaden.entrySet()) {
            String eventId = eintrag.getKey();
            Kandidat kandidat = eintrag.getValue();
            nummer++;

            System.out.printf(Locale.ROOT, "[%d/%d] %s (%s)%n",
                    nummer, auswahl.zuLaden.size(),
                    kandidat.name != null ? kandidat.name : eventId,
                    kandidat.beginn != null ? kandidat.beginn : "ohne Datum");

            EventStatus.Eintrag neu = new EventStatus.Eintrag();
            neu.name = kandidat.name;
            neu.beginn = kandidat.beginn;
            neu.ende = kandidat.ende;
            neu.ausrichter = kandidat.ausrichter;
            neu.land = kandidat.land;
            neu.manuell = kandidat.manuell;
            neu.standSporttech = kandidat.standSporttech;

            try {
                EventDaten daten = api.eventLaden(eventId);

                // Der Eventname aus der Struktur ist genauer als der aus der Liste.
                if (daten.Event != null && daten.Event.Title != null
                        && !daten.Event.Title.isBlank()) {
                    neu.name = Namen.aufraeumen(daten.Event.Title);
                }
                if (neu.beginn == null) {
                    neu.beginn = WertungsExtraktor.datumAusZeitstempel(daten.Event.StartDate);
                }
                if (neu.ende == null) {
                    neu.ende = WertungsExtraktor.datumAusZeitstempel(daten.Event.EndDate);
                }

                List<Wertung> wertungen = extraktor.extrahieren(daten);

                // Alte Zeilen dieses Events weg, dann die neuen rein.
                bestand.eventEntfernen(eventId);
                bestand.hinzufuegen(wertungen);

                neu.wertungen = wertungen.size();
                neu.zuletztGeladen = heute.toString();
                neu.fehler = null;
                neu.status = statusBestimmen(neu, wertungen.size(), heute,
                        konfig.erneutLadenTage);

                System.out.println("      " + wertungen.size() + " Wertungen, Status "
                        + neu.status);
                erfolgreich++;

            } catch (IOException | RuntimeException e) {
                // Ein kaputtes Event darf den Lauf nie abbrechen.
                EventStatus.Eintrag alt = status.get(eventId);
                neu.status = EventStatus.FEHLER;
                neu.fehler = e.getMessage();
                if (alt != null) {
                    // Bisherige Zahlen behalten, damit nichts verloren geht.
                    neu.wertungen = alt.wertungen;
                    neu.zuletztGeladen = alt.zuletztGeladen;
                    if (neu.name == null) {
                        neu.name = alt.name;
                    }
                }
                System.out.println("      FEHLER: " + e.getMessage());
                fehlgeschlagen++;
            }

            status.events.put(eventId, neu);
        }

        // --- Schritt 3: schreiben ---
        System.out.println();
        if (arg.trockenlauf) {
            System.out.println("Trockenlauf – es wird nichts geschrieben.");
        } else {
            status.aktualisiert = ZEITSTEMPEL.format(Instant.now());
            bestand.speichern();
            int anzahlPersonen = Personenindex.schreiben(
                    bestand.alleWertungen(), datenOrdner.resolve("personen.json"));
            bestand.metaSpeichern(datenOrdner.resolve("meta.json"),
                    status.aktualisiert, status.events.size());
            status.speichern(statusDatei);
            System.out.println("Dateien geschrieben nach " + datenOrdner
                    + " (Suchindex: " + anzahlPersonen + " Personen)");
        }

        // --- Zusammenfassung ---
        System.out.println();
        System.out.println("=== Zusammenfassung ===");
        System.out.println("Events geladen:            " + erfolgreich);
        System.out.println("Events mit Fehler:         " + fehlgeschlagen);
        System.out.println("Events noch offen:         " + auswahl.nochOffen);
        System.out.println("HTTP-Anfragen:             " + lader.getAnzahlAnfragen());
        System.out.println("HTTP-Fehlversuche:         " + lader.getAnzahlFehler());
        System.out.println("Wertungen neu/ersetzt:     " + bestand.getHinzugefuegt()
                + " / " + bestand.getErsetzt());
        System.out.println("Duplikate übersprungen:    " + bestand.getDuplikate());
        System.out.println("Übersprungen (Disziplin):  "
                + extraktor.getUebersprungeneDisziplin() + " Durchgänge");
        System.out.println("Übersprungen (nicht frei): "
                + extraktor.getUebersprungeneNichtVeroeffentlicht() + " Übungen");
        System.out.println("Übersprungen (Filter):     "
                + extraktor.getUebersprungeneFilter() + " Zeilen");
        System.out.println("Wertungen insgesamt:       " + bestand.anzahlWertungen());
        System.out.println("Personen insgesamt:        " + bestand.anzahlPersonen());

        if (auswahl.nochOffen > 0) {
            System.out.println();
            System.out.println("Hinweis: Es sind noch " + auswahl.nochOffen
                    + " neue Events offen (Grenze: " + konfig.maxNeueEventsProLauf
                    + " pro Lauf). Der nächste Lauf arbeitet sie ab.");
        }

        // Rückgabewert 0, auch wenn einzelne Events fehlgeschlagen sind – der
        // Workflow soll deswegen nicht rot werden.
        return 0;
    }

    /**
     * Legt fest, welche Events dieser Lauf lädt.
     *
     * <p>Aufgenommen wird ein Event, wenn es
     * <ul>
     *   <li>von Hand übergeben wurde (per {@code --event} oder
     *       {@code config.zusaetzlicheEvents}), oder</li>
     *   <li>schon früher von Hand aufgenommen wurde ({@code manuell: true}
     *       in events.json), oder</li>
     *   <li>im Raster liegt: Sportart Trampolin, Ausrichterland passt,
     *       Beginn nicht vor dem Stichtag.</li>
     * </ul>
     * Von Hand aufgenommene Events ignorieren Land und Stichtag – genau dafür
     * sind sie da.
     */
    private static Auswahl auswaehlen(SporttechApi api, Konfiguration konfig,
                                      EventStatus status, Argumente arg,
                                      LocalDate heute) throws IOException {

        Auswahl auswahl = new Auswahl();

        // Alle von Hand gewünschten IDs sammeln.
        Set<String> manuelle = new LinkedHashSet<>();
        manuelle.addAll(arg.eventIds);
        manuelle.addAll(konfig.zusaetzlicheEvents);
        manuelle.addAll(status.manuelleEvents().keySet());

        if (arg.nurEvents) {
            if (manuelle.isEmpty()) {
                System.out.println("--nur-events gesetzt, aber keine Event-ID angegeben.");
                return auswahl;
            }
            System.out.println("--nur-events: nur die " + manuelle.size()
                    + " von Hand angegebenen Events werden geladen.");
            for (String id : manuelle) {
                Kandidat k = new Kandidat();
                k.manuell = true;
                EventStatus.Eintrag alt = status.get(id);
                if (alt != null) {
                    k.name = alt.name;
                    k.beginn = alt.beginn;
                    k.ende = alt.ende;
                    k.ausrichter = alt.ausrichter;
                    k.land = alt.land;
                }
                auswahl.zuLaden.put(id, k);
            }
            return auswahl;
        }

        System.out.println("Eventliste wird geladen ...");
        EventListe liste = api.eventListeLaden();
        System.out.println("Eventliste: " + liste.event.size() + " Events, "
                + (liste.organization == null ? 0 : liste.organization.size())
                + " Organisationen.");

        // Die Kandidaten im Raster, sortiert nach Beginn – so werden beim
        // Erstaufbau die ältesten zuerst abgearbeitet und die Reihenfolge ist
        // über mehrere Läufe stabil.
        List<Map.Entry<String, Kandidat>> imRaster = new ArrayList<>();

        int nichtTrampolin = 0;
        int falschesLand = 0;
        int zuAlt = 0;

        for (Map.Entry<String, EventListe.Event> e : liste.event.entrySet()) {
            String id = e.getKey();
            EventListe.Event ev = e.getValue();
            if (ev == null) {
                continue;
            }

            if (!istTrampolin(ev)) {
                nichtTrampolin++;
                continue;
            }
            String beginn = WertungsExtraktor.datumAusZeitstempel(ev.begin_date);
            if (beginn == null || beginn.compareTo(konfig.abDatum) < 0) {
                zuAlt++;
                continue;
            }
            Kandidat k = kandidatAus(id, ev, liste);
            if (!konfig.laenderIds.isEmpty()
                    && (k.land == null || !konfig.laenderIds.contains(k.land))) {
                falschesLand++;
                continue;
            }
            imRaster.add(Map.entry(id, k));
        }

        imRaster.sort((a, b) -> {
            String ba = a.getValue().beginn == null ? "" : a.getValue().beginn;
            String bb = b.getValue().beginn == null ? "" : b.getValue().beginn;
            int c = ba.compareTo(bb);
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });

        System.out.println("  davon keine Trampolin-Events: " + nichtTrampolin);
        System.out.println("  davon vor " + konfig.abDatum + ":    " + zuAlt);
        System.out.println("  davon anderes Land:           " + falschesLand);
        System.out.println("  im Raster:                    " + imRaster.size());

        // Von Hand gewünschte Events zuerst – die sollen nie an der
        // Obergrenze für neue Events scheitern.
        for (String id : manuelle) {
            EventListe.Event ev = liste.event.get(id);
            Kandidat k = (ev != null) ? kandidatAus(id, ev, liste) : new Kandidat();
            k.manuell = true;
            if (ev == null) {
                EventStatus.Eintrag alt = status.get(id);
                if (alt != null) {
                    k.name = alt.name;
                    k.beginn = alt.beginn;
                    k.ende = alt.ende;
                }
                System.out.println("  Hinweis: Event " + id
                        + " steht nicht in der Eventliste, wird aber direkt geladen.");
            }
            if (status.mussGeladenWerden(id, k.standSporttech, heute, konfig.erneutLadenTage)) {
                auswahl.zuLaden.put(id, k);
            }
        }

        // Dann die Events im Raster, mit Obergrenze für neue.
        int neueAufgenommen = 0;
        for (Map.Entry<String, Kandidat> eintrag : imRaster) {
            String id = eintrag.getKey();
            Kandidat k = eintrag.getValue();

            if (auswahl.zuLaden.containsKey(id)) {
                continue;
            }
            if (!status.mussGeladenWerden(id, k.standSporttech, heute, konfig.erneutLadenTage)) {
                continue;
            }
            boolean istNeu = !status.istBekannt(id);
            if (istNeu) {
                if (neueAufgenommen >= konfig.maxNeueEventsProLauf) {
                    auswahl.nochOffen++;
                    continue;
                }
                neueAufgenommen++;
            }
            auswahl.zuLaden.put(id, k);
        }

        System.out.println("  neu in diesem Lauf:           " + neueAufgenommen);
        return auswahl;
    }

    /** Ist das ein Trampolin-Event? Zwei Felder, doppelt abgesichert. */
    static boolean istTrampolin(EventListe.Event ev) {
        boolean sportPasst = "Trampoline".equalsIgnoreCase(
                ev.sport == null ? "" : ev.sport.trim());
        boolean reglementPasst = ev.rules != null
                && ev.rules.trim().toUpperCase(Locale.ROOT).startsWith("TRA");
        return sportPasst && reglementPasst;
    }

    /** Baut einen Kandidaten aus einem Listeneintrag, inklusive Ausrichterland. */
    private static Kandidat kandidatAus(String id, EventListe.Event ev, EventListe liste) {
        Kandidat k = new Kandidat();
        k.name = Namen.aufraeumen(ev.en_name);
        k.beginn = WertungsExtraktor.datumAusZeitstempel(ev.begin_date);
        k.ende = WertungsExtraktor.datumAusZeitstempel(ev.end_date);
        k.standSporttech = ev.updated_at;

        // Das Land steht nicht im Event, sondern nur in der ausrichtenden
        // Organisation. Darum wird fetch_organizations=true gebraucht.
        if (ev.organizations != null && liste.organization != null) {
            EventListe.Organisation ausrichter = null;
            EventListe.Organisation ersatz = null;
            for (EventListe.Teilnahme t : ev.organizations) {
                if (t == null || t.organization_id == null) {
                    continue;
                }
                EventListe.Organisation o = liste.organization.get(t.organization_id);
                if (o == null) {
                    continue;
                }
                if ("host".equalsIgnoreCase(t.participation)) {
                    ausrichter = o;
                    break;
                }
                if (ersatz == null) {
                    ersatz = o;
                }
            }
            EventListe.Organisation benutzt = (ausrichter != null) ? ausrichter : ersatz;
            if (benutzt != null) {
                k.ausrichter = Namen.aufraeumen(benutzt.en_name);
                k.land = benutzt.country_id;
            }
        }
        return k;
    }

    /**
     * Welcher Status gehört in events.json?
     *
     * <p>{@code abgeschlossen} heißt: das Event ist lange genug vorbei, dass
     * keine Nachkorrekturen mehr zu erwarten sind. Nur solche Events werden in
     * Folgeläufen übersprungen.
     */
    private static String statusBestimmen(EventStatus.Eintrag eintrag, int anzahlWertungen,
                                          LocalDate heute, int erneutLadenTage) {
        LocalDate ende = EventStatus.datumOderNull(eintrag.ende);
        boolean vorbei = ende != null && ende.isBefore(heute.minusDays(erneutLadenTage));
        if (!vorbei) {
            return EventStatus.OFFEN;
        }
        if (anzahlWertungen == 0) {
            // Vorbei, aber nichts für uns drin – z. B. ein reines
            // Doppel-Mini- oder Synchron-Event.
            return EventStatus.OHNE_WERTUNGEN;
        }
        return EventStatus.ABGESCHLOSSEN;
    }

    /**
     * Findet den Projektordner: den ersten Ordner von hier aufwärts, der sowohl
     * {@code docs} als auch {@code crawler} enthält. So funktioniert der Start
     * aus dem Projektordner und aus {@code crawler/} gleich gut.
     */
    static Path projektWurzelFinden() {
        Path start = Paths.get("").toAbsolutePath().normalize();
        Path p = start;
        for (int i = 0; i < 5 && p != null; i++) {
            if (Files.isDirectory(p.resolve("crawler")) && Files.isDirectory(p.resolve("docs"))) {
                return p;
            }
            // Auch der Fall "wir stehen in crawler/" soll gehen.
            if (Files.exists(p.resolve("pom.xml"))
                    && p.getParent() != null
                    && Files.isDirectory(p.getParent().resolve("docs"))) {
                return p.getParent();
            }
            p = p.getParent();
        }
        return start;
    }

    /** Ein Event, das geladen werden soll, mit allem, was wir schon wissen. */
    private static class Kandidat {
        String name;
        String beginn;
        String ende;
        String ausrichter;
        Integer land;
        String standSporttech;
        boolean manuell;
    }

    /** Das Ergebnis der Auswahl. */
    private static class Auswahl {
        /** Event-ID -> Kandidat, in der Reihenfolge der Abarbeitung. */
        final Map<String, Kandidat> zuLaden = new LinkedHashMap<>();
        /** Wie viele neue Events wegen der Obergrenze warten müssen. */
        int nochOffen = 0;
    }

    /** Die Kommandozeilenargumente. */
    private static class Argumente {
        String konfigPfad;
        String ausgabePfad;
        Integer maxNeueEvents;
        final List<String> eventIds = new ArrayList<>();
        boolean nurEvents;
        boolean trockenlauf;
        boolean hilfe;

        static Argumente lesen(String[] args) {
            Argumente a = new Argumente();
            for (int i = 0; i < args.length; i++) {
                String s = args[i];
                switch (s) {
                    case "--config" -> a.konfigPfad = naechstes(args, ++i, s);
                    case "--ausgabe" -> a.ausgabePfad = naechstes(args, ++i, s);
                    case "--max" -> a.maxNeueEvents =
                            Integer.valueOf(naechstes(args, ++i, s));
                    case "--event" -> {
                        // Mehrere IDs dürfen mit Komma getrennt übergeben werden,
                        // damit der GitHub-Workflow eine einzige Eingabe reichen kann.
                        for (String teil : naechstes(args, ++i, s).split("[,;\\s]+")) {
                            String id = teil.trim();
                            if (!id.isEmpty()) {
                                a.eventIds.add(id);
                            }
                        }
                    }
                    case "--nur-events" -> a.nurEvents = true;
                    case "--trockenlauf" -> a.trockenlauf = true;
                    case "--hilfe", "-h", "--help" -> a.hilfe = true;
                    default -> {
                        System.out.println("Unbekanntes Argument: " + s);
                        a.hilfe = true;
                    }
                }
            }
            return a;
        }

        private static String naechstes(String[] args, int i, String name) {
            if (i >= args.length) {
                throw new IllegalArgumentException(name + " braucht einen Wert.");
            }
            return args[i];
        }

        static void hilfeAusgeben() {
            System.out.println("""
                Trampolin-Wertungssuche – Crawler

                Aufruf:
                  java -jar crawler/target/crawler.jar [Optionen]

                Optionen:
                  --config <pfad>     andere Konfigurationsdatei
                                      (Standard: crawler/config.json)
                  --ausgabe <pfad>    anderer Ausgabeordner
                                      (Standard: docs/data)
                  --max <zahl>        Obergrenze neuer Events für diesen Lauf
                  --event <ids>       zusätzliche Event-IDs, mit Komma getrennt.
                                      Diese Events werden unabhängig von Land und
                                      Stichtag geladen und in events.json als
                                      "manuell" vermerkt, damit sie in allen
                                      Folgeläufen automatisch mitlaufen.
                  --nur-events        nur die mit --event angegebenen Events laden,
                                      die Eventliste wird gar nicht abgerufen
                  --trockenlauf       alles ausrechnen, aber nichts schreiben
                  --hilfe             diese Übersicht
                """);
        }
    }
}
