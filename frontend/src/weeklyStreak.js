// Ukeserie: antall uker på rad der brukeren nådde ukemålet. Ren logikk (ingen nettleser-API), regnet ut fra øktene som
// allerede er lastet. Designet følger ENGAGEMENT.md: serien er tilgivende og ukebasert, en hviledag er aldri et brudd,
// og en glipp kan dekkes av et opptjent hvilekort eller reddes med handling (én ekstra økt uka etter), aldri med penger.
//
// Regler (se ENGAGEMENT.md, «Beslutninger for 0.1»):
//  - uke = mandag–søndag, og det telles treningsdager (to økter samme dag er én)
//  - god uke: dager >= mål. Hvert 4. gode uke gir ett hvilekort (maks 2 på lager)
//  - glipp med hvilekort på lager: kortet brukes, serien fortsetter (uka gir ikke +1)
//  - glipp uten hvilekort: serien brytes. Nås målet + 1 i uka ETTER, reddes den gamle serien (+1); nås bare målet, starter
//    en ny serie på 1 (comeback). Lengste serie huskes uansett
//  - pauset uke (sykdom/ferie/skade): verken bryter, øker eller bruker kort
//  - uka med første økt vurderes ikke som glipp (man kan ikke nå målet hvis man begynner på en torsdag)

export const DEFAULT_GOAL = 3
export const MIN_GOAL = 1
export const MAX_GOAL = 7
export const CARD_EVERY = 4
export const MAX_CARDS = 2
export const MAX_PAUSES = 52
const WEEKS_SHOWN = 8

const DAY_MS = 86400000

// Kalenderregning på YYYY-MM-DD uten tidssoner (UTC-midnatt), så sommertid aldri flytter en dato.
const toMs = (iso) => Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10))
const fromMs = (ms) => new Date(ms).toISOString().slice(0, 10)

export const isIsoDate = (s) => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && fromMs(toMs(s)) === s

export function addDays(iso, n) {
  return fromMs(toMs(iso) + n * DAY_MS)
}

/** Mandagen i uka som datoen ligger i. */
export function weekStart(iso) {
  const dow = (new Date(toMs(iso)).getUTCDay() + 6) % 7 // mandag = 0
  return addDays(iso, -dow)
}

export function clampGoal(goal) {
  const n = Math.round(Number(goal))
  return Number.isFinite(n) ? Math.min(MAX_GOAL, Math.max(MIN_GOAL, n)) : DEFAULT_GOAL
}

/** Lagret oppsett med trygge verdier: mål 1–7 og pauser som mandager (en pause gjelder en hel uke). */
export function normalizeSettings(raw) {
  const pauses = Array.isArray(raw?.pauses)
    ? [...new Set(raw.pauses.filter(isIsoDate).map(weekStart))].sort().slice(-MAX_PAUSES)
    : []
  return { goal: raw?.goal == null ? DEFAULT_GOAL : clampGoal(raw.goal), pauses }
}

/**
 * Vurderer én avsluttet uke og returnerer ny tilstand. `st` = { streak, longest, goodWeeks, cards, sinceCard, repair }.
 * Ren funksjon (muterer ikke `st`), brukt både for avsluttede uker og for en forhåndsvisning av uka som pågår.
 */
function step(st, days, goal) {
  if (days >= goal) {
    let status = 'good'
    let streak = st.streak + 1
    if (st.repair) {
      if (days >= goal + 1) {
        status = 'repaired'
        streak = st.repair.broken + 1
      } else {
        status = 'comeback'
        streak = 1
      }
    }
    let cards = st.cards
    let sinceCard = st.sinceCard + 1
    let earnedCard = false
    if (sinceCard >= CARD_EVERY) {
      earnedCard = cards < MAX_CARDS
      cards = Math.min(MAX_CARDS, cards + 1)
      sinceCard = 0
    }
    return { status, earnedCard, next: { streak, longest: Math.max(st.longest, streak), goodWeeks: st.goodWeeks + 1, cards, sinceCard, repair: null } }
  }
  if (st.repair) { // andre glipp på rad: reparasjonsvinduet er borte
    return { status: 'missed', next: { ...st, repair: null, sinceCard: 0 } }
  }
  if (st.cards > 0) {
    return { status: 'covered', next: { ...st, cards: st.cards - 1 } }
  }
  return {
    status: 'missed',
    next: { ...st, streak: 0, sinceCard: 0, repair: st.streak > 0 ? { broken: st.streak } : null },
  }
}

