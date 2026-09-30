// Øvelsesbiblioteket, gruppert for velgeren. Kilden er katalogen som deles med backend
// (src/main/resources/exercises.json): faste id-er med norske og engelske navn, så
// «Bicep Curls» og «Bicepscurl» er samme øvelse overalt. Gruppenavnene oversettes via t().
import catalog from '../../src/main/resources/exercises.json'

const ENTRIES = catalog.exercises

export const EXERCISE_GROUPS = (() => {
  const groups = new Map()
  for (const e of ENTRIES) {
    if (!groups.has(e.group)) groups.set(e.group, { group: e.group, items: [], en: [] })
    const g = groups.get(e.group)
    g.items.push(e.nb)
    g.en.push(e.en)
  }
  return [...groups.values()]
})()

/** Samme normalisering som backend (ExerciseCatalog.normalize): små bokstaver, uten aksenter/tegn/flertalls-s. */
export function normalizeName(name) {
  const s = String(name ?? '')
    .normalize('NFKD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]/gu, '')
  return s.length > 3 && s.endsWith('s') ? s.slice(0, -1) : s
}

const BY_KEY = new Map()
for (const e of ENTRIES) {
  for (const n of [e.nb, e.en, ...(e.aliases || [])]) {
    if (!BY_KEY.has(normalizeName(n))) BY_KEY.set(normalizeName(n), e)
  }
}

/** Katalogøvelsen et navn (på norsk, engelsk eller som alias) peker til, eller undefined. */
export function resolveExercise(name) {
  return BY_KEY.get(normalizeName(name))
}

/** Nøkkel å sammenligne øvelser på: katalog-id, ellers navnet normalisert (som backend). */
export function exerciseKey(name) {
  return resolveExercise(name)?.id ?? normalizeName(name)
}

/** Biblioteket på valgt språk: [{ group, items }]. */
export function groupsFor(lang) {
  return EXERCISE_GROUPS.map((g) => ({ group: g.group, items: lang === 'en' ? g.en : g.items }))
}

// Andre øvelser i samme muskelgruppe, på samme språk som øvelsen selv
// (tomt for øvelser utenfor biblioteket).
export function sameGroup(name) {
  const hit = resolveExercise(name)
  if (!hit) return []
  const g = EXERCISE_GROUPS.find((x) => x.group === hit.group)
  const key = normalizeName(name)
  const list = g.en.some((n) => normalizeName(n) === key) ? g.en : g.items
  return list.filter((n) => exerciseKey(n) !== hit.id)
}
