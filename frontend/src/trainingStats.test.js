import { describe, it, expect } from 'vitest'
import { dayIndex, strengthVolume, countSets, localIso, matchesPlan } from './trainingStats.js'

describe('dayIndex', () => {
  it('understands Norwegian and English day names, with or without extra text', () => {
    expect(dayIndex('mandag')).toBe(0)
    expect(dayIndex('Sunday')).toBe(6)
    expect(dayIndex('lørdag morgen')).toBe(5)
    expect(dayIndex('')).toBe(-1)
    expect(dayIndex('någon dag')).toBe(-1)
  })
})

describe('strength stats', () => {
  const content = {
    blocks: [
      { kind: 'exercise', name: 'Knebøy', sets: [{ reps: 5, weightKg: 100 }, { reps: 5, weightKg: 100 }] },
      { kind: 'dropset', name: 'Curl', drops: [{ reps: 10, weightKg: 20 }, { reps: 10, weightKg: 10 }] },
      { kind: 'superset', rounds: 3, exercises: [{ name: 'A', sets: [{ reps: 10, weightKg: 10 }] }, { name: 'B', sets: [{ reps: 10, weightKg: 5 }] }] },
    ],
  }
  it('sums volume including drops and superset rounds', () => {
    expect(strengthVolume(content)).toBe(1000 + 300 + (100 + 50) * 3)
  })
  it('counts sets including superset rounds', () => {
    expect(countSets(content)).toBe(2 + 2 + 2 * 3)
  })
  it('handles missing content', () => {
    expect(strengthVolume(undefined)).toBe(0)
    expect(countSets(null)).toBe(0)
  })
})

describe('localIso', () => {
  it('uses local calendar date, zero padded', () => {
    expect(localIso(new Date(2026, 0, 5))).toBe('2026-01-05')
  })
})

describe('matchesPlan', () => {
  const plan = { id: 'p1', title: 'Push' }
  it('matches on plan id even when the title was edited', () => {
    expect(matchesPlan({ plannedId: 'p1', title: 'Push (endret)' }, plan)).toBe(true)
  })
  it('does not match a workout from another plan with the same title', () => {
    expect(matchesPlan({ plannedId: 'p2', title: 'Push' }, plan)).toBe(false)
  })
  it('falls back to title for older workouts without plan id', () => {
    expect(matchesPlan({ title: 'push' }, plan)).toBe(true)
    expect(matchesPlan({ title: 'Pull' }, plan)).toBe(false)
  })
})
