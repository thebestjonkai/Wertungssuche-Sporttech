package de.wertungssuche;

import com.google.gson.Gson;
import de.wertungssuche.api.EventDaten;
import de.wertungssuche.api.EventListe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Lädt die Beispieldateien aus {@code crawler/samples} für die Tests.
 *
 * <p>Die Tests gehen bewusst nie ins Netz: sie müssen auch dann durchlaufen,
 * wenn sporttech.io gerade nicht erreichbar ist, und sie sollen den Server
 * nicht unnötig belasten.
 */
final class Beispieldaten {

    private static final Gson GSON = new Gson();

    private Beispieldaten() {
        // Hilfsklasse
    }

    /**
     * Findet den Ordner {@code samples}, egal ob Maven aus dem Projektordner
     * oder aus {@code crawler/} heraus gestartet wurde.
     */
    static Path samplesOrdner() {
        Path hier = Paths.get("").toAbsolutePath();
        for (Path kandidat : new Path[] {
                hier.resolve("samples"),
                hier.resolve("crawler").resolve("samples"),
                hier.getParent() == null ? hier : hier.getParent().resolve("samples")
        }) {
            if (Files.isDirectory(kandidat)) {
                return kandidat;
            }
        }
        throw new IllegalStateException(
                "Ordner samples nicht gefunden, gestartet in " + hier);
    }

    static String lesen(String dateiname) {
        try {
            return Files.readString(samplesOrdner().resolve(dateiname),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Das gekürzte CBI 2024 – alle vier Disziplinen, Vereine als Representing. */
    static EventDaten cbi2024() {
        return GSON.fromJson(lesen("event-cbi-2024-gekuerzt.json"), EventDaten.class);
    }

    /** Das gekürzte Cottbus 2024 – Nationen, fehlende Felder, kein Geburtsdatum. */
    static EventDaten cottbus2024() {
        return GSON.fromJson(lesen("event-cottbus-2024-gekuerzt.json"), EventDaten.class);
    }

    /** Die gekürzte Eventliste – drei Trampolin-Events und zwei andere. */
    static EventListe eventListe() {
        return GSON.fromJson(lesen("eventliste-gekuerzt.json"), EventListe.class);
    }

    /** Eine Konfiguration ohne Filter, wie sie die Tests brauchen. */
    static Konfiguration standardKonfiguration() {
        return new Konfiguration();
    }

    /** Ein Extraktor mit der mitgelieferten Länderliste, ohne Netzzugriff. */
    static WertungsExtraktor extraktor() {
        return new WertungsExtraktor(Laenderliste.nurMitgeliefert(),
                standardKonfiguration());
    }
}
