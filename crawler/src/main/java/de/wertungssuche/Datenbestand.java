package de.wertungssuche;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Verwaltet die Wertungsdateien unter {@code docs/data/}.
 *
 * <p>Aufgeteilt wird nach dem ersten Buchstaben des Nachnamens in der
 * Vergleichsform: {@code a.json}, {@code b.json} … {@code z.json}, alles
 * andere nach {@code other.json}. So lädt die Website nur die eine Datei, die
 * sie für eine Suche braucht.
 *
 * <p><b>Beim erneuten Laden eines Events werden dessen alte Zeilen ersetzt,
 * nicht ergänzt.</b> Das ist wichtig: wenn ein Kampfgericht eine Wertung
 * zurückzieht oder eine Schreibweise korrigiert wird, soll die alte Zeile
 * verschwinden und nicht als Geisterzeile stehen bleiben. Deshalb werden
 * zuerst alle Zeilen mit der betroffenen {@code eventId} entfernt.
 *
 * <p>Zusätzlich gibt es eine Sicherung gegen echte Duplikate über
 * {@link Wertung#schluessel()} – falls dasselbe Event in einem Lauf doppelt
 * verarbeitet würde.
 */
public class Datenbestand {

    /** Dateiname -> Wertungen in dieser Datei. */
    private final Map<String, List<Wertung>> dateien = new TreeMap<>();

    /** Alle schon vorhandenen Schlüssel, zur Duplikaterkennung. */
    private final Set<String> schluessel = new HashSet<>();

    private final Path datenOrdner;

    /** Zähler fürs Protokoll. */
    private int hinzugefuegt = 0;
    private int duplikate = 0;
    private int ersetzt = 0;

    public Datenbestand(Path datenOrdner) {
        this.datenOrdner = datenOrdner;
    }

    /** Lädt alle vorhandenen Wertungsdateien ein. */
    public void laden() throws IOException {
        if (!Files.isDirectory(datenOrdner)) {
            return;
        }
        Gson gson = new Gson();
        for (String name : moeglicheDateinamen()) {
            Path datei = datenOrdner.resolve(name + ".json");
            if (!Files.exists(datei)) {
                continue;
            }
            String json = Files.readString(datei, StandardCharsets.UTF_8);
            List<Wertung> gelesen = gson.fromJson(
                    json, new TypeToken<List<Wertung>>() { }.getType());
            if (gelesen == null) {
                continue;
            }
            List<Wertung> ziel = dateien.computeIfAbsent(name, k -> new ArrayList<>());
            for (Wertung w : gelesen) {
                if (w == null) {
                    continue;
                }
                if (schluessel.add(w.schluessel())) {
                    ziel.add(w);
                }
            }
        }
        System.out.println("Vorhandener Datenbestand: " + anzahlWertungen()
                + " Wertungen in " + dateien.size() + " Dateien.");
    }

    /**
     * Entfernt alle Zeilen eines Events. Wird vor dem Einfügen der neuen
     * Zeilen desselben Events aufgerufen.
     */
    public void eventEntfernen(String eventId) {
        for (List<Wertung> liste : dateien.values()) {
            List<Wertung> weg = new ArrayList<>();
            for (Wertung w : liste) {
                if (eventId.equals(w.eventId)) {
                    weg.add(w);
                }
            }
            for (Wertung w : weg) {
                liste.remove(w);
                schluessel.remove(w.schluessel());
                ersetzt++;
            }
        }
    }

    /** Nimmt eine Liste neuer Wertungen auf und überspringt Duplikate. */
    public void hinzufuegen(List<Wertung> neue) {
        for (Wertung w : neue) {
            if (!schluessel.add(w.schluessel())) {
                duplikate++;
                continue;
            }
            dateien.computeIfAbsent(w.datei(), k -> new ArrayList<>()).add(w);
            hinzugefuegt++;
        }
    }

    /**
     * Schreibt alle Dateien. Sortiert wird nach Nachname, Vorname, dann Datum
     * absteigend – so bleiben die Git-Diffs klein, weil ein neues Event nur an
     * wenigen Stellen einfügt statt die ganze Datei umzusortieren.
     */
    public void speichern() throws IOException {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                // Felder mit null trotzdem schreiben, damit auf der Website
                // sichtbar ist, dass es den Wert nicht gibt.
                .serializeNulls()
                .create();

        Files.createDirectories(datenOrdner);

        Comparator<Wertung> reihenfolge = Comparator
                .comparing((Wertung w) -> Namen.normalisieren(w.nachname))
                .thenComparing(w -> Namen.normalisieren(w.vorname))
                .thenComparing(w -> w.datum == null ? "" : w.datum,
                        Comparator.reverseOrder())
                .thenComparing(w -> w.wettkampf == null ? "" : w.wettkampf)
                .thenComparing(w -> w.durchgang == null ? "" : w.durchgang);

        for (String name : moeglicheDateinamen()) {
            Path datei = datenOrdner.resolve(name + ".json");
            List<Wertung> liste = dateien.get(name);

            if (liste == null || liste.isEmpty()) {
                // Leer gewordene Dateien löschen, damit keine Reste liegen bleiben.
                Files.deleteIfExists(datei);
                continue;
            }
            liste.sort(reihenfolge);
            Files.writeString(datei, gson.toJson(liste) + "\n", StandardCharsets.UTF_8);
        }
    }

    /**
     * Schreibt {@code meta.json} – Stand der Daten und ein paar Kennzahlen.
     * Die Website zeigt daraus an, wann die Daten zuletzt aktualisiert wurden.
     */
    public void metaSpeichern(Path datei, String zeitpunkt, int anzahlEvents)
            throws IOException {

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("aktualisiert", zeitpunkt);
        meta.put("quelle", "https://sporttech.io");
        meta.put("hinweis", "Alle Wertungen stammen von sporttech.io.");
        meta.put("anzahlWertungen", anzahlWertungen());
        meta.put("anzahlPersonen", anzahlPersonen());
        meta.put("anzahlEvents", anzahlEvents);

        Map<String, Integer> proDatei = new TreeMap<>();
        for (Map.Entry<String, List<Wertung>> e : dateien.entrySet()) {
            if (!e.getValue().isEmpty()) {
                proDatei.put(e.getKey(), e.getValue().size());
            }
        }
        meta.put("dateien", proDatei);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.createDirectories(datei.getParent());
        Files.writeString(datei, gson.toJson(meta) + "\n", StandardCharsets.UTF_8);
    }

    /** Alle Dateinamen, die es geben kann: a–z plus die Sammeldatei. */
    static List<String> moeglicheDateinamen() {
        List<String> namen = new ArrayList<>(27);
        for (char c = 'a'; c <= 'z'; c++) {
            namen.add(String.valueOf(c));
        }
        namen.add(Namen.SAMMELDATEI);
        return namen;
    }

    public int anzahlWertungen() {
        int summe = 0;
        for (List<Wertung> liste : dateien.values()) {
            summe += liste.size();
        }
        return summe;
    }

    public int anzahlPersonen() {
        Set<String> personen = new HashSet<>();
        for (List<Wertung> liste : dateien.values()) {
            for (Wertung w : liste) {
                personen.add(w.personSchluessel());
            }
        }
        return personen.size();
    }

    /** Alle Wertungen über alle Dateien hinweg – für den Personenindex. */
    public List<Wertung> alleWertungen() {
        List<Wertung> alle = new ArrayList<>(anzahlWertungen());
        for (List<Wertung> liste : dateien.values()) {
            alle.addAll(liste);
        }
        return alle;
    }

    public int getHinzugefuegt() {
        return hinzugefuegt;
    }

    public int getDuplikate() {
        return duplikate;
    }

    public int getErsetzt() {
        return ersetzt;
    }

    /** Nur für Tests. */
    Map<String, List<Wertung>> getDateien() {
        return dateien;
    }
}
