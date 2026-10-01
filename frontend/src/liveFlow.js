// Fokusmodus i live-økta: rekkefølgen settene gjøres i (sett for sett, supersett vekselvis), hvile mellom settene og dynamiske
// endringer midt i økta (lag/løs opp supersett, fjern øvelse). Ren logikk uten React, så den kan testes.
//
// Live-økta har en liste `exercises: [{ kind, name, sets: [{ reps, weightKg, done }], note, group?, restSec? }]`.
//  - `group`: øvelser med samme gruppe-id er et supersett. Settene gjøres vekselvis: A1, B1, A2, B2 … og det er hvile først etter at
//    hele runden er gjort (ikke mellom A og B).
//  - `restSec`: hvile for akkurat denne øvelsen; ellers brukes økas standard (`restSec` på økta).
// Et «steg» er ett sett i den rekkefølgen det skal gjøres: { ei, si, group? }.
import { toNum } from './liveSession.js'

export const DEFAULT_REST_SEC = 90
export const MIN_REST_SEC = 10
export const MAX_REST_SEC = 600

export const clampRest = (sec) => {
  const n = Math.round(Number(sec))
  return Number.isFinite(n) ? Math.min(MAX_REST_SEC, Math.max(MIN_REST_SEC, n)) : DEFAULT_REST_SEC
}

/** Hvile (sekunder) etter et sett i denne øvelsen: øvelsens egen verdi, ellers økas standard. */
export const restFor = (ex, sessionRest = DEFAULT_REST_SEC) => clampRest(ex?.restSec ?? sessionRest)

const newGroupId = () => `g${Math.random().toString(36).slice(2, 8)}`

/** Alle sett i den rekkefølgen de skal gjøres. Supersett går i runder (A1, B1, A2, B2 …), vanlige øvelser sett for sett. */
export function buildSteps(exercises) {
  const steps = []
  const emitted = new Set()
  exercises.forEach((ex, ei) => {
    const g = ex.group
    if (!g) {
      ex.sets.forEach((_, si) => steps.push({ ei, si }))
      return
    }
    if (emitted.has(g)) return
    emitted.add(g)
    const members = exercises.reduce((acc, e, i) => (e.group === g ? [...acc, i] : acc), [])
    const rounds = Math.max(...members.map((i) => exercises[i].sets.length))
    for (let si = 0; si < rounds; si++) {
      for (const mi of members) if (si < exercises[mi].sets.length) steps.push({ ei: mi, si, group: g })
    }
  })
  return steps
}

const isDone = (exercises, st) => Boolean(exercises[st.ei]?.sets[st.si]?.done)

/** Antall avhukede og totale sett. */
export function progress(exercises) {
  const all = exercises.flatMap((e) => e.sets)
  return { done: all.filter((s) => s.done).length, total: all.length }
}

/**
 * Indeksen i `steps` for steget som gjøres nå: det brukeren har hoppet til (`cursor`) hvis det finnes og ikke er gjort, ellers
 * første sett som ikke er gjort. -1 når alt er gjort.
 */
export function currentIndex(exercises, steps, cursor) {
  if (cursor) {
    const i = steps.findIndex((s) => s.ei === cursor.ei && s.si === cursor.si)
    if (i >= 0 && !isDone(exercises, steps[i])) return i
  }
  return steps.findIndex((s) => !isDone(exercises, s))
}

/** Neste sett som ikke er gjort etter steg `idx` (går rundt til starten om ingenting er igjen etter). -1 når alt er gjort. */
export function nextUndone(exercises, steps, idx) {
  for (let j = idx + 1; j < steps.length; j++) if (!isDone(exercises, steps[j])) return j
  for (let j = 0; j <= idx && j < steps.length; j++) if (!isDone(exercises, steps[j])) return j
  return -1
}

/**
 * Hvile (sekunder) etter å ha gjort steg `idx`. Vanlige øvelser: øvelsens hvile. Supersett: ingen hvile mellom øvelsene i en runde
 * (`transitionSec`, standard 0), men full hvile (den lengste av øvelsenes) etter siste øvelse i runden.
 */
export function restAfter(exercises, steps, idx, { restSec = DEFAULT_REST_SEC, transitionSec = 0 } = {}) {
  const st = steps[idx]
  if (!st) return 0
  if (!st.group) return restFor(exercises[st.ei], restSec)
  const next = steps[idx + 1]
  const endOfRound = !next || next.group !== st.group || next.si !== st.si
  if (!endOfRound) return Math.max(0, transitionSec)
  const round = steps.filter((x) => x.group === st.group && x.si === st.si)
  return Math.max(...round.map((m) => restFor(exercises[m.ei], restSec)))
}

