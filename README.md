# Trampolin-Wertungssuche

Eine Website, auf der man den Namen einer Turnerin oder eines Turners eingibt
und alle Trampolin-Wertungen dieser Person sieht – gesammelt aus allen
Wettkämpfen, die auf [sporttech.io](https://sporttech.io) veröffentlicht sind.

Auf sporttech.io selbst muss man jeden Wettkampf einzeln aufrufen und sich durch
Wettkampfklassen und Durchgänge klicken. Hier gibt man einmal den Namen ein und
bekommt die gesamte Laufbahn auf einer Seite, mit allen Einzelwertungen.

**Stand des Datenbestands:** 15 258 Wertungen von 2 932 Personen aus 71
Wettkämpfen (2024 bis 2026).

---

## Inhalt

- [Was gesammelt wird](#was-gesammelt-wird)
- [Wie das Ganze aufgebaut ist](#wie-das-ganze-aufgebaut-ist)
- [Einrichten](#einrichten)
- [Crawler lokal starten](#crawler-lokal-starten)
- [Website lokal ansehen](#website-lokal-ansehen)
- [Einzelne Wettkämpfe von Hand aufnehmen](#einzelne-wettkämpfe-von-hand-aufnehmen)
- [Workflow von Hand auslösen](#workflow-von-hand-auslösen)
- [Einstellungen](#einstellungen)
- [Die Dateien unter docs/data](#die-dateien-unter-docsdata)
- [Tests](#tests)
- [Was man wissen sollte](#was-man-wissen-sollte)
- [Projektaufbau](#projektaufbau)

---

## Was gesammelt wird

| | |
|---|---|
| **Disziplin** | nur **Trampolin Einzel** |
| **Wettkämpfe** | alle, die **in Deutschland ausgerichtet** werden – plus einzeln aufgenommene im Ausland |
| **Zeitraum** | ab dem **1. Januar 2024** |

Nicht dabei sind Synchronspringen, Doppel-Minitrampolin und Tumbling. Diese
Wettkämpfe stehen zwar in denselben Events, werden aber übersprungen.

Warum diese Einschränkung: weltweit gibt es auf sporttech.io 2 029
Trampolin-Events. Daraus würden rund **1,06 Millionen Wertungszeilen** und
etwa **600 MB** – das ist weder für ein Git-Repository noch für einen Browser
zu gebrauchen. Mit dem obigen Zuschnitt sind es 15 258 Zeilen und 10 MB, und die
Website lädt pro Suche höchstens 1,4 MB. Die Rechnung dazu steht in
[`DATENSTRUKTUR.md`](DATENSTRUKTUR.md), Kapitel 6.

Wichtige Wettkämpfe im Ausland lassen sich
[einzeln nachladen](#einzelne-wettkämpfe-von-hand-aufnehmen).

---

## Wie das Ganze aufgebaut ist

Es gibt keinen Server und keine Datenbank – alles läuft auf GitHub:

```
   sporttech.io
        │  einmal am Tag, höchstens 1 Anfrage pro Sekunde
        ▼
   Java-Crawler  ──────── läuft in GitHub Actions
        │  schreibt JSON
        ▼
   docs/data/*.json  ──── liegt im Repository, wird committet
        │
        ▼
   GitHub Pages  ──────── liefert docs/ als statische Website
        │
        ▼
   Browser  ───────────── sucht selbst in den JSON-Dateien
```

Der Crawler läuft **täglich um 03:00 UTC** und committet nur, wenn sich wirklich
etwas geändert hat. Die Website ist reines HTML, CSS und JavaScript – kein
Framework, keine Bauwerkzeuge, keine Webfonts, keine externen Dienste.

---

## Einrichten

### Voraussetzungen

| | |
|---|---|
| **Java 21** oder neuer | [Temurin](https://adoptium.net/) |
| **Maven** | zum Bauen des Crawlers |
| **Python 3** | nur für den lokalen Webserver und das Prüfskript |

Prüfen, ob alles da ist – in PowerShell:

```powershell
java -version
mvn -version
python --version
```

Fehlt Maven, lässt es sich mit winget installieren:

```powershell
winget install Apache.Maven
```

Danach **PowerShell neu öffnen**, damit der neue PATH gilt.

### GitHub Pages aktivieren

Damit die Website erreichbar ist:

1. Im Repository auf **Settings** gehen
2. links **Pages** auswählen
3. unter *Build and deployment* bei **Source** den Eintrag
   **Deploy from a branch** wählen
4. bei **Branch** den Zweig `main` und den Ordner **`/docs`** auswählen
5. auf **Save** klicken

Nach ein bis zwei Minuten ist die Seite erreichbar unter:

```
https://thebestjonkai.github.io/Wertungssuche-Sporttech/
```

### Berechtigung für den Crawler-Workflow

Der Crawler committet seine Ergebnisse selbst. Dafür braucht die Action
Schreibrechte:

1. **Settings** → **Actions** → **General**
2. ganz unten bei *Workflow permissions*
   **Read and write permissions** auswählen
3. **Save**

---

## Crawler lokal starten

Alle Befehle aus dem **Projektordner** heraus, in PowerShell:

```powershell
# Bauen - erzeugt crawler\target\crawler.jar
mvn -f crawler\pom.xml package

# Starten
java -jar crawler\target\crawler.jar
```

Der erste vollständige Lauf dauert rund zwei Minuten (71 Events, eine Anfrage
pro Sekunde). Danach werden nur noch Events geladen, die neu sind, die gerade
laufen oder die in den letzten 14 Tagen zu Ende gegangen sind – spätere Läufe
sind also deutlich kürzer.

### Nützliche Optionen

```powershell
# Erst mal nur schauen, was passieren würde - es wird nichts geschrieben
java -jar crawler\target\crawler.jar --trockenlauf

# Nur fünf neue Events laden, zum Ausprobieren
java -jar crawler\target\crawler.jar --max 5

# In einen anderen Ordner schreiben, statt docs\data anzufassen
java -jar crawler\target\crawler.jar --ausgabe C:\Temp\test-daten

# Alle Optionen anzeigen
java -jar crawler\target\crawler.jar --hilfe
```

### Noch einmal von vorne

Soll der ganze Bestand neu aufgebaut werden, reicht es, den Merkzettel zu
löschen – dann hält der Crawler kein Event mehr für erledigt:

```powershell
Remove-Item docs\data\events.json
java -jar crawler\target\crawler.jar
```

---

## Website lokal ansehen

Die Seite lädt ihre Daten per `fetch`. Browser verbieten das, wenn man die
`index.html` einfach doppelklickt – es braucht einen kleinen Webserver:

```powershell
cd docs
python -m http.server 8765
```

Dann im Browser [http://localhost:8765](http://localhost:8765) aufrufen.
Beenden mit <kbd>Strg</kbd>+<kbd>C</kbd>.

---

## Einzelne Wettkämpfe von Hand aufnehmen

Manche Wettkämpfe im Ausland sind wichtig, fallen aber durch das Raster
(Deutschland, ab 2024). Die lassen sich einzeln aufnehmen.

**Die Event-ID** steht in der Adresse der Ergebnisseite auf sporttech.io:

```
https://sporttech.io/events/91f733c2-17e1-402f-74d8-60b7dfbf28b7/ovs/event/TRA
                            └─────────── das ist die ID ────────────┘
```

### Von Hand, lokal

```powershell
java -jar crawler\target\crawler.jar --event 91f733c2-17e1-402f-74d8-60b7dfbf28b7
```

Mehrere auf einmal, mit Komma getrennt:

```powershell
java -jar crawler\target\crawler.jar --event "91f733c2-...,d13eb411-..."
```

Nur diese Events laden und die Eventliste gar nicht erst abrufen – das ist
deutlich schneller:

```powershell
java -jar crawler\target\crawler.jar --nur-events --event 91f733c2-...
```

### Über GitHub, ohne lokale Installation

Im Repository: **Actions** → **Wertungen holen** → **Run workflow**. Dort die
Event-IDs in das Feld *Zusätzliche Event-IDs* eintragen.

### Was danach passiert

So aufgenommene Events bekommen in `docs/data/events.json` das Kennzeichen
`"manuell": true`:

```json
"91f733c2-17e1-402f-74d8-60b7dfbf28b7": {
  "name": "FIG WORLD CUP COTTBUS 2024",
  "manuell": true,
  "status": "abgeschlossen",
  "wertungen": 381
}
```

**Sie laufen danach in jedem Lauf automatisch mit** – man muss die ID also nur
einmal angeben. Soll ein Event dauerhaft dabei sein, kann man es auch direkt in
[`crawler/config.json`](crawler/config.json) eintragen:

```json
"zusaetzlicheEvents": [
  "91f733c2-17e1-402f-74d8-60b7dfbf28b7"
]
```

> **Hinweis:** sporttech.io schaltet die Datenbank älterer oder selten
> aufgerufener Events zeitweise ab. Dann kommt ein `HTTP 500` und das Event
> landet mit `"status": "fehler"` im Merkzettel. Der nächste Lauf versucht es
> automatisch erneut – meist klappt es dann.

---

## Workflow von Hand auslösen

1. Im Repository auf **Actions**
2. links **Wertungen holen** auswählen
3. rechts **Run workflow**, Zweig `main`
4. wahlweise ausfüllen:

| Feld | Bedeutung |
|---|---|
| *Zusätzliche Event-IDs* | Event-IDs mit Komma getrennt, siehe oben |
| *Nur die oben angegebenen Events laden* | die Eventliste wird nicht abgerufen |
| *Obergrenze neuer Events* | leer lassen für den Wert aus `config.json` |

5. **Run workflow**

Nach dem Lauf steht unter *Summary* eine Zusammenfassung mit der Anzahl der
Wertungen, Personen und Events.

Der zweite Workflow, **Build und Tests**, läuft bei jedem Push automatisch. Er
kompiliert den Crawler, führt die Tests aus und prüft die Dateien unter
`docs/data` auf Gültigkeit.

---

## Einstellungen

Alles in [`crawler/config.json`](crawler/config.json):

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `abDatum` | `2024-01-01` | Events, die früher beginnen, werden übersprungen |
| `laenderIds` | `[276]` | Länder der ausrichtenden Organisation. 276 = Deutschland, leere Liste = alle |
| `maxNeueEventsProLauf` | `200` | Obergrenze neuer Events pro Lauf, damit der Workflow nicht in die Zeitgrenze läuft |
| `pauseZwischenAnfragenMs` | `1000` | Wartezeit zwischen zwei Anfragen. Bitte nicht verkleinern |
| `erneutLadenTage` | `14` | So lange nach Eventende wird neu geladen, damit Nachkorrekturen ankommen |
| `zusaetzlicheEvents` | `[]` | Event-IDs, die immer mitlaufen |
| `nationFilter` | `[]` | Nur diese Nationen übernehmen, z. B. `["GER"]` |
| `vereinFilter` | `[]` | Nur diese Vereine übernehmen (Teiltreffer) |
| `versuche` | `3` | Versuche pro Anfrage, bevor ein Event übersprungen wird |

Die Länder-IDs sind die aus `https://sporttech.io/api/countries/`. Ein paar
Beispiele: 276 Deutschland, 40 Österreich, 756 Schweiz, 620 Portugal,
208 Dänemark, 36 Australien.

---

## Die Dateien unter `docs/data`

| Datei | Inhalt |
|---|---|
| `a.json` … `z.json` | die Wertungen, nach dem ersten Buchstaben des Nachnamens |
| `other.json` | Nachnamen, die nicht mit a–z beginnen, z. B. kyrillische |
| `personen.json` | Suchindex: ein Eintrag pro Person, den die Website beim Start lädt |
| `events.json` | der Merkzettel des Crawlers: welche Events verarbeitet wurden |
| `meta.json` | Stand der Daten und Kennzahlen |

Eine Zeile sieht so aus:

```json
{
  "vorname": "Julie",
  "nachname": "Dierks",
  "jahrgang": 2013,
  "verein": "TV Voerde",
  "nation": null,
  "event": "Rheinische Jahrgangsmeisterschaften 2026",
  "eventId": "939b9a5e-8ed1-4140-5951-955577d36f1a",
  "datum": "2026-03-07",
  "disziplin": "Trampolin",
  "wettkampf": "WK 05 Jugendturnerinnen C (2012-2013)",
  "durchgang": "Qualifikation 1",
  "platz": 9,
  "d": 0.0,
  "e": 10.2,
  "h": 9.4,
  "t": 11.25,
  "abzug": 6.0,
  "gesamt": 24.85,
  "gesamtDurchgang": 59.995,
  "link": "https://sporttech.io/events/939b9a5e-.../ovs/stages/5/results"
}
```

**Eine Zeile ist eine Übung, nicht ein ganzer Durchgang.** Eine Qualifikation
aus zwei Übungen ergibt zwei Zeilen („Qualifikation 1" und „Qualifikation 2")
mit je eigenen D-, E-, T- und H-Werten. `platz` und `gesamtDurchgang` gehören
zum ganzen Durchgang und stehen deshalb auf beiden Zeilen gleich.

Werte, die es nicht gibt, sind `null` und nicht `0` – `t: null` heißt, dass
dieser Wettkampf keine Flugzeit gemessen hat.

Zwei Felder sind gegenüber der ursprünglichen Planung dazugekommen:
`disziplin` (sonst ließen sich Trampolin und Doppel-Mini später nicht trennen)
und `gesamtDurchgang`.

---

## Tests

```powershell
mvn -f crawler\pom.xml test
```

72 Tests, alle ohne Netzzugriff – sie arbeiten mit den echten, nur gekürzten
API-Antworten unter [`crawler/samples/`](crawler/samples/).

| Testklasse | Prüft |
|---|---|
| `WertungsExtraktorTest` | das Herauslösen der Wertungen und die Punkteformel |
| `DuplikatTest` | dass keine Wertung doppelt gespeichert wird |
| `ReglementTest` | die Sonderregel für die E-Note und die Platzhalter in den Zahlen |
| `NamenTest` | Umlaute, Akzente, Aufteilung auf die Dateien |
| `EventAuswahlTest` | den Trampolin-Filter und die Entscheidung, was neu geladen wird |

Dazu kommt ein Prüfskript für die fertigen Daten:

```powershell
python .github\scripts\daten_pruefen.py
```

Es rechnet jede einzelne Zeile nach, prüft die Pflichtfelder, die Zuordnung zu
den Dateien, Duplikate und ob `personen.json` und `meta.json` zu den Wertungen
passen.

---

## Was man wissen sollte

### Die Daten sind nur so gut wie sporttech.io

Alles kommt unverändert von dort. Einige Eigenheiten schlagen deshalb durch:

**Vor- und Nachname vertauscht.** Manche Veranstalter tragen die Felder falsch
herum ein. Beim 25. Barmstedt Cup 2026 steht zum Beispiel `GivenName: "Jacobs"`
und `Surname: "Aaron"`. Weil die Suche Wörter in beliebiger Reihenfolge nimmt,
findet man die Person trotzdem – sie erscheint nur unter dem falschen
Anfangsbuchstaben.

**Vereinsnamen sind nicht einheitlich.** Derselbe Verein heißt je nach
Veranstalter „TV Voerde" oder „TV Voerde 1920 e.V.". Personen werden deshalb
über **Nachname, Vorname und Jahrgang** zusammengefasst, nicht über den Verein.
Die Karte zeigt alle Vereinsangaben, neueste zuerst. Ohne diese Regel hätten
696 von 2 932 Personen mehrere Karten statt einer.

Die Kehrseite: zwei verschiedene Personen mit gleichem Namen **und** gleichem
Jahrgang landen auf einer Karte. Weil dann beide Vereine nebeneinander stehen,
fällt es auf.

**Der Jahrgang fehlt manchmal.** Bei 2 193 von 15 258 Zeilen – FIG-Wettkämpfe
veröffentlichen das Geburtsdatum oft nicht. Dann steht `null`.

**Nicht jeder Wettkampf misst alles.** Bei den Deutschen
Hochschulmeisterschaften 2024 gibt es in sechs Wettkämpfen keine Flugzeit, bei
13 Wettkämpfen keine Verlagerungswertung. Dort steht `null` statt einer
vorgetäuschten 0,00.

### Rücksicht auf sporttech.io

Der Crawler macht **höchstens eine Anfrage pro Sekunde**, schickt einen eigenen
User-Agent mit der Adresse dieses Repositories und fragt komprimierte Antworten
an – das spart gegenüber unkomprimiert etwa Faktor 17. Ein vollständiger Lauf
sind 73 Anfragen.

Bitte `pauseZwischenAnfragenMs` nicht verkleinern. Die Daten sind ein
freundliches Angebot von sporttech.io, kein Selbstbedienungsladen.

### Wenn etwas nicht geht

| Problem | Ursache und Abhilfe |
|---|---|
| Website zeigt „Die Daten konnten nicht geladen werden" | `index.html` direkt im Dateisystem geöffnet. Stattdessen `python -m http.server` benutzen, siehe oben |
| Suche findet eine Person nicht | Vielleicht war sie nur bei Wettkämpfen dabei, die nicht erfasst werden – Synchron, Doppel-Mini, Tumbling oder im Ausland |
| Workflow bricht beim Pushen ab | Schreibrechte fehlen, siehe [Einrichten](#berechtigung-für-den-crawler-workflow) |
| Ein Event bleibt auf `"status": "fehler"` | sporttech.io hat dessen Datenbank gerade abgeschaltet. Der nächste Lauf versucht es erneut |
| Suche zeigt Personen, aber keine Wertungen | `personen.json` und die Wertungsdateien passen nicht zusammen, meist alter Browser-Zwischenspeicher. <kbd>Strg</kbd>+<kbd>F5</kbd> |

---

## Projektaufbau

```
.
├── DATENSTRUKTUR.md          wie die API von sporttech.io aufgebaut ist
├── README.md                 diese Datei
│
├── crawler/
│   ├── config.json           Einstellungen
│   ├── pom.xml               Maven
│   ├── samples/              echte, gekürzte API-Antworten für die Tests
│   └── src/
│       ├── main/java/de/wertungssuche/
│       │   ├── Main.java                 Ablauf eines Laufs
│       │   ├── Konfiguration.java        config.json
│       │   ├── HttpLader.java            Tempo, gzip, Wiederholungen
│       │   ├── SporttechApi.java         die beiden Endpunkte
│       │   ├── api/                      die JSON-Struktur von sporttech.io
│       │   ├── WertungsExtraktor.java    Kern: API-Struktur zu flachen Zeilen
│       │   ├── Reglement.java            Sonderregel für die E-Note
│       │   ├── Disziplin.java            TRA, SYN, DMT, TUM
│       │   ├── Durchgang.java            Qualifikation, Finale, ...
│       │   ├── Frames.java               Status einer Übung
│       │   ├── Laenderliste.java         Verein oder Nation?
│       │   ├── Namen.java                Umlaute und Dateiaufteilung
│       │   ├── Zahlen.java               Umrechnung ohne Rundungsfehler
│       │   ├── Wertung.java              das Zielformat
│       │   ├── Datenbestand.java         docs/data schreiben
│       │   ├── Personenindex.java        personen.json
│       │   └── EventStatus.java          events.json
│       └── test/java/de/wertungssuche/   die Tests
│
├── docs/                     das ist die Website (GitHub Pages)
│   ├── index.html
│   ├── style.css
│   ├── app.js
│   └── data/                 die Daten, vom Crawler geschrieben
│
└── .github/
    ├── workflows/
    │   ├── crawl.yml         täglich 03:00 UTC, holt die Wertungen
    │   └── build.yml         bei jedem Push: kompilieren und testen
    └── scripts/
        └── daten_pruefen.py  prüft docs/data
```

---

Die Wertungen stammen von [sporttech.io](https://sporttech.io) und werden dort
veröffentlicht. Dieses Projekt sammelt sie nur ein und macht sie nach Namen
durchsuchbar.
