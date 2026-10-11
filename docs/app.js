/* ===========================================================================
   Trampolin-Wertungssuche

   Reines JavaScript, kein Framework, keine Bauwerkzeuge. Die Datei ist von
   oben nach unten lesbar: erst Hilfsfunktionen, dann Laden, dann Suchen,
   dann Anzeigen, am Ende das Zusammenschalten.

   So funktioniert die Suche:

     1. Beim Start wird personen.json geladen - ein kleiner Index mit einem
        Eintrag pro Person (Name, Jahrgang, Verein, Anzahl der Wertungen) und
        dem Verweis auf die Datei mit den Einzelwertungen.
     2. Die Eingabe wird im Index gesucht. Das geht im Browser und ohne
        Server, weil der Index klein ist.
     3. Erst wenn Treffer da sind, werden die Dateien mit den Einzelwertungen
        geladen - und nur die, in denen die Treffer stehen. Einmal geladen,
        bleiben sie im Speicher.

   Warum ein Index und nicht nur a.json ... z.json: die Wertungen sind nach
   dem ersten Buchstaben des Nachnamens aufgeteilt. Die Suche nach "Jonas"
   verrät aber nicht, in welcher Datei die Person steht. Ohne Index müsste die
   Seite alle 25 Dateien laden, also rund 9 MB.
   =========================================================================== */

'use strict';

/* --------------------------------------------------------------- Konstanten */

const ORDNER = 'data/';
const MIN_ZEICHEN = 2;
const MAX_PERSONEN = 20;       // mehr Treffer werden erst auf Knopfdruck gezeigt
const MAX_ZEILEN_ANFANGS = 25; // Zeilen pro Person, bevor "alle anzeigen" kommt

/* ----------------------------------------------------------- Zahlenformate */

/** Erzeugt einen Formatierer mit festen Nachkommastellen. */
function formatierer(min, max) {
  return new Intl.NumberFormat('de-DE', {
    minimumFractionDigits: min,
    maximumFractionDigits: max
  });
}

const F_EINE   = formatierer(1, 1);
const F_ZWEI   = formatierer(2, 2);
const F_DREI   = formatierer(1, 3);
const F_T      = formatierer(2, 3);
const F_GANZ   = formatierer(0, 0);

/** Zahl anzeigen, oder einen Gedankenstrich wenn es den Wert nicht gibt. */
function zahl(wert, format) {
  if (wert === null || wert === undefined) return '–';
  return format.format(wert);
}

/** 2025-10-05 wird zu 05.10.2025. */
function datumAnzeigen(iso) {
  if (!iso || iso.length < 10) return '–';
  return iso.slice(8, 10) + '.' + iso.slice(5, 7) + '.' + iso.slice(0, 4);
}

/** Zeitstempel aus meta.json lesbar machen. */
function zeitpunktAnzeigen(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d)) return iso;
  return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
       + ', ' + d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
       + ' Uhr';
}

/* ------------------------------------------------------- Namen vergleichbar */

/**
 * Bringt einen Namen auf die Vergleichsform.
 *
 * WICHTIG: Diese Regeln müssen dieselben sein wie in
 * crawler/src/main/java/de/wertungssuche/Namen.java - sonst sucht die Seite
 * in der falschen Datei. Schritt für Schritt:
 *   1. kleinschreiben
 *   2. ä -> a, ö -> o, ü -> u, ß -> ss
 *   3. alle übrigen Akzente entfernen (é -> e, č -> c, å -> a)
 *   4. mehrere Leerzeichen zu einem
 */
function normalisieren(text) {
  if (!text) return '';
  return text
    .toLowerCase()
    .replace(/ä/g, 'a')
    .replace(/ö/g, 'o')
    .replace(/ü/g, 'u')
    .replace(/ß/g, 'ss')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .trim()
    .replace(/\s+/g, ' ');
}

/* ------------------------------------------------------------- Daten laden */

/** Alles, was die Seite im Speicher hält. */
const speicher = {
  meta: null,
  personen: [],          // der Suchindex
  wertungen: new Map(),  // Dateiname -> Liste der Wertungen
  ladend: new Map()      // Dateiname -> laufendes Versprechen, gegen Doppelladen
};

