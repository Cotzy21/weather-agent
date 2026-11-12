// Garmin exercise names are resolved on the device only. Unknown names stay unchanged.
// Import-derived information must not be sent to the server or a language-model provider.
import { resolveExercise } from './exercises.js'

/** Keeps the async result contract used by the import workflow, without any network calls. */
export async function matchExerciseNames(names, { lang }) {
  const map = new Map()
  for (const name of names) {
    const known = resolveExercise(name)
    if (known) map.set(name, lang === 'en' ? known.en : known.nb)
  }
  return { map, matched: map.size, unmatched: names.length - map.size, viaAi: 0 }
}

/** Skriver økter om med appens øvelsesnavn (fra `map`); navn uten treff beholdes. Endrer ikke input. */
export function applyExerciseNames(sessions, map) {
  return sessions.map((s) => ({ ...s, blocks: s.blocks.map((b) => ({ ...b, name: map.get(b.name) ?? b.name })) }))
}
