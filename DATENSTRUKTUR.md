# Datenstruktur von sporttech.io

Stand der Untersuchung: 11.10.2026 · sporttech.io Build `v0.8-2216-g9971366b9`

Alles in diesem Dokument ist an echten Daten überprüft, nicht geraten. Die
untersuchten Beispiel-Events:

| Event | ID | Art |
|---|---|---|
| CBI – Campeonato Brasileiro de Ginástica de Trampolim 2024 | `0aaa0cc5-bc38-4ce6-6dd0-eff5a170a7ed` | national (BRA), alle 4 Disziplinen |
| FIG World Cup Cottbus 2024 | `91f733c2-17e1-402f-74d8-60b7dfbf28b7` | international (FIG) |
| Deutschland Cup 2025 | `4012437c-8d99-493b-6479-27e33db00c2f` | national (GER) |

Gekürzte, aber unveränderte Proben der echten API-Antworten liegen unter
[`crawler/samples/`](crawler/samples/) – mit einer Übersicht, welcher Sonderfall
in welcher Datei steckt.

---

## 1. Die zwei benötigten Endpunkte

### 1.1 Liste **aller** Events

```
GET https://sporttech.io/api/events/?fetch_organizations=true&year=
```

Dieser Endpunkt war in der Aufgabenstellung noch offen. Gefunden habe ich ihn im
JavaScript-Bundle der Explore-Seite (`https://sporttech.io/static/web/script.min.js`),
wo der Pfad als String `"/api/events/?fetch_organizations=true&year="` auftaucht.

Wichtig: **`year=` leer lassen** → der Server liefert dann *alle* Jahre in einer
einzigen Antwort. Mit `year=2024` bekommt man nur dieses Jahr.

Stand 11.10.2026:

| | Anzahl |
|---|---|
| Events insgesamt | 2 775 |
| davon `sport == "Trampoline"` | **2 029** |
| Antwortgröße | 3,3 MB unkomprimiert / ca. 300 KB mit gzip |

Antwortformat – ein Objekt `event`, das nach Event-ID (UUID) gemappt ist:

```json
{
  "event": {
    "4012437c-8d99-493b-6479-27e33db00c2f": {
      "event_id":   "4012437c-8d99-493b-6479-27e33db00c2f",
      "en_name":    "Deutschland Cup 2025",
      "begin_date": "2025-10-05T00:00:00Z",
      "end_date":   "2025-10-05T00:00:00Z",
      "sport":      "Trampoline",
      "rules":      "TRA FIG 2022",
      "updated_at": "2025-10-07T09:12:44.118Z",
      "highlight":  true,
      "organizations": [ { "organization_id": "...", "participation": "host" } ]
    }
  },
  "organization": { "...": { } }
}
```

Für den Crawler relevant: `event_id`, `en_name`, `begin_date`, `end_date`,
`sport`, `rules`, `updated_at` – und `organizations`, siehe gleich.

#### Das Land steht nur in der Organisation

Im Event selbst gibt es **kein** Länderfeld. Deshalb wird
`fetch_organizations=true` gebraucht: die Antwort enthält dann ein zweites
Objekt `organization`, und über `event.organizations[].organization_id` kommt
man an den Ausrichter:

```json
"organization": {
  "a02ce08a-…": {
    "organization_id": "a02ce08a-…",
    "en_name": "Deutscher Turner-Bund",
    "country_id": 276
  }
}
```

`country_id` ist die numerische Länder-ID, die auch `/api/countries/` benutzt –
**276 = Deutschland**. Jedes Event hat genau einen Eintrag mit
`participation: "host"`; weitere Einträge haben `participation: "participant"`.

Damit lassen sich die deutschen Events sauber herausfiltern. Stand 11.10.2026
ergibt das für Trampolin ab dem 01.01.2024:

| Ausrichterland | Events |
|---|---|
| Portugal | 160 |
| Russland | 154 |
| Australien | 148 |
| **Deutschland (276)** | **71** |
| Dänemark | 64 |
| Neuseeland | 56 |
| Schweden | 29 |

Die 71 gelisteten deutschen Events reichen von Vereinsmeisterschaften über Landes- und
Deutsche Meisterschaften bis zum FIG World Cup Cottbus.

#### ⚠️ Die Liste zeigt nicht alle Events

Dieser Endpunkt gibt **nur Events mit `highlight: true` heraus**. Geprüft: alle
2 775 Einträge der Antwort haben dieses Kennzeichen, ausnahmslos.

Events ohne das Kennzeichen fehlen in der Liste vollständig – obwohl ihre
Ergebnisseiten ganz normal öffentlich sind und die API sie auf direkte Nachfrage
herausgibt. Beispiel:

```
https://sporttech.io/events/d613f43d-fb1c-4beb-4770-9e684e2e0a8e/ovs/
```

Das ist die *WAGC / JWM Qualifikation* des DTB vom 06.06.2026 am
Bundesstützpunkt Stuttgart, mit 51 Starterinnen und Startern und 144
veröffentlichten Übungen. In der Eventliste: nicht vorhanden. Direkt abgefragt:

```
GET https://sporttech.io/api/events/d613f43d-fb1c-4beb-4770-9e684e2e0a8e?fetch_organizations=true
→ { "highlight": false, "sport": "Trampoline", "rules": "TRA FIG 2022", … }
```

Alle Filterkriterien treffen zu, nur gelistet ist es eben nicht.

Mit Parametern lässt sich das nicht aufheben – `highlight=false`, `all=true`,
`fetch_all=true` und `organization_id=…` werden samt und sonders ignoriert, die
Antwort bleibt bei denselben 2 775 Events.

