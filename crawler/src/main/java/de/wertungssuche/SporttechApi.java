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
     * Einzelnes Event aus dem Portal – <b>auch wenn es nicht in der Liste
     * steht</b>.
     *
     * <p>Liefert dieselben Felder wie ein Listeneintrag, also {@code sport},
     * {@code rules}, {@code begin_date} und über {@code fetch_organizations}
     * auch den Ausrichter samt Land. Damit lässt sich ein von Hand
     * aufgenommenes Event genauso prüfen und einordnen wie ein gelistetes.
     */
    public static String eventInfoUrl(String eventId) {
        return BASIS + "/api/events/" + eventId + "?fetch_organizations=true";
    }

    /**
     * Alle Events einer Organisation – <b>einschließlich der nicht
     * gelisteten</b>.
     *
     * <p>Das ist der Weg zu Wettkämpfen, die {@link #URL_EVENTLISTE} nicht
     * zeigt. Die Eventliste gibt nur Events mit {@code highlight: true}
     * heraus; über die Organisation kommen auch die anderen.
     *
     * <p>Der Parameter {@code fetch_events} stammt aus der Liste der
     * fetch-Parameter im JavaScript-Bundle von sporttech.io.
     */
    public static String organisationUrl(String organisationId) {
        return BASIS + "/api/organizations/" + organisationId + "?fetch_events=true";
    }

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

    /**
     * Lädt die Liste aller Events.
     *
     * <p><b>Achtung:</b> dieser Endpunkt gibt nur Events mit
     * {@code highlight: true} heraus – Stand 10/2026 sind das 2 775 von
     * mindestens 2 815. Wettkämpfe ohne dieses Kennzeichen fehlen hier
     * vollständig, obwohl ihre Ergebnisse öffentlich sind. Siehe
     * {@link #organisationEvents(String)}.
     */
    public EventListe eventListeLaden() throws IOException {
        String json = lader.holen(URL_EVENTLISTE);
        EventListe liste = parsen(json, EventListe.class);
        if (liste == null || liste.event == null) {
            throw new IOException("Eventliste hatte nicht die erwartete Form.");
        }
        return liste;
    }

    /**
     * Lädt die Stammdaten eines einzelnen Events aus dem Portal, auch wenn es
     * nicht in der Eventliste steht.
     */
    public EventListe eventInfoLaden(String eventId) throws IOException {
        String json = lader.holen(eventInfoUrl(eventId));
        EventListe antwort = parsen(json, EventListe.class);
        if (antwort == null || antwort.event == null) {
            throw new IOException("Event " + eventId + " hatte nicht die erwartete Form.");
        }
        return antwort;
    }

    /**
     * Lädt alle Events einer Organisation – auch die nicht gelisteten.
     *
     * <p>Die Antwort hat dieselbe Form wie die Eventliste, enthält aber nur
     * die Events dieser einen Organisation.
     */
    public EventListe organisationEvents(String organisationId) throws IOException {
        String json = lader.holen(organisationUrl(organisationId));
        EventListe antwort = parsen(json, EventListe.class);
        if (antwort == null) {
            throw new IOException("Organisation " + organisationId
                    + " hatte nicht die erwartete Form.");
        }
        return antwort;
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