async function jsonHolen(datei) {
  const antwort = await fetch(ORDNER + datei, { cache: 'no-cache' });
  if (!antwort.ok) {
    throw new Error(datei + ': HTTP ' + antwort.status);
  }
  return antwort.json();
}

/**
 * Lädt eine Wertungsdatei, falls noch nicht geladen.
 * Mehrfache Aufrufe für dieselbe Datei teilen sich eine Anfrage.
 */
function wertungenHolen(name) {
  if (speicher.wertungen.has(name)) {
    return Promise.resolve(speicher.wertungen.get(name));
  }
  if (speicher.ladend.has(name)) {
    return speicher.ladend.get(name);
  }
  const versprechen = jsonHolen(name + '.json')
    .then(liste => {
      speicher.wertungen.set(name, liste);
      speicher.ladend.delete(name);
      return liste;
    })
    .catch(fehler => {
      speicher.ladend.delete(name);
      throw fehler;
    });
  speicher.ladend.set(name, versprechen);
  return versprechen;
}

/* ------------------------------------------------------------------ Suchen */

/**
 * Sucht Personen zur Eingabe.
 *
 * Die Eingabe wird in Wörter zerlegt. Ein Treffer liegt vor, wenn **jedes**
 * eingegebene Wort der Anfang irgendeines Namensteils ist - des Vornamens,
 * des Nachnamens oder eines einzelnen Wortes darin. Damit findet
 * "muller lena" dasselbe wie "lena mu", und Doppelnamen wie
 * "Anna-Lena Schmitz-Berg" werden auch mit "berg anna" gefunden.
 */
function personenSuchen(eingabe) {
  const woerter = normalisieren(eingabe).split(' ').filter(w => w.length > 0);
  if (woerter.length === 0) return [];

  const treffer = [];

  for (const person of speicher.personen) {
    // Alle Namensteile, gegen die geprüft wird.
    const teile = person._teile;
    let alleGefunden = true;

    for (const wort of woerter) {
      let gefunden = false;
      for (const teil of teile) {
        if (teil.startsWith(wort)) { gefunden = true; break; }
      }
      if (!gefunden) { alleGefunden = false; break; }
    }
    if (alleGefunden) treffer.push(person);
  }

  // Wer mehr Wertungen hat, steht oben - meist ist das die gesuchte Person.
  treffer.sort((a, b) => {
    if (b.anzahl !== a.anzahl) return b.anzahl - a.anzahl;
    return a._sortName.localeCompare(b._sortName, 'de');
  });
  return treffer;
}

/**
 * Holt alle Wertungen einer Person aus einer geladenen Datei.
 *
 * Zusammengehalten wird über Nachname, Vorname und Jahrgang – ohne Verein.
 * Warum: die Vereinsangabe ist bei sporttech.io nicht stabil, jeder
 * Veranstalter trägt sie neu ein ("TV Voerde" und "TV Voerde 1920 e.V." sind
 * derselbe Verein). Dazu kommen echte Vereinswechsel. Nimmt man den Verein in
 * die Identität auf, zerfällt die Laufbahn einer Turnerin in mehrere Karten.
 * Dieselbe Regel steckt in Wertung.personSchluessel() im Crawler.
 */
function wertungenDerPerson(person) {
  const liste = speicher.wertungen.get(person.datei) || [];
  return liste
    .filter(w =>
      normalisieren(w.nachname) === normalisieren(person.nachname) &&
      normalisieren(w.vorname) === normalisieren(person.vorname) &&
      (w.jahrgang || null) === (person.jahrgang || null))
    // Neueste zuerst; innerhalb eines Tages nach Wettkampf und Durchgang.
    .sort((a, b) => {
      const d = (b.datum || '').localeCompare(a.datum || '');
      if (d !== 0) return d;
      const e = (a.wettkampf || '').localeCompare(b.wettkampf || '', 'de');
      if (e !== 0) return e;
      return (a.durchgang || '').localeCompare(b.durchgang || '', 'de');
    });
}

/* ----------------------------------------------------------- Bausteine HTML */

/** Erzeugt ein Element mit Klasse und Textinhalt. */
function el(tag, klasse, text) {
  const knoten = document.createElement(tag);
  if (klasse) knoten.className = klasse;
  if (text !== undefined && text !== null) knoten.textContent = text;
  return knoten;
}

