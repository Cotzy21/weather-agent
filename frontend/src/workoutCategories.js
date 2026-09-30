// Kategorier for lagrede økter (maler), så Start-skjermen kan sorteres når det blir mange. Ren logikk.
//
// Styrkeøkter får en kategori (Push, Pull, Bein, Overkropp, Underkropp, Hele kroppen, Kjerne, Annen styrke). Brukeren kan velge den selv
// (lagres som `content.category` på malen); ellers gjettes den fra tittelen («Push A») og øvelsene (muskelgruppene i katalogen).
// Andre aktiviteter (løping, sykkel, tur …) er sin egen kategori etter økttype.
import { resolveExercise } from './exercises.js'
import { dayIndex, matchesPlan } from './trainingStats.js'

export const STRENGTH_CATEGORIES = ['push', 'pull', 'legs', 'upper', 'lower', 'full', 'core', 'other']
export const ACTIVITY_TYPES = ['LØPING', 'SYKKEL', 'SVØMMING', 'HIKING', 'BULDRING', 'KAMPSPORT', 'FRISTIL']

/** Rekkefølgen kategoriene vises i på Start-skjermen. */
export const CATEGORY_ORDER = [...STRENGTH_CATEGORIES, ...ACTIVITY_TYPES]

/** Norske visningsnavn (oversettes med t() i grensesnittet). Aktivitetstypene bruker økttypens vanlige navn med ikon. */
export const CATEGORY_LABELS = {
  push: 'Push',
  pull: 'Pull',
  legs: 'Bein',
  upper: 'Overkropp',
  lower: 'Underkropp',
  full: 'Hele kroppen',
  core: 'Kjerne',
  other: 'Annen styrke',
  LØPING: '🏃 Løping',
  SYKKEL: '🚴 Sykkel',
  SVØMMING: '🏊 Svømming',
  HIKING: '🥾 Hiking',
  BULDRING: '🧗 Buldring',
  KAMPSPORT: '🥋 Kampsport',
  FRISTIL: '✨ Fristil',
}

const norm = (s) => String(s ?? '').toLowerCase().normalize('NFKD').replace(/\p{M}/gu, '')

// Tittelord, sjekket i denne rekkefølgen (mest spesifikke først). Grenser (\b) hindrer at «ben» treffer «benkpress».
const TITLE_RULES = [
  ['full', /\b(full ?body|fullkropp|helkropp|hele kroppen|total ?body)\b/],
  ['upper', /\b(upper|overkropp)\b/],
  ['lower', /\b(lower|underkropp)\b/],
  ['push', /\b(push|skyv)\b/],
  ['pull', /\b(pull|trekk)\b/],
  ['legs', /\b(legs?|bein|ben|knebøy|knebov|squat)\b/],
  ['core', /\b(core|kjerne|mage|abs)\b/],
]

// Katalogens muskelgrupper → hovedgruppe. Armer kan være både push (triceps) og pull (biceps), så de teller ikke som noen av dem.
const GROUP_KIND = { Bryst: 'push', Skuldre: 'push', Rygg: 'pull', Bein: 'legs', Mage: 'core', Helkropp: 'full', Armer: 'arms' }

/** Alle øvelsesnavn i en økt (også i supersett), for gjetting og søk. */
export function exerciseNames(content) {
  const names = []
  for (const b of content?.blocks ?? []) {
    if (b.kind === 'superset') for (const e of b.exercises ?? []) names.push(e.name)
    else names.push(b.name)
  }
  return names.filter((n) => typeof n === 'string' && n.trim())
}

/** Gjetter styrkekategori fra tittel og øvelser. Alltid en av STRENGTH_CATEGORIES. */
export function guessStrengthCategory(title, content) {
  const tt = norm(title)
  for (const [id, re] of TITLE_RULES) if (re.test(tt)) return id

  const n = { push: 0, pull: 0, legs: 0, core: 0, full: 0, arms: 0 }
  for (const name of exerciseNames(content)) {
    const kind = GROUP_KIND[resolveExercise(name)?.group]
    if (kind) n[kind]++
  }
  const main = n.push + n.pull + n.legs + n.core
  const total = main + n.full + n.arms
  if (total === 0) return 'other'
  if (n.full / total >= 0.5) return 'full'
  if (main === 0) return n.arms > 0 ? 'upper' : 'other'
  if (n.legs / total >= 0.6) return 'legs'
  if (n.core / total >= 0.6) return 'core'
  const upper = n.push + n.pull
  if (n.legs === 0 && n.core / total < 0.4) { // bare overkropp: ren push, ren pull eller blandet
    if (upper === 0) return n.arms > 0 ? 'upper' : 'other'
    if (n.push / upper >= 0.7) return 'push'
    if (n.pull / upper >= 0.7) return 'pull'
    return 'upper'
  }
  return n.legs > 0 && upper > 0 ? 'full' : 'other'
}

/** Kategorien en lagret økt (mal) hører til: brukerens eget valg, ellers aktivitetstypen, ellers gjetting. */
export function categoryOf(plan) {
  if (plan?.type && plan.type !== 'STYRKE') {
    return ACTIVITY_TYPES.includes(plan.type) ? plan.type : 'FRISTIL'
  }
  const chosen = plan?.content?.category
  if (STRENGTH_CATEGORIES.includes(chosen)) return chosen
  return guessStrengthCategory(plan?.title, plan?.content)
}

/** Siste dato (YYYY-MM-DD) malen ble gjennomført, eller null. Økter startet fra malen kobles på id, eldre på tittel. */
export function lastDoneDate(plan, workouts) {
  let last = null
  for (const w of workouts ?? []) {
    if (matchesPlan(w, plan) && (!last || w.date > last)) last = w.date
  }
  return last
}

/** Søketreff: tittel, kategorinavn (norsk) eller et øvelsesnavn. Tomt søk treffer alt. */
export function matchesQuery(plan, query) {
  const q = norm(query).trim()
  if (!q) return true
  const hay = [plan.title, CATEGORY_LABELS[categoryOf(plan)], ...exerciseNames(plan.content)].map(norm)
  return hay.some((h) => h.includes(q))
}

/**
 * Malene gruppert etter kategori i fast rekkefølge (CATEGORY_ORDER), tomme kategorier utelatt. Innen en kategori: nylig brukte
 * først, så alfabetisk. Hvert element er { plan, lastDone }.
 * @param opts.query fritekstsøk (se matchesQuery)
 */
export function groupTemplates(plans, workouts, { query = '' } = {}) {
  const groups = new Map()
  for (const plan of plans ?? []) {
    if (!matchesQuery(plan, query)) continue
    const id = categoryOf(plan)
    if (!groups.has(id)) groups.set(id, [])
    groups.get(id).push({ plan, lastDone: lastDoneDate(plan, workouts) })
  }
  const byRecent = (a, b) => {
    if (a.lastDone !== b.lastDone) return (a.lastDone ?? '') < (b.lastDone ?? '') ? 1 : -1
    return String(a.plan.title).localeCompare(String(b.plan.title), 'nb')
  }
  return CATEGORY_ORDER.filter((id) => groups.has(id)).map((id) => ({ id, items: groups.get(id).sort(byRecent) }))
}

/** Malene som er lagt til dagens ukedag (mandag = 0) og ikke er gjennomført i dag. */
export function plannedToday(plans, workouts, todayIso, todayIdx) {
  return (plans ?? []).filter((p) => dayIndex(p.content?.day) === todayIdx
    && !(workouts ?? []).some((w) => w.date === todayIso && matchesPlan(w, p)))
}
