# Beispieldaten von sporttech.io

Echte, nur gekürzte Antworten der sporttech.io-API. Sie dienen als Grundlage für
die JUnit-Tests und als Nachschlagewerk beim Lesen von
[`../../DATENSTRUKTUR.md`](../../DATENSTRUKTUR.md).

Gekürzt heißt: ganze Objekte wurden weggelassen, die behaltenen Objekte sind
**Byte für Byte unverändert** – inklusive der Kampfrichter-Einzelnoten
(`Deducts_G`, `ETotalsT_G`, `RefMedian_G`) und des `ChangeLog`. Nur
`Event.InfoLong` und `Event.InfoShort` (HTML-Blöcke ohne Bezug zu den
Wertungen) sind geleert, und `Event.Competitions` ist auf die behaltenen
Wettkämpfe gekürzt.

---

## `event-cbi-2024-gekuerzt.json` — der Haupt-Testfall

Quelle:

```
https://sporttech.io/events/0aaa0cc5-bc38-4ce6-6dd0-eff5a170a7ed/ovs/api/event
  ?fetch_event_competitions=true&fetch_competition_stages=true
  &fetch_stage_groups=true&fetch_group_performances=true
  &fetch_performance_frames=true&fetch_performance_athletes=true
```

Event: *CBI – Campeonato Brasileiro Loterias Caixa de Ginástica de Trampolim
(Elite e Junior) 2024*, Belo Horizonte, 26.–30.06.2024.

Behalten wurden die Wettkämpfe 0 (TRA), 9 (SYN), 16 (DMT) und 18 (TUM), davon
alle Durchgänge, und je Riege die ersten drei Starter plus die beiden Starter
mit einer gescheiterten Übung.

| | Original | hier |
|---|---|---|
| Competitions | 23 | 4 |
| Stages | 38 | 8 |
| Groups | 38 | 8 |
| Performances | 225 | 25 |
| Frames | 900 | 100 |
| Athletes | 85 | 26 |

Was dieses Sample abdeckt:

| Fall | konkret enthalten |
|---|---|
| alle vier Disziplinen | `Competition.Discipline` 0, 1, 2, 3 |
| Synchron mit zwei Athleten je Performance | 6 Performances mit `Athletes.length == 2` |
| Durchgangsarten | `Stage.Kind` 0 (Qualifikation), 3 (Finale), 8 (Finale 1), 7 (Finale 2) |
| eine *und* zwei Übungen pro Starter | `PerfomanceFramesLimit` 1 und 2 |
| nur veröffentlichte Übungen übernehmen | 33× `State == 3`, 65× `State == 0` (leere Platzhalter), 2× `State == 4` |
| gescheiterte Übungen | Frame 324 mit `SubState == 1` (DNS), Frame 453 mit `SubState == 3` (FSF) |
| zusätzlicher Abzug | Frames 32, 36, 40 mit `DPenaltyT == 120` → 12,0 Punkte Abzug |
| `Representing` = **Verein** | `CAP-SP`, `MTC-MG`, `PMC-AGTC-MG`, `ADESVA-RS`, `ITOP-MG`, `PMP-RJ` |
| Jahrgang statt vollem Geburtsdatum | `Event.UseFullDOB == false`, `DateOfBirth` endet auf `-01-01` |
| Durchgangssumme ≠ Summe der Übungen | TRA-Qualifikation wertet die **beste** Übung, DMT **summiert** |

---

## `event-cottbus-2024-gekuerzt.json` — der Gegentest

Quelle: derselbe Endpunkt für
`91f733c2-17e1-402f-74d8-60b7dfbf28b7` — *FIG World Cup Cottbus 2024*.

Sehr klein gehalten (2 Wettkämpfe, 3 Durchgänge, 6 Performances, 24 Frames,
8 Athleten), aber genau auf die Fälle zugeschnitten, die im CBI-Sample **nicht**
vorkommen:

| Fall | konkret enthalten |
|---|---|
| `Representing` = **Nation** | `GER`, `BRA`, `GBR`, `JPN`, `KAZ`, `USA` |
| `DateOfBirth` fehlt komplett | 0 von 8 Athleten haben das Feld → `jahrgang` muss `null` werden |
| einzelne Felder fehlen in einzelnen Frames | 1× ohne `DPenaltyT`, 2× ohne `TimeTMS` |
| zweite Qualifikation als eigener Durchgang | `Stage.Kind == 6` (Qualification2) |

Der Fall „Feld fehlt im Objekt" ist der wichtigste: er bringt jeden Parser zu
Fall, der primitive Typen oder direkte Feldzugriffe benutzt.

---

## `eventliste-gekuerzt.json`

Quelle:

```
https://sporttech.io/api/events/?fetch_organizations=true&year=
```

Die Originalantwort hat 3,3 MB mit 2 775 Events, davon 2 029 Trampolin. Hier
sind fünf enthalten, damit der Trampolin-Filter testbar ist:

| Event | `sport` | `rules` | wird gecrawlt? |
|---|---|---|---|
| Deutschland Cup 2025 | Trampoline | TRA FIG 2022 | ja |
| FIG World Cup 2024 Cottbus | Trampoline | TRA FIG 2022 | ja |
| CBI 2024 | Trampoline | TRA FIG 2022 | ja |
| zwei Turn-Events | Artistic gymnastics | AG DEF | **nein** |

Das Teil-Objekt `organization` der Originalantwort ist weggelassen, es wird
nicht gebraucht.