/**
 * Der Balken, der eine Wertung in ihre Teilnoten zerlegt.
 *
 * Die Breite des Balkens richtet sich nach der höchsten Wertung dieser Person
 * (massstab). So sind die Balken innerhalb einer Tabelle untereinander
 * vergleichbar - man sieht auf einen Blick, welcher Wettkampf besser lief und
 * woran es lag.
 *
 * Innerhalb des Balkens wird nach Werten aufgeteilt: ein Segment je Teilnote,
 * mit 2px Fuge dazwischen. Der Abzug hängt abgesetzt und schraffiert hinten
 * dran, weil er abgezogen wird.
 */
function balkenBauen(w, massstab) {
  const teile = [
    ['d', w.d, 'D'],
    ['e', w.e, 'E'],
    ['t', w.t, 'T'],
    ['h', w.h, 'H']
  ];

  const summe = teile.reduce((s, [, wert]) => s + (wert || 0), 0);
  const abzug = w.abzug || 0;

  const spur = el('div', 'balken-spur');

  if (summe <= 0 && abzug <= 0) {
    return spur; // nichts zu zeigen, aber die Zelle bleibt gleich hoch
  }

  const balken = el('div', 'balken');
  // Anteil an der Spurbreite. Abzug zählt mit, damit der Balken nicht
  // breiter wird als die Spur.
  const anteil = Math.min(1, (summe + abzug) / (massstab || summe || 1));
  balken.style.width = (anteil * 100).toFixed(2) + '%';

  for (const [kuerzel, wert] of teile) {
    if (!wert || wert <= 0) continue;
    const seg = el('span', 'seg seg-' + kuerzel);
    seg.style.flexGrow = String(wert);
    seg.style.flexBasis = '0';
    balken.appendChild(seg);
  }
  if (abzug > 0) {
    const seg = el('span', 'seg seg-abzug');
    seg.style.flexGrow = String(abzug);
    seg.style.flexBasis = '0';
    balken.appendChild(seg);
  }

  // Für Screenreader und als Tooltip: die Aufteilung in Worten.
  const beschreibung = teile
    .filter(([, wert]) => wert !== null && wert !== undefined)
    .map(([k, wert]) => k.toUpperCase() + ' ' + F_DREI.format(wert))
    .join(', ')
    + (abzug > 0 ? ', Abzug ' + F_EINE.format(abzug) : '');

  spur.setAttribute('role', 'img');
  spur.setAttribute('aria-label', 'Aufteilung: ' + beschreibung);
  spur.title = beschreibung;

  spur.appendChild(balken);
  return spur;
}

/** Eine Kennzahl-Kachel über der Tabelle. */
function kachelBauen(titel, wert, zusatz, farbe) {
  const kachel = el('div', 'kachel');

  const oben = el('div', 'kachel-oben' + (farbe ? ' mit-farbe' : ''), titel);
  if (farbe) oben.style.setProperty('--farbe', farbe);
  kachel.appendChild(oben);

  const wertKnoten = el('div', 'kachel-wert', wert);
  if (zusatz) {
    wertKnoten.appendChild(el('span', 'kachel-zusatz', ' ' + zusatz));
  }
  kachel.appendChild(wertKnoten);
  return kachel;
}

/** Eine Tabellenzelle. data-spalte wird in der Mobilansicht als Label gezeigt. */
function zelle(klasse, spalte, inhalt) {
  const td = el('td', klasse);
  td.setAttribute('data-spalte', spalte);
  if (inhalt instanceof Node) {
    td.appendChild(inhalt);
  } else {
    td.textContent = inhalt;
  }
  return td;
}

