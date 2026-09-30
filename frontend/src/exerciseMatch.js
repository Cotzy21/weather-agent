// Kobler øvelsesnavn fra en Garmin-fil til appens øvelser, så historikken skrives under riktig øvelse
// («Standing Dumbbell Biceps Curl» -> «Bicep Curls»). Katalogen løser det meste her på enheten uten nettverk;
// bare det som ikke gjenkjennes sendes til serveren, som bruker en språkmodell (og husker svaret).
// Feiler det, beholdes filens egne navn: importen stopper aldri på dette.
import { resolveExercise, exerciseDisplayName } from './exercises.js'

const CHUNK = 150 // under serverens tak på 200 navn per kall

/**
 * @param {string[]} names unike øvelsesnavn fra filen
 * @param {{ lang: string, post: Function }} opts `post(path, body, contentType)` -> JSON
 * @returns {Promise<{ map: Map<string, string>, matched: number, unmatched: number, viaAi: number }>}
 *   map: filens navn -> appens navn på valgt språk (bare navn som ble koblet)
 */
export async function matchExerciseNames(names, { lang, post }) {
  const map = new Map()
  let viaAi = 0
  const unknown = []
  for (const name of names) {
    const known = resolveExercise(name)
    if (known) map.set(name, lang === 'en' ? known.en : known.nb)
    else unknown.push(name)
  }

  for (let i = 0; i < unknown.length; i += CHUNK) {
    const chunk = unknown.slice(i, i + CHUNK)
    try {
      const res = await post('/api/trening/ovelser/koble', JSON.stringify({ names: chunk }), 'application/json')
      for (const m of res.matches ?? []) {
        // Bare navn vi spurte om, og bare id-er appen kjenner: alt annet ignoreres.
        const display = m.id ? exerciseDisplayName(m.id, lang) : undefined
        if (display && chunk.includes(m.name)) { map.set(m.name, display); viaAi++ }
      }
    } catch {
      // ingen tilgang til AI akkurat nå (kvote, nett, ikke konfigurert): behold Garmins navn for disse
    }
  }
  return { map, matched: map.size, unmatched: names.length - map.size, viaAi }
}

/** Skriver økter om med appens øvelsesnavn (fra `map`); navn uten treff beholdes. Endrer ikke input. */
export function applyExerciseNames(sessions, map) {
  return sessions.map((s) => ({ ...s, blocks: s.blocks.map((b) => ({ ...b, name: map.get(b.name) ?? b.name })) }))
}
