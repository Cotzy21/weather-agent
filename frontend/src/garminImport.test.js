import { describe, it, expect, vi } from 'vitest'
import { zipSync } from 'fflate'
import { importCsv, importFit } from './garminImport.js'
import { makeFit } from './fitTestUtils.js'

function fakePost(handler) {
  const calls = []
  const post = async (path, body, type) => { calls.push({ path, body, type }); return handler(path, body, type, calls.length) }
  return { post, calls }
}

describe('importCsv', () => {
  const csv = ['Aktivitetstype,Dato,Tittel', ...Array.from({ length: 600 }, (_, i) => `Styrketrening,2026-01-01,Økt ${i}`)].join('\n')

  it('uploads a big CSV in several small pieces, each with the header, and adds up the results', async () => {
    const { post, calls } = fakePost(() => ({ imported: 200, skipped: 50 }))
    const progress = []
    const res = await importCsv(new File([csv], 'a.csv'), { post, onProgress: (p) => progress.push(p.done) })

    expect(calls.length).toBe(3)
    expect(calls.every((c) => c.path === '/api/trening/import/garmin' && c.type === 'text/plain')).toBe(true)
    expect(calls.every((c) => c.body.startsWith('Aktivitetstype,Dato,Tittel\n'))).toBe(true)
    expect(res).toEqual({ imported: 600, skipped: 150, merged: 0, rows: 600 })
    expect(progress).toEqual([1, 2, 3])
  })

  it('does nothing for an empty file', async () => {
    const { post, calls } = fakePost(() => ({}))
    expect(await importCsv(new File(['Aktivitetstype,Dato,Tittel\n'], 'a.csv'), { post })).toEqual({ imported: 0, skipped: 0, merged: 0, rows: 0 })
    expect(calls).toEqual([])
  })

  it('lets a failing upload stop the import with the error', async () => {
    const post = async () => { throw new Error('Serveren er utilgjengelig') }
    await expect(importCsv(new File([csv], 'a.csv'), { post })).rejects.toThrow('utilgjengelig')
  })

  it('counts rows that were merged into workouts that already came from FIT', async () => {
    const small = 'Aktivitetstype,Dato,Tittel\nStyrketrening,2026-01-01,Push\nStyrketrening,2026-01-03,Pull'
    const { post } = fakePost(() => ({ imported: 0, skipped: 0, merged: 2 }))
    expect((await importCsv(new File([small], 'a.csv'), { post })).merged).toBe(2)
  })
})

describe('importFit', () => {
  const strength = (day) => makeFit({ start: `2026-05-${String(day).padStart(2, '0')}T17:00:00Z`, sets: [{ category: 'squat', sub: 0, reps: 5, weight: 100 }] })
  const run = makeFit({ start: '2026-05-20T17:00:00Z', sets: [], sport: 'running', subSport: 'generic' })

  it('reads FIT files in a zip on the device and uploads only the strength sessions, in batches', async () => {
    const days = Array.from({ length: 30 }, (_, i) => i + 1)
    const files = Object.fromEntries(days.map((d) => [`s${d}.fit`, strength(d)]))
    files['run.fit'] = run
    files['data.json'] = new Uint8Array(10)
    const { post, calls } = fakePost((_p, body) => ({ imported: JSON.parse(body).sessions.length, merged: 0, skipped: 0 }))

    const t = await importFit([new File([zipSync(files)], 'export.zip')], { post, batchSize: 20 })

    expect(calls.map((c) => JSON.parse(c.body).sessions.length)).toEqual([20, 10])
    expect(calls.every((c) => c.path === '/api/trening/import/fit' && c.type === 'application/json')).toBe(true)
    expect(t).toMatchObject({ files: 1, fitFiles: 31, strength: 30, imported: 30, notStrength: 1, unreadable: 0 })
  })

  it('sends parsed sessions, never raw FIT bytes', async () => {
    const { post, calls } = fakePost(() => ({ imported: 1 }))
    await importFit([new File([strength(3)], 'a.fit')], { post })
    const s = JSON.parse(calls[0].body).sessions[0]
    expect(Object.keys(s).sort()).toEqual(['avgHr', 'blocks', 'clientId', 'date', 'durationMin', 'kcal', 'maxHr', 'title'])
    expect(s.blocks[0].name).toBe('Leg Press') // Garmin sitt navn for undertype 0 i kategorien «squat»
  })

  it('counts unreadable files and keeps going', async () => {
    const { post } = fakePost(() => ({ imported: 1 }))
    const t = await importFit([new File([new TextEncoder().encode('ikke fit')], 'kaputt.fit'), new File([strength(4)], 'ok.fit')], { post })
    expect(t.strength).toBe(1)
    expect(t.notStrength + t.unreadable).toBe(1)
  })

  it('accumulates merged and skipped counts from the server', async () => {
    const { post } = fakePost(() => ({ imported: 0, merged: 1, skipped: 1 }))
    const t = await importFit([new File([strength(5)], 'a.fit'), new File([strength(6)], 'b.fit')], { post, batchSize: 1 })
    expect(t).toMatchObject({ merged: 2, skipped: 2, imported: 0 })
  })

  it('collects all sessions first, matches the exercise names once, then uploads them under the app\'s names', async () => {
    const files = Object.fromEntries([1, 2, 3].map((d) => [`s${d}.fit`, makeFit({
      start: `2026-05-0${d}T17:00:00Z`, sets: [{ category: 'benchPress', sub: 1, reps: 5, weight: 80 }],
    })]))
    const { post, calls } = fakePost(() => ({ imported: 3, merged: 0, skipped: 0 }))
    const matchNames = vi.fn(async (names) => ({ map: new Map(names.map((n) => [n, 'Bench Press'])), matched: names.length, unmatched: 0, viaAi: 1 }))
    const phases = []

    const t = await importFit([new File([zipSync(files)], 'x.zip')], { post, matchNames, onProgress: (p) => phases.push(p.phase) })

    expect(matchNames).toHaveBeenCalledTimes(1)
    expect(matchNames.mock.calls[0][0]).toEqual(['Barbell Bench Press']) // unike navn, ikke ett per økt
    const sent = JSON.parse(calls[0].body).sessions
    expect(sent.map((s) => s.blocks[0].name)).toEqual(['Bench Press', 'Bench Press', 'Bench Press'])
    expect(t.names).toEqual({ total: 1, matched: 1, unmatched: 0, viaAi: 1 })
    expect(phases.indexOf('match')).toBeLessThan(phases.indexOf('send'))
    expect(phases.indexOf('read')).toBeLessThan(phases.indexOf('match'))
  })

  it('still imports with the original names when matching fails', async () => {
    const { post, calls } = fakePost(() => ({ imported: 1, merged: 0, skipped: 0 }))
    const matchNames = async () => { throw new Error('nede') }
    const t = await importFit([new File([strength(7)], 'a.fit')], { post, matchNames })
    expect(JSON.parse(calls[0].body).sessions[0].blocks[0].name).toBe('Leg Press')
    expect(t.imported).toBe(1)
    expect(t.names.unmatched).toBe(1)
  })

  it('counts the same workout only once when it appears twice in an export', async () => {
    const same = strength(9)
    const { post, calls } = fakePost((_p, body) => ({ imported: JSON.parse(body).sessions.length }))
    const zip = zipSync({ 'a.fit': same, 'copy/a.fit': same })
    const t = await importFit([new File([zip], 'x.zip')], { post })
    expect(t.strength).toBe(1)
    expect(JSON.parse(calls[0].body).sessions).toHaveLength(1)
  })
})
