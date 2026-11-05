import { describe, expect, it } from 'vitest'
import { pairDays, spearman } from './garminAnalysis.js'

const days = [
  { date: '2026-10-24', metrics: { appFoodKcal: 2000, bedtime: 1430, workouts: 1 } },
  { date: '2026-10-25', metrics: { sleepMin: 420, bedtime: 10 } },
  { date: '2026-10-26', metrics: { sleepMin: 480, bedtime: 50 } },
  { date: '2026-10-27', metrics: {} },
  { date: '2026-10-28', metrics: { sleepMin: 400 } },
]

describe('Garmin comparisons', () => {
  it('matches logged food to the following wake date without imputing unlogged food', () => {
    expect(pairDays(days, 'appFoodKcal', 'sleepMin', 1)).toEqual([
      { date: '2026-10-24', target: '2026-10-25', x: 2000, y: 420 },
    ])
  })

  it('measures bedtime shifts across midnight and distinguishes an unlogged workout', () => {
    expect(pairDays(days, 'loggedWorkout', 'bedtimeShift', 1).map(({ x, y }) => [x, y])).toEqual([[1, 20], [0, 40]])
  })

  it('uses average ranks for ties and refuses sparse or constant comparisons', () => {
    const pairs = Array.from({ length: 12 }, (_, i) => ({ x: Math.floor(i / 3), y: -Math.floor(i / 3) }))
    expect(spearman(pairs)).toEqual({ rho: -1, count: 12 })
    expect(spearman(pairs.slice(0, 9))).toEqual({ reason: 'few', count: 9 })
    expect(spearman(pairs.map((pair) => ({ ...pair, x: 1 })))).toEqual({ reason: 'constant', count: 12 })
  })
})
