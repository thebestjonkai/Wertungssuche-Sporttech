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

        // Schon gespeicherte Probeläufe wieder entfernen. Wird der Filter
        // nachträglich eingeschaltet oder erweitert, verschwinden die Daten
        // beim nächsten Lauf von selbst – sonst blieben sie für immer liegen.
        int aufgeraeumt = probelaeufeEntfernen(konfig, status, bestand);
        if (aufgeraeumt > 0) {
            System.out.println(aufgeraeumt + " früher geladene Probeläufe wurden "
                    + "aus dem Datenbestand entfernt.");
            System.out.println();
        }

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
                // Wurde aufgeräumt, müssen die Wertungsdateien mit raus.
                if (aufgeraeumt > 0) {
                    bestand.speichern();
                }
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
            neu.ausrichterId = kandidat.ausrichterId;
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
                    // Den Ausrichter nicht verlieren - über ihn findet der
                    // Crawler die nicht gelisteten Events dieser Organisation.
                    if (neu.ausrichterId == null) {
                        neu.ausrichterId = alt.ausrichterId;
                        neu.ausrichter = alt.ausrichter;
                        neu.land = alt.land;
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

        // Alle von Hand gewünschten IDs sammeln. In der config.json darf statt
        // der ID auch die komplette sporttech.io-Adresse stehen.
        Set<String> manuelle = new LinkedHashSet<>();
        manuelle.addAll(arg.eventIds);
        for (String eintrag : konfig.zusaetzlicheEvents) {
            String id = eventIdAus(eintrag);
            if (id != null) {
                manuelle.add(id);
            } else if (eintrag != null && !eintrag.isBlank()) {
                System.out.println("zusaetzlicheEvents: keine Event-ID erkannt in "
                        + eintrag);
            }
        }
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

        // Alle bekannten Events und Organisationen in einem Topf sammeln.
        Map<String, EventListe.Event> alleEvents = new LinkedHashMap<>(liste.event);
        Map<String, EventListe.Organisation> alleOrgs =
                new LinkedHashMap<>(liste.organization == null
                        ? Map.of() : liste.organization);

        int ausOrganisationen = 0;
        if (konfig.organisationenScannen) {
            ausOrganisationen = organisationenDurchsuchen(
                    api, konfig, status, alleEvents, alleOrgs);
        }

        // Die Kandidaten im Raster, sortiert nach Beginn – so werden beim
        // Erstaufbau die ältesten zuerst abgearbeitet und die Reihenfolge ist
        // über mehrere Läufe stabil.
        List<Map.Entry<String, Kandidat>> imRaster = new ArrayList<>();

        int nichtTrampolin = 0;
        int falschesLand = 0;
        int zuAlt = 0;
        List<String> ignoriert = new ArrayList<>();

        for (Map.Entry<String, EventListe.Event> e : alleEvents.entrySet()) {
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
            Kandidat k = kandidatAus(ev, alleOrgs);
            if (!konfig.laenderIds.isEmpty()
                    && (k.land == null || !konfig.laenderIds.contains(k.land))) {
                falschesLand++;
                continue;
            }
            if (nameIgnorieren(k.name, konfig)) {
                ignoriert.add(k.name + "  (" + k.beginn + ", " + k.ausrichter + ")");
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

        System.out.println("  Events insgesamt geprüft:     " + alleEvents.size()
                + (ausOrganisationen > 0
                   ? " (" + ausOrganisationen + " davon nicht in der Eventliste)" : ""));
        System.out.println("  davon keine Trampolin-Events: " + nichtTrampolin);
        System.out.println("  davon vor " + konfig.abDatum + ":    " + zuAlt);
        System.out.println("  davon anderes Land:           " + falschesLand);
        if (!ignoriert.isEmpty()) {
            System.out.println("  davon Probeläufe:             " + ignoriert.size()
                    + "  (Name enthält " + konfig.ignorierenWennName + ")");
            for (String name : ignoriert) {
                System.out.println("      übersprungen: " + name);
            }
        }
        System.out.println("  im Raster:                    " + imRaster.size());

        // Von Hand gewünschte Events zuerst – die sollen nie an der
        // Obergrenze für neue Events scheitern.
        for (String id : manuelle) {
            EventListe.Event ev = alleEvents.get(id);

            // Nicht gelistet und auch über keine Organisation gefunden? Dann
            // die Stammdaten direkt beim Portal nachfragen - das geht auch
            // für Events, die in keiner Liste stehen.
            if (ev == null) {
                try {
                    EventListe antwort = api.eventInfoLaden(id);
                    ev = antwort.event.get(id);
                    if (antwort.organization != null) {
                        alleOrgs.putAll(antwort.organization);
                    }
                } catch (IOException fehler) {
                    System.out.println("  Stammdaten zu " + id
                            + " nicht abrufbar (" + fehler.getMessage()
                            + "), das Event wird trotzdem geladen.");
                }
            }

            Kandidat k = (ev != null) ? kandidatAus(ev, alleOrgs) : new Kandidat();
            k.manuell = true;

            if (ev == null) {
                EventStatus.Eintrag alt = status.get(id);
                if (alt != null) {
                    k.name = alt.name;
                    k.beginn = alt.beginn;
                    k.ende = alt.ende;
                    k.ausrichter = alt.ausrichter;
                    k.ausrichterId = alt.ausrichterId;
                    k.land = alt.land;
                }
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

    /**
     * Holt die Event-ID aus einer Eingabe.
     *
     * <p>Akzeptiert sowohl die nackte UUID als auch eine beliebige
     * sporttech.io-Adresse, denn genau die hat man beim Zuschauen im Browser
     * in der Adresszeile:
     *
     * <pre>
     * d613f43d-fb1c-4beb-4770-9e684e2e0a8e
     * https://sporttech.io/events/d613f43d-fb1c-4beb-4770-9e684e2e0a8e/ovs/
     * https://sporttech.io/events/d613f43d-fb1c-4beb-4770-9e684e2e0a8e/ovs/scoreboards/
     * https://sporttech.io/events/d613f43d-fb1c-4beb-4770-9e684e2e0a8e/ovs/stages/5/results
     * </pre>
     *
     * @return die ID, oder {@code null} wenn in der Eingabe keine steckt
     */
    static String eventIdAus(String eingabe) {
        if (eingabe == null) {
            return null;
        }
        var treffer = UUID_MUSTER.matcher(eingabe.trim());
        return treffer.find() ? treffer.group().toLowerCase(Locale.ROOT) : null;
    }

    /**
     * Eine UUID in der Schreibweise von sporttech.io.
     *
     * <p>Bewusst nicht die strenge RFC-Form: sporttech.io benutzt Gruppen wie
     * {@code 4770} an der Stelle, an der eine echte UUID Version und Variante
     * kodiert. Geprüft wird deshalb nur das Muster 8-4-4-4-12.
     */
    private static final java.util.regex.Pattern UUID_MUSTER =
            java.util.regex.Pattern.compile(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                    + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Entfernt Events, die früher geladen wurden, inzwischen aber unter den
     * Namensfilter fallen – samt ihrer Wertungen.
     *
     * <p>Ohne das hier würden einmal eingesammelte Probeläufe für immer im
     * Datenbestand bleiben, weil der Filter nur bei der Auswahl greift und
     * abgeschlossene Events gar nicht mehr angefasst werden.
     *
     * <p>Von Hand aufgenommene Events bleiben unangetastet.
     *
     * @return wie viele Events entfernt wurden
     */
    private static int probelaeufeEntfernen(Konfiguration konfig, EventStatus status,
                                            Datenbestand bestand) {
        if (konfig.ignorierenWennName.isEmpty()) {
            return 0;
        }
        List<String> weg = new ArrayList<>();
        for (Map.Entry<String, EventStatus.Eintrag> e : status.events.entrySet()) {
            EventStatus.Eintrag eintrag = e.getValue();
            if (eintrag == null || eintrag.manuell) {
                continue;
            }
            if (nameIgnorieren(eintrag.name, konfig)) {
                weg.add(e.getKey());
            }
        }
        for (String eventId : weg) {
            EventStatus.Eintrag eintrag = status.events.remove(eventId);
            bestand.eventEntfernen(eventId);
            System.out.println("  entfernt: " + (eintrag == null ? eventId : eintrag.name));
        }
        return weg.size();
    }

    /**
     * Soll dieses Event wegen seines Namens übersprungen werden?
     *
     * <p>Veranstalter richten vor einem Wettkampf Probeläufe ein, um ihre
     * Kampfrichteranlage zu prüfen. Die benennen sie selbst eindeutig –
     * „Testwettkampf", „Liga Test", „Landes Einzel Testversion" – und die
     * Ergebnisse darin sind erfunden.
     *
     * <p>Von Hand aufgenommene Events sind davon <b>nicht</b> betroffen: wer
     * eine Event-ID ausdrücklich einträgt, will sie auch haben.
     */
    static boolean nameIgnorieren(String name, Konfiguration konfig) {
        if (name == null || konfig.ignorierenWennName.isEmpty()) {
            return false;
        }
        String klein = name.toLowerCase(Locale.ROOT);
        for (String baustein : konfig.ignorierenWennName) {
            if (baustein != null && !baustein.isBlank()
                    && klein.contains(baustein.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Ist das ein Trampolin-Event? Zwei Felder, doppelt abgesichert. */
    static boolean istTrampolin(EventListe.Event ev) {
        boolean sportPasst = "Trampoline".equalsIgnoreCase(
                ev.sport == null ? "" : ev.sport.trim());
        boolean reglementPasst = ev.rules != null
                && ev.rules.trim().toUpperCase(Locale.ROOT).startsWith("TRA");
        return sportPasst && reglementPasst;
    }

    /**
     * Sucht über die Organisationen nach Events, die nicht in der Eventliste
     * stehen.
     *
     * <h2>Warum das nötig ist</h2>
     *
     * <p>{@code /api/events/} gibt nur Events mit {@code highlight: true}
     * heraus. Wettkämpfe ohne dieses Kennzeichen fehlen dort vollständig,
     * obwohl ihre Ergebnisseiten öffentlich sind – zum Beispiel die
     * WAGC/JWM-Qualifikation des DTB vom 06.06.2026 mit 51 Starterinnen und
     * Startern.
     *
     * <p>{@code /api/organizations/<id>?fetch_events=true} liefert dagegen
     * <b>alle</b> Events einer Organisation, auch die nicht gelisteten.
     *
     * <h2>Welche Organisationen durchsucht werden</h2>
     *
     * <ol>
     *   <li>alle aus der Eventliste bekannten Organisationen, deren Land
     *       passt (bei {@code laenderIds: [276]} rund 130 deutsche)</li>
     *   <li>die Ausrichter aller von Hand aufgenommenen Events – siehe
     *       {@link EventStatus#ausrichterManuellerEvents()}</li>
     *   <li>alles aus {@code zusaetzlicheOrganisationen} in der config.json</li>
     * </ol>
     *
     * <p>Punkt 2 ist der wichtige Teil: eine Organisation, die
     * <b>ausschließlich</b> nicht gelistete Events ausrichtet, steht selbst
     * in keiner Liste und ist auf keinem anderen Weg zu finden. Trägt man ein
     * einziges ihrer Events von Hand ein, merkt sich der Crawler den
     * Ausrichter – und findet ab dann alle weiteren Wettkämpfe dieser
     * Organisation von selbst.
     *
     * @return wie viele bisher unbekannte Events dazugekommen sind
     */
    private static int organisationenDurchsuchen(
            SporttechApi api, Konfiguration konfig, EventStatus status,
            Map<String, EventListe.Event> alleEvents,
            Map<String, EventListe.Organisation> alleOrgs) {

        Set<String> zuPruefen = new LinkedHashSet<>();

        for (Map.Entry<String, EventListe.Organisation> e : alleOrgs.entrySet()) {
            EventListe.Organisation o = e.getValue();
            if (o == null) {
                continue;
            }
            if (konfig.laenderIds.isEmpty()
                    || (o.country_id != null && konfig.laenderIds.contains(o.country_id))) {
                zuPruefen.add(e.getKey());
            }
        }
        zuPruefen.addAll(status.ausrichterManuellerEvents());
        zuPruefen.addAll(konfig.zusaetzlicheOrganisationen);

        if (zuPruefen.isEmpty()) {
            return 0;
        }
        System.out.println("Organisationen werden nach nicht gelisteten Events "
                + "durchsucht (" + zuPruefen.size() + ") ...");

        int neue = 0;
        int fehler = 0;

        for (String orgId : zuPruefen) {
            try {
                EventListe antwort = api.organisationEvents(orgId);
                if (antwort.organization != null) {
                    // Auch neue Organisationen merken, damit kandidatAus()
                    // später das Land findet.
                    for (Map.Entry<String, EventListe.Organisation> o
                            : antwort.organization.entrySet()) {
                        alleOrgs.putIfAbsent(o.getKey(), o.getValue());
                    }
                }
                if (antwort.event == null) {
                    continue;
                }
                for (Map.Entry<String, EventListe.Event> e : antwort.event.entrySet()) {
                    EventListe.Event ev = e.getValue();
                    // Events aus diesem Abruf tragen kein organizations-Feld -
                    // der Ausrichter steckt in der Anfrage. Wir tragen ihn
                    // nach, sonst fiele das Event später durch den Länderfilter.
                    if (ev != null && ev.organizations == null) {
                        EventListe.Teilnahme t = new EventListe.Teilnahme();
                        t.organization_id = orgId;
                        t.participation = "host";
                        ev.organizations = List.of(t);
                    }
                    if (alleEvents.putIfAbsent(e.getKey(), ev) == null) {
                        neue++;
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Eine kaputte Organisation darf den Lauf nicht aufhalten.
                fehler++;
            }
        }

        System.out.println("  nicht gelistete Events gefunden: " + neue
                + (fehler > 0 ? " (" + fehler + " Organisationen nicht abrufbar)" : ""));
        return neue;
    }

    /**
     * Baut einen Kandidaten aus einem Listeneintrag, inklusive Ausrichter
     * und dessen Land.
     *
     * <p>Das Land steht nicht im Event selbst, sondern nur in der
     * ausrichtenden Organisation – darum wird {@code fetch_organizations}
     * gebraucht und die Organisationen werden über alle Abrufe hinweg
     * gesammelt.
     */
    private static Kandidat kandidatAus(EventListe.Event ev,
                                        Map<String, EventListe.Organisation> orgs) {
        Kandidat k = new Kandidat();
        k.name = Namen.aufraeumen(ev.en_name);
        k.beginn = WertungsExtraktor.datumAusZeitstempel(ev.begin_date);
        k.ende = WertungsExtraktor.datumAusZeitstempel(ev.end_date);
        k.standSporttech = ev.updated_at;

        String ausrichterId = null;
        String ersatzId = null;
        if (ev.organizations != null) {
            for (EventListe.Teilnahme t : ev.organizations) {
                if (t == null || t.organization_id == null) {
                    continue;
                }
                if ("host".equalsIgnoreCase(t.participation)) {
                    ausrichterId = t.organization_id;
                    break;
                }
                if (ersatzId == null) {
                    ersatzId = t.organization_id;
                }
            }
        }
        // Rückfallebene: wer das Event angelegt hat, ist in allen geprüften
        // Fällen auch der Ausrichter.
        if (ausrichterId == null && ersatzId == null) {
            ersatzId = ev.created_by;
        }
        String benutztId = (ausrichterId != null) ? ausrichterId : ersatzId;
        if (benutztId != null) {
            k.ausrichterId = benutztId;
            EventListe.Organisation o = orgs.get(benutztId);
            if (o != null) {
                k.ausrichter = Namen.aufraeumen(o.en_name);
                k.land = o.country_id;
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
        /** ID der ausrichtenden Organisation – schaltet nicht gelistete Events frei. */
        String ausrichterId;
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
                            String id = eventIdAus(teil);
                            if (id != null) {
                                a.eventIds.add(id);
                            } else if (!teil.isBlank()) {
                                System.out.println("Keine Event-ID erkannt in: " + teil);
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
