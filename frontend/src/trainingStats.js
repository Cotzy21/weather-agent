// Små, rene hjelpere for treningsstatistikk (delt av Trening-fanen og ukeskortet).

// dayIndex tåler både norske og engelske dagnavn (assistenten skriver på brukerens språk).
const DAY_ALIASES = {
  mandag: 0, monday: 0, tirsdag: 1, tuesday: 1, onsdag: 2, wednesday: 2,
  torsdag: 3, thursday: 3, fredag: 4, friday: 4,
  lørdag: 5, lordag: 5, saturday: 5, søndag: 6, sondag: 6, sunday: 6,
}
export function dayIndex(day) {
  if (!day) return -1
  const k = day.trim().toLowerCase()
  if (k in DAY_ALIASES) return DAY_ALIASES[k]
  for (const [alias, idx] of Object.entries(DAY_ALIASES)) {
    if (k.startsWith(alias)) return idx
  }
  return -1
}

// Samlet styrkevolum (Σ reps×kg) i en økt, inkl. supersett-runder.
export function strengthVolume(content) {
  let vol = 0
  for (const b of content?.blocks ?? []) {
    if (b.kind === 'superset') {
      let sv = 0
      for (const ex of b.exercises ?? []) {
        for (const s of ex.sets ?? []) sv += (Number(s.reps) || 0) * (Number(s.weightKg) || 0)
      }
      vol += sv * (Number(b.rounds) || 1)
    } else {
      const sets = b.kind === 'dropset' ? (b.drops ?? []) : (b.sets ?? [])
      for (const s of sets) vol += (Number(s.reps) || 0) * (Number(s.weightKg) || 0)
    }
  }
  return vol
}

// Antall loggede sett i en styrkeøkt (supersett teller runder × sett).
export function countSets(content) {
  let n = 0
  for (const b of content?.blocks ?? []) {
    if (b.kind === 'superset') {
      for (const ex of b.exercises ?? []) n += (ex.sets ?? []).length * (Number(b.rounds) || 1)
    } else {
      n += (b.kind === 'dropset' ? (b.drops ?? []) : (b.sets ?? [])).length
    }
  }
  return n
}

// Lokal dato som YYYY-MM-DD (toISOString gir UTC og kan bomme på dagen rundt midnatt).
export function localIso(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// Hører den loggede økta til denne planlagte? Økter startet fra en plan kobles på plan-id (tåler at
// tittelen endres); eldre økter uten plan-id kobles på tittel som før.
export function matchesPlan(workout, plan) {
  if (workout.plannedId) return workout.plannedId === plan.id
  return (workout.title || '').toLowerCase() === (plan.title || '').toLowerCase()
}
