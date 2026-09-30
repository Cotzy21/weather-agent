// Personlige rekorder (PR): sammenligner en økt med brukerens egen historikk. Ren logikk, regnet i nettleseren.
// Bare ekte tall: en PR krever at øvelsen er logget før (første gang er en start, ikke en rekord), og at den nye verdien slår den
// gamle. Ingen «nesten-rekord»-meldinger her (se ENGAGEMENT.md, prinsipp 3).
import { exerciseKey } from './exercises.js'
import { estimate1RM, exercisesIn } from './exerciseProgress.js'

/** Epley er lite pålitelig for lange sett (30 reps sier lite om maks), så bare sett opp til dette teller for estimert 1RM. */
export const MAX_REPS_FOR_E1RM = 12
const EPS = 0.05
const num = (x) => {
  const n = Number(String(x ?? '').replace(',', '.'))
  return Number.isFinite(n) ? n : 0
}
const round1 = (x) => Math.round(x * 10) / 10

/** Styrken i ett sett: { kg, reps, e1rm }. e1rm er 0 for kroppsvekt (0 kg) og for sett over MAX_REPS_FOR_E1RM. */
export function setStrength(set) {
  const kg = num(set?.weightKg)
  const reps = num(set?.reps)
  return { kg, reps, e1rm: kg > 0 && reps > 0 && reps <= MAX_REPS_FOR_E1RM ? estimate1RM(kg, reps) : 0 }
}

/**
 * Beste tall per øvelse i historikken: Map fra øvelsesnøkkel til { key, name, sessions, e1rm, weight, reps }.
 * `e1rm` og `weight` gjelder sett med vekt, `reps` gjelder kroppsvekt-sett (0 kg). `sessions` = antall økter øvelsen er logget i.
 * @param history øktene (bare STYRKE brukes)
 * @param onOrBefore bare økter til og med denne datoen (YYYY-MM-DD); utelatt = alle
 */
export function priorBests(history, onOrBefore) {
  const map = new Map()
  for (const w of history ?? []) {
    if (w?.type !== 'STYRKE') continue
    if (onOrBefore && !(w.date <= onOrBefore)) continue
    for (const ex of exercisesIn(w.content)) {
      const name = (ex.name ?? '').trim()
      if (!name) continue
      const key = exerciseKey(name)
      const b = map.get(key) ?? { key, name, sessions: 0, e1rm: 0, weight: 0, reps: 0 }
      let logged = false
      for (const s of ex.sets) {
        const st = setStrength(s)
        if (st.reps <= 0) continue
        logged = true
        if (st.kg > 0) {
          b.weight = Math.max(b.weight, st.kg)
          b.e1rm = Math.max(b.e1rm, st.e1rm)
        } else b.reps = Math.max(b.reps, st.reps)
      }
      if (logged) { b.sessions++; b.name = name }
      map.set(key, b)
    }
  }
  return map
}

/**
 * Rekordene i en økt (`content` med blocks) mot historikken FØR økta. Én rekord per øvelse:
 *  - { kind: 'e1rm', value, previous, gain, kg, reps, heaviest }: høyeste estimerte 1RM (heaviest = også tyngste vekt noensinne)
 *  - { kind: 'reps', value, previous, gain }: flest reps i en kroppsvektøvelse
 * @param opts.date regn bare mot økter til og med denne datoen (for økter som logges i ettertid)
 */
export function detectPRs(history, content, { date, bests } = {}) {
  const prior = bests ?? priorBests(history, date)
  const session = new Map()
  for (const ex of exercisesIn(content)) {
    const name = (ex.name ?? '').trim()
    if (!name) continue
    const key = exerciseKey(name)
    const s = session.get(key) ?? { key, name, e1rm: 0, kg: 0, reps: 0, weight: 0, bodyReps: 0 }
    for (const set of ex.sets) {
      const st = setStrength(set)
      if (st.reps <= 0) continue
      if (st.kg > 0) {
        s.weight = Math.max(s.weight, st.kg)
        if (st.e1rm > s.e1rm) { s.e1rm = st.e1rm; s.kg = st.kg; s.reps = st.reps }
      } else s.bodyReps = Math.max(s.bodyReps, st.reps)
    }
    session.set(key, s)
  }

  const out = []
  for (const s of session.values()) {
    const p = prior.get(s.key)
    if (!p || p.sessions === 0) continue // første gang er en start, ikke en rekord
    if (s.weight > 0) {
      if (s.e1rm > 0 && p.e1rm > 0 && s.e1rm > p.e1rm + EPS) {
        out.push({
          kind: 'e1rm', key: s.key, name: s.name, kg: s.kg, reps: s.reps,
          value: round1(s.e1rm), previous: round1(p.e1rm), gain: round1(s.e1rm - p.e1rm), heaviest: s.weight > p.weight + EPS,
        })
      }
    } else if (s.bodyReps > 0 && p.reps > 0 && s.bodyReps > p.reps) {
      out.push({ kind: 'reps', key: s.key, name: s.name, value: s.bodyReps, previous: p.reps, gain: s.bodyReps - p.reps })
    }
  }
  return out
}

/**
 * Hvilke sett i en pågående live-økt som er rekord akkurat nå: Set av «øvelsesindeks-settindeks». Bare avhukede sett teller,
 * og bare det beste settet per øvelse merkes (ikke hvert sett som slår gammel rekord).
 * @param exercises live-øvelser: [{ name, kind, sets: [{ reps, weightKg, done }] }]
 */
export function livePRSets(exercises, bests) {
  const marked = new Set()
  ;(exercises ?? []).forEach((ex, ei) => {
    if (ex.kind === 'dropset') return
    const p = bests.get(exerciseKey((ex.name ?? '').trim()))
    if (!p || p.sessions === 0) return
    let bestE = null // beste vektsett: { si, e1rm }
    let bestR = null // beste kroppsvektsett: { si, reps }
    ex.sets.forEach((set, si) => {
      if (!set.done) return
      const st = setStrength(set)
      if (st.reps <= 0) return
      if (st.kg > 0) {
        if (st.e1rm > 0 && p.e1rm > 0 && st.e1rm > p.e1rm + EPS && (!bestE || st.e1rm > bestE.e1rm)) bestE = { si, e1rm: st.e1rm }
      } else if (p.reps > 0 && st.reps > p.reps && (!bestR || st.reps > bestR.reps)) {
        bestR = { si, reps: st.reps }
      }
    })
    const best = bestE ?? bestR // vektsett går foran (samme regel som i detectPRs)
    if (best) marked.add(`${ei}-${best.si}`)
  })
  return marked
}
