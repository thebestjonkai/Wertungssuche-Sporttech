package de.wertungssuche;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Einstellungen aus {@code crawler/config.json}.
 *
 * <p>Gson füllt die Felder dieser Klasse direkt aus der JSON-Datei. Felder, die
 * in der Datei fehlen, behalten den hier im Code gesetzten Standardwert – so
 * läuft der Crawler auch mit einer unvollständigen config.json.
 */
public class Konfiguration {

    /** Events, die vor diesem Tag beginnen, werden übersprungen. Format JJJJ-MM-TT. */
    public String abDatum = "2024-01-01";

    /**
     * Numerische Länder-IDs der ausrichtenden Organisation (276 = Deutschland).
     * Leere Liste bedeutet: keine Einschränkung nach Land.
     */
    public List<Integer> laenderIds = new ArrayList<>(List.of(276));

    /** Obergrenze neuer Events pro Lauf, damit der Workflow nicht in die Zeitgrenze läuft. */
    public int maxNeueEventsProLauf = 200;

    /** Wartezeit zwischen zwei Anfragen. 1000 ms = höchstens eine Anfrage pro Sekunde. */
    public long pauseZwischenAnfragenMs = 1000;

    /** Events, deren Ende weniger als so viele Tage zurückliegt, werden erneut geladen. */
    public int erneutLadenTage = 14;

    /** Event-IDs, die immer geladen werden – unabhängig von Land und Datum. */
    public List<String> zusaetzlicheEvents = new ArrayList<>();

    /**
     * Zusätzlich zu durchsuchende Organisationen (UUIDs).
     *
     * <p>Nur nötig für Organisationen, die <b>ausschließlich</b> nicht
     * gelistete Events ausrichten – die tauchen sonst nirgends auf. Wird ein
     * Event von Hand aufgenommen, merkt sich der Crawler dessen Ausrichter
     * von selbst, siehe {@link EventStatus.Eintrag#ausrichterId}.
     */
    public List<String> zusaetzlicheOrganisationen = new ArrayList<>();

    /**
     * Ob die Organisationen nach nicht gelisteten Events durchsucht werden.
     *
     * <p>Die Eventliste von sporttech.io zeigt nur Events mit
     * {@code highlight: true}. Über {@code /api/organizations/<id>
     * ?fetch_events=true} kommt man auch an die anderen. Das kostet eine
     * Anfrage je Organisation – bei den deutschen sind das rund 130, also
     * gut zwei Minuten zusätzlich pro Lauf.
     */
    public boolean organisationenScannen = true;

    /**
     * Events, deren Name einen dieser Textbausteine enthält, werden
     * übersprungen (Groß-/Kleinschreibung egal).
     *
     * <p>Hintergrund: der Organisationsscan findet auch die Probeläufe, mit
     * denen Veranstalter ihre Kampfrichteranlage einrichten. Gemessen am
     * Datenbestand sind das 16 Events mit zusammen 199 Wertungen – echte
     * Namen, aber erfundene Ergebnisse, die in einer Laufbahn nichts zu
     * suchen haben.
     *
     * <p>Die Veranstalter benennen diese Events selbst eindeutig:
     * „Testwettkampf", „Test 2", „Liga Test", „Landes Einzel Testversion".
     * Eine leere Liste schaltet den Filter ab.
     */
    public List<String> ignorierenWennName = new ArrayList<>(List.of("test"));

    /** Nur Athleten dieser Nationen übernehmen. Leer = alle. */
    public List<String> nationFilter = new ArrayList<>();

    /** Nur Athleten dieser Vereine übernehmen (Teiltreffer). Leer = alle. */
    public List<String> vereinFilter = new ArrayList<>();

    public long timeoutVerbindungMs = 15_000;
    public long timeoutAntwortMs = 120_000;
    public int versuche = 3;
    public long pauseNachFehlerMs = 5_000;

    /**
     * Die Doku-Felder aus der config.json. Sie interessieren den Crawler nicht,
     * stehen hier aber mit drin, damit Gson sie nicht als unbekannt meldet.
     */
    @SerializedName("_hinweis")
    private transient String hinweis;

    /**
     * Lädt die Konfiguration. Existiert die Datei nicht, werden die
     * Standardwerte aus dieser Klasse benutzt.
     */
    public static Konfiguration laden(Path datei) throws IOException {
        if (!Files.exists(datei)) {
            System.out.println("Hinweis: " + datei + " nicht gefunden, benutze Standardwerte.");
            return new Konfiguration();
        }
        String json = Files.readString(datei, StandardCharsets.UTF_8);
        Konfiguration k = new Gson().fromJson(json, Konfiguration.class);
        // Gson setzt Felder auf null, wenn in der JSON-Datei ausdrücklich null steht.
        if (k == null) {
            k = new Konfiguration();
        }
        if (k.laenderIds == null) k.laenderIds = new ArrayList<>();
        if (k.zusaetzlicheEvents == null) k.zusaetzlicheEvents = new ArrayList<>();
        if (k.zusaetzlicheOrganisationen == null) {
            k.zusaetzlicheOrganisationen = new ArrayList<>();
        }
        if (k.nationFilter == null) k.nationFilter = new ArrayList<>();
        if (k.vereinFilter == null) k.vereinFilter = new ArrayList<>();
        if (k.ignorierenWennName == null) k.ignorierenWennName = new ArrayList<>();
        if (k.abDatum == null || k.abDatum.isBlank()) k.abDatum = "1900-01-01";
        if (k.pauseZwischenAnfragenMs < 0) k.pauseZwischenAnfragenMs = 1000;
        if (k.versuche < 1) k.versuche = 1;
        return k;
    }

    @Override
    public String toString() {
        return "Konfiguration{abDatum=" + abDatum
                + ", laenderIds=" + laenderIds
                + ", maxNeueEventsProLauf=" + maxNeueEventsProLauf
                + ", pauseZwischenAnfragenMs=" + pauseZwischenAnfragenMs
                + ", erneutLadenTage=" + erneutLadenTage
                + ", zusaetzlicheEvents=" + zusaetzlicheEvents.size()
                + ", nationFilter=" + nationFilter
                + ", vereinFilter=" + vereinFilter + "}";
    }
}
