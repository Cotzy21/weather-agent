import { describe, it, expect } from 'vitest'
import { humanizeExerciseName, uuidFromString, blocksFromSets, sessionFromFit } from './garminFit.js'
import { makeFit } from './fitTestUtils.js'
import { localIso } from './trainingStats.js'

describe('humanizeExerciseName', () => {
  it('turns Garmin camelCase names into readable ones', () => {
    expect(humanizeExerciseName('barbellBenchPress')).toBe('Barbell Bench Press')
    expect(humanizeExerciseName('pullUp')).toBe('Pull Up')
    expect(humanizeExerciseName('')).toBe('')
  })
})

describe('uuidFromString', () => {
  it('is stable, UUID-shaped and different for different input', () => {
    const a = uuidFromString('fit:2026-05-05T17:00:00.000Z')
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(uuidFromString('fit:2026-05-05T17:00:00.000Z')).toBe(a)
    expect(uuidFromString('fit:2026-05-06T17:00:00.000Z')).not.toBe(a)
  })
})

describe('blocksFromSets', () => {
  const name = (s) => s.n
  it('groups consecutive sets of the same exercise and ignores rest and empty sets', () => {
    const blocks = blocksFromSets([
      { n: 'A', repetitions: 8, weight: 60 },
      { n: 'A', repetitions: 0, weight: 60 },
      { n: 'A', repetitions: 8, weight: 62.5, setType: 'active' },
      { n: 'A', repetitions: 8, weight: 0, setType: 'rest' },
      { n: 'B', repetitions: 10, weight: 20 },
      { n: 'A', repetitions: 6, weight: 65 },
    ], name)
    expect(blocks).toEqual([
      { name: 'A', sets: [{ reps: 8, weightKg: 60 }, { reps: 8, weightKg: 62.5 }] },
      { name: 'B', sets: [{ reps: 10, weightKg: 20 }] },
      { name: 'A', sets: [{ reps: 6, weightKg: 65 }] },
    ])
  })
  it('treats missing weight as bodyweight (0) and drops absurd values', () => {
    const blocks = blocksFromSets([
      { n: 'Pull Up', repetitions: 8 },
      { n: 'Bogus', repetitions: 5000, weight: 10 },
      { n: 'Bogus2', repetitions: 5, weight: 99999 },
    ], name)
    expect(blocks).toEqual([{ name: 'Pull Up', sets: [{ reps: 8, weightKg: 0 }] }])
  })
  it('splits blocks over the 100-set limit', () => {
    const many = Array.from({ length: 130 }, () => ({ n: 'A', repetitions: 5, weight: 50 }))
    expect(blocksFromSets(many, name).map((b) => b.sets.length)).toEqual([100, 30])
  })
})

describe('sessionFromFit (real FIT files)', () => {
  const start = '2026-05-05T17:00:00Z'

  it('reads exercises, reps and weights from a strength workout', async () => {
    const bytes = makeFit({
      start,
      sets: [
        { category: 'benchPress', sub: 1, reps: 8, weight: 60 },
        { category: 'benchPress', sub: 1, reps: 8, weight: 62.5 },
        { category: 'pullUp', sub: 0, reps: 10, weight: 0 },
      ],
    })
    const { session, error } = await sessionFromFit(bytes)
    expect(error).toBeUndefined()
    expect(session.blocks).toHaveLength(2)
    expect(session.blocks[0].name).toBe('Barbell Bench Press')
    expect(session.blocks[0].sets).toEqual([{ reps: 8, weightKg: 60 }, { reps: 8, weightKg: 62.5 }])
    expect(session.blocks[1].name).toMatch(/pull/i)
    expect(session.date).toBe(localIso(new Date(start)))
    expect(session.durationMin).toBe(50)
    expect(session.kcal).toBe(310)
    expect(session.avgHr).toBe(118)
    expect(session.maxHr).toBe(151)
    expect(session.clientId).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('gives the same clientId for the same workout, so re-uploading never duplicates', async () => {
    const a = await sessionFromFit(makeFit({ start, sets: [{ category: 'squat', sub: 0, reps: 5, weight: 100 }] }))
    const b = await sessionFromFit(makeFit({ start, sets: [{ category: 'squat', sub: 0, reps: 5, weight: 100 }] }))
    expect(a.session.clientId).toBe(b.session.clientId)
  })

  it('uses the name you gave the exercise on the watch when there is one', async () => {
    const bytes = makeFit({
      start,
      sets: [{ category: 'benchPress', sub: 1, reps: 5, weight: 80 }],
      titles: [{ category: 'benchPress', sub: 1, name: 'Min benkpress' }],
    })
    expect((await sessionFromFit(bytes)).session.blocks[0].name).toBe('Min benkpress')
  })

  it('uses the workout name as the title when the file has one', async () => {
    const bytes = makeFit({ start, sets: [{ category: 'squat', sub: 0, reps: 5, weight: 100 }], workoutName: 'Legs' })
    expect((await sessionFromFit(bytes)).session.title).toBe('Legs')
  })

  it('skips activities without sets (running, cycling ...)', async () => {
    const bytes = makeFit({ start, sets: [], sport: 'running', subSport: 'generic' })
    const r = await sessionFromFit(bytes)
    expect(r.session).toBeUndefined()
    expect(r.skipped).toBeTruthy()
  })

  it('does not crash on files that are not FIT', async () => {
    const r = await sessionFromFit(new TextEncoder().encode('dette er ikke en fit-fil'))
    expect(r.session).toBeUndefined()
    expect(r.skipped || r.error).toBeTruthy()
    const empty = await sessionFromFit(new Uint8Array(0))
    expect(empty.session).toBeUndefined()
  })

  it('reads a workout that has only warm-up rest sets as not strength', async () => {
    const bytes = makeFit({ start, sets: [{ category: 'squat', sub: 0, reps: 5, weight: 100, type: 'rest' }] })
    expect((await sessionFromFit(bytes)).session).toBeUndefined()
  })
})
