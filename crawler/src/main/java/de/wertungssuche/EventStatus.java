package de.wertungssuche;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Der Merkzettel des Crawlers: {@code docs/data/events.json}.
 *
 * <p>Darin steht, welche Events schon verarbeitet wurden und mit welchem
 * Ergebnis. Daraus entscheidet der Crawler bei jedem Lauf, was er überhaupt
 * laden muss – abgeschlossene Events werden nicht wieder angefasst.
 *
 * <p>Die Datei liegt bewusst unter {@code docs/data/}: so kann die Website
 * daraus die Liste der ausgewerteten Wettkämpfe anzeigen, und es gibt nur
 * einen Ort für alle Daten.
 */
public class EventStatus {

    public static final String ABGESCHLOSSEN = "abgeschlossen";
    public static final String OFFEN = "offen";
    public static final String FEHLER = "fehler";
    public static final String OHNE_WERTUNGEN = "ohne-wertungen";

    /** Zeitpunkt des letzten Crawler-Laufs. */
    public String aktualisiert;

    /** Event-ID -> Eintrag. {@link TreeMap}, damit die Datei stabil sortiert ist. */
    public Map<String, Eintrag> events = new TreeMap<>();

    /** Was wir uns zu einem Event merken. */
    public static class Eintrag {
        public String name;
        /** Beginn, Format JJJJ-MM-TT. */
        public String beginn;
        /** Ende, Format JJJJ-MM-TT. */
        public String ende;
        /** Name der ausrichtenden Organisation. */
        public String ausrichter;
        /** Numerische Länder-ID des Ausrichters (276 = Deutschland). */
        public Integer land;
        /** Einer der Werte {@link #ABGESCHLOSSEN}, {@link #OFFEN}, ... */
        public String status;
        /**
         * {@code true}, wenn das Event von Hand aufgenommen wurde. Solche
         * Events laufen in allen Folgeläufen automatisch mit, auch wenn sie
         * nicht ins Land- und Datumsraster passen.
         */
        public boolean manuell;
        /** Tag des letzten erfolgreichen Ladens. */
        public String zuletztGeladen;
        /** {@code updated_at} aus der Eventliste beim letzten Laden. */
        public String standSporttech;
        /** Anzahl der übernommenen Wertungen. */
        public int wertungen;
        /** Fehlermeldung, nur bei {@link #FEHLER}. */
        public String fehler;
    }

    /** Lädt die Datei. Existiert sie nicht, wird ein leerer Stand geliefert. */
    public static EventStatus laden(Path datei) throws IOException {
        if (!Files.exists(datei)) {
            return new EventStatus();
        }
        String json = Files.readString(datei, StandardCharsets.UTF_8);
        EventStatus status = new Gson().fromJson(json, EventStatus.class);
        if (status == null) {
            return new EventStatus();
        }
        if (status.events == null) {
            status.events = new TreeMap<>();
        } else {
            // Gson liefert eine LinkedHashMap; wir wollen sortiert schreiben.
            status.events = new TreeMap<>(status.events);
        }
        return status;
    }

    /** Schreibt die Datei mit Einrückung, damit Git-Diffs lesbar bleiben. */
    public void speichern(Path datei) throws IOException {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        Files.createDirectories(datei.getParent());
        Files.writeString(datei, gson.toJson(this) + "\n", StandardCharsets.UTF_8);
    }

    public Eintrag get(String eventId) {
        return events.get(eventId);
    }

    /**
     * Entscheidet, ob ein Event (erneut) geladen werden muss.
     *
     * <p>Die Regeln der Reihe nach:
     * <ol>
     *   <li>noch nie geladen → ja</li>
     *   <li>letztes Mal mit Fehler → ja, nochmal versuchen</li>
     *   <li>Event läuft noch oder ist erst vor kurzem zu Ende gegangen → ja,
     *       damit Nachkorrekturen der Kampfrichter übernommen werden</li>
     *   <li>auf sporttech.io geändert ({@code updated_at} neuer als unser
     *       letzter Ladezeitpunkt) → ja</li>
     *   <li>sonst → nein</li>
     * </ol>
     *
     * @param eventId         die Event-ID
     * @param standSporttech  {@code updated_at} aus der Eventliste
     * @param heute           das heutige Datum
     * @param erneutLadenTage wie viele Tage nach Eventende noch neu geladen wird
     */
    public boolean mussGeladenWerden(String eventId, String standSporttech,
                                     LocalDate heute, int erneutLadenTage) {
        Eintrag alt = events.get(eventId);
        if (alt == null) {
            return true;
        }
        if (FEHLER.equals(alt.status)) {
            return true;
        }
        LocalDate grenze = heute.minusDays(erneutLadenTage);
        LocalDate ende = datumOderNull(alt.ende);
        if (ende == null || !ende.isBefore(grenze)) {
            return true;
        }
        if (standSporttech != null && alt.standSporttech != null
                && standSporttech.compareTo(alt.standSporttech) > 0) {
            return true;
        }
        return false;
    }

    /** Ist dieses Event schon bekannt? Für die Obergrenze "neue Events pro Lauf". */
    public boolean istBekannt(String eventId) {
        return events.containsKey(eventId);
    }

    /** Alle von Hand aufgenommenen Event-IDs – die laufen immer mit. */
    public Map<String, Eintrag> manuelleEvents() {
        Map<String, Eintrag> ergebnis = new LinkedHashMap<>();
        for (Map.Entry<String, Eintrag> e : events.entrySet()) {
            if (e.getValue() != null && e.getValue().manuell) {
                ergebnis.put(e.getKey(), e.getValue());
            }
        }
        return ergebnis;
    }

    /** Wandelt {@code JJJJ-MM-TT} in ein Datum, oder {@code null}. */
    static LocalDate datumOderNull(String text) {
        if (text == null || text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
