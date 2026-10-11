package de.wertungssuche;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import de.wertungssuche.api.EventDaten;
import de.wertungssuche.api.EventListe;

import java.io.IOException;

/**
 * Die beiden Endpunkte von sporttech.io, die wir brauchen.
 *
 * <p>Beide sind hier an einer Stelle gebündelt, damit man im Fall einer
 * Änderung auf sporttech.io nur diese Datei anfassen muss.
 */
public class SporttechApi {

    private static final String BASIS = "https://sporttech.io";

    /**
     * Liste <b>aller</b> Events, alle Jahre.
     *
     * <p>Der Parameter {@code year} bleibt absichtlich leer – dann liefert der
     * Server alles in einer Antwort (Stand 10/2026 rund 2 775 Events, 3,3 MB
     * unkomprimiert). Mit {@code year=2024} bekäme man nur ein Jahr.
     *
     * <p>Gefunden habe ich diesen Endpunkt im JavaScript-Bundle der
     * Explore-Seite ({@code /static/web/script.min.js}), wo der Pfad als
     * Zeichenkette auftaucht. Die Explore-Seite selbst lädt die Liste per
     * JavaScript nach, im HTML steht sie nicht.
     */
    public static final String URL_EVENTLISTE =
            BASIS + "/api/events/?fetch_organizations=true&year=";

    /**
     * Die Abfrageparameter für die Event-Struktur.
     *
     * <p>{@code fetch_panels} ist bewusst nicht dabei: das sind die
     * Kampfrichter-Panels, die wir nicht brauchen und die die Antwort nur
     * größer machen.
     */
    private static final String EVENT_PARAMETER =
            "?fetch_event_competitions=true"
            + "&fetch_competition_stages=true"
            + "&fetch_stage_groups=true"
            + "&fetch_group_performances=true"
            + "&fetch_performance_frames=true"
            + "&fetch_performance_athletes=true";

    private final HttpLader lader;
    private final Gson gson = new Gson();

    public SporttechApi(HttpLader lader) {
        this.lader = lader;
    }

    /** Die URL der Event-Struktur für eine Event-ID. */
    public static String eventUrl(String eventId) {
        return BASIS + "/events/" + eventId + "/ovs/api/event" + EVENT_PARAMETER;
    }

    /** Die öffentliche Ergebnisseite eines Durchgangs – der Link für die Website. */
    public static String ergebnisUrl(String eventId, Integer stageId) {
        return BASIS + "/events/" + eventId + "/ovs/stages/" + stageId + "/results";
    }

    /** Lädt die Liste aller Events. */
    public EventListe eventListeLaden() throws IOException {
        String json = lader.holen(URL_EVENTLISTE);
        EventListe liste = parsen(json, EventListe.class);
        if (liste == null || liste.event == null) {
            throw new IOException("Eventliste hatte nicht die erwartete Form.");
        }
        return liste;
    }

    /**
     * Lädt die komplette Struktur eines Events.
     *
     * <p>Der Endpunkt wird im offiziellen Beispielprojekt von sporttech per
     * EventSource gelesen, funktioniert aber genauso als normales GET und
     * liefert dann einmalig den kompletten Stand.
     */
    public EventDaten eventLaden(String eventId) throws IOException {
        String json = lader.holen(eventUrl(eventId));
        EventDaten daten = parsen(json, EventDaten.class);
        if (daten == null || daten.Event == null) {
            throw new IOException("Event " + eventId + " hatte nicht die erwartete Form.");
        }
        return daten;
    }

    /**
     * Parst JSON und wirft eine {@link IOException} statt einer
     * {@link JsonSyntaxException} – damit die Aufrufer nur eine Art Fehler
     * behandeln müssen.
     *
     * <p>Das ist nicht nur Kosmetik: sporttech.io antwortet bei Fehlern mit
     * Klartext statt JSON, zum Beispiel
     * {@code Invalid event ID: Invalid UUID string}.
     */
    private <T> T parsen(String json, Class<T> typ) throws IOException {
        try {
            return gson.fromJson(json, typ);
        } catch (JsonSyntaxException e) {
            String kurz = json.length() > 120 ? json.substring(0, 120) + "..." : json;
            throw new IOException("Antwort war kein gültiges JSON: " + kurz, e);
        }
    }
}
