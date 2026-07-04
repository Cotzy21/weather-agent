/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useState } from 'react'

/**
 * Lettvekts i18n uten bibliotek, i gettext-stil: den NORSKE teksten er selve
 * nøkkelen. `t('Logg økt')` slår opp i EN-ordboka når språket er engelsk, og
 * returnerer nøkkelen uendret på norsk (eller når oversettelse mangler - da
 * vises norsk i stedet for en tom streng).
 *
 * Plassholdere skrives som {n} i begge språk og fylles av t():
 *   t('Du har trent {n} dager på rad', { n: 4 })
 *
 * Språket lagres i localStorage. Norsk er standard til ALLE views er dekket -
 * så flippes DEFAULT_LANG til 'en' (planen sier engelsk som standard).
 * Backend-innhold (råd, feilmeldinger) er fortsatt norsk; det oversettes i et
 * senere steg med Accept-Language mot API-et.
 */

const DEFAULT_LANG = 'nb'

const EN = {
  // Sidemeny / chrome
  'Hjem': 'Home',
  'Værsøk': 'Weather search',
  'Ruteplanlegger': 'Route planner',
  'Trening': 'Training',
  'Kosthold': 'Nutrition',
  'Restitusjon': 'Recovery',
  'Mørk modus': 'Dark mode',
  'Lys modus': 'Light mode',
  'Bytt mellom lys og mørk modus': 'Switch between light and dark mode',
  'Min konto': 'My account',
  'Logg inn': 'Log in',
  'Minimer menyen': 'Collapse the menu',
  'Utvid menyen': 'Expand the menu',
  'Norsk': 'Norwegian',
  'English': 'English',

  // Hjem (Dashboard)
  'Finn eventyret.': 'Find the adventure.',
  'Følg formen.': 'Follow your form.',
  'Finn finest turvær, planlegg ruter, og før treningsdagbok – på ett sted.':
      'Find the best hiking weather, plan routes and keep a training log – in one place.',
  'Finn turvær': 'Find hiking weather',
  'Finn turvær →': 'Find hiking weather →',
  'I fokus': 'In focus',
  '🏋️ Trening denne uka': '🏋️ Training this week',
  'økter': 'workouts',
  'totalt': 'total',
  '🥗 Kosthold i dag': '🥗 Nutrition today',
  'Ingenting logget ennå – begynn kostholdsdagboka her →': 'Nothing logged yet – start your food diary here →',
  'trening': 'training',
  'kcal igjen av målet': 'kcal left of your goal',
  '🧪 Lavt denne uka:': '🧪 Low this week:',
  '⏱️ Siste økt': '⏱️ Latest workout',
  '🌤️ Turvær': '🌤️ Hiking weather',
  'Hvor er det finest i helga?': 'Where is it best this weekend?',
  'Spør værsøket – rangerer topper og turruter etter vær.':
      'Ask the weather search – it ranks peaks and trails by weather.',
  'I dag': 'Today',
  'Ingen aktivitet logget i dag ennå.': 'No activity logged today yet.',
  'Logg økt': 'Log workout',
  'Planlegg rute': 'Plan a route',
  'Nylig aktivitet': 'Recent activity',
  'Ingen økter ennå – logg din første under «Trening».': 'No workouts yet – log your first under “Training”.',

  // Økt-typer
  '🏋️ Styrke': '🏋️ Strength',
  '🏃 Løping': '🏃 Running',
  '🏊 Svømming': '🏊 Swimming',
  '🚴 Sykkel': '🚴 Cycling',
  '🧗 Buldring': '🧗 Bouldering',
  '🥾 Hiking': '🥾 Hiking',
  '✨ Fristil': '✨ Freestyle',

  // Værsøk
  'Hva betyr mest for deg?': 'What matters most to you?',
  'Temperatur': 'Temperature',
  'Regn': 'Rain',
  'Vind': 'Wind',
  'Høyde': 'Elevation',
  'Lav': 'Low',
  'Middels': 'Medium',
  'Høy': 'High',
  'Prøv: «hvor er det finest fjellvær i Møre og Romsdal i helga»':
      'Try: “where is the best mountain weather in Møre og Romsdal this weekend”',
  'Skriv et spørsmål …': 'Ask a question …',
  'Send': 'Send',
  'Henter vær': 'Fetching weather',
  'Beklager –': 'Sorry –',
  'Finest vær:': 'Best weather:',
  'Finest vær på turrute:': 'Best weather on a trail:',
  'snitt': 'avg',
  'mm regn/dag': 'mm rain/day',
  '🎒 Klær & utstyr': '🎒 Clothing & gear',
  '🧭 Planlegg tur hit': '🧭 Plan a trip here',
  'Trykk på en rad for å åpne detaljside med varsel for de neste dagene.':
      'Tap a row to open a detail page with the forecast for the coming days.',
  'Sted': 'Place',
  'Tur': 'Trail',
  'moh': 'm a.s.l.',
  'score': 'score',
  '🥾 Merkede turer i området': '🥾 Marked trails in the area',
  '🥾 Merkede turer i nærheten': '🥾 Marked trails nearby',
  'Fant ingen merkede turer (OpenStreetMap) i nærheten.': 'No marked trails (OpenStreetMap) found nearby.',
  'Fant ingen værdata for perioden (kanskje for langt fram?).':
      'No weather data found for the period (maybe too far ahead?).',
  'Jeg fant ingen region i spørsmålet – prøv å nevne et fylke, f.eks. «Rogaland».':
      'I could not find a region in the question – try naming a county, e.g. “Rogaland”.',
  '← Tilbake til resultatene': '← Back to the results',
  'Vær de neste dagene': 'Weather the coming days',
  'Ingen værdata tilgjengelig for stedet.': 'No weather data available for this place.',
  'tørt': 'dry',

  // Ruteplanlegger
  '🧭 Planlegg rute': '🧭 Plan a route',
  'Klikk i kartet for å legge til punkter (start, stopp, teltplass, mål).':
      'Click the map to add points (start, stops, campsite, destination).',
  'punkt': 'points',
  '↩ Angre siste': '↩ Undo last',
  '✕ Tøm': '✕ Clear',
  'Vekt (kg)': 'Weight (kg)',
  'Beregn': 'Calculate',
  'Beregner …': 'Calculating …',
  'stigning': 'ascent',
  '⚠️ Vær forberedt på': '⚠️ Be prepared for',
  '🍫 Mat & drikke': '🍫 Food & drink',
  'Navn på ruta': 'Route name',
  'Lagre rute': 'Save route',
  'Logg inn for å lagre ruta.': 'Log in to save the route.',
  '📁 Mine ruter': '📁 My routes',
  'Minimer panelet': 'Collapse the panel',
  'Utvid panelet': 'Expand the panel',
  '🎯 Mål fra værsøket:': '🎯 Destination from weather search:',
  '– klikk i kartet for å tegne ruta di fram til målet.': '– click the map to draw your route to it.',
  'Fjern mål': 'Remove destination',
  'Tur til {name}': 'Trip to {name}',
  'Enkel': 'Easy',
  'Krevende': 'Demanding',
  'Ekspert': 'Expert',
  '⛰️ Høyeste punkt ~{m} moh · bratteste parti ~{p} % helning':
      '⛰️ Highest point ~{m} m a.s.l. · steepest section ~{p} % gradient',
  'Rute langs faktiske stier. Stigning fra høydeprofil. Grovt estimat (~4 km/t, ~6 MET).':
      'Route along actual trails. Ascent from the elevation profile. Rough estimate (~4 km/h, ~6 MET).',
  'Rett linje mellom punktene (sti-ruting ikke aktivert). Stigning fra høydeprofil. Grovt estimat (~4 km/t, ~6 MET).':
      'Straight line between points (trail routing not enabled). Ascent from the elevation profile. Rough estimate (~4 km/h, ~6 MET).',

  // Restitusjon
  '🧘 Restitusjon': '🧘 Recovery',
  'Logg inn og før treningsdagbok – så får du restitusjonsråd og beskjed når treningsmengden øker raskere enn kroppen liker.':
      'Log in and keep a training log – you will get recovery advice and a heads-up when your training volume grows faster than your body likes.',
  'Henter rapporten …': 'Fetching the report …',
  'Ingenting å melde – logg økter under «Trening», så dukker råd og varsler opp her.':
      'Nothing to report – log workouts under “Training” and advice and warnings will show up here.',
  '– ny aktivitet for deg': '– a new activity for you',
  '– ~{p} % over ditt vanlige nivå': '– ~{p} % above your usual level',
  'Du har trent {n} dager på rad': 'You have trained {n} days in a row',
  '– kroppen bygger seg sterkere på hviledagen. Vurder en rolig dag i morgen.':
      '– the body gets stronger on rest days. Consider an easy day tomorrow.',
  'Etter de siste øktene': 'After your recent workouts',
  'i dag': 'today',
  'i går': 'yesterday',
  'Varslene sammenligner siste 7 dager med snittet av de 4 ukene før (ACWR-metoden fra idrettsforskningen). Generelle tommelfingerregler – ikke medisinske råd.':
      'Warnings compare the last 7 days with the average of the 4 weeks before (the ACWR method from sports science). General rules of thumb – not medical advice.',

  // Habit tracker
  '📅 Vaner': '📅 Habits',
  '+ Ny vane': '+ New habit',
  'Lukk': 'Close',
  'Navn (f.eks. Tøying)': 'Name (e.g. Stretching)',
  'Emoji': 'Emoji',
  'Enhet (tom = ja/nei)': 'Unit (empty = yes/no)',
  'Legg til': 'Add',
  'Ingen vaner ennå – legg til koffein, søvn eller noe helt eget.':
      'No habits yet – add caffeine, sleep or something of your own.',
  'Dager på rad': 'Days in a row',
  'Slett vane': 'Delete habit',
  'Koffein': 'Caffeine',
  'Søvn': 'Sleep',
  'Lesing': 'Reading',
  'Meditasjon': 'Meditation',
  'Vann': 'Water',
  'kopper': 'cups',
  'timer': 'hours',
  'glass': 'glasses',
}

const I18nContext = createContext({ lang: DEFAULT_LANG, t: (s) => s, setLang: () => {} })

export function I18nProvider({ children }) {
  const [lang, setLang] = useState(() => {
    try { return localStorage.getItem('lang') ?? DEFAULT_LANG } catch { return DEFAULT_LANG }
  })

  useEffect(() => {
    try { localStorage.setItem('lang', lang) } catch { /* privat modus o.l. */ }
    document.documentElement.lang = lang
  }, [lang])

  function t(text, params) {
    let out = lang === 'en' ? (EN[text] ?? text) : text
    if (params) {
      for (const [key, value] of Object.entries(params)) {
        out = out.replaceAll(`{${key}}`, String(value))
      }
    }
    return out
  }

  return (
    <I18nContext.Provider value={{ lang, t, setLang }}>
      {children}
    </I18nContext.Provider>
  )
}

export function useI18n() {
  return useContext(I18nContext)
}
