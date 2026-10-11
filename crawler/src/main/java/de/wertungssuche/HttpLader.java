package de.wertungssuche;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

/**
 * Lädt Seiten von sporttech.io – rücksichtsvoll und fehlertolerant.
 *
 * <p>Diese Klasse kümmert sich um alles, was mit dem Netz zu tun hat:
 * <ul>
 *   <li><b>Tempo:</b> zwischen zwei Anfragen wird gewartet, standardmäßig eine
 *       Sekunde. Wir sind Gast auf einem fremden Server.</li>
 *   <li><b>gzip:</b> wir fragen komprimierte Antworten an. Eine Event-Antwort
 *       schrumpft damit von rund 2 MB auf rund 100 KB.</li>
 *   <li><b>Wiederholungen:</b> schlägt eine Anfrage fehl, wird sie nach einer
 *       Pause erneut versucht.</li>
 *   <li><b>Eigener User-Agent:</b> damit der Betreiber sieht, wer da anfragt.</li>
 * </ul>
 */
public class HttpLader {

    /**
     * Eigener User-Agent mit Projektnamen und Repository-Adresse, damit der
     * Betreiber von sporttech.io sehen kann, wer da anfragt, und uns bei
     * Problemen erreichen könnte.
     */
    private static final String USER_AGENT =
            "Trampolin-Wertungssuche/1.0 "
            + "(+https://github.com/thebestjonkai/Wertungssuche-Sporttech)";

    private final HttpClient client;
    private final Konfiguration konfig;

    /** Zeitpunkt der letzten Anfrage, für die Pause zwischen den Anfragen. */
    private long letzteAnfrageMs = 0;

    /** Zähler fürs Protokoll am Ende des Laufs. */
    private int anzahlAnfragen = 0;
    private int anzahlFehler = 0;

    public HttpLader(Konfiguration konfig) {
        this.konfig = konfig;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(konfig.timeoutVerbindungMs))
                // Umleitungen folgen, sporttech.io schickt http auf https
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Holt eine URL als Text. Versucht es bei einem Fehler mehrmals.
     *
     * @return der Antworttext
     * @throws IOException wenn alle Versuche fehlgeschlagen sind
     */
    public String holen(String url) throws IOException {
        IOException letzterFehler = null;

        for (int versuch = 1; versuch <= konfig.versuche; versuch++) {
            try {
                return holenEinmal(url);
            } catch (IOException e) {
                letzterFehler = e;
                anzahlFehler++;
                System.out.println("  Versuch " + versuch + " von " + konfig.versuche
                        + " fehlgeschlagen: " + e.getMessage());
                if (versuch < konfig.versuche) {
                    warten(konfig.pauseNachFehlerMs);
                }
            } catch (InterruptedException e) {
                // Thread wurde abgebrochen – Status weitergeben und aufhören.
                Thread.currentThread().interrupt();
                throw new IOException("Abgebrochen beim Laden von " + url, e);
            }
        }
        throw new IOException("Alle " + konfig.versuche + " Versuche fehlgeschlagen für "
                + url, letzterFehler);
    }

    /** Ein einzelner Versuch, inklusive Einhalten der Pause und Auspacken von gzip. */
    private String holenEinmal(String url) throws IOException, InterruptedException {
        pauseEinhalten();

        HttpRequest anfrage = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(konfig.timeoutAntwortMs))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();

        anzahlAnfragen++;
        HttpResponse<byte[]> antwort =
                client.send(anfrage, HttpResponse.BodyHandlers.ofByteArray());

        byte[] rohdaten = antwort.body();
        boolean istGezippt = antwort.headers()
                .firstValue("Content-Encoding")
                .map(wert -> wert.toLowerCase().contains("gzip"))
                .orElse(false);
        if (istGezippt) {
            rohdaten = auspacken(rohdaten);
        }
        String text = new String(rohdaten, StandardCharsets.UTF_8);

        if (antwort.statusCode() != 200) {
            // sporttech.io antwortet bei Fehlern mit Klartext, nicht mit JSON.
            String kurz = text.length() > 200 ? text.substring(0, 200) + "..." : text;
            throw new IOException("HTTP " + antwort.statusCode() + " – " + kurz);
        }
        return text;
    }

    /** Packt eine gzip-Antwort aus. */
    private static byte[] auspacken(byte[] gezippt) throws IOException {
        try (GZIPInputStream ein = new GZIPInputStream(new ByteArrayInputStream(gezippt))) {
            ByteArrayOutputStream aus = new ByteArrayOutputStream(gezippt.length * 8);
            byte[] puffer = new byte[16 * 1024];
            int gelesen;
            while ((gelesen = ein.read(puffer)) != -1) {
                aus.write(puffer, 0, gelesen);
            }
            return aus.toByteArray();
        }
    }

    /**
     * Wartet, falls die letzte Anfrage noch nicht lange genug zurückliegt.
     * So entsteht garantiert höchstens eine Anfrage pro konfigurierter Pause.
     */
    private void pauseEinhalten() {
        long vergangen = System.currentTimeMillis() - letzteAnfrageMs;
        long nochZuWarten = konfig.pauseZwischenAnfragenMs - vergangen;
        if (nochZuWarten > 0) {
            warten(nochZuWarten);
        }
        letzteAnfrageMs = System.currentTimeMillis();
    }

    private static void warten(long millisekunden) {
        try {
            Thread.sleep(millisekunden);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getAnzahlAnfragen() {
        return anzahlAnfragen;
    }

    public int getAnzahlFehler() {
        return anzahlFehler;
    }
}