/** Die Tabelle mit allen Wertungen einer Person. */
function tabelleBauen(zeilen, massstab) {
  const rahmen = el('div', 'tabelle-rahmen');
  const tabelle = el('table');

  const kopf = el('thead');
  const kopfZeile = el('tr');
  const spalten = [
    ['Datum', ''], ['Event', ''], ['Wettkampf', ''], ['Durchgang', ''],
    ['Platz', 'rechts'], ['D', 'rechts'], ['E', 'rechts'], ['T', 'rechts'],
    ['H', 'rechts'], ['Abzug', 'rechts'], ['Gesamt', 'rechts'],
    ['Aufteilung', ''], ['Quelle', '']
  ];
  for (const [name, klasse] of spalten) {
    kopfZeile.appendChild(el('th', klasse, name));
  }
  kopf.appendChild(kopfZeile);
  tabelle.appendChild(kopf);

  const koerper = el('tbody');

  for (const w of zeilen) {
    const tr = el('tr');

    tr.appendChild(zelle('datum-zelle', 'Datum', datumAnzeigen(w.datum)));
    tr.appendChild(zelle('event-zelle', 'Event', w.event || '–'));
    tr.appendChild(zelle('wettkampf-zelle', 'Wettkampf', w.wettkampf || '–'));
    tr.appendChild(zelle('durchgang-zelle', 'Durchgang', w.durchgang || '–'));

    const platzKlasse = 'platz-zelle'
      + (w.platz && w.platz <= 3 ? ' platz-' + w.platz : '');
    tr.appendChild(zelle(platzKlasse, 'Platz',
      w.platz ? F_GANZ.format(w.platz) + '.' : '–'));

    tr.appendChild(zelle(zahlKlasse(w.d), 'D', zahl(w.d, F_DREI)));
    tr.appendChild(zelle(zahlKlasse(w.e), 'E', zahl(w.e, F_DREI)));
    tr.appendChild(zelle(zahlKlasse(w.t), 'T', zahl(w.t, F_T)));
    tr.appendChild(zelle(zahlKlasse(w.h), 'H', zahl(w.h, F_ZWEI)));
    tr.appendChild(zelle(zahlKlasse(w.abzug), 'Abzug', zahl(w.abzug, F_EINE)));
    tr.appendChild(zelle('gesamt-zelle', 'Gesamt', zahl(w.gesamt, F_DREI)));

    tr.appendChild(zelle('balken-zelle', 'Aufteilung', balkenBauen(w, massstab)));

    const td = el('td', 'link-zelle');
    td.setAttribute('data-spalte', 'Quelle');
    if (w.link) {
      const a = el('a', null, 'sporttech.io');
      a.href = w.link;
      a.target = '_blank';
      a.rel = 'noopener';
      a.title = 'Ergebnisliste dieses Durchgangs auf sporttech.io';
      td.appendChild(a);
    } else {
      td.textContent = '–';
    }
    tr.appendChild(td);

    koerper.appendChild(tr);
  }

  tabelle.appendChild(koerper);
  rahmen.appendChild(tabelle);
  return rahmen;
}

function zahlKlasse(wert) {
  return (wert === null || wert === undefined) ? 'zahl leer' : 'zahl';
}

