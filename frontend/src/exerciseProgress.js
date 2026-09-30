// Progresjon per øvelse over hele historikken, regnet ut i nettleseren fra øktene som allerede er lastet
// (ingen ekstra serverjobb). Én øvelse er samme øvelse uansett språk/stavemåte (se exerciseKey).
import { exerciseKey } from './exercises.js'

/** Estimert 1RM (Epley). Gjør «80 kg x 5» og «77,5 kg x 8» sammenlignbare. */
export function estimate1RM(weightKg, reps) {
  if (!(weightKg > 0) || !(reps > 0)) return 0
  return reps === 1 ? weightKg : weightKg * (1 + reps / 30)
}

const num = (x) => (Number.isFinite(Number(x)) ? Number(x) : 0)

// Alle (øvelse, sett) i en styrkeøkt, også i supersett og dropsett (der teller toppsettet).
function* exercisesIn(content) {
  for (const b of content?.blocks ?? []) {
    if (b.kind === 'superset') {
      for (const e of b.exercises ?? []) yield { name: e.name, sets: e.sets ?? [] }
    } else if (b.kind === 'dropset') {
      yield { name: b.name, sets: (b.drops ?? []).slice(0, 1) }
    } else {
      yield { name: b.name, sets: b.sets ?? [] }
    }
  }
}

/**
 * Per øvelse: ett punkt per dag ({ date, topWeight, topReps, e1rm, volume, sets }), eldst først.
 * Returnerer en Map fra øvelsesnøkkel til { key, name, points }. Navnet er det som ble brukt sist.
 */
export function exerciseSeries(workouts) {
  const byKey = new Map()
  const strength = (workouts ?? []).filter((w) => w.type === 'STYRKE')
    .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0))
  for (const w of strength) {
    for (const ex of exercisesIn(w.content)) {
      const name = (ex.name ?? '').trim()
      const sets = ex.sets.map((s) => ({ reps: num(s.reps), weight: num(s.weightKg) })).filter((s) => s.reps > 0)
      if (!name || !sets.length) continue
      const key = exerciseKey(name)
      if (!byKey.has(key)) byKey.set(key, { key, name, points: [] })
      const entry = byKey.get(key)
      entry.name = name
      const point = {
        date: w.date,
        topWeight: Math.max(...sets.map((s) => s.weight)),
        topReps: Math.max(...sets.map((s) => s.reps)),
        e1rm: Math.max(...sets.map((s) => estimate1RM(s.weight, s.reps))),
        volume: sets.reduce((sum, s) => sum + s.reps * s.weight, 0),
        sets: sets.length,
      }
      const last = entry.points.at(-1)
      if (last && last.date === point.date) { // to økter samme dag: behold det beste
        last.topWeight = Math.max(last.topWeight, point.topWeight)
        last.topReps = Math.max(last.topReps, point.topReps)
        last.e1rm = Math.max(last.e1rm, point.e1rm)
        last.volume += point.volume
        last.sets += point.sets
      } else entry.points.push(point)
    }
  }
  return byKey
}

/**
 * Oppsummerer utviklingen for hver øvelse med minst `minSessions` økter, flest økter først.
 * Målestokk: estimert 1RM når det brukes vekt, ellers flest reps (kroppsvekt).
 * changePct = utvikling fra første til siste økt.
 */
export function exerciseTrends(workouts, { minSessions = 2 } = {}) {
  const trends = []
  for (const s of exerciseSeries(workouts).values()) {
    if (s.points.length < minSessions) continue
    const weighted = s.points.some((p) => p.topWeight > 0)
    const metric = weighted ? 'e1rm' : 'topReps'
    const values = s.points.map((p) => p[metric])
    const first = s.points[0]
    const last = s.points.at(-1)
    const bestPoint = s.points.reduce((a, b) => (b[metric] > a[metric] ? b : a))
    trends.push({
      key: s.key,
      name: s.name,
      sessions: s.points.length,
      weighted,
      values,
      firstDate: first.date,
      lastDate: last.date,
      first: weighted ? first.topWeight : first.topReps,
      last: weighted ? last.topWeight : last.topReps,
      best: weighted ? bestPoint.topWeight : bestPoint.topReps,
      changePct: values[0] > 0 ? Math.round(((values.at(-1) - values[0]) / values[0]) * 100) : null,
    })
  }
  return trends.sort((a, b) => b.sessions - a.sessions || (a.lastDate < b.lastDate ? 1 : -1))
}

/**
 * Økter over tid per tittel (f.eks. «Push», «Pull», «Legs»), for styrkeøkter med totaler fra Garmin-CSV
 * (sett, reps, varighet) eller med utfylte øvelser. Viser om økta blir lengre/tyngre selv uten vekter per øvelse.
 */
export function sessionTrends(workouts, { minSessions = 3 } = {}) {
  const groups = new Map()
  const strength = (workouts ?? []).filter((w) => w.type === 'STYRKE')
    .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0))
  for (const w of strength) {
    const c = w.content ?? {}
    let sets = num(c.totalSets)
    let reps = num(c.totalReps)
    let volume = 0
    for (const ex of exercisesIn(c)) {
      for (const s of ex.sets) {
        if (num(s.reps) > 0) { volume += num(s.reps) * num(s.weightKg); if (!c.totalSets) { sets += 1; reps += num(s.reps) } }
      }
    }
    if (!sets && !num(c.durationMin)) continue
    const title = (w.title ?? '').trim()
    if (!title) continue
    const key = title.toLowerCase()
    if (!groups.has(key)) groups.set(key, { key, title, points: [] })
    groups.get(key).points.push({ date: w.date, sets, reps, volume: Math.round(volume), durationMin: num(c.durationMin), kcal: num(c.kcal) })
  }
  return [...groups.values()]
    .filter((g) => g.points.length >= minSessions)
    .map((g) => {
      const metric = g.points.some((p) => p.volume > 0) ? 'volume' : g.points.some((p) => p.reps > 0) ? 'reps' : 'durationMin'
      const values = g.points.map((p) => p[metric])
      const half = Math.max(1, Math.floor(values.length / 4)) // snitt av første og siste kvart, mindre skjørt enn to enkeltøkter
      const avg = (a) => a.reduce((s, v) => s + v, 0) / a.length
      const early = avg(values.slice(0, half))
      const late = avg(values.slice(-half))
      return {
        key: g.key, title: g.title, sessions: g.points.length, metric, values,
        firstDate: g.points[0].date, lastDate: g.points.at(-1).date,
        changePct: early > 0 ? Math.round(((late - early) / early) * 100) : null,
        latest: g.points.at(-1),
      }
    })
    .sort((a, b) => b.sessions - a.sessions)
}
