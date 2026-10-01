import { exerciseKey } from './exercises.js'

// Live-økt: tilstand, mellomlagring og konvertering til/fra øktformatet.
// Mellomlagres per bruker så økta overlever at Safari lukker/laster siden på nytt.

const storeKey = (userId) => `live-session:${userId}`

export function loadLive(userId) {
  try { return JSON.parse(localStorage.getItem(storeKey(userId))) } catch { return null }
}

export function saveLive(userId, state) {
  try { localStorage.setItem(storeKey(userId), JSON.stringify(state)) } catch { /* privat modus o.l. */ }
}

export function clearLive(userId) {
  try { localStorage.removeItem(storeKey(userId)) } catch { /* privat modus o.l. */ }
}

// plannedId: planen økta ble startet fra (så uke-oversikten kobler på id, ikke tittel).
// clientId: lages én gang per økt, så innsending på nytt aldri lager en duplikat.
export function newLive(title, exercises, plannedId = null) {
  return { title, startedAt: Date.now(), exercises, restSec: 90, restEnd: null, plannedId, clientId: newId() }
}

export function newId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

// Norsk tastatur gir komma som desimaltegn.
export const toNum = (x) => Number(String(x ?? '').replace(',', '.'))

export function findSuggestion(name, nextSets) {
  const key = exerciseKey(name)
  return (nextSets || []).find((s) => exerciseKey(s.exercise) === key)
}

// Progresjonsforslaget vinner over planens tall: det bygger på siste faktiske økt.
// Dropsett har synkende vekt per drop, så der brukes planen uendret.
export function liveExercise(name, planSets, nextSets, kind = 'exercise') {
  const s = kind === 'dropset' ? null : findSuggestion(name, nextSets)
  // Eldre AI-planer lagret ett sett per øvelse; ett arbeidssett er nesten aldri meningen.
  const planned = Math.max(planSets?.length || 0, s?.sets || 0)
  const count = kind === 'exercise' && planned < 2 ? 3 : planned || 3
  const sets = Array.from({ length: count }, (_, i) => {
    const p = planSets?.[i] ?? planSets?.[planSets.length - 1]
    const weight = s ? (s.nextWeightKg > 0 ? s.nextWeightKg : '') : (p?.weightKg || '')
    return { reps: String(s?.nextReps ?? p?.reps ?? ''), weightKg: String(weight), done: false }
  })
  return { kind, name, sets }
}

// Supersett foldes ut til øvelser (runder × sett) som henger sammen med en felles `group`: de gjøres vekselvis (A1, B1, A2, B2 …)
// med hvile først etter hver runde (se liveFlow.js).
export function fromPlan(content, nextSets) {
  const out = []
  for (const b of content?.blocks || []) {
    if (b.kind === 'superset') {
      const rounds = Math.max(1, Number(b.rounds) || 1)
      const group = (b.exercises || []).length > 1 ? newId() : undefined
      for (const e of b.exercises || []) {
        const sets = Array.from({ length: rounds }, () => e.sets || []).flat()
        const ex = liveExercise(e.name || '', sets, nextSets)
        if (group) ex.group = group
        out.push(ex)
      }
    } else if (b.kind === 'dropset') {
      out.push(liveExercise(b.name || '', b.drops, nextSets, 'dropset'))
    } else {
      out.push(liveExercise(b.name || '', b.sets, nextSets))
    }
  }
  return out
}

// Bare avhukede sett lagres. durationMin gir ekte kaloriberegning i backend. Øvelser som er et supersett (felles `group`) lagres som
// ÉN supersett-blokk, så det syns i historikken at de ble gjort sammen.
export function toContent(exercises, startedAt, now = Date.now()) {
  const doneSets = (e) => e.sets
    .filter((s) => s.done && s.reps !== '')
    .map((s) => ({ reps: toNum(s.reps), weightKg: toNum(s.weightKg) || 0 }))
  const plain = (e, sets) => {
    const block = { kind: e.kind, name: e.name.trim(), [e.kind === 'dropset' ? 'drops' : 'sets']: sets }
    if (e.note?.trim()) block.note = e.note.trim()
    return block
  }
  const blocks = []
  const emitted = new Set()
  for (const e of exercises) {
    if (e.group) {
      if (emitted.has(e.group)) continue
      emitted.add(e.group)
      const members = exercises
        .filter((m) => m.group === e.group && m.name.trim() && doneSets(m).length)
        .map((m) => ({ m, sets: doneSets(m) }))
      if (members.length > 1) {
        blocks.push({
          kind: 'superset',
          rounds: 1,
          exercises: members.map(({ m, sets }) => ({ name: m.name.trim(), sets, ...(m.note?.trim() ? { note: m.note.trim() } : {}) })),
        })
      } else if (members.length === 1) {
        blocks.push(plain(members[0].m, members[0].sets))
      }
      continue
    }
    const sets = doneSets(e)
    if (e.name.trim() && sets.length) blocks.push(plain(e, sets))
  }
  return { blocks, durationMin: Math.max(1, Math.round((now - startedAt) / 60000)) }
}

// Tidligere sett for én øvelse, nyest først. Leter i vanlige blokker, dropsett og supersett.
export function exerciseHistory(workouts, name, limit = 12) {
  const key = exerciseKey(name)
  const out = []
  for (const w of workouts || []) {
    if (w.type !== 'STYRKE') continue
    for (const b of w.content?.blocks || []) {
      const entries = b.kind === 'superset'
        ? (b.exercises || []).map((e) => ({ name: e.name, sets: e.sets, note: e.note }))
        : [{ name: b.name, sets: b.kind === 'dropset' ? b.drops : b.sets, note: b.note }]
      for (const e of entries) {
        if (exerciseKey(e.name || '') === key && e.sets?.length) {
          out.push({ id: w.id, date: w.date, title: w.title, sets: e.sets, note: e.note })
        }
      }
    }
  }
  return out.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0)).slice(0, limit)
}
