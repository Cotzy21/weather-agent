import { describe, it, expect, vi } from 'vitest'
import { matchExerciseNames, applyExerciseNames } from './exerciseMatch.js'

const post = (matches) => vi.fn(async () => ({ matches }))

describe('matchExerciseNames', () => {
  it('matches what the catalog knows on the device, without touching the network', async () => {
    const p = post([])
    const r = await matchExerciseNames(['Barbell Bench Press', 'Standing Dumbbell Biceps Curl', 'Benkpress'], { lang: 'en', post: p })
    expect(p).not.toHaveBeenCalled()
    expect(r.map.get('Barbell Bench Press')).toBe('Bench Press')
    expect(r.map.get('Standing Dumbbell Biceps Curl')).toBe('Bicep Curls')
    expect(r.map.get('Benkpress')).toBe('Bench Press')
    expect(r).toMatchObject({ matched: 3, unmatched: 0, viaAi: 0 })
  })

  it('writes the names in the language the app is set to', async () => {
    const r = await matchExerciseNames(['Barbell Bench Press'], { lang: 'nb', post: post([]) })
    expect(r.map.get('Barbell Bench Press')).toBe('Benkpress')
  })

  it('keeps unknown imported names on the device, even when a server callback is supplied', async () => {
    const p = post([{ name: 'Private activity exercise', id: 'bench-press' }])
    const r = await matchExerciseNames(['Barbell Bench Press', 'Private activity exercise'], { lang: 'en', post: p })
    expect(p).not.toHaveBeenCalled()
    expect(r.map.has('Private activity exercise')).toBe(false)
    expect(r).toMatchObject({ matched: 1, unmatched: 1, viaAi: 0 })
  })

  it('does not send unknown names in bulk', async () => {
    const names = Array.from({ length: 320 }, (_, i) => `Ukjent ${i}`)
    const p = post([])
    const r = await matchExerciseNames(names, { lang: 'en', post: p })
    expect(p).not.toHaveBeenCalled()
    expect(r).toMatchObject({ matched: 0, unmatched: 320, viaAi: 0 })
  })

})

describe('applyExerciseNames', () => {
  it('renames exercises to the matched names, keeps the rest, and does not mutate the input', () => {
    const sessions = [{ date: 'd', blocks: [{ name: 'Barbell Bench Press', sets: [{ reps: 5, weightKg: 80 }] }, { name: 'Mystery', sets: [] }] }]
    const out = applyExerciseNames(sessions, new Map([['Barbell Bench Press', 'Bench Press']]))
    expect(out[0].blocks.map((b) => b.name)).toEqual(['Bench Press', 'Mystery'])
    expect(sessions[0].blocks[0].name).toBe('Barbell Bench Press')
    expect(out[0].blocks[0].sets).toEqual([{ reps: 5, weightKg: 80 }])
  })
})
