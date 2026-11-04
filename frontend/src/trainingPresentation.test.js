import { describe, expect, it } from 'vitest'
import { cleanBuilderSets, summarizeSets } from './trainingPresentation.js'

describe('treningsvisning og manuell logging', () => {
  it('beholder desimalvekt fra norsk tastatur', () => {
    expect(cleanBuilderSets([{ reps: '8', weightKg: '42,5' }, { reps: '12', weightKg: '' }]))
      .toEqual([{ reps: 8, weightKg: 42.5 }, { reps: 12, weightKg: 0 }])
  })
  it('samler like sett uten å endre økta', () => {
    const sets = [{ reps: 8, weightKg: 40 }, { reps: 8, weightKg: 40 }, { reps: 6, weightKg: 35 }]
    expect(summarizeSets(sets)).toEqual([
      { count: 2, reps: 8, weightKg: 40 }, { count: 1, reps: 6, weightKg: 35 },
    ])
    expect(sets).toHaveLength(3)
  })
  it('beholder rekkefølgen på ulik vekt, også dropsett', () => {
    expect(summarizeSets([{ reps: 8, weightKg: 40 }, { reps: 8, weightKg: 30 }, { reps: 8, weightKg: 40 }]))
      .toHaveLength(3)
  })
})
