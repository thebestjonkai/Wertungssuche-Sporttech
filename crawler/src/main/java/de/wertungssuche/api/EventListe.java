package de.wertungssuche.api;

import java.util.List;
import java.util.Map;

/**
 * Die Antwort von {@code GET /api/events/?fetch_organizations=true&year=}.
 *
 * <p>Zwei Maps: die Events selbst und die Organisationen. Die Verknüpfung läuft
 * über {@link Event#organizations}, und nur über die Organisation kommt man an
 * das Land ({@link Organisation#country_id}) – im Event selbst steht es nicht.
 *
 * <p>Alle Felder sind absichtlich Objekttypen und nicht {@code int}: die API
 * lässt Felder in einzelnen Objekten weg, und dann soll {@code null} stehen und
 * nicht stillschweigend eine 0.
 */
public class EventListe {

    /** Event-ID (UUID) -> Event. */
    public Map<String, Event> event;

    /** Organisations-ID (UUID) -> Organisation. */
    public Map<String, Organisation> organization;

    /** Ein Event in der Übersichtsliste. */
    public static class Event {
        public String event_id;
        public String en_name;
        /** Beginn im Format {@code 2025-10-05T00:00:00Z}. */
        public String begin_date;
        public String end_date;
        /** Sportart, z. B. {@code Trampoline}. Unser wichtigster Filter. */
        public String sport;
        /** Reglement, bei Trampolin immer mit Prefix {@code TRA}. */
        public String rules;
        public String updated_at;
        public List<Teilnahme> organizations;
    }

    /** Verknüpfung Event -> Organisation, mit der Rolle der Organisation. */
    public static class Teilnahme {
        public String organization_id;
        /** {@code host} = Ausrichter, {@code participant} = teilnehmender Verband. */
        public String participation;
    }

    /** Ein Verein oder Verband. */
    public static class Organisation {
        public String organization_id;
        public String en_name;
        /** Numerische Länder-ID, 276 = Deutschland. Passt zu {@code /api/countries/}. */
        public Integer country_id;
    }
}