/**
 * Regner ut serien.
 * @param workouts  økter med `date` (YYYY-MM-DD)
 * @param opts.goal treningsdager per uke (standard 3)
 * @param opts.pauses mandager (YYYY-MM-DD) for pausede uker
 * @param opts.today dagens dato (YYYY-MM-DD, norsk tid)
 */
export function computeStreak(workouts, { goal = DEFAULT_GOAL, pauses = [], today } = {}) {
  goal = clampGoal(goal)
  const paused = new Set(pauses.filter(isIsoDate).map(weekStart))
  const byWeek = new Map()
  for (const w of workouts ?? []) {
    if (!isIsoDate(w?.date)) continue
    const start = weekStart(w.date)
    if (!byWeek.has(start)) byWeek.set(start, new Set())
    byWeek.get(start).add(w.date)
  }
  const thisWeek = weekStart(today)
  const days = (start) => byWeek.get(start)?.size ?? 0
  const past = [...byWeek.keys()].filter((s) => s <= thisWeek)

  let st = { streak: 0, longest: 0, goodWeeks: 0, cards: 0, sinceCard: 0, repair: null }
  const weeks = []
  const earned = []
  if (past.length) {
    const first = past.reduce((a, b) => (a < b ? a : b))
    for (let start = first; start < thisWeek; start = addDays(start, 7)) {
      const d = days(start)
      if (paused.has(start)) { weeks.push({ start, days: d, status: 'pause' }); continue }
      if (start === first && d < goal) { weeks.push({ start, days: d, status: 'start' }); continue }
      const r = step(st, d, goal)
      st = r.next
      if (r.earnedCard) earned.push(start)
      weeks.push({ start, days: d, status: r.status })
    }
  }

  // Uka som pågår: vises som den ville blitt hvis den sluttet nå, men vurderes ikke som glipp ennå.
  const d = days(thisWeek)
  const isPaused = paused.has(thisWeek)
  const isFirst = past.length > 0 && past.reduce((a, b) => (a < b ? a : b)) === thisWeek
  let projected = st
  let currentStatus = 'open'
  if (isPaused) {
    currentStatus = 'pause'
  } else if (d >= goal) {
    const r = step(st, d, goal)
    projected = r.next
    currentStatus = r.status
  }
  const today0 = toMs(today)
  const daysLeft = Math.max(0, Math.round((toMs(addDays(thisWeek, 6)) - today0) / DAY_MS) + 1) // inkl. i dag
  const remaining = Math.max(0, goal - d)
  const done = !isPaused && d >= goal

  let state
  if (!past.length) state = 'empty'
  else if (isPaused) state = 'paused'
  else if (done) state = 'done'
  else if (remaining > daysLeft) state = st.cards > 0 ? 'covered' : (isFirst ? 'open' : 'atRisk')
  else state = 'open'

  // Reparasjon: forrige uke var en glipp uten hvilekort, og uka som pågår kan redde den gamle serien.
  const repair = !done && !isPaused && st.repair ? { broken: st.repair.broken, target: goal + 1 } : null

  const recent = [...weeks, { start: thisWeek, days: d, status: currentStatus === 'open' ? 'current' : currentStatus, current: true }]
    .slice(-WEEKS_SHOWN)

  return {
    goal,
    streak: projected.streak,
    longest: Math.max(st.longest, projected.longest),
    goodWeeks: projected.goodWeeks,
    cards: projected.cards,
    cardProgress: projected.sinceCard,
    cardEvery: CARD_EVERY,
    maxCards: MAX_CARDS,
    weeks: recent,
    earnedCardWeeks: earned,
    current: { start: thisWeek, days: d, goal, remaining, daysLeft, done, paused: isPaused, status: currentStatus },
    state,
    repair,
  }
}

/** Pauser med uka som pågår slått av/på. Returnerer ny liste (maks MAX_PAUSES, nyeste beholdes). */
export function togglePause(pauses, weekIso) {
  const start = weekStart(weekIso)
  const set = new Set((pauses ?? []).map(weekStart))
  if (set.has(start)) set.delete(start)
  else set.add(start)
  return [...set].sort().slice(-MAX_PAUSES)
}
