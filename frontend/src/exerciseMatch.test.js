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

  it('asks the server only about the names it does not recognise, and uses its answers', async () => {
    const p = post([{ name: 'Cable Bench Thing', id: 'bench-press', nb: 'Benkpress', en: 'Bench Press' }, { name: 'Zercher Something', id: null }])
    const r = await matchExerciseNames(['Barbell Bench Press', 'Cable Bench Thing', 'Zercher Something'], { lang: 'en', post: p })
    expect(JSON.parse(p.mock.calls[0][1]).names).toEqual(['Cable Bench Thing', 'Zercher Something'])
    expect(p.mock.calls[0][0]).toBe('/api/trening/ovelser/koble')
    expect(r.map.get('Cable Bench Thing')).toBe('Bench Press')
    expect(r.map.has('Zercher Something')).toBe(false)
    expect(r).toMatchObject({ matched: 2, unmatched: 1, viaAi: 1 })
  })

  it('ignores answers about names it did not ask for and ids the app does not know', async () => {
    const p = post([{ name: 'Noe annet', id: 'squat' }, { name: 'Mystery', id: 'finnes-ikke' }])
    const r = await matchExerciseNames(['Mystery'], { lang: 'en', post: p })
    expect(r.map.size).toBe(0)
  })

  it('keeps the original names when the server cannot help (quota, offline, no AI key)', async () => {
    const p = vi.fn(async () => { throw new Error('For mange forespørsler') })
    const r = await matchExerciseNames(['Barbell Bench Press', 'Mystery'], { lang: 'en', post: p })
    expect(r.map.get('Barbell Bench Press')).toBe('Bench Press')
    expect(r).toMatchObject({ matched: 1, unmatched: 1 })
  })

  it('splits long lists so each call stays under the server limit', async () => {
    const names = Array.from({ length: 320 }, (_, i) => `Ukjent ${i}`)
    const p = post([])
    await matchExerciseNames(names, { lang: 'en', post: p })
    expect(p.mock.calls.map((c) => JSON.parse(c[1]).names.length)).toEqual([150, 150, 20])
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