/** Øvelsene i den rekkefølgen de utføres, med fremdrift: [{ ei, name, done, total, group, state }]. state = 'done' | 'current' | 'upcoming'. */
export function overview(exercises, steps, curIdx) {
  const cur = steps[curIdx]
  const order = []
  for (const st of steps) if (!order.includes(st.ei)) order.push(st.ei)
  return order.map((ei) => {
    const ex = exercises[ei]
    const done = ex.sets.filter((s) => s.done).length
    const total = ex.sets.length
    const inGroup = ex.group ? exercises.filter((e) => e.group === ex.group).length > 1 : false
    const state = done === total ? 'done' : (cur && (cur.ei === ei || (cur.group && cur.group === ex.group))) ? 'current' : 'upcoming'
    return { ei, name: ex.name, done, total, group: inGroup ? ex.group : null, state }
  })
}

/**
 * Lag et supersett av øvelse `a` og `b`: de får samme gruppe, havner etter hverandre og gjøres vekselvis fra neste runde. Er en av
 * dem alt i et supersett, slås gruppene sammen. Dropsett kan ikke være med. Returnerer en ny liste (muterer ikke).
 */
export function makeSuperset(exercises, a, b, groupId = newGroupId()) {
  const ea = exercises[a]
  const eb = exercises[b]
  if (a === b || !ea || !eb || ea.kind === 'dropset' || eb.kind === 'dropset') return exercises
  const group = ea.group ?? eb.group ?? groupId
  const members = new Set([a, b])
  exercises.forEach((e, i) => { if (e.group && (e.group === ea.group || e.group === eb.group)) members.add(i) })
  const idxs = [...members].sort((x, y) => x - y)
  const grouped = idxs.map((i) => ({ ...exercises[i], group }))
  const rest = exercises.filter((_, i) => !members.has(i))
  rest.splice(idxs[0], 0, ...grouped) // alt før første medlem er ikke-medlemmer, så plassen er den samme
  return rest
}

/** Løs opp supersettet øvelse `ei` er med i (alle medlemmene blir vanlige øvelser). */
export function unlinkSuperset(exercises, ei) {
  const g = exercises[ei]?.group
  if (!g) return exercises
  return exercises.map((e) => {
    if (e.group !== g) return e
    const { group: _group, ...plain } = e
    return plain
  })
}

/** Fjern en øvelse. Blir det bare én igjen i supersettet, er den en vanlig øvelse igjen. */
export function removeExercise(exercises, ei) {
  const g = exercises[ei]?.group
  const next = exercises.filter((_, i) => i !== ei)
  if (!g || next.filter((e) => e.group === g).length > 1) return next
  return next.map((e) => {
    if (e.group !== g) return e
    const { group: _group, ...plain } = e
    return plain
  })
}

/**
 * Endre et tall med steg (kg i 2,5, reps i 1). Første trykk på en tom verdi gir `start` (hvis den er over 0). Resultatet er en streng med punktum som desimaltegn
 * (så det kan lagres rett i settet), eller '' når det blir 0 og `blankAtZero` (kg: kroppsvekt).
 */
export function stepValue(value, delta, { min = 0, max = 1000, start = 0, blankAtZero = false } = {}) {
  const raw = toNum(value)
  const empty = value === '' || value == null || !Number.isFinite(raw)
  // Første trykk på en tom verdi gir startverdien (f.eks. 8 reps) i stedet for startverdien ± ett steg.
  const target = empty && start > 0 ? start : (empty ? 0 : raw) + delta
  const next = Math.min(max, Math.max(min, Math.round(target * 100) / 100))
  return blankAtZero && next <= 0 ? '' : String(next)
}

/**
 * Juster en hvile som pågår med ±sekunder. Har den gått ut, starter + en ny hvile på det antallet. Returnerer ny { restEnd, restTotal }
 * (restTotal er hele lengden, til fremdriftsringen).
 */
export function adjustRestTimer({ restEnd, restTotal }, deltaSec, now = Date.now()) {
  if (!restEnd) return { restEnd, restTotal }
  const over = restEnd <= now
  const base = deltaSec > 0 && over ? now : restEnd
  const end = Math.max(now, base + deltaSec * 1000)
  const total = deltaSec > 0 && over ? deltaSec : Math.max(1, (restTotal ?? 0) + deltaSec)
  return { restEnd: end, restTotal: Math.max(total, Math.ceil((end - now) / 1000)) }
}