/** Ein Block pro Person: Kopf, Kennzahlen, Tabelle. */
function personBauen(person, zeilen) {
  const block = el('article', 'person');

  // --- Kopf ---
  const kopf = el('div', 'person-kopf');
  const name = el('h3', 'person-name',
    [person.vorname, person.nachname].filter(Boolean).join(' '));
  kopf.appendChild(name);

  if (person.jahrgang) {
    kopf.appendChild(el('span', 'person-jahrgang', 'Jahrgang ' + person.jahrgang));
  }

  // Alle Vereine und Nationen, neueste zuerst. Mehrere Einträge entstehen
  // durch Vereinswechsel und durch unterschiedliche Schreibweisen desselben
  // Vereins - beides gehört zu derselben Person.
  const zeile = el('p', 'person-verein');
  const nationen = person.nationen || [];
  const vereine = person.vereine || [];

  for (const n of nationen) {
    zeile.appendChild(el('span', 'person-nation', n));
  }
  if (vereine.length > 0) {
    if (nationen.length > 0) zeile.appendChild(document.createTextNode(' · '));
    zeile.appendChild(document.createTextNode(vereine[0]));
    if (vereine.length > 1) {
      const frueher = el('span', 'person-frueher',
        ' · früher ' + vereine.slice(1).join(' · '));
      frueher.title = 'Weitere Vereinsangaben derselben Person. '
        + 'Oft ist es derselbe Verein, nur anders geschrieben.';
      zeile.appendChild(frueher);
    }
  } else if (nationen.length === 0) {
    zeile.textContent = 'ohne Vereinsangabe';
  }
  kopf.appendChild(zeile);
  block.appendChild(kopf);

  // --- Kennzahlen ---
  const beste = besteWerte(zeilen);
  const events = new Set(zeilen.map(w => w.eventId)).size;

  const kacheln = el('div', 'kacheln');
  kacheln.appendChild(kachelBauen('Wettkämpfe', F_GANZ.format(events),
    F_GANZ.format(zeilen.length) + ' Übungen'));
  kacheln.appendChild(kachelBauen('Bestwert Gesamt',
    beste.gesamt === null ? '–' : F_DREI.format(beste.gesamt)));
  kacheln.appendChild(kachelBauen('Beste D',
    beste.d === null ? '–' : F_DREI.format(beste.d), null, 'var(--n-d)'));
  kacheln.appendChild(kachelBauen('Beste E',
    beste.e === null ? '–' : F_DREI.format(beste.e), null, 'var(--n-e)'));
  kacheln.appendChild(kachelBauen('Beste T',
    beste.t === null ? '–' : F_T.format(beste.t), null, 'var(--n-t)'));
  kacheln.appendChild(kachelBauen('Beste H',
    beste.h === null ? '–' : F_ZWEI.format(beste.h), null, 'var(--n-h)'));
  block.appendChild(kacheln);

  // --- Tabelle, erst gekürzt ---
  const massstab = beste.summe || 1;
  const gekuerzt = zeilen.length > MAX_ZEILEN_ANFANGS;
  const sichtbar = gekuerzt ? zeilen.slice(0, MAX_ZEILEN_ANFANGS) : zeilen;

  const tabelle = tabelleBauen(sichtbar, massstab);
  block.appendChild(tabelle);

  if (gekuerzt) {
    const knopf = el('button', 'mehr',
      'Alle ' + F_GANZ.format(zeilen.length) + ' Übungen anzeigen');
    knopf.type = 'button';
    knopf.addEventListener('click', () => {
      block.replaceChild(tabelleBauen(zeilen, massstab), tabelle);
      knopf.remove();
    });
    block.appendChild(knopf);
  }

  return block;
}

/**
 * Die Bestwerte einer Person.
 *
 * `summe` ist der grösste Wert aus (D+E+T+H+Abzug) einer einzelnen Übung und
 * dient als Maßstab für die Balken - nicht `gesamt`, weil der Abzug die
 * Balkenbreite mitbestimmt.
 */
function besteWerte(zeilen) {
  const beste = { gesamt: null, d: null, e: null, t: null, h: null, summe: 0 };

  for (const w of zeilen) {
    for (const feld of ['gesamt', 'd', 'e', 't', 'h']) {
      const wert = w[feld];
      if (wert !== null && wert !== undefined
          && (beste[feld] === null || wert > beste[feld])) {
        beste[feld] = wert;
      }
    }
    const summe = (w.d || 0) + (w.e || 0) + (w.t || 0) + (w.h || 0) + (w.abzug || 0);
    if (summe > beste.summe) beste.summe = summe;
  }
  return beste;
}

/* ----------------------------------------------------------------- Anzeige */

const knoten = {
  feld: document.getElementById('suchfeld'),
  leeren: document.getElementById('leeren'),
  zustand: document.getElementById('zustand'),
  ergebnisse: document.getElementById('ergebnisse'),
  bestand: document.getElementById('bestand'),
  fussStand: document.getElementById('fussStand'),
  themaSchalter: document.getElementById('themaSchalter'),
  themaText: document.getElementById('themaText')
};

/** Schreibt eine Meldung in den Zustandsbereich. */
function zustandZeigen(html, istFehler) {
  knoten.zustand.className = 'zustand' + (istFehler ? ' fehler' : '');
  knoten.zustand.innerHTML = html;
}

function zustandLeeren() {
  knoten.zustand.className = 'zustand';
  knoten.zustand.innerHTML = '';
}

function ergebnisseLeeren() {
  knoten.ergebnisse.replaceChildren();
}

/* ------------------------------------------------------------- Suchvorgang */

let laufendeSuche = 0;

