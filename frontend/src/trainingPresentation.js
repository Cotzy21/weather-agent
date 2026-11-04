import { toNum } from './liveSession.js'

// Samle like sett ved siden av hverandre uten å endre rekkefølge eller dropsett.
export function summarizeSets(sets = []) {
  return sets.reduce((groups, set) => {
    const previous = groups.at(-1)
    if (previous && previous.reps === set.reps && previous.weightKg === set.weightKg) previous.count++
    else groups.push({ count: 1, reps: set.reps, weightKg: set.weightKg })
    return groups
  }, [])
}

// Samme desimalbehandling som live-økten, også ved manuell logging.
export function cleanBuilderSets(sets = []) {
  return sets.filter((set) => set.reps !== '' && set.reps != null)
    .map((set) => ({ reps: toNum(set.reps), weightKg: toNum(set.weightKg) || 0 }))
}
