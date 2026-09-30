import { describe, it, expect } from 'vitest'
import { estimate1RM, exerciseSeries, exerciseTrends, sessionTrends } from './exerciseProgress.js'

const w = (date, blocks, extra = {}) => ({ date, type: 'STYRKE', title: 'Push', content: { blocks, ...extra } })
const ex = (name, ...sets) => ({ kind: 'exercise', name, sets: sets.map(([reps, weightKg]) => ({ reps, weightKg })) })

describe('estimate1RM', () => {
  it('makes different rep ranges comparable and ignores empty sets', () => {
    expect(estimate1RM(80, 1)).toBe(80)
    expect(estimate1RM(60, 10)).toBeCloseTo(80, 5)
    expect(estimate1RM(0, 10)).toBe(0)
    expect(estimate1RM(60, 0)).toBe(0)
  })
})

describe('exerciseSeries', () => {
  it('collects one point per day, oldest first, with the best set of the day', () => {
    const s = exerciseSeries([
      w('2026-03-01', [ex('Benkpress', [5, 80], [5, 82.5])]),
      w('2026-01-01', [ex('Benkpress', [8, 70])]),
    ]).get('bench-press')
    expect(s.points.map((p) => p.date)).toEqual(['2026-01-01', '2026-03-01'])
    expect(s.points[1].topWeight).toBe(82.5)
    expect(s.points[1].sets).toBe(2)
  })

  it('puts Norwegian and English spellings of one exercise in the same series', () => {
    const map = exerciseSeries([
      w('2026-01-01', [ex('Bicepscurl', [10, 15])]),
      w('2026-02-01', [ex('Bicep Curls', [10, 17.5])]),
      w('2026-03-01', [ex('Barbell Curl Variant', [10, 20])]),
    ])
    expect(map.get('bicep-curls').points).toHaveLength(2)
    expect(map.size).toBe(2)
  })

  it('reads supersets and only the top drop of a dropset', () => {
    const map = exerciseSeries([w('2026-01-01', [
      { kind: 'superset', rounds: 3, exercises: [{ name: 'Knebøy', sets: [{ reps: 5, weightKg: 100 }] }, { name: 'Pull-ups', sets: [{ reps: 8, weightKg: 0 }] }] },
      { kind: 'dropset', name: 'Sidehev', drops: [{ reps: 10, weightKg: 12 }, { reps: 10, weightKg: 8 }] },
    ])])
    expect(map.get('squat').points[0].topWeight).toBe(100)
    expect(map.get('pull-ups').points[0].topReps).toBe(8)
    expect(map.get('lateral-raises').points[0].topWeight).toBe(12)
  })

  it('ignores non-strength workouts, empty sets and CSV-only sessions without exercises', () => {
    const map = exerciseSeries([
      { date: '2026-01-01', type: 'LØPING', content: { distanceKm: 5 } },
      w('2026-01-02', [ex('Benkpress', [0, 80])]),
      w('2026-01-03', []),
    ])
    expect(map.size).toBe(0)
  })
})

describe('exerciseTrends', () => {
  it('shows progression over several years, per exercise, with percent change', () => {
    const workouts = [
      w('2024-01-01', [ex('Benkpress', [5, 60])]),
      w('2024-06-01', [ex('Benkpress', [5, 70])]),
      w('2025-06-01', [ex('Benkpress', [5, 80])]),
      w('2024-01-02', [ex('Knebøy', [5, 80])]),
      w('2025-06-02', [ex('Knebøy', [5, 100])]),
    ]
    const trends = exerciseTrends(workouts)
    const bench = trends.find((t) => t.key === 'bench-press')
    expect(trends[0].key).toBe('bench-press') // flest økter først
    expect(bench).toMatchObject({ sessions: 3, first: 60, last: 80, best: 80, firstDate: '2024-01-01', lastDate: '2025-06-01' })
    expect(bench.changePct).toBe(33)
    expect(trends.find((t) => t.key === 'squat').changePct).toBe(25)
  })

  it('uses reps for bodyweight exercises', () => {
    const t = exerciseTrends([
      w('2026-01-01', [ex('Pull-ups', [6, 0])]),
      w('2026-02-01', [ex('Pull-ups', [9, 0])]),
    ])[0]
    expect(t).toMatchObject({ weighted: false, first: 6, last: 9, changePct: 50 })
  })

  it('needs at least two sessions to talk about progress', () => {
    expect(exerciseTrends([w('2026-01-01', [ex('Benkpress', [5, 60])])])).toEqual([])
  })

  it('reports 0 % for an exercise that has not changed', () => {
    const t = exerciseTrends([w('2026-01-01', [ex('Sidehev', [10, 12])]), w('2026-02-01', [ex('Sidehev', [10, 12])])])[0]
    expect(t.changePct).toBe(0)
  })
})

describe('sessionTrends (CSV totals, no exercises)', () => {
  const csv = (date, title, totalSets, totalReps, durationMin) => ({
    date, type: 'STYRKE', title, content: { totalSets, totalReps, durationMin, kcal: 300 },
  })

  it('groups by title and reports how the sessions develop over time', () => {
    const workouts = [
      csv('2025-01-01', 'Push', 15, 150, 50), csv('2025-02-01', 'Push', 15, 150, 50),
      csv('2025-03-01', 'Push', 18, 180, 55), csv('2025-04-01', 'Push', 20, 220, 60),
      csv('2025-01-02', 'Pull', 12, 120, 45),
    ]
    const trends = sessionTrends(workouts)
    expect(trends).toHaveLength(1) // Pull har for få økter
    expect(trends[0]).toMatchObject({ title: 'Push', sessions: 4, metric: 'reps', firstDate: '2025-01-01', lastDate: '2025-04-01' })
    expect(trends[0].changePct).toBe(47) // 150 -> 220
    expect(trends[0].latest.sets).toBe(20)
  })

  it('treats "push" and "Push" as one split and skips sessions without any data', () => {
    const workouts = [
      csv('2025-01-01', 'push', 10, 100, 40), csv('2025-01-08', 'Push', 10, 100, 40), csv('2025-01-15', 'PUSH', 10, 100, 40),
      { date: '2025-01-20', type: 'STYRKE', title: 'Push', content: {} },
    ]
    const trends = sessionTrends(workouts)
    expect(trends[0].sessions).toBe(3)
  })

  it('uses volume when sessions have exercises with weights', () => {
    const detailed = (date, kg) => ({ date, type: 'STYRKE', title: 'Legs', content: { blocks: [ex('Knebøy', [5, kg], [5, kg])], durationMin: 60 } })
    const t = sessionTrends([detailed('2025-01-01', 100), detailed('2025-02-01', 110), detailed('2025-03-01', 120)])[0]
    expect(t.metric).toBe('volume')
    expect(t.latest.volume).toBe(1200)
  })
})