async function suchen() {
  const eingabe = knoten.feld.value;
  knoten.leeren.hidden = eingabe.length === 0;

  const lauf = ++laufendeSuche;

  if (normalisieren(eingabe).replace(/\s/g, '').length < MIN_ZEICHEN) {
    ergebnisseLeeren();
    if (eingabe.length === 0) {
      zustandLeeren();
    } else {
      zustandZeigen('<p>Bitte mindestens ' + MIN_ZEICHEN
        + ' Buchstaben eingeben.</p>');
    }
    return;
  }

  const treffer = personenSuchen(eingabe);

  if (treffer.length === 0) {
    ergebnisseLeeren();
    zustandZeigen(
      '<p><strong>Keine Treffer.</strong></p>'
      + '<p>Möglich ist auch: die Person ist zwar auf sporttech.io zu finden, '
      + 'aber nicht in einem Wettkampf, den diese Seite erfasst. '
      + 'Gesammelt werden Trampolin-Einzelwertungen aus Wettkämpfen in '
      + 'Deutschland ab 2024.</p>');
    return;
  }

  const gezeigt = treffer.slice(0, MAX_PERSONEN);

  // Nur die Dateien laden, in denen die Treffer stehen.
  const dateien = [...new Set(gezeigt.map(p => p.datei))];
  const fehlende = dateien.filter(d => !speicher.wertungen.has(d));

  if (fehlende.length > 0) {
    zustandZeigen('<p class="punkte">Wertungen werden geladen</p>');
  }

  try {
    await Promise.all(dateien.map(wertungenHolen));
  } catch (fehler) {
    if (lauf !== laufendeSuche) return;
    ergebnisseLeeren();
    zustandZeigen(
      '<p><strong>Die Wertungen konnten nicht geladen werden.</strong></p>'
      + '<p>' + textSicher(fehler.message) + '</p>'
      + '<p>Bitte die Seite neu laden. Besteht das Problem weiter, fehlen die '
      + 'Datendateien vielleicht gerade – dann hilft ein späterer Versuch.</p>',
      true);
    return;
  }

  // Wurde zwischenzeitlich weitergetippt? Dann dieses Ergebnis verwerfen.
  if (lauf !== laufendeSuche) return;

  ergebnisseLeeren();

  const kopf = el('div', 'treffer-kopf');
  kopf.appendChild(el('h2', null, treffer.length === 1 ? 'Eine Person' : 'Personen'));
  let hinweis = F_GANZ.format(treffer.length)
    + (treffer.length === 1 ? ' Treffer' : ' Treffer');
  if (treffer.length > gezeigt.length) {
    hinweis += ' · die ' + gezeigt.length + ' mit den meisten Wertungen werden'
      + ' gezeigt, bitte genauer eingeben';
  }
  kopf.appendChild(el('span', 'anzahl', hinweis));
  knoten.ergebnisse.appendChild(kopf);

  let leerePersonen = 0;
  for (const person of gezeigt) {
    const zeilen = wertungenDerPerson(person);
    if (zeilen.length === 0) { leerePersonen++; continue; }
    knoten.ergebnisse.appendChild(personBauen(person, zeilen));
  }

  if (leerePersonen === gezeigt.length) {
    zustandZeigen(
      '<p><strong>Der Suchindex und die Wertungsdateien passen nicht zusammen.</strong></p>'
      + '<p>Das liegt meist an einer alten Zwischenspeicherung im Browser. '
      + 'Ein Neuladen mit Strg+F5 behebt es.</p>', true);
  } else {
    zustandLeeren();
  }
}

/** Entschärft Text, der in innerHTML landet. */
function textSicher(text) {
  const div = document.createElement('div');
  div.textContent = String(text || '');
  return div.innerHTML;
}

/* ------------------------------------------------------------- Farbschema */

const THEMA_SPEICHER = 'wertungssuche.thema';
const THEMEN = ['auto', 'light', 'dark'];
const THEMA_NAMEN = { auto: 'Automatisch', light: 'Hell', dark: 'Dunkel' };

function themaSetzen(thema) {
  if (thema === 'auto') {
    document.documentElement.removeAttribute('data-theme');
  } else {
    document.documentElement.setAttribute('data-theme', thema);
  }
  knoten.themaText.textContent = THEMA_NAMEN[thema];
  try { localStorage.setItem(THEMA_SPEICHER, thema); } catch (e) { /* egal */ }
}

