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

export function newLive(title, exercises) {
  return { title, startedAt: Date.now(), exercises, restSec: 90, restEnd: null }
}

// Norsk tastatur gir komma som desimaltegn.
export const toNum = (x) => Number(String(x ?? '').replace(',', '.'))

export function findSuggestion(name, nextSets) {
  const n = name.trim().toLowerCase()
  return (nextSets || []).find((s) => s.exercise.toLowerCase() === n)
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

// Supersett foldes ut til vanlige øvelser (runder × sett) - enklere å huke av på mobil.
export function fromPlan(content, nextSets) {
  const out = []
  for (const b of content?.blocks || []) {
    if (b.kind === 'superset') {
      const rounds = Math.max(1, Number(b.rounds) || 1)
      for (const e of b.exercises || []) {
        const sets = Array.from({ length: rounds }, () => e.sets || []).flat()
        out.push(liveExercise(e.name || '', sets, nextSets))
      }
    } else if (b.kind === 'dropset') {
      out.push(liveExercise(b.name || '', b.drops, nextSets, 'dropset'))
    } else {
      out.push(liveExercise(b.name || '', b.sets, nextSets))
    }
  }
  return out
}

// Bare avhukede sett lagres. durationMin gir ekte kaloriberegning i backend.
export function toContent(exercises, startedAt, now = Date.now()) {
  const blocks = exercises
    .map((e) => {
      const done = e.sets
        .filter((s) => s.done && s.reps !== '')
        .map((s) => ({ reps: toNum(s.reps), weightKg: toNum(s.weightKg) || 0 }))
      const block = { kind: e.kind, name: e.name.trim(), [e.kind === 'dropset' ? 'drops' : 'sets']: done }
      if (e.note?.trim()) block.note = e.note.trim()
      return block
    })
    .filter((b) => b.name && (b.sets || b.drops).length)
  return { blocks, durationMin: Math.max(1, Math.round((now - startedAt) / 60000)) }
}

// Tidligere sett for én øvelse, nyest først. Leter i vanlige blokker, dropsett og supersett.
export function exerciseHistory(workouts, name, limit = 12) {
  const key = name.trim().toLowerCase()
  const out = []
  for (const w of workouts || []) {
    if (w.type !== 'STYRKE') continue
    for (const b of w.content?.blocks || []) {
      const entries = b.kind === 'superset'
        ? (b.exercises || []).map((e) => ({ name: e.name, sets: e.sets, note: e.note }))
        : [{ name: b.name, sets: b.kind === 'dropset' ? b.drops : b.sets, note: b.note }]
      for (const e of entries) {
        if ((e.name || '').trim().toLowerCase() === key && e.sets?.length) {
          out.push({ id: w.id, date: w.date, title: w.title, sets: e.sets, note: e.note })
        }
      }
    }
  }
  return out.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0)).slice(0, limit)
}
