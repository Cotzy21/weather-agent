// Kategorier for lagrede økter (maler), så Start-skjermen kan sorteres når det blir mange: etter økttype (Styrke, Løping, Svømming …).
// Ren logikk.
import { dayIndex, matchesPlan } from './trainingStats.js'

/** Rekkefølgen kategoriene vises i på Start-skjermen (samme som økttypene i byggeren). */
export const CATEGORY_ORDER = ['STYRKE', 'LØPING', 'SVØMMING', 'SYKKEL', 'BULDRING', 'KAMPSPORT', 'HIKING', 'FRISTIL']

/** Norske visningsnavn (oversettes med t() i grensesnittet). */
export const CATEGORY_LABELS = {
  STYRKE: '🏋️ Styrke',
  LØPING: '🏃 Løping',
  SVØMMING: '🏊 Svømming',
  SYKKEL: '🚴 Sykkel',
  BULDRING: '🧗 Buldring',
  KAMPSPORT: '🥋 Kampsport',
  HIKING: '🥾 Hiking',
  FRISTIL: '✨ Fristil',
}

const norm = (s) => String(s ?? '').toLowerCase().normalize('NFKD').replace(/\p{M}/gu, '')

/** Alle øvelsesnavn i en styrkeøkt (også i supersett), for søk og visning. */
export function exerciseNames(content) {
  const names = []
  for (const b of content?.blocks ?? []) {
    if (b.kind === 'superset') for (const e of b.exercises ?? []) names.push(e.name)
    else names.push(b.name)
  }
  return names.filter((n) => typeof n === 'string' && n.trim())
}

/** Kategorien til en lagret økt (mal): økttypen. Ukjente typer havner i Fristil. */
export function categoryOf(plan) {
  return CATEGORY_ORDER.includes(plan?.type) ? plan.type : 'FRISTIL'
}

/** Siste dato (YYYY-MM-DD) malen ble gjennomført, eller null. Økter startet fra malen kobles på id, eldre på tittel. */
export function lastDoneDate(plan, workouts) {
  let last = null
  for (const w of workouts ?? []) {
    if (matchesPlan(w, plan) && (!last || w.date > last)) last = w.date
  }
  return last
}

/** Søketreff: tittel, kategorinavn (norsk, uten ikon) eller et øvelsesnavn. Tomt søk treffer alt. */
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
