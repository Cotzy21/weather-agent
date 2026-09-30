import { describe, it, expect } from 'vitest'
import { exerciseKey, normalizeName, sameGroup, EXERCISE_GROUPS, resolveExercise } from './exercises.js'

describe('exercise catalog', () => {
  it('treats Norwegian, English and alias spellings as one exercise', () => {
    const id = exerciseKey('Bicepscurl')
    expect(id).toBe('bicep-curls')
    expect(exerciseKey('Bicep Curls')).toBe(id)
    expect(exerciseKey('  biceps curl ')).toBe(id)
    expect(exerciseKey('Knebøy')).toBe(exerciseKey('Squat'))
  })

  it('falls back to the normalised name for exercises outside the catalog', () => {
    expect(exerciseKey('Min Øvelse')).toBe(exerciseKey('min  øvelse'))
    expect(exerciseKey('Min øvelse')).not.toBe(exerciseKey('Din øvelse'))
  })

  it('normalises like the backend (accents, punctuation, plural s)', () => {
    expect(normalizeName('Push-ups')).toBe(normalizeName('push up'))
    expect(normalizeName('Café')).toBe('cafe')
  })

  it('resolves every catalog name to its own id', () => {
    for (const g of EXERCISE_GROUPS) {
      for (const n of [...g.items, ...g.en]) expect(resolveExercise(n)).toBeDefined()
    }
  })

  it('suggests same-group exercises in the language of the input, excluding itself and its translations', () => {
    const nb = sameGroup('Benkpress')
    expect(nb).toContain('Skråbenkpress')
    expect(nb).not.toContain('Benkpress')
    const en = sameGroup('Bench Press')
    expect(en).toContain('Incline Bench Press')
    expect(en).not.toContain('Bench Press')
    expect(sameGroup('Helt egen øvelse')).toEqual([])
  })
})