#### Der Weg zu den nicht gelisteten Events: über die Organisation

```
GET https://sporttech.io/api/organizations/<ORGANISATIONS-ID>?fetch_events=true
```

Dieser Aufruf liefert **alle** Events einer Organisation, auch die ohne
`highlight`. Der Parameter `fetch_events` stammt aus der Liste der
fetch-Parameter im JavaScript-Bundle.

Die Antwort hat dieselbe Form wie die Eventliste (`event` + `organization`), mit
einem Unterschied: **die Events tragen kein `organizations`-Feld.** Der
Ausrichter ist ja schon durch die Anfrage bestimmt. Wer das übersieht, verliert
genau diese Events wieder am Länderfilter. Als Rückfallebene steht im Feld
`created_by` die anlegende Organisation – in allen geprüften Fällen der
Ausrichter.

Ergebnis des Scans über alle 133 deutschen Organisationen: **43 Events, die in
der Eventliste fehlen.**

#### Was dabei *nicht* geht

Eine Organisation, die **ausschließlich** nicht gelistete Events ausrichtet,
steht selbst in keiner Liste – die Organisationen der Eventliste sind ja nur
die, die von gelisteten Events referenziert werden. Genau das ist beim DTB der
Fall: die Organisation `d51afe92-…` („Deutscher Turner-Bund (DTB)") richtet drei
Events aus, alle drei ohne `highlight`, und taucht deshalb in den 1 048
Organisationen der Eventliste nicht auf.

Ein Verzeichnis der Organisationen gibt es nicht: `/api/organizations/` ohne ID
antwortet mit `405`, und eine Suche wie bei den Personen
(`autocomplete_prefix`) existiert für Organisationen nicht.

**Es bleibt also eine Lücke, die nur von Hand zu schließen ist.** Der Crawler
macht daraus aber einen einmaligen Handgriff: trägt man eine Event-ID einer
solchen Organisation ein, merkt er sich deren Ausrichter in `events.json`
(`ausrichterId`) und durchsucht ihn ab dann in jedem Lauf. Die eine von Hand
eingetragene WAGC-Qualifikation hat so die EM/JEM-Qualifikation desselben
Ausrichters von selbst nachgezogen.

#### So erkennt man Trampolin

Das Feld **`sport`** ist der verlässliche Filter. Vorkommende Werte:

| `sport` | Anzahl |
|---|---|
| **Trampoline** | **2 029** |
| Rhythmic gymnastics | 192 |
| Artistic gymnastics | 179 |
| Acrobatics | 164 |
| Aerobics | 88 |
| Snowboard | 77 |
| TeamGym | 37 |
| Ski Freestyle | 4 |
| Aesthetic group gymnastics | 3 |
| Parkour | 1 |
| Shooting sport | 1 |

Zusätzlich beginnt bei Trampolin-Events das Feld `rules` immer mit `TRA`:

| `rules` | Anzahl |
|---|---|
| TRA FIG 2022 | 1 206 |
| TRA FIG 2017 | 578 |
| TRA FIG | 105 |
| TRA RUS 2017 | 80 |
| TRA DEN | 30 |
| TRA RUS | 19 |
| TRA FIG WAGC | 7 |
| TRA FIG WCh | 4 |

Beide Filter stimmen über alle 2 775 Events **exakt** überein: es gibt kein
Event mit `sport == "Trampoline"` und einem `rules` ohne `TRA`-Prefix, und
keines mit `TRA`-Prefix unter einem anderen `sport`. Das Kürzel „TRA" aus der
Aufgabenstellung steckt also in `rules` – und außerdem im Pfad der
JavaScript-App eines Trampolin-Events (`/static/TRA/script.min.js`).

> **Der Crawler filtert auf `sport == "Trampoline"` und prüft zusätzlich, dass
> `rules` mit `TRA` beginnt.** Doppelt abgesichert, falls sporttech.io eines der
> beiden Felder umbenennt.

Verteilung der Trampolin-Events nach Jahr:

```
2013:   2    2017: 110    2021: 135    2025: 295
2014:  10    2018: 162    2022: 186    2026: 283
2015:  55    2019: 152    2023: 240
2016:  57    2020:  64    2024: 278
```

### 1.2 Komplette Struktur eines Events

```
GET https://sporttech.io/events/<EVENT-ID>/ovs/api/event
    ?fetch_event_competitions=true
    &fetch_competition_stages=true
    &fetch_stage_groups=true
    &fetch_group_performances=true
    &fetch_performance_frames=true
    &fetch_performance_athletes=true
```

`fetch_panels=true` aus der Aufgabenstellung lassen wir weg – das sind die
Kampfrichter-Panels, die wir nicht brauchen, und sie machen die Antwort größer.

Der Endpunkt wird im sporttech-Beispielprojekt per EventSource (SSE) gelesen,
funktioniert aber genauso als ganz normales `GET` und liefert dann einmalig den
kompletten Stand als JSON. Genau das macht unser Crawler.

Typische Größen pro Event: **1,5–2,5 MB unkomprimiert, 80–130 KB mit gzip.**
`Accept-Encoding: gzip` bringt also Faktor ~17 und wird vom Crawler gesetzt.

Antworten tragen einen `ETag` – damit kann man später per `If-None-Match`
billig prüfen, ob sich ein Event überhaupt geändert hat.

Fehlerfälle:

| Situation | Antwort |
|---|---|
| ID ist keine UUID | `400` + Text `Invalid event ID: Invalid UUID string` |
| UUID existiert nicht | `500` + Text `Failed to dial OVS: ...` |

Beides ist **kein JSON**. Der Crawler muss also den Status-Code prüfen und darf
bei einem Fehler nur dieses Event überspringen, nicht den ganzen Lauf abbrechen.

---

## 2. Aufbau der Event-Antwort

Die Antwort hat acht Objekte auf oberster Ebene. Alle außer `Event` sind
**Maps von ID (als String) auf Objekt** – keine Arrays:

```
{
  "Event":        { ... }                 // genau ein Objekt
  "Competitions": { "0": {...}, "1": {...} }
  "Stages":       { "0": {...}, ... }
  "Groups":       { "0": {...}, ... }
  "Performances": { "0": {...}, ... }
  "Frames":       { "0": {...}, ... }
  "Athletes":     { "0": {...}, ... }
  "Panels":       { ... }                 // nur mit fetch_panels=true
}
```

Die Hierarchie wird über ID-Listen verknüpft, man muss sie selbst zusammensetzen:

```
Event
 └─ Competitions[]        Wettkampfklasse, z. B. "AK 13/14 (weiblich)"
     └─ Stages[]          Durchgangsart, z. B. Qualifikation / Finale
         └─ Groups[]      Riegen innerhalb eines Stage (nur Einteilung!)
             └─ Performances[]   ein Starter (oder Synchronpaar) in diesem Stage
                 ├─ Athletes[]   1 Athlet (TRA/DMT/TUM) oder 2 (Synchron)
                 └─ Frames[]     die einzelnen Übungen / Durchgänge
```

Richtungen beider Art existieren: `Stage.CompetitionID`, `Group.StageID`,
`Performance.GroupID`, `Frame.PerformanceID` zeigen jeweils nach oben.

### ⚠️ Felder können in einzelnen Objekten fehlen

Das ist die wichtigste Falle. Die API serialisiert sparsam: einzelne Objekte
lassen Felder einfach weg. Gemessen:

| Datei | fehlende Felder |
|---|---|
| CBI 2024 (900 Frames) | 1× `TimeTMS`, 1× `DisplacementT` |
| Cottbus 2024 (1132 Frames) | 2× `TimeTMS`, **1× `DPenaltyT`** |
| Deutschland Cup 2025 (1372 Frames) | 2× `DifficultyT`, **2× `DPenaltyT`**, 1× `PenaltyT` |

Und: in Cottbus 2024 fehlt `DateOfBirth` bei **allen 167** Athleten – FIG-Events
veröffentlichen das Geburtsdatum oft nicht.

> **Konsequenz für den Java-Crawler:** niemals primitive Typen (`int`, `double`)
> für API-Felder verwenden, sondern `Integer`/`Double`, und jeden Zugriff mit
> einem Standardwert absichern (`0` für Punktefelder, `null` für Angaben wie
> Jahrgang). Sonst wirft Gson entweder `NullPointerException` oder setzt still
> eine `0`, wo `null` richtig wäre.

---

## 3. Die Objekte im Einzelnen

### 3.1 `Event`

| Feld | Beispiel | Bedeutung |
|---|---|---|
| `UUID` | `0aaa0cc5-bc38-…` | Event-ID, gleich der ID aus der Eventliste |
| `Title` | `CBI - CAMPEONATO BRASILEIRO …` | **Eventname** |
| `Subtitle` | `Belo Horizonte / MG, 26 a 30 de junho de 2024` | Ort + Zeitraum (Freitext) |
| `StartDate` / `EndDate` | `2024-06-26T00:00:00Z` | Zeitraum |
| `RulesCode` | `TRA.FIG.2022.2024-06-26` | Reglement; Prefix `TRA.` = Trampolin |
| `Competitions` | `[0,1,2,…,22]` | IDs der Wettkämpfe |
| `Participants_G` | `85` | Anzahl Teilnehmer |
| `UseFullDOB` | `false` | `false` → in `DateOfBirth` ist nur das Jahr echt |
| `ShowAthleteCountryFlag` | `true` | **nicht** als Nation-Erkennung brauchbar (s. 3.6) |
| `HideOnlineResults` | `false` | wenn `true`, sind die Ergebnisse nicht öffentlich |

### 3.2 `Competitions` – der Wettkampf

| Feld | Beispiel | Bedeutung |
|---|---|---|
| `ID` | `0` | |
| `Title` | `AK 13/14 (weiblich)` | **Wettkampfname** (Freitext des Veranstalters) |
| `Discipline` | `0` | **Disziplin, siehe Tabelle unten** |
| `AgeGroup` | `SEN`, `JUN`, `AG1`, `""` | Altersklasse, oft leer |
| `SexCategory` | `male`, `female`, `""` | oft leer |
| `Stages` | `[0, 29]` | IDs der Durchgänge in Reihenfolge |

#### `Competition.Discipline` – hier wird Trampolin von DMT/Tumbling getrennt

Quelle: `TRA.shadow.CompetitionTypes` im Bundle `/static/TRA/script.min.js`.

| Wert | Code | Bedeutung |
|---|---|---|
| **0** | **TRA** | **Trampolin Einzel** |
| **1** | **SYN** | **Synchronspringen** |
| 2 | DMT | Doppel-Minitrampolin |
| 3 | TUM | Tumbling |
| 4 | TEAM | Team-Mehrkampf (dann steht die echte Disziplin in `Performance.Discipline`) |

> ⚠️ Diese Nummern gelten **nur** innerhalb von Trampolin-Events. Im
> Gerätturnen bedeutet `Discipline` etwas völlig anderes (1 = Boden,
> 2 = Sprung, …). Die Nummer allein sagt also nicht, ob es Trampolin ist –
> dafür ist der `sport`-Filter aus der Eventliste zuständig.

Beispiel CBI 2024: 23 Competitions, davon 6× TRA, 6× SYN, 6× DMT, 5× TUM.

### 3.3 `Stages` – der Durchgang (Qualifikation / Finale)

| Feld | Beispiel | Bedeutung |
|---|---|---|
| `ID` | `0` | wird auch für den **Ergebnis-Link** gebraucht |
| `CompetitionID` | `0` | |
| `Kind` | `0` | **Art des Durchgangs, siehe Tabelle** |
| `PerfomanceFramesLimit` | `2` | **wie viele Übungen pro Starter zählen** (Tippfehler „Perfomance" ist im Original so!) |
| `Groups` | `[0]` | IDs der Riegen |
| `EventTitle_G` | `CBI - …` | Eventname, im Stage gespiegelt |
| `EventSubtitle_G` | `Belo Horizonte / MG …` | |
| `LastEdit_G` | `2024-06-29T18:00:05-03:00` | letzte Änderung |
| `InProgress_G` | `false` | läuft gerade |
| `FinishedFrames_G` / `TotalFrames_G` | `20` / `20` | Fortschritt |

**Stages haben keinen Titel.** Der Name des Durchgangs muss aus `Kind` gebildet
werden. Quelle: `TRA.shadow.StageKinds`:

| `Kind` | sporttech-Name | Name auf unserer Seite |
|---|---|---|
| 0 | Qualification | Qualifikation |
| 1 | QuarterFinal | Viertelfinale |
| 2 | SemiFinal | Halbfinale |
| 3 | Final | Finale |
| 4 | TeamSemiFinal | Team-Halbfinale |
| 5 | TeamFinal | Team-Finale |
| 6 | Qualification2 | Qualifikation II |
| 7 | Final2 | Finale 2 |
| 8 | Final1 | Finale 1 |

Beobachtet: CBI 2024 benutzt `Kind` 0 (23×), 3 (11×), 7 (2×) und 8 (2×); der
FIG World Cup Cottbus 2024 benutzt zusätzlich `Kind` 6 (zweite Qualifikation).

#### Durchgang und Übung sind zwei verschiedene Dinge

Ist `PerfomanceFramesLimit > 1`, besteht der Durchgang aus mehreren Übungen
pro Starter. Eine Qualifikation hat fast immer zwei.

**Diese Übungsnummer gehört nicht in den Durchgangsnamen.** Sie steht in
unseren Daten im eigenen Feld `uebung`. Der erste Entwurf hängte sie an –
„Qualifikation 1", „Qualifikation 2" – und das war gleich doppelt falsch:

1. **Im Trampolinturnen meint „Qualifikation 2" üblicherweise den zweiten
   Durchgang, also das Halbfinale.** An den echten Daten sieht man den
   Unterschied sofort. FIG World Cup Cottbus 2024, Wettkampf „Men":

   | Durchgang | Starter |
   |---|---|
   | Qualifikation, Übung 1 | 89 |
   | Qualifikation, Übung 2 | 89 |
   | `Kind 6` – Qualifikation II | 24 |
   | Finale | 8 |

   Die zwei Übungen der Qualifikation haben dieselben 89 Starter – es wird
   nicht gesiebt. Gesiebt wird erst beim nächsten *Durchgang*.

2. **Es kollidierte mit echten Durchgangsnamen.** `Kind 8` heißt „Finale 1".
   Ein Finale (`Kind 3`) aus drei Übungen hätte ebenfalls „Finale 1",
   „Finale 2", „Finale 3" ergeben – genau das kommt beim Wölfe Open
   13.09.2025 vor. Zwei verschiedene Sachverhalte unter demselben Namen.

#### Ist `Kind 6` ein Halbfinale?

Funktional meistens ja, aber nicht immer – deshalb heißt es bei uns weiter
„Qualifikation II" und nicht „Halbfinale":

| Event | Qualifikation | Kind 6 | Finale |
|---|---|---|---|
| FIG World Cup Cottbus 2024, Men | 89 | **24** | 8 |
| FIG World Cup Cottbus 2025, Men | 33 | **16** | 8 |
| Schülerliga erster Vorkampf, Gruppe 1 | 20 | **20** | 20 |

Bei den World Cups ist `Kind 6` klar das Halbfinale. Bei der Schülerliga wird
überhaupt nicht gesiebt, da ist es schlicht ein zweiter Durchgang. Außerdem
hat sporttech.io mit `Kind 2` eine eigene Durchgangsart „SemiFinal", die
9 Wettkämpfe im Datenbestand auch benutzen. Beides auf „Halbfinale"
abzubilden würde zwei unterschiedliche Angaben der Quelle gleich aussehen
lassen.

### 3.4 `Groups` – die Riege

```json
{ "ID": 0, "StageID": 0, "Performances": [104,105,…], "Time": "" }
```

Nur eine Einteilung in Riegen/Gruppen innerhalb eines Durchgangs, **kein**
eigener Durchgang. Für die Wertungen ist die Gruppe inhaltlich irrelevant – man
braucht sie nur, um von `Stage` an die `Performances` zu kommen.

### 3.5 `Performances` – ein Starter in einem Durchgang

| Feld | Beispiel | Bedeutung |
|---|---|---|
| `ID` | `105` | |
| `GroupID` | `0` | |
| `Athletes` | `[13]` bzw. `[106,107]` | **1 Athlet, bei Synchron 2** |
| `Frames` | `[420,421,422,423]` | IDs der Übungen; nur die ersten `PerfomanceFramesLimit` zählen, der Rest sind leere Platzhalter |
| `MarkTTT_G` | `58055` | **Gesamtergebnis des Durchgangs** × 1000 |
| `Rank_G` | `1` | **Platz** in diesem Durchgang |
| `Ranked` | `true` | ob überhaupt gewertet wird (Gäste: `false`) |
| `CountingFrame_G` | `420` | welche Übung in die Wertung eingeht |
| `Mark17TTT_G` | `46500` | Punktzahl nach dem **alten** Reglement 2017 – **nicht verwenden** |
| `Discipline` | `0` | nur bei `Competition.Discipline == 4` (Team) aussagekräftig |
| `Team`, `TeamRank_G`, `TeamMarkTTT_G` | | Mannschaftswertung |

Wie `MarkTTT_G` zustande kommt, hängt von den `Stage.CalcOptions` ab:

* CBI 2024, Qualifikation TRA: Rayan Dutra hat 58,055 und 6,260 geturnt →
  `Performance.MarkTTT_G = 58055`. Hier zählt die **beste** Übung.
* CBI 2024, DMT: 21,500 + 20,900 → `MarkTTT_G = 42400`. Hier wird **summiert**.

Deshalb rechnet der Crawler die Durchgangssumme **nicht selbst**, sondern
übernimmt `Performance.MarkTTT_G` als das, was sporttech.io als offizielles
Durchgangsergebnis ausweist.

### 3.6 `Athletes` – die Person

| Feld | Beispiel | Bedeutung |
|---|---|---|
| `ID` | `10` | nur innerhalb des Events eindeutig! |
| `GivenName` | `Adrian` | **Vorname** |
| `Surname` | `Thomson` | **Nachname** |
| `DateOfBirth` | `2006-01-01T00:00:00Z` | **kann ganz fehlen**; bei `Event.UseFullDOB == false` ist nur das **Jahr** echt, Tag/Monat sind Platzhalter `01-01` |
| `Representing` | `TG Münster 1862` **oder** `GER` | **Verein *oder* Nation – siehe unten** |
| `Representing2` | `""` | zweite Zuordnung, in allen untersuchten Events leer |
| `Sex` | `male`, `female`, `""` | oft leer |
| `Level` | `Elite`, `""` | Leistungsklasse, oft leer |
| `Bib`, `ExternalID`, `GUID`, `PhotoURL` | meist leer | |

#### Verein oder Nation? – das gleiche Feld, zwei Bedeutungen

`Representing` enthält je nach Veranstalter etwas anderes:

| Event | `Representing`-Werte |
|---|---|
| Deutschland Cup 2025 | `TG Münster 1862`, `SV Barnstorf 1967 e.V.`, `MTV Bad Kreuznach 1877 e.V.` → **Vereine** |
| FIG World Cup Cottbus 2024 | `GER`, `CHN`, `FRA`, `AIN`, `USA` → **Nationen** |
| CBI 2024 (Brasilien) | `CAP-SP`, `MTC-MG`, `PMC-AGTC-MG` → **Vereine mit Bundesstaat-Kürzel** |

`Event.ShowAthleteCountryFlag` hilft nicht: das steht in *allen* drei Events auf
`true`, auch beim Deutschland Cup mit Vereinsnamen. `Representing2` ist überall
leer.

**Lösung:** der Crawler lädt einmal pro Lauf die offizielle Länderliste

```
GET https://sporttech.io/api/countries/
→ { "country": { "276": { "alpha3": "DEU", "ioc": "GER", "en_name": "Germany" }, … } }
```

und entscheidet dann datengetrieben:

* `Representing` (getrimmt, großgeschrieben) ist ein bekannter `ioc`- oder
  `alpha3`-Code → `nation` = dieser Code, `verein` = `null`
* sonst → `verein` = `Representing`, `nation` = `null`

Ein Fallback mit der eingebauten Liste greift, falls `/api/countries/` mal nicht
erreichbar ist.

> ⚠️ **`Athlete.ID` ist nur innerhalb eines Events eindeutig.** Es gibt keine
> globale Athleten-ID (`GUID` und `ExternalID` sind in allen untersuchten
> Events leer). Personen müssen über Name + Jahrgang + Verein/Nation
> zusammengeführt werden – genau wie in der Aufgabenstellung vorgesehen.

### 3.7 `Frames` – die einzelne Übung, hier stehen die Wertungen

Das ist das Objekt mit den Einzelwertungen. Alle Zahlen sind **Ganzzahlen mit
festem Faktor**, nicht Kommazahlen.

| Feld | Beispiel | Umrechnung | Bedeutung |
|---|---|---|---|
| `State` | `3` | – | **Nur `3` = veröffentlicht zählt**, siehe unten |
| `SubState` | `0` | – | `1`=DNS, `2`=BLOCKED, `3`=FSF, `4`=DNF, `5`=DSQ, `6`=DQB, `7`=DNC |
| `MarkTTT_G` | `58055` | `/1000` | **Gesamt der Übung** → 58,055 |
| `DifficultyT_G` | `180` | `/10` | **D** – Schwierigkeit → 18,0. **`−1` heißt „nicht gesetzt"** |
| `ETotalT_G` | `148` | *besonders* | **E** – Ausführung → 14,8. **Nicht immer `/10`, siehe unten** |
| `TimeTMS_G` | `15955` | `/1000` | **T** – Flugzeit in Sekunden → 15,955 |
| `Displacement_G` | `930` | `/100` | **H / HD** – horizontale Verlagerung → 9,30 |
| `PenaltyT` | `0` | `/10` | **Abzug** (Strafe) |
| `DPenaltyT` | `120` | `/10` | **zusätzlicher Abzug** → 12,0 |
| `BonusT` | `0` | `/10` | Bonus |
| `PerformanceID` | `105` | – | Rückverweis |
| `NElements` | `10` | – | Anzahl Elemente |
| `LastPublishTime_G` | `2024-06-29T17:29:49-03:00` | – | **Zeitpunkt der Veröffentlichung → unser Datum** |
| `Discipline_G` | `0` | – | Disziplin, gespiegelt aus der Competition |
| `Mark17TTT_G` | `46500` | `/1000` | altes Reglement 2017 – **nicht verwenden** |
| `Deducts_G`, `ETotalsT_G`, `RefMedian_G` | Maps | – | Einzelnoten je Kampfrichter |
| `ChangeLog` | Text | – | Protokoll; enthält die Rohwerte als Klartext |

#### `State` – nur veröffentlichte Übungen übernehmen

Quelle: `model/constants/frameStates.js` im sporttech-Beispielprojekt.

| Wert | Bedeutung |
|---|---|
| 0 | NOT_STARTED – leerer Platzhalter |
| 1 | STARTED |
| 2 | FINISHED |
| **3** | **PUBLISHED – nur diese übernehmen** |
| 4 | FAILED (DNS/DNF/DSQ, Details in `SubState`) |
| 5 | EDITING |

Im CBI 2024: 900 Frames, davon 342 mit `State == 3`. Der Rest sind größtenteils
nie benutzte Platzhalter (jede Performance hat 4 Frame-Slots, auch wenn nur 2
geturnt werden).

#### Die Punkteformel

```
Gesamt = D + E + T + H + Bonus − Abzug − zusätzlicher Abzug
```

also

```
MarkTTT_G/1000 = DifficultyT_G/10 + E + TimeTMS_G/1000
                 + Displacement_G/100 + BonusT/10
                 − PenaltyT/10 − DPenaltyT/10
```

wobei `E` der einzige Wert mit einer Sonderregel ist – siehe das nächste
Kapitel. Bei den Reglements 2017 und 2022 ist es einfach `ETotalT_G/10`.

**Geprüft an allen 2 073 veröffentlichten Übungen aus fünf Events** (CBI 2024,
World Cup Cottbus 2024, Deutschland Cup 2025, 3. Wölfe Open 2024, Offene Kölner
Pokalwettkämpfe 2026) sowie an allen **15 258 Zeilen** des fertigen
Datenbestands – überall stimmt die Summe auf die dritte Dezimalstelle.

Gegenprobe Rayan Dutra, CBI 2024, Qualifikation TRA, Übung 1:

```
D 18,0  +  E 14,8  +  T 15,955  +  H 9,30  =  58,055   ✓ = MarkTTT_G 58055
```

Gegenprobe Vitoria Cunha, CBI 2024, Tumbling, Übung 1 (Frame 32):

```
D 0,0  +  E 15,8  −  DPenalty 12,0  =  3,80            ✓ = MarkTTT_G 3800
```

Dieses zweite Beispiel ist der Grund, warum `DPenaltyT` mit muss: die
Beispiel-Erweiterung `vmixLivesportTRA.js` von sporttech wertet nur `PenaltyT`
aus und kommt bei solchen Übungen auf das falsche Ergebnis (15,80 statt 3,80).

Der JUnit-Test aus Schritt 5 prüft die Formel gegen die gespeicherten
Beispieldateien.

#### Die E-Note ist der eine Wert mit einer echten Sonderregel

`ETotalT_G / 10` stimmt bei den meisten Events – aber nicht bei allen. Bei 200
von 15 258 Zeilen des fertigen Datenbestands ging die Summe damit nicht auf.

Beispiel: Offene Kölner Pokalwettkämpfe 2026, Wettkampf WK13, erste Übung von
Elia Altinova (Frame 312):

```
MarkTTT_G      = 28100   ->  Gesamt   28,100
DifficultyT_G  =     0   ->  D         0,0
TimeTMS_G      =  8600   ->  T         8,600
Displacement_G =     0   ->  H         0,00
ETotalT_G      =    65   ->  E = ?
```

Damit die Summe aufgeht, muss E = **19,5** sein, also `65 × 3 / 10` und nicht
`65 / 10`.

Die maßgebliche Regel steht in sporttechs eigener Anzeige-Logik im Bundle
`/static/TRA/script.min.js`:

```js
M.filter("EScore", function(f, m) {
    var s = <Stage des Frames>;
    var subtype = common.filters.getSubtype(m.Event.RulesCode);
    if (subtype != "2017" && subtype != "2022") {
        return ETotalFilter(EMultipliedFilter(f), s);   // EMultiplied = ETotalT_G * 3
    }
    if (f.Discipline_G != SYN) { return ETotalFilter(f.ETotalT_G, s); }
    return SynETotalFilter(f.ETotalT_G);
});

M.filter("ETotal", function(n, s) {
    if (hasCalcOption(s, CalcCroppedAvg) || hasCalcOption(s, CalcAvgX2)
            || hasCalcOption(s, CalcAvgX3)) {
        return formatDDD(n);     // n / 1000
    }
    return formatD(n);           // n / 10
});

getSubtype = function(rulesCode) {
    var parts = rulesCode.split(".");
    if (parts.length != 4) { return; }
    return parts[2];
};
```

Also:

1. **Subtyp bestimmen:** `Event.RulesCode` an den Punkten zerlegen. Nur wenn
   genau vier Teile herauskommen, ist der dritte der Subtyp.
   `TRA.FIG.2022.2024-06-26` → `"2022"`.
2. **Faktor:** bei Subtyp `2017` oder `2022` mal 1, bei **jedem anderen** mal 3.
3. **Teiler:** 10 – außer der Durchgang hat eine der Rechenoptionen
   `CalcCroppedAvg` (3), `CalcAvgX2` (16) oder `CalcAvgX3` (17) in
   `Stage.CalcOptions`, dann 1000.

Beim Kölner Event ist der `RulesCode` übrigens `TRA.FIG..2026-06-28` – vier
Teile, aber der dritte ist **leer**. Damit greift Punkt 2 und der Faktor ist 3.

Mit dieser Regel stimmen alle 15 258 Zeilen des Datenbestands. Die
Rechenoptionen sind in `TRA.shadow.CalcKinds` im Bundle definiert; umgesetzt ist
das Ganze in `crawler/src/main/java/de/wertungssuche/Reglement.java`.

#### `DifficultyT_G == -1` heißt „keine D-Note"

sporttech.io benutzt **−1 als Marker für „nicht gesetzt"** – man sieht das auch
bei Feldern wie `DefaultNElements` oder `JudgesPanelID`. Bei der D-Note kommt
das wirklich vor: Frame 1016 des 3. Wölfe Open 2024 hat
`DifficultyT_G == -1`, und das Gesamtergebnis rechnet mit 0.

Wichtig: eine D-Note von echten **0,0** gibt es daneben auch, und zwar häufig
(Anfängerübungen in Nachwuchswettkämpfen). Die darf nicht mit dem Marker
verwechselt werden. In unseren Daten wird aus −1 ein `null`, aus 0 eine 0,0.

#### Sonderfall Synchronspringen (`Discipline == 1`)

Beim Synchron gelten zwei andere Umrechnungsfaktoren:

| Feld | TRA / DMT / TUM | **SYN** |
|---|---|---|
| `ETotalT_G` | `/10` | **`/1000`** |
| `TimeTMS_G` | `/1000` | **`2 × /100`** |
| `DifficultyT_G` | `/10` | `/10` |
| `Displacement_G` | `/100` | `/100` |

Gegenprobe Isa Laguna / Sofia Borges, CBI 2024, SYN Junior B:

```
D 3,5  +  E 7,05  +  T 18,08  +  H 9,60  =  38,23      ✓ = MarkTTT_G 38230
```

Diese Faktoren stehen so auch in `formatRoutineField()` in
`extensions/vmixLivesportTRA.js` des sporttech-Beispielprojekts.

#### DMT und Tumbling

Dort gibt es keine Flugzeit und keine Verlagerung: `TimeTMS_G` und
`Displacement_G` sind immer `0`. In unserem flachen Format werden `t` und `h`
deshalb `null` – nicht `0`, damit auf der Website keine „0,00" steht, wo es die
Wertung gar nicht gibt.

---

## 4. Woher kommen Datum und Link?

**Datum:** `Frame.LastPublishTime_G` – der Zeitpunkt, an dem die Wertung
freigegeben wurde, inklusive Zeitzone des Veranstalters. Das ist pro Durchgang
genau richtig, weil ein Event über mehrere Tage läuft. Ist der Wert leer oder
`0001-01-01T00:00:00Z`, fällt der Crawler auf `Event.StartDate` zurück.

**Link:** die öffentliche Ergebnisseite eines Durchgangs ist

```
https://sporttech.io/events/<EVENT-ID>/ovs/stages/<STAGE-ID>/results
```

Gefunden in der Routing-Tabelle des TRA-Bundles (`/stages/:stageID/results`).
Weitere nutzbare Routen derselben App: `/stages/:stageID/startlist`,
`/stages/:stageID`, `/event/:discipline` (mit `TRA`, `SYN`, `DMT`, `TUM`).

---

## 5. Zuordnung zum gewünschten flachen Format

| unser Feld | Quelle |
|---|---|
| `vorname` | `Athlete.GivenName` |
| `nachname` | `Athlete.Surname` |
| `jahrgang` | `Athlete.DateOfBirth` → erste 4 Zeichen; **`null`, wenn Feld fehlt** |
| `verein` | `Athlete.Representing`, wenn **kein** Ländercode |
| `nation` | `Athlete.Representing`, wenn es ein `ioc`/`alpha3`-Code ist |
| `event` | `Event.Title` |
| `eventId` | `Event.UUID` |
| `datum` | `Frame.LastPublishTime_G` (Fallback `Event.StartDate`) |
| `disziplin` | `Competition.Discipline` → `Trampolin` / `Synchron` / `Doppel-Mini` / `Tumbling` |
| `wettkampf` | `Competition.Title` |
| `durchgang` | `Stage.Kind` → Name, **ohne** Übungsnummer |
| `uebung` | Position des Frames in `Performance.Frames`, 1-basiert |
| `uebungen` | `Stage.PerfomanceFramesLimit` |
| `platz` | `Performance.Rank_G` |
| `d` | `Frame.DifficultyT_G / 10`; bei `−1` → `null` |
| `e` | `Frame.ETotalT_G` mit der Reglement-Regel von oben (SYN: `/1000`) |
| `h` | `Frame.Displacement_G / 100`; bei 0 → `null` (DMT/TUM und Wettkämpfe ohne HD-Wertung) |
| `t` | `Frame.TimeTMS_G / 1000`; bei 0 → `null` (SYN: `2 × /100`) |
| `abzug` | `(Frame.PenaltyT + Frame.DPenaltyT) / 10` |
| `gesamt` | `Frame.MarkTTT_G / 1000` |
| `gesamtDurchgang` | `Performance.MarkTTT_G / 1000` – Ergebnis des gesamten Durchgangs |
| `link` | `https://sporttech.io/events/<eventId>/ovs/stages/<stageId>/results` |

Warum `h` und `t` bei 0 zu `null` werden: es gibt Wettkämpfe, die weder
Flugzeit noch Verlagerung messen. Bei den Deutschen Hochschulmeisterschaften
2024 ist `TimeTMS_G` in allen sechs Wettkämpfen durchgehend 0, bei 13
Wettkämpfen des Datenbestands gilt dasselbe für `Displacement_G`. Eine „0,00"
auf der Website würde dort eine Wertung vorgeben, die es gar nicht gibt.

Der Preis dafür: bei einer abgebrochenen Übung, deren Flugzeit wirklich
praktisch 0 ist, steht ebenfalls `null`. Das betrifft eine Handvoll Zeilen und
ist der bessere Kompromiss.

Zwei Felder habe ich gegenüber der Aufgabenstellung **ergänzt**, weil die echten
Daten es verlangen:

* **`disziplin`** – in einem Trampolin-Event stecken auch DMT-, Tumbling- und
  Synchron-Wettkämpfe. Ohne dieses Feld kann die Website sie nicht trennen,
  und `wettkampf` (z. B. „AK 13/14 (weiblich)") sagt es nicht.
* **`gesamtDurchgang`** – eine Qualifikation besteht aus zwei Übungen mit je
  eigenen D/E/T/H-Werten. Eine Zeile pro Übung ist die richtige Auflösung;
  dann braucht man zusätzlich die Summe/Bestleistung des Durchgangs, zu der
  `platz` gehört.

---

## 6. Mengen und die gebaute Dateistruktur

### Entscheidung über den Umfang

Weltweit gibt es 2 029 Trampolin-Events. Gemessen an echten Events ergeben die
etwa **524 Zeilen pro Event**, also rund **1,06 Millionen Zeilen** bzw. 600 MB
mit Einrückung. Das trägt die Aufteilung nach dem ersten Nachnamen-Buchstaben
nicht, weil die Verteilung zu schief ist (S 14,2 %, M 11,3 %) – `s.json` wären
rund 40 MB in einer Datei.

Gesammelt wird deshalb ein klar abgegrenzter Ausschnitt:

* **nur Events, die in Deutschland ausgerichtet werden** (`country_id == 276`
  der ausrichtenden Organisation)
* **nur ab dem 01.01.2024**
* **nur Trampolin Einzel** (`Competition.Discipline == 0`); Synchron,
  Doppel-Minitrampolin und Tumbling fallen weg
* zusätzlich **einzeln nachladbare Events** für wichtige Wettkämpfe im Ausland,
  siehe `README.md`

### Was dabei herauskommt

Stand 11.10.2026, ein vollständiger Lauf über alle 78 deutschen Events
(71 gelistete plus 7 über den Organisationsscan gefundene):

| | |
|---|---|
| Events geladen | 71, davon 0 mit Fehler |
| übersprungen (falsche Disziplin) | 454 Durchgänge |
| übersprungen (nicht veröffentlicht) | 1 044 Übungen |
| **Wertungszeilen** | **15 448** |
| **Personen** | **3 981** |
| Zeitraum | 2024: 3 781 · 2025: 4 922 · 2026: 6 555 |
| Gesamtgröße `docs/data/` | 10,2 MB (davon 0,8 MB Suchindex) |
| größte Datei | `s.json` mit 1,4 MB |
| HTTP-Anfragen | rund 210 (Eventliste, Länderliste, 133 Organisationen, 78 Events) |
| Laufzeit | rund 2 Minuten bei einer Anfrage pro Sekunde |

Damit funktioniert die ursprünglich geplante, einfache Aufteilung: die Website
lädt für eine Suche genau eine Datei, im schlechtesten Fall 1,4 MB – und
GitHub Pages liefert die komprimiert aus.

### Die Dateien unter `docs/data/`

| Datei | Inhalt |
|---|---|
| `a.json` … `z.json` | die Wertungen, nach dem ersten Buchstaben des Nachnamens in der Vergleichsform (Ä→a, Ö→o, Ü→u, Akzente entfernt) |
| `other.json` | alles, was nicht mit a–z beginnt – zum Beispiel kyrillische Namen |
| `events.json` | der Merkzettel des Crawlers: welche Events verarbeitet wurden, mit Status, Datum und Anzahl der Wertungen |
| `meta.json` | Stand der Daten und Kennzahlen; die Website zeigt daraus das Datum der letzten Aktualisierung |

Sortiert wird innerhalb einer Datei nach Nachname, Vorname, Datum absteigend.
Das hält die Git-Diffs klein: ein neues Event fügt an wenigen Stellen ein,
statt die ganze Datei umzusortieren.

Dass dieselbe Wertung nicht zweimal auftaucht, sichern zwei Dinge ab:

1. Beim erneuten Laden eines Events werden **zuerst alle alten Zeilen dieses
   Events entfernt** und dann die neuen eingefügt. So verschwindet eine
   zurückgezogene oder korrigierte Wertung wirklich, statt als Geisterzeile
   stehen zu bleiben.
2. Ein eindeutiger Schlüssel aus `eventId`, Durchgang, Wettkampf und
   normalisiertem Namen plus Jahrgang. Der Name wird normalisiert, damit eine
   korrigierte Schreibweise („Mueller" → „Müller") nicht als zweite Person
   durchgeht.