function themaStarten() {
  let gespeichert = 'auto';
  try {
    gespeichert = localStorage.getItem(THEMA_SPEICHER) || 'auto';
  } catch (e) { /* Privatmodus: dann eben automatisch */ }
  if (!THEMEN.includes(gespeichert)) gespeichert = 'auto';
  themaSetzen(gespeichert);

  knoten.themaSchalter.addEventListener('click', () => {
    const jetzt = document.documentElement.getAttribute('data-theme') || 'auto';
    const naechstes = THEMEN[(THEMEN.indexOf(jetzt) + 1) % THEMEN.length];
    themaSetzen(naechstes);
  });
}

/* ----------------------------------------------------------------- Starten */

async function starten() {
  themaStarten();

  knoten.feld.disabled = true;
  zustandZeigen('<p class="punkte">Daten werden geladen</p>');

  try {
    const [meta, personen] = await Promise.all([
      jsonHolen('meta.json'),
      jsonHolen('personen.json')
    ]);

    speicher.meta = meta;
    speicher.personen = personen;

    // Die Vergleichsformen einmal vorberechnen - danach ist jede Suche
    // nur noch ein Durchlauf über ein Feld von Zeichenketten.
    for (const p of personen) {
      const vorname = normalisieren(p.vorname);
      const nachname = normalisieren(p.nachname);
      const teile = new Set();
      for (const ganz of [vorname, nachname]) {
        if (!ganz) continue;
        teile.add(ganz);
        // Doppelnamen und Bindestrichnamen einzeln aufnehmen.
        for (const stueck of ganz.split(/[\s-]+/)) {
          if (stueck) teile.add(stueck);
        }
      }
      p._teile = [...teile];
      p._sortName = nachname + ' ' + vorname;
    }

    knoten.bestand.textContent =
      F_GANZ.format(meta.anzahlPersonen) + ' Personen · '
      + F_GANZ.format(meta.anzahlWertungen) + ' Wertungen · '
      + F_GANZ.format(meta.anzahlEvents) + ' Wettkämpfe';

    knoten.fussStand.textContent =
      'Daten zuletzt aktualisiert am ' + zeitpunktAnzeigen(meta.aktualisiert) + '.';

    knoten.feld.disabled = false;
    zustandLeeren();

    // Eine Suche in der Adresse erlaubt das Verlinken von Ergebnissen:
    // index.html?name=müller
    const parameter = new URLSearchParams(location.search);
    const vorgabe = parameter.get('name');
    if (vorgabe) {
      knoten.feld.value = vorgabe;
      suchen();
    }
    knoten.feld.focus({ preventScroll: true });

  } catch (fehler) {
    knoten.bestand.textContent = '';
    zustandZeigen(
      '<p><strong>Die Daten konnten nicht geladen werden.</strong></p>'
      + '<p>' + textSicher(fehler.message) + '</p>'
      + '<p>Mögliche Gründe:</p>'
      + '<ul>'
      + '<li>Die Seite wurde direkt aus dem Dateisystem geöffnet. '
      + 'Browser verbieten dabei das Nachladen von Dateien – '
      + 'stattdessen einen kleinen Webserver benutzen, zum Beispiel '
      + '<code>python -m http.server</code> im Ordner <code>docs</code>.</li>'
      + '<li>Der Crawler ist noch nicht gelaufen, dann fehlt <code>data/</code>.</li>'
      + '<li>Keine Verbindung.</li>'
      + '</ul>', true);
  }
}

/* -------------------------------------------------------------- Verdrahtung */

// Kleine Verzögerung beim Tippen: so wird nicht nach jedem Buchstaben die
// ganze Liste durchgegangen und neu gezeichnet.
let tippBremse = null;
knoten.feld.addEventListener('input', () => {
  clearTimeout(tippBremse);
  tippBremse = setTimeout(suchen, 140);
});

knoten.feld.addEventListener('keydown', ereignis => {
  if (ereignis.key === 'Enter') {
    clearTimeout(tippBremse);
    suchen();
  }
  if (ereignis.key === 'Escape') {
    knoten.feld.value = '';
    suchen();
  }
});

knoten.leeren.addEventListener('click', () => {
  knoten.feld.value = '';
  knoten.feld.focus();
  suchen();
});

starten();
