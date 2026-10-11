package de.wertungssuche;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unterscheidet, ob in {@code Athlete.Representing} ein Verein oder eine
 * Nation steht.
 *
 * <p>Das ist nötig, weil sporttech.io für beides dasselbe Feld benutzt:
 * <ul>
 *   <li>Deutschland Cup 2025: {@code TG Münster 1862} – ein <b>Verein</b></li>
 *   <li>FIG World Cup Cottbus 2024: {@code GER} – eine <b>Nation</b></li>
 *   <li>CBI 2024 (Brasilien): {@code CAP-SP} – ein <b>Verein</b> mit
 *       Bundesstaat-Kürzel</li>
 * </ul>
 *
 * <p>{@code Event.ShowAthleteCountryFlag} hilft nicht weiter: das steht in allen
 * drei Events auf {@code true}. Darum entscheiden wir datengetrieben anhand der
 * offiziellen Länderliste von sporttech.io selbst:
 * {@code GET https://sporttech.io/api/countries/} liefert 243 Länder mit
 * IOC-Kürzel ({@code GER}) und ISO-Kürzel ({@code DEU}).
 *
 * <p>Ist der Wert ein bekanntes Kürzel, ist es eine Nation. Sonst ein Verein.
 * Fällt der Abruf aus, greift die mitgelieferte Kopie
 * {@code src/main/resources/laender.json}.
 */
public class Laenderliste {

    private static final String URL = "https://sporttech.io/api/countries/";

    /** Alle bekannten Kürzel in Großschreibung – IOC und ISO gemischt. */
    private final Set<String> kuerzel = new HashSet<>();

    /** Länder-ID (z. B. 276) -> Name ("Germany"). Nur fürs Protokoll. */
    private final Map<Integer, String> namen = new HashMap<>();

    /** Ein Eintrag der API-Antwort beziehungsweise der mitgelieferten Kopie. */
    private static class Land {
        String ioc;
        String alpha3;
        String en_name;
        /** Nur in der API-Antwort vorhanden. */
        Integer country_id;
        /** Nur in der mitgelieferten Kopie vorhanden. */
        String name;
    }

    private static class Antwort {
        Map<String, Land> country;
    }

    /**
     * Lädt die Länderliste von sporttech.io und fällt bei einem Fehler auf die
     * mitgelieferte Kopie zurück.
     */
    public static Laenderliste laden(HttpLader lader) {
        Laenderliste liste = new Laenderliste();
        try {
            String json = lader.holen(URL);
            Antwort antwort = new Gson().fromJson(json, Antwort.class);
            if (antwort != null && antwort.country != null && !antwort.country.isEmpty()) {
                for (Map.Entry<String, Land> e : antwort.country.entrySet()) {
                    Land l = e.getValue();
                    liste.aufnehmen(l.ioc);
                    liste.aufnehmen(l.alpha3);
                    Integer id = (l.country_id != null) ? l.country_id : zahlOderNull(e.getKey());
                    if (id != null) {
                        liste.namen.put(id, l.en_name != null ? l.en_name : l.name);
                    }
                }
                System.out.println("Länderliste von sporttech.io geladen: "
                        + liste.kuerzel.size() + " Kürzel.");
                return liste;
            }
            System.out.println("Länderliste war leer, benutze mitgelieferte Kopie.");
        } catch (IOException | RuntimeException e) {
            System.out.println("Länderliste konnte nicht geladen werden ("
                    + e.getMessage() + "), benutze mitgelieferte Kopie.");
        }
        liste.ausRessourceLaden();
        return liste;
    }

    /** Nur für Tests: eine Liste ausschließlich aus der mitgelieferten Kopie. */
    public static Laenderliste nurMitgeliefert() {
        Laenderliste liste = new Laenderliste();
        liste.ausRessourceLaden();
        return liste;
    }

    private void ausRessourceLaden() {
        try (InputStream ein = Laenderliste.class.getResourceAsStream("/laender.json")) {
            if (ein == null) {
                System.out.println("WARNUNG: /laender.json nicht gefunden. "
                        + "Alle Representing-Werte gelten jetzt als Verein.");
                return;
            }
            String json = new String(ein.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Land> daten = new Gson().fromJson(
                    json, new TypeToken<Map<String, Land>>() { }.getType());
            for (Map.Entry<String, Land> e : daten.entrySet()) {
                aufnehmen(e.getValue().ioc);
                aufnehmen(e.getValue().alpha3);
                Integer id = zahlOderNull(e.getKey());
                if (id != null) {
                    namen.put(id, e.getValue().name);
                }
            }
            System.out.println("Mitgelieferte Länderliste geladen: "
                    + kuerzel.size() + " Kürzel.");
        } catch (IOException | RuntimeException e) {
            System.out.println("WARNUNG: mitgelieferte Länderliste unlesbar: " + e.getMessage());
        }
    }

    private void aufnehmen(String k) {
        if (k != null && !k.isBlank()) {
            kuerzel.add(k.trim().toUpperCase());
        }
    }

    private static Integer zahlOderNull(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Ist dieser {@code Representing}-Wert ein Länderkürzel?
     *
     * <p>Geprüft wird nur, wenn der Wert genau drei Zeichen lang ist. Das
     * verhindert, dass ein Verein, der zufällig so heißt wie ein Land, als
     * Nation durchgeht.
     */
    public boolean istNation(String representing) {
        if (representing == null) {
            return false;
        }
        String wert = representing.trim();
        return wert.length() == 3 && kuerzel.contains(wert.toUpperCase());
    }

    /** Der englische Ländername zur numerischen ID, oder {@code null}. */
    public String nameZuId(Integer countryId) {
        return countryId == null ? null : namen.get(countryId);
    }

    public int anzahlKuerzel() {
        return kuerzel.size();
    }
}
